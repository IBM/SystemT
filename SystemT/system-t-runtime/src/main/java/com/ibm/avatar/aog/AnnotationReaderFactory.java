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

/**
 * Abstract base class for objects that produce ADI input operators for reading existing
 * annotations. By default, this class places a Project after the output operator to project down to
 * just the annotations. Derived classes that do not want this behavior can override makeOp().
 * 
 */
public abstract class AnnotationReaderFactory {

  /**
   * External interface for the factory.
   * 
   * @param docsrc Teed output of a DocScan operator that will produce the documents.
   * @param nameStr string that identifies the type of annotation being read.
   */
  public Operator makeOp(Operator docsrc, String nameStr) throws ParseException {

    try {
      // Call the subclass's factory method.
      Operator getAnnot = makeOpInternal(docsrc, nameStr);

      return getAnnot;

    } catch (Exception e) {
      // Convert to a more specific exception.
      throw new RuntimeException(String.format("%s thrown while generating output for %s",
          e.getClass().getName(), nameStr), e);
    }
  }

  /**
   * Internal hook for operator creation.
   * 
   * @param docsrc Teed output of a DocScan operator that will produce the documents.
   * @param nameStr string that identifies the type of annotation being read.
   * @return
   * @throws Exception
   */
  protected abstract Operator makeOpInternal(Operator docsrc, String nameStr) throws Exception;

}
