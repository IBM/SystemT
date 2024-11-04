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
package com.ibm.wcs.annotationservice;

import java.io.File;

import com.ibm.wcs.annotationservice.models.json.AnnotatorBundleConfig;

/**
 * Various constants used by the Annotation Service.
 * 
 */
public class AnnotationServiceConstants {

  /** Default timeout (in milliseconds) to use whenever no timeout is specified. */
  public static final long DEFAULT_TIMEOUT = 1000000;

  /** Special value to use when invoking the annotation service with no timeout. */
  public static final long NO_TIMEOUT = -1;

  /**
   * Name of the field in the Annotator Bundle Configuration JSON record that indicates the version
   * of the configuration format, e.g., "1.0"
   */
  public static final String VERSION_FIELD_NAME = "version";

  /**
   * Name of the field in the Annotator Bundle Configuration JSON record that indicates the
   * annotator info, including the key and version field; this is also the name under which the
   * annotator info is serialized inside each output annotation, if the
   * {@link AnnotatorBundleConfig#serializeAnnotatorInfo} is set to true.
   */
  public static final String ANNOTATOR_FIELD_NAME = "annotator";
  /**
   * Name of the field in the annotator field of the Annotator Bundle Configuration JSON record that
   * indicates the annotator key
   */
  public static final String ANNOTATOR_KEY_FIELD_NAME = "key";

  /**
   * Name of the field in the annotator field of the Annotator Bundle Configuration JSON record that
   * indicates the annotator version
   */
  public static final String ANNOTATOR_VERSION_FIELD_NAME = "version";

  /**
   * Name of the field in the Annotator Bundle Configuration JSON record that indicates the text
   * analytics runtime for executing this annotator, e.g., SystemT, UIMA, SIRE
   */
  public static final String ANNOTATOR_RUNTIME_FIELD_NAME = "annotatorRuntime";

  /**
   * Name of the field in the Annotator Bundle Configuration JSON record that indicates the types of
   * input text accepted by this annotator, e.g., "text/plain", or "text/html", or "text/plain,
   * text/html" for both
   */
  public static final String ACCEPTED_CONTENT_TYPES_FIELD_NAME = "acceptedContentTypes";

  /**
   * Name of the field in the Annotator Bundle Configuration JSON record that indicates the input
   * types expected by this annotator
   */
  public static final String INPUT_TYPES_FIELD_NAME = "inputTypes";

  /**
   * Name of the field in the Annotator Bundle Configuration JSON record that indicates the output
   * types produced by this annotator
   */
  public static final String OUTPUT_TYPES_FIELD_NAME = "outputTypes";

  /**
   * Name of the field in the Annotator Bundle Configuration JSON record that indicates the location
   * where the annotator bundle has been deployed
   */
  public static final String LOCATION_FIELD_NAME = "location";

  /**
   * Name of the field in the Annotator Bundle Configuration JSON record that indicates whether
   * annotator info, including the key and version field are serialized inside each output
   * annotation
   */
  public static final String SERIALIZE_ANNOTATOR_INFO_FIELD_NAME = "serializeAnnotatorInfo";

  /**
   * Name of the field in the Annotator Bundle Configuration JSON record that indicates the
   * serialization scheme. The value true indiacates the Ontology aware serialization and false
   * indicate the Ontology ignorant serialization.
   */
  public static final String ONTOLOGY_AWARE_FIELD_NAME = "ontologyAware";

  /**
   * Name of the field in the Annotator Bundle Configuration JSON record that indicates the span
   * format. Supporeted values are "simple" (default) or "locationAndText".
   */
  public static final String SERIALIZE_SPAN_FIELD_NAME = "serializeSpan";

  /*
   * SystemT-specific configuration parameter names
   */

  /**
   * For SystemT annotators, name of the field in the Annotator Bundle Configuration JSON record
   * that indicates the names of modules to instantiate for this annotator as a JSON Array of
   * Strings, where each string is a module name
   */
  public static final String MODULE_NAMES_FIELD_NAME = "moduleNames";

  /**
   * For SystemT annotators, name of the field in the Annotator Bundle Configuration JSON record
   * that indicates the module path for this annotator as a JSON array of String values, where each
   * String is a HDFS URI
   */
  public static final String MODULE_PATH_FIELD_NAME = "modulePath";

  /**
   * For SystemT annotators, name of the field in the Annotator Bundle Configuration JSON record
   * that indicates the AQL module path for this annotator as a JSON array of String values, where
   * each String is a HDFS URI
   */
  public static final String SOURCE_MODULES_FIELD_NAME = "sourceModules";

  /**
   * For SystemT annotators, name of the field in the Annotator Bundle Configuration JSON record
   * that indicates the external dictionaries used by this annotator, as a JSON array of key value
   * pairs, where the key is the AQL external dictionary name and the value is the location of the
   * dictionary file, relative to the location field.
   */
  public static final String EXTERNAL_DICTIONARIES_FIELD_NAME = "externalDictionaries";

  /**
   * For SystemT annotators, name of the field in the Annotator Bundle Configuration JSON record
   * that indicates the external tables used by this annotator, as a JSON array of key value pairs,
   * where the key is the AQL external table name and the value is the location of the CSV file with
   * the table entries, relative to the location field.
   */
  public static final String EXTERNAL_TABLES_FIELD_NAME = "externalTables";

  /**
   * For SystemT annotators, name of the field in the Annotator Bundle Configuration JSON record
   * that indicates the type of tokenizer used by the SystemT annotator, e.g., standard or
   * multilingual
   */
  public static final String TOKENIZER_FIELD_NAME = "tokenizer";

  /**
   * For SystemT annotators, the name of the field in the Annotator Bundle Configuration JSON record
   * that indicates the location of the tokenizer pear file, relative to the location field.
   */
  public static final String TOKENIZER_PEAR_FILE_FIELD_NAME = "tokenizerPearFile";


  /**
   * The name of the field in the JSON record representation of a span that indicates the begin
   * offset of the span.
   */
  public static final String SPAN_BEGIN_FIELD_NAME = "begin";

  /**
   * The name of the field in the JSON record representation of a span that indicates the end offset
   * of the span.
   */
  public static final String SPAN_END_FIELD_NAME = "end";

  /**
   * The name of the (optional) field in the JSON record representation of a span that indicates the
   * name of the field in the input document record that the span is over. This field is not
   * populated for spans that are over default document content fields
   * {@link #DOCUMENT_CONTENT_FIELD_NAME} and {@link #DOCUMENT_TEXT_FIELD_NAME}.
   */
  public static final String SPAN_DOCREF_FIELD_NAME = "docref";

  /**
   * The name of the field in the JSON record representation of a span when
   * serializeSpan:"locationAndText" is specified
   */
  public static final String SPAN_LOCATION_FIELD_NAME = "location";

  /**
   * The name of the field in the JSON record representation of a text in location when
   * serializeSpan:"locationAndText" is specified
   */
  public static final String SPAN_TEXT_FIELD_NAME = "text";

  /*
   * UIMA-specific configuration parameter names
   */

  /**
   * For UIMA annotators, the name of the field in the Annotator Bundle Configuration JSON record
   * that indicates the location of the UIMA descriptor XML file, relative to the location field.
   */
  public static final String UIMA_DESCRIPTOR_FILE_FIELD_NAME = "uimaDescriptorFile";

  /**
   * For UIMA annotators, the name of the field in the Annotator Bundle Configuration JSON record
   * that indicates the UIMA data path of this annotator, as a JSON Array of Strings, where each
   * string represents the location of one entry in the data path, relative to the location field.
   */
  public static final String UIMA_DATA_PATH_FIELD_NAME = "uimaDataPath";

  /**
   * For UIMA annotators, the name of the field in the Annotator Bundle Configuration JSON record
   * that indicates the extended UIMA class path of this annotator, as a JSON Array of Strings,
   * where each string represents one class path entry, relative to the location field.
   */
  public static final String UIMA_CLASS_PATH_FIELD_NAME = "uimaExtensionClassPath";

  /**
   * For UIMA annotators, the name of the field in the Annotator Bundle Configuration JSON record
   * that indicates the location of the UIMA pear file, relative to the location field.
   */
  public static final String UIMA_PEAR_FILE_FIELD_NAME = "uimaPearFile";

  /** Uima data path separator character */
  // FIXME replace ';' with the official UIMA data path separator
  public static final char UIMA_DATA_PATH_SEP_CHAR = File.pathSeparatorChar;


  /*
   * SIRE-specific configuration parameter names
   */

  /**
   * For SIRE annotators, the name of the field in the Annotator Bundle Configuration JSON record
   * that indicates the location of the SIRE model file, relative to the location field.
   */
  // public static final String SIRE_MODEL_FILE_FIELD_NAME = "sireModelFile";

  /*
   * FIELDS OF THE INPUT DOCUMENT RECORD
   */

  /**
   * Name of the input document parameter
   */
  public static final String DOCUMENT_PARAMETER_NAME = "document";

  /**
   * Name of the field in the output JSON Record indicating annotations output for the document
   */
  public static final String ANNOTATIONS_FIELD_NAME = "annotations";

  /*
   * FIELDS RELATED TO INSTRUMENTATION INFO
   */

  /**
   * Name of the field in the output document JSON Record where the instrumentation info collected
   * during runtime is stored
   */
  public static final String INSTRUMENTATION_INFO_FIELD_NAME = "instrumentationInfo";

  /**
   * Name of the fields in the output JSON Record indicating the number of milliseconds spent
   * processing a given document
   */
  public static final String RUNNING_TIME_FIELD_NAME = "runningTimeMS";

  /**
   * Name of the field in the output JSON Record indicating the size (in number of characters) of
   * the input document; obtained by adding up the length in characters of all fields of the input
   * document that are of type String, ignoring other fields of other type (e.g., integer, float,
   * boolean)
   */
  public static final String DOCUMENT_SIZE_FIELD_NAME = "documentSizeChars";

  /**
   * Name of the field in the output JSON Record indicating the total number of annotations
   * extracted for the input document
   */
  public static final String ANNOTATIONS_SIZE_TOTAL_FIELD_NAME = "numAnnotationsTotal";

  /**
   * Name of the field in the output JSON Record indicating the number of annotations extracted for
   * each output type
   */
  public static final String ANNOTATIONS_SIZE_PER_TYPE_FIELD_NAME = "numAnnotationsPerType";

  /**
   * Name of the field in the output JSON Record indicating the number of annotations extracted for
   * a particular output type
   */
  public static final String ANNOTATIONS_SIZE_FIELD_NAME = "numAnnotations";

  /**
   * Name of the field in the output JSON Record indicating the name of an output annotation type.
   */
  public static final String ANNOTATION_TYPE_FIELD_NAME = "annotationType";

  /**
   * Name of the field in the output JSON Record indicating whether the annotator was interrupted
   */
  public static final String INTERRUPTED_FIELD_NAME = "interrupted";

  /**
   * Name of the field in the output JSON Record indicating whether the annotator completely
   * successfully (i.e., there were no errors, and it was not interrupted).
   */
  public static final String SUCCESS_FIELD_NAME = "success";

  /**
   * Name of the field in the output document JSON Record that stores the error message, if any
   * error occurred
   */
  public static final String EXCEPTION_MESSAGE_FIELD_NAME = "exceptionMessage";

  /**
   * Name of the field in the output document JSON Record that stores the error stack trace, if any
   * error occurred
   */
  public static final String EXCEPTION_STACKTRACE_FIELD_NAME = "exceptionStackTrace";

  /*
   * OTHER CONSTANTS
   */
  /**
   * Name of the field in the input document JSON Record that is considered the first choice of
   * default document content field (first choice) for all annotator runtimes, with
   * {@link #DOCUMENT_TEXT_FIELD_NAME} being used as the second choice as follows: <br>
   * <br>
   * <ul>
   * <li>UIMA annotator: if UIMA annotator use the default SOFA view _InitialView, pass it the value
   * of content (if it exists in the input record); otherwise, pass the value of the text (second
   * choice) if it exists in the input record; otherwise throw an exception</li>
   * <li>SIRE annotator:pass the value of content (if it exists in the input record); otherwise,
   * pass the value of the text (second choice) if it exists in the input record; otherwise throw an
   * exception</li>
   * <li>SystemT annotator: if the annotator expects the view Document to have the field called
   * 'text', pass the value of content (if it exists in the input record) into Document.text;
   * otherwise, pass the value of the text (second choice) if it exists in the input record. Throw
   * an exception if either none of the two fields are present in the input record, or both fields
   * are present in the input record. SystemT annotators whose Document view contains both text and
   * content fields are not supported.</li>
   * </ul>
   */
  public static final String DOCUMENT_CONTENT_FIELD_NAME = "content";

  /**
   * Name of the field in the input document JSON Record that is considered the second choice to
   * pass as document content field (after {@link #DOCUMENT_CONTENT_FIELD_NAME} which is the first
   * choice) for all annotator runtime; see {@link #DOCUMENT_CONTENT_FIELD_NAME} for a full
   * explanation
   */
  public static final String DOCUMENT_TEXT_FIELD_NAME = "text";

  /**
   * Default language to use when no language code is specified in the execution parameters for a
   * given document
   */
  public static final String DOCUMENT_DEFAULT_LANGUAGE_CODE = "en";

  /**
   * Default content type to use when no language code is specified in the execution parameters for
   * a given document
   */
  public static final String DOCUMENT_DEFAULT_CONTENT_TYPE =
      AnnotatorBundleConfig.ContentType.HTML.getValue();

  /**
   * Tag name expected in descriptor XML (analysisEngineMetaData)
   */
  public static final String DESCRIPTOR_TAG_ANALYSIS_ENGINE_METADATA = "analysisEngineMetaData";

  /**
   * Tag name expected in descriptor XML (capability)
   */
  public static final String DESCRIPTOR_TAG_CAPABILITY = "capability";

  /**
   * Tag name expected in descriptor XML (inputSofas)
   */
  public static final String DESCRIPTOR_TAG_INPUT_SOFAS = "inputSofas";

  /**
   * Tag name expected in descriptor XML (sofaName)
   */
  public static final String DESCRIPTOR_TAG_SOFA_NAME = "sofaName";

  /**
   * Tag name expected in descriptor XML (typeDescription)
   */
  public static final String DESCRIPTOR_TAG_TYPE_DESCRIPTION = "typeDescription";

  /**
   * Tag name expected in descriptor XML (delegateAnalysisEngine)
   */
  public static final String DESCRIPTOR_TAG_DELEGATE_ANALYSIS_ENGINE = "delegateAnalysisEngine";

  /**
   * Tag name expected in descriptor XML (import)
   */
  public static final String DESCRIPTOR_TAG_IMPORT = "import";


  /**
   * Attribute name expected in descriptor XML (location)
   */
  public static final String DESCRIPTOR_ATTRIBUTE_LOCATION = "location";

  /**
   * Tag name expected in descriptor XML (name)
   */
  public static final String DESCRIPTOR_TAG_NAME = "name";

  /**
   * Tag name expected in descriptor XML (supertypeName)
   */
  public static final String DESCRIPTOR_TAG_SUPER_TYPE_NAME = "supertypeName";

  /**
   * Name of the annotation type. All the annotations inherit from this type.
   */
  public static final String TYPE_NAME_ANNOTATION = "uima.tcas.Annotation";

  /**
   * This is a feature from the type 'uima.cas.Annotation', marking the beginning of the text to
   * which the annotation is applied.
   */
  public static final String ATTRIBUTE_NAME_BEGIN = "begin";

  /**
   * This is a feature from the type 'uima.cas.Annotation', marking the end of the text to which the
   * annotation is applied.
   */
  public static final String ATTRIBUTE_NAME_END = "end";

  /**
   * This is the default attribute used for serializing all the features subsuming the type
   * 'uima.cas.Annotation'. This attribute wraps the features 'begin' and 'end' which are always
   * present in each feature of the type 'uima.cas.Annotation'. It may also contain an optional
   * feature called 'docref' which exists if the particular annotation comes from the non-default
   * view of the input CAS. The value of 'docref' is the name of the non-default view, to which this
   * annotation refers.
   */
  public static final String ATTRIBUTE_NAME_SPAN = "span";

  public static final String ID_KEY = "@id";
}
