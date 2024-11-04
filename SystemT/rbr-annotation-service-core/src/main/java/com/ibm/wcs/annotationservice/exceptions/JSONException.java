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
package com.ibm.wcs.annotationservice.exceptions;

import com.fasterxml.jackson.databind.JsonNode;


@SuppressWarnings("serial")
public class JSONException extends AnnotationServiceException {

  /**
   * Version ID for serialization
   */
  private static final long serialVersionUID = 1L;

  private JSONException(String fmt, Object... args) {
    super(fmt, args);
  }

  private JSONException(Throwable cause, String fmt, Object... args) {
    super(cause, fmt, args);
  }

  /*
   * Inner classes for the different types of JSON exception
   */

  public static class WrongType extends JSONException {
    public WrongType(Object value, String description, String expectedType) {
      super("Expected %s value for %s, but got %s", expectedType, description, value);
    }
  }

  public static class MissingField extends JSONException {
    public MissingField(JsonNode record, String key) {
      super("Record %s missing expected field '%s'", record, key);
    }
  }

  public static class UnexpectedField extends JSONException {
    public UnexpectedField(JsonNode record, String key) {
      super("Record %s has unexpected field '%s'", record, key);
    }
  }

  public static class CantParse extends JSONException {
    public CantParse(Object value, String expectedType) {
      super("Cannot parse value %s as a %s", value, expectedType);
    }

    public CantParse(Throwable cause, Object value, String expectedType) {
      super(cause, "Cannot parse value %s as a %s", value, expectedType);
    }
  }
}
