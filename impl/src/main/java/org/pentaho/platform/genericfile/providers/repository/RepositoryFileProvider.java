/*! ******************************************************************************
 *
 * Pentaho
 *
 * Copyright (C) 2024 - 2026 by Pentaho Canada Inc. : http://www.pentaho.com
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE.TXT file.
 *
 * Change Date: 2030-06-15
 ******************************************************************************/


package org.pentaho.platform.genericfile.providers.repository;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.MediaType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.pentaho.platform.api.genericfile.GenericFilePath;
import org.pentaho.platform.api.genericfile.GenericFilePermission;
import org.pentaho.platform.api.genericfile.GenericFilePrincipalType;
import org.pentaho.platform.api.genericfile.GetFileOptions;
import org.pentaho.platform.api.genericfile.GetTreeOptions;
import org.pentaho.platform.api.genericfile.TreeProviderTypes;
import org.pentaho.platform.api.genericfile.exception.AccessControlException;
import org.pentaho.platform.api.genericfile.exception.ConflictException;
import org.pentaho.platform.api.genericfile.exception.InvalidOperationException;
import org.pentaho.platform.api.genericfile.exception.InvalidPathException;
import org.pentaho.platform.api.genericfile.exception.NotFoundException;
import org.pentaho.platform.api.genericfile.exception.OperationFailedException;
import org.pentaho.platform.api.genericfile.exception.ResourceAccessDeniedException;
import org.pentaho.platform.api.genericfile.model.CreateFileOptions;
import org.pentaho.platform.api.genericfile.model.IGenericFile;
import org.pentaho.platform.api.genericfile.model.IGenericFileAce;
import org.pentaho.platform.api.genericfile.model.IGenericFileAcl;
import org.pentaho.platform.api.genericfile.model.IGenericFileContent;
import org.pentaho.platform.api.genericfile.model.IGenericFileMetadata;
import org.pentaho.platform.api.genericfile.model.IGenericFileTree;
import org.pentaho.platform.api.importexport.ExportException;
import org.pentaho.platform.api.repository2.unified.IRepositoryContentConverterHandler;
import org.pentaho.platform.api.repository2.unified.IUnifiedRepository;
import org.pentaho.platform.api.repository2.unified.RepositoryFileAcl;
import org.pentaho.platform.api.repository2.unified.RepositoryFilePermission;
import org.pentaho.platform.api.repository2.unified.UnifiedRepositoryAccessDeniedException;
import org.pentaho.platform.api.repository2.unified.UnifiedRepositoryException;
import org.pentaho.platform.api.repository2.unified.data.simple.SimpleRepositoryFileData;
import org.pentaho.platform.api.repository2.unified.webservices.RepositoryFileAclAceDto;
import org.pentaho.platform.api.repository2.unified.webservices.RepositoryFileAclDto;
import org.pentaho.platform.api.repository2.unified.webservices.RepositoryFileDto;
import org.pentaho.platform.api.repository2.unified.webservices.RepositoryFileTreeDto;
import org.pentaho.platform.api.repository2.unified.webservices.StringKeyStringValueDto;
import org.pentaho.platform.engine.core.system.PentahoSessionHolder;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.genericfile.BaseGenericFileProvider;
import org.pentaho.platform.genericfile.messages.Messages;
import org.pentaho.platform.genericfile.model.BaseGenericFile;
import org.pentaho.platform.genericfile.model.BaseGenericFileAce;
import org.pentaho.platform.genericfile.model.BaseGenericFileAcl;
import org.pentaho.platform.genericfile.model.BaseGenericFileMetadata;
import org.pentaho.platform.genericfile.model.BaseGenericFileTree;
import org.pentaho.platform.genericfile.model.DefaultGenericFileContent;
import org.pentaho.platform.genericfile.providers.repository.model.RepositoryFile;
import org.pentaho.platform.genericfile.providers.repository.model.RepositoryFileTree;
import org.pentaho.platform.genericfile.providers.repository.model.RepositoryFolder;
import org.pentaho.platform.genericfile.providers.repository.model.RepositoryObject;
import org.pentaho.platform.plugin.services.importexport.BaseExportProcessor;
import org.pentaho.platform.plugin.services.importexport.DefaultExportHandler;
import org.pentaho.platform.plugin.services.importexport.ExportHandler;
import org.pentaho.platform.plugin.services.importexport.ZipExportProcessor;
import org.pentaho.platform.repository.RepositoryFilenameUtils;
import org.pentaho.platform.repository2.unified.fileio.RepositoryFileInputStream;
import org.pentaho.platform.repository2.unified.fileio.RepositoryFileOutputStream;
import org.pentaho.platform.repository2.unified.webservices.DateAdapter;
import org.pentaho.platform.repository2.unified.webservices.DefaultUnifiedRepositoryWebService;
import org.pentaho.platform.util.StringUtil;
import org.pentaho.platform.util.messages.LocaleHelper;
import org.pentaho.platform.web.http.api.resources.services.FileService;
import org.pentaho.platform.web.http.api.resources.utils.SystemUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.pentaho.platform.util.RepositoryPathEncoder.encodeRepositoryPath;

@SuppressWarnings( { "java:S3008", "java:S1192" } )
public class RepositoryFileProvider extends BaseGenericFileProvider<RepositoryFile> {
  private static final String INVALID_SECURITY_PRINCIPAL_CHARACTERS = "[#,+\"\\\\<>;=]";
  private static final Pattern INVALID_SECURITY_PRINCIPAL_PATTERN =
    Pattern.compile( INVALID_SECURITY_PRINCIPAL_CHARACTERS );

  public static final String ROOT_PATH = "/";
  public static final String FOLDER_NAME_TRASH = ".trash";
  public static final String FILE_UPDATE_MSG = "Updating existing file";
  public static final String FILE_CREATE_MSG = "Create file";

  private static GenericFilePath ROOT_GENERIC_PATH;

  static {
    try {
      ROOT_GENERIC_PATH = GenericFilePath.parseRequired( ROOT_PATH );
    } catch ( InvalidPathException e ) {
      // ROOT_PATH is a valid constant; initialization cannot fail.
    }
  }

  public static final String TYPE = TreeProviderTypes.REPOSITORY;

  @NonNull
  private final IUnifiedRepository unifiedRepository;

  /**
   * The file service wraps a unified repository and provides additional functionality.
   */
  @NonNull
  private final FileService fileService;

  @NonNull
  private final DateAdapter repositoryWsDateAdapter;

  // TODO: Actually fix the base FileService class to do this and eliminate this class when available on the platform.

  /**
   * Custom {@code FileService} class that ensures that the contained repository web service uses the specified unified
   * repository instance. The methods {@code getRepositoryFileInputStream} and {@code getRepositoryFileOutputStream}
   * also do not pass the correct repository instance forward.
   */
  private static class CustomFileService extends FileService {
    public CustomFileService( @NonNull IUnifiedRepository repository ) {
      this.repository = Objects.requireNonNull( repository );
    }

    @Override
    protected DefaultUnifiedRepositoryWebService getRepoWs() {
      if ( defaultUnifiedRepositoryWebService == null ) {
        defaultUnifiedRepositoryWebService = new DefaultUnifiedRepositoryWebService( repository );
      }

      return defaultUnifiedRepositoryWebService;
    }

    @Override
    public RepositoryFileOutputStream getRepositoryFileOutputStream( String path ) {
      return new RepositoryFileOutputStream( path, false, false, repository, false );
    }

    @Override
    public RepositoryFileInputStream getRepositoryFileInputStream(
      org.pentaho.platform.api.repository2.unified.RepositoryFile repositoryFile ) throws FileNotFoundException {
      return new RepositoryFileInputStream( repositoryFile, repository );
    }
  }

  @SuppressWarnings( "unused" )
  public RepositoryFileProvider() {
    this( PentahoSystem.get( IUnifiedRepository.class, PentahoSessionHolder.getSession() ) );
  }

  public RepositoryFileProvider( @NonNull IUnifiedRepository unifiedRepository ) {
    this( unifiedRepository, new CustomFileService( unifiedRepository ) );
  }

  public RepositoryFileProvider( @NonNull IUnifiedRepository unifiedRepository, @NonNull FileService fileService ) {
    this.unifiedRepository = Objects.requireNonNull( unifiedRepository );
    this.fileService = Objects.requireNonNull( fileService );
    this.repositoryWsDateAdapter = new DateAdapter();
  }

  @NonNull
  @Override
  public Class<RepositoryFile> getFileClass() {
    return RepositoryFile.class;
  }

  @NonNull
  @Override
  public String getName() {
    return Messages.getString( "GenericFileRepository.REPOSITORY_FOLDER_DISPLAY" );
  }

  @NonNull
  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  protected boolean createFolderCore( @NonNull GenericFilePath path ) throws OperationFailedException {
    // When the parent path is not found, its creation is attempted.
    try {
      return fileService.doCreateDirSafe( pathToString( path ) );
    } catch ( UnifiedRepositoryAccessDeniedException e ) {
      // URADE covers both operation-wide ABS denial and WRITE denial on an existing ancestor.
      GenericFilePath deniedPath = findFirstNonWritablePath( path );

      if ( deniedPath != null ) {
        throw new ResourceAccessDeniedException(
          String.format( "User is not authorized to create folder at '%s'.", deniedPath ), path, e );
      }

      throw new AccessControlException( e );
    } catch ( FileService.InvalidNameException e ) {
      // FileService rejected the folder name before repository creation.
      throw new InvalidPathException();
    } catch ( UnifiedRepositoryException e ) {
      // Non-access repository failure; permission probing must not reclassify it.
      throw new OperationFailedException( e );
    }
  }

  @SuppressWarnings( "java:S1141" )
  @Override
  protected void createFileCore( @NonNull GenericFilePath path,
                                 @NonNull InputStream content,
                                 @NonNull CreateFileOptions createFileOptions )
    throws OperationFailedException {
    if ( !Boolean.parseBoolean( fileService.doGetCanCreate() ) ) {
      throw new AccessControlException();
    }

    String pathString = pathToString( path );

    if ( !fileService.isPathValid( pathString ) ) {
      throw new InvalidPathException( String.format( "Invalid path: '%s'.", path ) );
    }

    org.pentaho.platform.api.repository2.unified.RepositoryFile file = null;

    try {
      SimpleRepositoryFileData fileData = createSimpleRepositoryFileData( content, path );

      try {
        file = getNativeFile( path );
      } catch ( NotFoundException e ) {
        // Missing target selects create rather than overwrite behavior.
      }

      // Checking if the file exists for create or update
      if ( file != null ) {
        if ( file.isFolder() ) {
          throw new InvalidOperationException( "File is a folder." );
        }

        if ( !createFileOptions.isOverwrite() ) {
          throw new ConflictException( String.format( "File already exists at '%s'.", path ) );
        }

        file = unifiedRepository.updateFile( file, fileData, FILE_UPDATE_MSG );
      } else {
        String newName = path.getLastSegment();
        validateFileName( newName );

        org.pentaho.platform.api.repository2.unified.RepositoryFile newFile =
          new org.pentaho.platform.api.repository2.unified.RepositoryFile.Builder( newName )
            .versioned( false )
            .build();

        org.pentaho.platform.api.repository2.unified.RepositoryFile parentFile =
          getOrCreateNativeFolder( Objects.requireNonNull( path.getParent() ) );

        file = unifiedRepository.createFile( parentFile.getId(), newFile, fileData, FILE_CREATE_MSG );
      }
    } catch ( UnifiedRepositoryAccessDeniedException e ) {
      // URADE can identify operation-wide denial or WRITE denial on the target or parent.
      if ( fileService.doesExist( pathString ) && !canWrite( path ) ) {
        throw new ResourceAccessDeniedException( String.format( "User is not authorized to write to '%s'.", path ),
          path, e );
      }

      GenericFilePath parentPath = path.getParent();

      if ( parentPath != null ) {
        checkFileExists( parentPath );

        if ( !canWrite( parentPath ) ) {
          throw new ResourceAccessDeniedException(
            String.format( "User is not authorized to write to '%s'.", parentPath ), parentPath, e );
        }
      }

      throw new AccessControlException( e );
    } catch ( UnifiedRepositoryException | IOException e ) {
      // Repository or content-stream failure occurred without an access-denial signal.
      throw new OperationFailedException( e );
    }

    if ( file == null ) {
      throw new NotFoundException( "Unable to create " + path + " in the repository." );
    }
  }

  @SuppressWarnings( "java:S1141" )
  @Override
  protected void setFileContentCore( @NonNull GenericFilePath path, @NonNull InputStream content )
    throws OperationFailedException {
    String pathString = pathToString( path );

    if ( !fileService.isPathValid( pathString ) ) {
      throw new InvalidPathException( String.format( "Invalid path: '%s'.", path ) );
    }

    try {
      org.pentaho.platform.api.repository2.unified.RepositoryFile file = getNativeFile( path );

      if ( file.isFolder() ) {
        throw new InvalidOperationException( "Path references a folder, not a file." );
      }

      SimpleRepositoryFileData fileData = createSimpleRepositoryFileData( content, path );

      org.pentaho.platform.api.repository2.unified.RepositoryFile updatedFile =
        unifiedRepository.updateFile( file, fileData, FILE_UPDATE_MSG );

      if ( updatedFile == null ) {
        throw new NotFoundException( "Unable to update content of " + path + " in the repository." );
      }
    } catch ( UnifiedRepositoryAccessDeniedException e ) {
      // URADE can identify operation-wide denial or WRITE denial on the file or parent.
      if ( fileService.doesExist( pathString ) && !canWrite( path ) ) {
        throw new ResourceAccessDeniedException( String.format( "User is not authorized to write to '%s'.", path ),
          path, e );
      }

      GenericFilePath parentPath = path.getParent();

      if ( parentPath != null ) {
        checkFileExists( parentPath );

        if ( !canWrite( parentPath ) ) {
          throw new ResourceAccessDeniedException(
            String.format( "User is not authorized to write to '%s'.", parentPath ), parentPath, e );
        }
      }

      throw new AccessControlException( e );
    } catch ( UnifiedRepositoryException | IOException e ) {
      // Repository or content-stream failure occurred without an access-denial signal.
      throw new OperationFailedException( e );
    }
  }

  /**
   * Creates a {@link SimpleRepositoryFileData} from the given input stream, attempting to detect the MIME type
   * from the stream content. If detection fails, it falls back to guessing from the file path extension.
   * If both detection methods fail, defaults to {@code "application/octet-stream"}.
   *
   * @param content the input stream with the file content. Must support mark/reset for MIME detection from content.
   * @param path    the generic file path, used as a fallback for MIME type detection based on file extension.
   * @return a {@link SimpleRepositoryFileData} instance with the detected or default MIME type.
   * @throws IOException if an I/O error occurs while probing the content type.
   */
  @NonNull
  protected SimpleRepositoryFileData createSimpleRepositoryFileData( @NonNull InputStream content,
                                                                     @NonNull GenericFilePath path )
    throws IOException {
    return new SimpleRepositoryFileData( content, LocaleHelper.getSystemEncoding(), detectMimeType( content, path ) );
  }

  /**
   * Detects the MIME type of the given input stream content. First attempts detection from the stream bytes
   * (requires the stream to support mark/reset). If that fails, falls back to guessing from the file path extension
   * using {@link URLConnection#guessContentTypeFromName(String)}.
   * Defaults to {@code "application/octet-stream"} if both methods fail.
   *
   * @param content the input stream (should support mark/reset for content-based detection).
   * @param path    the generic file path used for extension-based fallback detection.
   * @return the detected MIME type, or {@code "application/octet-stream"} if detection fails.
   * @throws IOException if an I/O error occurs while reading from the stream.
   */
  @NonNull
  protected String detectMimeType( @NonNull InputStream content, @NonNull GenericFilePath path ) throws IOException {
    String mimeType = null;

    // Attempt to detect MIME type from stream content (requires mark/reset support).
    if ( content.markSupported() ) {
      mimeType = URLConnection.guessContentTypeFromStream( content );
    }

    // Fallback: guess from file name/extension.
    if ( mimeType == null ) {
      mimeType = URLConnection.guessContentTypeFromName( path.getLastSegment() );
    }

    // Default fallback.
    return mimeType != null ? mimeType : "application/octet-stream";
  }

  protected void validateFileName( @NonNull String fileName ) throws InvalidOperationException {
    if ( StringUtils.isEmpty( fileName ) ) {
      throw new InvalidOperationException( "File name cannot be empty." );
    }

    String ext = RepositoryFilenameUtils.getExtension( fileName );
    IRepositoryContentConverterHandler handler = getContentConverterHandler();

    if ( handler != null && handler.getConverter( ext ) == null ) {
      throw new InvalidOperationException( String.format( "The file extension '%s' is not valid.", fileName ) );
    }

    if ( !fileService.isValidFileName( fileName, true ) ) {
      throw new InvalidOperationException( String.format( "The new name '%s' is not valid.", fileName ) );
    }
  }

  @NonNull
  @Override
  protected List<BaseGenericFileTree> getRootTreesCore( @NonNull GetTreeOptions options )
    throws OperationFailedException {
    if ( !options.includesProviderType( TYPE ) ) {
      return Collections.emptyList();
    }

    // Ignore options.getBasePath()
    // Result already has a null parent path.
    return List.of( getTreeCore( ROOT_GENERIC_PATH, options ) );
  }

  @NonNull
  protected RepositoryFileTree getTreeCore( @NonNull GetTreeOptions options ) throws OperationFailedException {
    return getTreeCore( options.getBasePath(), options );
  }

  @NonNull
  protected RepositoryFileTree getTreeCore( @Nullable GenericFilePath basePath, @NonNull GetTreeOptions options )
    throws OperationFailedException {
    // Get the whole tree under the provider root (VFS connections)?
    if ( basePath == null ) {
      basePath = ROOT_GENERIC_PATH;
      assert basePath != null;
    } else if ( !owns( basePath ) ) {
      throw new NotFoundException( String.format( "Base path not found '%s'.", basePath ), basePath );
    }

    if ( !options.includesProviderType( TYPE ) ) {
      throw new NotFoundException( String.format( "Base path not found '%s'.", basePath ), basePath );
    }

    String repositoryFilterString = getRepositoryFilter( options.getFilter() );

    // TODO: FileService has a bug for depth=0, where an NPE is thrown due to tree.getChildren() being null.
    // So, until that's fixed, must send depth = 1 and then cut children on this side.
    Integer maxDepth = options.getMaxDepth();
    boolean isZeroDepth = maxDepth != null && maxDepth == 0;

    if ( isZeroDepth ) {
      maxDepth = 1;
    }

    String basePathString = pathToString( basePath );

    RepositoryFileTreeDto nativeTree = fileService.doGetTree(
      basePathString,
      maxDepth,
      repositoryFilterString,
      options.isIncludeHidden(),
      false,
      false );

    if ( nativeTree == null ) {
      try {
        if ( fileService.doesExist( basePathString ) ) {
          throw new OperationFailedException( String.format( "Unable to get the tree for base path '%s'.", basePath ) );
        }

        throw new NotFoundException( String.format( "Base path not found '%s'.", basePath ), basePath );
      } catch ( UnifiedRepositoryAccessDeniedException e ) {
        // The existence diagnostic itself was blocked by operation-wide repository access control.
        throw new AccessControlException( e );
      }
    }

    if ( isZeroDepth ) {
      nativeTree.setChildren( null );
    }

    // The parent path of the base path.
    String parentPathString = getParentPath( basePath );

    RepositoryFileTree tree = convertFromNativeFileTreeDto( nativeTree, parentPathString );

    if ( options.isIncludeMetadata() ) {
      setMetadataRecursively( tree );
    }

    return tree;
  }

  private void setMetadataRecursively( IGenericFileTree tree ) throws OperationFailedException {
    if ( tree == null ) {
      return;
    }

    if ( tree.getFile() instanceof BaseGenericFile node ) {
      try {
        node.setMetadata( getFileMetadata( GenericFilePath.parseRequired( node.getPath() ) ) );
      } catch ( InvalidPathException e ) {
        // A malformed DTO path cannot be queried for metadata; leave this node without metadata.
      }
    }

    if ( tree.getChildren() != null ) {
      for ( IGenericFileTree child : tree.getChildren() ) {
        setMetadataRecursively( child );
      }
    }
  }

  @NonNull
  @Override
  public IGenericFileContent getFileContent( @NonNull GenericFilePath path, boolean compressed )
    throws OperationFailedException {
    org.pentaho.platform.api.repository2.unified.RepositoryFile repositoryFile = getNativeFile( path );

    if ( !compressed && repositoryFile.isFolder() ) {
      throw new InvalidOperationException(
        "To get the content of a folder, the 'compressed' parameter must be set to true." );
    }

    try {
      if ( compressed ) {
        if ( !fileService.isPathValid( path.toString() ) ) {
          throw new InvalidOperationException( String.format( "Invalid path: '%s'.", path ) );
        }

        // This is called twice to validate the operation itself and the operation on the specific resource.
        if ( !SystemUtils.canDownload( path.toString() ) ) {
          if ( !SystemUtils.canDownload( null ) ) {
            throw new AccessControlException( "User is not authorized to perform this operation." );
          }

          throw new ResourceAccessDeniedException( "User is not authorized to get the content of this path.", path );
        }

        FileInputStream inputStream = getFileContentCompressedStream( repositoryFile );
        return new DefaultGenericFileContent( inputStream, repositoryFile.getName() + ".zip",
          MediaType.ZIP.toString() );
      }

      if ( !fileService.doGetCanGetFileContent( repositoryFile.getName() ) ) {
        throw new ResourceAccessDeniedException( "User is not authorized to get the content of this path.", path );
      }

      RepositoryFileInputStream inputStream = fileService.getRepositoryFileInputStream( repositoryFile );
      return new DefaultGenericFileContent( inputStream, repositoryFile.getName(), inputStream.getMimeType() );
    } catch ( FileNotFoundException e ) {
      // File vanished after lookup or its content stream is no longer readable.
      throw new NotFoundException( String.format( "Path not found '%s'.", path ), path, e );
    } catch ( ExportException | IOException e ) {
      // Archive generation or stream I/O failed after authorization completed.
      throw new OperationFailedException( e );
    }
  }

  @NonNull
  @Override
  public IGenericFile getFile( @NonNull GenericFilePath path, @NonNull GetFileOptions options )
    throws OperationFailedException {
    RepositoryObject file = convertFromNativeFile( getNativeFile( path ), getParentPath( path ) );

    if ( options.isIncludeMetadata() ) {
      file.setMetadata( getFileMetadata( path ) );
    }

    return file;
  }

  protected org.pentaho.platform.api.repository2.unified.RepositoryFile getNativeFile( @NonNull GenericFilePath path )
    throws OperationFailedException {
    Objects.requireNonNull( path );

    org.pentaho.platform.api.repository2.unified.RepositoryFile repositoryFile = null;

    if ( owns( path ) ) {
      try {
        repositoryFile = unifiedRepository.getFile( path.toString() );
      } catch ( UnifiedRepositoryAccessDeniedException e ) {
        // Operation-wide repository.read denial prevented the lookup.
        throw new AccessControlException( e );
      } catch ( UnifiedRepositoryException e ) {
        // Non-access repository failure prevented a file/not-found result.
        throw new OperationFailedException( e );
      }
    }

    if ( repositoryFile == null ) {
      throw new NotFoundException( String.format( "Path not found '%s'.", path ), path );
    }

    return repositoryFile;
  }

  protected void checkFileExists( @NonNull GenericFilePath path ) throws OperationFailedException {
    getNativeFile( path );
  }

  /**
   * Get the tree filter's corresponding repository filter
   */
  protected String getRepositoryFilter( GetTreeOptions.TreeFilter treeFilter ) {
    return switch ( treeFilter ) {
      case FOLDERS -> "*|FOLDERS";
      case FILES -> "*|FILES";
      default -> "*";
    };
  }

  @Override
  public boolean doesFolderExist( @NonNull GenericFilePath path ) throws OperationFailedException {
    try {
      return getNativeFile( path ).isFolder();
    } catch ( NotFoundException e ) {
      // Contract intentionally collapses missing and unreadable folders to false.
      return false;
    }
  }

  // region Conversion
  @NonNull
  private RepositoryObject createRepositoryObject( String name,
                                                   String path,
                                                   String title,
                                                   boolean isFolder,
                                                   @Nullable String parentPath ) {
    RepositoryObject repositoryObject = isFolder ? new RepositoryFolder() : new RepositoryFile();

    boolean isRoot = parentPath == null;

    if ( isRoot ) {
      assert isFolder;

      RepositoryFolder folder = (RepositoryFolder) repositoryObject;
      // Must match the first segment as parsed by GenericFilePath#parse.
      folder.setName( path );
      folder.setTitle( Messages.getString( "GenericFileRepository.REPOSITORY_FOLDER_DISPLAY" ) );
      folder.setCanEdit( false );
      folder.setCanDelete( false );
      folder.setCanAddChildren( false );
    } else {
      repositoryObject.setName( name );
      repositoryObject.setTitle( title );
      repositoryObject.setCanEdit( true );
      repositoryObject.setCanDelete( true );

      if ( repositoryObject.isFolder() ) {
        assert repositoryObject instanceof RepositoryFolder;
        ( (RepositoryFolder) repositoryObject ).setCanAddChildren( true );
      }
    }

    repositoryObject.setPath( path );
    repositoryObject.setParentPath( parentPath );

    return repositoryObject;
  }

  /**
   * Must be kept in sync with
   * {@link #convertFromNativeFile(org.pentaho.platform.api.repository2.unified.RepositoryFile, String)}.
   */
  @NonNull
  private RepositoryObject convertFromNativeFileDto( @NonNull RepositoryFileDto nativeFile,
                                                     @Nullable String parentPath ) {
    RepositoryObject repositoryObject = createRepositoryObject(
      nativeFile.getName(), nativeFile.getPath(), nativeFile.getTitle(), nativeFile.isFolder(), parentPath );

    repositoryObject.setModifiedDate( getModifiedDateFromNativeFileDto( nativeFile ) );
    repositoryObject.setObjectId( nativeFile.getId() );
    repositoryObject.setDescription( nativeFile.getDescription() );
    repositoryObject.setOwner( getOwnerFromNativeFileDto( nativeFile ) );
    repositoryObject.setCreatedDate( parseDate( nativeFile.getCreatedDate() ) );
    repositoryObject.setCreatorId( nativeFile.getCreatorId() );
    repositoryObject.setFileSize( nativeFile.getFileSize() );

    return repositoryObject;
  }

  protected RepositoryObject convertFromNativeFileDto( @NonNull RepositoryFileDto nativeFile ) {
    return convertFromNativeFileDto( nativeFile, getParentPath( nativeFile ) );
  }

  @Nullable
  private Date parseDate( String date ) {
    try {
      if ( !StringUtil.isEmpty( date ) ) {
        return repositoryWsDateAdapter.unmarshal( date );
      }
    } catch ( Exception e ) {
      // Invalid optional DTO dates are represented as absent rather than failing file conversion.
    }

    return null;
  }

  @Nullable
  private Date getModifiedDateFromNativeFileDto( @NonNull RepositoryFileDto nativeFile ) {
    Date lastModified = parseDate( nativeFile.getLastModifiedDate() );

    if ( lastModified != null ) {
      return lastModified;
    }

    return parseDate( nativeFile.getCreatedDate() );
  }

  private String getOwnerFromNativeFileDto( @NonNull RepositoryFileDto nativeFile ) {
    String owner = nativeFile.getOwner();

    if ( owner != null ) {
      return owner;
    }

    // Owner may not be available in the DTO depending on the service and/or parameters used.
    // So we need to fall back to the ACL to get the owner.
    return getOwnerByFileId( nativeFile.getId() );
  }

  @VisibleForTesting
  String getOwnerByFileId( String fileId ) {
    RepositoryFileAcl acl = unifiedRepository.getAcl( fileId );

    if ( acl != null ) {
      return acl.getOwner().getName();
    }

    return null;
  }

  @NonNull
  private RepositoryFileTree convertFromNativeFileTreeDto( @NonNull RepositoryFileTreeDto nativeTree,
                                                           @Nullable String parentPath ) {
    RepositoryObject repositoryObject = convertFromNativeFileDto( nativeTree.getFile(), parentPath );
    RepositoryFileTree repositoryTree = new RepositoryFileTree( repositoryObject );

    if ( nativeTree.getChildren() != null ) {
      // Ensure an empty list is reflected.
      repositoryTree.setChildren( new ArrayList<>() );

      String path = repositoryObject.getPath();

      for ( RepositoryFileTreeDto nativeChildTree : nativeTree.getChildren() ) {
        repositoryTree.addChild( convertFromNativeFileTreeDto( nativeChildTree, path ) );
      }
    }

    return repositoryTree;
  }

  /**
   * Must be kept in sync with {@link #convertFromNativeFileDto(RepositoryFileDto, String)}.
   */
  @NonNull
  protected RepositoryObject convertFromNativeFile(
    @NonNull org.pentaho.platform.api.repository2.unified.RepositoryFile nativeFile, @Nullable String parentPath ) {

    RepositoryObject repositoryObject = createRepositoryObject(
      nativeFile.getName(), nativeFile.getPath(), nativeFile.getTitle(), nativeFile.isFolder(), parentPath );

    repositoryObject.setModifiedDate(
      nativeFile.getLastModifiedDate() != null ? nativeFile.getLastModifiedDate() : nativeFile.getCreatedDate() );

    if ( nativeFile.getId() != null ) {
      String id = nativeFile.getId().toString();

      repositoryObject.setObjectId( id );
      repositoryObject.setOwner( getOwnerByFileId( id ) );
      repositoryObject.setCreatedDate( nativeFile.getCreatedDate() );
      repositoryObject.setCreatorId( nativeFile.getCreatorId() );
      repositoryObject.setFileSize( nativeFile.getFileSize() );
    }

    repositoryObject.setDescription( nativeFile.getDescription() );

    return repositoryObject;
  }

  @NonNull
  protected IGenericFileMetadata convertFromNativeFileMetadata( List<StringKeyStringValueDto> nativeMetadata ) {
    BaseGenericFileMetadata metadata = new BaseGenericFileMetadata();

    if ( nativeMetadata == null || nativeMetadata.isEmpty() ) {
      return metadata;
    }

    nativeMetadata.forEach( dto -> metadata.addMetadatum( dto.getKey(), dto.getValue() ) );

    return metadata;
  }

  @SuppressWarnings( { "java:S2589", "ConstantValue" } )
  @NonNull
  protected List<StringKeyStringValueDto> convertToNativeFileMetadata( IGenericFileMetadata metadata ) {
    if ( metadata == null ) {
      return Collections.emptyList();
    }

    Map<String, String> fileMetadata = metadata.getMetadata();

    if ( fileMetadata == null || fileMetadata.isEmpty() ) {
      return Collections.emptyList();
    }

    return fileMetadata.entrySet().stream()
      .map( fileMetadatum -> new StringKeyStringValueDto( fileMetadatum.getKey(), fileMetadatum.getValue() ) )
      .toList();
  }
  // endregion

  @Override
  public boolean owns( @NonNull GenericFilePath path ) {
    return path.getFirstSegment().equals( ROOT_PATH );
  }

  @Override
  public boolean hasAccess( @NonNull GenericFilePath path, @NonNull EnumSet<GenericFilePermission> permissions )
    throws OperationFailedException {
    try {
      return unifiedRepository.hasAccess( path.toString(), getRepositoryPermissions( permissions ) );
    } catch ( UnifiedRepositoryAccessDeniedException e ) {
      // Operation-wide denial; the resource permission query did not run.
      throw new AccessControlException( e );
    } catch ( UnifiedRepositoryException e ) {
      // Repository failed before producing true or false.
      throw new OperationFailedException( e );
    }
  }

  @SuppressWarnings( "BooleanMethodIsAlwaysInverted" )
  private boolean canWrite( @NonNull GenericFilePath path ) throws OperationFailedException {
    return hasAccess( path, EnumSet.of( GenericFilePermission.WRITE ) );
  }

  @SuppressWarnings( "BooleanMethodIsAlwaysInverted" )
  private boolean canDelete( @NonNull GenericFilePath path ) throws OperationFailedException {
    return hasAccess( path, EnumSet.of( GenericFilePermission.DELETE ) );
  }

  @SuppressWarnings( "BooleanMethodIsAlwaysInverted" )
  private boolean canManageAcl( @NonNull GenericFilePath path ) throws OperationFailedException {
    return hasAccess( path, EnumSet.of( GenericFilePermission.ACL_MANAGEMENT ) );
  }

  @SuppressWarnings( "BooleanMethodIsAlwaysInverted" )
  private boolean canWrite( @NonNull String path ) throws OperationFailedException {
    return canWrite( GenericFilePath.parseRequired( path ) );
  }

  @SuppressWarnings( "BooleanMethodIsAlwaysInverted" )
  private boolean canDelete( @NonNull String path ) throws OperationFailedException {
    return canDelete( GenericFilePath.parseRequired( path ) );
  }

  @Nullable
  private GenericFilePath findFirstNonWritablePath( @NonNull GenericFilePath path ) throws OperationFailedException {
    GenericFilePath current = path;

    // Walk upward from the target path until we find the closest existing ancestor.
    while ( current != null ) {
      if ( fileService.doesExist( pathToString( current ) ) ) {
        return canWrite( current ) ? null : current;
      }

      current = current.getParent();
    }

    return null;
  }

  private EnumSet<RepositoryFilePermission> getRepositoryPermissions( EnumSet<GenericFilePermission> permissions ) {
    EnumSet<RepositoryFilePermission> repositoryFilePermissions = EnumSet.noneOf( RepositoryFilePermission.class );

    for ( GenericFilePermission permission : permissions ) {
      switch ( permission ) {
        case READ:
          repositoryFilePermissions.add( RepositoryFilePermission.READ );
          break;
        case WRITE:
          repositoryFilePermissions.add( RepositoryFilePermission.WRITE );
          break;
        case DELETE:
          repositoryFilePermissions.add( RepositoryFilePermission.DELETE );
          break;
        case ACL_MANAGEMENT:
          repositoryFilePermissions.add( RepositoryFilePermission.ACL_MANAGEMENT );
          break;
        default:
          break;
      }
    }

    return repositoryFilePermissions;
  }

  @NonNull
  @Override
  public List<IGenericFile> getDeletedFiles() {
    return fileService.doGetDeletedFiles().stream()
      .map( fileDto -> {
        RepositoryObject repositoryObject = convertFromNativeFileDto( fileDto );

        repositoryObject.setOriginalLocation( getLocation( fileDto.getOriginalParentFolderPath() ) );
        repositoryObject.setDeletedBy( fileDto.getCreatorId() );
        repositoryObject.setDeletedDate( parseDate( fileDto.getDeletedDate() ) );

        return repositoryObject;
      } )
      .collect( Collectors.toList() );
  }

  @Override
  public void deleteFilePermanently( @NonNull GenericFilePath path ) throws OperationFailedException {
    String fileId = getTrashFileId( path );

    try {
      fileService.doDeleteFilesPermanent( fileId );
    } catch ( UnifiedRepositoryAccessDeniedException e ) {
      // URADE can identify operation-wide denial or DELETE denial on the trashed item.
      org.pentaho.platform.api.repository2.unified.RepositoryFile file = unifiedRepository.getFileById( fileId );

      if ( file != null && !canDelete( file.getPath() ) ) {
        throw new ResourceAccessDeniedException( String.format( "User is not authorized to delete '%s'.", path ), path,
          e );
      }

      throw new AccessControlException( e );
    } catch ( Exception e ) {
      // Non-access failure: follow-up lookup distinguishes a vanished item, not permission scope.
      checkNativeFileExistsById( fileId );
      throw new OperationFailedException( e );
    }
  }

  @Override
  public void deleteFile( @NonNull GenericFilePath path, boolean permanent ) throws OperationFailedException {
    String fileId = getFileId( path );

    try {
      if ( permanent ) {
        fileService.doDeleteFilesPermanent( fileId );
      } else {
        fileService.doDeleteFiles( fileId );
      }
    } catch ( UnifiedRepositoryAccessDeniedException e ) {
      // URADE can identify operation-wide denial or DELETE denial on the active item.
      org.pentaho.platform.api.repository2.unified.RepositoryFile file = unifiedRepository.getFileById( fileId );

      if ( file != null && !canDelete( file.getPath() ) ) {
        throw new ResourceAccessDeniedException( String.format( "User is not authorized to delete '%s'.", path ), path,
          e );
      }

      throw new AccessControlException( e );
    } catch ( Exception e ) {
      // Non-access failure: follow-up lookup distinguishes a vanished item, not permission scope.
      checkNativeFileExistsById( fileId );
      throw new OperationFailedException( e );
    }
  }

  @Override
  public void restoreFile( @NonNull GenericFilePath path ) throws OperationFailedException {
    String fileId = getTrashFileId( path );

    try {
      fileService.doRestoreFiles( fileId );
    } catch ( UnifiedRepositoryAccessDeniedException e ) {
      // URADE can identify operation-wide denial or WRITE denial on the restore target.
      org.pentaho.platform.api.repository2.unified.RepositoryFile file = unifiedRepository.getFileById( fileId );

      if ( file != null && !canWrite( file.getPath() ) ) {
        throw new ResourceAccessDeniedException( String.format( "User is not authorized to restore '%s'.", path ), path,
          e );
      }

      throw new AccessControlException( e );
    } catch ( InternalError e ) {
      // FileService collapses every non-URADE restore failure; only disappearance remains distinguishable.
      checkNativeFileExistsById( fileId );
      throw new OperationFailedException( e );
    }
  }

  @Override
  public boolean renameFile( @NonNull GenericFilePath path, @NonNull String newName ) throws OperationFailedException {
    if ( !Boolean.parseBoolean( fileService.doGetCanCreate() ) ) {
      throw new AccessControlException();
    }

    checkFileExists( path );

    if ( !fileService.isValidFileName( newName, true ) ) {
      throw new InvalidOperationException( String.format( "The new name '%s' is not valid.", newName ) );
    }

    String pathExtension = FilenameUtils.getExtension( path.getLastSegment() );
    String fullNewName =
      StringUtil.isEmpty( pathExtension ) ? newName : String.format( "%s.%s", newName, pathExtension );

    GenericFilePath newPath = getNewPath( Objects.requireNonNull( path.getParent() ), fullNewName );

    if ( fileService.doesExist( pathToString( newPath ) ) ) {
      throw new ConflictException(
        String.format( "Item to be renamed already exists on the destination folder: '%s'.", newName ) );
    }

    try {
      return fileService.doRename( pathToString( path ), newName );
    } catch ( UnifiedRepositoryAccessDeniedException e ) {
      // URADE can identify operation-wide denial or WRITE denial on the source.
      if ( !canWrite( path ) ) {
        throw new ResourceAccessDeniedException( String.format( "User is not authorized to rename '%s'.", path ), path,
          e );
      }

      throw new AccessControlException( e );
    } catch ( Exception e ) {
      // Rename failed without an access-denial signal.
      throw new OperationFailedException( e );
    }
  }

  @Override
  public void copyFile( @NonNull GenericFilePath path, @NonNull GenericFilePath destinationFolder )
    throws OperationFailedException {
    if ( !Boolean.parseBoolean( fileService.doGetCanCreate() ) ) {
      throw new AccessControlException();
    }

    if ( !getNativeFile( destinationFolder ).isFolder() ) {
      throw new InvalidOperationException( "The destination path is not a folder." );
    }

    GenericFilePath newPath = getNewPath( destinationFolder, path.getLastSegment() );

    if ( fileService.doesExist( pathToString( newPath ) ) ) {
      throw new ConflictException(
        String.format( "File to be copied already exists on the destination folder: '%s'.", newPath ) );
    }

    String fileId = getFileId( path );

    try {
      fileService.doCopyFiles( pathToString( destinationFolder ), FileService.MODE_RENAME, fileId );
    } catch ( UnifiedRepositoryAccessDeniedException e ) {
      // URADE can come from operation-wide checks or source/destination/ACL/metadata access.
      checkFileExists( path );

      if ( !canWrite( destinationFolder ) ) {
        throw new ResourceAccessDeniedException(
          String.format( "User is not authorized to write to '%s'.", destinationFolder ), destinationFolder, e );
      }

      throw new AccessControlException( e );
    } catch ( UnifiedRepositoryException | IllegalArgumentException e ) {
      // Non-access repository or copy-validation failure.
      throw new OperationFailedException( e );
    }
  }

  @Override
  public void moveFile( @NonNull GenericFilePath path, @NonNull GenericFilePath destinationFolder )
    throws OperationFailedException {
    if ( !Boolean.parseBoolean( fileService.doGetCanCreate() ) ) {
      throw new AccessControlException();
    }

    if ( !getNativeFile( destinationFolder ).isFolder() ) {
      throw new InvalidOperationException( "The destination path is not a folder." );
    }

    GenericFilePath newPath = getNewPath( destinationFolder, path.getLastSegment() );

    if ( fileService.doesExist( pathToString( newPath ) ) ) {
      throw new ConflictException(
        String.format( "File to be moved already exists on the destination folder: '%s'.", path ) );
    }

    String fileId = getFileId( path );

    try {
      fileService.doMoveFiles( pathToString( destinationFolder ), fileId );
    } catch ( FileNotFoundException e ) {
      // FileService explicitly reports a missing destination folder.
      throw new NotFoundException( String.format( "Destination folder not found '%s'.", destinationFolder ),
        destinationFolder, e );
    } catch ( UnifiedRepositoryAccessDeniedException e ) {
      // URADE can identify operation-wide denial or WRITE denial on source or destination.
      checkFileExists( path );

      if ( !canDelete( path ) ) {
        throw new ResourceAccessDeniedException( String.format( "User is not authorized to move '%s'.", path ), path,
          e );
      }

      GenericFilePath sourceParent = path.getParent();
      if ( sourceParent != null && !canWrite( sourceParent ) ) {
        throw new ResourceAccessDeniedException(
          String.format( "User is not authorized to remove a child from '%s'.", sourceParent ), sourceParent, e );
      }

      if ( !canWrite( destinationFolder ) ) {
        throw new ResourceAccessDeniedException(
          String.format( "User is not authorized to write to '%s'.", destinationFolder ), destinationFolder, e );
      }

      throw new AccessControlException( e );
    } catch ( InternalError | IllegalArgumentException e ) {
      // FileService collapsed a non-access repository failure, or rejected move arguments.
      throw new OperationFailedException( e );
    }
  }

  @NonNull
  @Override
  public IGenericFileMetadata getFileMetadata( @NonNull GenericFilePath path ) throws OperationFailedException {
    try {
      return convertFromNativeFileMetadata( fileService.doGetMetadata( pathToString( path ) ) );
    } catch ( UnifiedRepositoryAccessDeniedException e ) {
      // Repository access control prevented metadata retrieval before a result was produced.
      throw new AccessControlException( e );
    } catch ( UnifiedRepositoryException e ) {
      // Non-access repository failure prevented metadata retrieval.
      throw new OperationFailedException( e );
    } catch ( FileNotFoundException e ) {
      // FileService explicitly reports a missing or unreadable path.
      throw new NotFoundException( String.format( "Path not found '%s'.", path ), path, e );
    }
  }

  @Override
  public void setFileMetadata( @NonNull GenericFilePath path, @NonNull IGenericFileMetadata metadata )
    throws OperationFailedException {
    checkFileExists( path );

    try {
      fileService.doSetMetadata( pathToString( path ), convertToNativeFileMetadata( metadata ) );
    } catch ( UnifiedRepositoryAccessDeniedException e ) {
      // URADE can identify operation-wide denial or WRITE denial on the target.
      if ( !canWrite( path ) ) {
        throw new ResourceAccessDeniedException( String.format( "User is not authorized to write to '%s'.", path ),
          path, e );
      }

      throw new AccessControlException( e );
    } catch ( GeneralSecurityException e ) {
      // FileService's metadata-specific authorization check rejected the operation.
      throw new AccessControlException( "User is not authorized to perform this operation." );
    } catch ( UnifiedRepositoryException e ) {
      // Non-access repository failure occurred while reading or writing metadata.
      throw new OperationFailedException( e );
    }
  }

  @NonNull
  @Override
  public IGenericFileAcl getFileAcl( @NonNull GenericFilePath path, boolean forceInheriting )
    throws OperationFailedException {
    // Check existence before trying to get ACL to ensure correct exception is thrown.
    checkFileExists( path );

    try {
      return convertFromNativeFileAcl( fileService.doGetFileAcl( pathToString( path ), forceInheriting ) );
    } catch ( UnifiedRepositoryAccessDeniedException e ) {
      // Repository access control prevented ACL retrieval; FileService exposes no narrower scope.
      throw new AccessControlException( e );
    } catch ( InvalidOperationException e ) {
      // Preserve the GFS conversion error produced for an unsupported native ACL.
      throw e;
    } catch ( Exception e ) {
      // Any other ACL retrieval or conversion failure is non-access and operation-wide.
      throw new OperationFailedException( e );
    }
  }

  @Override
  public void setFileAcl( @NonNull GenericFilePath path, @NonNull IGenericFileAcl acl )
    throws OperationFailedException {
    // Validate the ACL before trying to set it to ensure correct exception is thrown in case of invalid ACL.
    if ( !validateFileAcl( acl ) ) {
      throw new InvalidOperationException(
        "The ACL is invalid. It may contain invalid users or roles, empty or missing entries, or entries with empty "
          + "permissions." );
    }

    String pathString = pathToString( path );

    try {
      fileService.setFileAcls( pathString, convertToNativeFileAcl( acl ) );
    } catch ( FileNotFoundException e ) {
      // FileService explicitly reports a missing or unreadable target.
      throw new NotFoundException( String.format( "Path not found '%s'.", path ), path, e );
    } catch ( UnifiedRepositoryAccessDeniedException e ) {
      // URADE can identify operation-wide denial or ACL_MANAGEMENT denial on the target.
      if ( fileService.doesExist( pathString ) && !canManageAcl( path ) ) {
        throw new ResourceAccessDeniedException(
          String.format( "User is not authorized to manage the ACL of '%s'.", path ), path, e );
      }

      throw new AccessControlException( e );
    } catch ( Exception e ) {
      // ACL update failed without an access-denial signal.
      throw new OperationFailedException( e );
    }
  }

  protected String pathToString( @NonNull GenericFilePath path ) {
    Objects.requireNonNull( path );
    return encodeRepositoryPath( path.toString() );
  }

  protected GenericFilePath getNewPath( @NonNull GenericFilePath path, @NonNull String newName )
    throws InvalidPathException {
    Objects.requireNonNull( path );
    Objects.requireNonNull( newName );

    return path.child( newName );
  }

  protected FileInputStream getFileContentCompressedStream(
    org.pentaho.platform.api.repository2.unified.RepositoryFile repositoryFile ) throws IOException, ExportException {
    BaseExportProcessor exportProcessor =
      new ZipExportProcessor( repositoryFile.getPath(), fileService.getRepository(), true );
    exportProcessor.addExportHandler( getPentahoExportHandler() );

    return new FileInputStream( exportProcessor.performExport( repositoryFile ) );
  }

  protected ExportHandler getPentahoExportHandler() {
    return PentahoSystem.get( DefaultExportHandler.class );
  }

  protected IRepositoryContentConverterHandler getContentConverterHandler() {
    return PentahoSystem.get( IRepositoryContentConverterHandler.class );
  }

  protected String getFileId( @NonNull GenericFilePath path ) throws OperationFailedException {
    return getNativeFile( path ).getId().toString();
  }

  protected void checkNativeFileExistsById( @NonNull String fileId ) throws NotFoundException {
    final var file = unifiedRepository.getFileById( fileId );

    if ( file == null ) {
      throw new NotFoundException( String.format( "Path not found '%s'.", fileId ) );
    }
  }

  protected org.pentaho.platform.api.repository2.unified.RepositoryFile getOrCreateNativeFolder(
    @NonNull GenericFilePath path ) throws OperationFailedException {
    org.pentaho.platform.api.repository2.unified.RepositoryFile folder;

    try {
      folder = getNativeFile( path );
    } catch ( NotFoundException e ) {
      // Missing ancestors are auto-created as required by the create-file contract.
      if ( createFolderCore( path ) ) {
        folder = getNativeFile( path );
      } else {
        throw new NotFoundException( String.format( "Unable to create folder '%s'.", path ), path );
      }
    }

    if ( !folder.isFolder() ) {
      throw new InvalidOperationException( "Path is not a folder." );
    }

    return folder;
  }

  protected String getTrashFileId( @NonNull GenericFilePath path ) throws InvalidPathException, NotFoundException {
    Objects.requireNonNull( path );
    boolean isTrash = false;
    List<String> segments = path.getSegments();

    for ( int i = 0; i < segments.size(); i++ ) {
      if ( FOLDER_NAME_TRASH.equals( segments.get( i ) ) && i + 1 < segments.size() ) {
        isTrash = true;
        String segment = segments.get( i + 1 );
        int colonIndex = segment.indexOf( ':' );

        if ( colonIndex != -1 ) {
          return segment.substring( colonIndex + 1 );
        }
      }
    }

    if ( isTrash ) {
      throw new InvalidPathException( "File ID not found in the path." );
    }

    throw new NotFoundException( "The path does not correspond to a deleted file.", path );
  }

  private String getParentPath( RepositoryFileDto fileDto ) {
    try {
      return getParentPath( GenericFilePath.parseRequired( fileDto.getPath() ) );
    } catch ( InvalidPathException e ) {
      // Malformed DTO paths have no representable GFS parent.
    }

    return null;
  }

  private String getParentPath( GenericFilePath path ) {
    Objects.requireNonNull( path );
    GenericFilePath parentPath = path.getParent();

    return parentPath != null ? parentPath.toString() : null;
  }

  private List<IGenericFile> getLocation( String path ) {
    GenericFilePath locationPath = null;

    try {
      locationPath = GenericFilePath.parseRequired( path );
    } catch ( InvalidPathException e ) {
      // Malformed original locations cannot contribute hierarchy entries.
    }

    List<IGenericFile> location = new ArrayList<>();

    while ( locationPath != null ) {
      IGenericFile folder;

      try {
        folder = getFile( locationPath, new GetFileOptions() );
      } catch ( OperationFailedException e ) {
        // Deleted or otherwise unavailable ancestors are represented by synthetic location entries.
        String parentPath = getParentPath( locationPath );
        String name = locationPath.getLastSegment();

        folder = createRepositoryObject( name, locationPath.toString(), null, true, parentPath );
      }

      location.add( folder );
      locationPath = locationPath.getParent();
    }

    Collections.reverse( location );

    return location;
  }

  /**
   * Checks if the given ACL is valid, i.e. users and roles don't contain illegal characters.
   * Illegal characters may lead to repository corruption.
   * <p>
   * RFC 2253 - The names of security principal objects can contain all Unicode characters except the special LDAP
   * characters defined in RFC 2253. This list of special characters includes: a leading space; a trailing space;
   * and any of the following characters: # , + " \ < > ; =
   *
   * @param acl the ACL containing the permissions we want to set
   * @return true if the ACL is valid, i.e. all users and roles are valid, false otherwise
   */
  @Override
  public boolean validateFileAcl( @NonNull IGenericFileAcl acl ) {
    // validate the ACL owner first
    if ( !validateSecurityPrincipal( acl.getOwner() ) ) {
      return false;
    }

    // If entries are inheriting, no need to validate entries
    if ( acl.isEntriesInheriting() ) {
      return true;
    }

    // ACL must contain at least one entry for all owners, including admin
    if ( acl.getEntries() == null || acl.getEntries().isEmpty() ) {
      return false;
    }

    // then check the ACL recipients
    for ( IGenericFileAce entry : acl.getEntries() ) {
      if ( !validateSecurityPrincipal( entry.getRecipient() ) ) {
        return false;
      }

      if ( entry.getPermissions().isEmpty() ) {
        return false;
      }
    }

    // if all users and roles are syntactically valid we end up here!
    return true;
  }

  /**
   * Checks if all characters in the security principal name are valid.
   *
   * @param principal The security principal to validate
   * @return true if all characters are valid.
   */
  @SuppressWarnings( "BooleanMethodIsAlwaysInverted" )
  protected boolean validateSecurityPrincipal( String principal ) {
    if ( isBlank( principal ) ) {
      return false;
    }

    // RFC 2253: leading and trailing spaces are illegal in security principal names.
    if ( !principal.equals( principal.trim() ) ) {
      return false;
    }

    return !INVALID_SECURITY_PRINCIPAL_PATTERN.matcher( principal ).find();
  }

  /**
   * Converts a native principal type (int) to {@link GenericFilePrincipalType} enum.
   * Uses enum ordinal indices for efficient mapping.
   *
   * @param nativeType the native principal type as int (0=USER, 1=ROLE)
   * @return the corresponding {@link GenericFilePrincipalType}, or throw exception if unknown
   * @throws InvalidOperationException if the type is unknown
   */
  @NonNull
  protected GenericFilePrincipalType convertFromNativePrincipalType( int nativeType ) throws InvalidOperationException {
    if ( nativeType < 0 || nativeType >= GenericFilePrincipalType.values().length ) {
      throw new InvalidOperationException( "Unknown principal type: " + nativeType );
    }

    return GenericFilePrincipalType.values()[ nativeType ];
  }

  /**
   * Converts a native permission (int) to {@link GenericFilePermission} enum.
   * <p>
   * Native permission {@code 4} (ALL) is mapped to {@link GenericFilePermission#ACL_MANAGEMENT},
   * as the ALL permission has been removed from the generic file permission model.
   *
   * @param nativePermission the native permission as int
   * @return the corresponding {@link GenericFilePermission}, or throw exception if unknown
   * @throws InvalidOperationException if the permission is unknown
   */
  @NonNull
  protected GenericFilePermission convertFromNativePermission( int nativePermission ) throws InvalidOperationException {
    // Native permission 4 (ALL) is mapped to ACL_MANAGEMENT.
    if ( nativePermission == 4 ) {
      return GenericFilePermission.ACL_MANAGEMENT;
    }

    if ( nativePermission < 0 || nativePermission >= GenericFilePermission.values().length ) {
      throw new InvalidOperationException( "Unknown permission: " + nativePermission );
    }

    return GenericFilePermission.values()[ nativePermission ];
  }

  /**
   * Converts a {@link GenericFilePrincipalType} enum to native principal type (int).
   * Uses enum ordinal value for efficient mapping.
   *
   * @param principalType the {@link GenericFilePrincipalType}
   * @return the ordinal value as int (0=USER, 1=ROLE).
   */
  protected int convertToNativePrincipalType( @NonNull GenericFilePrincipalType principalType ) {
    return principalType.ordinal();
  }

  /**
   * Converts a {@link GenericFilePermission} enum to native permission (int).
   * Uses enum ordinal value for efficient mapping.
   *
   * @param permission the {@link GenericFilePermission}
   * @return the ordinal value as int.
   */
  protected int convertToNativePermission( @NonNull GenericFilePermission permission ) {
    return permission.ordinal();
  }

  @NonNull
  protected IGenericFileAcl convertFromNativeFileAcl( @NonNull RepositoryFileAclDto nativeAcl )
    throws InvalidOperationException {
    List<IGenericFileAce> aces = null;

    if ( nativeAcl.getAces() != null ) {
      aces = new ArrayList<>();

      for ( RepositoryFileAclAceDto nativeEntry : nativeAcl.getAces() ) {
        aces.add( convertFromNativeFileAclEntry( nativeEntry ) );
      }
    }

    return new BaseGenericFileAcl( nativeAcl.getOwner(),
      convertFromNativePrincipalType( nativeAcl.getOwnerType() ),
      nativeAcl.isEntriesInheriting(),
      aces );
  }

  @NonNull
  protected IGenericFileAce convertFromNativeFileAclEntry( @NonNull RepositoryFileAclAceDto nativeEntry )
    throws InvalidOperationException {
    List<GenericFilePermission> permissions = new ArrayList<>();

    if ( nativeEntry.getPermissions() != null ) {
      for ( Integer nativePermission : nativeEntry.getPermissions() ) {
        permissions.add( convertFromNativePermission( nativePermission ) );
      }
    }

    return new BaseGenericFileAce( nativeEntry.getRecipient(),
      convertFromNativePrincipalType( nativeEntry.getRecipientType() ),
      nativeEntry.isModifiable(),
      permissions );
  }

  @NonNull
  protected RepositoryFileAclDto convertToNativeFileAcl( @NonNull IGenericFileAcl acl ) {
    RepositoryFileAclDto nativeAcl = new RepositoryFileAclDto();

    nativeAcl.setOwner( acl.getOwner() );
    nativeAcl.setOwnerType( convertToNativePrincipalType( acl.getOwnerType() ) );

    // This cannot be null and cannot be immutable because the service expects a mutable list, but it can be empty if
    // entries are inheriting.
    List<RepositoryFileAclAceDto> nativeAces = new ArrayList<>();

    if ( acl.getEntries() != null ) {
      for ( IGenericFileAce nativeEntry : acl.getEntries() ) {
        nativeAces.add( convertToNativeFileAclEntry( nativeEntry ) );
      }
    }

    nativeAcl.setAces( nativeAces, acl.isEntriesInheriting() );

    return nativeAcl;
  }

  @SuppressWarnings( "java:S6204" )
  @NonNull
  protected RepositoryFileAclAceDto convertToNativeFileAclEntry( @NonNull IGenericFileAce entry ) {
    RepositoryFileAclAceDto nativeEntry = new RepositoryFileAclAceDto();

    nativeEntry.setRecipient( entry.getRecipient() );
    nativeEntry.setRecipientType( convertToNativePrincipalType( entry.getRecipientType() ) );
    nativeEntry.setModifiable( entry.isModifiable() );
    nativeEntry.setPermissions(
      entry.getPermissions().stream().map( this::convertToNativePermission ).collect( Collectors.toList() ) );

    return nativeEntry;
  }
}
