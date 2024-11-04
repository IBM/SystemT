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
package com.ibm.avatar.api;

import java.util.List;

import com.ibm.avatar.aql.compiler.CompilerWarning;

/**
 * This class contains the summary of AQL compilation process. As part of the summary, it provides
 * the following information: names of the modules compiled, total number of views compiled across
 * all the modules and the list of warnings encountered during compilation. This class also contains
 * the original compilation request, for reference.
 *
 */
public interface CompilationSummary {
  /**
   * Returns the original compilation request, that is {@link com.ibm.avatar.api.CompileAQLParams}
   * object.
   *
   * @return the original compilation request, that is {@link com.ibm.avatar.api.CompileAQLParams}
   *         object
   */
  public CompileAQLParams getCompilationRequest();

  /**
   * Returns the name of modules successfully compiled.
   *
   * @return name of modules successfully compiled
   */
  public List<String> getCompiledModules();

  /**
   * Returns the total number of views compiled from the requested modules to be compiled.
   *
   * @return the total number of views compiled from the requested modules to be compiled
   */
  public int getNumberOfViewsCompiled();

  /**
   * Returns warnings encountered across all the compiled modules.
   *
   * @return warnings encountered across all the compiled modules
   */
  public List<CompilerWarning> getCompilerWarning();

  /**
   * This method updates the global <code>CompilationSummary</code> object with summary of
   * compilation of module represented by the <code>moduleName</code> parameter.
   *
   * @param compiledModuleName name of the module for which summary is to be updated
   * @param noOfViewsCompiled number of views compiled from the module represented by the
   *        <code>moduleName</code> parameter
   * @param warnings compilation warnings encountered while compiling the module represented by the
   *        <code>moduleName</code> parameter
   */
  public void updateSummary(String compiledModuleName, int noOfViewsCompiled,
      List<CompilerWarning> warnings);

}
