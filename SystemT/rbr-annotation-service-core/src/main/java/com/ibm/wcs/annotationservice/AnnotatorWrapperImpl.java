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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.factories.JsonSchemaFactory;
import com.fasterxml.jackson.module.jsonSchema.types.ArraySchema;
import com.fasterxml.jackson.module.jsonSchema.types.ObjectSchema;
import com.ibm.wcs.annotationservice.exceptions.AnnotationServiceException;
import com.ibm.wcs.annotationservice.models.json.AnnotatorBundleConfig;

/**
 * Super class encapsulating common functionality across different implementations of the Annotator
 * Module runtime functionality; all implementations supported by the Annotation Modules (SystemT,
 * UIMA, SIRE) inherit from this class.
 * 
 */
public abstract class AnnotatorWrapperImpl implements AnnotatorWrapper {
  /** The annotator bundle configuration */
  protected AnnotatorBundleConfig annotateConfig;

  /** Used by Jackson for JSON binding */
  protected static final ObjectMapper MAPPER = new ObjectMapper();
  /** Used by Jackson Schema module for JSON schema creation */
  protected static final JsonSchemaFactory JSFACTORY = new JsonSchemaFactory();

  /**
   * @param annotBundleCfg the annotator bundle configuration
   */
  protected AnnotatorWrapperImpl(AnnotatorBundleConfig annotBundleCfg)
      throws AnnotationServiceException {
    // already validated during deserialization
    annotateConfig = annotBundleCfg;
  }

  /**
   * Default implementation returns false; subclasses may override the default behavior.
   */
  @Override
  public boolean supportsInterrupt() {
    return false;
  }

  @Override
  public void close() throws AnnotationServiceException {
    // do nothing by default
  }

  /**
   * Create a schema for span type.
   *
   * @return JSON representation of the span as follows:
   *         <ul>
   *         <li><i>begin</i> (mandatory): an Integer representing the span begin offset
   *         <li><i>end</i> (mandatory): an Integer representing the span end offset
   *         <li><i>docref</i>: a String representing the name of the field in the input document
   *         record that the span is over; if no such field exists (i.e., the span is over a text
   *         object generated by the annotator), this method throws an exception; this field is
   *         optional and it is serialized only for spans that are not over the default document
   *         fields {@link AnnotationServiceConstants#DOCUMENT_CONTENT_FIELD_NAME} or
   *         {@link AnnotationServiceConstants#DOCUMENT_TEXT_FIELD_NAME}
   *         </ul>
   */
  static public JsonSchema createSpanSchema() {
    ObjectSchema spanSchema = JSFACTORY.objectSchema();
    spanSchema.putProperty(AnnotationServiceConstants.SPAN_BEGIN_FIELD_NAME,
        JSFACTORY.integerSchema());
    spanSchema.putProperty(AnnotationServiceConstants.SPAN_END_FIELD_NAME,
        JSFACTORY.integerSchema());
    spanSchema.putProperty(AnnotationServiceConstants.SPAN_DOCREF_FIELD_NAME,
        JSFACTORY.stringSchema());
    return spanSchema;
  }


  /**
   * @return hard-coded instrumentation info schema
   */
  static public ObjectSchema getInstrumentationInfoSchema() {
    ObjectSchema instrumentationInfoSchema = JSFACTORY.objectSchema();
    ObjectSchema instrumentationInfoValueSchema = JSFACTORY.objectSchema();
    ObjectSchema annotatorSchema = JSFACTORY.objectSchema();
    ObjectSchema numAnnotationsPerTypeItemSchema = JSFACTORY.objectSchema();
    ArraySchema numAnnotationsPerTypeSchema = JSFACTORY.arraySchema();

    annotatorSchema.putProperty(AnnotationServiceConstants.ANNOTATOR_KEY_FIELD_NAME,
        JSFACTORY.stringSchema());
    annotatorSchema.putProperty(AnnotationServiceConstants.ANNOTATOR_VERSION_FIELD_NAME,
        JSFACTORY.stringSchema());

    instrumentationInfoValueSchema.putProperty(AnnotationServiceConstants.ANNOTATOR_FIELD_NAME,
        annotatorSchema);
    instrumentationInfoValueSchema.putProperty(AnnotationServiceConstants.RUNNING_TIME_FIELD_NAME,
        JSFACTORY.integerSchema());
    instrumentationInfoValueSchema.putProperty(AnnotationServiceConstants.DOCUMENT_SIZE_FIELD_NAME,
        JSFACTORY.integerSchema());
    instrumentationInfoValueSchema.putProperty(
        AnnotationServiceConstants.ANNOTATIONS_SIZE_TOTAL_FIELD_NAME, JSFACTORY.integerSchema());

    numAnnotationsPerTypeItemSchema.putProperty(
        AnnotationServiceConstants.ANNOTATION_TYPE_FIELD_NAME, JSFACTORY.stringSchema());
    numAnnotationsPerTypeItemSchema.putProperty(
        AnnotationServiceConstants.ANNOTATIONS_SIZE_FIELD_NAME, JSFACTORY.integerSchema());
    numAnnotationsPerTypeSchema.setItemsSchema(numAnnotationsPerTypeItemSchema);
    instrumentationInfoValueSchema.putProperty(
        AnnotationServiceConstants.ANNOTATIONS_SIZE_PER_TYPE_FIELD_NAME,
        numAnnotationsPerTypeSchema);

    instrumentationInfoValueSchema.putProperty(AnnotationServiceConstants.INTERRUPTED_FIELD_NAME,
        JSFACTORY.booleanSchema());
    instrumentationInfoValueSchema.putProperty(AnnotationServiceConstants.SUCCESS_FIELD_NAME,
        JSFACTORY.booleanSchema());
    instrumentationInfoValueSchema.putProperty(
        AnnotationServiceConstants.EXCEPTION_MESSAGE_FIELD_NAME, JSFACTORY.stringSchema());
    instrumentationInfoValueSchema.putProperty(
        AnnotationServiceConstants.EXCEPTION_STACKTRACE_FIELD_NAME, JSFACTORY.stringSchema());

    instrumentationInfoSchema.putProperty(
        AnnotationServiceConstants.INSTRUMENTATION_INFO_FIELD_NAME, instrumentationInfoValueSchema);
    return instrumentationInfoSchema;
  }


}
