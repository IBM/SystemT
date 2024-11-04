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
package com.ibm.avatar.api.exceptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.ibm.avatar.algebra.datamodel.Pair;

/**
 * Notifies the user about circular dependencies among the source modules to compile. There is an
 * additional method {@link #getErrorLocations()}, which return the detailed error location of the
 * import statements involved in circular dependencies.
 * 
 */
public class CircularDependencyException extends TextAnalyticsException {
  private static final long serialVersionUID = -6177143937430252981L;

  /**
   * Constructor to create an instance of {@link CircularDependencyException} exception.
   * 
   * @param modulesInTheCycleVsErrorMarker Map of circularly dependent source modules. The key of
   *        the map is source module name, which is part of the cycle and the value is the list of
   *        location of the import statements, importing the next module in the cycle. Location is a
   *        {@link Pair} of {@link String} and {@link Integer}, where string contains the absolute
   *        path to the aql file containing the import statement and the integer points to the line
   *        number of the import statement.
   */
  public CircularDependencyException(
      Map<String, List<Pair<String, Integer>>> modulesInTheCycleVsErrorMarker) {
    super(formatErrorMessage(modulesInTheCycleVsErrorMarker));
    this.modulesInTheCycleVsErrorMarker = modulesInTheCycleVsErrorMarker;
  }

  /**
   * Constructor to create an instance of {@link CircularDependencyException} exception when
   * constructing a module set.
   * 
   * @param currentModule the module in the module set with a circular dependency
   * @param moduleStack the stack of dependent modules (i.e. [A, B, C, A])
   */
  public CircularDependencyException(String currentModule, Stack<String> moduleStack) {
    super(
        "Cyclic dependency found while combining modules.  Module '%s' depends upon itself : %s%s.",
        currentModule, currentModule, printStack(moduleStack));
  }

  private static String printStack(Stack<String> moduleStack) {
    StringBuilder errMsg = new StringBuilder();

    while (false == moduleStack.isEmpty()) {
      errMsg.append(" -> ");
      errMsg.append(moduleStack.pop());
    }

    return errMsg.toString();
  }

  /**
   * Map of circularly dependent source modules. The key of the map is source module name, which is
   * part of the cycle and the value is the list of location of the import statements, importing the
   * next module in the cycle. Location is a {@link Pair} of {@link String} and {@link Integer},
   * where string contains the absolute path to the aql file containing the import statement and the
   * integer points to the line number of the import statement.
   */
  private Map<String, List<Pair<String, Integer>>> modulesInTheCycleVsErrorMarker;

  /**
   * Returns the map of circularly dependent source modules. The key of the map is source module
   * name, which is part of the cycle and the value is the list of location of the import
   * statements, importing the next module in the cycle. Location is a {@link Pair} of
   * {@link String} and {@link Integer}, where string contains the absolute path to the aql file
   * containing the import statement and the integer points to the line number of the import
   * statement.
   * 
   * @return the map of circularly dependent source modules
   */
  public Map<String, List<Pair<String, Integer>>> getErrorLocations() {
    return this.modulesInTheCycleVsErrorMarker;
  }

  /**
   * Helper method to prepare detailed error message from the specified map of module name and error
   * location.
   * 
   * @param modulesInTheCyleVsErrorMarker Map of circularly dependent source module. The key of the
   *        map is source module name, which is part of the cycle and the value is the list of
   *        location of the import statements, importing the next module in the cycle. Location is a
   *        {@link Pair} of {@link String} and {@link Integer}, where string contains the absolute
   *        path to the aql file containing the import statement and the integer points to the line
   *        number of the import statement
   * @return a formatted error message string
   */
  private static String formatErrorMessage(
      Map<String, List<Pair<String, Integer>>> modulesInTheCyleVsErrorMarker) {
    List<String> modulesInTheCycle = new ArrayList<String>(modulesInTheCyleVsErrorMarker.keySet());
    StringBuilder sb = new StringBuilder();
    sb.append(String.format(
        "A circular dependency is detected between the following modules to compile: %s.",
        modulesInTheCycle));

    int cycleLength = modulesInTheCycle.size();

    for (int i = 0; i < cycleLength; i++) {
      String currentModule = modulesInTheCycle.get(i);
      // Module depends on the following module in the list; last module depends on the first module
      // in the list
      String dependentModule = modulesInTheCycle.get(i == cycleLength - 1 ? 0 : i + 1);

      sb.append(String.format(
          " Module '%s' depends on module '%s' as specified by the import statement(s) ",
          currentModule, dependentModule));

      List<Pair<String, Integer>> importsInvolvedInCycle =
          modulesInTheCyleVsErrorMarker.get(currentModule);

      int noOfImports = importsInvolvedInCycle.size();

      for (int j = 0; j < noOfImports; j++) {
        Pair<String, Integer> errorLoc = importsInvolvedInCycle.get(j);
        sb.append(
            String.format("in line number %d of the file '%s'", errorLoc.second, errorLoc.first));

        // No comma after the last import statement in the list
        if (j != noOfImports - 1)
          sb.append(", ");
      }

      // No semi-colon after the last module in the cycle
      if (i != cycleLength - 1)
        sb.append("; ");
      else
        sb.append(".");
    }

    return sb.toString();
  }
}
