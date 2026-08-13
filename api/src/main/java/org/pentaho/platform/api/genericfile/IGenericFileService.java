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
import edu.umd.cs.findbugs.annotations.Nullable;
import org.pentaho.platform.api.genericfile.exception.AccessControlException;
import org.pentaho.platform.api.genericfile.exception.BatchOperationFailedException;
import org.pentaho.platform.api.genericfile.exception.ConflictException;
import org.pentaho.platform.api.genericfile.exception.InvalidOperationException;
import org.pentaho.platform.api.genericfile.exception.InvalidPathException;
import org.pentaho.platform.api.genericfile.exception.NotFoundException;
import org.pentaho.platform.api.genericfile.exception.OperationFailedException;
import org.pentaho.platform.api.genericfile.exception.ResourceAccessDeniedException;
import org.pentaho.platform.api.genericfile.model.CreateFileOptions;
import org.pentaho.platform.api.genericfile.model.IGenericFile;
import org.pentaho.platform.api.genericfile.model.IGenericFileAcl;
import org.pentaho.platform.api.genericfile.model.IGenericFileContent;
import org.pentaho.platform.api.genericfile.model.IGenericFileMetadata;
import org.pentaho.platform.api.genericfile.model.IGenericFileTree;

import java.io.InputStream;
import java.util.EnumSet;
import java.util.List;

/**
 * The {@code IGenericFileService} interface contains operations to access and modify generic files.
 *
 * @see GenericFilePath
 * @see IGenericFileProvider
 * @see IGenericFileTree
 */
public interface IGenericFileService {
  /**
   * Clears the cache of file trees, for all generic file providers, for the current user session.
   *
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   * @see #getTree(GetTreeOptions)
   * @see #createFolder(GenericFilePath)
   */
  void clearTreeCache() throws OperationFailedException;

  /**
   * Gets a tree of files.
   *
   * <p>
   * The results of this method are cached. To ensure fresh results, set {@link GetTreeOptions#setBypassCache(boolean)}
   * to {@code true} or call {@link #clearTreeCache()} beforehand.
   *
   * <h3>Obtaining the root tree</h3>
   * When called with a {@code null} {@link GetTreeOptions#getBasePath() base path option}, this method provides an
   * umbrella view of the generic file system, by using an <i>abstract</i> root tree folder. It is abstract in the sense
   * that the folder cannot be addressed for any actual file operations and serves only a grouping purpose. Moreover,
   * the children of this root folder may themselves be <i>umbrella</i> root tree folders, one per generic file system
   * provider backing the service. The exact structure of these provider umbrella trees is determined by the providers
   * themselves. While some providers may represent and expose a single, addressable file system, and opt to directly
   * return that file system's real root tree folder, others may represent several file systems, in which case these
   * are exposed under an abstract, umbrella provider root tree folder.
   * <p>
   * Implementations may directly return the tree result of a provider, when there is only one registered provider,
   * bypassing the abstract root tree folder.
   * <p>
   * In any case, the root tree folder returned by a provider is considered to have a depth of {@code 0}.
   * <p>
   * For a more regular and predictable interface to obtaining actually addressable generic file system root folders,
   * see {@link #getRootTrees(GetTreeOptions)} or {@link #getRootTrees()}.
   *
   * <h3>Obtaining a subtree</h3>
   * When {@link GetTreeOptions#getBasePath() base path} is specified, the returned tree is rooted at the specified
   * <i>base</i> folder. The <i>base</i> folder is considered to have a depth of {@code 0}.
   *
   * @param options The operation options.
   * @return The file tree.
   * @throws NotFoundException        If the specified base file does not exist, is not a folder, or the current user
   *                                  is not allowed to read it.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   */
  @NonNull
  IGenericFileTree getTree( @NonNull GetTreeOptions options ) throws OperationFailedException;

  /**
   * Gets a tree of files.
   *
   * <p>
   * The results of this method are cached. To ensure fresh results, call {@link #clearTreeCache()} beforehand.
   *
   * <h3>Obtaining the root tree</h3>
   * This method provides an umbrella view of the generic file system, by using an <i>abstract</i> root tree folder.
   * It is abstract in the sense that the folder cannot be addressed for any actual file operations and serves only a
   * grouping purpose. Moreover, the children of this root folder may themselves be <i>umbrella</i> root tree
   * folders, one per generic file system provider backing the service. The exact structure of these provider
   * umbrella trees is determined by the providers themselves. While some providers may represent and expose a
   * single, addressable file system, and opt to directly return that file system's real root tree folder, others may
   * represent several file systems, in which case these are exposed under an abstract, umbrella provider root tree
   * folder.
   * <p>
   * Implementations may directly return the tree result of a provider, when there is only one registered provider,
   * bypassing the abstract root tree folder.
   * <p>
   * In any case, the root tree folder returned by a provider is considered to have a depth of {@code 0}.
   * <p>
   * For a more regular and predictable interface to obtaining actually addressable generic file system root folders,
   * see {@link #getRootTrees(GetTreeOptions)} or {@link #getRootTrees()}.
   *
   * <h3>Obtaining a subtree</h3>
   * The <i>base</i> folder is considered to have a depth of {@code 0}.
   *
   * @return The file tree.
   * @throws NotFoundException        If the specified base file does not exist, is not a folder, or the current user
   *                                  is not allowed to read it.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   */
  @NonNull
  default IGenericFileTree getTree() throws OperationFailedException {
    return getTree( new GetTreeOptions() );
  }

  /**
   * Gets a list of the real root trees of the generic file system.
   * <p>
   * This method returns the real root trees that compose the generic file system.
   * Contrast with the method {@link #getTree(GetTreeOptions)}, when called with a {@code null}
   * {@link GetTreeOptions#getBasePath() base path option} or {@link #getTree()}, which instead offers an umbrella
   * view of the generic file system.
   * <p>
   * Each returned root folder is considered to have a depth of {@code 0}.
   * <p>
   * The results of this method are not cached, and so {@link GetTreeOptions#isBypassCache()} is ignored.
   *
   * @param options The operation options. The {@link GetTreeOptions#getBasePath() base path option} is ignored.
   * @return A list of the real root trees.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   */
  @NonNull
  List<IGenericFileTree> getRootTrees( @NonNull GetTreeOptions options ) throws OperationFailedException;

  /**
   * Gets a list of the real root trees of the generic file system.
   * <p>
   * This method returns the real root trees that compose the generic file system.
   * Contrast with the method {@link #getTree(GetTreeOptions)}, when called with a {@code null}
   * {@link GetTreeOptions#getBasePath() base path option} or {@link #getTree()}, which instead offers an umbrella
   * view of the generic file system.
   * <p>
   * Each returned root folder is considered to have a depth of {@code 0}.
   * <p>
   * The results of this method are not cached, and so {@link GetTreeOptions#isBypassCache()} is ignored.
   *
   * @return A list of the real root trees.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   */
  @NonNull
  default List<IGenericFileTree> getRootTrees() throws OperationFailedException {
    return getRootTrees( new GetTreeOptions() );
  }

  /**
   * Checks whether a generic file exists, is a folder and the current user can read it, given its path.
   *
   * @param path The path of the generic file.
   * @return {@code true}, if the conditions are met; {@code false}, otherwise.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   */
  boolean doesFolderExist( @NonNull GenericFilePath path ) throws OperationFailedException;

  /**
   * Checks whether a generic file exists, is a folder and the current user can read it, given its path's string
   * representation.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #doesFolderExist(GenericFilePath)} with the
   * result.
   *
   * @param path The string representation of the path of the generic file.
   * @return {@code true}, if the generic file exists; {@code false}, otherwise.
   * @throws InvalidPathException     If the specified path's string representation is not valid, according to
   *                                  {@link GenericFilePath#parseRequired(String)}.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   * @see IGenericFileService#doesFolderExist(GenericFilePath)
   */
  default boolean doesFolderExist( @NonNull String path ) throws OperationFailedException {
    return doesFolderExist( GenericFilePath.parseRequired( path ) );
  }

  /**
   * Creates a folder given its path.
   * <p>
   * This method ensures that each ancestor folder of the specified folder exists, creating it if necessary, and
   * allowed.
   * <p>
   * When the operation is successful, the folder session cache for the generic file provider owning the folder is
   * automatically cleared.
   *
   * @param path The path of the generic folder to create.
   * @return {@code true}, if the folder did not exist and was created; {@code false}, if the folder already existed.
   * @throws AccessControlException    If the current user cannot perform this operation.
   * @throws InvalidPathException      If the folder path is not valid.
   * @throws InvalidOperationException If the path, or one of its prefixes, does not exist and cannot be created
   *                                   using this service (e.g. connections, buckets);
   *                                   if the path or its longest existing prefix does not reference a folder;
   *                                   if the path does not exist and the current user is not allowed to create
   *                                   folders on the folder denoted by its longest existing prefix.
   * @throws OperationFailedException  If the operation fails for some other (checked) reason.
   * @see #clearTreeCache()
   */
  boolean createFolder( @NonNull GenericFilePath path ) throws OperationFailedException;

  /**
   * Creates a folder given its path's string representation.
   * <p>
   * This method ensures that each ancestor folder of the specified folder exists, creating it if necessary, and
   * allowed.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #createFolder(GenericFilePath)} with the
   * result.
   * <p>
   * When the operation is successful, the folder session cache for the generic file provider owning the folder is
   * automatically cleared.
   *
   * @param path The string representation of the path of the generic folder to create.
   * @return {@code true}, if the folder did not exist and was created; {@code false}, if the folder already existed.
   * @throws AccessControlException    If the current user cannot perform this operation.
   * @throws InvalidPathException      If the specified path's string representation is not valid, according to
   *                                   {@link GenericFilePath#parseRequired(String)}.
   * @throws InvalidOperationException If the path, or one of its prefixes, does not exist and cannot be created using
   *                                   this service (e.g. connections, buckets);
   *                                   if the path or its longest existing prefix does not reference a folder;
   *                                   if the path does not exist and the current user is not allowed to create
   *                                   folders on the folder denoted by its longest existing prefix.
   * @throws OperationFailedException  If the operation fails for some other (checked) reason.
   * @see #clearTreeCache()
   * @see IGenericFileService#createFolder(GenericFilePath)
   */
  default boolean createFolder( @Nullable String path ) throws OperationFailedException {
    return createFolder( GenericFilePath.parseRequired( path ) );
  }

  /**
   * Creates a file given its path and content, with default options.
   * <p>
   * This method ensures that each ancestor folder of the specified file exists, creating it if necessary, and allowed.
   * <p>
   * When the operation is successful, the folder session cache for the generic file provider owning the folder is
   * automatically cleared.
   *
   * @param path              The path of the generic file to create.
   * @param content           The content to write to the file as an InputStream.
   * @param createFileOptions The options for creating the file, includes the overwrite flag.
   * @throws AccessControlException    If the current user cannot perform this operation.
   * @throws InvalidPathException      If the file path is not valid.
   * @throws InvalidOperationException If the path, or one of its prefixes, does not exist and cannot be created
   *                                   using this service (e.g. connections, buckets);
   *                                   if the path exists but references a folder, or its longest existing prefix
   *                                   does not reference a folder;
   *                                   if the path does not exist and the file type is not accepted;
   * @throws NotFoundException         if the path does not exist and the current user is not allowed to create
   *                                   files on the folder denoted by its longest existing prefix, or the path exists
   *                                   and the user is not allowed to edit it when the {@code overwrite} flag is
   *                                   set to {@code true}.
   * @throws ConflictException         If the file with the new name already exists and the {@code overwrite} flag is
   *                                   set to {@code false}.
   * @throws OperationFailedException  If the operation fails for some other (checked) reason.
   */
  void createFile( @NonNull GenericFilePath path,
                   @NonNull InputStream content,
                   @NonNull CreateFileOptions createFileOptions )
    throws OperationFailedException;

  /**
   * Creates a file given its path's string representation and content, with default options.
   * <p>
   * This method ensures that each ancestor folder of the specified file exists, creating it if necessary, and allowed.
   * <p>
   * When the operation is successful, the folder session cache for the generic file provider owning the folder is
   * automatically cleared.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls
   * {@link #createFile(GenericFilePath, InputStream, CreateFileOptions)} with the result.
   *
   * @param path              The string representation of the path of the generic file.
   * @param content           The content to write to the file as an InputStream.
   * @param createFileOptions The options for creating the file, includes the overwrite flag.
   * @throws AccessControlException    If the current user cannot perform this operation.
   * @throws InvalidPathException      If the file path is not valid.
   * @throws InvalidOperationException If the path, or one of its prefixes, does not exist and cannot be created
   *                                   using this service (e.g. connections, buckets);
   *                                   if the path exists but references a folder, or its longest existing prefix
   *                                   does not reference a folder;
   *                                   if the path does not exist and the file type is not accepted;
   * @throws NotFoundException         if the path does not exist and the current user is not allowed to create
   *                                   files on the folder denoted by its longest existing prefix, or the path exists
   *                                   and the user is not allowed to edit it when the {@code overwrite} flag is
   *                                   set to {@code true}.
   * @throws ConflictException         If the file with the new name already exists and the {@code overwrite} flag is
   *                                   set to {@code false}.
   * @throws OperationFailedException  If the operation fails for some other (checked) reason.
   * @see IGenericFileService#createFile(GenericFilePath, InputStream, CreateFileOptions)
   */
  default void createFile( @NonNull String path,
                           @NonNull InputStream content,
                           @NonNull CreateFileOptions createFileOptions )
    throws OperationFailedException {
    createFile( GenericFilePath.parseRequired( path ), content, createFileOptions );
  }

  /**
   * Sets the content of an existing file, given its path and the new content.
   *
   * @param path    The path of the file to update.
   * @param content The new content to write to the file as an InputStream.
   * @throws AccessControlException    If the current user cannot perform this operation.
   * @throws InvalidPathException      If the file path is not valid.
   * @throws InvalidOperationException If the path is a folder.
   * @throws NotFoundException         If the specified file does not exist, or the current user is not allowed
   *                                   to access it.
   * @throws OperationFailedException  If the operation fails for some other (checked) reason.
   */
  void setFileContent( @NonNull GenericFilePath path, @NonNull InputStream content ) throws OperationFailedException;

  /**
   * Sets the content of an existing file, given its path's string representation and the new content.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls
   * {@link #setFileContent(GenericFilePath, InputStream)} with the result.
   *
   * @param path    The string representation of the path of the file to update.
   * @param content The new content to write to the file as an InputStream.
   * @throws AccessControlException    If the current user cannot perform this operation.
   * @throws InvalidPathException      If the file path is not valid, or if the specified path's string
   *                                   representation is not valid, according to
   *                                   {@link GenericFilePath#parseRequired(String)}.
   * @throws InvalidOperationException If the path is a folder.
   * @throws NotFoundException         If the specified file does not exist, or the current user is not allowed
   *                                   to access it.
   * @throws OperationFailedException  If the operation fails for some other (checked) reason.
   * @see IGenericFileService#setFileContent(GenericFilePath, InputStream)
   */
  default void setFileContent( @NonNull String path, @NonNull InputStream content ) throws OperationFailedException {
    setFileContent( GenericFilePath.parseRequired( path ), content );
  }

  /**
   * Checks whether a generic file exists and the current user has the specified permissions on it, given its path's
   * string representation.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #hasAccess(GenericFilePath, EnumSet)} with the
   * result.
   *
   * @param path        The string representation of the path of the generic file.
   * @param permissions Set of permissions needed for any operation like READ/WRITE/DELETE
   * @return {@code true}, if the conditions are; {@code false}, otherwise.
   * @throws InvalidPathException     If the specified path's string representation is not valid, according to
   *                                  {@link GenericFilePath#parseRequired(String)}.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   * @see IGenericFileService#hasAccess(GenericFilePath, EnumSet)
   */
  default boolean hasAccess( @NonNull String path, @NonNull EnumSet<GenericFilePermission> permissions )
    throws OperationFailedException {
    return hasAccess( GenericFilePath.parseRequired( path ), permissions );
  }

  /**
   * Checks whether a generic file exists and the current user has the specified permissions on it.
   *
   * @param path        The path of the generic file.
   * @param permissions Set of permissions needed for any operation like READ/WRITE/DELETE
   * @return {@code true}, if the conditions are; {@code false}, otherwise.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   */
  boolean hasAccess( @NonNull GenericFilePath path, @NonNull EnumSet<GenericFilePermission> permissions )
    throws OperationFailedException;

  /**
   * Gets the content of a file, given its path's string representation.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #getFileContent(GenericFilePath, boolean)} with
   * the result.
   *
   * @param path       The string representation of the path of the file.
   * @param compressed If {@code true}, returns the content as a compressed archive (works for files and folders).
   *                   If {@code false}, returns the raw file content (only valid for files).
   * @return The file's content.
   * @throws InvalidOperationException     If the path is a folder and {@code compressed} is {@code false}.
   *                                       Or if the path is invalid when the output is compressed.
   * @throws InvalidPathException          If the specified path's string representation is not valid, according to
   *                                       {@link GenericFilePath#parseRequired(String)}.
   * @throws NotFoundException             If the specified file does not exist, or the current user is not allowed
   *                                       to access it.
   * @throws ResourceAccessDeniedException If the current user cannot access the content of the specified path.
   * @throws AccessControlException        If the current user cannot perform this operation.
   * @throws OperationFailedException      If the operation fails for some other (checked) reason.
   * @see IGenericFileService#getFileContent(GenericFilePath, boolean)
   */
  @NonNull
  default IGenericFileContent getFileContent( @NonNull String path, boolean compressed )
    throws OperationFailedException {
    return getFileContent( GenericFilePath.parseRequired( path ), compressed );
  }

  /**
   * Gets the content of a file, given its path.
   *
   * @param path       The path of the file.
   * @param compressed If {@code true}, returns the content as a compressed archive (works for files and folders).
   *                   If {@code false}, returns the raw file content (only valid for files).
   * @return The file's content.
   * @throws InvalidOperationException     If the path is a folder and {@code compressed} is {@code false}.
   *                                       Or if the path is invalid when the output is compressed.
   * @throws NotFoundException             If the specified file does not exist, or the current user is not allowed
   *                                       to access it.
   * @throws ResourceAccessDeniedException If the current user cannot access the content of the specified path.
   * @throws AccessControlException        If the current user cannot perform this operation.
   * @throws OperationFailedException      If the operation fails for some other (checked) reason.
   */
  @NonNull
  IGenericFileContent getFileContent( @NonNull GenericFilePath path, boolean compressed )
    throws OperationFailedException;

  /**
   * Gets the compressed content of a file, given its path. This method can be used to retrieve the compressed
   * content of both files and folders.
   *
   * @param path The path of the file.
   * @return The compressed file's content.
   * @throws InvalidOperationException     If the path is invalid.
   * @throws NotFoundException             If the specified file does not exist, or the current user is not allowed
   *                                       to access it.
   * @throws ResourceAccessDeniedException If the current user cannot access the content of the specified path.
   * @throws AccessControlException        If the current user cannot perform this operation.
   * @throws OperationFailedException      If the operation fails for some other (checked) reason.
   * @see IGenericFileService#getFileContent(GenericFilePath, boolean)
   */
  @NonNull
  default IGenericFileContent getFileContentCompressed( @NonNull GenericFilePath path )
    throws OperationFailedException {
    return getFileContent( path, true );
  }

  /**
   * Gets the compressed content of a file, given its path's string representation. This method can be used to
   * retrieve the compressed content of both files and folders.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #getFileContent(GenericFilePath, boolean)} with
   * the result.
   *
   * @param path The string representation of the path of the file.
   * @return The compressed file's content.
   * @throws InvalidPathException          If the specified path's string representation is not valid, according to
   *                                       {@link GenericFilePath#parseRequired(String)}, or if the path is invalid.
   * @throws NotFoundException             If the specified file does not exist, or the current user is not allowed
   *                                       to access it.
   * @throws ResourceAccessDeniedException If the current user cannot access the content of the specified path.
   * @throws AccessControlException        If the current user cannot perform this operation.
   * @throws OperationFailedException      If the operation fails for some other (checked) reason.
   * @see IGenericFileService#getFileContent(GenericFilePath, boolean)
   */
  @NonNull
  default IGenericFileContent getFileContentCompressed( @NonNull String path ) throws OperationFailedException {
    return getFileContent( GenericFilePath.parseRequired( path ), true );
  }

  /**
   * Gets a file given its path's string representation.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #getFile(GenericFilePath)} with the result.
   *
   * @param path The string representation of the path of the file.
   * @return The file.
   * @throws InvalidPathException     If the specified path's string representation is not valid, according to
   *                                  {@link GenericFilePath#parseRequired(String)}.
   * @throws NotFoundException        If the specified file does not exist, or the current user is not allowed to
   *                                  access it.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   * @see IGenericFileService#getFile(GenericFilePath)
   */
  @NonNull
  default IGenericFile getFile( @NonNull String path ) throws OperationFailedException {
    return getFile( GenericFilePath.parseRequired( path ) );
  }

  /**
   * Gets a file given its path's string representation.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #getFile(GenericFilePath)} with the result.
   *
   * @param path    The string representation of the path of the file.
   * @param options The operation options.
   * @return The file.
   * @throws InvalidPathException     If the specified path's string representation is not valid, according to
   *                                  {@link GenericFilePath#parseRequired(String)}.
   * @throws NotFoundException        If the specified file does not exist, or the current user is not allowed to
   *                                  access it.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   * @see IGenericFileService#getFile(GenericFilePath)
   */
  @NonNull
  default IGenericFile getFile( @NonNull String path, @NonNull GetFileOptions options )
    throws OperationFailedException {
    return getFile( GenericFilePath.parseRequired( path ), options );
  }

  /**
   * Gets a file given its path.
   *
   * @param path The path of the file.
   * @return The file.
   * @throws NotFoundException        If the specified file does not exist, or the current user is not allowed to
   *                                  access it.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   */
  @NonNull
  IGenericFile getFile( @NonNull GenericFilePath path ) throws OperationFailedException;

  /**
   * Gets a file given its path.
   *
   * @param path    The path of the file.
   * @param options The operation options.
   * @return The file.
   * @throws NotFoundException        If the specified file does not exist, or the current user is not allowed to
   *                                  access it.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   */
  @NonNull
  IGenericFile getFile( @NonNull GenericFilePath path, @NonNull GetFileOptions options )
    throws OperationFailedException;

  /**
   * Gets a list of deleted files which are still available in the trash folder.
   *
   * @return The list of deleted files.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   */
  @NonNull
  List<IGenericFile> getDeletedFiles() throws OperationFailedException;

  /**
   * Permanently deletes files, given their paths.
   *
   * @param paths The list of file paths to be permanently deleted. These paths must refer to items that are in the
   *              trash (deleted).
   * @throws AccessControlException        If the current user cannot perform this operation.
   * @throws BatchOperationFailedException If the batch operation fails for some reason.
   * @throws OperationFailedException      If the operation fails for some other (checked) reason.
   */
  void deleteFilesPermanently( @NonNull List<GenericFilePath> paths ) throws OperationFailedException;

  /**
   * Permanently deletes a file, given its path.
   *
   * @param path The file path to be permanently deleted. This path must refer to an item in the trash (deleted).
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws InvalidPathException     If the specified path is not valid.
   * @throws NotFoundException        If the specified path does not exist, or does not refer to an item in the trash
   *                                  (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   */
  void deleteFilePermanently( @NonNull GenericFilePath path ) throws OperationFailedException;

  /**
   * Permanently deletes a file, given its path's string representation.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #deleteFilePermanently(GenericFilePath)}
   * with the result.
   *
   * @param path The string representation of the file's path to be permanently deleted. This path must refer to an
   *             item in the trash (deleted).
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws InvalidPathException     If the specified path is not valid, or if the specified path's string
   *                                  representation is not valid, according to
   *                                  {@link GenericFilePath#parseRequired(String)}.
   * @throws NotFoundException        If the specified path does not exist, or does not refer to an item in the trash
   *                                  (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   * @see IGenericFileService#deleteFilePermanently(GenericFilePath)
   */
  default void deleteFilePermanently( @NonNull String path ) throws OperationFailedException {
    deleteFilePermanently( GenericFilePath.parseRequired( path ) );
  }

  /**
   * Deletes files, given their paths, by sending them to the trash or permanently deleting them, depending on the
   * value of the {@code permanent} variable.
   *
   * @param paths     The list of file paths to be deleted. These paths must not refer to items that are in the
   *                  trash (deleted).
   * @param permanent If {@code true}, the file is permanently deleted; if {@code false}, the file is sent to the trash.
   * @throws AccessControlException        If the current user cannot perform this operation.
   * @throws BatchOperationFailedException If the batch operation fails for some reason.
   * @throws OperationFailedException      If the operation fails for some other (checked) reason.
   */
  void deleteFiles( @NonNull List<GenericFilePath> paths, boolean permanent ) throws OperationFailedException;

  /**
   * Deletes files, given their paths, by sending them to the trash.
   *
   * @param paths The list of file paths to be deleted. These paths must not refer to items that are in the trash
   *              (deleted).
   * @throws AccessControlException        If the current user cannot perform this operation.
   * @throws BatchOperationFailedException If the batch operation fails for some reason.
   * @throws OperationFailedException      If the operation fails for some other (checked) reason.
   */
  default void deleteFiles( @NonNull List<GenericFilePath> paths ) throws OperationFailedException {
    deleteFiles( paths, false );
  }

  /**
   * Deletes a file, given its path, by sending it to the trash or permanently deleting it, depending on the value of
   * the {@code permanent} variable.
   *
   * @param path      The file path to be deleted. This path must not refer to an item in the trash (deleted).
   * @param permanent If {@code true}, the file is permanently deleted; if {@code false}, the file is sent to the trash.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws NotFoundException        If the specified path does not exist, or does refer to an item in the trash
   *                                  (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   */
  void deleteFile( @NonNull GenericFilePath path, boolean permanent ) throws OperationFailedException;

  /**
   * Deletes a file, given its path, by sending it to the trash.
   *
   * @param path The file path to be deleted. This path must not refer to an item in the trash (deleted).
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws NotFoundException        If the specified path does not exist, or does refer to an item in the trash
   *                                  (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   * @see IGenericFileService#deleteFile(GenericFilePath, boolean)
   */
  default void deleteFile( @NonNull GenericFilePath path ) throws OperationFailedException {
    deleteFile( path, false );
  }

  /**
   * Deletes a file, given its path's string representation, by sending it to the trash or permanently deleting it,
   * depending on the value of the {@code permanent} variable.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #deleteFile(GenericFilePath, boolean)}
   * with the result.
   *
   * @param path      The string representation of the file's path to be deleted. This path must not refer to an item
   *                  in the trash (deleted).
   * @param permanent If {@code true}, the file is permanently deleted; if {@code false}, the file is sent to the trash.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws InvalidPathException     If the specified path's string representation is not valid, according to
   *                                  {@link GenericFilePath#parseRequired(String)}.
   * @throws NotFoundException        If the specified path does not exist, or does refer to an item in the trash
   *                                  (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   * @see IGenericFileService#deleteFile(GenericFilePath, boolean)
   */
  default void deleteFile( @NonNull String path, boolean permanent ) throws OperationFailedException {
    deleteFile( GenericFilePath.parseRequired( path ), permanent );
  }

  /**
   * Deletes a file, given its path's string representation, by sending it to the trash.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #deleteFile(String, boolean)}
   * with the result.
   *
   * @param path The string representation of the file's path to be deleted. This path must not refer to an item in
   *             the trash (deleted).
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws InvalidPathException     If the specified path's string representation is not valid, according to
   *                                  {@link GenericFilePath#parseRequired(String)}.
   * @throws NotFoundException        If the specified path does not exist, or does refer to an item in the trash
   *                                  (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   * @see IGenericFileService#deleteFile(GenericFilePath, boolean)
   */
  default void deleteFile( @NonNull String path ) throws OperationFailedException {
    deleteFile( path, false );
  }

  /**
   * Restores files, given their paths.
   *
   * @param paths The list of file paths to be restored. These paths must refer to items that are in the trash
   *              (deleted).
   * @throws AccessControlException        If the current user cannot perform this operation.
   * @throws BatchOperationFailedException If the batch operation fails for some reason.
   * @throws OperationFailedException      If the operation fails for some other (checked) reason.
   */
  void restoreFiles( @NonNull List<GenericFilePath> paths ) throws OperationFailedException;

  /**
   * Restores a file, given its path.
   *
   * @param path The file path to be restored. This path must refer to an item in the trash (deleted).
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws InvalidPathException     If the specified path is not valid.
   * @throws NotFoundException        If the specified path does not exist, or does not refer to an item in the
   *                                  trash (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   */
  void restoreFile( @NonNull GenericFilePath path ) throws OperationFailedException;

  /**
   * Restores a file, given its path's string representation.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #restoreFile(GenericFilePath)}
   * with the result.
   *
   * @param path The string representation of the file's path to be restored. This path must refer to an item in the
   *             trash (deleted).
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws InvalidPathException     If the specified path is not valid, or if the specified path's string
   *                                  representation is not valid, according to
   *                                  {@link GenericFilePath#parseRequired(String)}.
   * @throws NotFoundException        If the specified path does not exist, or does not refer to an item in the
   *                                  trash (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   * @see IGenericFileService#restoreFile(GenericFilePath)
   */
  default void restoreFile( @NonNull String path ) throws OperationFailedException {
    restoreFile( GenericFilePath.parseRequired( path ) );
  }

  /**
   * Renames a file or folder, given its path's string representation and the new name. If it's a file, this method
   * does not change its extension.
   *
   * @param path    The path of the file or folder to be renamed. This path must not refer to an item in the trash
   *                (deleted).
   * @param newName The new name of the file or folder. If it's a file, the new name must not have its extension.
   *                This name must not be empty, and must not contain any control characters.
   * @return {@code true} if the file or folder was renamed, {@code false} otherwise.
   * @throws AccessControlException    If the current user cannot perform this operation.
   * @throws InvalidPathException      If the new path is not valid.
   * @throws InvalidOperationException If the {@code newName} is not valid.
   * @throws NotFoundException         If the specified path does not exist, or does refer to an item in the
   *                                   trash (deleted), or the current user is not allowed to access it.
   * @throws ConflictException         If the file or folder with the new name already exists.
   * @throws OperationFailedException  If the operation fails for some other (checked) reason.
   */
  boolean renameFile( @NonNull GenericFilePath path, @NonNull String newName ) throws OperationFailedException;

  /**
   * Renames a file or folder, given its path's string representation and the new name. If it's a file, this method
   * does not change its extension.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #renameFile(GenericFilePath, String)}
   * with the result.
   *
   * @param path    The string representation of the file or folder's path to be renamed. This path must not refer to
   *                an item in the trash (deleted).
   * @param newName The new name of the file or folder. If it's a file, the new name must not have its extension.
   *                This name must not be empty, and must not contain any control characters.
   * @return {@code true} if the file or folder was renamed, {@code false} otherwise.
   * @throws AccessControlException    If the current user cannot perform this operation.
   * @throws InvalidPathException      If either path's string representation is not valid, according to
   *                                   {@link GenericFilePath#parseRequired(String)}, or if the new path is not
   *                                   valid.
   * @throws InvalidOperationException If the {@code newName} is not valid.
   * @throws NotFoundException         If the specified path does not exist, or does refer to an item or folder
   *                                   in the trash (deleted), or the current user is not allowed to access it.
   * @throws ConflictException         If the file or folder with the new name already exists.
   * @throws OperationFailedException  If the operation fails for some other (checked) reason.
   * @see IGenericFileService#renameFile(GenericFilePath, String)
   */
  default boolean renameFile( @NonNull String path, @NonNull String newName ) throws OperationFailedException {
    return renameFile( GenericFilePath.parseRequired( path ), newName );
  }

  /**
   * Copies files or folders from a given path to a destination folder.
   *
   * @param paths             The list of file or folder paths to be copied. These paths must not refer to an item in
   *                          the trash (deleted).
   * @param destinationFolder The folder path to copy the files to. This path must not refer to a folder in the trash
   *                          (deleted).
   * @throws AccessControlException        If the current user cannot perform this operation.
   * @throws BatchOperationFailedException If the batch operation fails for some reason.
   * @throws OperationFailedException      If the operation fails for some other (checked) reason.
   */
  void copyFiles( @NonNull List<GenericFilePath> paths, @NonNull GenericFilePath destinationFolder )
    throws OperationFailedException;

  /**
   * Copies a file or folder from a given path to a destination folder.
   *
   * @param path              The path of the file or folder to be copied. This path must not refer to an item in the
   *                          trash (deleted).
   * @param destinationFolder The path of the destination folder. This path must not refer to a folder in the trash
   *                          (deleted).
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws InvalidPathException     If the destination path is not valid.
   * @throws NotFoundException        If either path does not exist or does refer to an item in the trash
   *                                  (deleted), or the current user is not allowed to access it.
   * @throws ConflictException        If the file or folder to be copied already exists on the destination folder.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   */
  void copyFile( @NonNull GenericFilePath path, @NonNull GenericFilePath destinationFolder )
    throws OperationFailedException;

  /**
   * Copies a file or folder from a given path's string representation to a destination folder.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #copyFile(GenericFilePath, GenericFilePath)}
   * with the result.
   *
   * @param path              The string representation of the file's path to be copied. This path must not refer to
   *                          an item in the trash (deleted).
   * @param destinationFolder The string representation of the folder's path to copy the files to. This path must not
   *                          refer to a folder in the trash (deleted).
   * @throws AccessControlException    If the current user cannot perform this operation.
   * @throws InvalidOperationException If the {@code destinationFolder} is not valid.
   * @throws InvalidPathException      If either path's string representation is not valid, according to
   *                                   {@link GenericFilePath#parseRequired(String)}, or if the destination path
   *                                   is not valid.
   * @throws NotFoundException         If either path does not exist or does refer to an item in the
   *                                   trash (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException  If the operation fails for some other (checked) reason.
   * @see IGenericFileService#copyFile(GenericFilePath, GenericFilePath)
   */
  default void copyFile( @NonNull String path, @NonNull String destinationFolder ) throws OperationFailedException {
    copyFile( GenericFilePath.parseRequired( path ), GenericFilePath.parseRequired( destinationFolder ) );
  }

  /**
   * Moves files or folders from a given path to a destination folder.
   *
   * @param paths             The list of file or folder paths to be moved. These paths must not refer to an item in
   *                          the trash (deleted).
   * @param destinationFolder The folder path to move the files to. This path must not refer to a folder in the trash
   *                          (deleted).
   * @throws AccessControlException        If the current user cannot perform this operation.
   * @throws BatchOperationFailedException If the batch operation fails for some reason.
   * @throws OperationFailedException      If the operation fails for some other (checked) reason.
   */
  void moveFiles( @NonNull List<GenericFilePath> paths, @NonNull GenericFilePath destinationFolder )
    throws OperationFailedException;

  /**
   * Moves a file or folder from a given path to a destination folder.
   *
   * @param path              The path of the file or folder to be moved. This path must not refer to an item in the
   *                          trash (deleted).
   * @param destinationFolder The path of the destination folder. This path must not refer to a folder in the trash
   *                          (deleted).
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws InvalidPathException     If the destination path is not valid.
   * @throws NotFoundException        If either path does not exist or does refer to an item in the trash
   *                                  (deleted), or the current user is not allowed to access it.
   * @throws ConflictException        If the file or folder to be moved already exists on the destination folder.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   */
  void moveFile( @NonNull GenericFilePath path, @NonNull GenericFilePath destinationFolder )
    throws OperationFailedException;

  /**
   * Moves a file or folder from a given path's string representation to a destination folder.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #moveFile(GenericFilePath, GenericFilePath)}
   * with the result.
   *
   * @param path              The string representation of the file's path to be moved. This path must not
   *                          refer to an item in the trash (deleted).
   * @param destinationFolder The string representation of the folder's path to move the files to. This path must not
   *                          refer to a folder in the trash (deleted).
   * @throws AccessControlException    If the current user cannot perform this operation.
   * @throws InvalidOperationException If the {@code destinationFolder} is not valid.
   * @throws InvalidPathException      If either path's string representation is not valid, according to
   *                                   {@link GenericFilePath#parseRequired(String)}, or if the destination path
   *                                   is not valid.
   * @throws NotFoundException         If either path does not exist or does refer to an item in the
   *                                   trash (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException  If the operation fails for some other (checked) reason.
   * @see IGenericFileService#moveFile(GenericFilePath, GenericFilePath)
   */
  default void moveFile( @NonNull String path, @NonNull String destinationFolder ) throws OperationFailedException {
    copyFile( GenericFilePath.parseRequired( path ), GenericFilePath.parseRequired( destinationFolder ) );
  }

  /**
   * Gets the file metadata, given its path.
   *
   * @param path The file path to get the metadata from. This path must not refer to an item in the trash (deleted).
   * @return The file metadata.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws NotFoundException        If the specified path does not exist, or does refer to an item in the trash
   *                                  (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   */
  @NonNull
  IGenericFileMetadata getFileMetadata( @NonNull GenericFilePath path ) throws OperationFailedException;

  /**
   * Gets the file metadata, given its path.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #getFileMetadata(GenericFilePath)}
   * with the result.
   *
   * @param path The string representation of the file's path to get the metadata from. This path must not refer to
   *             an item in the trash (deleted).
   * @return The file metadata.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws InvalidPathException     If the specified path's string representation is not valid, according to
   *                                  {@link GenericFilePath#parseRequired(String)}.
   * @throws NotFoundException        If the specified path does not exist, or does refer to an item in the trash
   *                                  (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   * @see IGenericFileService#getFileMetadata(GenericFilePath)
   */
  @NonNull
  default IGenericFileMetadata getFileMetadata( @NonNull String path ) throws OperationFailedException {
    return getFileMetadata( GenericFilePath.parseRequired( path ) );
  }

  /**
   * Sets the file metadata, given its path and the metadata to set.
   *
   * @param path     The file path to set the metadata for. This path must not refer to an item in the trash (deleted).
   * @param metadata The metadata to set. If empty, all existing metadata is removed.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws NotFoundException        If the specified path does not exist, or does refer to an item in the trash
   *                                  (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   */
  void setFileMetadata( @NonNull GenericFilePath path, @NonNull IGenericFileMetadata metadata )
    throws OperationFailedException;

  /**
   * Sets the file metadata, given its path's string representation and the metadata to set.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls
   * {@link #setFileMetadata(GenericFilePath, IGenericFileMetadata)} with the result.
   *
   * @param path     The string representation of the file's path to set the metadata for. This path must not refer
   *                 to an item in the trash (deleted).
   * @param metadata The metadata to set. If empty, all existing metadata is removed.
   * @throws AccessControlException   If the current user cannot perform this operation.
   * @throws InvalidPathException     If the specified path's string representation is not valid, according to
   *                                  {@link GenericFilePath#parseRequired(String)}.
   * @throws NotFoundException        If the specified path does not exist, or does refer to an item in the
   *                                  trash (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException If the operation fails for some other (checked) reason.
   * @see IGenericFileService#setFileMetadata(GenericFilePath, IGenericFileMetadata)
   */
  default void setFileMetadata( @NonNull String path, @NonNull IGenericFileMetadata metadata )
    throws OperationFailedException {
    setFileMetadata( GenericFilePath.parseRequired( path ), metadata );
  }

  /**
   * Gets the file Access Control List (ACL), given its path.
   *
   * @param path The file path to get the Access Control List (ACL) from. This path must not refer to an item in the
   *             trash (deleted).
   * @return The file Access Control List (ACL).
   * @throws InvalidOperationException If the Access Control List (ACL) cannot be converted to an
   *                                   {@link IGenericFileAcl} implementation.
   * @throws AccessControlException    If the current user cannot perform this operation.
   * @throws NotFoundException         If the specified path does not exist, or does refer to an item in the trash
   *                                   (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException  If the operation fails for some other (checked) reason.
   */
  @NonNull
  default IGenericFileAcl getFileAcl( @NonNull GenericFilePath path ) throws OperationFailedException {
    return getFileAcl( path, false );
  }

  /**
   * Gets the file Access Control List (ACL), given its path's string representation.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #getFileAcl(GenericFilePath, boolean)} with
   * the result.
   *
   * @param path The string representation of the file's path to get the Access Control List (ACL) from. This path
   *             must not refer to an item in the trash (deleted).
   * @return The file Access Control List (ACL).
   * @throws InvalidOperationException If the Access Control List (ACL) cannot be converted to an
   *                                   {@link IGenericFileAcl} implementation.
   * @throws InvalidPathException      If the specified path's string representation is not valid, according to
   *                                   {@link GenericFilePath#parseRequired(String)}.
   * @throws AccessControlException    If the current user cannot perform this operation.
   * @throws NotFoundException         If the specified path does not exist, or does refer to an item in the trash
   *                                   (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException  If the operation fails for some other (checked) reason.
   * @see IGenericFileService#getFileAcl(GenericFilePath, boolean)
   */
  @NonNull
  default IGenericFileAcl getFileAcl( @NonNull String path ) throws OperationFailedException {
    return getFileAcl( GenericFilePath.parseRequired( path ), false );
  }

  /**
   * Gets the file Access Control List (ACL), given its path.
   *
   * @param path            The file path to get the Access Control List (ACL) from. This path must not refer to an
   *                        item in the trash (deleted).
   * @param forceInheriting If {@code true}, the returned Access Control List (ACL) will be forced to inherit entries
   *                        from its parent folders, if it does not have its own entries; if {@code false}, the returned
   *                        ACL will be as stored for the file, without any modification.
   * @return The file Access Control List (ACL).
   * @throws InvalidOperationException If the Access Control List (ACL) cannot be converted to an
   *                                   {@link IGenericFileAcl} implementation.
   * @throws AccessControlException    If the current user cannot perform this operation.
   * @throws NotFoundException         If the specified path does not exist, or does refer to an item in the trash
   *                                   (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException  If the operation fails for some other (checked) reason.
   */
  @NonNull
  IGenericFileAcl getFileAcl( @NonNull GenericFilePath path, boolean forceInheriting ) throws OperationFailedException;

  /**
   * Gets the file Access Control List (ACL), given its path's string representation.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls {@link #getFileAcl(GenericFilePath, boolean)} with
   * the result.
   *
   * @param path            The string representation of the file's path to get the Access Control List (ACL) from.
   *                        This path must not refer to an item in the trash (deleted).
   * @param forceInheriting If {@code true}, the returned Access Control List (ACL) will be forced to inherit entries
   *                        from its parent folders, if it does not have its own entries; if {@code false}, the returned
   *                        ACL will be as stored for the file, without any modification.
   * @return The file Access Control List (ACL).
   * @throws InvalidOperationException If the Access Control List (ACL) cannot be converted to an
   *                                   {@link IGenericFileAcl} implementation.
   * @throws InvalidPathException      If the specified path's string representation is not valid, according to
   *                                   {@link GenericFilePath#parseRequired(String)}.
   * @throws AccessControlException    If the current user cannot perform this operation.
   * @throws NotFoundException         If the specified path does not exist, or does refer to an item in the trash
   *                                   (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException  If the operation fails for some other (checked) reason.
   * @see IGenericFileService#getFileAcl(GenericFilePath, boolean)
   */
  @NonNull
  default IGenericFileAcl getFileAcl( @NonNull String path, boolean forceInheriting ) throws OperationFailedException {
    return getFileAcl( GenericFilePath.parseRequired( path ), forceInheriting );
  }

  /**
   * Sets the file Access Control List (ACL), given its path and the ACL to set.
   *
   * @param path The file path to set the Access Control List (ACL) for. This path must not refer to an item in the
   *             trash (deleted).
   * @param acl  The Access Control List (ACL) to set. When {@code entriesInheriting} is {@code false} on the given
   *             {@link IGenericFileAcl}, the acl must contain at least one entry; when{@code entriesInheriting} is
   *             {@code true}, the acl entries may be {@code null} or empty and will be interpreted according to the
   *             inheritance semantics.
   * @throws InvalidOperationException If the Access Control List (ACL) is not valid, for the target file (for
   *                                   example, if inheritance is disabled but no entries are provided).
   * @throws AccessControlException    If the current user cannot perform this operation.
   * @throws NotFoundException         If the specified path does not exist, or does refer to an item in the trash
   *                                   (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException  If the operation fails for some other (checked) reason.
   */
  void setFileAcl( @NonNull GenericFilePath path, @NonNull IGenericFileAcl acl )
    throws OperationFailedException;

  /**
   * Sets the file Access Control List (ACL), given its path's string representation and the ACL to set.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls
   * {@link #setFileAcl(GenericFilePath, IGenericFileAcl)} with the result.
   *
   * @param path The string representation of the file's path to set the Access Control List (ACL) for. This path
   *             must not refer to an item in the trash (deleted).
   * @param acl  The Access Control List (ACL) to set. When {@code entriesInheriting} is {@code false} on the given
   *             {@link IGenericFileAcl}, the acl must contain at least one entry; when{@code entriesInheriting} is
   *             {@code true}, the acl entries may be {@code null} or empty and will be interpreted according to the
   *             inheritance semantics.
   * @throws InvalidOperationException If the Access Control List (ACL) is not valid, for the target file (for
   *                                   example, if inheritance is disabled but no entries are provided).
   * @throws InvalidPathException      If the specified path's string representation is not valid, according to
   *                                   {@link GenericFilePath#parseRequired(String)}.
   * @throws AccessControlException    If the current user cannot perform this operation.
   * @throws NotFoundException         If the specified path does not exist, or does refer to an item in the
   *                                   trash (deleted), or the current user is not allowed to access it.
   * @throws OperationFailedException  If the operation fails for some other (checked) reason.
   * @see IGenericFileService#setFileAcl(GenericFilePath, IGenericFileAcl)
   */
  default void setFileAcl( @NonNull String path, @NonNull IGenericFileAcl acl )
    throws OperationFailedException {
    setFileAcl( GenericFilePath.parseRequired( path ), acl );
  }

  /**
   * Validates the given Access Control List (ACL) for a file, given its path.
   * <p>
   * This method can be used to validate an ACL before setting it on a file, to ensure that the ACL is valid for the
   * provider and the file, and to avoid setting an invalid ACL and then having to handle the exception from the
   * {@code setFileAcl} method.
   * <p>
   * The ACL is considered valid if it meets the provider-specific requirements for ACLs, such as having the
   * necessary fields, having valid entries, and meeting any constraints related to inheritance or other ACL features.
   *
   * @param path The file path to validate the Access Control List (ACL). This path must not refer to an item in
   *             the trash (deleted).
   * @param acl  The Access Control List (ACL) to validate.
   * @return {@code true} if the ACL is valid; {@code false} otherwise.
   */
  boolean validateFileAcl( @NonNull GenericFilePath path, @NonNull IGenericFileAcl acl )
    throws OperationFailedException;

  /**
   * Validates the given Access Control List (ACL) for a file, given its path's string representation.
   * <p>
   * The default implementation of this method parses the given path's string representation using
   * {@link GenericFilePath#parseRequired(String)} and then calls
   * {@link #validateFileAcl(GenericFilePath, IGenericFileAcl)} with the result.
   *
   * @param path The string representation of the file's path to validate the Access Control List (ACL). This path
   *             must not refer to an item in the trash (deleted).
   * @param acl  The Access Control List (ACL) to validate.
   * @return {@code true} if the ACL is valid; {@code false} otherwise.
   * @see IGenericFileService#validateFileAcl(GenericFilePath, IGenericFileAcl)
   */
  default boolean validateFileAcl( @NonNull String path, @NonNull IGenericFileAcl acl )
    throws OperationFailedException {
    return validateFileAcl( GenericFilePath.parseRequired( path ), acl );
  }
}
