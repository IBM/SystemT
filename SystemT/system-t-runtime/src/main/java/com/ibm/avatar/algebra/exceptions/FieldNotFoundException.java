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
package com.ibm.avatar.algebra.exceptions;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TupleSchema;

/**
 * An exception class that indicates that a specified field is not found in TupleSchema
 * 
 */
public class FieldNotFoundException extends RuntimeException {

  String viewName;
  String fieldName;

  private static final long serialVersionUID = 1239987209167163413L;

  /**
   * Main constructor.
   * 
   * @param viewName name of the view for which the field lookup failed
   * @param fieldName name of the (alleged) field within the view that does not exist
   * @param allFields names of the fields that *do* exist in the schema
   * @param allTypes types of the fields referenced in allFields
   */
  public FieldNotFoundException(String viewName, String fieldName, String[] allFields,
      FieldType[] allTypes) {
    super(String.format("Schema of view '%s' does not contain field name '%s'. Schema is %s",
        viewName, fieldName, new TupleSchema(allFields, allTypes)));
    this.viewName = viewName;
    this.fieldName = fieldName;
  }

  /**
   * Constructor for when the view name is not known.
   * 
   * @param fieldName name of the (alleged) field within the view that does not exist
   * @param allFields names of the fields that *do* exist in the schema
   * @param allTypes types of the fields referenced in allFields
   */
  public FieldNotFoundException(String fieldName, String[] allFields, FieldType[] allTypes) {
    super(String.format("Schema %s does not contain field name '%s'.",
        new TupleSchema(allFields, allTypes), fieldName));
    this.viewName = null;
    this.fieldName = fieldName;
  }

  /**
   * @return the viewName
   */
  public String getViewName() {
    return viewName;
  }

  /**
   * @return the field
   */
  public String getFieldName() {
    return fieldName;
  }

}
