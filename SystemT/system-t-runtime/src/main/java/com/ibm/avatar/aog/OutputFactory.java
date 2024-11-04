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
package com.ibm.avatar.aog;

import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.relational.Project;

/**
 * Abstract base class for callbacks that produce ADI output operators. By default, this class
 * places a Project in front of its inputs as necessary, so that derived classes only need to deal
 * with single-column input. Derived classes that do not want this behavior can override makeOp().
 * 
 */
public abstract class OutputFactory {

  /**
   * External interface for the factory. Also provides automatic renaming of Annotations.
   * 
   * @param child child subtree, with output tuples whose rightmost column is the annotation to be
   *        output.
   * @param nameStr string that identifies the type of annotation being output.
   */
  public Operator makeOp(Operator child, String nameStr) throws ParseException {

    try {
      // Rename the output annotations according to the label provided.
      Project project = new Project(nameStr, null, null, child);

      // Call the subclass's factory method.
      Operator ret = makeOpInternal(project, nameStr);

      // Set up view names so that the profiler will catch the overhead of
      // these output operators.
      project.setViewName(nameStr);
      ret.setViewName(nameStr);

      return ret;

    } catch (Exception e) {
      // Convert to a more specific exception.
      throw new ParseException(
          String.format("Error while generating output for %s: %s", nameStr, e.getMessage()));
    }
  }

  /**
   * Internal hook for operator creation.
   * 
   * @param child child subtree, with Projects inserted as needed to ensure that its output has only
   *        one column.
   * @param nameStr string that identifies the type of annotation being output.
   * @return the newly-created operator
   * @throws Exception
   */
  protected abstract Operator makeOpInternal(Operator child, String nameStr) throws Exception;
}
