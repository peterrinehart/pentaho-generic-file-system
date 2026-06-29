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

package org.pentaho.platform.api.genericfile;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.pentaho.platform.api.genericfile.exception.InvalidPathException;
import org.pentaho.platform.api.genericfile.exception.OperationFailedException;
import org.pentaho.platform.api.genericfile.model.CreateFileOptions;
import org.pentaho.platform.api.genericfile.model.IGenericFile;
import org.pentaho.platform.api.genericfile.model.IGenericFileAcl;
import org.pentaho.platform.api.genericfile.model.IGenericFileContent;
import org.pentaho.platform.api.genericfile.model.IGenericFileMetadata;
import org.pentaho.platform.api.genericfile.model.IGenericFileTree;

import java.io.InputStream;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests the default methods of the {@link IGenericFileService} interface.
 */
class IGenericFileServiceTest {
  static class GenericFileServiceForTesting implements IGenericFileService {
    @Override
    public void clearTreeCache() {
      // this is empty on purpose
    }

    @NonNull
    @Override
    public IGenericFileTree getTree( @NonNull GetTreeOptions options ) throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public List<IGenericFileTree> getRootTrees( @NonNull GetTreeOptions options ) throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean doesFolderExist( @NonNull GenericFilePath path ) throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasAccess( @NonNull GenericFilePath path, @NonNull EnumSet<GenericFilePermission> permissions )
      throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean createFolder( @NonNull GenericFilePath path ) throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void createFile( @NonNull GenericFilePath path,
                            @NonNull InputStream content,
                            @NonNull CreateFileOptions createFileOptions )
      throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setFileContent( @NonNull GenericFilePath path, @NonNull InputStream content )
      throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public IGenericFileContent getFileContent( @NonNull GenericFilePath path, boolean compressed )
      throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public IGenericFile getFile( @NonNull GenericFilePath path ) throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public IGenericFile getFile( @NonNull GenericFilePath path, @NonNull GetFileOptions options )
      throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public List<IGenericFile> getDeletedFiles() throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteFilesPermanently( @NonNull List<GenericFilePath> paths ) throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteFilePermanently( @NonNull GenericFilePath path ) throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteFiles( @NonNull List<GenericFilePath> paths, boolean permanent ) throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteFile( @NonNull GenericFilePath path, boolean permanent ) throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void restoreFiles( @NonNull List<GenericFilePath> paths ) throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void restoreFile( @NonNull GenericFilePath path ) throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean renameFile( @NonNull GenericFilePath path, @NonNull String newName )
      throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void copyFiles( @NonNull List<GenericFilePath> files, @NonNull GenericFilePath destinationFolder )
      throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void copyFile( @NonNull GenericFilePath file, @NonNull GenericFilePath destinationFolder )
      throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void moveFiles( @NonNull List<GenericFilePath> files, @NonNull GenericFilePath destinationFolder )
      throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void moveFile( @NonNull GenericFilePath file, @NonNull GenericFilePath destinationFolder )
      throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public IGenericFileMetadata getFileMetadata( @NonNull GenericFilePath path ) throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setFileMetadata( @NonNull GenericFilePath path, @NonNull IGenericFileMetadata metadata )
      throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public IGenericFileAcl getFileAcl( @NonNull GenericFilePath path, boolean forceInheriting )
      throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setFileAcl( @NonNull GenericFilePath path, @NonNull IGenericFileAcl acl )
      throws OperationFailedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean validateFileAcl( @NonNull GenericFilePath path, @NonNull IGenericFileAcl acl )
      throws OperationFailedException {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Tests for the {@link IGenericFileService#doesFolderExist(String)} method.
   */
  @Nested
  class DoesFileExistTests {
    @Test
    void testValidStringPathIsAccepted() throws OperationFailedException {
      GenericFileServiceForTesting service = spy( new GenericFileServiceForTesting() );

      ArgumentCaptor<GenericFilePath> pathCaptor = ArgumentCaptor.forClass( GenericFilePath.class );

      doReturn( true ).when( service ).doesFolderExist( any( GenericFilePath.class ) );

      assertTrue( service.doesFolderExist( "/foo" ) );

      verify( service, times( 1 ) ).doesFolderExist( pathCaptor.capture() );
      GenericFilePath path = pathCaptor.getValue();
      assertNotNull( path );
      assertEquals( "/foo", path.toString() );
    }

    @Test
    void testEmptyStringPathThrowsInvalidPathException() {
      GenericFileServiceForTesting service = new GenericFileServiceForTesting();

      assertThrows( InvalidPathException.class, () -> service.doesFolderExist( "" ) );
    }

    @Test
    void testInvalidStringPathThrowsInvalidPathException() {
      GenericFileServiceForTesting service = new GenericFileServiceForTesting();

      assertThrows( InvalidPathException.class, () -> service.doesFolderExist( "foo" ) );
    }
  }

  /**
   * Tests for the {@link IGenericFileService#createFolder(String)} method.
   */
  @Nested
  class CreateFolderTests {
    @Test
    void testValidStringPathIsAccepted() throws OperationFailedException {
      GenericFileServiceForTesting service = spy( new GenericFileServiceForTesting() );
      ArgumentCaptor<GenericFilePath> pathCaptor = ArgumentCaptor.forClass( GenericFilePath.class );

      doReturn( true ).when( service ).createFolder( any( GenericFilePath.class ) );

      assertTrue( service.createFolder( "/foo" ) );

      verify( service, times( 1 ) ).createFolder( pathCaptor.capture() );
      GenericFilePath path = pathCaptor.getValue();
      assertNotNull( path );
      assertEquals( "/foo", path.toString() );
    }

    @Test
    void testEmptyStringPathThrowsInvalidPathException() {
      GenericFileServiceForTesting service = new GenericFileServiceForTesting();

      assertThrows( InvalidPathException.class, () -> service.createFolder( "" ) );
    }

    @Test
    void testInvalidStringPathThrowsInvalidPathException() {
      GenericFileServiceForTesting service = new GenericFileServiceForTesting();

      assertThrows( InvalidPathException.class, () -> service.createFolder( "foo" ) );
    }
  }

  /**
   * Tests for the {@link IGenericFileService#createFile(String, InputStream, CreateFileOptions)} method.
   */
  @Nested
  class CreateFileTests {
    @Test
    void testValidStringPathIsAccepted() throws OperationFailedException {
      GenericFileServiceForTesting service = spy( new GenericFileServiceForTesting() );
      ArgumentCaptor<GenericFilePath> pathCaptor = ArgumentCaptor.forClass( GenericFilePath.class );
      ArgumentCaptor<CreateFileOptions> createFileOptionsArgumentCaptor =
        ArgumentCaptor.forClass( CreateFileOptions.class );
      ArgumentCaptor<InputStream> contentCaptor = ArgumentCaptor.forClass( InputStream.class );

      InputStream mockContent = new java.io.ByteArrayInputStream( "test content".getBytes() );
      CreateFileOptions options = new CreateFileOptions();
      options.setOverwrite( true );

      doNothing().when( service )
        .createFile( any( GenericFilePath.class ), any( InputStream.class ), any( CreateFileOptions.class ) );

      service.createFile( "/foo/test.txt", mockContent, options );

      verify( service, times( 1 ) ).createFile( pathCaptor.capture(), contentCaptor.capture(),
        createFileOptionsArgumentCaptor.capture() );
      GenericFilePath path = pathCaptor.getValue();
      assertNotNull( path );
      assertEquals( "/foo/test.txt", path.toString() );
      assertEquals( true, createFileOptionsArgumentCaptor.getValue().isOverwrite() );
      assertEquals( mockContent, contentCaptor.getValue() );
    }

    @Test
    void testEmptyStringPathThrowsInvalidPathException() {
      GenericFileServiceForTesting service = new GenericFileServiceForTesting();
      InputStream mockContent = new java.io.ByteArrayInputStream( "test content".getBytes() );
      CreateFileOptions options = new CreateFileOptions();

      assertThrows( InvalidPathException.class, () -> service.createFile( "", mockContent, options ) );
    }

    @Test
    void testInvalidStringPathThrowsInvalidPathException() {
      GenericFileServiceForTesting service = new GenericFileServiceForTesting();
      InputStream mockContent = new java.io.ByteArrayInputStream( "test content".getBytes() );
      CreateFileOptions options = new CreateFileOptions();

      assertThrows( InvalidPathException.class, () -> service.createFile( "foo", mockContent, options ) );
    }
  }
}
