/*! ******************************************************************************
 *
 * Pentaho
 *
 * Copyright (C) 2024 by Hitachi Vantara, LLC : http://www.pentaho.com
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE.TXT file.
 *
 * Change Date: 2029-07-20
 ******************************************************************************/


package org.pentaho.platform.genericfile;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.pentaho.platform.api.genericfile.GenericFilePath;
import org.pentaho.platform.api.genericfile.GenericFilePermission;
import org.pentaho.platform.api.genericfile.GetFileOptions;
import org.pentaho.platform.api.genericfile.GetTreeOptions;
import org.pentaho.platform.api.genericfile.IGenericFileDecorator;
import org.pentaho.platform.api.genericfile.IGenericFileProvider;
import org.pentaho.platform.api.genericfile.IGenericFileService;
import org.pentaho.platform.api.genericfile.exception.BatchOperationFailedException;
import org.pentaho.platform.api.genericfile.exception.InvalidGenericFileProviderException;
import org.pentaho.platform.api.genericfile.exception.NotFoundException;
import org.pentaho.platform.api.genericfile.exception.OperationFailedException;
import org.pentaho.platform.api.genericfile.model.CreateFileOptions;
import org.pentaho.platform.api.genericfile.model.IGenericFile;
import org.pentaho.platform.api.genericfile.model.IGenericFileAcl;
import org.pentaho.platform.api.genericfile.model.IGenericFileContent;
import org.pentaho.platform.api.genericfile.model.IGenericFileMetadata;
import org.pentaho.platform.api.genericfile.model.IGenericFileTree;
import org.pentaho.platform.genericfile.decorators.NullGenericFileDecorator;
import org.pentaho.platform.genericfile.model.BaseGenericFile;
import org.pentaho.platform.genericfile.model.BaseGenericFileTree;
import org.pentaho.platform.util.logging.Logger;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings( "unused" )
public class DefaultGenericFileService implements IGenericFileService {
  @VisibleForTesting
  static final String MULTIPLE_PROVIDER_ROOT_PROVIDER = "combined";
  @VisibleForTesting
  static final String MULTIPLE_PROVIDER_ROOT_NAME = "root";

  private final List<IGenericFileProvider<?>> fileProviders;
  private final IGenericFileDecorator fileDecorator;

  public DefaultGenericFileService( @NonNull List<IGenericFileProvider<?>> fileProviders )
    throws InvalidGenericFileProviderException {
    this( fileProviders, new NullGenericFileDecorator() );
  }

  public DefaultGenericFileService( @NonNull List<IGenericFileProvider<?>> fileProviders,
                                    @NonNull IGenericFileDecorator fileDecorator )
    throws InvalidGenericFileProviderException {
    Objects.requireNonNull( fileProviders );
    Objects.requireNonNull( fileDecorator );

    if ( fileProviders.isEmpty() ) {
      throw new InvalidGenericFileProviderException();
    }

    // Create defensive copy to disallow external modification (and be sure there's always >= 1 provider).
    this.fileProviders = new ArrayList<>( fileProviders );
    this.fileDecorator = fileDecorator;
  }

  @Override
  public void clearTreeCache() {
    for ( IGenericFileProvider<?> fileProvider : fileProviders ) {
      try {
        fileProvider.clearTreeCache();
      } catch ( OperationFailedException e ) {
        // Clear as many as possible. Still, log each failure.
        Logger.error( this.getClass().getName(), "Error clearing tree cache.", e );
      }
    }
  }

  @NonNull
  @Override
  public List<IGenericFileTree> getRootTrees( @NonNull GetTreeOptions options ) throws OperationFailedException {
    List<IGenericFileTree> rootTrees = new ArrayList<>();

    boolean oneProviderSucceeded = false;
    OperationFailedException firstProviderException = null;

    for ( IGenericFileProvider<?> fileProvider : fileProviders ) {
      try {
        rootTrees.addAll( fileProvider.getRootTrees( options ) );
        oneProviderSucceeded = true;
      } catch ( OperationFailedException e ) {
        if ( firstProviderException == null ) {
          firstProviderException = e;
        }

        // Continue, collecting providers that work. But still log failed ones, JIC.
        Logger.error( this.getClass().getName(), "Error getting root trees.", e );
      }
    }

    if ( firstProviderException != null && !oneProviderSucceeded ) {
      // All providers failed. Opting to throw the error of the first failed one to the caller.
      throw firstProviderException;
    }

    for ( IGenericFileTree rootTree : rootTrees ) {
      fileDecorator.decorateTree( rootTree, this, options );
    }

    return rootTrees;
  }

  @NonNull
  @Override
  public IGenericFileTree getTree( @NonNull GetTreeOptions options ) throws OperationFailedException {
    Objects.requireNonNull( options );

    if ( isSingleProviderMode() ) {
      IGenericFileTree tree = fileProviders.get( 0 ).getTree( options );
      fileDecorator.decorateTree( tree, this, options );
      return tree;
    }

    return options.getBasePath() == null
      ? getTreeFromRoot( options )
      : getSubTree( options.getBasePath(), options );
  }

  @VisibleForTesting
  boolean isSingleProviderMode() {
    return fileProviders.size() == 1;
  }

  @NonNull
  private IGenericFileTree getTreeFromRoot( @NonNull GetTreeOptions options ) throws OperationFailedException {
    BaseGenericFileTree rootTree = createMultipleProviderTreeRoot();
    OperationFailedException firstProviderException = null;

    for ( IGenericFileProvider<?> fileProvider : fileProviders ) {
      try {
        rootTree.addChild( fileProvider.getTree( options ) );
      } catch ( OperationFailedException e ) {
        if ( firstProviderException == null ) {
          firstProviderException = e;
        }

        // Continue, collecting providers that work. But still log failed ones, JIC.
        Logger.error( this.getClass().getName(), "Error getting tree from root.", e );
      }
    }

    if ( firstProviderException != null && rootTree.getChildren() == null ) {
      // All providers failed. Opting to throw the error of the first failed one to the caller.
      throw firstProviderException;
    }

    fileDecorator.decorateTree( rootTree, this, options );

    return rootTree;
  }

  @NonNull
  private static BaseGenericFileTree createMultipleProviderTreeRoot() {
    // Note that the absolute root has a null path.
    BaseGenericFile entity = new BaseGenericFile();
    entity.setName( MULTIPLE_PROVIDER_ROOT_NAME );
    entity.setProvider( MULTIPLE_PROVIDER_ROOT_PROVIDER );
    entity.setType( IGenericFile.TYPE_FOLDER );

    return new BaseGenericFileTree( entity );
  }

  @NonNull
  private IGenericFileTree getSubTree( @NonNull GenericFilePath basePath, @NonNull GetTreeOptions options )
    throws OperationFailedException {
    // In multi-provider mode, and fetching a subtree based on basePath, the parent path is the parent path of basePath.
    IGenericFileTree tree = getOwnerFileProvider( basePath ).getTree( options );
    fileDecorator.decorateTree( tree, this, options );
    return tree;
  }

  @Override
  public boolean doesFolderExist( @NonNull GenericFilePath path ) throws OperationFailedException {
    Optional<IGenericFileProvider<?>> fileProvider = getFirstOwnerFileProvider( path );
    return fileProvider.isPresent() && fileProvider.get().doesFolderExist( path );
  }

  @Override
  public boolean createFolder( @NonNull GenericFilePath path ) throws OperationFailedException {
    return getOwnerFileProvider( path ).createFolder( path );
  }

  @Override
  public void createFile( @NonNull GenericFilePath path,
                          @NonNull InputStream content,
                          @NonNull CreateFileOptions createFileOptions )
    throws OperationFailedException {
    getOwnerFileProvider( path ).createFile( path, content, createFileOptions );
  }

  @Override
  public void setFileContent( @NonNull GenericFilePath path, @NonNull InputStream content )
    throws OperationFailedException {
    getOwnerFileProvider( path ).setFileContent( path, content );
  }

  @NonNull
  @Override
  public IGenericFileContent getFileContent( @NonNull GenericFilePath path, boolean compressed )
    throws OperationFailedException {
    return getOwnerFileProvider( path ).getFileContent( path, compressed );
  }

  @NonNull
  @Override
  public IGenericFile getFile( @NonNull GenericFilePath path ) throws OperationFailedException {
    return getFile( path, new GetFileOptions() );
  }

  @NonNull
  @Override
  public IGenericFile getFile( @NonNull GenericFilePath path, @NonNull GetFileOptions options )
    throws OperationFailedException {
    IGenericFile file = getOwnerFileProvider( path ).getFile( path, options );
    fileDecorator.decorateFile( file, this, options );
    return file;
  }

  private Optional<IGenericFileProvider<?>> getFirstOwnerFileProvider( @NonNull GenericFilePath path ) {
    return fileProviders.stream()
      .filter( fileProvider -> fileProvider.owns( path ) )
      .findFirst();
  }

  private IGenericFileProvider<?> getOwnerFileProvider( @NonNull GenericFilePath path ) throws NotFoundException {
    return getFirstOwnerFileProvider( path ).orElseThrow(
      () -> new NotFoundException( String.format( "Path not found '%s'.", path ) ) );
  }

  @Override
  public boolean hasAccess( @NonNull GenericFilePath path, @NonNull EnumSet<GenericFilePermission> permissions )
    throws OperationFailedException {
    Optional<IGenericFileProvider<?>> fileProvider = getFirstOwnerFileProvider( path );
    return fileProvider.isPresent() && fileProvider.get().hasAccess( path, permissions );
  }

  @Override
  @NonNull
  public List<IGenericFile> getDeletedFiles() throws OperationFailedException {
    List<IGenericFile> deletedFiles = new ArrayList<>();

    boolean oneProviderSucceeded = false;
    OperationFailedException firstProviderException = null;

    for ( IGenericFileProvider<?> fileProvider : fileProviders ) {
      try {
        deletedFiles.addAll( fileProvider.getDeletedFiles() );
        oneProviderSucceeded = true;
      } catch ( OperationFailedException e ) {
        if ( firstProviderException == null ) {
          firstProviderException = e;
        }

        // Continue, collecting providers that work. But still log failed ones, JIC.
        Logger.error( this.getClass().getName(), "Error getting deleted files.", e );
      }
    }

    if ( firstProviderException != null && !oneProviderSucceeded ) {
      // All providers failed. Opting to throw the error of the first failed one to the caller.
      throw firstProviderException;
    }

    return deletedFiles;
  }

  @Override
  public void deleteFilesPermanently( @NonNull List<GenericFilePath> paths ) throws OperationFailedException {
    BatchOperationFailedException batchException = null;

    for ( GenericFilePath path : paths ) {
      try {
        deleteFilePermanently( path );
      } catch ( OperationFailedException e ) {
        if ( batchException == null ) {
          batchException = new BatchOperationFailedException( "Error(s) occurred during permanent deletion." );
        }

        batchException.addFailedPath( path, e );
      }
    }

    if ( batchException != null ) {
      throw batchException;
    }
  }

  @Override
  public void deleteFilePermanently( @NonNull GenericFilePath path ) throws OperationFailedException {
    getOwnerFileProvider( path ).deleteFilePermanently( path );
  }

  @Override
  public void deleteFiles( @NonNull List<GenericFilePath> paths, boolean permanent ) throws OperationFailedException {
    BatchOperationFailedException batchException = null;

    for ( GenericFilePath path : paths ) {
      try {
        deleteFile( path, permanent );
      } catch ( OperationFailedException e ) {
        if ( batchException == null ) {
          batchException = new BatchOperationFailedException( "Error(s) occurred during deletion." );
        }

        batchException.addFailedPath( path, e );
      }
    }

    if ( batchException != null ) {
      throw batchException;
    }
  }

  @Override
  public void deleteFile( @NonNull GenericFilePath path, boolean permanent ) throws OperationFailedException {
    getOwnerFileProvider( path ).deleteFile( path, permanent );
  }

  @Override
  public void restoreFiles( @NonNull List<GenericFilePath> paths ) throws OperationFailedException {
    BatchOperationFailedException batchException = null;

    for ( GenericFilePath path : paths ) {
      try {
        restoreFile( path );
      } catch ( OperationFailedException e ) {
        if ( batchException == null ) {
          batchException = new BatchOperationFailedException( "Error(s) occurred while attempting to restore files." );
        }

        batchException.addFailedPath( path, e );
      }
    }

    if ( batchException != null ) {
      throw batchException;
    }
  }

  @Override
  public void restoreFile( @NonNull GenericFilePath path ) throws OperationFailedException {
    getOwnerFileProvider( path ).restoreFile( path );
  }

  @Override
  public boolean renameFile( @NonNull GenericFilePath path, @NonNull String newName ) throws OperationFailedException {
    return getOwnerFileProvider( path ).renameFile( path, newName );
  }

  protected boolean isDifferentProvider( @NonNull GenericFilePath path1, @NonNull GenericFilePath path2 )
    throws NotFoundException {
    Objects.requireNonNull( path1 );
    Objects.requireNonNull( path2 );

    return !getOwnerFileProvider( path1 ).equals( getOwnerFileProvider( path2 ) );
  }

  @Override
  public void copyFiles( @NonNull List<GenericFilePath> paths, @NonNull GenericFilePath destinationFolder )
    throws OperationFailedException {
    BatchOperationFailedException batchException = null;

    for ( GenericFilePath path : paths ) {
      try {
        copyFile( path, destinationFolder );
      } catch ( OperationFailedException e ) {
        if ( batchException == null ) {
          batchException = new BatchOperationFailedException( "Error(s) occurred while attempting to copy files." );
        }

        batchException.addFailedPath( path, e );
      }
    }

    if ( batchException != null ) {
      throw batchException;
    }
  }

  @Override
  public void copyFile( @NonNull GenericFilePath path, @NonNull GenericFilePath destinationFolder )
    throws OperationFailedException {
    if ( isDifferentProvider( path, destinationFolder ) ) {
      throw new UnsupportedOperationException( "Cannot copy files to different providers." );
    }

    getOwnerFileProvider( path ).copyFile( path, destinationFolder );
  }

  @Override
  public void moveFiles( @NonNull List<GenericFilePath> paths, @NonNull GenericFilePath destinationFolder )
    throws OperationFailedException {
    BatchOperationFailedException batchException = null;

    for ( GenericFilePath path : paths ) {
      try {
        moveFile( path, destinationFolder );
      } catch ( OperationFailedException e ) {
        if ( batchException == null ) {
          batchException = new BatchOperationFailedException( "Error(s) occurred while attempting to move files." );
        }

        batchException.addFailedPath( path, e );
      }
    }

    if ( batchException != null ) {
      throw batchException;
    }
  }

  @Override
  public void moveFile( @NonNull GenericFilePath path, @NonNull GenericFilePath destinationFolder )
    throws OperationFailedException {
    if ( isDifferentProvider( path, destinationFolder ) ) {
      throw new UnsupportedOperationException( "Cannot move files to different providers." );
    }

    getOwnerFileProvider( path ).moveFile( path, destinationFolder );
  }

  @NonNull
  @Override
  public IGenericFileMetadata getFileMetadata( @NonNull GenericFilePath path ) throws OperationFailedException {
    IGenericFileMetadata metadata = getOwnerFileProvider( path ).getFileMetadata( path );
    fileDecorator.decorateFileMetadata( metadata, path, this );
    return metadata;
  }

  @Override
  public void setFileMetadata( @NonNull GenericFilePath path, @NonNull IGenericFileMetadata metadata )
    throws OperationFailedException {
    getOwnerFileProvider( path ).setFileMetadata( path, metadata );
  }

  @NonNull
  @Override
  public IGenericFileAcl getFileAcl( @NonNull GenericFilePath path, boolean forceInheriting )
    throws OperationFailedException {
    return getOwnerFileProvider( path ).getFileAcl( path, forceInheriting );
  }

  @Override
  public void setFileAcl( @NonNull GenericFilePath path, @NonNull IGenericFileAcl acl )
    throws OperationFailedException {
    getOwnerFileProvider( path ).setFileAcl( path, acl );
  }

  @Override
  public boolean validateFileAcl( @NonNull GenericFilePath path, @NonNull IGenericFileAcl acl )
    throws OperationFailedException {
    return getOwnerFileProvider( path ).validateFileAcl( acl );
  }
}
