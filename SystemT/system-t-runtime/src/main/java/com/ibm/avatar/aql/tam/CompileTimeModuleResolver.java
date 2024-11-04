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
package com.ibm.avatar.aql.tam;

import com.ibm.avatar.api.exceptions.ModuleNotFoundException;
import com.ibm.avatar.api.exceptions.VerboseNullPointerException;
import com.ibm.avatar.api.tam.ModuleMetadata;
import com.ibm.avatar.api.tam.ModuleMetadataFactory;

/**
 * Resolves modules by searching within compilationTempDir first. If no modules are found in the
 * temp directory, it searches for modules from the specified modulePath. Presently supports only
 * reading of metadata.
 * 
 */
public class CompileTimeModuleResolver {
  /**
   * Absolute URI of the temporary directory where .tam files are written into during compilation,
   * before they are copied to outputURI. Consider two source input modules A and B, where B depends
   * on A. Assume the module path contains an old version of A.tam. When compiling B, the compiler
   * should read the module metadata of module A from the freshly compiled A.tam instead of from the
   * older version of A.tam found in modulePath.
   */
  protected String compilationTempDir = null;

  /**
   * Initializes the module resolver with temporary directory used during compilation
   * 
   * @param compilationTempDir absolute path of compilation temp directory in URI format
   */
  public CompileTimeModuleResolver(String compilationTempDir) {
    super();
    this.compilationTempDir = compilationTempDir;
  }

  /**
   * If compilationTempDir is set, then reads the metadata from compilationTempDir first. If none
   * found, it searches for the module from modulePath. However, if compilationTempDir is not set,
   * then it attempts to read metadata from modulePath
   * 
   * @param moduleName name of the module whose metadata is requested for
   * @param modulePath A list of file:, hdfs:, or gpfs: URIs separated by semi-colon. Used as a
   *        fallback search path, if the requested module is not found in compilationTempDir
   * @return Metadata of the module requested for
   * @throws Exception
   */
  public ModuleMetadata readMetaData(String moduleName, String modulePath) throws Exception {

    // load from modulePath, if compilationTempDir is not set
    if (null == compilationTempDir) {
      return ModuleMetadataFactory.readMetaData(moduleName, modulePath);
    }

    // attempt to load from compilationTempDir, if it is set to a non-null value
    try {
      ModuleMetadata metadata = ModuleMetadataFactory.readMetaData(moduleName, compilationTempDir);
      return metadata;
    } catch (ModuleNotFoundException mef) {
      // fall back to modulePath if module not found in compilationTempDir
      try {
        return ModuleMetadataFactory.readMetaData(moduleName, modulePath);
      } catch (VerboseNullPointerException vnpe) {
        throw new ModuleNotFoundException(moduleName, modulePath);
      }
    }

  }

}
