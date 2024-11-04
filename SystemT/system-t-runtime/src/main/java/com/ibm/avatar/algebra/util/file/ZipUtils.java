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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utilities to work with zip files
 * 
 */
public class ZipUtils {

  /**
   * Extracts the specified zip file to the specified output directory.
   * 
   * @param zipFile File descriptor of the file to extract
   * @param outDir File descriptor of the directory where the zip file should be extracted
   * @throws IOException If there are any problems in extracting zip file to the specified
   *         directory.
   */
  public static void extractZip(File zipFile, File outDir) throws IOException {
    ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
    try {
      extractZip(zis, outDir);
    } finally {
      if (zis != null) {
        zis.close();
      }
    }
  }

  /**
   * Extracts the contents of specified ZipInputStream file to the specified output directory.
   * 
   * @param zis ZipInputStream whose contents are to be extracted to the specified output directory
   * @param outDir File descriptor of the directory where the contents of ZipInputStream should be
   *        extracted
   * @throws IOException If there are any problems in extracting the contents of ZipInputStream to
   *         the specified directory.
   */
  public static void extractZip(ZipInputStream zis, File outDir) throws IOException {
    ZipEntry entry = null;

    while ((entry = zis.getNextEntry()) != null) {
      if (entry.isDirectory()) {
        mkdirs(outDir, entry.getName());
      } else {
        extractFile(zis, outDir, entry.getName());
      }
    }
  }

  /**
   * Creates a directory of specified path under specified parent directory
   * 
   * @param parentDir base directory where the child dir should be created
   * @param path path of the child directory relative to the parentDir
   */
  private static void mkdirs(File parentDir, String path) {
    File dir = new File(parentDir, path);
    if (!dir.exists())
      dir.mkdirs();
  }

  /**
   * Extracts the file identified by zipEntryName from the specified ZipInputStream into the output
   * directory.
   * 
   * @param zis ZipInputStream whose contents are extracted to output directory
   * @param outDir File descriptor of the directory where the contents of ZipInputStream are to be
   *        extracted
   * @param zipEntryName Name of the zip entry being extracted
   * @throws IOException If there are any problems in extracting the zip entry
   */
  private static void extractFile(ZipInputStream zis, File outDir, String zipEntryName)
      throws IOException {
    // create parent dir, if not already created. This could happen when the child zip entry appears
    // before the parent
    // in the ZipInputStream
    String parentDir = getDirName(zipEntryName);
    if (parentDir != null) {
      mkdirs(outDir, parentDir);
    }

    // copy file to parent dir
    byte[] buffer = new byte[1024];
    BufferedOutputStream out =
        new BufferedOutputStream(new FileOutputStream(new File(outDir, zipEntryName)));
    try {
      int nread = -1;
      while ((nread = zis.read(buffer)) > 0) {
        out.write(buffer, 0, nread);
      }
    } finally {
      out.close();
    }

  }

  /**
   * Returns the directory portion of the path
   * 
   * @param path a String containing relative path to a resource
   * @return directory portion of the path
   */
  private static String getDirName(String path) {
    int idx = path.lastIndexOf(File.separatorChar);
    return (idx == -1) ? null : path.substring(0, idx);
  }
}
