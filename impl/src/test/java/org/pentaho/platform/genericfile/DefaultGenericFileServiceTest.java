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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.pentaho.platform.api.genericfile.GenericFilePath;
import org.pentaho.platform.api.genericfile.GetFileOptions;
import org.pentaho.platform.api.genericfile.GetTreeOptions;
import org.pentaho.platform.api.genericfile.IGenericFileDecorator;
import org.pentaho.platform.api.genericfile.IGenericFileProvider;
import org.pentaho.platform.api.genericfile.exception.BatchOperationFailedException;
import org.pentaho.platform.api.genericfile.exception.ConflictException;
import org.pentaho.platform.api.genericfile.exception.InvalidGenericFileProviderException;
import org.pentaho.platform.api.genericfile.exception.InvalidPathException;
import org.pentaho.platform.api.genericfile.exception.NotFoundException;
import org.pentaho.platform.api.genericfile.exception.OperationFailedException;
import org.pentaho.platform.api.genericfile.model.CreateFileOptions;
import org.pentaho.platform.api.genericfile.model.IGenericFile;
import org.pentaho.platform.api.genericfile.model.IGenericFileAcl;
import org.pentaho.platform.api.genericfile.model.IGenericFileContent;
import org.pentaho.platform.api.genericfile.model.IGenericFileMetadata;
import org.pentaho.platform.api.genericfile.model.IGenericFileTree;
import org.pentaho.platform.genericfile.decorators.CompositeGenericFileDecorator;
import org.pentaho.platform.genericfile.decorators.NullGenericFileDecorator;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultGenericFileServiceTest {
  // region Construction Tests
  @Test
  void testThrowsInvalidGenericFileProviderExceptionIfCreatedWithEmptyProviderList() {
    List<IGenericFileProvider<?>> providers = new ArrayList<>();
    assertThrows( InvalidGenericFileProviderException.class, () -> new DefaultGenericFileService( providers ) );
  }

  @Test
  void testCanBeCreatedWithNoDecorator()
    throws InvalidGenericFileProviderException, NoSuchFieldException, IllegalAccessException {
    IGenericFileProvider<?> providerMock = mock( IGenericFileProvider.class );

    DefaultGenericFileService service = new DefaultGenericFileService( Collections.singletonList( providerMock ) );

    Field decoratorField = DefaultGenericFileService.class.getDeclaredField( "fileDecorator" );
    decoratorField.setAccessible( true );
    Object actualDecorator = decoratorField.get( service );

    assertInstanceOf( NullGenericFileDecorator.class, actualDecorator );
  }

  @Test
  void testCanBeCreatedWithASingleProvider() throws InvalidGenericFileProviderException {
    IGenericFileProvider<?> providerMock = mock( IGenericFileProvider.class );

    DefaultGenericFileService service = new DefaultGenericFileService( Collections.singletonList( providerMock ) );

    assertTrue( service.isSingleProviderMode() );
  }

  @Test
  void testCanBeCreatedWithASingleDecorator()
    throws InvalidGenericFileProviderException, NoSuchFieldException, IllegalAccessException {
    IGenericFileProvider<?> providerMock = mock( IGenericFileProvider.class );
    IGenericFileDecorator decoratorMock = mock( IGenericFileDecorator.class );

    DefaultGenericFileService service =
      new DefaultGenericFileService( Collections.singletonList( providerMock ), decoratorMock );

    Field decoratorField = DefaultGenericFileService.class.getDeclaredField( "fileDecorator" );
    decoratorField.setAccessible( true );
    Object actualDecorator = decoratorField.get( service );

    assertSame( decoratorMock, actualDecorator );
    assertInstanceOf( IGenericFileDecorator.class, actualDecorator );
  }

  @Test
  void testCanBeCreatedWithTwoProviders() throws InvalidGenericFileProviderException {
    IGenericFileProvider<?> provider1Mock = mock( IGenericFileProvider.class );
    IGenericFileProvider<?> provider2Mock = mock( IGenericFileProvider.class );

    DefaultGenericFileService service = new DefaultGenericFileService( Arrays.asList( provider1Mock, provider2Mock ) );
    assertFalse( service.isSingleProviderMode() );
  }

  @Test
  void testCanBeCreatedWithTwoDecorators()
    throws InvalidGenericFileProviderException, NoSuchFieldException, IllegalAccessException {
    IGenericFileProvider<?> providerMock = mock( IGenericFileProvider.class );
    IGenericFileDecorator decorator1Mock = mock( IGenericFileDecorator.class );
    IGenericFileDecorator decorator2Mock = mock( IGenericFileDecorator.class );

    CompositeGenericFileDecorator compositeGenericFileDecorator =
      new CompositeGenericFileDecorator( Arrays.asList( decorator1Mock, decorator2Mock ) );

    DefaultGenericFileService service = new DefaultGenericFileService( Collections.singletonList( providerMock ),
      compositeGenericFileDecorator );

    Field decoratorField = DefaultGenericFileService.class.getDeclaredField( "fileDecorator" );
    decoratorField.setAccessible( true );
    Object actualDecorator = decoratorField.get( service );

    assertSame( compositeGenericFileDecorator, actualDecorator );
    assertInstanceOf( CompositeGenericFileDecorator.class, actualDecorator );
  }
  // endregion

  private static class MultipleProviderUseCase {
    public final IGenericFileProvider<?> provider1Mock;
    public final IGenericFileProvider<?> provider2Mock;
    public final DefaultGenericFileService service;

    public MultipleProviderUseCase() throws InvalidGenericFileProviderException {
      provider1Mock = mock( IGenericFileProvider.class );

      provider2Mock = mock( IGenericFileProvider.class );

      service = new DefaultGenericFileService( Arrays.asList( provider1Mock, provider2Mock ) );
    }
  }

  // region getTree()
  private static class GetTreeMultipleProviderUseCase extends MultipleProviderUseCase {
    public final IGenericFileTree tree1Mock;
    public final IGenericFileTree tree2Mock;
    public final GetTreeOptions optionsMock;

    public GetTreeMultipleProviderUseCase() throws OperationFailedException, InvalidGenericFileProviderException {
      tree1Mock = mock( IGenericFileTree.class );
      doReturn( tree1Mock ).when( provider1Mock ).getTree( any( GetTreeOptions.class ) );

      tree2Mock = mock( IGenericFileTree.class );
      doReturn( tree2Mock ).when( provider2Mock ).getTree( any( GetTreeOptions.class ) );

      optionsMock = mock( GetTreeOptions.class );
    }
  }

  @Test
  void testGetTreeWithSingleProviderReturnsProviderTreeDirectly()
    throws InvalidGenericFileProviderException, OperationFailedException {
    IGenericFileProvider<?> providerMock = mock( IGenericFileProvider.class );

    IGenericFileTree treeMock = mock( IGenericFileTree.class );
    when( providerMock.getTree( any( GetTreeOptions.class ) ) ).thenReturn( treeMock );

    DefaultGenericFileService service = new DefaultGenericFileService( Collections.singletonList( providerMock ) );
    GetTreeOptions optionsMock = mock( GetTreeOptions.class );

    IGenericFileTree resultTree = service.getTree( optionsMock );

    assertEquals( treeMock, resultTree );
  }

  @Test
  void testGetTreeWithMultipleProvidersAndNullBasePathAggregatesProviderTrees()
    throws InvalidGenericFileProviderException, OperationFailedException {
    GetTreeMultipleProviderUseCase useCase = new GetTreeMultipleProviderUseCase();

    IGenericFileTree aggregateTree = useCase.service.getTree( useCase.optionsMock );

    // ---

    assertNotNull( aggregateTree );
    assertNotSame( useCase.tree1Mock, aggregateTree );
    assertNotSame( useCase.tree2Mock, aggregateTree );

    // Test Aggregate Root File
    IGenericFile aggregateRoot = aggregateTree.getFile();
    assertNotNull( aggregateRoot );
    assertEquals( DefaultGenericFileService.MULTIPLE_PROVIDER_ROOT_NAME, aggregateRoot.getName() );
    assertEquals( DefaultGenericFileService.MULTIPLE_PROVIDER_ROOT_PROVIDER, aggregateRoot.getProvider() );
    assertTrue( aggregateRoot.isFolder() );

    // Test Aggregate Tree's Children
    assertEquals( Arrays.asList( useCase.tree1Mock, useCase.tree2Mock ), aggregateTree.getChildren() );
  }

  @Test
  void testGetTreeWithMultipleProvidersAndNullBasePathIgnoresFailedProviders()
    throws OperationFailedException, InvalidGenericFileProviderException {
    GetTreeMultipleProviderUseCase useCase = new GetTreeMultipleProviderUseCase();

    doThrow( mock( OperationFailedException.class ) )
      .when( useCase.provider1Mock )
      .getTree( any( GetTreeOptions.class ) );

    IGenericFileTree aggregateTree = useCase.service.getTree( useCase.optionsMock );

    // ---

    assertNotNull( aggregateTree );
    assertEquals( Collections.singletonList( useCase.tree2Mock ), aggregateTree.getChildren() );
  }

  @Test
  void testGetTreeWithMultipleProvidersAndNullBasePathThrowsFirstExceptionIfAllFailed()
    throws OperationFailedException, InvalidGenericFileProviderException {
    GetTreeMultipleProviderUseCase useCase = new GetTreeMultipleProviderUseCase();

    OperationFailedException ex1 = mock( OperationFailedException.class );
    doThrow( ex1 )
      .when( useCase.provider1Mock )
      .getTree( any( GetTreeOptions.class ) );

    OperationFailedException ex2 = mock( OperationFailedException.class );
    doThrow( ex2 )
      .when( useCase.provider2Mock )
      .getTree( any( GetTreeOptions.class ) );

    try {
      useCase.service.getTree( useCase.optionsMock );
      fail();
    } catch ( OperationFailedException ex ) {
      assertSame( ex1, ex );
    }
  }

  @Test
  void testGetTreeWithMultipleProvidersAndUnknownProviderBasePathThrowsNotFoundException()
    throws OperationFailedException, InvalidGenericFileProviderException {
    GetTreeMultipleProviderUseCase useCase = new GetTreeMultipleProviderUseCase();

    doReturn( false ).when( useCase.provider1Mock ).owns( any( GenericFilePath.class ) );
    doReturn( false ).when( useCase.provider2Mock ).owns( any( GenericFilePath.class ) );

    doReturn( mock( GenericFilePath.class ) ).when( useCase.optionsMock ).getBasePath();

    assertThrows( NotFoundException.class, () -> useCase.service.getTree( useCase.optionsMock ) );
  }

  @Test
  void testGetTreeWithMultipleProvidersAndKnownProviderBasePathReturnsProviderSubtree()
    throws OperationFailedException, InvalidGenericFileProviderException {
    GetTreeMultipleProviderUseCase useCase = new GetTreeMultipleProviderUseCase();

    doReturn( false ).when( useCase.provider1Mock ).owns( any( GenericFilePath.class ) );
    doReturn( true ).when( useCase.provider2Mock ).owns( any( GenericFilePath.class ) );

    doReturn( mock( GenericFilePath.class ) ).when( useCase.optionsMock ).getBasePath();

    IGenericFileTree resultTree = useCase.service.getTree( useCase.optionsMock );

    assertSame( useCase.tree2Mock, resultTree );
    verify( useCase.provider2Mock, times( 1 ) ).getTree( useCase.optionsMock );
    verify( useCase.provider1Mock, never() ).getTree( useCase.optionsMock );
  }

  @Test
  void testGetTreeCallsDecorateTree() throws Exception {
    IGenericFileProvider<?> providerMock = mock( IGenericFileProvider.class );
    IGenericFileDecorator decoratorMock = mock( IGenericFileDecorator.class );
    IGenericFileTree treeMock = mock( IGenericFileTree.class );
    GetTreeOptions optionsMock = mock( GetTreeOptions.class );

    when( providerMock.getTree( optionsMock ) ).thenReturn( treeMock );

    DefaultGenericFileService service = new DefaultGenericFileService(
      Collections.singletonList( providerMock ), decoratorMock );

    service.getTree( optionsMock );

    verify( decoratorMock ).decorateTree( treeMock, service, optionsMock );
  }
  // endregion

  // region getRootTrees()
  private static class GetRootTreesMultipleProviderUseCase extends MultipleProviderUseCase {
    public final IGenericFileTree tree1Mock;
    public final IGenericFileTree tree2Mock;
    public final IGenericFileTree tree3Mock;
    public final IGenericFileTree tree4Mock;

    public final GetTreeOptions optionsMock;

    public GetRootTreesMultipleProviderUseCase() throws OperationFailedException, InvalidGenericFileProviderException {
      tree1Mock = mock( IGenericFileTree.class );
      tree2Mock = mock( IGenericFileTree.class );
      doReturn( List.of( tree1Mock, tree2Mock ) ).when( provider1Mock ).getRootTrees( any( GetTreeOptions.class ) );

      tree3Mock = mock( IGenericFileTree.class );
      tree4Mock = mock( IGenericFileTree.class );
      doReturn( List.of( tree3Mock, tree4Mock ) ).when( provider2Mock ).getRootTrees( any( GetTreeOptions.class ) );

      optionsMock = mock( GetTreeOptions.class );
    }
  }

  @Test
  void testGetTreeWithMultipleProvidersAggregatesAllProvidersRootTrees()
    throws InvalidGenericFileProviderException, OperationFailedException {
    GetRootTreesMultipleProviderUseCase useCase = new GetRootTreesMultipleProviderUseCase();

    List<IGenericFileTree> rootTrees = useCase.service.getRootTrees( useCase.optionsMock );

    // ---

    assertNotNull( rootTrees );
    assertEquals( 4, rootTrees.size() );
    assertSame( useCase.tree1Mock, rootTrees.get( 0 ) );
    assertSame( useCase.tree2Mock, rootTrees.get( 1 ) );
    assertSame( useCase.tree3Mock, rootTrees.get( 2 ) );
    assertSame( useCase.tree4Mock, rootTrees.get( 3 ) );
  }


  @Test
  void testGetTreeWithMultipleProvidersIgnoresFailedProviders()
    throws InvalidGenericFileProviderException, OperationFailedException {
    GetRootTreesMultipleProviderUseCase useCase = new GetRootTreesMultipleProviderUseCase();

    doThrow( mock( OperationFailedException.class ) )
      .when( useCase.provider1Mock )
      .getRootTrees( any( GetTreeOptions.class ) );

    List<IGenericFileTree> rootTrees = useCase.service.getRootTrees( useCase.optionsMock );

    // ---

    assertNotNull( rootTrees );
    assertEquals( 2, rootTrees.size() );
    assertSame( useCase.tree3Mock, rootTrees.get( 0 ) );
    assertSame( useCase.tree4Mock, rootTrees.get( 1 ) );
  }

  @Test
  void testGetRootTreesCallsDecorateTreeForEachTree() throws Exception {
    IGenericFileProvider<?> providerMock = mock( IGenericFileProvider.class );
    IGenericFileDecorator decoratorMock = mock( IGenericFileDecorator.class );
    IGenericFileTree tree1Mock = mock( IGenericFileTree.class );
    IGenericFileTree tree2Mock = mock( IGenericFileTree.class );
    GetTreeOptions optionsMock = mock( GetTreeOptions.class );

    when( providerMock.getRootTrees( optionsMock ) ).thenReturn( List.of( tree1Mock, tree2Mock ) );

    DefaultGenericFileService service =
      new DefaultGenericFileService( Collections.singletonList( providerMock ), decoratorMock );

    service.getRootTrees( optionsMock );

    verify( decoratorMock ).decorateTree( tree1Mock, service, optionsMock );
    verify( decoratorMock ).decorateTree( tree2Mock, service, optionsMock );
  }
  // endregion

  // region getFile
  private static class GetFileMultipleProviderUseCase extends MultipleProviderUseCase {
    public final IGenericFile file1Mock;
    public final IGenericFile file2Mock;

    public GetFileMultipleProviderUseCase() throws Exception {
      file1Mock = mock( IGenericFile.class );
      doReturn( file1Mock ).when( provider1Mock ).getFile( any( GenericFilePath.class ), any( GetFileOptions.class ) );

      file2Mock = mock( IGenericFile.class );
      doReturn( file2Mock ).when( provider2Mock ).getFile( any( GenericFilePath.class ), any( GetFileOptions.class ) );
    }
  }

  @Test
  void testGetFile() throws Exception {
    GetFileMultipleProviderUseCase useCase = new GetFileMultipleProviderUseCase();

    doReturn( false ).when( useCase.provider1Mock ).owns( any( GenericFilePath.class ) );
    doReturn( true ).when( useCase.provider2Mock ).owns( any( GenericFilePath.class ) );

    IGenericFile resultFile = useCase.service.getFile( mock( GenericFilePath.class ) );

    assertSame( useCase.file2Mock, resultFile );
    verify( useCase.provider2Mock, times( 1 ) ).getFile( any(), any() );
    verify( useCase.provider1Mock, never() ).getFile( any(), any() );
  }

  private static class GetFileWithOptionsMultipleProviderUseCase extends MultipleProviderUseCase {
    public final IGenericFile file1Mock;
    public final IGenericFile file2Mock;
    public final GetFileOptions optionsMock;

    public GetFileWithOptionsMultipleProviderUseCase() throws Exception {
      file1Mock = mock( IGenericFile.class );
      doReturn( file1Mock ).when( provider1Mock ).getFile( any( GenericFilePath.class ), any( GetFileOptions.class ) );

      file2Mock = mock( IGenericFile.class );
      doReturn( file2Mock ).when( provider2Mock ).getFile( any( GenericFilePath.class ), any( GetFileOptions.class ) );

      optionsMock = mock( GetFileOptions.class );
    }
  }

  @Test
  void testGetFileWithOptions() throws Exception {
    GetFileWithOptionsMultipleProviderUseCase useCase = new GetFileWithOptionsMultipleProviderUseCase();

    doReturn( false ).when( useCase.provider1Mock ).owns( any( GenericFilePath.class ) );
    doReturn( true ).when( useCase.provider2Mock ).owns( any( GenericFilePath.class ) );

    GenericFilePath pathMock = mock( GenericFilePath.class );

    IGenericFile resultFile = useCase.service.getFile( pathMock, useCase.optionsMock );

    assertSame( useCase.file2Mock, resultFile );
    verify( useCase.provider2Mock, times( 1 ) ).getFile( pathMock, useCase.optionsMock );
    verify( useCase.provider1Mock, never() ).getFile( any( GenericFilePath.class ), any( GetFileOptions.class ) );
  }

  @Test
  void testGetFileCallsDecorateFile() throws Exception {
    IGenericFileProvider<?> providerMock = mock( IGenericFileProvider.class );
    IGenericFileDecorator decoratorMock = mock( IGenericFileDecorator.class );
    IGenericFile fileMock = mock( IGenericFile.class );
    GetFileOptions optionsMock = mock( GetFileOptions.class );
    GenericFilePath pathMock = mock( GenericFilePath.class );

    doReturn( true ).when( providerMock ).owns( any( GenericFilePath.class ) );
    when( providerMock.getFile( pathMock, optionsMock ) ).thenReturn( fileMock );

    DefaultGenericFileService service = new DefaultGenericFileService(
      Collections.singletonList( providerMock ), decoratorMock );

    service.getFile( pathMock, optionsMock );

    verify( decoratorMock ).decorateFile( fileMock, service, optionsMock );
  }
  // endregion

  // region getDeletedFiles()
  private static class GetDeletedFilesMultipleProviderUseCase extends MultipleProviderUseCase {
    public final IGenericFile file1Mock;
    public final IGenericFile file2Mock;
    public final IGenericFile file3Mock;
    public final IGenericFile file4Mock;

    public GetDeletedFilesMultipleProviderUseCase()
      throws OperationFailedException, InvalidGenericFileProviderException {
      file1Mock = mock( IGenericFile.class );
      file2Mock = mock( IGenericFile.class );
      doReturn( List.of( file1Mock, file2Mock ) ).when( provider1Mock ).getDeletedFiles();

      file3Mock = mock( IGenericFile.class );
      file4Mock = mock( IGenericFile.class );
      doReturn( List.of( file3Mock, file4Mock ) ).when( provider2Mock ).getDeletedFiles();
    }
  }

  @Test
  void testGetDeletedFilesWithMultipleProvidersAggregatesAllProvidersDeletedFiles()
    throws InvalidGenericFileProviderException, OperationFailedException {
    GetDeletedFilesMultipleProviderUseCase useCase = new GetDeletedFilesMultipleProviderUseCase();

    List<IGenericFile> rootTrees = useCase.service.getDeletedFiles();

    // ---

    assertNotNull( rootTrees );
    assertEquals( 4, rootTrees.size() );
    assertSame( useCase.file1Mock, rootTrees.get( 0 ) );
    assertSame( useCase.file2Mock, rootTrees.get( 1 ) );
    assertSame( useCase.file3Mock, rootTrees.get( 2 ) );
    assertSame( useCase.file4Mock, rootTrees.get( 3 ) );
  }

  @Test
  void testGetDeletedFilesWithMultipleProvidersIgnoresFailedProviders()
    throws InvalidGenericFileProviderException, OperationFailedException {
    GetDeletedFilesMultipleProviderUseCase useCase = new GetDeletedFilesMultipleProviderUseCase();

    doThrow( mock( OperationFailedException.class ) )
      .when( useCase.provider1Mock )
      .getDeletedFiles();

    List<IGenericFile> rootTrees = useCase.service.getDeletedFiles();

    // ---

    assertNotNull( rootTrees );
    assertEquals( 2, rootTrees.size() );
    assertSame( useCase.file3Mock, rootTrees.get( 0 ) );
    assertSame( useCase.file4Mock, rootTrees.get( 1 ) );
  }
  // endregion

  // region deleteFilePermanently
  private static class DeleteFilesPermanentlyMultipleProviderUseCase extends MultipleProviderUseCase {
    public final GenericFilePath path1;
    public final GenericFilePath path2;

    public DeleteFilesPermanentlyMultipleProviderUseCase() throws InvalidGenericFileProviderException {
      path1 = mock( GenericFilePath.class );
      path2 = mock( GenericFilePath.class );

      doReturn( true ).when( provider1Mock ).owns( path1 );
      doReturn( false ).when( provider1Mock ).owns( path2 );

      doReturn( false ).when( provider2Mock ).owns( path1 );
      doReturn( true ).when( provider2Mock ).owns( path2 );
    }
  }

  @Test
  void testDeleteFilesPermanentlySuccess() throws Exception {
    DeleteFilesPermanentlyMultipleProviderUseCase useCase = new DeleteFilesPermanentlyMultipleProviderUseCase();

    useCase.service.deleteFilesPermanently( Arrays.asList( useCase.path1, useCase.path2 ) );

    verify( useCase.provider1Mock ).deleteFilePermanently( useCase.path1 );
    verify( useCase.provider2Mock ).deleteFilePermanently( useCase.path2 );
  }

  @Test
  void testDeleteFilePermanentlyPathNotFound() throws Exception {
    DeleteFilesPermanentlyMultipleProviderUseCase useCase = new DeleteFilesPermanentlyMultipleProviderUseCase();

    doReturn( false ).when( useCase.provider1Mock ).owns( useCase.path1 );

    BatchOperationFailedException exception = assertThrows( BatchOperationFailedException.class,
      () -> useCase.service.deleteFilesPermanently( Collections.singletonList( useCase.path1 ) ) );

    Map<GenericFilePath, Exception> failedFiles = exception.getFailedFiles();

    assertEquals( "Error(s) occurred during permanent deletion.", exception.getMessage() );
    assertNotNull( failedFiles );
    assertFalse( failedFiles.isEmpty() );
    assertEquals( 1, failedFiles.size() );
    assertTrue( failedFiles.containsKey( useCase.path1 ) );
    assertEquals( "Path not found '" + useCase.path1 + "'.", failedFiles.get( useCase.path1 ).getMessage() );
    assertEquals( NotFoundException.class, exception.getSuppressed()[ 0 ].getClass() );
    verify( useCase.provider1Mock, never() ).deleteFilePermanently( any( GenericFilePath.class ) );
  }

  @Test
  void testDeleteFilePermanentlyInvalidPath() throws Exception {
    DeleteFilesPermanentlyMultipleProviderUseCase useCase = new DeleteFilesPermanentlyMultipleProviderUseCase();

    doThrow( InvalidPathException.class ).when( useCase.provider1Mock ).deleteFilePermanently( useCase.path1 );

    InvalidPathException exception =
      assertThrows( InvalidPathException.class, () -> useCase.service.deleteFilePermanently( useCase.path1 ) );

    assertEquals( InvalidPathException.class, exception.getClass() );
  }

  @Test
  void testDeleteFilesPermanentlyException() throws Exception {
    DeleteFilesPermanentlyMultipleProviderUseCase useCase = new DeleteFilesPermanentlyMultipleProviderUseCase();

    doThrow( new OperationFailedException( "Deletion failed." ) ).when( useCase.provider1Mock )
      .deleteFilePermanently( useCase.path1 );

    BatchOperationFailedException exception = assertThrows( BatchOperationFailedException.class,
      () -> useCase.service.deleteFilesPermanently( Arrays.asList( useCase.path1, useCase.path2 ) )
    );

    Map<GenericFilePath, Exception> failedFiles = exception.getFailedFiles();

    assertEquals( "Error(s) occurred during permanent deletion.", exception.getMessage() );
    assertNotNull( failedFiles );
    assertFalse( failedFiles.isEmpty() );
    assertEquals( 1, failedFiles.size() );
    assertTrue( failedFiles.containsKey( useCase.path1 ) );
    assertEquals( "Deletion failed.", failedFiles.get( useCase.path1 ).getMessage() );
    verify( useCase.provider1Mock ).deleteFilePermanently( useCase.path1 );
    verify( useCase.provider2Mock ).deleteFilePermanently( useCase.path2 );
  }
  // endregion

  // region deleteFile
  private static class DeleteFilesMultipleProviderUseCase extends MultipleProviderUseCase {
    public final GenericFilePath path1;
    public final GenericFilePath path2;

    public DeleteFilesMultipleProviderUseCase() throws InvalidGenericFileProviderException {
      path1 = mock( GenericFilePath.class );
      path2 = mock( GenericFilePath.class );

      doReturn( true ).when( provider1Mock ).owns( path1 );
      doReturn( false ).when( provider1Mock ).owns( path2 );

      doReturn( false ).when( provider2Mock ).owns( path1 );
      doReturn( true ).when( provider2Mock ).owns( path2 );
    }
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testDeleteFilesSuccess( boolean permanent ) throws Exception {
    DeleteFilesMultipleProviderUseCase useCase = new DeleteFilesMultipleProviderUseCase();

    useCase.service.deleteFiles( Arrays.asList( useCase.path1, useCase.path2 ), permanent );

    verify( useCase.provider1Mock ).deleteFile( useCase.path1, permanent );
    verify( useCase.provider2Mock ).deleteFile( useCase.path2, permanent );
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testDeleteFilePathNotFound( boolean permanent ) throws Exception {
    DeleteFilesMultipleProviderUseCase useCase = new DeleteFilesMultipleProviderUseCase();

    doReturn( false ).when( useCase.provider1Mock ).owns( useCase.path1 );

    BatchOperationFailedException exception = assertThrows( BatchOperationFailedException.class,
      () -> useCase.service.deleteFiles( Collections.singletonList( useCase.path1 ), permanent ) );

    Map<GenericFilePath, Exception> failedFiles = exception.getFailedFiles();

    assertEquals( "Error(s) occurred during deletion.", exception.getMessage() );
    assertNotNull( failedFiles );
    assertFalse( failedFiles.isEmpty() );
    assertEquals( 1, failedFiles.size() );
    assertTrue( failedFiles.containsKey( useCase.path1 ) );
    assertEquals( "Path not found '" + useCase.path1 + "'.", failedFiles.get( useCase.path1 ).getMessage() );
    verify( useCase.provider1Mock, never() ).deleteFile( any( GenericFilePath.class ), eq( permanent ) );
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testDeleteFilesException( boolean permanent ) throws Exception {
    DeleteFilesMultipleProviderUseCase useCase = new DeleteFilesMultipleProviderUseCase();

    doThrow( new OperationFailedException( "Deletion failed." ) ).when( useCase.provider1Mock )
      .deleteFile( useCase.path1, permanent );

    BatchOperationFailedException exception = assertThrows( BatchOperationFailedException.class, () ->
      useCase.service.deleteFiles( Arrays.asList( useCase.path1, useCase.path2 ), permanent )
    );

    Map<GenericFilePath, Exception> failedFiles = exception.getFailedFiles();

    assertEquals( "Error(s) occurred during deletion.", exception.getMessage() );
    assertNotNull( failedFiles );
    assertFalse( failedFiles.isEmpty() );
    assertEquals( 1, failedFiles.size() );
    assertTrue( failedFiles.containsKey( useCase.path1 ) );
    assertEquals( "Deletion failed.", failedFiles.get( useCase.path1 ).getMessage() );
    verify( useCase.provider1Mock ).deleteFile( useCase.path1, permanent );
    verify( useCase.provider2Mock ).deleteFile( useCase.path2, permanent );
  }
  // endregion

  // region restoreFile
  private static class RestoreFilesMultipleProviderUseCase extends MultipleProviderUseCase {
    public final GenericFilePath path1;
    public final GenericFilePath path2;

    public RestoreFilesMultipleProviderUseCase() throws InvalidGenericFileProviderException {
      path1 = mock( GenericFilePath.class );
      path2 = mock( GenericFilePath.class );

      doReturn( true ).when( provider1Mock ).owns( path1 );
      doReturn( false ).when( provider1Mock ).owns( path2 );

      doReturn( false ).when( provider2Mock ).owns( path1 );
      doReturn( true ).when( provider2Mock ).owns( path2 );
    }
  }

  @Test
  void testRestoreFilesSuccess() throws Exception {
    RestoreFilesMultipleProviderUseCase useCase = new RestoreFilesMultipleProviderUseCase();

    useCase.service.restoreFiles( Arrays.asList( useCase.path1, useCase.path2 ) );

    verify( useCase.provider1Mock ).restoreFile( useCase.path1 );
    verify( useCase.provider2Mock ).restoreFile( useCase.path2 );
  }

  @Test
  void testRestoreFilePathNotFound() throws Exception {
    RestoreFilesMultipleProviderUseCase useCase = new RestoreFilesMultipleProviderUseCase();

    doReturn( false ).when( useCase.provider1Mock ).owns( useCase.path1 );

    BatchOperationFailedException exception = assertThrows( BatchOperationFailedException.class,
      () -> useCase.service.restoreFiles( Collections.singletonList( useCase.path1 ) ) );

    Map<GenericFilePath, Exception> failedFiles = exception.getFailedFiles();

    assertEquals( "Error(s) occurred while attempting to restore files.", exception.getMessage() );
    assertNotNull( failedFiles );
    assertFalse( failedFiles.isEmpty() );
    assertEquals( 1, failedFiles.size() );
    assertTrue( failedFiles.containsKey( useCase.path1 ) );
    assertEquals( "Path not found '" + useCase.path1 + "'.", failedFiles.get( useCase.path1 ).getMessage() );
    verify( useCase.provider1Mock, never() ).restoreFile( any( GenericFilePath.class ) );
  }

  @Test
  void testRestoreFilesException() throws Exception {
    RestoreFilesMultipleProviderUseCase useCase = new RestoreFilesMultipleProviderUseCase();

    doThrow( new OperationFailedException( "Restore failed." ) ).when( useCase.provider1Mock )
      .restoreFile( useCase.path1 );

    BatchOperationFailedException exception = assertThrows( BatchOperationFailedException.class, () ->
      useCase.service.restoreFiles( Arrays.asList( useCase.path1, useCase.path2 ) )
    );

    Map<GenericFilePath, Exception> failedFiles = exception.getFailedFiles();

    assertEquals( "Error(s) occurred while attempting to restore files.", exception.getMessage() );
    assertNotNull( failedFiles );
    assertFalse( failedFiles.isEmpty() );
    assertEquals( 1, failedFiles.size() );
    assertTrue( failedFiles.containsKey( useCase.path1 ) );
    assertEquals( "Restore failed.", failedFiles.get( useCase.path1 ).getMessage() );
    verify( useCase.provider1Mock ).restoreFile( useCase.path1 );
    verify( useCase.provider2Mock ).restoreFile( useCase.path2 );
  }
  // endregion

  // region getFileContent
  private static class GetFileContentMultipleProviderUseCase extends MultipleProviderUseCase {
    public final GenericFilePath path1;
    public final GenericFilePath path2;

    public GetFileContentMultipleProviderUseCase() throws InvalidGenericFileProviderException {
      path1 = mock( GenericFilePath.class );
      path2 = mock( GenericFilePath.class );

      doReturn( true ).when( provider1Mock ).owns( path1 );
      doReturn( false ).when( provider1Mock ).owns( path2 );

      doReturn( false ).when( provider2Mock ).owns( path1 );
      doReturn( true ).when( provider2Mock ).owns( path2 );
    }
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testGetFileContentSuccess( boolean compressed ) throws Exception {
    GetFileContentMultipleProviderUseCase useCase = new GetFileContentMultipleProviderUseCase();
    IGenericFileContent content1 = mock( IGenericFileContent.class );
    IGenericFileContent content2 = mock( IGenericFileContent.class );

    doReturn( content1 ).when( useCase.provider1Mock ).getFileContent( useCase.path1, compressed );
    doReturn( content2 ).when( useCase.provider2Mock ).getFileContent( useCase.path2, compressed );

    assertSame( content1, useCase.service.getFileContent( useCase.path1, compressed ) );
    assertSame( content2, useCase.service.getFileContent( useCase.path2, compressed ) );
    verify( useCase.provider1Mock ).getFileContent( useCase.path1, compressed );
    verify( useCase.provider2Mock ).getFileContent( useCase.path2, compressed );
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testGetFileContentPathNotFound( boolean compressed ) throws Exception {
    GetFileContentMultipleProviderUseCase useCase = new GetFileContentMultipleProviderUseCase();
    doReturn( false ).when( useCase.provider1Mock ).owns( useCase.path1 );

    NotFoundException ex =
      assertThrows( NotFoundException.class, () -> useCase.service.getFileContent( useCase.path1, compressed ) );

    assertEquals( "Path not found '" + useCase.path1 + "'.", ex.getMessage() );
    verify( useCase.provider1Mock, never() ).getFileContent( any(), anyBoolean() );
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testGetFileContentException( boolean compressed ) throws Exception {
    GetFileContentMultipleProviderUseCase useCase = new GetFileContentMultipleProviderUseCase();
    doThrow( new OperationFailedException( "Read failed." ) ).when( useCase.provider1Mock )
      .getFileContent( useCase.path1, compressed );

    OperationFailedException ex =
      assertThrows( OperationFailedException.class, () -> useCase.service.getFileContent( useCase.path1, compressed ) );

    assertEquals( "Read failed.", ex.getMessage() );
    verify( useCase.provider1Mock ).getFileContent( useCase.path1, compressed );
  }
  // endregion

  // region createFolder
  private static class CreateFolderMultipleProviderUseCase extends MultipleProviderUseCase {
    public final GenericFilePath path1;
    public final GenericFilePath path2;

    public CreateFolderMultipleProviderUseCase() throws InvalidGenericFileProviderException {
      path1 = mock( GenericFilePath.class );
      path2 = mock( GenericFilePath.class );

      doReturn( true ).when( provider1Mock ).owns( path1 );
      doReturn( false ).when( provider1Mock ).owns( path2 );

      doReturn( false ).when( provider2Mock ).owns( path1 );
      doReturn( true ).when( provider2Mock ).owns( path2 );
    }
  }

  @Test
  void testCreateFolderSuccess() throws Exception {
    CreateFolderMultipleProviderUseCase useCase = new CreateFolderMultipleProviderUseCase();

    doReturn( true ).when( useCase.provider1Mock ).createFolder( useCase.path1 );
    doReturn( true ).when( useCase.provider2Mock ).createFolder( useCase.path2 );

    assertTrue( useCase.service.createFolder( useCase.path1 ) );
    assertTrue( useCase.service.createFolder( useCase.path2 ) );
    verify( useCase.provider1Mock ).createFolder( useCase.path1 );
    verify( useCase.provider2Mock ).createFolder( useCase.path2 );
  }

  @Test
  void testCreateFolderPathNotFound() throws Exception {
    CreateFolderMultipleProviderUseCase useCase = new CreateFolderMultipleProviderUseCase();

    doReturn( false ).when( useCase.provider1Mock ).owns( useCase.path1 );

    NotFoundException ex = assertThrows( NotFoundException.class, () -> useCase.service.createFolder( useCase.path1 ) );

    assertEquals( "Path not found '" + useCase.path1 + "'.", ex.getMessage() );
    verify( useCase.provider1Mock, never() ).createFolder( any() );
  }

  @Test
  void testCreateFolderOperationFailed() throws Exception {
    CreateFolderMultipleProviderUseCase useCase = new CreateFolderMultipleProviderUseCase();

    doReturn( false ).when( useCase.provider1Mock ).createFolder( useCase.path1 );

    assertFalse( useCase.service.createFolder( useCase.path1 ) );
    verify( useCase.provider1Mock ).createFolder( useCase.path1 );
  }

  @Test
  void testCreateFolderThrowsException() throws Exception {
    CreateFolderMultipleProviderUseCase useCase = new CreateFolderMultipleProviderUseCase();

    doThrow( new OperationFailedException( "Create failed." ) ).when( useCase.provider1Mock )
      .createFolder( useCase.path1 );

    OperationFailedException ex =
      assertThrows( OperationFailedException.class, () -> useCase.service.createFolder( useCase.path1 ) );

    assertEquals( "Create failed.", ex.getMessage() );
    verify( useCase.provider1Mock ).createFolder( useCase.path1 );
  }
  // endregion

  // region renameFile
  private static class RenameFilesMultipleProviderUseCase extends MultipleProviderUseCase {
    public final GenericFilePath path1;
    public final GenericFilePath path2;

    public RenameFilesMultipleProviderUseCase() throws InvalidGenericFileProviderException {
      path1 = mock( GenericFilePath.class );
      path2 = mock( GenericFilePath.class );

      doReturn( true ).when( provider1Mock ).owns( path1 );
      doReturn( false ).when( provider1Mock ).owns( path2 );

      doReturn( false ).when( provider2Mock ).owns( path1 );
      doReturn( true ).when( provider2Mock ).owns( path2 );
    }
  }

  @Test
  void testRenameFilesSuccess() throws Exception {
    RenameFilesMultipleProviderUseCase useCase = new RenameFilesMultipleProviderUseCase();

    useCase.service.renameFile( useCase.path1, "newName1" );
    useCase.service.renameFile( useCase.path2, "newName2" );

    verify( useCase.provider1Mock ).renameFile( useCase.path1, "newName1" );
    verify( useCase.provider2Mock ).renameFile( useCase.path2, "newName2" );
  }

  @Test
  void testRenameFilePathNotFound() throws Exception {
    RenameFilesMultipleProviderUseCase useCase = new RenameFilesMultipleProviderUseCase();

    doReturn( false ).when( useCase.provider1Mock ).owns( useCase.path1 );

    NotFoundException exception = assertThrows( NotFoundException.class,
      () -> useCase.service.renameFile( useCase.path1, "newName" ) );

    assertEquals( "Path not found '" + useCase.path1 + "'.", exception.getMessage() );
    verify( useCase.provider1Mock, never() ).renameFile( any( GenericFilePath.class ), any() );
  }

  @Test
  void testRenameFileException() throws Exception {
    RenameFilesMultipleProviderUseCase useCase = new RenameFilesMultipleProviderUseCase();

    doThrow( new OperationFailedException( "Rename failed." ) ).when( useCase.provider1Mock )
      .renameFile( useCase.path1, "newName" );

    OperationFailedException exception = assertThrows( OperationFailedException.class,
      () -> useCase.service.renameFile( useCase.path1, "newName" ) );

    assertEquals( "Rename failed.", exception.getMessage() );
    verify( useCase.provider1Mock ).renameFile( useCase.path1, "newName" );
  }
  // endregion

  // region copyFile
  private static class CopyFilesMultipleProviderUseCase extends MultipleProviderUseCase {
    public final GenericFilePath path11;
    public final GenericFilePath path12;
    public final GenericFilePath path21;
    public final GenericFilePath path22;
    public final GenericFilePath destPath;

    public CopyFilesMultipleProviderUseCase() throws InvalidGenericFileProviderException {
      path11 = mock( GenericFilePath.class );
      path12 = mock( GenericFilePath.class );
      path21 = mock( GenericFilePath.class );
      path22 = mock( GenericFilePath.class );
      destPath = mock( GenericFilePath.class );

      doReturn( true ).when( provider1Mock ).owns( path11 );
      doReturn( true ).when( provider1Mock ).owns( path12 );
      doReturn( false ).when( provider1Mock ).owns( path21 );
      doReturn( false ).when( provider1Mock ).owns( path22 );
      doReturn( true ).when( provider1Mock ).owns( destPath );

      doReturn( false ).when( provider2Mock ).owns( path11 );
      doReturn( false ).when( provider2Mock ).owns( path12 );
      doReturn( true ).when( provider2Mock ).owns( path21 );
      doReturn( true ).when( provider2Mock ).owns( path22 );
      doReturn( false ).when( provider2Mock ).owns( destPath );
    }
  }

  @Test
  void testCopyFilesSuccess() throws Exception {
    CopyFilesMultipleProviderUseCase useCase = new CopyFilesMultipleProviderUseCase();

    useCase.service.copyFiles( Arrays.asList( useCase.path11, useCase.path12 ), useCase.destPath );

    verify( useCase.provider1Mock, times( 2 ) ).copyFile( any(), any() );
    verify( useCase.provider2Mock, never() ).copyFile( any(), any() );
  }

  @Test
  void testCopyFilePathNotFound() throws Exception {
    CopyFilesMultipleProviderUseCase useCase = new CopyFilesMultipleProviderUseCase();

    doReturn( false ).when( useCase.provider1Mock ).owns( useCase.path11 );

    BatchOperationFailedException exception = assertThrows( BatchOperationFailedException.class,
      () -> useCase.service.copyFiles( Collections.singletonList( useCase.path11 ), useCase.destPath ) );

    Map<GenericFilePath, Exception> failedFiles = exception.getFailedFiles();

    assertEquals( "Error(s) occurred while attempting to copy files.", exception.getMessage() );
    assertNotNull( failedFiles );
    assertFalse( failedFiles.isEmpty() );
    assertEquals( 1, failedFiles.size() );
    assertTrue( failedFiles.containsKey( useCase.path11 ) );
    assertEquals( "Path not found '" + useCase.path11 + "'.", failedFiles.get( useCase.path11 ).getMessage() );
    verify( useCase.provider1Mock, never() ).copyFile( any(), any() );
  }

  @Test
  void testCopyFilesException() throws Exception {
    CopyFilesMultipleProviderUseCase useCase = new CopyFilesMultipleProviderUseCase();

    doThrow( new OperationFailedException( "Copy failed." ) ).when( useCase.provider1Mock )
      .copyFile( useCase.path11, useCase.destPath );

    BatchOperationFailedException exception = assertThrows( BatchOperationFailedException.class,
      () -> useCase.service.copyFiles( Arrays.asList( useCase.path11, useCase.path12 ), useCase.destPath ) );

    Map<GenericFilePath, Exception> failedFiles = exception.getFailedFiles();

    assertEquals( "Error(s) occurred while attempting to copy files.", exception.getMessage() );
    assertNotNull( failedFiles );
    assertFalse( failedFiles.isEmpty() );
    assertEquals( 1, failedFiles.size() );
    assertTrue( failedFiles.containsKey( useCase.path11 ) );
    assertEquals( "Copy failed.", exception.getFailedFiles().get( useCase.path11 ).getMessage() );
    verify( useCase.provider1Mock, times( 2 ) ).copyFile( any(), any() );
    verify( useCase.provider2Mock, never() ).copyFile( any(), any() );
  }

  @Test
  void testCopyFilesUnsupportedOperationException() throws Exception {
    CopyFilesMultipleProviderUseCase useCase = new CopyFilesMultipleProviderUseCase();

    doNothing().when( useCase.provider1Mock ).copyFile( any(), any() );
    doNothing().when( useCase.provider2Mock ).copyFile( any(), any() );

    List<GenericFilePath> files = Arrays.asList( useCase.path11, useCase.path21 );
    assertThrows( UnsupportedOperationException.class, () -> useCase.service.copyFiles( files, useCase.destPath ) );

    verify( useCase.provider1Mock ).copyFile( any(), any() );
    verify( useCase.provider2Mock, never() ).copyFile( any(), any() );
  }
  // endregion

  // region moveFile
  private static class MoveFilesMultipleProviderUseCase extends MultipleProviderUseCase {
    public final GenericFilePath path11;
    public final GenericFilePath path12;
    public final GenericFilePath path21;
    public final GenericFilePath path22;
    public final GenericFilePath destPath;

    public MoveFilesMultipleProviderUseCase() throws InvalidGenericFileProviderException {
      path11 = mock( GenericFilePath.class );
      path12 = mock( GenericFilePath.class );
      path21 = mock( GenericFilePath.class );
      path22 = mock( GenericFilePath.class );
      destPath = mock( GenericFilePath.class );

      doReturn( true ).when( provider1Mock ).owns( path11 );
      doReturn( true ).when( provider1Mock ).owns( path12 );
      doReturn( false ).when( provider1Mock ).owns( path21 );
      doReturn( false ).when( provider1Mock ).owns( path22 );
      doReturn( true ).when( provider1Mock ).owns( destPath );

      doReturn( false ).when( provider2Mock ).owns( path11 );
      doReturn( false ).when( provider2Mock ).owns( path12 );
      doReturn( true ).when( provider2Mock ).owns( path21 );
      doReturn( true ).when( provider2Mock ).owns( path22 );
      doReturn( false ).when( provider2Mock ).owns( destPath );
    }
  }

  @Test
  void testMoveFilesSuccess() throws Exception {
    MoveFilesMultipleProviderUseCase useCase = new MoveFilesMultipleProviderUseCase();

    useCase.service.moveFiles( Arrays.asList( useCase.path11, useCase.path12 ), useCase.destPath );

    verify( useCase.provider1Mock, times( 2 ) ).moveFile( any(), any() );
    verify( useCase.provider2Mock, never() ).moveFile( any(), any() );
  }

  @Test
  void testMoveFilePathNotFound() throws Exception {
    MoveFilesMultipleProviderUseCase useCase = new MoveFilesMultipleProviderUseCase();

    doReturn( false ).when( useCase.provider1Mock ).owns( useCase.path11 );

    BatchOperationFailedException exception = assertThrows( BatchOperationFailedException.class,
      () -> useCase.service.moveFiles( Collections.singletonList( useCase.path11 ), useCase.destPath ) );

    Map<GenericFilePath, Exception> failedFiles = exception.getFailedFiles();

    assertEquals( "Error(s) occurred while attempting to move files.", exception.getMessage() );
    assertNotNull( failedFiles );
    assertFalse( failedFiles.isEmpty() );
    assertEquals( 1, failedFiles.size() );
    assertTrue( failedFiles.containsKey( useCase.path11 ) );
    assertEquals( "Path not found '" + useCase.path11 + "'.", failedFiles.get( useCase.path11 ).getMessage() );
    verify( useCase.provider1Mock, never() ).moveFile( any(), any() );
  }

  @Test
  void testMoveFilesException() throws Exception {
    MoveFilesMultipleProviderUseCase useCase = new MoveFilesMultipleProviderUseCase();

    doThrow( new OperationFailedException( "Move failed." ) ).when( useCase.provider1Mock )
      .moveFile( useCase.path11, useCase.destPath );

    BatchOperationFailedException exception = assertThrows( BatchOperationFailedException.class,
      () -> useCase.service.moveFiles( Arrays.asList( useCase.path11, useCase.path12 ), useCase.destPath ) );

    Map<GenericFilePath, Exception> failedFiles = exception.getFailedFiles();

    assertEquals( "Error(s) occurred while attempting to move files.", exception.getMessage() );
    assertNotNull( failedFiles );
    assertFalse( failedFiles.isEmpty() );
    assertEquals( 1, failedFiles.size() );
    assertTrue( failedFiles.containsKey( useCase.path11 ) );
    assertEquals( "Move failed.", exception.getFailedFiles().get( useCase.path11 ).getMessage() );
    verify( useCase.provider1Mock, times( 2 ) ).moveFile( any(), any() );
    verify( useCase.provider2Mock, never() ).moveFile( any(), any() );
  }

  @Test
  void testMoveFilesUnsupportedOperationException() throws Exception {
    MoveFilesMultipleProviderUseCase useCase = new MoveFilesMultipleProviderUseCase();

    doNothing().when( useCase.provider1Mock ).moveFile( any(), any() );
    doNothing().when( useCase.provider2Mock ).moveFile( any(), any() );

    List<GenericFilePath> files = Arrays.asList( useCase.path11, useCase.path21 );
    assertThrows( UnsupportedOperationException.class, () -> useCase.service.moveFiles( files, useCase.destPath ) );

    verify( useCase.provider1Mock ).moveFile( any(), any() );
    verify( useCase.provider2Mock, never() ).moveFile( any(), any() );
  }
  // endregion

  // region getFileMetadata and setFileMetadata
  private static class FileMetadataMultipleProviderUseCase extends MultipleProviderUseCase {
    public final GenericFilePath path1;
    public final GenericFilePath path2;

    public FileMetadataMultipleProviderUseCase() throws InvalidGenericFileProviderException {
      path1 = mock( GenericFilePath.class );
      path2 = mock( GenericFilePath.class );

      doReturn( true ).when( provider1Mock ).owns( path1 );
      doReturn( false ).when( provider1Mock ).owns( path2 );

      doReturn( false ).when( provider2Mock ).owns( path1 );
      doReturn( true ).when( provider2Mock ).owns( path2 );
    }
  }

  @Test
  void testGetFileMetadataSuccess() throws Exception {
    FileMetadataMultipleProviderUseCase useCase = new FileMetadataMultipleProviderUseCase();
    IGenericFileMetadata metadata1 = mock( IGenericFileMetadata.class );
    IGenericFileMetadata metadata2 = mock( IGenericFileMetadata.class );

    doReturn( metadata1 ).when( useCase.provider1Mock ).getFileMetadata( useCase.path1 );
    doReturn( metadata2 ).when( useCase.provider2Mock ).getFileMetadata( useCase.path2 );

    assertSame( metadata1, useCase.service.getFileMetadata( useCase.path1 ) );
    assertSame( metadata2, useCase.service.getFileMetadata( useCase.path2 ) );
    verify( useCase.provider1Mock ).getFileMetadata( useCase.path1 );
    verify( useCase.provider2Mock ).getFileMetadata( useCase.path2 );
  }

  @Test
  void testGetFileMetadataPathNotFound() throws Exception {
    FileMetadataMultipleProviderUseCase useCase = new FileMetadataMultipleProviderUseCase();

    doReturn( false ).when( useCase.provider1Mock ).owns( useCase.path1 );

    NotFoundException exception =
      assertThrows( NotFoundException.class, () -> useCase.service.getFileMetadata( useCase.path1 ) );

    assertEquals( "Path not found '" + useCase.path1 + "'.", exception.getMessage() );
    verify( useCase.provider1Mock, never() ).getFileMetadata( any() );
  }

  @Test
  void testGetFileMetadataException() throws Exception {
    FileMetadataMultipleProviderUseCase useCase = new FileMetadataMultipleProviderUseCase();

    doThrow( new OperationFailedException( "Metadata failed." ) ).when( useCase.provider1Mock )
      .getFileMetadata( useCase.path1 );

    OperationFailedException exception =
      assertThrows( OperationFailedException.class, () -> useCase.service.getFileMetadata( useCase.path1 ) );

    assertEquals( "Metadata failed.", exception.getMessage() );
    verify( useCase.provider1Mock ).getFileMetadata( useCase.path1 );
  }

  @Test
  void testSetFileMetadataSuccess() throws Exception {
    FileMetadataMultipleProviderUseCase useCase = new FileMetadataMultipleProviderUseCase();
    IGenericFileMetadata metadata1 = mock( IGenericFileMetadata.class );
    IGenericFileMetadata metadata2 = mock( IGenericFileMetadata.class );

    useCase.service.setFileMetadata( useCase.path1, metadata1 );
    useCase.service.setFileMetadata( useCase.path2, metadata2 );

    verify( useCase.provider1Mock ).setFileMetadata( useCase.path1, metadata1 );
    verify( useCase.provider2Mock ).setFileMetadata( useCase.path2, metadata2 );
  }

  @Test
  void testSetFileMetadataPathNotFound() throws Exception {
    FileMetadataMultipleProviderUseCase useCase = new FileMetadataMultipleProviderUseCase();
    IGenericFileMetadata metadata = mock( IGenericFileMetadata.class );

    doReturn( false ).when( useCase.provider1Mock ).owns( useCase.path1 );

    NotFoundException exception = assertThrows( NotFoundException.class,
      () -> useCase.service.setFileMetadata( useCase.path1, metadata ) );

    assertEquals( "Path not found '" + useCase.path1 + "'.", exception.getMessage() );
    verify( useCase.provider1Mock, never() ).setFileMetadata( any(), any() );
  }

  @Test
  void testSetFileMetadataException() throws Exception {
    FileMetadataMultipleProviderUseCase useCase = new FileMetadataMultipleProviderUseCase();
    IGenericFileMetadata metadata = mock( IGenericFileMetadata.class );

    doThrow( new OperationFailedException( "Set metadata failed." ) ).when( useCase.provider1Mock )
      .setFileMetadata( useCase.path1, metadata );

    OperationFailedException exception = assertThrows( OperationFailedException.class,
      () -> useCase.service.setFileMetadata( useCase.path1, metadata ) );

    assertEquals( "Set metadata failed.", exception.getMessage() );
    verify( useCase.provider1Mock ).setFileMetadata( useCase.path1, metadata );
  }

  @Test
  void testGetFileMetadataCallsDecorateFileMetadata() throws Exception {
    IGenericFileProvider<?> providerMock = mock( IGenericFileProvider.class );
    IGenericFileDecorator decoratorMock = mock( IGenericFileDecorator.class );
    IGenericFileMetadata fileMetadataMock = mock( IGenericFileMetadata.class );
    GenericFilePath pathMock = mock( GenericFilePath.class );

    doReturn( true ).when( providerMock ).owns( any( GenericFilePath.class ) );
    when( providerMock.getFileMetadata( pathMock ) ).thenReturn( fileMetadataMock );

    DefaultGenericFileService service = new DefaultGenericFileService(
      Collections.singletonList( providerMock ), decoratorMock );

    service.getFileMetadata( pathMock );

    verify( decoratorMock ).decorateFileMetadata( fileMetadataMock, pathMock, service );
  }
  // endregion

  // region getFileAcl and setFileAcl
  private static class FileAclMultipleProviderUseCase extends MultipleProviderUseCase {
    public final GenericFilePath path1;
    public final GenericFilePath path2;

    public FileAclMultipleProviderUseCase() throws InvalidGenericFileProviderException {
      path1 = mock( GenericFilePath.class );
      path2 = mock( GenericFilePath.class );

      doReturn( true ).when( provider1Mock ).owns( path1 );
      doReturn( false ).when( provider1Mock ).owns( path2 );

      doReturn( false ).when( provider2Mock ).owns( path1 );
      doReturn( true ).when( provider2Mock ).owns( path2 );
    }
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testGetFileAclSuccess( boolean forceInheriting ) throws Exception {
    FileAclMultipleProviderUseCase useCase = new FileAclMultipleProviderUseCase();
    IGenericFileAcl acl1 = mock( IGenericFileAcl.class );
    IGenericFileAcl acl2 = mock( IGenericFileAcl.class );

    doReturn( acl1 ).when( useCase.provider1Mock ).getFileAcl( useCase.path1, forceInheriting );
    doReturn( acl2 ).when( useCase.provider2Mock ).getFileAcl( useCase.path2, forceInheriting );

    assertSame( acl1, useCase.service.getFileAcl( useCase.path1, forceInheriting ) );
    assertSame( acl2, useCase.service.getFileAcl( useCase.path2, forceInheriting ) );
    verify( useCase.provider1Mock ).getFileAcl( useCase.path1, forceInheriting );
    verify( useCase.provider2Mock ).getFileAcl( useCase.path2, forceInheriting );
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testGetFileAclPathNotFound( boolean forceInheriting ) throws Exception {
    FileAclMultipleProviderUseCase useCase = new FileAclMultipleProviderUseCase();

    doReturn( false ).when( useCase.provider1Mock ).owns( useCase.path1 );

    NotFoundException exception =
      assertThrows( NotFoundException.class, () -> useCase.service.getFileAcl( useCase.path1, forceInheriting ) );

    assertEquals( "Path not found '" + useCase.path1 + "'.", exception.getMessage() );
    verify( useCase.provider1Mock, never() ).getFileAcl( any(), anyBoolean() );
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testGetFileAclException( boolean forceInheriting ) throws Exception {
    FileAclMultipleProviderUseCase useCase = new FileAclMultipleProviderUseCase();

    doThrow( new OperationFailedException( "ACL failed." ) ).when( useCase.provider1Mock )
      .getFileAcl( useCase.path1, forceInheriting );

    OperationFailedException exception =
      assertThrows( OperationFailedException.class,
        () -> useCase.service.getFileAcl( useCase.path1, forceInheriting ) );

    assertEquals( "ACL failed.", exception.getMessage() );
    verify( useCase.provider1Mock ).getFileAcl( useCase.path1, forceInheriting );
  }

  @Test
  void testSetFileAclSuccess() throws Exception {
    FileAclMultipleProviderUseCase useCase = new FileAclMultipleProviderUseCase();
    IGenericFileAcl acl1 = mock( IGenericFileAcl.class );
    IGenericFileAcl acl2 = mock( IGenericFileAcl.class );

    useCase.service.setFileAcl( useCase.path1, acl1 );
    useCase.service.setFileAcl( useCase.path2, acl2 );

    verify( useCase.provider1Mock ).setFileAcl( useCase.path1, acl1 );
    verify( useCase.provider2Mock ).setFileAcl( useCase.path2, acl2 );
  }

  @Test
  void testSetFileAclPathNotFound() throws Exception {
    FileAclMultipleProviderUseCase useCase = new FileAclMultipleProviderUseCase();
    IGenericFileAcl acl = mock( IGenericFileAcl.class );

    doReturn( false ).when( useCase.provider1Mock ).owns( useCase.path1 );

    NotFoundException exception = assertThrows( NotFoundException.class,
      () -> useCase.service.setFileAcl( useCase.path1, acl ) );

    assertEquals( "Path not found '" + useCase.path1 + "'.", exception.getMessage() );
    verify( useCase.provider1Mock, never() ).setFileAcl( any(), any() );
  }

  @Test
  void testSetFileAclException() throws Exception {
    FileAclMultipleProviderUseCase useCase = new FileAclMultipleProviderUseCase();
    IGenericFileAcl acl = mock( IGenericFileAcl.class );

    doThrow( new OperationFailedException( "Set ACL failed." ) ).when( useCase.provider1Mock )
      .setFileAcl( useCase.path1, acl );

    OperationFailedException exception = assertThrows( OperationFailedException.class,
      () -> useCase.service.setFileAcl( useCase.path1, acl ) );

    assertEquals( "Set ACL failed.", exception.getMessage() );
    verify( useCase.provider1Mock ).setFileAcl( useCase.path1, acl );
  }

  // region validateFileAcl
  @Test
  void testValidateFileAclSuccess() throws Exception {
    FileAclMultipleProviderUseCase useCase = new FileAclMultipleProviderUseCase();
    IGenericFileAcl acl1 = mock( IGenericFileAcl.class );
    IGenericFileAcl acl2 = mock( IGenericFileAcl.class );

    doReturn( true ).when( useCase.provider1Mock ).validateFileAcl( acl1 );
    doReturn( true ).when( useCase.provider2Mock ).validateFileAcl( acl2 );

    assertTrue( useCase.service.validateFileAcl( useCase.path1, acl1 ) );
    assertTrue( useCase.service.validateFileAcl( useCase.path2, acl2 ) );
    verify( useCase.provider1Mock ).validateFileAcl( acl1 );
    verify( useCase.provider2Mock ).validateFileAcl( acl2 );
  }

  @Test
  void testValidateFileAclReturnsFalse() throws Exception {
    FileAclMultipleProviderUseCase useCase = new FileAclMultipleProviderUseCase();
    IGenericFileAcl acl = mock( IGenericFileAcl.class );

    doReturn( false ).when( useCase.provider1Mock ).validateFileAcl( acl );

    assertFalse( useCase.service.validateFileAcl( useCase.path1, acl ) );
    verify( useCase.provider1Mock ).validateFileAcl( acl );
  }

  @Test
  void testValidateFileAclPathNotFound() throws Exception {
    FileAclMultipleProviderUseCase useCase = new FileAclMultipleProviderUseCase();
    IGenericFileAcl acl = mock( IGenericFileAcl.class );

    doReturn( false ).when( useCase.provider1Mock ).owns( useCase.path1 );

    NotFoundException exception = assertThrows( NotFoundException.class,
      () -> useCase.service.validateFileAcl( useCase.path1, acl ) );

    assertEquals( "Path not found '" + useCase.path1 + "'.", exception.getMessage() );
    verify( useCase.provider1Mock, never() ).validateFileAcl( any() );
  }

  @Test
  void testValidateFileAclException() throws Exception {
    FileAclMultipleProviderUseCase useCase = new FileAclMultipleProviderUseCase();
    IGenericFileAcl acl = mock( IGenericFileAcl.class );

    doThrow( new OperationFailedException( "Validate ACL failed." ) ).when( useCase.provider1Mock )
      .validateFileAcl( acl );

    OperationFailedException exception = assertThrows( OperationFailedException.class,
      () -> useCase.service.validateFileAcl( useCase.path1, acl ) );

    assertEquals( "Validate ACL failed.", exception.getMessage() );
    verify( useCase.provider1Mock ).validateFileAcl( acl );
  }
  // endregion

  // region createFile
  private static CreateFileOptions createFileOptions( boolean overwrite ) {
    CreateFileOptions options = new CreateFileOptions();
    options.setOverwrite( overwrite );
    return options;
  }

  private static class CreateFileMultipleProviderUseCase extends MultipleProviderUseCase {
    public final GenericFilePath path1;
    public final GenericFilePath path2;
    public final java.io.InputStream content;

    public CreateFileMultipleProviderUseCase() throws InvalidGenericFileProviderException {
      super();

      try {
        path1 = GenericFilePath.parseRequired( "/provider1/test/file.txt" );
        path2 = GenericFilePath.parseRequired( "/provider2/test/file.txt" );
      } catch ( InvalidPathException e ) {
        throw new RuntimeException( e );
      }
      content = new java.io.ByteArrayInputStream( "test content".getBytes() );

      doReturn( true ).when( provider1Mock ).owns( path1 );
      doReturn( false ).when( provider1Mock ).owns( path2 );
      doReturn( false ).when( provider2Mock ).owns( path1 );
      doReturn( true ).when( provider2Mock ).owns( path2 );
    }
  }

  @Test
  void testCreateFileSuccessfullyByProvider1() throws Exception {
    CreateFileMultipleProviderUseCase useCase = new CreateFileMultipleProviderUseCase();

    doNothing().when( useCase.provider1Mock )
      .createFile( useCase.path1, useCase.content, createFileOptions( true ) );

    useCase.service.createFile( useCase.path1, useCase.content, createFileOptions( true ) );

    verify( useCase.provider1Mock ).createFile( useCase.path1, useCase.content, createFileOptions( true ) );
    verify( useCase.provider2Mock, never() ).createFile( any(), any(), any() );
  }

  @Test
  void testCreateFileSuccessfullyByProvider1WithOverwriteFalse() throws Exception {
    CreateFileMultipleProviderUseCase useCase = new CreateFileMultipleProviderUseCase();

    doNothing().when( useCase.provider1Mock )
      .createFile( useCase.path1, useCase.content, createFileOptions( false ) );

    useCase.service.createFile( useCase.path1, useCase.content, createFileOptions( false ) );

    verify( useCase.provider1Mock ).createFile( useCase.path1, useCase.content, createFileOptions( false ) );
    verify( useCase.provider2Mock, never() ).createFile( any(), any(), any() );
  }

  @Test
  void testCreateFileSuccessfullyByProvider2() throws Exception {
    CreateFileMultipleProviderUseCase useCase = new CreateFileMultipleProviderUseCase();

    doNothing().when( useCase.provider2Mock )
      .createFile( useCase.path2, useCase.content, createFileOptions( true ) );

    useCase.service.createFile( useCase.path2, useCase.content, createFileOptions( true ) );

    verify( useCase.provider2Mock ).createFile( useCase.path2, useCase.content, createFileOptions( true ) );
    verify( useCase.provider1Mock, never() ).createFile( any(), any(), any() );
  }

  @Test
  void testCreateFileFileAlreadyExists() throws Exception {
    CreateFileMultipleProviderUseCase useCase = new CreateFileMultipleProviderUseCase();

    doThrow( new ConflictException( "File already exists." ) ).when( useCase.provider1Mock )
      .createFile( useCase.path1, useCase.content, createFileOptions( true ) );

    ConflictException exception = assertThrows( ConflictException.class,
      () -> useCase.service.createFile( useCase.path1, useCase.content, createFileOptions( true ) ) );

    assertEquals( "File already exists.", exception.getMessage() );
    verify( useCase.provider1Mock ).createFile( useCase.path1, useCase.content, createFileOptions( true ) );
  }

  @Test
  void testCreateFileThrowsNotFoundExceptionWhenPathNotOwned() throws Exception {
    CreateFileMultipleProviderUseCase useCase = new CreateFileMultipleProviderUseCase();

    doReturn( false ).when( useCase.provider1Mock ).owns( useCase.path1 );
    doReturn( false ).when( useCase.provider2Mock ).owns( useCase.path1 );

    NotFoundException exception = assertThrows( NotFoundException.class,
      () -> useCase.service.createFile( useCase.path1, useCase.content, createFileOptions( true ) ) );

    assertEquals( "Path not found '" + useCase.path1 + "'.", exception.getMessage() );
    verify( useCase.provider1Mock, never() ).createFile( any(), any(), any() );
    verify( useCase.provider2Mock, never() ).createFile( any(), any(), any() );
  }

  @Test
  void testCreateFileException() throws Exception {
    CreateFileMultipleProviderUseCase useCase = new CreateFileMultipleProviderUseCase();

    doThrow( new OperationFailedException( "Create file failed." ) ).when( useCase.provider1Mock )
      .createFile( useCase.path1, useCase.content, createFileOptions( true ) );

    OperationFailedException exception = assertThrows( OperationFailedException.class,
      () -> useCase.service.createFile( useCase.path1, useCase.content, createFileOptions( true ) ) );

    assertEquals( "Create file failed.", exception.getMessage() );
    verify( useCase.provider1Mock ).createFile( useCase.path1, useCase.content, createFileOptions( true ) );
  }

  @Test
  void testCreateFileWithStringPath() throws Exception {
    CreateFileMultipleProviderUseCase useCase = new CreateFileMultipleProviderUseCase();

    doNothing().when( useCase.provider1Mock )
      .createFile( useCase.path1, useCase.content, createFileOptions( true ) );

    useCase.service.createFile( GenericFilePath.parseRequired( useCase.path1.toString() ), useCase.content,
      createFileOptions( true ) );

    verify( useCase.provider1Mock ).createFile( useCase.path1, useCase.content, createFileOptions( true ) );
  }

  @Test
  void testCreateFileWithStringPathInvalid() throws Exception {
    CreateFileMultipleProviderUseCase useCase = new CreateFileMultipleProviderUseCase();

    InvalidPathException exception = assertThrows( InvalidPathException.class,
      () -> useCase.service.createFile( GenericFilePath.parseRequired( "" ), useCase.content,
        createFileOptions( true ) ) );

    assertNotNull( exception.getMessage() );
  }

  @Test
  void testCreateFileSuccessfullyByProvider2WithOverwriteFalse() throws Exception {
    CreateFileMultipleProviderUseCase useCase = new CreateFileMultipleProviderUseCase();

    doNothing().when( useCase.provider2Mock )
      .createFile( useCase.path2, useCase.content, createFileOptions( false ) );

    useCase.service.createFile( useCase.path2, useCase.content, createFileOptions( false ) );

    verify( useCase.provider2Mock ).createFile( useCase.path2, useCase.content, createFileOptions( false ) );
    verify( useCase.provider1Mock, never() ).createFile( any(), any(), any() );
  }

  @Test
  void testCreateFileFileAlreadyExistsWithOverwriteFalse() throws Exception {
    CreateFileMultipleProviderUseCase useCase = new CreateFileMultipleProviderUseCase();

    doThrow( new ConflictException( "File already exists." ) ).when( useCase.provider1Mock )
      .createFile( useCase.path1, useCase.content, createFileOptions( false ) );

    ConflictException exception = assertThrows( ConflictException.class,
      () -> useCase.service.createFile( useCase.path1, useCase.content, createFileOptions( false ) ) );

    assertEquals( "File already exists.", exception.getMessage() );
    verify( useCase.provider1Mock ).createFile( useCase.path1, useCase.content, createFileOptions( false ) );
  }

  @Test
  void testCreateFileExceptionByProvider2() throws Exception {
    CreateFileMultipleProviderUseCase useCase = new CreateFileMultipleProviderUseCase();

    doThrow( new OperationFailedException( "Create file failed on provider2." ) ).when( useCase.provider2Mock )
      .createFile( useCase.path2, useCase.content, createFileOptions( true ) );

    OperationFailedException exception = assertThrows( OperationFailedException.class,
      () -> useCase.service.createFile( useCase.path2, useCase.content, createFileOptions( true ) ) );

    assertEquals( "Create file failed on provider2.", exception.getMessage() );
    verify( useCase.provider2Mock ).createFile( useCase.path2, useCase.content, createFileOptions( true ) );
  }
  // endregion

  // region setFileContent
  private static class SetFileContentMultipleProviderUseCase extends MultipleProviderUseCase {
    public final GenericFilePath path1;
    public final GenericFilePath path2;
    public final java.io.InputStream content;

    public SetFileContentMultipleProviderUseCase() throws InvalidGenericFileProviderException {
      super();

      try {
        path1 = GenericFilePath.parseRequired( "/provider1/test/file.txt" );
        path2 = GenericFilePath.parseRequired( "/provider2/test/file.txt" );
      } catch ( InvalidPathException e ) {
        throw new RuntimeException( e );
      }

      content = new java.io.ByteArrayInputStream( "updated content".getBytes() );

      doReturn( true ).when( provider1Mock ).owns( path1 );
      doReturn( false ).when( provider1Mock ).owns( path2 );
      doReturn( false ).when( provider2Mock ).owns( path1 );
      doReturn( true ).when( provider2Mock ).owns( path2 );
    }
  }

  @Test
  void testSetFileContentSuccessfullyByProvider1() throws Exception {
    SetFileContentMultipleProviderUseCase useCase = new SetFileContentMultipleProviderUseCase();

    doNothing().when( useCase.provider1Mock ).setFileContent( useCase.path1, useCase.content );

    useCase.service.setFileContent( useCase.path1, useCase.content );

    verify( useCase.provider1Mock ).setFileContent( useCase.path1, useCase.content );
    verify( useCase.provider2Mock, never() ).setFileContent( any(), any() );
  }

  @Test
  void testSetFileContentSuccessfullyByProvider2() throws Exception {
    SetFileContentMultipleProviderUseCase useCase = new SetFileContentMultipleProviderUseCase();

    doNothing().when( useCase.provider2Mock ).setFileContent( useCase.path2, useCase.content );

    useCase.service.setFileContent( useCase.path2, useCase.content );

    verify( useCase.provider2Mock ).setFileContent( useCase.path2, useCase.content );
    verify( useCase.provider1Mock, never() ).setFileContent( any(), any() );
  }

  @Test
  void testSetFileContentThrowsNotFoundExceptionWhenPathNotOwned() throws Exception {
    SetFileContentMultipleProviderUseCase useCase = new SetFileContentMultipleProviderUseCase();

    doReturn( false ).when( useCase.provider1Mock ).owns( useCase.path1 );
    doReturn( false ).when( useCase.provider2Mock ).owns( useCase.path1 );

    NotFoundException exception = assertThrows( NotFoundException.class,
      () -> useCase.service.setFileContent( useCase.path1, useCase.content ) );

    assertEquals( "Path not found '" + useCase.path1 + "'.", exception.getMessage() );
    verify( useCase.provider1Mock, never() ).setFileContent( any(), any() );
    verify( useCase.provider2Mock, never() ).setFileContent( any(), any() );
  }

  @Test
  void testSetFileContentException() throws Exception {
    SetFileContentMultipleProviderUseCase useCase = new SetFileContentMultipleProviderUseCase();

    doThrow( new OperationFailedException( "Set file content failed." ) )
      .when( useCase.provider1Mock ).setFileContent( useCase.path1, useCase.content );

    OperationFailedException exception = assertThrows( OperationFailedException.class,
      () -> useCase.service.setFileContent( useCase.path1, useCase.content ) );

    assertEquals( "Set file content failed.", exception.getMessage() );
    verify( useCase.provider1Mock ).setFileContent( useCase.path1, useCase.content );
  }

  @Test
  void testSetFileContentWithStringPath() throws Exception {
    SetFileContentMultipleProviderUseCase useCase = new SetFileContentMultipleProviderUseCase();

    doNothing().when( useCase.provider1Mock ).setFileContent( useCase.path1, useCase.content );

    useCase.service.setFileContent( useCase.path1.toString(), useCase.content );

    verify( useCase.provider1Mock ).setFileContent( useCase.path1, useCase.content );
  }

  @Test
  void testSetFileContentWithStringPathInvalid() throws Exception {
    SetFileContentMultipleProviderUseCase useCase = new SetFileContentMultipleProviderUseCase();

    InvalidPathException exception = assertThrows( InvalidPathException.class,
      () -> useCase.service.setFileContent( "", useCase.content ) );

    assertNotNull( exception.getMessage() );
  }

  @Test
  void testSetFileContentExceptionByProvider2() throws Exception {
    SetFileContentMultipleProviderUseCase useCase = new SetFileContentMultipleProviderUseCase();

    doThrow( new OperationFailedException( "Set file content failed on provider2." ) )
      .when( useCase.provider2Mock ).setFileContent( useCase.path2, useCase.content );

    OperationFailedException exception = assertThrows( OperationFailedException.class,
      () -> useCase.service.setFileContent( useCase.path2, useCase.content ) );

    assertEquals( "Set file content failed on provider2.", exception.getMessage() );
    verify( useCase.provider2Mock ).setFileContent( useCase.path2, useCase.content );
  }
  // endregion
}
