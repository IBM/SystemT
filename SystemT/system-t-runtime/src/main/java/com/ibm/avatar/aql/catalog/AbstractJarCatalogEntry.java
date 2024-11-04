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

import java.io.IOException;
import java.util.ArrayList;

import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.ParseException;

/**
 * Base class for the different types of catalog entries that represent jar files embedded inside
 * the extractor.
 */
public abstract class AbstractJarCatalogEntry extends CatalogEntry {

  /**
   * Main constructor to be called by subclasses.
   * 
   * @param name fully qualified identifier of the jar file; i.e. module name, plus the path to the
   *        jar file from the root of the source directory of the original AQL module.
   */
  protected AbstractJarCatalogEntry(String name) {
    super(name);
  }

  /**
   * @param parentClassLoader fallback ClassLoader (usually the system classloader) for loading
   *        classes outside the jar
   * @return a classloader that loads classes from this catalog entry's underlying jar first, then
   *         falls back on the parent classloader if the jar does not contain a given class or
   *         resource
   */
  public abstract ClassLoader makeClassLoader(ClassLoader parentClassLoader);

  @Override
  public boolean getIsView() {
    throw new FatalInternalError("This function should never be called");
  }

  @Override
  public boolean getIsExternal() {
    throw new FatalInternalError("This function should never be called");
  }

  @Override
  public boolean getIsDetag() {
    throw new FatalInternalError("This function should never be called");
  }

  @Override
  public ArrayList<String> getColNames() throws ParseException {
    throw new FatalInternalError("This function should never be called");
  }

  /**
   * This method is used only during serialization of jar files to AOG and should be removed
   * (replaced by streaming code) when we switch to storing jar files directly inside the TAM.
   * 
   * @return the binary contents of the jar file
   * @throws IOException if there is an error fetching the bytes from disk
   */
  public abstract byte[] getJarBytes() throws IOException;


}
