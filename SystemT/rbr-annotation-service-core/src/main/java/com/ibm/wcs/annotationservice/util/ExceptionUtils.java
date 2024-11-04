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
/**
 * 
 */
package com.ibm.wcs.annotationservice.util;

import java.io.PrintWriter;
import java.io.StringWriter;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.wcs.annotationservice.AnnotationServiceConstants;

/**
 * Various helper methods for serializing exceptions to JSON.
 * 
 */
public class ExceptionUtils {

  /**
   * Helper method to record an exception in the instrumentation info; records the error message and
   * the stack trace
   * 
   * @param e exception to record in the instrumentation info portion of the document record
   */
  public static ObjectNode serializeException(Throwable e) {

    ObjectNode errorInfo = JsonNodeFactory.instance.objectNode();
    errorInfo.put(AnnotationServiceConstants.EXCEPTION_MESSAGE_FIELD_NAME, e.getMessage());
    StringWriter stackTrace = new StringWriter();
    e.printStackTrace(new PrintWriter(stackTrace));
    errorInfo.put(AnnotationServiceConstants.EXCEPTION_STACKTRACE_FIELD_NAME,
        stackTrace.toString());
    return errorInfo;
  }

  /**
   * Helper method to exclude input document content if any, within the current exception's
   * message.<br/>
   * Removal logic is shallow, and is expected to break should system-t-runtime decide to format
   * input document content in another way
   * 
   * @param e {@link java.lang.Throwable} instance of current exception
   * @return {@link java.lang.String} instance of current exception's message without input document
   *         content
   */
  public static String removeDocumentContent(Throwable e) {
    // Sample exception message: "Exception on input Document tuple ['docFileName',
    // '<docFileContent>'(2 fields)]"
    // Return exception message: "Exception on input Document tuple"
    if (e.getMessage().startsWith("Exception on input Document tuple"))
      return e.getMessage().replace(
          e.getMessage().substring(e.getMessage().indexOf("["), e.getMessage().length()), "");
    return e.getMessage();
  }
}
