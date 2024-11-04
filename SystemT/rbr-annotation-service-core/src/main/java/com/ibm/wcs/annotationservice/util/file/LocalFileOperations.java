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
package com.ibm.wcs.annotationservice.util.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;

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
  protected void copyFileToLocalImpl(String src, String localDest) throws Exception {
    File srcFile = new File(src);
    File destFile = new File(localDest);

    Files.copy(srcFile.toPath(), destFile.toPath());
  }

  @Override
  protected void copyDirToLocalImpl(String src, String localDest) throws Exception {
    Files.copy(new File(src).toPath(), new File(localDest).toPath());
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

  @Override
  protected String getDirChecksumImpl(String dirPath) throws Exception {
    File file = new File(dirPath);
    if (file.isDirectory()) {
      return getMd5(getFileList(file));
    } else {
      /*
       * if a file, just return md5 of that file; {@link #getFileListRecurisve(File)} does not treat
       * a single file
       */
      return getMd5(getFileHash(file));
    }
  }

  private String getFileList(File pFile) {

    TreeSet<String> fileList = getFileListRecursive(pFile);

    return StringUtils.join(fileList, "\n");
  }

  private TreeSet<String> getFileListRecursive(File pFile) {
    TreeSet<String> fileList = new TreeSet<String>();

    if (!pFile.isDirectory())
      return fileList;

    for (File file : pFile.listFiles()) {
      if (file.isDirectory()) {
        fileList.addAll(getFileListRecursive(file));
      } else {
        fileList.add(getFileHash(file));
      }
    }
    return fileList;
  }

  /**
   * A custom hash function for a given file.
   * 
   * @param file
   * @return a string of hashed value
   */
  private String getFileHash(File file) {
    String permissionHash =
        Boolean.toString(file.canRead() && file.canWrite() && file.canExecute());
    String size = Long.toString(file.length());
    String modTime = Long.toString(file.lastModified());
    String path = file.getAbsolutePath();
    return permissionHash + ";" + size + ";" + modTime + ";" + path;
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
