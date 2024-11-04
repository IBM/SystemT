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

import java.util.ArrayList;
import java.util.List;

import com.ibm.avatar.aql.compiler.CompilerWarning;

/**
 * This class implements the {@link CompilationSummary} interface.
 * 
 */
public class CompilationSummaryImpl implements CompilationSummary {
  /**
   * Associated compilation request.
   */
  private final CompileAQLParams compileParam;

  /**
   * Name of the modules compiled.
   */
  private final List<String> modulesCompiled;

  /**
   * Total number of view compiled for the associated request.
   */
  private int noOfViewsCompiled;

  /**
   * List of warning encountered during compilation.
   */
  private final List<CompilerWarning> compilationWarning;

  public CompilationSummaryImpl(CompileAQLParams compilationRequest) {
    // System.out.println("Creating summary object");
    this.modulesCompiled = new ArrayList<String>();
    this.compilationWarning = new ArrayList<CompilerWarning>();
    this.noOfViewsCompiled = 0;
    this.compileParam = compilationRequest;
  }

  @Override
  public CompileAQLParams getCompilationRequest() {
    return this.compileParam;
  }

  @Override
  public List<String> getCompiledModules() {
    return this.modulesCompiled;
  }

  @Override
  public int getNumberOfViewsCompiled() {
    return this.noOfViewsCompiled;
  }

  @Override
  public List<CompilerWarning> getCompilerWarning() {
    return this.compilationWarning;
  }

  @Override
  public void updateSummary(String moduleName, int noViewsCompiled,
      List<CompilerWarning> moreWarnings) {
    // System.out.printf("updateSummary: %s, %d, %s\n", moduleName, noViewsCompiled, moreWarnings);
    modulesCompiled.add(moduleName);
    this.noOfViewsCompiled += noViewsCompiled;
    this.compilationWarning.addAll(moreWarnings);
  }
}
