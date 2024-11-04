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
package com.ibm.wcs.annotationservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.ibm.wcs.annotationservice.exceptions.AnnotationServiceException;
import com.ibm.wcs.annotationservice.exceptions.JSONException;

/**
 * Various utilities for processing JSON data.
 * 
 */
public class JsonUtils {

  /**
   * Process the value of a field in the annotator bundle configuration that is expected to be of
   * type JSON String.
   * 
   * @param fieldName the name of the field we are processing, for error reporting purposes
   * @param jsonVal the input JSON value of expected type JSON String
   * @param context name of the input object where the input value comes from, for error messages
   * @param required whether the value is required to be non-null
   * @return the String value of the input JSON value
   * @throws AnnotationServiceException if the input JSON value is not of the expected type, or if
   *         the value is required but received a null value
   */
  public static String processJSONString(String fieldName, JsonNode jsonVal, String context,
      boolean required) throws AnnotationServiceException {

    if (true == required && null == jsonVal)
      throw new AnnotationServiceException(
          "Required field '%s' of %s is null; provide a non-null value for this field", fieldName,
          context);

    // Field is not required; if null, just return null
    if (null == jsonVal)
      return null;

    if (!(jsonVal.isTextual()))
      throw new AnnotationServiceException(
          "Required field '%s' of %s is of type %s instead of String; provide a String value for this field",
          fieldName, context, jsonVal.getClass().getName());

    return jsonVal.textValue();
  }

  /**
   * Process the value of a field in the annotator bundle configuration that is expected to be of
   * type JSON Boolean.
   * 
   * @param fieldName the name of the field we are processing, for error reporting purposes
   * @param jsonVal the input JSON value of expected type JSON Boolean
   * @param context name of the input object where the input value comes from, for error messages
   * @param required whether the value is required to be non-null
   * @return the String value of the input JSON value
   * @throws AnnotationServiceException if the input JSON value is not of the expected type, or if
   *         the value is required but received a null value
   */
  @Deprecated
  public static Boolean processJSONBoolean(String fieldName, Object jsonVal, String context,
      boolean required) throws AnnotationServiceException {

    if (true == required && null == jsonVal)
      throw new AnnotationServiceException(
          "Required field '%s' of %s is null; provide a non-null value for this field", fieldName,
          context);

    // Field is not required; if null, just return null
    if (null == jsonVal)
      return null;

    if (!(jsonVal instanceof Boolean))
      throw new AnnotationServiceException(
          "Required field '%s' of %s is of type %s instead of Boolean; provide a Boolean value for this field",
          fieldName, context, jsonVal.getClass().getName());

    return (Boolean) jsonVal;
  }

  /**
   * Fetch a value from a record, throwing an exception if the value is not found
   * 
   * @param record record to search for the key
   * @param key key to search for
   * @return value at the present key
   * @throws JSONException if the value is not found
   */
  public static JsonNode getVal(JsonNode record, String key) throws AnnotationServiceException {
    if (null == record) {
      throw new AnnotationServiceException(
          String.format("Attempted to treat a null pointer as a JsonRecord and read the '%s' field",
              key.toString()));
    }

    JsonNode val = (JsonNode) record.get(key);
    if (val == null) {
      throw new JSONException.MissingField(record, key);
    }

    return val;
  }

  /**
   * Cast a JSON value of unknown type to a string, throwing an exception if the value is of the
   * wrong type.
   * 
   * @param json the input JSON value
   * @param fieldName the name of the field we are processing, for error reporting purposes
   * @param context name of the input object where the input value comes from, for error messages
   */
  public static String castToString(JsonNode json, String fieldName, String context)
      throws JSONException {
    if (false == (json instanceof TextNode)) {
      throw new JSONException.WrongType(json, String.format("%s of %s", fieldName, context),
          "JSON string");
    }
    return json.asText();
  }

  /**
   * Cast a JSON value of unknown type to a boolean, throwing an exception if the value is of the
   * wrong type.
   * 
   * @param json the input JSON value
   * @param fieldName the name of the field we are processing, for error reporting purposes
   * @param context name of the input object where the input value comes from, for error messages
   */
  public static Boolean castToBoolean(Object json, String fieldName, String context)
      throws JSONException {
    if (false == (json instanceof Boolean)) {
      throw new JSONException.WrongType(json, String.format("%s of %s", fieldName, context),
          "Boolean");
    }
    return (Boolean) json;
  }

  /**
   * Cast a JSON value of unknown type to a record, throwing an exception if the value is of the
   * wrong type.
   * 
   * @param json the input JSON value
   * @param fieldName the name of the field we are processing, for error reporting purposes
   * @param context name of the input object where the input value comes from, for error messages
   */
  public static ObjectNode castToRecord(Object json, String fieldName, String context)
      throws JSONException {
    if (false == (json instanceof ObjectNode)) {
      throw new JSONException.WrongType(json, String.format("%s of %s", fieldName, context),
          "JSON record");
    }
    return (ObjectNode) json;
  }

  /**
   * Shorthand for pulling an array value out of record, checking whether it exists.
   * 
   * @throws AnnotationServiceException
   */
  public static ArrayNode getArrayVal(JsonNode record, String key)
      throws AnnotationServiceException {
    Object jval = getVal(record, key);

    if (false == (jval instanceof ArrayNode)) {
      throw new JSONException.WrongType(jval, key, "JSON array");
    }

    return (ArrayNode) jval;
  }

}
