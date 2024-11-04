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
package com.ibm.avatar.aql.catalog;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.AQLParseTreeNode;

/**
 * Catalog entry for a jar file that is on the local filesystem.
 * <p>
 * Currently this type of catalog entry is used only during AQL compilation, since jar files used
 * during operator graph loading are always stored inside the TAM files.
 * 
 */
public class OnDiskJarCatalogEntry extends AbstractJarCatalogEntry {

  private File jarFileLoc;

  /**
   * Main contructor.
   * 
   * @param name name of the jar file within the current module; currently we use the relative path
   *        in the external_name clause as the jar file name.
   * @param moduleRoot location of the root of the module
   */
  protected OnDiskJarCatalogEntry(String name, File moduleRoot) {
    super(name);

    jarFileLoc = new File(moduleRoot, name);
  }

  @Override
  public ClassLoader makeClassLoader(ClassLoader parentClassLoader) {
    try {
      return new URLClassLoader(new URL[] {jarFileLoc.toURI().toURL()}, parentClassLoader);
    } catch (MalformedURLException e) {
      throw new FatalInternalError(
          "Local filesystem location %s couldn't be turned into a URL." + "  Should never happen.",
          jarFileLoc);
    }
  }

  @Override
  public byte[] getJarBytes() throws IOException {
    return FileUtils.fileToBytes(jarFileLoc);
  }

  @Override
  protected AQLParseTreeNode getNode() {
    // return null as there is no parse tree node associated with this catalog entry.
    return null;
  }
}
