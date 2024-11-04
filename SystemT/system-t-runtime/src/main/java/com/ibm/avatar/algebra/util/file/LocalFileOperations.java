/*******************************************************************************
 * Copyright IBM
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *******************************************************************************/
package com.ibm.avatar.algebra.util.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.api.exceptions.URIToFileException;

public class LocalFileOperations extends FileOperations {

  /**
   * Singleton instance of this class. Marked as volatile so that multi-threaded calls will
   * guarantee seeing a completely initialized object.
   */
  static volatile LocalFileOperations singleton;

  /**
   * Returns a handle to the singleton instance of this class. If this instance has not yet been
   * created, lazily initialize it.
   * 
   * @return a pointer to the Local Filesystem handler
   */
  public static LocalFileOperations getInstance() {
    if (singleton == null) {
      synchronized (LocalFileOperations.class) {
        if (singleton == null) {
          singleton = new LocalFileOperations();
        }
      }
    }
    return singleton;
  }

  @Override
  protected InputStream getStreamImpl(String uri) throws Exception {
    return new FileInputStream(getFile(uri));
  }

  @Override
  protected boolean isFileImpl(String uri) throws Exception {
    return getFile(uri).isFile();
  }

  @Override
  protected boolean existsImpl(String uri) throws Exception {
    return getFile(uri).exists();
  }

  @Override
  protected boolean containsImpl(String parentDirURI, String childName) throws Exception {
    // create a URI from dir + name, then check to see if it exists
    return existsImpl(constructValidURIImpl(parentDirURI, childName));
  }

  @Override
  protected boolean isDirectoryImpl(String uri) throws Exception {
    return getFile(uri).isDirectory();
  }

  @Override
  protected String constructValidURIImpl(String directoryURI, String name) throws Exception {
    try {
      File dir = new File(new URI(directoryURI));
      return new File(dir, name).toURI().toString();
    } catch (URISyntaxException e) {
      throw new TextAnalyticsException(e, "URI %s has illegal syntax.", directoryURI);
    }
  }

  // Does the actual work in getting the file handle from the local filesystem
  private File getFile(String uri) throws Exception {
    File file;

    try {
      file = new File(createURI(uri));
    } catch (Exception e) {
      throw new URIToFileException(e, uri);
    }

    return file;
  }

}
