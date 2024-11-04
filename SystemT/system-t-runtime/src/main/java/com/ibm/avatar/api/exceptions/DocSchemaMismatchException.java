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

/**
 * Exception class to notify that docschema of given modules is not compatible with each other.
 * 
 */
public class DocSchemaMismatchException extends TextAnalyticsException {

  private static final long serialVersionUID = -1616004708342011536L;

  /**
   * Name of the field whose type does not match with the expected type
   */
  protected String fieldName;

  /**
   * Names of modules whose docSchemas are compared for compatibility. <br/>
   */
  protected String module1;
  protected String module2;

  /**
   * Type of the fieldName in module1;
   */
  protected String fieldType1;

  /**
   * Type of the fieldName in module2;
   */
  protected String fieldType2;

  /**
   * Constructs an exception object by providing details of the field whose type mismatches between
   * module1 and module2
   * 
   * @param fieldName
   * @param module1
   * @param module2
   * @param fieldType1
   * @param fieldType2
   */
  public DocSchemaMismatchException(String fieldName, String module1, String module2,
      String fieldType1, String fieldType2) {
    super(formatMessage(fieldName, module1, module2, fieldType1, fieldType2));
    this.fieldName = fieldName;
    this.fieldType1 = fieldType1;
    this.module1 = module1;
    this.module2 = module2;
    this.fieldType2 = fieldType2;
  }

  /**
   * Constructs an exception object with the given message. This is used to report exceptions caused
   * due to reasons other than field type mismatches. For instance, the compared schema is null.
   * 
   * @param message
   */
  public DocSchemaMismatchException(String message) {
    super(message);
  }

  /**
   * Prepares a message with given field name and conflicting field types. The module information is
   * known by the caller and is passed as parameter to the method.
   * 
   * @param fieldName Name of the field whose type mismatches between two modules that are stitched
   *        together
   * @param module1 name of the first module
   * @param module2 name of the second module
   * @param fieldType1 Type of the specified filed in first module
   * @param fieldType2 Type of the specified filed in second module
   * @return
   */
  private static String formatMessage(String fieldName, String module1, String module2,
      String fieldType1, String fieldType2) {
    return String.format(
        "The type of field '%s' of view Document is incompatible between the modules that are loaded. The type of the specified field in module [%s] is '%s', whereas its type in module(s) %s is '%s'. Ensure that the field types match.",
        fieldName, module1, fieldType1, module2, fieldType2);
  }

}
