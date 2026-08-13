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

package org.pentaho.platform.api.genericfile.model;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.InputStream;

// NOTE: Designed after the class
// {@code org.pentaho.platform.web.http.api.resources.services.FileService.RepositoryFileToStream}.

/**
 * The {@code IGenericFileContent} interface contains the necessary information for returning a
 * {@code IGenericFile}'s content.
 */
@SuppressWarnings( "unused" )
public interface IGenericFileContent {
  /**
   * Gets the file's content InputStream.
   */
  @NonNull
  InputStream getInputStream();

  /**
   * Gets the name of the file associated with the content InputStream.
   */
  @NonNull
  String getFileName();

  /**
   * Gets the MIME type of the file's content.
   * <p>
   * For more information on MIME types, @see <a href="https://www.w3.org/wiki/WebIntents/MIME_Types">MIME Types</a>
   */
  @NonNull
  String getMimeType();
}
