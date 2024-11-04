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
package com.ibm.wcs.annotationservice;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.jsonSchema.types.ObjectSchema;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.api.tam.ViewMetadata;
import com.ibm.wcs.annotationservice.exceptions.AnnotationServiceException;

/**
 * Interface encapsulating the major functionality of an annotator runtime supported by the
 * Annotator Module: the ability to execute the annotator on a given document, and the ability to
 * interrupt execution; all instances of annotator runtimes supported by the Annotation Module
 * implement this interface
 *
 */
public interface AnnotatorWrapper {

  /**
   * Annotate the input document with the given language.
   *
   * @param document the input document as a JSON record; the input document is ontology mapped;
   *        this method assumes the input record contains fields containing actual content, but also
   *        fields containing metadata (for example, to indicate the language or the content type of
   *        the document); this method does not validate that the record conforms to the ontology;
   *        instead, this method queries the input schema of the annotator and passes through to the
   *        annotator the values of the expected fields from the input document record; for example,
   *        in the case of a SystemT annotator, this method queries the schema of the view Document;
   *        if a field expected by the annotator does not exist in the input record, or the field
   *        exists but has an incompatible type, this method throws an exception; in the case of
   *        UIMA annotators, the current implementation assumes that the input document contains a
   *        field named {@link AnnotationServiceConstants#DOCUMENT_TEXT_FIELD_NAME} of type String
   *        and will throw an exception otherwise
   * @param langCode 2-letter language code of the input document content
   * @return a JSON record with as many fields as output type names in the given annotator bundle
   *         config, where each element is a non-null JSON array (potentially empty) containing the
   *         annotator results of that type; the value of the second field is a non-null JSON record
   *         containing information about: input document size (in characters), running time (in
   *         milliseconds), number of annotations output, whether the annotation was interrupted due
   *         to running time exceeding the given timeout, and exception information (if any)
   * @throws AnnotationServiceException if any exception encountered during the execution
   */
  public abstract ObjectNode invoke(JsonNode document, String langCode)
      throws AnnotationServiceException;

  /**
   * Interrupts the annotation process, i.e., the call to {@link #invoke(JsonNode, String)}
   *
   * @throws AnnotationServiceException if any exception encountered during the execution
   */
  public abstract void interrupt() throws AnnotationServiceException;

  /**
   * Whether the annotator runtime supports interruption; annotators that don't support interrupt
   * are never interrupted (even though the user might have requested interruption)
   *
   * @return true if the annotator runtime supports interruption; false otherwise
   */
  public abstract boolean supportsInterrupt();

  /**
   * Closes the annotator and clear the resource allocated for the annotator
   *
   * @throws AnnotationServiceException
   */
  public abstract void close() throws AnnotationServiceException;

  /**
   * Computes the output schema of this annotator
   *
   * @param inputSchema a list of field names already defined in the input schema; will skip
   *        processing for field names of output schema if it exists in the input schema
   * @return a JSON Schema of output
   * @throws Exception
   * @see AnnotatorWrapperSystemT#getOutputSchema(List)
   * @see AnnotatorWrapperUIMA#getOutputSchema(List)
   */
  ObjectSchema getOutputSchema(List<String> inputSchema) throws Exception;

  /**
   * Computes the short output schema of this annotator (see
   * {@link AnnotatorWrapperSystemT#getShortOutputViewSchema(ViewMetadata)}
   *
   * @param inputSchema a list of field names already defined in the input schema; will skip
   *        processing for field names of output schema if it exists in the input schema
   * @return an ObjectNode of output
   * @throws Exception
   * @see AnnotatorWrapperSystemT#getShortOutputViewSchema(ViewMetadata)
   * @return for an output view <code>
   * 	    MyView: [{
   * 	      "spanList": [{
   * 	          "begin": 0,
   * 	          "end": 1
   * 	      },{
   * 	          "begin": 2,
   * 	          "end": 3
   * 	      }],
   * 	      "text": "Abc",
   * 	      "intType": 1,
   * 	      "noneType": null
   * 	    }]
   * 	</code> the equivalent consise schema would be: <code>
   * 	    MyView: {
   * 	        "spanList": [ "Span" ],
   * 	        "text": "Text",
   * 	        "intType": "Integer",
   * 	        "noneType": "Null"
   * 	    }
   * 	</code>
   */
  default ObjectNode getShortOutputViewSchema(List<String> inputSchema) throws Exception {
    return new ObjectMapper().valueToTree(getOutputSchema(inputSchema));
  };

  default void save(String zipFilePath, boolean compileDictionaries) throws Exception {
    throw new AnnotationServiceException("The save method does not support %s annotator.",
        this.getClass().toString());
  };
}
