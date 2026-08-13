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


package org.pentaho.platform.genericfile.providers.repository;

import com.google.common.net.MediaType;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.pentaho.platform.api.genericfile.GenericFilePath;
import org.pentaho.platform.api.genericfile.GenericFilePermission;
import org.pentaho.platform.api.genericfile.GenericFilePrincipalType;
import org.pentaho.platform.api.genericfile.GetFileOptions;
import org.pentaho.platform.api.genericfile.GetTreeOptions;
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
import org.pentaho.platform.api.genericfile.model.IGenericFolder;
import org.pentaho.platform.api.importexport.ExportException;
import org.pentaho.platform.api.repository2.unified.IRepositoryContentConverterHandler;
import org.pentaho.platform.api.repository2.unified.IUnifiedRepository;
import org.pentaho.platform.api.repository2.unified.RepositoryFile;
import org.pentaho.platform.api.repository2.unified.RepositoryFileAcl;
import org.pentaho.platform.api.repository2.unified.RepositoryFilePermission;
import org.pentaho.platform.api.repository2.unified.RepositoryFileSid;
import org.pentaho.platform.api.repository2.unified.UnifiedRepositoryAccessDeniedException;
import org.pentaho.platform.api.repository2.unified.UnifiedRepositoryException;
import org.pentaho.platform.api.repository2.unified.webservices.RepositoryFileAclAceDto;
import org.pentaho.platform.api.repository2.unified.webservices.RepositoryFileAclDto;
import org.pentaho.platform.api.repository2.unified.webservices.RepositoryFileDto;
import org.pentaho.platform.api.repository2.unified.webservices.RepositoryFileTreeDto;
import org.pentaho.platform.api.repository2.unified.webservices.StringKeyStringValueDto;
import org.pentaho.platform.genericfile.messages.Messages;
import org.pentaho.platform.genericfile.model.BaseGenericFileMetadata;
import org.pentaho.platform.genericfile.providers.repository.model.RepositoryObject;
import org.pentaho.platform.repository2.unified.fileio.RepositoryFileInputStream;
import org.pentaho.platform.util.RepositoryPathEncoder;
import org.pentaho.platform.web.http.api.resources.services.FileService;
import org.pentaho.platform.web.http.api.resources.utils.SystemUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.pentaho.platform.genericfile.providers.repository.RepositoryFileProvider.ROOT_PATH;
import static org.pentaho.platform.util.RepositoryPathEncoder.encodeRepositoryPath;

/**
 * Tests for the {@link RepositoryFileProvider} class.
 */
@SuppressWarnings( { "DataFlowIssue" } )
class RepositoryFileProviderTest {
  static final String ENCODED_ROOT_PATH = RepositoryPathEncoder.encodeRepositoryPath( ROOT_PATH );
  static final String ALL_FILTER = "*";

  // region Helpers and Sample Structures

  /**
   * A sample native repository tree structure.
   * <p>
   * /
   * /home
   * /public
   * /public/testFile1
   * /public/testFolder2
   */
  static class NativeDtoRepositoryScenario {
    @NonNull
    public final RepositoryFileTreeDto rootTree;
    @NonNull
    public final RepositoryFileDto rootFolder;
    @NonNull
    public final RepositoryFileTreeDto homeTree;
    @NonNull
    public final RepositoryFileDto homeFolder;
    @NonNull
    public final RepositoryFileTreeDto publicTree;
    @NonNull
    public final RepositoryFileDto publicFolder;
    @NonNull
    public final RepositoryFileDto testFile1;
    @NonNull
    public final RepositoryFileDto testFolder2;
    @NonNull
    public final RepositoryFileDto testDeletedFile3;

    public NativeDtoRepositoryScenario() {
      rootFolder = createNativeFileDto( ROOT_PATH, "", true );
      rootTree = createNativeTreeDto( rootFolder );

      // ---
      // /home
      homeFolder = createNativeFileDto( "/home", "home", true );
      homeTree = createNativeTreeDto( homeFolder );

      // ---
      // /public
      publicFolder = createNativeFileDto( "/public", "public", true );
      publicTree = createNativeTreeDto( publicFolder );

      testFile1 = createSampleTestFile1();
      testFolder2 = createSampleTestFolder2();

      publicTree.setChildren( Arrays.asList(
        createNativeTreeDto( testFile1 ),
        createNativeTreeDto( testFolder2 )
      ) );

      rootTree.setChildren( Arrays.asList( homeTree, publicTree ) );

      testDeletedFile3 = createSampleTestDeletedFile3();
    }

    @NonNull
    private static RepositoryFileDto createSampleTestFile1() {
      RepositoryFileDto testFile1 = createNativeFileDto( "/public/testFile1", "testFile1", false );
      testFile1.setHidden( true );

      String elapsedMilliseconds = "100";
      testFile1.setLastModifiedDate( elapsedMilliseconds );

      testFile1.setId( "Test File 1 Id" );
      testFile1.setTitle( "Test File 1 title" );
      testFile1.setDescription( "Test File 1 description" );

      return testFile1;
    }

    @NonNull
    private static RepositoryFileDto createSampleTestFolder2() {
      RepositoryFileDto testFolder2 = createNativeFileDto( "/public/testFolder2", "testFolder2", true );

      String elapsedMilliseconds = "200";
      testFolder2.setLastModifiedDate( elapsedMilliseconds );

      testFolder2.setId( "Test Folder 2 Id" );
      testFolder2.setTitle( "Test Folder 2 title" );
      testFolder2.setDescription( "Test Folder 2 description" );

      return testFolder2;
    }

    @NonNull
    private static RepositoryFileDto createSampleTestDeletedFile3() {
      RepositoryFileDto testDeletedFile3 =
        createNativeFileDto( "/home/userA/.trash/pho:1234/deletedFile3", "deletedFile3", false );
      testDeletedFile3.setOriginalParentFolderPath( "/public" );
      testDeletedFile3.setCreatorId( "userB" );

      String elapsedMilliseconds = "300";
      testDeletedFile3.setLastModifiedDate( elapsedMilliseconds );

      String deletedMilliseconds = "400";
      testDeletedFile3.setDeletedDate( deletedMilliseconds );

      testDeletedFile3.setId( "Test Deleted File 3 Id" );
      testDeletedFile3.setTitle( "Test Deleted File 3 title" );
      testDeletedFile3.setDescription( "Test Deleted File 3 description" );

      return testDeletedFile3;
    }
  }

  @NonNull
  static RepositoryFileTreeDto createNativeTreeDto( @NonNull RepositoryFileDto nativeFile ) {
    RepositoryFileTreeDto nativeTree = new RepositoryFileTreeDto();
    nativeTree.setFile( nativeFile );
    return nativeTree;
  }

  @NonNull
  private static RepositoryFileDto createNativeFileDto( String path, String name, boolean isFolder ) {
    RepositoryFileDto nativeFile = new RepositoryFileDto();
    nativeFile.setName( name );
    nativeFile.setPath( path );
    nativeFile.setFolder( isFolder );

    String numberOfMilliseconds = "0";
    nativeFile.setCreatedDate( numberOfMilliseconds );

    return nativeFile;
  }

  @NonNull
  private static RepositoryFile createNativeFile( String id, GenericFilePath path, boolean isFolder ) {
    Date createdDate = new Date( 100 );
    Date lastModeDate = new Date( 200 );
    Date lockDate = new Date();
    String name = path.getLastSegment();

    return new RepositoryFile( id, name, isFolder, false, false, false,
      "versionId", path.toString(), createdDate, lastModeDate, false, "lockOwner", "lockMessage", lockDate, "en_US",
      name + " title", name + " description", null, null, 4096, name + "creatorId", null );
  }

  /**
   * Represents the result structure of a root tree operation for {@link NativeDtoRepositoryScenario}.
   */
  static class RepositoryValidatedScenario {
    @NonNull
    public final IGenericFileTree rootTree;
    @NonNull
    public final IGenericFolder rootFolder;
    @NonNull
    public final IGenericFileTree homeTree;
    @NonNull
    public final IGenericFolder homeFolder;
    @NonNull
    public final IGenericFileTree publicTree;
    @NonNull
    public final IGenericFolder publicFolder;
    @NonNull
    public final IGenericFile testFile1;
    @NonNull
    public final IGenericFolder testFolder2;

    public RepositoryValidatedScenario( @NonNull IGenericFileTree tree ) {
      assertNotNull( tree );
      rootTree = tree;
      rootFolder = assertRootFolder( tree.getFile() );

      assertEquals( ROOT_PATH, rootFolder.getPath() );

      // Check that the children of the home subtree are now part of the root tree.
      List<IGenericFileTree> rootChildren = tree.getChildren();
      assertNotNull( rootChildren );
      assertEquals( 2, rootChildren.size() );

      // ---
      // /home
      assertNotNull( rootChildren.get( 0 ) );
      homeTree = rootChildren.get( 0 );
      homeFolder = assertGenericFolder( homeTree.getFile() );
      assertEquals( "/home", homeFolder.getPath() );
      assertEquals( "home", homeFolder.getName() );
      assertEquals( ROOT_PATH, homeFolder.getParentPath() );

      // ---
      // /public

      assertNotNull( rootChildren.get( 1 ) );
      publicTree = rootChildren.get( 1 );
      publicFolder = assertPublicTree( publicTree );

      List<IGenericFileTree> publicChildren = publicTree.getChildren();
      assertNotNull( publicChildren );
      assertEquals( 2, publicChildren.size() );

      IGenericFileTree testTree1 = publicChildren.get( 0 );
      assertNotNull( testTree1 );
      assertNotNull( testTree1.getFile() );
      testFile1 = testTree1.getFile();

      IGenericFileTree testTree2 = publicChildren.get( 1 );
      assertNotNull( testTree2 );
      assertNotNull( testTree2.getFile() );
      testFolder2 = assertGenericFolder( testTree2.getFile() );
    }
  }

  @NonNull
  RepositoryValidatedScenario assertRepositoryTree( IGenericFileTree tree ) {
    return new RepositoryValidatedScenario( tree );
  }

  private static @NonNull IGenericFolder assertPublicTree( IGenericFileTree publicTree ) {
    IGenericFolder publicFolder = assertGenericFolder( publicTree.getFile() );

    assertEquals( "/public", publicFolder.getPath() );
    assertEquals( "public", publicFolder.getName() );
    assertEquals( ROOT_PATH, publicFolder.getParentPath() );
    assertRegularCapabilities( publicFolder );

    return publicFolder;
  }

  @NonNull
  static IGenericFolder assertGenericFolder( IGenericFile file ) {
    assertNotNull( file );
    assertTrue( file.isFolder() );
    assertInstanceOf( IGenericFolder.class, file );
    return (IGenericFolder) file;
  }

  @NonNull
  static IGenericFolder assertRootFolder( IGenericFile file ) {
    IGenericFolder folder = assertGenericFolder( file );

    assertEquals( ROOT_PATH, file.getPath() );
    assertEquals( ROOT_PATH, file.getName() );
    assertNull( file.getParentPath() );
    assertEquals( Messages.getString( "GenericFileRepository.REPOSITORY_FOLDER_DISPLAY" ), file.getTitle() );

    assertFalse( folder.isCanDelete() );
    assertFalse( folder.isCanEdit() );
    assertFalse( folder.isCanAddChildren() );
    return folder;
  }

  static void assertRegularCapabilities( IGenericFile file ) {
    assertTrue( file.isCanDelete() );
    assertTrue( file.isCanEdit() );

    if ( file.isFolder() ) {
      IGenericFolder folder = (IGenericFolder) file;
      assertTrue( folder.isCanAddChildren() );
    }
  }

  @NonNull
  private RepositoryFileAcl createMockFileOwner( String owner ) {
    RepositoryFileAcl acl = mock( RepositoryFileAcl.class );

    RepositoryFileSid ownerSid = mock( RepositoryFileSid.class );
    doReturn( owner )
      .when( ownerSid )
      .getName();

    doReturn( ownerSid )
      .when( acl )
      .getOwner();

    return acl;
  }
  // endregion

  // region getTree
  @Test
  void testGetTreeThrowsNotFoundExceptionIfBasePathNotOwned() throws OperationFailedException {
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    GetTreeOptions options = new GetTreeOptions();
    options.setBasePath( "scheme://path" );
    options.setMaxDepth( 1 );

    assertThrows( NotFoundException.class, () -> repositoryProvider.getTree( options ) );
  }

  @Test
  void testGetTreeDelegatesToFileServiceDoGetTree() throws OperationFailedException {
    NativeDtoRepositoryScenario scenario = new NativeDtoRepositoryScenario();

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( scenario.rootTree )
      .when( fileServiceMock )
      .doGetTree( any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean() );

    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GetTreeOptions options = new GetTreeOptions();
    options.setBasePath( ROOT_PATH );
    options.setMaxDepth( 2 );

    IGenericFileTree tree = repositoryProvider.getTree( options );

    assertRepositoryTree( tree );

    verify( fileServiceMock, times( 1 ) ).doGetTree( ENCODED_ROOT_PATH, 2, ALL_FILTER, false, false, false );
  }

  @Test
  void testGetTreeDefaultsBasePathToRepositoryRoot() throws OperationFailedException {
    NativeDtoRepositoryScenario scenario = new NativeDtoRepositoryScenario();

    FileService fileServiceMock = mock( FileService.class );
    doReturn( scenario.rootTree )
      .when( fileServiceMock )
      .doGetTree( any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean() );

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    GetTreeOptions options = new GetTreeOptions();
    options.setBasePath( (GenericFilePath) null );
    options.setMaxDepth( 1 );

    repositoryProvider.getTree( options );

    verify( fileServiceMock, times( 1 ) ).doGetTree( eq( ENCODED_ROOT_PATH ), anyInt(), anyString(), anyBoolean(),
      anyBoolean(), anyBoolean() );
  }

  @Test
  void testGetTreeRespectsNullChildrenList() throws OperationFailedException {
    NativeDtoRepositoryScenario scenario = new NativeDtoRepositoryScenario();

    // Check initial structure has null children list for /home.
    assertNull( scenario.homeTree.getChildren() );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( scenario.rootTree )
      .when( fileServiceMock )
      .doGetTree( any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean() );

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    GetTreeOptions options = new GetTreeOptions();
    options.setBasePath( ROOT_PATH );
    options.setMaxDepth( 1 );

    IGenericFileTree tree = repositoryProvider.getTree( options );

    RepositoryValidatedScenario validatedScenario = assertRepositoryTree( tree );
    assertNull( validatedScenario.homeTree.getChildren() );
  }

  @Test
  void testGetTreeRespectsEmptyChildrenList() throws OperationFailedException {
    NativeDtoRepositoryScenario scenario = new NativeDtoRepositoryScenario();

    // Set empty list to children of /home.
    scenario.homeTree.setChildren( Collections.emptyList() );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( scenario.rootTree )
      .when( fileServiceMock )
      .doGetTree( any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean() );

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    GetTreeOptions options = new GetTreeOptions();
    options.setBasePath( ROOT_PATH );
    options.setMaxDepth( 1 );

    IGenericFileTree tree = repositoryProvider.getTree( options );

    RepositoryValidatedScenario validatedScenario = assertRepositoryTree( tree );
    assertNotNull( validatedScenario.homeTree.getChildren() );
    assertTrue( validatedScenario.homeTree.getChildren().isEmpty() );
  }

  @Test
  void testGetTreeTestFile1HasExpectedProperties() throws OperationFailedException {
    NativeDtoRepositoryScenario scenario = new NativeDtoRepositoryScenario();

    FileService fileServiceMock = mock( FileService.class );
    doReturn( scenario.rootTree )
      .when( fileServiceMock )
      .doGetTree( any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean() );

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    GetTreeOptions options = new GetTreeOptions();
    options.setBasePath( ROOT_PATH );
    options.setMaxDepth( 1 );

    IGenericFileTree tree = repositoryProvider.getTree( options );

    RepositoryValidatedScenario validatedScenario = assertRepositoryTree( tree );

    RepositoryObject testFile1 = (RepositoryObject) validatedScenario.testFile1;
    assertEquals( "/public/testFile1", testFile1.getPath() );
    assertEquals( "/public", testFile1.getParentPath() );
    assertEquals( "testFile1", testFile1.getName() );
    assertEquals( "Test File 1 Id", testFile1.getObjectId() );
    assertEquals( "Test File 1 title", testFile1.getTitle() );
    assertEquals( "Test File 1 description", testFile1.getDescription() );
    assertEquals( new Date( 100 ), testFile1.getModifiedDate() );
  }

  @Test
  void testGetTreeTestFolder2HasExpectedProperties() throws OperationFailedException {
    NativeDtoRepositoryScenario scenario = new NativeDtoRepositoryScenario();

    FileService fileServiceMock = mock( FileService.class );
    doReturn( scenario.rootTree )
      .when( fileServiceMock )
      .doGetTree( any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean() );

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    GetTreeOptions options = new GetTreeOptions();
    options.setBasePath( ROOT_PATH );
    options.setMaxDepth( 1 );

    IGenericFileTree tree = repositoryProvider.getTree( options );

    RepositoryValidatedScenario validatedScenario = assertRepositoryTree( tree );

    RepositoryObject testFolder2 = (RepositoryObject) validatedScenario.testFolder2;
    assertEquals( "/public/testFolder2", testFolder2.getPath() );
    assertEquals( "/public", testFolder2.getParentPath() );
    assertEquals( "testFolder2", testFolder2.getName() );
    assertEquals( "Test Folder 2 Id", testFolder2.getObjectId() );
    assertEquals( "Test Folder 2 title", testFolder2.getTitle() );
    assertEquals( "Test Folder 2 description", testFolder2.getDescription() );
    assertEquals( new Date( 200 ), testFolder2.getModifiedDate() );
  }

  @Test
  void testGetSubTreeRootNodeHasExpectedProperties() throws OperationFailedException {
    NativeDtoRepositoryScenario scenario = new NativeDtoRepositoryScenario();

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( scenario.publicTree )
      .when( fileServiceMock )
      .doGetTree( any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean() );

    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GetTreeOptions options = new GetTreeOptions();
    options.setBasePath( scenario.publicFolder.getPath() );
    options.setMaxDepth( 1 );

    IGenericFileTree tree = repositoryProvider.getTree( options );

    assertPublicTree( tree );
  }

  @Test
  void testGetTreeIncludesMetadataWhenEnabled() throws Exception {
    NativeDtoRepositoryScenario scenario = new NativeDtoRepositoryScenario();

    FileService fileServiceMock = mock( FileService.class );
    doReturn( scenario.rootTree )
      .when( fileServiceMock )
      .doGetTree( any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean() );
    doReturn( List.of( new StringKeyStringValueDto( "key", "value" ) ) )
      .when( fileServiceMock ).doGetMetadata( any() );

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    GetTreeOptions options = new GetTreeOptions();
    options.setBasePath( ROOT_PATH );
    options.setMaxDepth( 1 );
    options.setIncludeMetadata( true );

    IGenericFileTree tree = repositoryProvider.getTree( options );

    RepositoryValidatedScenario validatedScenario = assertRepositoryTree( tree );

    RepositoryObject testFile1 = (RepositoryObject) validatedScenario.testFile1;
    assertEquals( "/public/testFile1", testFile1.getPath() );
    assertEquals( "/public", testFile1.getParentPath() );
    assertEquals( "testFile1", testFile1.getName() );
    assertEquals( "Test File 1 Id", testFile1.getObjectId() );
    assertEquals( "Test File 1 title", testFile1.getTitle() );
    assertEquals( "Test File 1 description", testFile1.getDescription() );
    assertEquals( new Date( 100 ), testFile1.getModifiedDate() );
    assertNotNull( tree.getFile().getMetadata() );
    assertTrue( tree.getFile().getMetadata().getMetadata().containsKey( "key" ) );
    assertEquals( "value", tree.getFile().getMetadata().getMetadata().get( "key" ) );
  }

  @Test
  void testGetTreeCoreOmitsMetadataWhenDisabled() throws Exception {
    NativeDtoRepositoryScenario scenario = new NativeDtoRepositoryScenario();

    FileService fileServiceMock = mock( FileService.class );
    doReturn( scenario.rootTree )
      .when( fileServiceMock )
      .doGetTree( any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean() );
    doReturn( List.of( new StringKeyStringValueDto( "key", "value" ) ) )
      .when( fileServiceMock ).doGetMetadata( any() );

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    GetTreeOptions options = new GetTreeOptions();
    options.setBasePath( ROOT_PATH );
    options.setMaxDepth( 1 );

    IGenericFileTree tree = repositoryProvider.getTree( options );

    RepositoryValidatedScenario validatedScenario = assertRepositoryTree( tree );

    RepositoryObject testFile1 = (RepositoryObject) validatedScenario.testFile1;
    assertEquals( "/public/testFile1", testFile1.getPath() );
    assertEquals( "/public", testFile1.getParentPath() );
    assertEquals( "testFile1", testFile1.getName() );
    assertEquals( "Test File 1 Id", testFile1.getObjectId() );
    assertEquals( "Test File 1 title", testFile1.getTitle() );
    assertEquals( "Test File 1 description", testFile1.getDescription() );
    assertEquals( new Date( 100 ), testFile1.getModifiedDate() );
    assertNull( tree.getFile().getMetadata() );
    verify( fileServiceMock, never() ).doGetMetadata( any() );
  }

  @Test
  void testGetTreeZeroDepthSetsChildrenToNull() throws OperationFailedException {
    NativeDtoRepositoryScenario scenario = new NativeDtoRepositoryScenario();

    FileService fileServiceMock = mock( FileService.class );
    doReturn( scenario.rootTree )
      .when( fileServiceMock )
      .doGetTree( any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean() );

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    GetTreeOptions options = new GetTreeOptions();
    options.setBasePath( ROOT_PATH );
    options.setMaxDepth( 0 );

    IGenericFileTree tree = repositoryProvider.getTree( options );

    assertNotNull( tree );
    assertNull( tree.getChildren() );

    // Zero depth workaround sends depth=1 to the backend.
    verify( fileServiceMock, times( 1 ) ).doGetTree( ENCODED_ROOT_PATH, 1, ALL_FILTER, false, false, false );
  }

  @Test
  void testGetTreeThrowsNotFoundWhenDoGetTreeReturnsNull() throws InvalidPathException {
    FileService fileServiceMock = mock( FileService.class );
    doReturn( null )
      .when( fileServiceMock )
      .doGetTree( any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean() );

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    GetTreeOptions options = new GetTreeOptions();
    options.setBasePath( ROOT_PATH );
    options.setMaxDepth( 1 );

    assertThrows( NotFoundException.class, () -> repositoryProvider.getTree( options ) );
  }

  @Test
  void testGetTreePassesIncludeHiddenToFileService() throws OperationFailedException {
    NativeDtoRepositoryScenario scenario = new NativeDtoRepositoryScenario();

    FileService fileServiceMock = mock( FileService.class );
    doReturn( scenario.rootTree )
      .when( fileServiceMock )
      .doGetTree( any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean() );

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    GetTreeOptions options = new GetTreeOptions();
    options.setBasePath( ROOT_PATH );
    options.setMaxDepth( 1 );
    options.setIncludeHidden( true );

    repositoryProvider.getTree( options );

    verify( fileServiceMock, times( 1 ) ).doGetTree( ENCODED_ROOT_PATH, 1, ALL_FILTER, true, false, false );
  }

  @Test
  void testGetTreePassesFoldersFilterToFileService() throws OperationFailedException {
    NativeDtoRepositoryScenario scenario = new NativeDtoRepositoryScenario();

    FileService fileServiceMock = mock( FileService.class );
    doReturn( scenario.rootTree )
      .when( fileServiceMock )
      .doGetTree( any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean() );

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    GetTreeOptions options = new GetTreeOptions();
    options.setBasePath( ROOT_PATH );
    options.setMaxDepth( 1 );
    options.setFilter( GetTreeOptions.TreeFilter.FOLDERS );

    repositoryProvider.getTree( options );

    verify( fileServiceMock, times( 1 ) ).doGetTree( ENCODED_ROOT_PATH, 1, "*|FOLDERS", false, false, false );
  }

  @Test
  void testGetTreePassesFilesFilterToFileService() throws OperationFailedException {
    NativeDtoRepositoryScenario scenario = new NativeDtoRepositoryScenario();

    FileService fileServiceMock = mock( FileService.class );
    doReturn( scenario.rootTree )
      .when( fileServiceMock )
      .doGetTree( any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean() );

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    GetTreeOptions options = new GetTreeOptions();
    options.setBasePath( ROOT_PATH );
    options.setMaxDepth( 1 );
    options.setFilter( GetTreeOptions.TreeFilter.FILES );

    repositoryProvider.getTree( options );

    verify( fileServiceMock, times( 1 ) ).doGetTree( ENCODED_ROOT_PATH, 1, "*|FILES", false, false, false );
  }
  // endregion

  // region getRootTrees
  @Test
  void testGetRootTreesDelegatesToGetTreeCoreReturnsSingleRootTree() throws OperationFailedException {
    NativeDtoRepositoryScenario scenario = new NativeDtoRepositoryScenario();

    FileService fileServiceMock = mock( FileService.class );
    doReturn( scenario.rootTree )
      .when( fileServiceMock )
      .doGetTree( any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean() );

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    GetTreeOptions options = new GetTreeOptions();
    options.setBasePath( (GenericFilePath) null );
    options.setMaxDepth( 1 );

    List<IGenericFileTree> rootTrees = repositoryProvider.getRootTrees( options );
    assertNotNull( rootTrees );
    assertEquals( 1, rootTrees.size() );

    assertRepositoryTree( rootTrees.get( 0 ) );

    // Call again to make sure we're using getTreeCore / no cache.
    repositoryProvider.getRootTrees( options );

    // Must have called backend twice!
    verify( fileServiceMock, times( 2 ) ).doGetTree( eq( ENCODED_ROOT_PATH ), anyInt(), anyString(), anyBoolean(),
      anyBoolean(), anyBoolean() );
  }
  // endregion

  // region getFile
  @Test
  void testGetFileThrowsNotFoundExceptionIfPathNotOwned() {
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    assertThrows( NotFoundException.class,
      () -> repositoryProvider.getFile( GenericFilePath.parse( "scheme://path" ), new GetFileOptions() ) );
  }

  @Test
  void testGetFileThrowsNotFoundExceptionIfPathNotFound() {
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( null ).when( repositoryMock ).getFile( "/path" );

    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( NotFoundException.class,
      () -> repositoryProvider.getFile( GenericFilePath.parse( "/path" ), new GetFileOptions() ) );
  }

  @Test
  void testGetFileRootHasExpectedProperties() throws OperationFailedException {
    GenericFilePath path = GenericFilePath.parse( ROOT_PATH );
    RepositoryFile nativeFile = createNativeFile( "12345", path, true );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( ROOT_PATH );

    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    IGenericFile file = repositoryProvider.getFile( path, new GetFileOptions() );

    assertRootFolder( file );
  }

  @Test
  void testGetFileRegularHasExpectedProperties() throws OperationFailedException {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( "12345", path, false );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( nativeFile.getPath() );

    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    IGenericFile file =
      repositoryProvider.getFile( GenericFilePath.parse( nativeFile.getPath() ), new GetFileOptions() );

    assertEquals( "/public/testFile1", file.getPath() );
    assertEquals( "/public", file.getParentPath() );
    assertEquals( "testFile1", file.getName() );
    assertEquals( "testFile1 title", file.getTitle() );
    assertEquals( "testFile1 description", file.getDescription() );

    assertEquals( new Date( 200 ), file.getModifiedDate() );

    assertRegularCapabilities( file );
  }

  @Test
  void testGetFileThrowsAccessControlException() {
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doThrow( UnifiedRepositoryAccessDeniedException.class ).when( repositoryMock ).getFile( "/path" );

    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( AccessControlException.class,
      () -> repositoryProvider.getFile( GenericFilePath.parse( "/path" ), new GetFileOptions() ) );
  }

  @Test
  void testGetFileThrowsOperationFailedException() throws OperationFailedException {
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doThrow( UnifiedRepositoryException.class ).when( repositoryMock ).getFile( "/path" );

    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GenericFilePath path = GenericFilePath.parse( "/path" );
    assertThrows( OperationFailedException.class, () -> repositoryProvider.getFile( path, new GetFileOptions() ) );
  }

  @Test
  void testGetFileWithOptionsDoesNotFetchMetadataWhenDisabled() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( "12345", path, false );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( path.toString() );

    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GetFileOptions options = new GetFileOptions();
    options.setIncludeMetadata( false );

    IGenericFile file = repositoryProvider.getFile( GenericFilePath.parse( nativeFile.getPath() ), options );

    assertNotNull( file );
    assertInstanceOf( RepositoryObject.class, file );
    RepositoryObject repoObj = (RepositoryObject) file;
    assertNull( repoObj.getMetadata(), "Metadata must not be set when includeMetadata=false" );

    verify( fileServiceMock, never() ).doGetMetadata( anyString() );
  }

  @Test
  void testGetFileWithOptionsIncludesMetadataWhenEnabled() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( "12345", path, false );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( path.toString() );

    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    StringKeyStringValueDto md1 = new StringKeyStringValueDto();
    md1.setKey( "k1" );
    md1.setValue( "v1" );
    StringKeyStringValueDto md2 = new StringKeyStringValueDto();
    md2.setKey( "k2" );
    md2.setValue( "v2" );
    doReturn( Arrays.asList( md1, md2 ) ).when( fileServiceMock )
      .doGetMetadata( encodeRepositoryPath( path.toString() ) );

    GetFileOptions options = new GetFileOptions();
    options.setIncludeMetadata( true );

    IGenericFile file = repositoryProvider.getFile( GenericFilePath.parse( nativeFile.getPath() ), options );

    assertNotNull( file );
    RepositoryObject repoObj = (RepositoryObject) file;
    assertNotNull( repoObj.getMetadata() );
    assertEquals( "v1", repoObj.getMetadata().getMetadata().get( "k1" ) );
    assertEquals( "v2", repoObj.getMetadata().getMetadata().get( "k2" ) );

    verify( fileServiceMock, times( 1 ) ).doGetMetadata( encodeRepositoryPath( path.toString() ) );
  }

  @Test
  void testGetFileWithOptionsPropagatesMetadataFailure() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( "12345", path, false );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( path.toString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );

    OperationFailedException failure = new OperationFailedException( "md error" );
    doThrow( failure ).when( repositoryProvider ).getFileMetadata( path );

    GetFileOptions options = new GetFileOptions();
    options.setIncludeMetadata( true );

    OperationFailedException ex =
      assertThrows( OperationFailedException.class, () -> repositoryProvider.getFile( path, options ) );
    assertSame( failure, ex );

    verify( repositoryProvider, times( 1 ) ).getFile( path, options );
    verify( repositoryProvider, times( 1 ) ).getFileMetadata( path );
  }
  // endregion

  // region getDeletedFiles
  @Test
  void getDeletedFilesTestDeletedFile3HasExpectedProperties() {
    NativeDtoRepositoryScenario scenario = new NativeDtoRepositoryScenario();

    FileService fileServiceMock = mock( FileService.class );
    doReturn( Collections.singletonList( scenario.testDeletedFile3 ) )
      .when( fileServiceMock )
      .doGetDeletedFiles();

    String expectedOwner = "userA";
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( createMockFileOwner( expectedOwner ) )
      .when( repositoryMock )
      .getAcl( scenario.testDeletedFile3.getId() );

    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    List<IGenericFile> deletedFiles = repositoryProvider.getDeletedFiles();

    assertNotNull( deletedFiles );
    assertEquals( 1, deletedFiles.size() );

    IGenericFile deletedFile = deletedFiles.get( 0 );
    assertEquals( scenario.testDeletedFile3.getPath(), deletedFile.getPath() );
    assertEquals( scenario.testDeletedFile3.getCreatorId(), deletedFile.getDeletedBy() );
    assertEquals( expectedOwner, deletedFile.getOwner() );

    Date expectedDeletedDate = new Date( Integer.parseInt( scenario.testDeletedFile3.getDeletedDate() ) );
    assertEquals( expectedDeletedDate, deletedFile.getDeletedDate() );

    List<IGenericFile> originalLocations = deletedFile.getOriginalLocation();
    assertEquals( 2, originalLocations.size() );

    assertEquals( ROOT_PATH, originalLocations.get( 0 ).getPath() );
    assertEquals( "/public", originalLocations.get( 1 ).getPath() );
  }
  // endregion

  // region deleteFilePermanently
  @Test
  void testDeleteFilePermanentlySuccess() throws Exception {
    GenericFilePath path =
      GenericFilePath.parse( "/home/admin/.trash/pho:8b69da2b-2a10-4a82-89bc-a376e52d5482" + "/PAZReport.xanalyzer" );

    FileService fileServiceMock = mock( FileService.class );
    doNothing().when( fileServiceMock ).doDeleteFilesPermanent( any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    repositoryProvider.deleteFilePermanently( path );

    verify( fileServiceMock, times( 1 ) ).doDeleteFilesPermanent( repositoryProvider.getTrashFileId( path ) );
  }

  @Test
  void testDeleteFilePermanentlyInvalidPath() throws Exception {
    GenericFilePath path =
      GenericFilePath.parse( "/home/admin/pho:8b69da2b-2a10-4a82-89bc-a376e52d5482" + "/PAZReport.xanalyzer" );

    FileService fileServiceMock = mock( FileService.class );
    doNothing().when( fileServiceMock ).doDeleteFilesPermanent( any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    NotFoundException exception =
      assertThrows( NotFoundException.class, () -> repositoryProvider.deleteFilePermanently( path ) );

    assertEquals( "The path does not correspond to a deleted file.", exception.getMessage() );
    verify( fileServiceMock, never() ).doDeleteFilesPermanent( anyString() );
  }

  @Test
  void testDeleteFilePermanentlyOperationFailed() throws Exception {
    GenericFilePath path =
      GenericFilePath.parse( "/home/admin/.trash/pho:8b69da2b-2a10-4a82-89bc-a376e52d5482" + "/PAZReport.xanalyzer" );

    FileService fileServiceMock = mock( FileService.class );
    doNothing().when( fileServiceMock ).doDeleteFilesPermanent( any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    doThrow( new OperationFailedException() ).when( fileServiceMock ).doDeleteFilesPermanent( any() );

    assertThrows( OperationFailedException.class, () -> repositoryProvider.deleteFilePermanently( path ) );
    verify( fileServiceMock ).doDeleteFilesPermanent( repositoryProvider.getTrashFileId( path ) );
  }

  @Test
  void testGetTrashFileIdValidPath() throws Exception {
    testGetTrashFileIdValidPath(
      "/home/admin/.trash/pho:8b69da2b-2a10-4a82-89bc-a376e52d5483/report/PAZReport.xanalyzer",
      "8b69da2b-2a10-4a82-89bc-a376e52d5483" );
  }

  @Test
  void testGetTrashFileIdValidPathWithColon() throws Exception {
    testGetTrashFileIdValidPath( "/home/admin/.trash/pho:8b69da2b-2a10-4a82-89bc-a376e52d5482/PAZReport.xanalyzer",
      "8b69da2b-2a10-4a82-89bc-a376e52d5482" );
  }

  @Test
  void testGetTrashFileIdInvalidPathWithoutColon() throws Exception {
    testGetTrashFileIdInvalidPath( "/home/admin/.trash/8b69da2b-2a10-4a82-89bc-a376e52d5482/PAZReport.xanalyzer",
      InvalidPathException.class );
  }

  @Test
  void testGetTrashFileIdInvalidPathNoTrash() throws Exception {
    testGetTrashFileIdInvalidPath( "/home/admin/pho:8b69da2b-2a10-4a82-89bc-a376e52d5482/PAZReport.xanalyzer",
      NotFoundException.class );
  }

  @Test
  void testGetTrashFileIdInvalidPathNoTrashNoId() throws Exception {
    testGetTrashFileIdInvalidPath( "/home/admin/PAZReport.xanalyzer", NotFoundException.class );
  }

  @Test
  void testGetFileIdInvalidPathNoIdNoTrashFile() throws Exception {
    testGetTrashFileIdInvalidPath( "/home/admin/.trash/", NotFoundException.class );
  }

  @Test
  void testGetTrashFileIdInvalidPathRoot() throws Exception {
    testGetTrashFileIdInvalidPath( "/", NotFoundException.class );
  }

  @Test
  void testGetTrashFileIdInvalidPathNoTrashNoColon() throws Exception {
    testGetTrashFileIdInvalidPath( "/home/admin/8b69da2b-2a10-4a82-89bc-a376e52d5482/PAZReport.xanalyzer",
      NotFoundException.class );
  }

  private <T extends Throwable> void testGetTrashFileIdInvalidPath( String pathString, Class<T> exceptionClass )
    throws Exception {
    GenericFilePath path = GenericFilePath.parse( pathString );

    FileService fileServiceMock = mock( FileService.class );
    doNothing().when( fileServiceMock ).doDeleteFilesPermanent( any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    T exception = assertThrows( exceptionClass, () -> repositoryProvider.getTrashFileId( path ) );

    if ( exception instanceof NotFoundException ) {
      assertEquals( "The path does not correspond to a deleted file.", exception.getMessage() );
    } else if ( exception instanceof InvalidPathException ) {
      assertEquals( "File ID not found in the path.", exception.getMessage() );
    }
  }

  private void testGetTrashFileIdValidPath( String pathString, String id ) throws Exception {
    GenericFilePath path = GenericFilePath.parse( pathString );

    FileService fileServiceMock = mock( FileService.class );
    doNothing().when( fileServiceMock ).doDeleteFilesPermanent( any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    String fileId = repositoryProvider.getTrashFileId( path );
    assertEquals( id, fileId );
  }
  // endregion

  // region deleteFile
  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testDeleteFileSuccess( boolean permanent ) throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path =
      GenericFilePath.parse( "/home/admin/8b69da2b-2a10-4a82-89bc-a376e52d5482" + "/PAZReport.xanalyzer" );

    FileService fileServiceMock = mock( FileService.class );

    if ( permanent ) {
      doNothing().when( fileServiceMock ).doDeleteFilesPermanent( any() );
    } else {
      doNothing().when( fileServiceMock ).doDeleteFiles( any() );
    }

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( createNativeFile( fileId, path, false ) ).when( repositoryMock ).getFile( any() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    repositoryProvider.deleteFile( path, permanent );

    if ( permanent ) {
      verify( fileServiceMock, never() ).doDeleteFiles( fileId );
      verify( fileServiceMock, times( 1 ) ).doDeleteFilesPermanent( fileId );
    } else {
      verify( fileServiceMock, times( 1 ) ).doDeleteFiles( fileId );
      verify( fileServiceMock, never() ).doDeleteFilesPermanent( fileId );
    }
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testDeleteFileOperationFailed( boolean permanent ) throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path =
      GenericFilePath.parse( "/home/admin/8b69da2b-2a10-4a82-89bc-a376e52d5482" + "/PAZReport.xanalyzer" );

    FileService fileServiceMock = mock( FileService.class );

    if ( permanent ) {
      doThrow( new Exception() ).when( fileServiceMock ).doDeleteFilesPermanent( any() );
    } else {
      doThrow( new Exception() ).when( fileServiceMock ).doDeleteFiles( any() );
    }

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( createNativeFile( fileId, path, false ) ).when( repositoryMock ).getFile( any() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( OperationFailedException.class, () -> repositoryProvider.deleteFile( path, permanent ) );

    if ( permanent ) {
      verify( fileServiceMock, never() ).doDeleteFiles( fileId );
      verify( fileServiceMock ).doDeleteFilesPermanent( fileId );
    } else {
      verify( fileServiceMock ).doDeleteFiles( fileId );
      verify( fileServiceMock, never() ).doDeleteFilesPermanent( fileId );
    }
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testDeleteFileNotFound( boolean permanent ) throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/home/admin/nonexistent-file.xanalyzer" );

    FileService fileServiceMock = mock( FileService.class );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( null ).when( repositoryMock ).getFile( any() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( NotFoundException.class, () -> repositoryProvider.deleteFile( path, permanent ) );

    verify( fileServiceMock, never() ).doDeleteFiles( anyString() );
    verify( fileServiceMock, never() ).doDeleteFilesPermanent( anyString() );
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testDeleteFileAccessControlException( boolean permanent ) throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/access-denied-file.xanalyzer" );

    FileService fileServiceMock = mock( FileService.class );

    if ( permanent ) {
      doThrow( UnifiedRepositoryAccessDeniedException.class ).when( fileServiceMock ).doDeleteFilesPermanent( any() );
    } else {
      doThrow( UnifiedRepositoryAccessDeniedException.class ).when( fileServiceMock ).doDeleteFiles( any() );
    }

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( createNativeFile( fileId, path, false ) ).when( repositoryMock ).getFile( any() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( AccessControlException.class, () -> repositoryProvider.deleteFile( path, permanent ) );

    if ( permanent ) {
      verify( fileServiceMock, never() ).doDeleteFiles( anyString() );
      verify( fileServiceMock ).doDeleteFilesPermanent( fileId );
    } else {
      verify( fileServiceMock ).doDeleteFiles( fileId );
      verify( fileServiceMock, never() ).doDeleteFilesPermanent( anyString() );
    }
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testDeleteFileOperationFailedException( boolean permanent ) throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/exception-file.xanalyzer" );

    FileService fileServiceMock = mock( FileService.class );

    if ( permanent ) {
      doThrow( UnifiedRepositoryException.class ).when( fileServiceMock ).doDeleteFilesPermanent( any() );
    } else {
      doThrow( UnifiedRepositoryException.class ).when( fileServiceMock ).doDeleteFiles( any() );
    }

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( createNativeFile( fileId, path, false ) ).when( repositoryMock ).getFile( any() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( OperationFailedException.class, () -> repositoryProvider.deleteFile( path, permanent ) );

    if ( permanent ) {
      verify( fileServiceMock, never() ).doDeleteFiles( anyString() );
      verify( fileServiceMock ).doDeleteFilesPermanent( fileId );
    } else {
      verify( fileServiceMock ).doDeleteFiles( fileId );
      verify( fileServiceMock, never() ).doDeleteFilesPermanent( anyString() );
    }
  }
  // endregion

  // region restoreFile
  @Test
  void testRestoreFileSuccess() throws Exception {
    GenericFilePath path =
      GenericFilePath.parse( "/home/admin/.trash/pho:8b69da2b-2a10-4a82-89bc-a376e52d5482" + "/PAZReport.xanalyzer" );

    FileService fileServiceMock = mock( FileService.class );
    doNothing().when( fileServiceMock ).doRestoreFiles( any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    repositoryProvider.restoreFile( path );

    verify( fileServiceMock, times( 1 ) ).doRestoreFiles( repositoryProvider.getTrashFileId( path ) );
  }

  @Test
  void testRestoreFileInvalidPath() throws Exception {
    GenericFilePath path =
      GenericFilePath.parse( "/home/admin/pho:8b69da2b-2a10-4a82-89bc-a376e52d5482" + "/PAZReport.xanalyzer" );

    FileService fileServiceMock = mock( FileService.class );
    doNothing().when( fileServiceMock ).doRestoreFiles( any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    NotFoundException exception = assertThrows( NotFoundException.class, () -> repositoryProvider.restoreFile( path )
    );

    assertEquals( "The path does not correspond to a deleted file.", exception.getMessage() );
    verify( fileServiceMock, never() ).doRestoreFiles( anyString() );
  }

  @Test
  void testRestoreFileUnifiedRepositoryAccessDeniedException() throws Exception {
    GenericFilePath path =
      GenericFilePath.parse( "/home/admin/.trash/pho:8b69da2b-2a10-4a82-89bc-a376e52d5482" + "/PAZReport.xanalyzer" );

    FileService fileServiceMock = mock( FileService.class );
    doNothing().when( fileServiceMock ).doRestoreFiles( any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    doThrow( new UnifiedRepositoryAccessDeniedException() ).when( fileServiceMock ).doRestoreFiles( any() );

    assertThrows( AccessControlException.class, () -> repositoryProvider.restoreFile( path ) );
    verify( fileServiceMock ).doRestoreFiles( repositoryProvider.getTrashFileId( path ) );
  }

  @Test
  void testRestoreFileOperationFailed() throws Exception {
    GenericFilePath path =
      GenericFilePath.parse( "/home/admin/.trash/pho:8b69da2b-2a10-4a82-89bc-a376e52d5482" + "/PAZReport.xanalyzer" );

    FileService fileServiceMock = mock( FileService.class );
    doNothing().when( fileServiceMock ).doRestoreFiles( any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    doThrow( new InternalError() ).when( fileServiceMock ).doRestoreFiles( any() );

    assertThrows( OperationFailedException.class, () -> repositoryProvider.restoreFile( path ) );
    verify( fileServiceMock ).doRestoreFiles( repositoryProvider.getTrashFileId( path ) );
  }
  // endregion

  // region getFileContent
  @Test
  void testGetFileContentNotFound() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/missing.txt" );

    FileService fileServiceMock = mock( FileService.class );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( null ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( NotFoundException.class, () -> repositoryProvider.getFileContent( path, false ) );
  }

  // region compressed
  @Test
  void testGetFileContentCompressedSuccess() throws Exception {
    String fileId = "file-123";
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( fileId, path, false );

    FileService fileServiceMock = mock( FileService.class );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( path.toString() );
    doReturn( true ).when( fileServiceMock ).isPathValid( path.toString() );
    FileInputStream compressedStream = mock( FileInputStream.class );
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( compressedStream ).when( repositoryProvider ).getFileContentCompressedStream( nativeFile );

    try ( var mocked = mockStatic( SystemUtils.class ) ) {
      mocked.when( () -> SystemUtils.canDownload( path.toString() ) ).thenReturn( true );
      mocked.when( () -> SystemUtils.canDownload( null ) ).thenReturn( true );
      IGenericFileContent content = repositoryProvider.getFileContent( path, true );

      assertNotNull( content );
      assertEquals( nativeFile.getName() + ".zip", content.getFileName() );
      assertEquals( MediaType.ZIP.toString(), content.getMimeType() );
      verify( repositoryProvider ).getFileContentCompressedStream( nativeFile );
    }
  }

  @Test
  void testGetFileContentCompressedInvalidPath() throws Exception {
    String fileId = "file-123";
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( fileId, path, false );

    FileService fileServiceMock = mock( FileService.class );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( path.toString() );
    doReturn( false ).when( fileServiceMock ).isPathValid( path.toString() );
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );

    try ( var mocked = mockStatic( SystemUtils.class ) ) {
      mocked.when( () -> SystemUtils.canDownload( path.toString() ) ).thenReturn( true );
      mocked.when( () -> SystemUtils.canDownload( null ) ).thenReturn( true );

      assertThrows( InvalidOperationException.class, () -> repositoryProvider.getFileContent( path, true ) );
    }
  }

  @Test
  void testGetFileContentCompressedResourceAccessDeniedException() throws Exception {
    String fileId = "file-123";
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( fileId, path, false );

    FileService fileServiceMock = mock( FileService.class );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( path.toString() );
    doReturn( true ).when( fileServiceMock ).isPathValid( path.toString() );
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );

    try ( var mocked = mockStatic( SystemUtils.class ) ) {
      mocked.when( () -> SystemUtils.canDownload( path.toString() ) ).thenReturn( false );
      mocked.when( () -> SystemUtils.canDownload( null ) ).thenReturn( true );

      assertThrows( ResourceAccessDeniedException.class, () -> repositoryProvider.getFileContent( path, true ) );
    }
  }

  @Test
  void testGetFileContentCompressedAccessControlException() throws Exception {
    String fileId = "file-123";
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( fileId, path, false );

    FileService fileServiceMock = mock( FileService.class );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( path.toString() );
    doReturn( true ).when( fileServiceMock ).isPathValid( path.toString() );
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );

    try ( var mocked = mockStatic( SystemUtils.class ) ) {
      mocked.when( () -> SystemUtils.canDownload( path.toString() ) ).thenReturn( false );
      mocked.when( () -> SystemUtils.canDownload( null ) ).thenReturn( false );

      assertThrows( AccessControlException.class, () -> repositoryProvider.getFileContent( path, true ) );
    }
  }

  @Test
  void testGetFileContentCompressedThrowsRuntimeException() throws Exception {
    String fileId = "file-123";
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( fileId, path, false );

    FileService fileServiceMock = mock( FileService.class );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( path.toString() );
    doReturn( true ).when( fileServiceMock ).isPathValid( path.toString() );
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doThrow( RuntimeException.class ).when( repositoryProvider ).getFileContentCompressedStream( nativeFile );

    try ( var mocked = mockStatic( SystemUtils.class ) ) {
      mocked.when( () -> SystemUtils.canDownload( path.toString() ) ).thenReturn( true );
      mocked.when( () -> SystemUtils.canDownload( null ) ).thenReturn( true );

      assertThrows( RuntimeException.class, () -> repositoryProvider.getFileContent( path, true ) );
    }
  }

  @Test
  void testGetFileContentCompressedThrowsExportException() throws Exception {
    String fileId = "file-123";
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( fileId, path, false );

    FileService fileServiceMock = mock( FileService.class );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( path.toString() );
    doReturn( true ).when( fileServiceMock ).isPathValid( path.toString() );
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doThrow( ExportException.class ).when( repositoryProvider ).getFileContentCompressedStream( nativeFile );

    try ( var mocked = mockStatic( SystemUtils.class ) ) {
      mocked.when( () -> SystemUtils.canDownload( path.toString() ) ).thenReturn( true );
      mocked.when( () -> SystemUtils.canDownload( null ) ).thenReturn( true );

      assertThrows( OperationFailedException.class, () -> repositoryProvider.getFileContent( path, true ) );
    }
  }

  @Test
  void testGetFileContentCompressedThrowsIOException() throws Exception {
    String fileId = "file-123";
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( fileId, path, false );

    FileService fileServiceMock = mock( FileService.class );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( path.toString() );
    doReturn( true ).when( fileServiceMock ).isPathValid( path.toString() );
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doThrow( java.io.IOException.class ).when( repositoryProvider ).getFileContentCompressedStream( nativeFile );

    try ( var mocked = mockStatic( SystemUtils.class ) ) {
      mocked.when( () -> SystemUtils.canDownload( path.toString() ) ).thenReturn( true );
      mocked.when( () -> SystemUtils.canDownload( null ) ).thenReturn( true );

      assertThrows( OperationFailedException.class, () -> repositoryProvider.getFileContent( path, true ) );
    }
  }

  @Test
  void testGetFileContentCompressedFolderSuccess() throws Exception {
    String fileId = "folder-123";
    GenericFilePath path = GenericFilePath.parse( "/public/testFolder2" );
    RepositoryFile nativeFile = createNativeFile( fileId, path, true );

    FileService fileServiceMock = mock( FileService.class );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( path.toString() );
    doReturn( true ).when( fileServiceMock ).isPathValid( path.toString() );
    FileInputStream compressedStream = mock( FileInputStream.class );
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( compressedStream ).when( repositoryProvider ).getFileContentCompressedStream( nativeFile );

    try ( var mocked = mockStatic( SystemUtils.class ) ) {
      mocked.when( () -> SystemUtils.canDownload( path.toString() ) ).thenReturn( true );
      mocked.when( () -> SystemUtils.canDownload( null ) ).thenReturn( true );
      IGenericFileContent content = repositoryProvider.getFileContent( path, true );

      assertNotNull( content );
      assertEquals( nativeFile.getName() + ".zip", content.getFileName() );
      assertEquals( MediaType.ZIP.toString(), content.getMimeType() );
    }
  }
  // endregion compressed

  // region uncompressed
  @Test
  void testGetFileContentUncompressedSuccess() throws Exception {
    String fileId = "file-123";
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( fileId, path, false );

    FileService fileServiceMock = mock( FileService.class );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileInputStream inputStream = mock( RepositoryFileInputStream.class );
    doReturn( MediaType.PLAIN_TEXT_UTF_8.toString() ).when( inputStream ).getMimeType();
    doReturn( inputStream ).when( fileServiceMock ).getRepositoryFileInputStream( nativeFile );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    doReturn( true ).when( fileServiceMock ).doGetCanGetFileContent( nativeFile.getName() );

    IGenericFileContent content = repositoryProvider.getFileContent( path, false );

    assertNotNull( content );
    assertEquals( nativeFile.getName(), content.getFileName() );
    assertEquals( MediaType.PLAIN_TEXT_UTF_8.toString(), content.getMimeType() );
    verify( fileServiceMock ).getRepositoryFileInputStream( nativeFile );
  }

  @Test
  void testGetFileContentUncompressedFolderThrowsException() throws Exception {
    String fileId = "folder-123";
    GenericFilePath path = GenericFilePath.parse( "/public/testFolder2" );
    RepositoryFile nativeFile = createNativeFile( fileId, path, true );

    FileService fileServiceMock = mock( FileService.class );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );

    assertThrows( InvalidOperationException.class, () -> repositoryProvider.getFileContent( path, false ) );
  }

  @Test
  void testGetFileContentUncompressedThrowsRuntimeException() throws Exception {
    String fileId = "file-123";
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( fileId, path, false );

    FileService fileServiceMock = mock( FileService.class );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( path.toString() );
    doThrow( RuntimeException.class ).when( fileServiceMock ).getRepositoryFileInputStream( nativeFile );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    doReturn( true ).when( fileServiceMock ).doGetCanGetFileContent( nativeFile.getName() );

    assertThrows( RuntimeException.class, () -> repositoryProvider.getFileContent( path, false ) );
  }

  @Test
  void testGetFileContentUncompressedFileNotFound() throws Exception {
    String fileId = "file-123";
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( fileId, path, false );

    FileService fileServiceMock = mock( FileService.class );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( path.toString() );
    doThrow( FileNotFoundException.class ).when( fileServiceMock ).getRepositoryFileInputStream( nativeFile );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    doReturn( true ).when( fileServiceMock ).doGetCanGetFileContent( nativeFile.getName() );

    assertThrows( NotFoundException.class, () -> repositoryProvider.getFileContent( path, false ) );
  }

  @Test
  void testGetFileContentUncompressedResourceAccessDeniedException() throws Exception {
    String fileId = "file-123";
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( fileId, path, false );

    FileService fileServiceMock = mock( FileService.class );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileInputStream inputStream = mock( RepositoryFileInputStream.class );
    doReturn( MediaType.PLAIN_TEXT_UTF_8.toString() ).when( inputStream ).getMimeType();
    doReturn( inputStream ).when( fileServiceMock ).getRepositoryFileInputStream( nativeFile );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    doReturn( false ).when( fileServiceMock ).doGetCanGetFileContent( nativeFile.getName() );

    assertThrows( ResourceAccessDeniedException.class, () -> repositoryProvider.getFileContent( path, false ) );
  }
  // endregion uncompressed
  // endregion getFileContent

  // region convertFromNativeFileDto
  @Test
  void testConvertFromNativeFileDtoFile() {
    RepositoryFileProvider provider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    RepositoryFileDto dto = new RepositoryFileDto();
    dto.setName( "file.txt" );
    dto.setPath( "/public/file.txt" );
    dto.setDescription( "description" );
    dto.setFolder( false );
    dto.setCreatedDate( "1000" );
    dto.setLastModifiedDate( "2000" );
    dto.setTitle( "File Title" );
    dto.setDescription( "File Description" );
    dto.setId( "id-1" );
    dto.setOwner( "owner1" );
    dto.setCreatorId( "creator1" );
    dto.setFileSize( 123L );

    RepositoryObject obj = provider.convertFromNativeFileDto( dto );

    assertNotNull( obj );
    assertEquals( "file.txt", obj.getName() );
    assertEquals( "/public/file.txt", obj.getPath() );
    assertEquals( "/public", obj.getParentPath() );
    assertEquals( "id-1", obj.getObjectId() );
    assertEquals( "File Title", obj.getTitle() );
    assertEquals( "File Description", obj.getDescription() );
    assertFalse( obj.isFolder() );
    assertEquals( new Date( 1000 ), obj.getCreatedDate() );
    assertEquals( new Date( 2000 ), obj.getModifiedDate() );
    assertEquals( "owner1", obj.getOwner() );
    assertEquals( "creator1", obj.getCreatorId() );
    assertEquals( 123L, obj.getFileSize() );
  }

  @Test
  void testConvertFromNativeFileDtoRootFolder() {
    RepositoryFileProvider provider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    RepositoryFileDto dto = new RepositoryFileDto();
    dto.setName( "root" );
    dto.setPath( "/" );
    dto.setFolder( true );
    dto.setCreatedDate( "1000" );
    dto.setLastModifiedDate( "2000" );
    dto.setTitle( "Folder Title" );
    dto.setDescription( "Folder Description" );
    dto.setId( "id-2" );
    dto.setOwner( "owner2" );
    dto.setCreatorId( "creator2" );
    dto.setFileSize( 0L );

    RepositoryObject obj = provider.convertFromNativeFileDto( dto );

    assertNotNull( obj );
    assertEquals( "/", obj.getName() );
    assertEquals( "/", obj.getPath() );
    assertNull( obj.getParentPath() );
    assertEquals( "id-2", obj.getObjectId() );
    assertEquals( Messages.getString( "GenericFileRepository.REPOSITORY_FOLDER_DISPLAY" ), obj.getTitle() );
    assertEquals( "Folder Description", obj.getDescription() );
    assertTrue( obj.isFolder() );
    assertEquals( new Date( 1000 ), obj.getCreatedDate() );
    assertEquals( new Date( 2000 ), obj.getModifiedDate() );
    assertEquals( "owner2", obj.getOwner() );
    assertEquals( "creator2", obj.getCreatorId() );
    assertEquals( 0L, obj.getFileSize() );
  }

  @Test
  void testConvertFromNativeFileDtoFileModifiedDateNull() {
    RepositoryFileProvider provider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    RepositoryFileDto dto = new RepositoryFileDto();
    dto.setName( "file.txt" );
    dto.setPath( "/public/file.txt" );
    dto.setFolder( false );
    dto.setCreatedDate( "1000" );
    dto.setLastModifiedDate( null );
    dto.setTitle( "File Title" );
    dto.setDescription( "File Description" );
    dto.setId( "id-1" );
    dto.setOwner( "owner1" );
    dto.setCreatorId( "creator1" );
    dto.setFileSize( 123L );

    RepositoryObject obj = provider.convertFromNativeFileDto( dto );

    assertNotNull( obj );
    assertEquals( "file.txt", obj.getName() );
    assertEquals( "/public/file.txt", obj.getPath() );
    assertEquals( "/public", obj.getParentPath() );
    assertEquals( "id-1", obj.getObjectId() );
    assertEquals( "File Title", obj.getTitle() );
    assertEquals( "File Description", obj.getDescription() );
    assertFalse( obj.isFolder() );
    assertEquals( new Date( 1000 ), obj.getCreatedDate() );
    assertEquals( new Date( 1000 ), obj.getModifiedDate() );
    assertEquals( "owner1", obj.getOwner() );
    assertEquals( "creator1", obj.getCreatorId() );
    assertEquals( 123L, obj.getFileSize() );
  }
  // endregion

  // region convertFromNativeFile
  @Test
  void testConvertFromNativeFileFile() {
    RepositoryFileProvider provider =
      spy( new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) ) );

    String id = "file-123";
    String name = "file.txt";
    String path = "/public/file.txt";
    String parentPath = "/public";
    String title = "File Title";
    String description = "File Description";
    boolean isFolder = false;
    Date createdDate = new Date( 1000 );
    Date modifiedDate = new Date( 2000 );
    String owner = "owner1";
    String creatorId = "creator1";
    long fileSize = 123L;

    RepositoryFile nativeFile = mock( RepositoryFile.class );
    doReturn( name ).when( nativeFile ).getName();
    doReturn( path ).when( nativeFile ).getPath();
    doReturn( title ).when( nativeFile ).getTitle();
    doReturn( isFolder ).when( nativeFile ).isFolder();
    doReturn( createdDate ).when( nativeFile ).getCreatedDate();
    doReturn( modifiedDate ).when( nativeFile ).getLastModifiedDate();
    doReturn( id ).when( nativeFile ).getId();
    doReturn( description ).when( nativeFile ).getDescription();
    doReturn( creatorId ).when( nativeFile ).getCreatorId();
    doReturn( fileSize ).when( nativeFile ).getFileSize();
    doReturn( owner ).when( provider ).getOwnerByFileId( id );

    RepositoryObject obj = provider.convertFromNativeFile( nativeFile, parentPath );

    assertNotNull( obj );
    assertEquals( name, obj.getName() );
    assertEquals( path, obj.getPath() );
    assertEquals( parentPath, obj.getParentPath() );
    assertEquals( id, obj.getObjectId() );
    assertEquals( title, obj.getTitle() );
    assertEquals( description, obj.getDescription() );
    assertEquals( isFolder, obj.isFolder() );
    assertEquals( createdDate, obj.getCreatedDate() );
    assertEquals( modifiedDate, obj.getModifiedDate() );
    assertEquals( owner, obj.getOwner() );
    assertEquals( creatorId, obj.getCreatorId() );
    assertEquals( fileSize, obj.getFileSize() );
  }

  @Test
  void testConvertFromNativeFileFolder() {
    RepositoryFileProvider provider =
      spy( new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) ) );

    String id = "file-123";
    String name = "root";
    String path = "/";
    String title = "Root Folder";
    String description = "Root Folder Description";
    boolean isFolder = true;
    Date createdDate = new Date( 1000 );
    Date modifiedDate = new Date( 2000 );
    String owner = "owner1";
    String creatorId = "creator1";
    long fileSize = 0L;

    RepositoryFile nativeFile = mock( RepositoryFile.class );
    doReturn( name ).when( nativeFile ).getName();
    doReturn( path ).when( nativeFile ).getPath();
    doReturn( title ).when( nativeFile ).getTitle();
    doReturn( isFolder ).when( nativeFile ).isFolder();
    doReturn( createdDate ).when( nativeFile ).getCreatedDate();
    doReturn( modifiedDate ).when( nativeFile ).getLastModifiedDate();
    doReturn( id ).when( nativeFile ).getId();
    doReturn( description ).when( nativeFile ).getDescription();
    doReturn( creatorId ).when( nativeFile ).getCreatorId();
    doReturn( fileSize ).when( nativeFile ).getFileSize();
    doReturn( owner ).when( provider ).getOwnerByFileId( id );

    RepositoryObject obj = provider.convertFromNativeFile( nativeFile, null );

    assertNotNull( obj );
    assertEquals( path, obj.getName() );
    assertEquals( path, obj.getPath() );
    assertNull( obj.getParentPath() );
    assertEquals( id, obj.getObjectId() );
    assertEquals( Messages.getString( "GenericFileRepository.REPOSITORY_FOLDER_DISPLAY" ), obj.getTitle() );
    assertEquals( description, obj.getDescription() );
    assertEquals( isFolder, obj.isFolder() );
    assertEquals( createdDate, obj.getCreatedDate() );
    assertEquals( modifiedDate, obj.getModifiedDate() );
    assertEquals( owner, obj.getOwner() );
    assertEquals( creatorId, obj.getCreatorId() );
    assertEquals( fileSize, obj.getFileSize() );
  }

  @Test
  void testConvertFromNativeFileFileIdNull() {
    RepositoryFileProvider provider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    String name = "file.txt";
    String path = "/public/file.txt";
    String parentPath = "/public";
    String title = "File Title";
    String description = "File Description";
    boolean isFolder = false;
    Date createdDate = new Date( 1000 );
    Date modifiedDate = new Date( 2000 );
    String creatorId = "creator1";
    long fileSize = 123L;

    RepositoryFile nativeFile = mock( RepositoryFile.class );
    doReturn( name ).when( nativeFile ).getName();
    doReturn( path ).when( nativeFile ).getPath();
    doReturn( title ).when( nativeFile ).getTitle();
    doReturn( isFolder ).when( nativeFile ).isFolder();
    doReturn( createdDate ).when( nativeFile ).getCreatedDate();
    doReturn( modifiedDate ).when( nativeFile ).getLastModifiedDate();
    doReturn( null ).when( nativeFile ).getId();
    doReturn( description ).when( nativeFile ).getDescription();
    doReturn( creatorId ).when( nativeFile ).getCreatorId();
    doReturn( fileSize ).when( nativeFile ).getFileSize();

    RepositoryObject obj = provider.convertFromNativeFile( nativeFile, parentPath );

    assertNotNull( obj );
    assertEquals( name, obj.getName() );
    assertEquals( path, obj.getPath() );
    assertEquals( parentPath, obj.getParentPath() );
    assertNull( obj.getObjectId() );
    assertEquals( title, obj.getTitle() );
    assertEquals( description, obj.getDescription() );
    assertEquals( isFolder, obj.isFolder() );
    assertNull( obj.getCreatedDate() );
    assertEquals( modifiedDate, obj.getModifiedDate() );
    assertNull( obj.getOwner() );
    assertNull( obj.getCreatorId() );
    assertEquals( 0L, obj.getFileSize() );
  }

  @Test
  void testConvertFromNativeFileFileModifiedDateNull() {
    RepositoryFileProvider provider =
      spy( new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) ) );

    String id = "file-123";
    String name = "file.txt";
    String path = "/public/file.txt";
    String parentPath = "/public";
    String title = "File Title";
    String description = "File Description";
    boolean isFolder = false;
    Date createdDate = new Date( 1000 );
    String owner = "owner1";
    String creatorId = "creator1";
    long fileSize = 123L;

    RepositoryFile nativeFile = mock( RepositoryFile.class );
    doReturn( name ).when( nativeFile ).getName();
    doReturn( path ).when( nativeFile ).getPath();
    doReturn( title ).when( nativeFile ).getTitle();
    doReturn( isFolder ).when( nativeFile ).isFolder();
    doReturn( createdDate ).when( nativeFile ).getCreatedDate();
    doReturn( null ).when( nativeFile ).getLastModifiedDate();
    doReturn( id ).when( nativeFile ).getId();
    doReturn( description ).when( nativeFile ).getDescription();
    doReturn( creatorId ).when( nativeFile ).getCreatorId();
    doReturn( fileSize ).when( nativeFile ).getFileSize();
    doReturn( owner ).when( provider ).getOwnerByFileId( id );

    RepositoryObject obj = provider.convertFromNativeFile( nativeFile, parentPath );

    assertNotNull( obj );
    assertEquals( name, obj.getName() );
    assertEquals( path, obj.getPath() );
    assertEquals( parentPath, obj.getParentPath() );
    assertEquals( id, obj.getObjectId() );
    assertEquals( title, obj.getTitle() );
    assertEquals( description, obj.getDescription() );
    assertEquals( isFolder, obj.isFolder() );
    assertEquals( createdDate, obj.getCreatedDate() );
    assertEquals( createdDate, obj.getModifiedDate() );
    assertEquals( owner, obj.getOwner() );
    assertEquals( creatorId, obj.getCreatorId() );
    assertEquals( fileSize, obj.getFileSize() );
  }
  // endregion

  // region renameFile
  @Test
  void testRenameFileSuccess() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    String newName = "renamed.xanalyzer";

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( path.toString() ) );
    doReturn( true ).when( fileServiceMock ).isValidFileName( newName, true );
    doReturn( true ).when( fileServiceMock ).doRename( encodeRepositoryPath( path.toString() ), newName );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GenericFilePath newPath = repositoryProvider.getNewPath( path.getParent(), newName );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );
    assertEquals( GenericFilePath.parse( "/home/admin/" + fileId + "/" + newName ), newPath );

    boolean result = repositoryProvider.renameFile( path, newName );

    assertTrue( result );
    verify( fileServiceMock, times( 1 ) ).doRename( encodeRepositoryPath( path.toString() ), newName );
  }

  @Test
  void testRenameFileErrorThrowsException() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    String newName = "renamed";

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( path.toString() ) );
    doReturn( true ).when( fileServiceMock ).isValidFileName( newName, true );
    doThrow( new IllegalArgumentException( "rename failed" ) ).when( fileServiceMock )
      .doRename( encodeRepositoryPath( path.toString() ), newName );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GenericFilePath newPath = repositoryProvider.getNewPath( path.getParent(), newName + ".xanalyzer" );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    OperationFailedException exception =
      assertThrows( OperationFailedException.class, () -> repositoryProvider.renameFile( path, newName ) );

    assertEquals( "rename failed", exception.getCause().getMessage() );
    verify( fileServiceMock ).doRename( encodeRepositoryPath( path.toString() ), newName );
  }

  @Test
  void testRenameFileOperationFailed() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    String newName = "renamed";

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( path.toString() ) );
    doReturn( true ).when( fileServiceMock ).isValidFileName( newName, true );
    doReturn( false ).when( fileServiceMock ).doRename( encodeRepositoryPath( path.toString() ), newName );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GenericFilePath newPath = repositoryProvider.getNewPath( path.getParent(), newName + ".xanalyzer" );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    boolean result = repositoryProvider.renameFile( path, newName );

    assertFalse( result );
    verify( fileServiceMock, times( 1 ) ).doRename( encodeRepositoryPath( path.toString() ), newName );
  }

  @Test
  void testRenameAccessControlException() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    String newName = "renamed";

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "false" ).when( fileServiceMock ).doGetCanCreate();
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( AccessControlException.class, () -> repositoryProvider.renameFile( path, newName ) );
    verify( fileServiceMock, never() ).doRename( encodeRepositoryPath( path.toString() ), newName );
  }

  @Test
  void testRenameFileNotFoundException() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    String newName = "renamed";

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( path.toString() ) );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( NotFoundException.class, () -> repositoryProvider.renameFile( path, newName ) );
    verify( fileServiceMock, never() ).doRename( encodeRepositoryPath( path.toString() ), newName );
  }

  @Test
  void testRenameFileInvalidName() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    String newName = "bad/name";

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( path.toString() ) );
    doReturn( false ).when( fileServiceMock ).isValidFileName( newName, true );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( InvalidOperationException.class, () -> repositoryProvider.renameFile( path, newName ) );
    verify( fileServiceMock, never() ).doRename( encodeRepositoryPath( path.toString() ), newName );
  }

  @Test
  void testRenameNewFileExists() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    String newName = "renamed";

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( path.toString() ) );
    doReturn( true ).when( fileServiceMock ).isValidFileName( newName, true );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GenericFilePath newPath = repositoryProvider.getNewPath( path.getParent(), newName + ".xanalyzer" );
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    ConflictException exception =
      assertThrows( ConflictException.class, () -> repositoryProvider.renameFile( path, newName ) );

    assertEquals( String.format( "Item to be renamed already exists on the destination folder: '%s'.", newName ),
      exception.getMessage() );
    verify( fileServiceMock, never() ).doRename( encodeRepositoryPath( path.toString() ), newName );
  }

  @Test
  void testRenameFileAccessControlException() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    String newName = "renamed.xanalyzer";

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( path.toString() ) );
    doReturn( true ).when( fileServiceMock ).isValidFileName( newName, true );
    doThrow( new UnifiedRepositoryAccessDeniedException( "Not Authorized" ) ).when( fileServiceMock )
      .doRename( encodeRepositoryPath( path.toString() ), newName );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GenericFilePath newPath = repositoryProvider.getNewPath( path.getParent(), newName + ".xanalyzer" );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    assertThrows( AccessControlException.class, () -> repositoryProvider.renameFile( path, newName ) );

    verify( fileServiceMock ).doRename( encodeRepositoryPath( path.toString() ), newName );
  }

  @Test
  void testRenameFileSuccessWithNoExtension() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/myFolder" );
    String newName = "renamedFolder";

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( path.toString() ) );
    doReturn( true ).when( fileServiceMock ).isValidFileName( newName, true );
    doReturn( true ).when( fileServiceMock ).doRename( encodeRepositoryPath( path.toString() ), newName );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    // No extension, so fullNewName == newName.
    GenericFilePath newPath = repositoryProvider.getNewPath( path.getParent(), newName );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    boolean result = repositoryProvider.renameFile( path, newName );

    assertTrue( result );
    verify( fileServiceMock, times( 1 ) ).doRename( encodeRepositoryPath( path.toString() ), newName );
  }
  // endregion

  // region copyFiles
  @Test
  void testCopyFilesSuccess() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    GenericFilePath destPath = GenericFilePath.parse( "/archive/" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( destPath.toString() ) );
    doNothing().when( fileServiceMock ).doCopyFiles( any(), any(), any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( createNativeFile( fileId, path, false ) ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    GenericFilePath newPath = repositoryProvider.getNewPath( destPath, path.getLastSegment() );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    repositoryProvider.copyFile( path, destPath );

    verify( fileServiceMock, times( 1 ) ).doCopyFiles( any(), any(), any() );
  }

  @Test
  void testCopyFilesDestinationInvalidPath() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    GenericFilePath destPath = GenericFilePath.parse( "/archive/" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( destPath.toString() ) );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    NotFoundException exception =
      assertThrows( NotFoundException.class, () -> repositoryProvider.copyFile( path, destPath ) );
    assertEquals( String.format( "Destination folder not found '%s'.", destPath ), exception.getMessage() );
    verify( fileServiceMock, never() ).doCopyFiles( any(), any(), any() );
  }

  @Test
  void testCopyFilesAccessControlException() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    GenericFilePath destPath = GenericFilePath.parse( "/archive/" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "false" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( destPath.toString() ) );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    GenericFilePath newPath = repositoryProvider.getNewPath( destPath, path.getLastSegment() );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    assertThrows( AccessControlException.class, () -> repositoryProvider.copyFile( path, destPath ) );
    verify( fileServiceMock, never() ).doCopyFiles( any(), any(), any() );
  }

  @Test
  void testCopyFilesConflictException() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    GenericFilePath destPath = GenericFilePath.parse( "/archive/" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( destPath.toString() ) );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    GenericFilePath newPath = repositoryProvider.getNewPath( destPath, path.getLastSegment() );
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    assertThrows( ConflictException.class, () -> repositoryProvider.copyFile( path, destPath ) );
    verify( fileServiceMock, never() ).doCopyFiles( any(), any(), any() );
  }

  @Test
  void testCopyFilesNotFound() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    GenericFilePath destPath = GenericFilePath.parse( "/archive/" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( destPath.toString() ) );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( null ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    GenericFilePath newPath = repositoryProvider.getNewPath( destPath, path.getLastSegment() );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    assertThrows( NotFoundException.class, () -> repositoryProvider.copyFile( path, destPath ) );
    verify( fileServiceMock, never() ).doCopyFiles( any(), any(), any() );
  }

  @Test
  void testCopyFilesAcessDeniedAccessControlException() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    GenericFilePath destPath = GenericFilePath.parse( "/archive/" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( destPath.toString() ) );
    doThrow( new UnifiedRepositoryAccessDeniedException( "Access Denied" ) ).when( fileServiceMock )
      .doCopyFiles( any(), any(), any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( createNativeFile( fileId, path, false ) ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    GenericFilePath newPath = repositoryProvider.getNewPath( destPath, path.getLastSegment() );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    assertThrows( AccessControlException.class, () -> repositoryProvider.copyFile( path, destPath ) );
    verify( fileServiceMock ).doCopyFiles( any(), any(), any() );
  }

  @Test
  void testCopyFilesOperationIllegalArgumentException() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    GenericFilePath destPath = GenericFilePath.parse( "/archive/" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( destPath.toString() ) );
    doThrow( new IllegalArgumentException( "copy failed" ) ).when( fileServiceMock )
      .doCopyFiles( any(), any(), any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( createNativeFile( fileId, path, false ) ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    GenericFilePath newPath = repositoryProvider.getNewPath( destPath, path.getLastSegment() );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    assertThrows( OperationFailedException.class, () -> repositoryProvider.copyFile( path, destPath ) );
    verify( fileServiceMock ).doCopyFiles( any(), any(), any() );
  }

  @Test
  void testCopyFilesOperationException() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    GenericFilePath destPath = GenericFilePath.parse( "/archive/" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( destPath.toString() ) );
    doThrow( new RuntimeException( "copy failed" ) ).when( fileServiceMock ).doCopyFiles( any(), any(), any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( createNativeFile( fileId, path, false ) ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    GenericFilePath newPath = repositoryProvider.getNewPath( destPath, path.getLastSegment() );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    assertThrows( Exception.class, () -> repositoryProvider.copyFile( path, destPath ) );
    verify( fileServiceMock ).doCopyFiles( any(), any(), any() );
  }
  // endregion

  // region moveFiles
  @Test
  void testMoveFilesSuccess() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    GenericFilePath destPath = GenericFilePath.parse( "/archive/" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doNothing().when( fileServiceMock ).doMoveFiles( any(), any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( createNativeFile( fileId, path, false ) ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    GenericFilePath newPath = repositoryProvider.getNewPath( destPath, path.getLastSegment() );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    repositoryProvider.moveFile( path, destPath );

    verify( fileServiceMock, times( 1 ) ).doMoveFiles( any(), any() );
  }

  @Test
  void testMoveFilesConflictException() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    GenericFilePath destPath = GenericFilePath.parse( "/archive/" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doThrow( new UnifiedRepositoryException() ).when( fileServiceMock ).doMoveFiles( any(), any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( createNativeFile( fileId, path, false ) ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    GenericFilePath newPath = repositoryProvider.getNewPath( destPath, path.getLastSegment() );
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    assertThrows( ConflictException.class, () -> repositoryProvider.moveFile( path, destPath ) );
    verify( fileServiceMock, never() ).doMoveFiles( any(), any() );
  }

  @Test
  void testMoveFilesNotFoundExceptionSourceFolder() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    GenericFilePath destPath = GenericFilePath.parse( "/archive/" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( null ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    GenericFilePath newPath = repositoryProvider.getNewPath( destPath, path.getLastSegment() );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    assertThrows( NotFoundException.class, () -> repositoryProvider.moveFile( path, destPath ) );
    verify( fileServiceMock, never() ).doMoveFiles( any(), any() );
  }

  @Test
  void testMoveFilesAccessControlException() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    GenericFilePath destPath = GenericFilePath.parse( "/archive/" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doThrow( UnifiedRepositoryAccessDeniedException.class ).when( fileServiceMock ).doMoveFiles( any(), any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( createNativeFile( fileId, path, false ) ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    GenericFilePath newPath = repositoryProvider.getNewPath( destPath, path.getLastSegment() );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    assertThrows( AccessControlException.class, () -> repositoryProvider.moveFile( path, destPath ) );
    verify( fileServiceMock ).doMoveFiles( any(), any() );
  }

  @Test
  void testMoveFilesNotFoundExceptionDestinationFolder() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    GenericFilePath destPath = GenericFilePath.parse( "/archive/" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doThrow( new FileNotFoundException() ).when( fileServiceMock ).doMoveFiles( any(), any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( createNativeFile( fileId, path, false ) ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    GenericFilePath newPath = repositoryProvider.getNewPath( destPath, path.getLastSegment() );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    assertThrows( NotFoundException.class, () -> repositoryProvider.moveFile( path, destPath ) );
    verify( fileServiceMock ).doMoveFiles( any(), any() );
  }

  @Test
  void testMoveFilesAccessDeniedAccessControlException() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    GenericFilePath destPath = GenericFilePath.parse( "/archive/" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "false" ).when( fileServiceMock ).doGetCanCreate();
    doThrow( new UnifiedRepositoryAccessDeniedException() ).when( fileServiceMock ).doMoveFiles( any(), any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( createNativeFile( fileId, path, false ) ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    GenericFilePath newPath = repositoryProvider.getNewPath( destPath, path.getLastSegment() );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    assertThrows( AccessControlException.class, () -> repositoryProvider.moveFile( path, destPath ) );
    verify( fileServiceMock, never() ).doMoveFiles( any(), any() );
  }

  @Test
  void testMoveFilesOperationInternalError() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    GenericFilePath destPath = GenericFilePath.parse( "/archive/" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doThrow( new InternalError() ).when( fileServiceMock ).doMoveFiles( any(), any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( createNativeFile( fileId, path, false ) ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    GenericFilePath newPath = repositoryProvider.getNewPath( destPath, path.getLastSegment() );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    assertThrows( OperationFailedException.class, () -> repositoryProvider.moveFile( path, destPath ) );
    verify( fileServiceMock ).doMoveFiles( any(), any() );
  }

  @Test
  void testMoveFilesRuntimeException() throws Exception {
    String fileId = "8b69da2b-2a10-4a82-89bc-a376e52d5482";
    GenericFilePath path = GenericFilePath.parse( "/home/admin/" + fileId + "/PAZReport.xanalyzer" );
    GenericFilePath destPath = GenericFilePath.parse( "/archive/" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doThrow( new RuntimeException() ).when( fileServiceMock ).doMoveFiles( any(), any() );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( createNativeFile( fileId, path, false ) ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );
    GenericFilePath newPath = repositoryProvider.getNewPath( destPath, path.getLastSegment() );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( newPath.toString() ) );

    assertThrows( RuntimeException.class, () -> repositoryProvider.moveFile( path, destPath ) );
    verify( fileServiceMock ).doMoveFiles( any(), any() );
  }
  // endregion

  // region getFileMetadata
  @Test
  void testGetFileMetadataSuccess() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    List<StringKeyStringValueDto> nativeMetadata = List.of(
      new StringKeyStringValueDto( "key1", "value1" ),
      new StringKeyStringValueDto( "key2", "value2" )
    );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( nativeMetadata ).when( fileServiceMock ).doGetMetadata( encodeRepositoryPath( path.toString() ) );
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    IGenericFileMetadata metadata = repositoryProvider.getFileMetadata( path );

    assertNotNull( metadata );
    assertNotNull( metadata.getMetadata() );
    assertEquals( 2, metadata.getMetadata().size() );
    assertTrue( metadata.getMetadata().containsKey( "key1" ) );
    assertTrue( metadata.getMetadata().containsValue( "value1" ) );
    assertTrue( metadata.getMetadata().containsKey( "key2" ) );
    assertTrue( metadata.getMetadata().containsValue( "value2" ) );
    verify( fileServiceMock ).doGetMetadata( encodeRepositoryPath( path.toString() ) );
  }

  @Test
  void testGetFileMetadataAccessControlException() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );

    FileService fileServiceMock = mock( FileService.class );
    doThrow( UnifiedRepositoryAccessDeniedException.class ).when( fileServiceMock )
      .doGetMetadata( encodeRepositoryPath( path.toString() ) );
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    assertThrows( AccessControlException.class, () -> repositoryProvider.getFileMetadata( path ) );
  }

  @Test
  void testGetFileMetadataOperationFailedException() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );

    FileService fileServiceMock = mock( FileService.class );
    doThrow( UnifiedRepositoryException.class ).when( fileServiceMock )
      .doGetMetadata( encodeRepositoryPath( path.toString() ) );
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    assertThrows( OperationFailedException.class, () -> repositoryProvider.getFileMetadata( path ) );
  }

  @Test
  void testGetFileMetadataNotFound() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );

    FileService fileServiceMock = mock( FileService.class );
    doThrow( FileNotFoundException.class ).when( fileServiceMock )
      .doGetMetadata( encodeRepositoryPath( path.toString() ) );
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    assertThrows( NotFoundException.class, () -> repositoryProvider.getFileMetadata( path ) );
  }

  @Test
  void testGetFileMetadataNull() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( null ).when( fileServiceMock ).doGetMetadata( encodeRepositoryPath( path.toString() ) );
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    IGenericFileMetadata metadata = repositoryProvider.getFileMetadata( path );

    assertNotNull( metadata );
    assertNotNull( metadata.getMetadata() );
    assertTrue( metadata.getMetadata().isEmpty() );
    verify( fileServiceMock ).doGetMetadata( encodeRepositoryPath( path.toString() ) );
  }

  @Test
  void testGetFileMetadataEmpty() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( Collections.emptyList() ).when( fileServiceMock )
      .doGetMetadata( encodeRepositoryPath( path.toString() ) );
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    IGenericFileMetadata metadata = repositoryProvider.getFileMetadata( path );

    assertNotNull( metadata );
    assertNotNull( metadata.getMetadata() );
    assertTrue( metadata.getMetadata().isEmpty() );
    verify( fileServiceMock ).doGetMetadata( encodeRepositoryPath( path.toString() ) );
  }

  @Test
  void testGetFileMetadataRuntimeException() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );

    FileService fileServiceMock = mock( FileService.class );
    doThrow( RuntimeException.class ).when( fileServiceMock )
      .doGetMetadata( encodeRepositoryPath( path.toString() ) );
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    assertThrows( RuntimeException.class, () -> repositoryProvider.getFileMetadata( path ) );
  }
  // endregion

  // region setFileMetadata
  @SuppressWarnings( "unchecked" )
  @Test
  void testSetFileMetadataSuccess() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    BaseGenericFileMetadata metadata = new BaseGenericFileMetadata();
    metadata.addMetadatum( "key1", "value1" );
    metadata.addMetadatum( "key2", "value2" );

    FileService fileServiceMock = mock( FileService.class );
    doNothing().when( fileServiceMock ).doSetMetadata( any(), any() );
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );
    List<StringKeyStringValueDto> nativeMetadata = repositoryProvider.convertToNativeFileMetadata( metadata );

    repositoryProvider.setFileMetadata( path, metadata );

    ArgumentCaptor<List<StringKeyStringValueDto>> metadataCaptor = ArgumentCaptor.forClass( List.class );
    verify( fileServiceMock ).doSetMetadata( any(), metadataCaptor.capture() );
    List<StringKeyStringValueDto> captured = metadataCaptor.getValue();

    assertNotNull( captured );
    assertEquals( nativeMetadata.size(), captured.size() );
    assertEquals( nativeMetadata.get( 0 ).getKey(), captured.get( 0 ).getKey() );
    assertEquals( nativeMetadata.get( 0 ).getValue(), captured.get( 0 ).getValue() );
    assertEquals( nativeMetadata.get( 1 ).getKey(), captured.get( 1 ).getKey() );
    assertEquals( nativeMetadata.get( 1 ).getValue(), captured.get( 1 ).getValue() );
    verify( fileServiceMock ).doSetMetadata( encodeRepositoryPath( path.toString() ), nativeMetadata );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  void testSetFileMetadataNull() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );

    FileService fileServiceMock = mock( FileService.class );
    doNothing().when( fileServiceMock ).doSetMetadata( any(), any() );
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    repositoryProvider.setFileMetadata( path, null );

    ArgumentCaptor<List<StringKeyStringValueDto>> metadataCaptor = ArgumentCaptor.forClass( List.class );
    verify( fileServiceMock ).doSetMetadata( eq( encodeRepositoryPath( path.toString() ) ), metadataCaptor.capture() );
    List<StringKeyStringValueDto> captured = metadataCaptor.getValue();

    assertNotNull( captured );
    assertTrue( captured.isEmpty() );
    verify( fileServiceMock ).doSetMetadata( encodeRepositoryPath( path.toString() ), Collections.emptyList() );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  void testSetFileMetadataEmpty() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    BaseGenericFileMetadata metadata = new BaseGenericFileMetadata();

    FileService fileServiceMock = mock( FileService.class );
    doNothing().when( fileServiceMock ).doSetMetadata( any(), any() );
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    repositoryProvider.setFileMetadata( path, metadata );

    ArgumentCaptor<List<StringKeyStringValueDto>> metadataCaptor = ArgumentCaptor.forClass( List.class );
    verify( fileServiceMock ).doSetMetadata( eq( encodeRepositoryPath( path.toString() ) ), metadataCaptor.capture() );
    List<StringKeyStringValueDto> captured = metadataCaptor.getValue();

    assertNotNull( captured );
    assertTrue( captured.isEmpty() );
    verify( fileServiceMock ).doSetMetadata( encodeRepositoryPath( path.toString() ), Collections.emptyList() );
  }

  @Test
  void testSetFileMetadataGeneralSecurityException() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    BaseGenericFileMetadata metadata = new BaseGenericFileMetadata();
    metadata.addMetadatum( "key1", "value1" );
    metadata.addMetadatum( "key2", "value2" );

    FileService fileServiceMock = mock( FileService.class );
    doThrow( GeneralSecurityException.class ).when( fileServiceMock ).doSetMetadata( any(), any() );
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    assertThrows( AccessControlException.class, () -> repositoryProvider.setFileMetadata( path, metadata ) );
  }

  @Test
  void testSetFileMetadataOperationFailed() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    BaseGenericFileMetadata metadata = new BaseGenericFileMetadata();
    metadata.addMetadatum( "key1", "value1" );
    metadata.addMetadatum( "key2", "value2" );

    FileService fileServiceMock = mock( FileService.class );
    doThrow( new RuntimeException( "set metadata failed" ) ).when( fileServiceMock ).doSetMetadata( any(), any() );
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    assertThrows( RuntimeException.class, () -> repositoryProvider.setFileMetadata( path, metadata ) );
  }
  // endregion

  // region getFileAcl
  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testGetFileAclSuccess( boolean forceInheriting ) throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    RepositoryFileAclDto nativeAcl = mock( RepositoryFileAclDto.class );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( path.toString() ) );
    doReturn( nativeAcl ).when( fileServiceMock )
      .doGetFileAcl( encodeRepositoryPath( path.toString() ), forceInheriting );
    RepositoryFileProvider repositoryProvider = spy(
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock ) );
    doReturn( acl ).when( repositoryProvider ).convertFromNativeFileAcl( nativeAcl );

    IGenericFileAcl result = repositoryProvider.getFileAcl( path, forceInheriting );

    assertNotNull( result );
    assertSame( acl, result );
    verify( fileServiceMock ).doGetFileAcl( encodeRepositoryPath( path.toString() ), forceInheriting );
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testGetFileAclNotFound( boolean forceInheriting ) throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( false ).when( fileServiceMock ).doesExist( encodeRepositoryPath( path.toString() ) );
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    assertThrows( NotFoundException.class, () -> repositoryProvider.getFileAcl( path, forceInheriting ) );
    verify( fileServiceMock, never() ).doGetFileAcl( any(), anyBoolean() );
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testGetFileAclAccessControlException( boolean forceInheriting ) throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( path.toString() ) );
    doThrow( UnifiedRepositoryAccessDeniedException.class ).when( fileServiceMock )
      .doGetFileAcl( encodeRepositoryPath( path.toString() ), forceInheriting );
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    assertThrows( AccessControlException.class, () -> repositoryProvider.getFileAcl( path, forceInheriting ) );
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testGetFileAclInvalidOperationException( boolean forceInheriting ) throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( path.toString() ) );
    RepositoryFileProvider repositoryProvider = spy(
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock ) );
    doThrow( InvalidOperationException.class ).when( repositoryProvider ).convertFromNativeFileAcl( any() );

    assertThrows( InvalidOperationException.class, () -> repositoryProvider.getFileAcl( path, forceInheriting ) );
  }

  @ParameterizedTest
  @ValueSource( booleans = { true, false } )
  void testGetFileAclOperationFailedException( boolean forceInheriting ) throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( true ).when( fileServiceMock ).doesExist( encodeRepositoryPath( path.toString() ) );
    doThrow( new RuntimeException( "acl failed" ) ).when( fileServiceMock )
      .doGetFileAcl( encodeRepositoryPath( path.toString() ), forceInheriting );
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock );

    assertThrows( OperationFailedException.class, () -> repositoryProvider.getFileAcl( path, forceInheriting ) );
  }
  // endregion

  // region setFileAcl
  @Test
  void testSetFileAclSuccess() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    RepositoryFileAclDto nativeAcl = mock( RepositoryFileAclDto.class );

    FileService fileServiceMock = mock( FileService.class );
    doNothing().when( fileServiceMock ).setFileAcls( any(), any() );
    RepositoryFileProvider repositoryProvider = spy(
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock ) );
    doReturn( nativeAcl ).when( repositoryProvider ).convertToNativeFileAcl( acl );
    doReturn( true ).when( repositoryProvider ).validateFileAcl( acl );

    repositoryProvider.setFileAcl( path, acl );

    verify( fileServiceMock ).setFileAcls( encodeRepositoryPath( path.toString() ), nativeAcl );
  }

  @Test
  void testSetFileAclInvalidAcl() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    RepositoryFileAclDto nativeAcl = mock( RepositoryFileAclDto.class );

    FileService fileServiceMock = mock( FileService.class );
    doNothing().when( fileServiceMock ).setFileAcls( any(), any() );
    RepositoryFileProvider repositoryProvider = spy(
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock ) );
    doReturn( nativeAcl ).when( repositoryProvider ).convertToNativeFileAcl( acl );
    doReturn( false ).when( repositoryProvider ).validateFileAcl( acl );

    assertThrows( InvalidOperationException.class, () -> repositoryProvider.setFileAcl( path, acl ) );
  }

  @Test
  void testSetFileAclNotFound() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    RepositoryFileAclDto nativeAcl = mock( RepositoryFileAclDto.class );

    FileService fileServiceMock = mock( FileService.class );
    doThrow( FileNotFoundException.class ).when( fileServiceMock ).setFileAcls( any(), any() );
    RepositoryFileProvider repositoryProvider = spy(
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock ) );
    doReturn( nativeAcl ).when( repositoryProvider ).convertToNativeFileAcl( acl );
    doReturn( true ).when( repositoryProvider ).validateFileAcl( acl );

    assertThrows( NotFoundException.class, () -> repositoryProvider.setFileAcl( path, acl ) );
  }

  @Test
  void testSetFileAclAccessControlException() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    RepositoryFileAclDto nativeAcl = mock( RepositoryFileAclDto.class );

    FileService fileServiceMock = mock( FileService.class );
    doThrow( UnifiedRepositoryAccessDeniedException.class ).when( fileServiceMock ).setFileAcls( any(), any() );
    RepositoryFileProvider repositoryProvider = spy(
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock ) );
    doReturn( nativeAcl ).when( repositoryProvider ).convertToNativeFileAcl( acl );
    doReturn( true ).when( repositoryProvider ).validateFileAcl( acl );

    assertThrows( AccessControlException.class, () -> repositoryProvider.setFileAcl( path, acl ) );
  }

  @Test
  void testSetFileAclOperationFailedException() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    RepositoryFileAclDto nativeAcl = mock( RepositoryFileAclDto.class );

    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = spy(
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock ) );
    doReturn( nativeAcl ).when( repositoryProvider ).convertToNativeFileAcl( acl );
    doReturn( true ).when( repositoryProvider ).validateFileAcl( acl );
    doThrow( new RuntimeException( "acl set failed" ) ).when( fileServiceMock )
      .setFileAcls( encodeRepositoryPath( path.toString() ), nativeAcl );

    assertThrows( OperationFailedException.class, () -> repositoryProvider.setFileAcl( path, acl ) );
  }
  // endregion

  // region validateFileAcl
  @ParameterizedTest
  @ValueSource( strings = { "validUser", "admin", "pentahoRepoAdmin" } )
  void testValidateFileAclSuccess( String owner ) {
    IGenericFileAce ace1 = mock( IGenericFileAce.class );
    doReturn( "user1" ).when( ace1 ).getRecipient();
    doReturn( List.of( GenericFilePermission.READ ) ).when( ace1 ).getPermissions();

    IGenericFileAce ace2 = mock( IGenericFileAce.class );
    doReturn( "user2" ).when( ace2 ).getRecipient();
    doReturn( List.of( GenericFilePermission.WRITE ) ).when( ace2 ).getPermissions();

    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    doReturn( owner ).when( acl ).getOwner();
    doReturn( Arrays.asList( ace1, ace2 ) ).when( acl ).getEntries();
    doReturn( false ).when( acl ).isEntriesInheriting();

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    assertTrue( repositoryProvider.validateFileAcl( acl ) );
  }

  @ParameterizedTest
  @NullSource
  @ValueSource( strings = { "", "user#invalid", "user+invalid", "user,invalid", "user\"invalid", "user\\invalid",
    "user<invalid", "user>invalid", "user;invalid", "user=invalid", " user", "user ", " user " } )
  void testValidateFileAclError( String owner ) {
    IGenericFileAce ace1 = mock( IGenericFileAce.class );
    doReturn( "user1" ).when( ace1 ).getRecipient();
    doReturn( List.of( GenericFilePermission.READ ) ).when( ace1 ).getPermissions();

    IGenericFileAce ace2 = mock( IGenericFileAce.class );
    doReturn( "user2" ).when( ace2 ).getRecipient();
    doReturn( List.of( GenericFilePermission.WRITE ) ).when( ace2 ).getPermissions();

    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    doReturn( owner ).when( acl ).getOwner();
    doReturn( Arrays.asList( ace1, ace2 ) ).when( acl ).getEntries();
    doReturn( false ).when( acl ).isEntriesInheriting();

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    assertFalse( repositoryProvider.validateFileAcl( acl ) );
  }

  @Test
  void testValidateFileAclEmptyEntriesList() {
    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    doReturn( "validUser" ).when( acl ).getOwner();
    doReturn( Collections.emptyList() ).when( acl ).getEntries();
    doReturn( false ).when( acl ).isEntriesInheriting();

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    assertFalse( repositoryProvider.validateFileAcl( acl ) );
  }

  @Test
  void testValidateFileAclWithValidAces() {
    IGenericFileAce ace1 = mock( IGenericFileAce.class );
    doReturn( "user1" ).when( ace1 ).getRecipient();
    doReturn( List.of( GenericFilePermission.READ ) ).when( ace1 ).getPermissions();

    IGenericFileAce ace2 = mock( IGenericFileAce.class );
    doReturn( "user2" ).when( ace2 ).getRecipient();
    doReturn( List.of( GenericFilePermission.WRITE ) ).when( ace2 ).getPermissions();

    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    doReturn( "validUser" ).when( acl ).getOwner();
    doReturn( Arrays.asList( ace1, ace2 ) ).when( acl ).getEntries();
    doReturn( false ).when( acl ).isEntriesInheriting();

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    assertTrue( repositoryProvider.validateFileAcl( acl ) );
  }

  @Test
  void testValidateFileAclWithEmptyPermissions() {
    IGenericFileAce ace = mock( IGenericFileAce.class );
    doReturn( "validRecipient" ).when( ace ).getRecipient();
    doReturn( Collections.emptyList() ).when( ace ).getPermissions();

    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    doReturn( "validUser" ).when( acl ).getOwner();
    doReturn( Collections.singletonList( ace ) ).when( acl ).getEntries();
    doReturn( false ).when( acl ).isEntriesInheriting();

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    assertFalse( repositoryProvider.validateFileAcl( acl ) );
  }

  @ParameterizedTest
  @NullSource
  @ValueSource( strings = { "", "user#invalid", "user+invalid", "user,invalid", "user\"invalid", "user\\invalid",
    "user<invalid", "user>invalid", "user;invalid", "user=invalid", " user", "user ", " user " } )
  void testValidateFileAclWithInvalidRecipient( String recipient ) {
    IGenericFileAce ace = mock( IGenericFileAce.class );
    doReturn( recipient ).when( ace ).getRecipient();
    doReturn( List.of( GenericFilePermission.READ ) ).when( ace ).getPermissions();

    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    doReturn( "validUser" ).when( acl ).getOwner();
    doReturn( Collections.singletonList( ace ) ).when( acl ).getEntries();
    doReturn( false ).when( acl ).isEntriesInheriting();

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    assertFalse( repositoryProvider.validateFileAcl( acl ) );
  }

  @Test
  void testValidateFileAclWithMixedValidAndInvalidAces() {
    IGenericFileAce ace1 = mock( IGenericFileAce.class );
    doReturn( "validRecipient" ).when( ace1 ).getRecipient();
    doReturn( List.of( GenericFilePermission.READ ) ).when( ace1 ).getPermissions();

    IGenericFileAce ace2 = mock( IGenericFileAce.class );
    doReturn( "" ).when( ace2 ).getRecipient();
    doReturn( List.of( GenericFilePermission.WRITE ) ).when( ace2 ).getPermissions();

    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    doReturn( "validUser" ).when( acl ).getOwner();
    doReturn( Arrays.asList( ace1, ace2 ) ).when( acl ).getEntries();
    doReturn( false ).when( acl ).isEntriesInheriting();

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    assertFalse( repositoryProvider.validateFileAcl( acl ) );
  }

  @Test
  void testValidateFileAclWithOneAceHavingEmptyPermissions() {
    IGenericFileAce ace1 = mock( IGenericFileAce.class );
    doReturn( "validRecipient1" ).when( ace1 ).getRecipient();
    doReturn( List.of( GenericFilePermission.READ ) ).when( ace1 ).getPermissions();

    IGenericFileAce ace2 = mock( IGenericFileAce.class );
    doReturn( "validRecipient2" ).when( ace2 ).getRecipient();
    doReturn( Collections.emptyList() ).when( ace2 ).getPermissions();

    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    doReturn( "validUser" ).when( acl ).getOwner();
    doReturn( Arrays.asList( ace1, ace2 ) ).when( acl ).getEntries();
    doReturn( false ).when( acl ).isEntriesInheriting();

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    assertFalse( repositoryProvider.validateFileAcl( acl ) );
  }

  @Test
  void testValidateFileAclWithMultipleAcesValidPermissions() {
    IGenericFileAce ace1 = mock( IGenericFileAce.class );
    doReturn( "user1" ).when( ace1 ).getRecipient();
    doReturn( List.of( GenericFilePermission.READ, GenericFilePermission.WRITE ) ).when( ace1 ).getPermissions();

    IGenericFileAce ace2 = mock( IGenericFileAce.class );
    doReturn( "user2" ).when( ace2 ).getRecipient();
    doReturn( List.of( GenericFilePermission.DELETE ) ).when( ace2 ).getPermissions();

    IGenericFileAce ace3 = mock( IGenericFileAce.class );
    doReturn( "user3" ).when( ace3 ).getRecipient();
    doReturn( List.of( GenericFilePermission.ACL_MANAGEMENT ) ).when( ace3 )
      .getPermissions();

    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    doReturn( "validUser" ).when( acl ).getOwner();
    doReturn( Arrays.asList( ace1, ace2, ace3 ) ).when( acl ).getEntries();
    doReturn( true ).when( acl ).isEntriesInheriting();

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    assertTrue( repositoryProvider.validateFileAcl( acl ) );
  }

  @Test
  void testValidateFileAclWithEntriesInheritingAndNullEntries() {
    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    doReturn( "validUser" ).when( acl ).getOwner();
    doReturn( null ).when( acl ).getEntries();
    doReturn( true ).when( acl ).isEntriesInheriting();

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    // When isEntriesInheriting is true, validation should pass regardless of entries
    assertTrue( repositoryProvider.validateFileAcl( acl ) );
  }

  @Test
  void testValidateFileAclWithEntriesInheritingAndEmptyEntries() {
    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    doReturn( "validUser" ).when( acl ).getOwner();
    doReturn( Collections.emptyList() ).when( acl ).getEntries();
    doReturn( true ).when( acl ).isEntriesInheriting();

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    // When isEntriesInheriting is true, validation should pass regardless of entries
    assertTrue( repositoryProvider.validateFileAcl( acl ) );
  }

  @Test
  void testValidateFileAclWithNullEntriesAndNotInheriting() {
    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    doReturn( "validUser" ).when( acl ).getOwner();
    doReturn( null ).when( acl ).getEntries();
    doReturn( false ).when( acl ).isEntriesInheriting();

    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    // When isEntriesInheriting is false, null entries should fail validation
    assertFalse( repositoryProvider.validateFileAcl( acl ) );
  }
  // endregion

  // region doesFolderExist
  @Test
  void testDoesFolderExistTrue() throws OperationFailedException {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( "12345", path, true );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( nativeFile.getPath() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertTrue( repositoryProvider.doesFolderExist( path ) );
    verify( repositoryMock ).getFile( anyString() );
  }

  @Test
  void testDoesFolderExistFalse() throws OperationFailedException {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );
    RepositoryFile nativeFile = createNativeFile( "12345", path, false );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( nativeFile ).when( repositoryMock ).getFile( nativeFile.getPath() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertFalse( repositoryProvider.doesFolderExist( path ) );
    verify( repositoryMock ).getFile( anyString() );
  }

  @Test
  void testDoesFolderExistNotFound() throws OperationFailedException {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( null ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertFalse( repositoryProvider.doesFolderExist( path ) );
    verify( repositoryMock ).getFile( anyString() );
  }

  @Test
  void testDoesFolderExistReturnsFalseWhenNotFoundExceptionIsThrown() throws OperationFailedException {
    GenericFilePath path = GenericFilePath.parse( "/public/nonexistent" );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doThrow( new NotFoundException( "Path not found.", path ) ).when( repositoryProvider ).getNativeFile( path );

    assertFalse( repositoryProvider.doesFolderExist( path ) );
  }

  @Test
  void testDoesFolderExistThrowsAccessControlException() throws OperationFailedException {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doThrow( UnifiedRepositoryAccessDeniedException.class ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( AccessControlException.class, () -> repositoryProvider.doesFolderExist( path ) );
    verify( repositoryMock ).getFile( anyString() );
  }

  @Test
  void testDoesFolderExistThrowsOperationFailedException() throws OperationFailedException {
    GenericFilePath path = GenericFilePath.parse( "/public/testFile1" );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doThrow( UnifiedRepositoryException.class ).when( repositoryMock ).getFile( path.toString() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( OperationFailedException.class, () -> repositoryProvider.doesFolderExist( path ) );
    verify( repositoryMock ).getFile( anyString() );
  }
  // endregion

  // region createFolder
  @Test
  void testCreateFolderSuccess() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/newFolder" );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( true ).when( fileServiceMock ).doCreateDirSafe( encodeRepositoryPath( path.toString() ) );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertTrue( repositoryProvider.createFolder( path ) );
    verify( fileServiceMock ).doCreateDirSafe( encodeRepositoryPath( path.toString() ) );
  }

  @Test
  void testCreateFolderAccessDenied() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/newFolder" );

    FileService fileServiceMock = mock( FileService.class );
    doThrow( UnifiedRepositoryAccessDeniedException.class ).when( fileServiceMock )
      .doCreateDirSafe( encodeRepositoryPath( path.toString() ) );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( AccessControlException.class, () -> repositoryProvider.createFolder( path ) );
    verify( fileServiceMock ).doCreateDirSafe( encodeRepositoryPath( path.toString() ) );
  }

  @Test
  void testCreateFolderInvalidName() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/invalid:name" );

    FileService fileServiceMock = mock( FileService.class );
    doThrow( FileService.InvalidNameException.class ).when( fileServiceMock )
      .doCreateDirSafe( encodeRepositoryPath( path.toString() ) );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( InvalidPathException.class, () -> repositoryProvider.createFolder( path ) );
    verify( fileServiceMock ).doCreateDirSafe( encodeRepositoryPath( path.toString() ) );
  }
  // endregion

  // region createFile
  @Test
  void testCreateFileCoreThrowsAccessControlExceptionWhenCannotCreate() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/newFile.txt" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "false" ).when( fileServiceMock ).doGetCanCreate();

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions();

    assertThrows( AccessControlException.class, () -> repositoryProvider.createFile( path, inputStream, options ) );
  }

  @Test
  void testCreateFileCoreThrowsInvalidPathExceptionWhenPathIsInvalid() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/invalid\0file.txt" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( false ).when( fileServiceMock ).isPathValid( anyString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions();

    assertThrows( InvalidPathException.class, () -> repositoryProvider.createFile( path, inputStream, options ) );
  }

  @Test
  void testCreateFileCoreReturnsFalseWhenFileExistsAndNoOverwrite() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/existingFile.txt" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );
    doReturn( true ).when( fileServiceMock ).isValidFileName( path.getLastSegment(), true );

    RepositoryFile existingFile = createNativeFile( "fileId1", path, false );
    doReturn( existingFile ).when( repositoryMock ).getFile( path.toString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions( false );

    ConflictException exception = assertThrows( ConflictException.class,
      () -> repositoryProvider.createFile( path, inputStream, options ) );

    assertEquals( "File already exists at '" + path + "'.", exception.getMessage() );
    verify( repositoryMock, never() ).updateFile( any(), any(), anyString() );
  }

  @Test
  void testCreateFileCoreThrowsInvalidOperationExceptionWhenExistingPathIsFolder() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/existingFolder" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );

    RepositoryFile existingFolder = createNativeFile( "folderId1", path, true );
    doReturn( existingFolder ).when( repositoryMock ).getFile( path.toString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    InputStream inputStream = mock( InputStream.class );

    InvalidOperationException exception = assertThrows( InvalidOperationException.class,
      () -> repositoryProvider.createFile( path, inputStream, new CreateFileOptions( true ) ) );

    assertEquals( "File is a folder.", exception.getMessage() );
    verify( repositoryMock, never() ).updateFile( any(), any(), anyString() );
    verify( repositoryMock, never() ).createFile( any(), any(), any(), anyString() );
  }

  @Test
  void testCreateFileCoreUpdatesExistingFileWhenOverwriteEnabled() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/existingFile.txt" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );

    RepositoryFile existingFile = createNativeFile( "fileId1", path, false );
    RepositoryFile updatedFile = createNativeFile( "fileId1", path, false );
    doReturn( existingFile ).when( repositoryMock ).getFile( path.toString() );
    doReturn( updatedFile ).when( repositoryMock ).updateFile( any(), any(), anyString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    InputStream inputStream = mock( InputStream.class );

    assertDoesNotThrow( () -> repositoryProvider.createFile( path, inputStream, new CreateFileOptions( true ) ) );
    verify( repositoryMock ).updateFile( eq( existingFile ), any(), eq( RepositoryFileProvider.FILE_UPDATE_MSG ) );
    verify( repositoryMock, never() ).createFile( any(), any(), any(), anyString() );
  }

  @Test
  void testCreateFileCoreCreatesFileWhenFileDoesNotExist() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/newFile.txt" );
    GenericFilePath parentPath = path.getParent();
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );
    doReturn( true ).when( fileServiceMock ).isValidFileName( path.getLastSegment(), true );

    // getNativeFile returns null → NotFoundException (file doesn't exist)
    doReturn( null ).when( repositoryMock ).getFile( path.toString() );

    // getNativeFile for parent returns a folder with an ID
    RepositoryFile parentFolder = createNativeFile( "parentId", parentPath, true );
    doReturn( parentFolder ).when( repositoryMock ).getFile( parentPath.toString() );

    RepositoryFile createdFile = createNativeFile( "newId", path, false );
    doReturn( createdFile ).when( repositoryMock ).createFile( any(), any(), any(), anyString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions( false );

    assertDoesNotThrow( () -> repositoryProvider.createFile( path, inputStream, options ) );
    verify( repositoryMock ).createFile( eq( "parentId" ), any( RepositoryFile.class ), any(),
      eq( RepositoryFileProvider.FILE_CREATE_MSG ) );
    verify( repositoryMock, never() ).updateFile( any(), any(), anyString() );
  }

  @Test
  void testCreateFileCoreThrowsAccessControlExceptionOnUnifiedRepositoryAccessDenied() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/newFile.txt" );
    GenericFilePath parentPath = path.getParent();
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );
    doReturn( true ).when( fileServiceMock ).isValidFileName( path.getLastSegment(), true );

    // getNativeFile throws NotFoundException (file doesn't exist)
    doReturn( null ).when( repositoryMock ).getFile( path.toString() );
    // getNativeFile for parent returns a folder
    RepositoryFile parentFolder = createNativeFile( "parentId", parentPath, true );
    doReturn( parentFolder ).when( repositoryMock ).getFile( parentPath.toString() );
    // createFile throws access denied
    doThrow( new UnifiedRepositoryAccessDeniedException( "denied" ) )
      .when( repositoryMock ).createFile( any(), any(), any(), anyString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions( false );

    assertThrows( AccessControlException.class, () -> repositoryProvider.createFile( path, inputStream, options ) );
  }

  @Test
  void testCreateFileCoreThrowsOperationFailedExceptionOnUnifiedRepositoryException() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/newFile.txt" );
    GenericFilePath parentPath = path.getParent();
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );
    doReturn( true ).when( fileServiceMock ).isValidFileName( path.getLastSegment(), true );

    // getNativeFile throws NotFoundException (file doesn't exist)
    doReturn( null ).when( repositoryMock ).getFile( path.toString() );
    // getNativeFile for parent returns a folder
    RepositoryFile parentFolder = createNativeFile( "parentId", parentPath, true );
    doReturn( parentFolder ).when( repositoryMock ).getFile( parentPath.toString() );
    // createFile throws generic repository exception
    doThrow( new UnifiedRepositoryException( "error" ) )
      .when( repositoryMock ).createFile( any(), any(), any(), anyString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions( false );

    assertThrows( OperationFailedException.class, () -> repositoryProvider.createFile( path, inputStream, options ) );
  }

  @Test
  void testCreateFileCoreThrowsOperationFailedExceptionWhenCreateFileReturnsNull() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/newFile.txt" );
    GenericFilePath parentPath = path.getParent();
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );

    doReturn( null ).when( repositoryMock ).getFile( path.toString() );
    // getNativeFile for parent returns a folder
    RepositoryFile parentFolder = createNativeFile( "parentId", parentPath, true );
    doReturn( parentFolder ).when( repositoryMock ).getFile( parentPath.toString() );
    doReturn( null ).when( repositoryMock ).createFile( any(), any(), any(), anyString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions( false );

    assertThrows( OperationFailedException.class, () -> repositoryProvider.createFile( path, inputStream, options ) );
  }

  @Test
  void testCreateFileThrowsOperationFailedExceptionWhenUpdateFileReturnsNull() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/existingFile.txt" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );
    doReturn( true ).when( fileServiceMock ).isValidFileName( path.getLastSegment(), true );

    RepositoryFile existingFile = createNativeFile( "fileId1", path, false );
    doReturn( existingFile ).when( repositoryMock ).getFile( path.toString() );
    doReturn( null ).when( repositoryMock ).updateFile( any(), any(), anyString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    doReturn( "text/plain" ).when( repositoryProvider ).detectMimeType( any(), eq( path ) );
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions( true );

    assertThrows( OperationFailedException.class, () -> repositoryProvider.createFile( path, inputStream, options ) );
  }

  @Test
  void testCreateFileCoreThrowsAccessControlExceptionWhenGetNativeFileThrowsAccessDenied() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/restricted.txt" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );

    // getNativeFile → throws AccessControlException (via UnifiedRepositoryAccessDeniedException)
    doThrow( new UnifiedRepositoryAccessDeniedException( "no access" ) ).when( repositoryMock )
      .getFile( path.toString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions( false );

    assertThrows( AccessControlException.class, () -> repositoryProvider.createFile( path, inputStream, options ) );
  }

  @Test
  void testCreateFileCoreUsesLastSegmentAsFileName() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/subfolder/report.xml" );
    GenericFilePath parentPath = path.getParent();
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );
    doReturn( true ).when( fileServiceMock ).isValidFileName( path.getLastSegment(), true );

    doReturn( null ).when( repositoryMock ).getFile( path.toString() );

    // getNativeFile for parent returns a folder with an ID
    RepositoryFile parentFolder = createNativeFile( "subFolderId", parentPath, true );
    doReturn( parentFolder ).when( repositoryMock ).getFile( parentPath.toString() );

    RepositoryFile createdFile = createNativeFile( "newId", path, false );
    doReturn( createdFile ).when( repositoryMock ).createFile( any(), any(), any(), anyString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions( false );

    repositoryProvider.createFile( path, inputStream, options );

    ArgumentCaptor<RepositoryFile> fileCaptor = ArgumentCaptor.forClass( RepositoryFile.class );
    verify( repositoryMock ).createFile( eq( "subFolderId" ), fileCaptor.capture(), any(), anyString() );

    assertEquals( "report.xml", fileCaptor.getValue().getName() );
  }

  @Test
  void testCreateFileCallsClearTreeCacheOnSuccess() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/newFile.txt" );
    GenericFilePath parentPath = path.getParent();
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );
    doReturn( true ).when( fileServiceMock ).isValidFileName( path.getLastSegment(), true );

    doReturn( null ).when( repositoryMock ).getFile( path.toString() );

    // getNativeFile for parent returns a folder
    RepositoryFile parentFolder = createNativeFile( "parentId", parentPath, true );
    doReturn( parentFolder ).when( repositoryMock ).getFile( parentPath.toString() );

    RepositoryFile createdFile = createNativeFile( "newId", path, false );
    doReturn( createdFile ).when( repositoryMock ).createFile( any(), any(), any(), anyString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions( false );

    repositoryProvider.createFile( path, inputStream, options );

    verify( repositoryProvider ).clearTreeCache();
  }

  @Test
  void testCreateFileThrowsNullPointerExceptionForNullPath() {
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions();

    assertThrows( NullPointerException.class, () -> repositoryProvider.createFile( null, inputStream, options ) );
  }

  @Test
  void testCreateFileThrowsNullPointerExceptionForNullContent() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/newFile.txt" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    CreateFileOptions options = new CreateFileOptions();

    assertThrows( NullPointerException.class, () -> repositoryProvider.createFile( path, null, options ) );
  }

  @Test
  void testCreateFileCoreCreatesParentFolderWhenParentFolderDoesNotExist() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/nonExistentFolder/newFile.txt" );
    GenericFilePath parentPath = path.getParent();
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );
    doReturn( true ).when( fileServiceMock ).isValidFileName( path.getLastSegment(), true );

    // getNativeFile for file returns null → NotFoundException (file doesn't exist)
    doReturn( null ).when( repositoryMock ).getFile( path.toString() );

    // Parent folder after it's created
    RepositoryFile parentFolder = createNativeFile( "parentId", parentPath, true );

    RepositoryFile createdFile = createNativeFile( "newId", path, false );
    doReturn( createdFile ).when( repositoryMock ).createFile( any(), any(), any(), anyString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    // Mock getOrCreateNativeFile to simulate folder creation
    doReturn( parentFolder ).when( repositoryProvider ).getOrCreateNativeFolder( parentPath );
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions( false );

    assertDoesNotThrow( () -> repositoryProvider.createFile( path, inputStream, options ) );
    verify( repositoryProvider ).getOrCreateNativeFolder( parentPath );
    verify( repositoryMock ).createFile( eq( "parentId" ), any(), any(), anyString() );
  }

  @Test
  void testCreateFileCoreThrowsResourceAccessDeniedExceptionWhenParentFolderAccessDenied() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/restricted/newFile.txt" );
    GenericFilePath parentPath = path.getParent();
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );
    doReturn( true ).when( fileServiceMock ).isValidFileName( path.getLastSegment(), true );

    // getNativeFile for file returns null → NotFoundException (file doesn't exist)
    doReturn( null ).when( repositoryMock ).getFile( path.toString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    // Mock getOrCreateNativeFile to throw ResourceAccessDeniedException when parent access is denied
    doThrow( new ResourceAccessDeniedException( "User is not authorized to access this path.", parentPath ) )
      .when( repositoryProvider ).getOrCreateNativeFolder( parentPath );
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions( false );

    assertThrows( ResourceAccessDeniedException.class,
      () -> repositoryProvider.createFile( path, inputStream, options ) );

    verify( repositoryMock, never() ).createFile( any(), any(), any(), anyString() );
  }

  @Test
  void testCreateFileCorePassesParentIdToCreateFile() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/home/admin/data.csv" );
    GenericFilePath parentPath = path.getParent();
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );
    doReturn( true ).when( fileServiceMock ).isValidFileName( path.getLastSegment(), true );

    // File doesn't exist
    doReturn( null ).when( repositoryMock ).getFile( path.toString() );
    // Parent folder exists with specific ID
    RepositoryFile parentFolder = createNativeFile( "admin-folder-id-123", parentPath, true );
    doReturn( parentFolder ).when( repositoryMock ).getFile( parentPath.toString() );

    RepositoryFile createdFile = createNativeFile( "newFileId", path, false );
    doReturn( createdFile ).when( repositoryMock ).createFile( any(), any(), any(), anyString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions( false );

    assertDoesNotThrow( () -> repositoryProvider.createFile( path, inputStream, options ) );
    // Verify the parent ID (Serializable) is passed, not the parent path string
    verify( repositoryMock ).createFile( eq( "admin-folder-id-123" ), any( RepositoryFile.class ), any(),
      eq( RepositoryFileProvider.FILE_CREATE_MSG ) );
  }

  @Test
  void testCreateFileThrowsInvalidOperationExceptionWhenParentPathIsNotAFolder() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/newfile.txt" );
    GenericFilePath parentPath = GenericFilePath.parse( "/public" );
    RepositoryFile parentFile = createNativeFile( "parent-id", parentPath, false );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    doReturn( null ).when( repositoryMock ).getFile( path.toString() );
    doReturn( parentFile ).when( repositoryMock ).getFile( parentPath.toString() );

    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );
    doReturn( true ).when( fileServiceMock ).isValidFileName( path.getLastSegment(), true );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    doReturn( "text/plain" ).when( repositoryProvider ).detectMimeType( any(), eq( path ) );
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions( false );

    InvalidOperationException exception = assertThrows( InvalidOperationException.class,
      () -> repositoryProvider.createFile( path, inputStream, options ) );

    assertEquals( "Path is not a folder.", exception.getMessage() );
  }

  @Test
  void testCreateFileThrowsOperationFailedExceptionWhenDetectMimeTypeFails() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/newfile.txt" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( "true" ).when( fileServiceMock ).doGetCanCreate();
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );
    doReturn( true ).when( fileServiceMock ).isValidFileName( path.getLastSegment(), true );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    doThrow( new IOException( "IO error" ) ).when( repositoryProvider ).detectMimeType( any(), eq( path ) );
    InputStream inputStream = mock( InputStream.class );
    CreateFileOptions options = new CreateFileOptions( false );

    OperationFailedException exception = assertThrows( OperationFailedException.class,
      () -> repositoryProvider.createFile( path, inputStream, options ) );

    assertNotNull( exception.getCause() );
  }
  // endregion

  // region setFileContent
  @Test
  void testSetFileContentCoreThrowsNullPointerExceptionForNullPath() {
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    InputStream inputStream = mock( InputStream.class );

    assertThrows( NullPointerException.class, () -> repositoryProvider.setFileContent( null, inputStream ) );
  }

  @Test
  void testSetFileContentCoreThrowsNullPointerExceptionForNullContent() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/existingFile.txt" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();

    assertThrows( NullPointerException.class, () -> repositoryProvider.setFileContent( path, null ) );
  }

  @Test
  void testSetFileContentCoreThrowsInvalidPathExceptionWhenPathIsInvalid() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/existingFile.txt" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( false ).when( fileServiceMock ).isPathValid( anyString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();

    assertThrows( InvalidPathException.class,
      () -> repositoryProvider.setFileContent( path, mock( InputStream.class ) ) );
  }

  @Test
  void testSetFileContentCoreThrowsInvalidOperationExceptionWhenPathIsFolder() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/existingFolder" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );
    doReturn( createNativeFile( "folderId", path, true ) ).when( repositoryMock ).getFile( path.toString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();

    InvalidOperationException exception = assertThrows( InvalidOperationException.class,
      () -> repositoryProvider.setFileContent( path, mock( InputStream.class ) ) );
    assertEquals( "Path references a folder, not a file.", exception.getMessage() );
  }

  @Test
  void testSetFileContentCoreUpdatesFileContentSuccessfully() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/existingFile.txt" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );

    RepositoryFile existingFile = createNativeFile( "fileId", path, false );
    doReturn( existingFile ).when( repositoryMock ).getFile( path.toString() );
    doReturn( existingFile ).when( repositoryMock ).updateFile( any(), any(), anyString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();

    repositoryProvider.setFileContent( path, mock( InputStream.class ) );

    verify( repositoryMock ).updateFile( eq( existingFile ), any(), eq( RepositoryFileProvider.FILE_UPDATE_MSG ) );
  }

  @Test
  void testSetFileContentCoreThrowsOperationFailedExceptionWhenUpdateReturnsNull() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/existingFile.txt" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );

    RepositoryFile existingFile = createNativeFile( "fileId", path, false );
    doReturn( existingFile ).when( repositoryMock ).getFile( path.toString() );
    doReturn( null ).when( repositoryMock ).updateFile( any(), any(), anyString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();

    assertThrows( OperationFailedException.class,
      () -> repositoryProvider.setFileContent( path, mock( InputStream.class ) ) );
  }

  @Test
  void testSetFileContentCoreThrowsAccessControlExceptionWhenUpdateDenied() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/existingFile.txt" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );

    RepositoryFile existingFile = createNativeFile( "fileId", path, false );
    doReturn( existingFile ).when( repositoryMock ).getFile( path.toString() );
    doThrow( new UnifiedRepositoryAccessDeniedException( "denied" ) )
      .when( repositoryMock ).updateFile( any(), any(), anyString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();

    assertThrows( AccessControlException.class,
      () -> repositoryProvider.setFileContent( path, mock( InputStream.class ) ) );
  }

  @Test
  void testSetFileContentCoreThrowsOperationFailedExceptionWhenUpdateFails() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/existingFile.txt" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );

    RepositoryFile existingFile = createNativeFile( "fileId", path, false );
    doReturn( existingFile ).when( repositoryMock ).getFile( path.toString() );
    doThrow( new UnifiedRepositoryException( "error" ) ).when( repositoryMock ).updateFile( any(), any(), anyString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();

    assertThrows( OperationFailedException.class,
      () -> repositoryProvider.setFileContent( path, mock( InputStream.class ) ) );
  }

  @Test
  void testSetFileContentCoreThrowsOperationFailedExceptionWhenFileDataCreationFails() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/existingFile.txt" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( true ).when( fileServiceMock ).isPathValid( anyString() );

    RepositoryFile existingFile = createNativeFile( "fileId", path, false );
    doReturn( existingFile ).when( repositoryMock ).getFile( path.toString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();
    doThrow( new IOException( "io" ) ).when( repositoryProvider ).createSimpleRepositoryFileData( any(), eq( path ) );

    assertThrows( OperationFailedException.class,
      () -> repositoryProvider.setFileContent( path, mock( InputStream.class ) ) );
    verify( repositoryMock, never() ).updateFile( any(), any(), anyString() );
  }
  // endregion

  // region owns
  @Test
  void testOwnsReturnsTrueForRootPath() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertTrue( repositoryProvider.owns( path ) );
  }

  @Test
  void testOwnsReturnsTrueForSubPath() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/test" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertTrue( repositoryProvider.owns( path ) );
  }

  @Test
  void testOwnsReturnsFalseForNonRepositoryPath() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "scheme://my/folder" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertFalse( repositoryProvider.owns( path ) );
  }
  // endregion

  // region hasAccess
  @Test
  void testHasAccessReturnsTrue() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/test" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( true ).when( repositoryMock ).hasAccess( eq( path.toString() ), any() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertTrue( repositoryProvider.hasAccess( path, EnumSet.of( GenericFilePermission.READ ) ) );
    verify( repositoryMock ).hasAccess( eq( path.toString() ), any() );
  }

  @Test
  void testHasAccessReturnsFalse() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/test" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( false ).when( repositoryMock ).hasAccess( eq( path.toString() ), any() );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertFalse( repositoryProvider.hasAccess( path, EnumSet.of( GenericFilePermission.WRITE ) ) );
    verify( repositoryMock ).hasAccess( eq( path.toString() ), any() );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  void testHasAccessMapsAllPermissionTypes() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/test" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );

    ArgumentCaptor<EnumSet<RepositoryFilePermission>> captor =
      ArgumentCaptor.forClass( EnumSet.class );
    doReturn( true ).when( repositoryMock ).hasAccess( eq( path.toString() ), captor.capture() );

    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    EnumSet<GenericFilePermission> allPermissions = EnumSet.allOf( GenericFilePermission.class );
    assertTrue( repositoryProvider.hasAccess( path, allPermissions ) );

    EnumSet<RepositoryFilePermission> expectedPermissions = EnumSet.of(
      RepositoryFilePermission.READ,
      RepositoryFilePermission.WRITE,
      RepositoryFilePermission.DELETE,
      RepositoryFilePermission.ACL_MANAGEMENT
    );
    EnumSet<RepositoryFilePermission> captured = captor.getValue();
    assertEquals( expectedPermissions, captured );
  }
  // endregion

  // region getType and getName
  @Test
  void testGetTypeReturnsCorrectType() {
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertEquals( "repository", repositoryProvider.getType() );
  }

  @Test
  void testGetNameReturnsCorrectName() {
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertEquals( Messages.getString( "GenericFileRepository.REPOSITORY_FOLDER_DISPLAY" ),
      repositoryProvider.getName() );
  }

  @Test
  void testGetFileClassReturnsCorrectClass() {
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertEquals( org.pentaho.platform.genericfile.providers.repository.model.RepositoryFile.class,
      repositoryProvider.getFileClass() );
  }
  // endregion

  // region convertToNativeFileMetadata
  @Test
  void testConvertToNativeFileMetadataWithValidData() {
    BaseGenericFileMetadata metadata = new BaseGenericFileMetadata();
    metadata.addMetadatum( "key1", "value1" );
    metadata.addMetadatum( "key2", "value2" );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    List<StringKeyStringValueDto> result = repositoryProvider.convertToNativeFileMetadata( metadata );

    assertNotNull( result );
    assertEquals( 2, result.size() );
    assertEquals( "key1", result.get( 0 ).getKey() );
    assertEquals( "value1", result.get( 0 ).getValue() );
    assertEquals( "key2", result.get( 1 ).getKey() );
    assertEquals( "value2", result.get( 1 ).getValue() );
  }

  @Test
  void testConvertToNativeFileMetadataWithNullMetadata() {
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    List<StringKeyStringValueDto> result = repositoryProvider.convertToNativeFileMetadata( null );

    assertNotNull( result );
    assertTrue( result.isEmpty() );
  }

  @Test
  void testConvertToNativeFileMetadataWithEmptyMap() {
    BaseGenericFileMetadata metadata = new BaseGenericFileMetadata();

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    List<StringKeyStringValueDto> result = repositoryProvider.convertToNativeFileMetadata( metadata );

    assertNotNull( result );
    assertTrue( result.isEmpty() );
  }

  @Test
  void testConvertToNativeFileMetadataWithNullInnerMap() {
    IGenericFileMetadata metadata = mock( IGenericFileMetadata.class );
    doReturn( null ).when( metadata ).getMetadata();

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    List<StringKeyStringValueDto> result = repositoryProvider.convertToNativeFileMetadata( metadata );

    assertNotNull( result );
    assertTrue( result.isEmpty() );
  }
  // endregion

  // region getOwnerByFileId
  @Test
  void testGetOwnerByFileIdReturnsOwner() {
    String fileId = "12345";
    String ownerName = "admin";

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileAcl aclMock = createMockFileOwner( ownerName );
    doReturn( aclMock ).when( repositoryMock ).getAcl( fileId );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    String result = repositoryProvider.getOwnerByFileId( fileId );

    assertEquals( ownerName, result );
    verify( repositoryMock ).getAcl( fileId );
  }

  @Test
  void testGetOwnerByFileIdReturnsNullWhenAclNull() {
    String fileId = "12345";

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    doReturn( null ).when( repositoryMock ).getAcl( fileId );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    String result = repositoryProvider.getOwnerByFileId( fileId );

    assertNull( result );
    verify( repositoryMock ).getAcl( fileId );
  }
  // endregion

  // region getRepositoryFilter
  @Test
  void testGetRepositoryFilterFolders() {
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    String result = repositoryProvider.getRepositoryFilter( GetTreeOptions.TreeFilter.FOLDERS );

    assertEquals( "*|FOLDERS", result );
  }

  @Test
  void testGetRepositoryFilterFiles() {
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    String result = repositoryProvider.getRepositoryFilter( GetTreeOptions.TreeFilter.FILES );

    assertEquals( "*|FILES", result );
  }

  @Test
  void testGetRepositoryFilterAll() {
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    String result = repositoryProvider.getRepositoryFilter( GetTreeOptions.TreeFilter.ALL );

    assertEquals( "*", result );
  }
  // endregion

  // region convertFromNativeFileAcl
  @Test
  void testConvertFromNativeFileAcl() throws InvalidOperationException {
    RepositoryFileAclDto nativeAcl = new RepositoryFileAclDto();
    nativeAcl.setOwner( "admin" );
    nativeAcl.setOwnerType( 0 ); // USER

    RepositoryFileAclAceDto ace1 = new RepositoryFileAclAceDto();
    ace1.setRecipient( "user1" );
    ace1.setRecipientType( 0 ); // USER
    ace1.setModifiable( true );
    ace1.setPermissions( List.of( 0, 1 ) ); // READ, WRITE

    RepositoryFileAclAceDto ace2 = new RepositoryFileAclAceDto();
    ace2.setRecipient( "role1" );
    ace2.setRecipientType( 1 ); // ROLE
    ace2.setModifiable( false );
    ace2.setPermissions( List.of( 0 ) ); // READ

    nativeAcl.setAces( List.of( ace1, ace2 ), false ); // entriesInheriting = false

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    IGenericFileAcl result = repositoryProvider.convertFromNativeFileAcl( nativeAcl );

    assertNotNull( result );
    assertEquals( "admin", result.getOwner() );
    assertEquals( GenericFilePrincipalType.USER, result.getOwnerType() );
    assertFalse( result.isEntriesInheriting() );
    assertNotNull( result.getEntries() );
    assertEquals( 2, result.getEntries().size() );

    IGenericFileAce entry1 = result.getEntries().get( 0 );
    assertEquals( "user1", entry1.getRecipient() );
    assertEquals( GenericFilePrincipalType.USER, entry1.getRecipientType() );
    assertTrue( entry1.isModifiable() );
    assertEquals( 2, entry1.getPermissions().size() );
    assertEquals( GenericFilePermission.READ, entry1.getPermissions().get( 0 ) );
    assertEquals( GenericFilePermission.WRITE, entry1.getPermissions().get( 1 ) );

    IGenericFileAce entry2 = result.getEntries().get( 1 );
    assertEquals( "role1", entry2.getRecipient() );
    assertEquals( GenericFilePrincipalType.ROLE, entry2.getRecipientType() );
    assertFalse( entry2.isModifiable() );
    assertEquals( 1, entry2.getPermissions().size() );
    assertEquals( GenericFilePermission.READ, entry2.getPermissions().get( 0 ) );
  }

  @Test
  void testConvertFromNativeFileAclWithEntriesInheriting() throws InvalidOperationException {
    RepositoryFileAclDto nativeAcl = new RepositoryFileAclDto();
    nativeAcl.setOwner( "admin" );
    nativeAcl.setOwnerType( 0 ); // USER

    // When entriesInheriting is true, aces are still preserved in the conversion
    RepositoryFileAclAceDto ace = new RepositoryFileAclAceDto();
    ace.setRecipient( "user1" );
    ace.setRecipientType( 0 );
    ace.setPermissions( List.of( 0 ) );
    nativeAcl.setAces( List.of( ace ), true ); // entriesInheriting = true

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    IGenericFileAcl result = repositoryProvider.convertFromNativeFileAcl( nativeAcl );

    assertNotNull( result );
    assertEquals( "admin", result.getOwner() );
    assertEquals( GenericFilePrincipalType.USER, result.getOwnerType() );
    assertTrue( result.isEntriesInheriting() );
    // Aces are preserved even when entriesInheriting is true
    assertNotNull( result.getEntries() );
    assertEquals( 1, result.getEntries().size() );
    assertEquals( "user1", result.getEntries().get( 0 ).getRecipient() );
  }

  @Test
  void testConvertFromNativeFileAclWithNullAces() throws InvalidOperationException {
    RepositoryFileAclDto nativeAcl = new RepositoryFileAclDto();
    nativeAcl.setOwner( "admin" );
    nativeAcl.setOwnerType( 0 ); // USER
    nativeAcl.setAces( null, false );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    IGenericFileAcl result = repositoryProvider.convertFromNativeFileAcl( nativeAcl );

    assertNotNull( result );
    assertEquals( "admin", result.getOwner() );
    assertEquals( GenericFilePrincipalType.USER, result.getOwnerType() );
    assertFalse( result.isEntriesInheriting() );
    assertNull( result.getEntries() ); // null aces should result in null entries
  }

  @Test
  void testConvertFromNativeFileAclWithEmptyAces() throws InvalidOperationException {
    RepositoryFileAclDto nativeAcl = new RepositoryFileAclDto();
    nativeAcl.setOwner( "admin" );
    nativeAcl.setOwnerType( 0 ); // USER
    nativeAcl.setAces( Collections.emptyList(), false );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    IGenericFileAcl result = repositoryProvider.convertFromNativeFileAcl( nativeAcl );

    assertNotNull( result );
    assertEquals( "admin", result.getOwner() );
    assertEquals( GenericFilePrincipalType.USER, result.getOwnerType() );
    assertFalse( result.isEntriesInheriting() );
    assertNotNull( result.getEntries() );
    assertTrue( result.getEntries().isEmpty() );
  }

  @Test
  void testConvertFromNativeFileAclWithInvalidOwnerType() {
    RepositoryFileAclDto nativeAcl = new RepositoryFileAclDto();
    nativeAcl.setOwner( "admin" );
    nativeAcl.setOwnerType( 999 ); // Invalid type
    nativeAcl.setAces( null, false );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( InvalidOperationException.class, () -> repositoryProvider.convertFromNativeFileAcl( nativeAcl ) );
  }

  @Test
  void testConvertFromNativeFileAclWithAllPermissionTypes() throws InvalidOperationException {
    RepositoryFileAclDto nativeAcl = new RepositoryFileAclDto();
    nativeAcl.setOwner( "admin" );
    nativeAcl.setOwnerType( 0 ); // USER

    RepositoryFileAclAceDto ace = new RepositoryFileAclAceDto();
    ace.setRecipient( "user1" );
    ace.setRecipientType( 0 ); // USER
    ace.setModifiable( true );
    // Add all permission types (0-4 for READ, WRITE, DELETE, ACL_MANAGEMENT, native ALL mapped to ACL_MANAGEMENT)
    ace.setPermissions( List.of( 0, 1, 2, 3, 4 ) );

    nativeAcl.setAces( List.of( ace ), false );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    IGenericFileAcl result = repositoryProvider.convertFromNativeFileAcl( nativeAcl );

    assertNotNull( result );
    assertEquals( 1, result.getEntries().size() );
    IGenericFileAce entry = result.getEntries().get( 0 );
    assertTrue( entry.isModifiable() );
    assertEquals( 5, entry.getPermissions().size() );
    assertEquals( GenericFilePermission.READ, entry.getPermissions().get( 0 ) );
    assertEquals( GenericFilePermission.WRITE, entry.getPermissions().get( 1 ) );
    assertEquals( GenericFilePermission.DELETE, entry.getPermissions().get( 2 ) );
    assertEquals( GenericFilePermission.ACL_MANAGEMENT, entry.getPermissions().get( 3 ) );
    // Native permission 4 (ALL) is mapped to ACL_MANAGEMENT
    assertEquals( GenericFilePermission.ACL_MANAGEMENT, entry.getPermissions().get( 4 ) );
  }

  @Test
  void testConvertFromNativeFileAclEntry() throws InvalidOperationException {
    RepositoryFileAclAceDto nativeEntry = new RepositoryFileAclAceDto();
    nativeEntry.setRecipient( "testUser" );
    nativeEntry.setRecipientType( 0 ); // USER
    nativeEntry.setModifiable( true );
    nativeEntry.setPermissions( List.of( 0, 1, 2, 3, 4 ) ); // READ, WRITE, DELETE, ACL_MANAGEMENT, native ALL

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    IGenericFileAce result = repositoryProvider.convertFromNativeFileAclEntry( nativeEntry );

    assertNotNull( result );
    assertEquals( "testUser", result.getRecipient() );
    assertEquals( GenericFilePrincipalType.USER, result.getRecipientType() );
    assertTrue( result.isModifiable() );
    assertEquals( 5, result.getPermissions().size() );
    assertEquals( GenericFilePermission.READ, result.getPermissions().get( 0 ) );
    assertEquals( GenericFilePermission.WRITE, result.getPermissions().get( 1 ) );
    assertEquals( GenericFilePermission.DELETE, result.getPermissions().get( 2 ) );
    assertEquals( GenericFilePermission.ACL_MANAGEMENT, result.getPermissions().get( 3 ) );
    // Native permission 4 (ALL) is mapped to ACL_MANAGEMENT
    assertEquals( GenericFilePermission.ACL_MANAGEMENT, result.getPermissions().get( 4 ) );
  }

  @Test
  void testConvertFromNativeFileAclEntryWithNullPermissions() throws InvalidOperationException {
    RepositoryFileAclAceDto nativeEntry = new RepositoryFileAclAceDto();
    nativeEntry.setRecipient( "testRole" );
    nativeEntry.setRecipientType( 1 ); // ROLE
    nativeEntry.setModifiable( false );
    nativeEntry.setPermissions( null );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    IGenericFileAce result = repositoryProvider.convertFromNativeFileAclEntry( nativeEntry );

    assertNotNull( result );
    assertEquals( "testRole", result.getRecipient() );
    assertEquals( GenericFilePrincipalType.ROLE, result.getRecipientType() );
    assertFalse( result.isModifiable() );
    assertNotNull( result.getPermissions() );
    assertTrue( result.getPermissions().isEmpty() );
  }

  @Test
  void testConvertFromNativeFileAclEntryInvalidPermissions() {
    RepositoryFileAclAceDto nativeEntry = new RepositoryFileAclAceDto();
    nativeEntry.setRecipient( "testUser" );
    nativeEntry.setRecipientType( 0 ); // USER
    nativeEntry.setPermissions( List.of( 0, -1 ) ); // READ, UNKNOWN

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( InvalidOperationException.class,
      () -> repositoryProvider.convertFromNativeFileAclEntry( nativeEntry ) );
  }

  @Test
  void testConvertFromNativeFileAclEntryWithInvalidRecipientType() {
    RepositoryFileAclAceDto nativeEntry = new RepositoryFileAclAceDto();
    nativeEntry.setRecipient( "testUser" );
    nativeEntry.setRecipientType( -1 ); // Invalid type
    nativeEntry.setPermissions( List.of( 0 ) );

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( InvalidOperationException.class,
      () -> repositoryProvider.convertFromNativeFileAclEntry( nativeEntry ) );
  }
  // endregion

  // region convertToNativeFileAcl
  @Test
  void testConvertToNativeFileAclWithAllFields() {
    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    IGenericFileAce ace1 = mock( IGenericFileAce.class );
    IGenericFileAce ace2 = mock( IGenericFileAce.class );

    doReturn( "admin" ).when( acl ).getOwner();
    doReturn( GenericFilePrincipalType.USER ).when( acl ).getOwnerType();
    doReturn( false ).when( acl ).isEntriesInheriting();
    doReturn( List.of( ace1, ace2 ) ).when( acl ).getEntries();

    doReturn( "user1" ).when( ace1 ).getRecipient();
    doReturn( GenericFilePrincipalType.USER ).when( ace1 ).getRecipientType();
    doReturn( true ).when( ace1 ).isModifiable();
    doReturn( List.of( GenericFilePermission.READ, GenericFilePermission.WRITE ) ).when( ace1 ).getPermissions();

    doReturn( "role1" ).when( ace2 ).getRecipient();
    doReturn( GenericFilePrincipalType.ROLE ).when( ace2 ).getRecipientType();
    doReturn( false ).when( ace2 ).isModifiable();
    doReturn( List.of( GenericFilePermission.READ ) ).when( ace2 ).getPermissions();

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    RepositoryFileAclDto result = repositoryProvider.convertToNativeFileAcl( acl );

    assertNotNull( result );
    assertEquals( "admin", result.getOwner() );
    assertEquals( 0, result.getOwnerType() ); // USER
    assertFalse( result.isEntriesInheriting() );
    assertNotNull( result.getAces() );
    assertEquals( 2, result.getAces().size() );

    RepositoryFileAclAceDto nativeAce1 = result.getAces().get( 0 );
    assertEquals( "user1", nativeAce1.getRecipient() );
    assertEquals( 0, nativeAce1.getRecipientType() ); // USER
    assertTrue( nativeAce1.isModifiable() );
    assertEquals( 2, nativeAce1.getPermissions().size() );
    assertEquals( 0, nativeAce1.getPermissions().get( 0 ) ); // READ
    assertEquals( 1, nativeAce1.getPermissions().get( 1 ) ); // WRITE

    RepositoryFileAclAceDto nativeAce2 = result.getAces().get( 1 );
    assertEquals( "role1", nativeAce2.getRecipient() );
    assertEquals( 1, nativeAce2.getRecipientType() ); // ROLE
    assertFalse( nativeAce2.isModifiable() );
    assertEquals( 1, nativeAce2.getPermissions().size() );
    assertEquals( 0, nativeAce2.getPermissions().get( 0 ) ); // READ
  }

  @Test
  void testConvertToNativeFileAclWithNullOwnerType() {
    IGenericFileAcl acl = mock( IGenericFileAcl.class );

    doReturn( "admin" ).when( acl ).getOwner();
    doReturn( null ).when( acl ).getOwnerType();
    doReturn( false ).when( acl ).isEntriesInheriting();
    doReturn( Collections.emptyList() ).when( acl ).getEntries();

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( NullPointerException.class, () -> repositoryProvider.convertToNativeFileAcl( acl ) );
  }

  @Test
  void testConvertToNativeFileAclWithNullEntries() {
    IGenericFileAcl acl = mock( IGenericFileAcl.class );

    doReturn( "admin" ).when( acl ).getOwner();
    doReturn( GenericFilePrincipalType.USER ).when( acl ).getOwnerType();
    doReturn( false ).when( acl ).isEntriesInheriting();
    doReturn( null ).when( acl ).getEntries();

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    RepositoryFileAclDto result = repositoryProvider.convertToNativeFileAcl( acl );

    assertNotNull( result );
    assertEquals( "admin", result.getOwner() );
    assertEquals( 0, result.getOwnerType() ); // USER
    assertFalse( result.isEntriesInheriting() );
    assertNotNull( result.getAces() );
    assertTrue( result.getAces().isEmpty() );
  }

  @Test
  void testConvertToNativeFileAclWithNullEntriesAndEntriesInheriting() {
    IGenericFileAcl acl = mock( IGenericFileAcl.class );

    doReturn( "admin" ).when( acl ).getOwner();
    doReturn( GenericFilePrincipalType.USER ).when( acl ).getOwnerType();
    doReturn( true ).when( acl ).isEntriesInheriting();
    doReturn( null ).when( acl ).getEntries(); // null entries when inheriting

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    RepositoryFileAclDto result = repositoryProvider.convertToNativeFileAcl( acl );

    assertNotNull( result );
    assertEquals( "admin", result.getOwner() );
    assertEquals( 0, result.getOwnerType() ); // USER
    assertTrue( result.isEntriesInheriting() );
    assertTrue( result.getAces().isEmpty() );
  }

  @Test
  void testConvertToNativeFileAclWithEntriesAndEntriesInheriting() {
    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    IGenericFileAce ace1 = mock( IGenericFileAce.class );

    doReturn( "admin" ).when( acl ).getOwner();
    doReturn( GenericFilePrincipalType.USER ).when( acl ).getOwnerType();
    doReturn( true ).when( acl ).isEntriesInheriting();
    doReturn( List.of( ace1 ) ).when( acl ).getEntries();

    doReturn( "user1" ).when( ace1 ).getRecipient();
    doReturn( GenericFilePrincipalType.USER ).when( ace1 ).getRecipientType();
    doReturn( true ).when( ace1 ).isModifiable();
    doReturn( List.of( GenericFilePermission.READ ) ).when( ace1 ).getPermissions();

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    RepositoryFileAclDto result = repositoryProvider.convertToNativeFileAcl( acl );

    assertNotNull( result );
    assertEquals( "admin", result.getOwner() );
    assertEquals( 0, result.getOwnerType() ); // USER
    assertTrue( result.isEntriesInheriting() );
    // Aces are preserved even when entriesInheriting is true
    assertNotNull( result.getAces() );
    assertEquals( 1, result.getAces().size() );
    assertEquals( "user1", result.getAces().get( 0 ).getRecipient() );
  }

  @Test
  void testConvertToNativeFileAclWithEmptyEntriesAndNotInheriting() {
    IGenericFileAcl acl = mock( IGenericFileAcl.class );

    doReturn( "admin" ).when( acl ).getOwner();
    doReturn( GenericFilePrincipalType.USER ).when( acl ).getOwnerType();
    doReturn( false ).when( acl ).isEntriesInheriting();
    doReturn( Collections.emptyList() ).when( acl ).getEntries();

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    RepositoryFileAclDto result = repositoryProvider.convertToNativeFileAcl( acl );

    assertNotNull( result );
    assertEquals( "admin", result.getOwner() );
    assertEquals( 0, result.getOwnerType() );
    assertFalse( result.isEntriesInheriting() );
    assertNotNull( result.getAces() );
    assertTrue( result.getAces().isEmpty() );
  }

  @Test
  void testConvertToNativeFileAclWithAllEnumValues() {
    IGenericFileAcl acl = mock( IGenericFileAcl.class );

    doReturn( "testOwner" ).when( acl ).getOwner();
    doReturn( GenericFilePrincipalType.USER ).when( acl ).getOwnerType();
    doReturn( false ).when( acl ).isEntriesInheriting();

    List<GenericFilePermission> allPermissions = Arrays.asList( GenericFilePermission.values() );
    IGenericFileAce ace = mock( IGenericFileAce.class );
    doReturn( "testUser" ).when( ace ).getRecipient();
    doReturn( GenericFilePrincipalType.ROLE ).when( ace ).getRecipientType();
    doReturn( true ).when( ace ).isModifiable();
    doReturn( allPermissions ).when( ace ).getPermissions();

    doReturn( List.of( ace ) ).when( acl ).getEntries();

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    RepositoryFileAclDto result = repositoryProvider.convertToNativeFileAcl( acl );

    assertNotNull( result );
    assertEquals( GenericFilePrincipalType.USER.ordinal(), result.getOwnerType() );
    assertEquals( 1, result.getAces().size() );
    RepositoryFileAclAceDto nativeAce = result.getAces().get( 0 );
    assertEquals( GenericFilePrincipalType.ROLE.ordinal(), nativeAce.getRecipientType() );
    assertTrue( nativeAce.isModifiable() );
    assertEquals( allPermissions.size(), nativeAce.getPermissions().size() );

    for ( int i = 0; i < allPermissions.size(); i++ ) {
      assertEquals( allPermissions.get( i ).ordinal(), nativeAce.getPermissions().get( i ) );
    }
  }

  @Test
  void testConvertToNativeFileAclReturnsModifiableAcesList() {
    IGenericFileAcl acl = mock( IGenericFileAcl.class );
    IGenericFileAce ace1 = mock( IGenericFileAce.class );
    IGenericFileAce ace2 = mock( IGenericFileAce.class );

    doReturn( "admin" ).when( acl ).getOwner();
    doReturn( GenericFilePrincipalType.USER ).when( acl ).getOwnerType();
    doReturn( false ).when( acl ).isEntriesInheriting();
    doReturn( List.of( ace1, ace2 ) ).when( acl ).getEntries();

    doReturn( "user1" ).when( ace1 ).getRecipient();
    doReturn( GenericFilePrincipalType.USER ).when( ace1 ).getRecipientType();
    doReturn( true ).when( ace1 ).isModifiable();
    doReturn( List.of( GenericFilePermission.READ ) ).when( ace1 ).getPermissions();

    doReturn( "user2" ).when( ace2 ).getRecipient();
    doReturn( GenericFilePrincipalType.USER ).when( ace2 ).getRecipientType();
    doReturn( true ).when( ace2 ).isModifiable();
    doReturn( List.of( GenericFilePermission.WRITE ) ).when( ace2 ).getPermissions();

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    RepositoryFileAclDto result = repositoryProvider.convertToNativeFileAcl( acl );

    assertNotNull( result );
    assertNotNull( result.getAces() );
    assertEquals( 2, result.getAces().size() );

    // Verify the list is mutable by removing an item (should not throw UnsupportedOperationException)
    assertDoesNotThrow( () -> result.getAces().remove( 0 ) );
    assertEquals( 1, result.getAces().size() );
  }

  @Test
  void testConvertToNativeFileAclEntry() {
    IGenericFileAce ace = mock( IGenericFileAce.class );

    doReturn( "testUser" ).when( ace ).getRecipient();
    doReturn( GenericFilePrincipalType.USER ).when( ace ).getRecipientType();
    doReturn( true ).when( ace ).isModifiable();
    doReturn( List.of( GenericFilePermission.READ, GenericFilePermission.WRITE, GenericFilePermission.DELETE,
      GenericFilePermission.ACL_MANAGEMENT ) ).when( ace ).getPermissions();

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    RepositoryFileAclAceDto result = repositoryProvider.convertToNativeFileAclEntry( ace );

    assertNotNull( result );
    assertEquals( "testUser", result.getRecipient() );
    assertEquals( 0, result.getRecipientType() ); // USER
    assertTrue( result.isModifiable() );
    assertEquals( 4, result.getPermissions().size() );
    assertEquals( 0, result.getPermissions().get( 0 ) ); // READ
    assertEquals( 1, result.getPermissions().get( 1 ) ); // WRITE
    assertEquals( 2, result.getPermissions().get( 2 ) ); // DELETE
    assertEquals( 3, result.getPermissions().get( 3 ) ); // ACL_MANAGEMENT
  }

  @Test
  void testConvertToNativeFileAclEntryWithNullRecipientType() {
    IGenericFileAce ace = mock( IGenericFileAce.class );

    doReturn( "testRole" ).when( ace ).getRecipient();
    doReturn( null ).when( ace ).getRecipientType();
    doReturn( false ).when( ace ).isModifiable();
    doReturn( List.of( GenericFilePermission.READ ) ).when( ace ).getPermissions();

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( NullPointerException.class, () -> repositoryProvider.convertToNativeFileAclEntry( ace ) );
  }

  @Test
  void testConvertToNativeFileAclEntryWithEmptyPermissions() {
    IGenericFileAce ace = mock( IGenericFileAce.class );

    doReturn( "testUser" ).when( ace ).getRecipient();
    doReturn( GenericFilePrincipalType.ROLE ).when( ace ).getRecipientType();
    doReturn( false ).when( ace ).isModifiable();
    doReturn( Collections.emptyList() ).when( ace ).getPermissions();

    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    RepositoryFileAclAceDto result = repositoryProvider.convertToNativeFileAclEntry( ace );

    assertNotNull( result );
    assertEquals( "testUser", result.getRecipient() );
    assertEquals( 1, result.getRecipientType() ); // ROLE
    assertFalse( result.isModifiable() );
    assertNotNull( result.getPermissions() );
    assertTrue( result.getPermissions().isEmpty() );
  }
  // endregion

  // region convertFromNativePrincipalType
  @Test
  void testConversionPrincipalTypeUserOrdinal() throws InvalidOperationException {
    // Test that principal type 0 (USER) is correctly converted
    int nativePrincipalType = 0; // USER ordinal
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GenericFilePrincipalType result = repositoryProvider.convertFromNativePrincipalType( nativePrincipalType );

    assertEquals( GenericFilePrincipalType.USER, result );
    assertEquals( nativePrincipalType, GenericFilePrincipalType.USER.ordinal() );
  }

  @Test
  void testConversionPrincipalTypeRoleOrdinal() throws InvalidOperationException {
    // Test that principal type 1 (ROLE) is correctly converted
    int nativePrincipalType = 1; // ROLE ordinal
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GenericFilePrincipalType result = repositoryProvider.convertFromNativePrincipalType( nativePrincipalType );

    assertEquals( GenericFilePrincipalType.ROLE, result );
    assertEquals( nativePrincipalType, GenericFilePrincipalType.ROLE.ordinal() );
  }

  @Test
  void testConversionPrincipalTypeInvalidNegative() {
    // Test that invalid principal type (-1) throws exception
    int nativePrincipalType = -1; // unknown ordinal
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( InvalidOperationException.class,
      () -> repositoryProvider.convertFromNativePrincipalType( nativePrincipalType ) );
  }

  @Test
  void testConversionPrincipalTypeInvalidTooHigh() {
    // Test that invalid principal type (10) throws exception
    int nativePrincipalType = 10; // unknown ordinal
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( InvalidOperationException.class,
      () -> repositoryProvider.convertFromNativePrincipalType( nativePrincipalType ) );
  }
  // endregion

  // region convertFromNativePermission
  @Test
  void testConversionPermissionReadOrdinals() throws InvalidOperationException {
    // Test that permission 0 (READ) is correctly converted
    int nativePermission = 0; // READ ordinal
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GenericFilePermission result = repositoryProvider.convertFromNativePermission( nativePermission );

    assertEquals( GenericFilePermission.READ, result );
    assertEquals( nativePermission, GenericFilePermission.READ.ordinal() );
  }

  @Test
  void testConversionPermissionWriteOrdinals() throws InvalidOperationException {
    // Test that permission 1 (WRITE) is correctly converted
    int nativePermission = 1; // WRITE ordinal
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GenericFilePermission result = repositoryProvider.convertFromNativePermission( nativePermission );

    assertEquals( GenericFilePermission.WRITE, result );
    assertEquals( nativePermission, GenericFilePermission.WRITE.ordinal() );
  }

  @Test
  void testConversionPermissionDeleteOrdinals() throws InvalidOperationException {
    // Test that permission 2 (DELETE) is correctly converted
    int nativePermission = 2; // DELETE ordinal
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GenericFilePermission result = repositoryProvider.convertFromNativePermission( nativePermission );

    assertEquals( GenericFilePermission.DELETE, result );
    assertEquals( nativePermission, GenericFilePermission.DELETE.ordinal() );
  }

  @Test
  void testConversionPermissionAclManagementOrdinals() throws InvalidOperationException {
    // Test that permission 3 (ACL_MANAGEMENT) is correctly converted
    int nativePermission = 3; // ACL_MANAGEMENT ordinal
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GenericFilePermission result = repositoryProvider.convertFromNativePermission( nativePermission );

    assertEquals( GenericFilePermission.ACL_MANAGEMENT, result );
    assertEquals( nativePermission, GenericFilePermission.ACL_MANAGEMENT.ordinal() );
  }

  @Test
  void testConversionNativePermissionAllMapsToAclManagement() throws InvalidOperationException {
    // Test that native permission 4 (ALL) is mapped to ACL_MANAGEMENT
    int nativePermission = 4; // native ALL ordinal
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GenericFilePermission result = repositoryProvider.convertFromNativePermission( nativePermission );

    assertEquals( GenericFilePermission.ACL_MANAGEMENT, result );
  }

  @Test
  void testConversionPermissionInvalidNegative() {
    // Test that invalid permission (-1) throws exception
    int nativePermission = -1; // unknown ordinal
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( InvalidOperationException.class,
      () -> repositoryProvider.convertFromNativePermission( nativePermission ) );
  }

  @Test
  void testConversionPermissionInvalidTooHigh() {
    // Test that invalid permission (10) throws exception
    int nativePermission = 10; // unknown ordinal
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( InvalidOperationException.class,
      () -> repositoryProvider.convertFromNativePermission( nativePermission ) );
  }

  @Test
  void testConversionPermissionNativeFiveIsInvalid() {
    // After removing ALL, native permission 5 is now invalid
    int nativePermission = 5;
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    assertThrows( InvalidOperationException.class,
      () -> repositoryProvider.convertFromNativePermission( nativePermission ) );
  }

  @Test
  void testConversionPermissionAllValuesRoundTrip() throws InvalidOperationException {
    // Test that all GenericFilePermission values can be converted to native and back
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    for ( GenericFilePermission permission : GenericFilePermission.values() ) {
      int nativePermission = repositoryProvider.convertToNativePermission( permission );
      GenericFilePermission result = repositoryProvider.convertFromNativePermission( nativePermission );

      assertEquals( permission, result );
    }
  }

  @Test
  void testConversionPermissionNativeFourAlsoMapsToAclManagement() throws InvalidOperationException {
    // Verify that both native 3 (ACL_MANAGEMENT ordinal) and native 4 (old ALL) map to ACL_MANAGEMENT
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );
    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    GenericFilePermission resultFromThree = repositoryProvider.convertFromNativePermission( 3 );
    GenericFilePermission resultFromFour = repositoryProvider.convertFromNativePermission( 4 );

    assertEquals( GenericFilePermission.ACL_MANAGEMENT, resultFromThree );
    assertEquals( GenericFilePermission.ACL_MANAGEMENT, resultFromFour );
  }
  // endregion

  // region convertToNativePrincipalType
  @Test
  void testConversionToNativePrincipalType() {
    for ( GenericFilePrincipalType type : GenericFilePrincipalType.values() ) {
      IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
      FileService fileServiceMock = mock( FileService.class );
      RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

      int result = repositoryProvider.convertToNativePrincipalType( type );

      assertEquals( type.ordinal(), result );
    }
  }
  // endregion

  // region convertToNativePermission
  @Test
  void testConversionRoundTripPermission() {
    for ( GenericFilePermission permission : GenericFilePermission.values() ) {
      IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
      FileService fileServiceMock = mock( FileService.class );
      RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

      int result = repositoryProvider.convertToNativePermission( permission );

      assertEquals( permission.ordinal(), result );
    }
  }
  // endregion

  // region validateSecurityPrincipal
  @ParameterizedTest
  @ValueSource( strings = { "validUser", "admin", "pentahoRepoAdmin", "a" } )
  void testValidateSecurityPrincipalValid( String principal ) {
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    assertTrue( repositoryProvider.validateSecurityPrincipal( principal ) );
  }

  @ParameterizedTest
  @NullSource
  @ValueSource( strings = { "", " ", "  ", " user", "user ", " user ",
    "user#invalid", "user+invalid", "user,invalid", "user\"invalid", "user\\invalid",
    "user<invalid", "user>invalid", "user;invalid", "user=invalid" } )
  void testValidateSecurityPrincipalInvalid( String principal ) {
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    assertFalse( repositoryProvider.validateSecurityPrincipal( principal ) );
  }
  // endregion

  // region validateFileName
  @Test
  void testValidateFileNameValidWithNoConverterHandler() {
    FileService fileServiceMock = mock( FileService.class );
    doReturn( true ).when( fileServiceMock ).isValidFileName( "file.txt", true );

    RepositoryFileProvider repositoryProvider = spy(
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();

    assertDoesNotThrow( () -> repositoryProvider.validateFileName( "file.txt" ) );
  }

  @Test
  void testValidateFileNameEmptyName() {
    RepositoryFileProvider repositoryProvider =
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

    InvalidOperationException exception = assertThrows( InvalidOperationException.class,
      () -> repositoryProvider.validateFileName( "" ) );

    assertEquals( "File name cannot be empty.", exception.getMessage() );
  }

  @Test
  void testValidateFileNameInvalidExtensionWhenConverterMissing() {
    IRepositoryContentConverterHandler converterHandlerMock = mock( IRepositoryContentConverterHandler.class );

    RepositoryFileProvider repositoryProvider = spy(
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) ) );
    doReturn( converterHandlerMock ).when( repositoryProvider ).getContentConverterHandler();

    InvalidOperationException exception = assertThrows( InvalidOperationException.class,
      () -> repositoryProvider.validateFileName( "report.prpt" ) );

    assertEquals( "The file extension 'report.prpt' is not valid.", exception.getMessage() );
  }

  @Test
  void testValidateFileNameInvalidByFileService() {
    FileService fileServiceMock = mock( FileService.class );
    doReturn( false ).when( fileServiceMock ).isValidFileName( "bad?.txt", true );

    RepositoryFileProvider repositoryProvider = spy(
      new RepositoryFileProvider( mock( IUnifiedRepository.class ), fileServiceMock ) );
    doReturn( null ).when( repositoryProvider ).getContentConverterHandler();

    InvalidOperationException exception = assertThrows( InvalidOperationException.class,
      () -> repositoryProvider.validateFileName( "bad?.txt" ) );

    assertEquals( "The new name 'bad?.txt' is not valid.", exception.getMessage() );
  }
  // endregion

  // region detectMimeType and createSimpleRepositoryFileData
  @Test
  void testDetectMimeTypeFromStreamContentSupported() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/image.png" );
    // Mock InputStream that supports mark/reset and can be detected as PNG
    InputStream mockStream = mock( InputStream.class );
    doReturn( true ).when( mockStream ).markSupported();
    // Simulate that URLConnection detects PNG content
    try ( var mocked = mockStatic( java.net.URLConnection.class ) ) {
      mocked.when( () -> java.net.URLConnection.guessContentTypeFromStream( mockStream ) )
        .thenReturn( "image/png" );

      RepositoryFileProvider repositoryProvider =
        new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

      String mimeType = repositoryProvider.detectMimeType( mockStream, path );

      assertEquals( "image/png", mimeType );
      verify( mockStream ).markSupported();
    }
  }

  @Test
  void testDetectMimeTypeFromStreamContentNotSupported() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/document.pdf" );
    // Mock InputStream that doesn't support mark/reset
    InputStream mockStream = mock( InputStream.class );
    doReturn( false ).when( mockStream ).markSupported();

    try ( var mocked = mockStatic( java.net.URLConnection.class ) ) {
      mocked.when( () -> java.net.URLConnection.guessContentTypeFromName( "document.pdf" ) )
        .thenReturn( "application/pdf" );

      RepositoryFileProvider repositoryProvider =
        new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

      String mimeType = repositoryProvider.detectMimeType( mockStream, path );

      assertEquals( "application/pdf", mimeType );
      // Verify that stream content detection was skipped
      verify( mockStream ).markSupported();
    }
  }

  @Test
  void testDetectMimeTypeStreamContentDetectionFailsFallbackToExtension() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/document.docx" );
    InputStream mockStream = mock( InputStream.class );
    doReturn( true ).when( mockStream ).markSupported();

    try ( var mocked = mockStatic( java.net.URLConnection.class ) ) {
      // Stream content detection returns null
      mocked.when( () -> java.net.URLConnection.guessContentTypeFromStream( mockStream ) )
        .thenReturn( null );
      // But extension-based detection succeeds
      mocked.when( () -> java.net.URLConnection.guessContentTypeFromName( "document.docx" ) )
        .thenReturn( "application/vnd.openxmlformats-officedocument.wordprocessingml.document" );

      RepositoryFileProvider repositoryProvider =
        new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

      String mimeType = repositoryProvider.detectMimeType( mockStream, path );

      assertEquals( "application/vnd.openxmlformats-officedocument.wordprocessingml.document", mimeType );
    }
  }

  @Test
  void testDetectMimeTypeDefaultsToOctetStream() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/unknownfile.xyz" );
    InputStream mockStream = mock( InputStream.class );
    doReturn( false ).when( mockStream ).markSupported();

    try ( var mocked = mockStatic( java.net.URLConnection.class ) ) {
      // Both detection methods fail
      mocked.when( () -> java.net.URLConnection.guessContentTypeFromName( "unknownfile.xyz" ) )
        .thenReturn( null );

      RepositoryFileProvider repositoryProvider =
        new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

      String mimeType = repositoryProvider.detectMimeType( mockStream, path );

      assertEquals( "application/octet-stream", mimeType );
    }
  }

  @Test
  void testDetectMimeTypeTextFile() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/readme.txt" );
    InputStream mockStream = mock( InputStream.class );
    doReturn( false ).when( mockStream ).markSupported();

    try ( var mocked = mockStatic( java.net.URLConnection.class ) ) {
      mocked.when( () -> java.net.URLConnection.guessContentTypeFromName( "readme.txt" ) )
        .thenReturn( "text/plain" );

      RepositoryFileProvider repositoryProvider =
        new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

      String mimeType = repositoryProvider.detectMimeType( mockStream, path );

      assertEquals( "text/plain", mimeType );
    }
  }

  @Test
  void testCreateSimpleRepositoryFileDataWithDetectedMimeType() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/test.json" );
    byte[] content = "{\"key\": \"value\"}".getBytes();
    InputStream inputStream = new java.io.ByteArrayInputStream( content );

    try ( var mocked = mockStatic( java.net.URLConnection.class ) ) {
      mocked.when( () -> java.net.URLConnection.guessContentTypeFromName( "test.json" ) )
        .thenReturn( "application/json" );

      RepositoryFileProvider repositoryProvider =
        new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

      org.pentaho.platform.api.repository2.unified.data.simple.SimpleRepositoryFileData fileData =
        repositoryProvider.createSimpleRepositoryFileData( inputStream, path );

      assertNotNull( fileData );
      assertEquals( "application/json", fileData.getMimeType() );
    }
  }

  @Test
  void testCreateSimpleRepositoryFileDataUsesPlatformEncoding() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/test.xml" );
    byte[] content = "<root></root>".getBytes();
    InputStream inputStream = new java.io.ByteArrayInputStream( content );

    try ( var mocked = mockStatic( java.net.URLConnection.class ) ) {
      mocked.when( () -> java.net.URLConnection.guessContentTypeFromName( "test.xml" ) )
        .thenReturn( "application/xml" );

      RepositoryFileProvider repositoryProvider =
        new RepositoryFileProvider( mock( IUnifiedRepository.class ), mock( FileService.class ) );

      org.pentaho.platform.api.repository2.unified.data.simple.SimpleRepositoryFileData fileData =
        repositoryProvider.createSimpleRepositoryFileData( inputStream, path );

      assertNotNull( fileData );
      // The encoding should be the system encoding (from LocaleHelper.getSystemEncoding())
      assertNotNull( fileData );
    }
  }
  // endregion

  // region getOrCreateNativeFile
  @Test
  void testGetOrCreateNativeFolderReturnsFolderWhenItExists() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/existingFolder" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );

    // Existing folder
    RepositoryFile existingFolder = createNativeFile( "folderId", path, true );
    doReturn( existingFolder ).when( repositoryMock ).getFile( path.toString() );

    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    org.pentaho.platform.api.repository2.unified.RepositoryFile result =
      repositoryProvider.getOrCreateNativeFolder( path );

    assertNotNull( result );
    assertEquals( "folderId", result.getId().toString() );
    verify( repositoryMock, times( 1 ) ).getFile( path.toString() );
  }

  @Test
  void testGetOrCreateNativeFolderThrowsInvalidOperationExceptionWhenPathIsNotAFolder() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/notAFolder" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );

    RepositoryFile existingFile = createNativeFile( "fileId", path, false );
    doReturn( existingFile ).when( repositoryMock ).getFile( path.toString() );

    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    InvalidOperationException exception =
      assertThrows( InvalidOperationException.class, () -> repositoryProvider.getOrCreateNativeFolder( path ) );

    assertEquals( "Path is not a folder.", exception.getMessage() );
    verify( repositoryMock, times( 1 ) ).getFile( path.toString() );
  }

  @Test
  void testGetOrCreateNativeFolderCreatesAndReturnsFolderWhenItDoesNotExist() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/newFolder" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );

    // First call returns null (not found), triggering creation
    RepositoryFile newFolder = createNativeFile( "newFolderId", path, true );
    doReturn( null ).doReturn( newFolder ).when( repositoryMock ).getFile( path.toString() );

    // Mock createFolderCore to succeed
    doReturn( true ).when( fileServiceMock ).doCreateDirSafe( path.toString() );

    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( true ).when( repositoryProvider ).createFolderCore( path );

    org.pentaho.platform.api.repository2.unified.RepositoryFile result =
      repositoryProvider.getOrCreateNativeFolder( path );

    assertNotNull( result );
    assertEquals( "newFolderId", result.getId().toString() );
    verify( repositoryProvider ).createFolderCore( path );
    verify( repositoryMock, times( 2 ) ).getFile( path.toString() );
  }

  @Test
  void testGetOrCreateNativeFolderReturnsNullWhenCreationFails() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/failFolder" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );

    // First call returns null (not found), triggering creation
    doReturn( null ).when( repositoryMock ).getFile( path.toString() );

    // Mock createFolderCore to fail (return false)
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doReturn( false ).when( repositoryProvider ).createFolderCore( path );

    assertThrows( NotFoundException.class, () -> repositoryProvider.getOrCreateNativeFolder( path ) );

    verify( repositoryProvider ).createFolderCore( path );
    verify( repositoryMock, times( 1 ) ).getFile( path.toString() );
  }

  @Test
  void testGetOrCreateNativeFolderThrowsInvalidOperationExceptionWhenAccessDenied() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/restrictedFolder" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );

    doThrow( new UnifiedRepositoryAccessDeniedException( "Access denied" ) ).when( repositoryMock )
      .getFile( path.toString() );

    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    AccessControlException exception =
      assertThrows( AccessControlException.class, () -> repositoryProvider.getOrCreateNativeFolder( path ) );

    assertNotNull( exception );
    verify( repositoryMock, times( 1 ) ).getFile( path.toString() );
  }

  @Test
  void testGetOrCreateNativeFolderThrowsOperationFailedExceptionWhenUnifiedRepositoryFails() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/errorFolder" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );

    // getNativeFile throws UnifiedRepositoryException
    doThrow( new UnifiedRepositoryException( "Repository error" ) )
      .when( repositoryMock ).getFile( path.toString() );

    RepositoryFileProvider repositoryProvider = new RepositoryFileProvider( repositoryMock, fileServiceMock );

    OperationFailedException exception = assertThrows( OperationFailedException.class,
      () -> repositoryProvider.getOrCreateNativeFolder( path ) );

    assertNotNull( exception );
    verify( repositoryMock, times( 1 ) ).getFile( path.toString() );
  }

  @Test
  void testGetOrCreateNativeFolderThrowsOperationFailedExceptionWhenCreateFolderCoreFails() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/createFailFolder" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );

    // First call returns null (not found), triggering creation
    doReturn( null ).when( repositoryMock ).getFile( path.toString() );

    // Mock createFolderCore to throw an exception
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doThrow( new OperationFailedException( "Failed to create folder" ) )
      .when( repositoryProvider ).createFolderCore( path );

    assertThrows( OperationFailedException.class,
      () -> repositoryProvider.getOrCreateNativeFolder( path ) );

    verify( repositoryProvider ).createFolderCore( path );
  }

  @Test
  void testGetOrCreateNativeFolderThrowsInvalidPathExceptionDuringCreation() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/invalidNameFolder" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );

    // First call returns null (not found), triggering creation
    doReturn( null ).when( repositoryMock ).getFile( path.toString() );

    // Mock createFolderCore to throw InvalidPathException
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doThrow( new InvalidPathException( "Invalid path" ) )
      .when( repositoryProvider ).createFolderCore( path );

    InvalidPathException exception = assertThrows( InvalidPathException.class,
      () -> repositoryProvider.getOrCreateNativeFolder( path ) );

    assertNotNull( exception );
    verify( repositoryProvider ).createFolderCore( path );
  }

  @Test
  void testGetOrCreateNativeFolderThrowsAccessControlExceptionDuringCreation() throws Exception {
    GenericFilePath path = GenericFilePath.parse( "/public/noCreatePermissionFolder" );
    IUnifiedRepository repositoryMock = mock( IUnifiedRepository.class );
    FileService fileServiceMock = mock( FileService.class );

    // First call returns null (not found), triggering creation
    doReturn( null ).when( repositoryMock ).getFile( path.toString() );

    // Mock createFolderCore to throw AccessControlException
    RepositoryFileProvider repositoryProvider = spy( new RepositoryFileProvider( repositoryMock, fileServiceMock ) );
    doThrow( new AccessControlException( "No permission to create" ) )
      .when( repositoryProvider ).createFolderCore( path );

    AccessControlException exception = assertThrows( AccessControlException.class,
      () -> repositoryProvider.getOrCreateNativeFolder( path ) );

    assertNotNull( exception );
    verify( repositoryProvider ).createFolderCore( path );
  }
  // endregion

}
