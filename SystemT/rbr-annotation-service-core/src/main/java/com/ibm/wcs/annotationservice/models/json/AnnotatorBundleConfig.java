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
package com.ibm.wcs.annotationservice.models.json;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.ibm.wcs.annotationservice.AnnotationServiceConstants;
import com.ibm.wcs.annotationservice.exceptions.AnnotationServiceException;
import com.ibm.wcs.annotationservice.exceptions.VerboseNullPointerException;
import com.ibm.wcs.annotationservice.polymorphic.ABCTypeIdResolver;


/**
 * A base class representing an Annotator Bundle Configuration for SystemT and UIMA. Reads the
 * configuration from a JSON Record and performs necessary validations. The configuration parameters
 * are listed below.
 * <p>
 * version: JSON String (mandatory)<br>
 * Name of the field in the Annotator Bundle Configuration JSON record that indicates the version of
 * the configuration format, e.g., "1.0"
 * </p>
 * <p>
 * annotator: JSON record (mandatory)<BR>
 * Name of the field in the Annotator Bundle Configuration JSON record that indicates the annotator
 * key and version; the format is a record with two fields (both mandatory):
 * <ul>
 * <li>key: JSON String (mandatory) - specifies the key of the annotator; recommend assignment using
 * name::guid format
 * <li>version: JSON String - indicates the annotator version; recommend CCYY-MM-DDThh:mm:ssZ
 * format</li>
 * </ul>
 * </p>
 * <p>
 * annotatorRuntime: JSON String (mandatory)<BR>
 * Name of the field in the Annotator Bundle Configuration JSON record that indicates the text
 * analytics runtime for executing this annotator, e.g., SystemT, UIMA, SIRE
 * </p>
 * <p>
 * acceptedContentTypes: JSON Array of String (mandatory)<BR>
 * Name of the field in the Annotator Bundle Configuration JSON record that indicates the types of
 * input text accepted by this annotator; possible values are: "text/plain" if the annotator
 * supports only plain text, and "text/html" if the annotator supports only HTML text; specify both
 * "text/plain" and "text/html" if the annotator supports both kinds of document text
 * </p>
 * <p>
 * inputTypes: JSON Array of String (optional field)<BR>
 * Name of the field in the Annotator Bundle Configuration JSON record that indicates the input
 * types expected by this annotator; null if not specified
 * </p>
 * <p>
 * outputTypes: JSON Array of String (mandatory)<BR>
 * Name of the field in the Annotator Bundle Configuration JSON record that indicates the output
 * types produced by this annotator
 * </p>
 * <p>
 * serializeAnnotatorInfo: boolean (optional)<BR>
 * Whether to serialize the annotator info (including the key and the version) inside each output
 * annotation; if true, the value of {@link #getAnnotator()} is copied to each output annotation; if
 * not specified, the default is false (no serialization)
 * </p>
 * <p>
 * location: String (mandatory)<BR>
 * Location on HDFS or local FS where the annotator bundle has been deployed
 * </p>
 * <p>
 * SystemT-specific configuration parameters <BR>
 * </p>
 * <p>
 * moduleNames: JSON Array of String (mandatory for SystemT annotators)<BR>
 * For SystemT annotators, the names of modules to instantiate for this annotator as a JSON Array of
 * Strings, where each string is a module name
 * </p>
 * <p>
 * modulePath: JSON Array of String (mandatory for SystemT annotators)<BR>
 * For SystemT annotators, the module path for this annotator as a JSON array of String values,
 * where each String indicates a path relative to {@link #location} of a directory or jar/zip
 * archive containing compiled SystemT modules (.tam files)
 * </p>
 * <p>
 * externalDictionaries: JSON Record of String key/value pairs (optional for SystemT annotators)<BR>
 * For SystemT annotators, the external dictionaries used by this annotator, as a JSON array of key
 * value pairs, where the key is the AQL external dictionary name and the value is the path relative
 * to {@link #location} of the dictionary file; null if not specified
 * </p>
 * <p>
 * externalTables: JSON Record of String key/value pairs (optional for SystemT annotators)<BR>
 * For SystemT annotators, the external tables used by this annotator, as a JSON array of key value
 * pairs, where the key is the AQL external table name and the value is the path relative to
 * {@link #location} of the CSV file with the table entries; null if not specified
 * </p>
 * <p>
 * tokenizer: JSON String (mandatory for SystemT annotators)<BR>
 * For SystemT annotators, he type of tokenizer used by the SystemT annotator; possible values:
 * "standard"
 * </p>
 * <p>
 * UIMA-specific configuration parameters<BR>
 * </p>
 * <p>
 * uimaDescriptorFile: JSON String (mandatory for UIMA annotators) <BR>
 * For UIMA annotators, the location of the UIMA descriptor XML file relative to {@link #location}
 * </p>
 * <p>
 * uimaDataPath: JSON Array of String (mandatory for UIMA annotators)<BR>
 * For UIMA annotators,the UIMA data path of this annotator, as a JSON Array of Strings, where each
 * string represents the location relative to {@link #location} of one entry in the data path
 * </p>
 * <p>
 * uimaPearFile: JSON String (mandatory for UIMA annotators)<BR>
 * For UIMA annotators,the location of the .pear file relative to {@link #location}
 * </p>
 * <p>
 * ontologyAware: boolean (optional) <BR>
 * For UIMA annotators, this flag controls the serialization/de-serialization scheme; it does not
 * affect the serialization for SystemT annotators. The flag is optional; if not specified, the
 * default value is <code>false</code> . If the flag is set to false, nested UIMA annotations are
 * referenced using a unique generated ID, and serialized separately. If the flag is set to true,
 * nested UIMA annotations with no special attributes apart from the default ones
 * (<code>begin</code> and <code>end</code>) are expanded in-line; nested annotations with
 * additional attributes are referenced by their IDs and serialized separately.
 * </p>
 * <p>
 * If it is set to false, all the nested annotations are referenced using the ID.
 * </p>
 * <p>
 * SIRE-specific configuration parameters
 * <p>
 * sireModelFile: JSON String (mandatory for SIRE annotators)<BR>
 * For SIRE annotators, the location of the SIRE model file relative to {@link #location}
 * </p>
 *
 **/
@JsonTypeInfo(use = Id.CUSTOM, include = As.PROPERTY, property = "annotatorRuntime", visible = true)
@JsonTypeIdResolver(ABCTypeIdResolver.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AnnotatorBundleConfig implements Serializable {

  /**
   * Templorary solution. will unify constructor when we support serializeSpan type for PEAR
   */
  public AnnotatorBundleConfig(String version, HashMap<String, String> annotator,
      String annotatorRuntime, List<String> acceptedContentTypes, List<String> inputTypes,
      List<String> outputTypes, String location, boolean serializeAnnotatorInfo,
      String serializeSpan) throws AnnotationServiceException {
    this(version, annotator, annotatorRuntime, acceptedContentTypes, inputTypes, outputTypes,
        location, serializeAnnotatorInfo);
    this.serializeSpan = serializeSpan;
    if (null == serializeSpan) {
      this.serializeSpan = SerializeSpan.SIMPLE.getValue();
    } else {
      if (null == AnnotatorBundleConfig.SerializeSpan.getEnum(serializeSpan)) {
        throw new AnnotationServiceException(
            "Value '%s' of field '%s' in %s is not supported; supported values are: %s",
            annotatorRuntime, AnnotationServiceConstants.SERIALIZE_SPAN_FIELD_NAME, CONTEXT_NAME,
            AnnotatorBundleConfig.SerializeSpan.values());
      }
    }
  }


  public AnnotatorBundleConfig(String version, HashMap<String, String> annotator,
      String annotatorRuntime, List<String> acceptedContentTypes, List<String> inputTypes,
      List<String> outputTypes, String location, boolean serializeAnnotatorInfo)
      throws AnnotationServiceException {
    super();
    this.version = version;
    this.annotator = annotator;
    this.annotatorRuntime = annotatorRuntime;
    this.acceptedContentTypes = acceptedContentTypes;
    this.inputTypes = inputTypes;
    this.outputTypes = outputTypes;
    this.location = location;
    this.serializeAnnotatorInfo = serializeAnnotatorInfo;

    // FIXME: JsonProperty(required=true) is not supported until 2.6, so
    // manual check. Remove once upgraded
    /** TEMP CHECK **/
    if (null == version)
      throw new VerboseNullPointerException(CONTEXT_NAME,
          AnnotationServiceConstants.VERSION_FIELD_NAME);

    if (null == annotator)
      throw new VerboseNullPointerException(CONTEXT_NAME,
          AnnotationServiceConstants.ANNOTATOR_FIELD_NAME);

    if (null == annotatorRuntime)
      throw new VerboseNullPointerException(CONTEXT_NAME,
          AnnotationServiceConstants.ANNOTATOR_RUNTIME_FIELD_NAME);

    if (null == acceptedContentTypes)
      throw new VerboseNullPointerException(CONTEXT_NAME,
          AnnotationServiceConstants.ACCEPTED_CONTENT_TYPES_FIELD_NAME);

    if (null == location)
      throw new VerboseNullPointerException(CONTEXT_NAME,
          AnnotationServiceConstants.LOCATION_FIELD_NAME);

    if (null == outputTypes)
      throw new VerboseNullPointerException(CONTEXT_NAME,
          AnnotationServiceConstants.OUTPUT_TYPES_FIELD_NAME);

    /** TEMP CHECK **/

    // General
    if (null == AnnotatorBundleConfig.AnnotatorRuntime.getEnum(annotatorRuntime))
      throw new AnnotationServiceException(
          "Value '%s' of field '%s' in %s is not supported; supported values are: %s",
          annotatorRuntime, AnnotationServiceConstants.ANNOTATOR_RUNTIME_FIELD_NAME, CONTEXT_NAME,
          AnnotatorBundleConfig.AnnotatorRuntime.values());

    if (0 == acceptedContentTypes.size())
      throw new AnnotationServiceException(
          "Value of field '%s' in %s is empty; provide at least one of the following values: %s",
          AnnotationServiceConstants.ACCEPTED_CONTENT_TYPES_FIELD_NAME, CONTEXT_NAME,
          ContentType.getValues());

    if (0 == outputTypes.size())
      throw new AnnotationServiceException(
          "Value of field '%s' in %s is empty; provide at least one output type",
          AnnotationServiceConstants.OUTPUT_TYPES_FIELD_NAME, CONTEXT_NAME);


    // Validate that input and output types are disjoint
    if (null != inputTypes) {
      List<String> commonTypes = intersection(inputTypes, outputTypes);

      if (0 != commonTypes.size())
        throw new AnnotationServiceException(
            "The annotation types %s appear as both input and output types; input and output types must be disjoint",
            commonTypes);
    }

  }


  private static final long serialVersionUID = 1L;

  /* CONSTANTS */
  private static final String CONTEXT_NAME = "Annotator Module Configuration";

  /*
   * VARIOUS ENUM TYPES
   */

  /** Possible values for the content type of a document */
  public enum ContentType {
    PLAIN("text/plain"), HTML("text/html");

    private String value;

    /**
     * Main constructor
     *
     * @param value String in the Annotator Bundle Configuration that represents this content type
     */
    private ContentType(String value) {
      this.value = value;
    }

    /**
     * @return String in the Annotator Bundle Configuration that represents this content type; may
     *         be different from the enum's default string representation
     */
    public String getValue() {
      return value;
    }

    /**
     * @param contentType the content type of an input document
     * @return true if the input content type is compatible with one of the values in this enum
     */
    public boolean isCompatible(String contentType) {
      for (ContentType e : ContentType.values()) {
        if (e.value.indexOf(contentType) >= 0)
          return true;
      }
      return false;
    }

    /**
     * @return the set of all strings in the Annotator Bundle Configuration that are allowed as
     *         values for this enum; may be different from the enum's default string representation
     */
    public static String[] getValues() {
      String[] ret = new String[ContentType.values().length];
      for (int ix = 0; ix < ContentType.values().length; ix++) {
        ret[ix] = ContentType.values()[ix].value;
      }
      return ret;
    }

    public String toString() {
      return value;
    }

    /**
     * Return the actual enum for the given value
     *
     * @param value
     * @return
     */
    public static ContentType getEnum(String value) {
      for (ContentType e : ContentType.values()) {
        if (e.value.equals(value))
          return e;
      }
      throw new IllegalArgumentException(
          String.format("Invalid value '%s'; possible values are: %s", value,
              Arrays.asList(ContentType.values())));
    }
  }

  /** Possible values for the annotator runtime */
  public enum AnnotatorRuntime {
    SYSTEMT("SystemT"), UIMA("UIMA");// , SIRE("SIRE");

    private String value;

    /**
     * Main constructor
     *
     * @param value String in the Annotator Bundle Configuration that represents this runtime type
     */
    private AnnotatorRuntime(String value) {
      this.value = value;
    }

    /**
     * @return String in the Annotator Bundle Configuration that represents this runtime type; may
     *         be different from the enum's default string representation
     */
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return value;
    }

    /**
     * @return the set of all strings in the Annotator Bundle Configuration that are allowed as
     *         values for this enum; may be different from the enum's default string representation
     */
    public static String[] getValues() {
      String[] ret = new String[AnnotatorRuntime.values().length];
      for (int ix = 0; ix < AnnotatorRuntime.values().length; ix++) {
        ret[ix] = AnnotatorRuntime.values()[ix].value;
      }
      return ret;
    }

    /**
     * Return the actual enum for the given value
     *
     * @param value
     * @return
     */
    public static AnnotatorRuntime getEnum(String value) {
      for (AnnotatorRuntime e : AnnotatorRuntime.values()) {
        if (e.value.equals(value))
          return e;
      }
      throw new IllegalArgumentException(
          String.format("Invalid value '%s'; possible values are: %s", value,
              Arrays.asList(AnnotatorRuntime.values())));
    }
  }

  /** Possible values for the serialize span type */
  public enum SerializeSpan {
    SIMPLE("simple"), LOCATION_AND_TEXT("locationAndText");

    private String value;

    /**
     * Main constructor
     *
     * @param value String in the Annotator Bundle Configuration that represents this serialize span
     *        type
     */
    private SerializeSpan(String value) {
      this.value = value;
    }

    /**
     * @return String in the Annotator Bundle Configuration that represents this serialize span
     *         type; may be different from the enum's default string representation
     */
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return value;
    }

    /**
     * @return the set of all strings in the Annotator Bundle Configuration that are allowed as
     *         values for this enum; may be different from the enum's default string representation
     */
    public static String[] getValues() {
      String[] ret = new String[SerializeSpan.values().length];
      for (int ix = 0; ix < SerializeSpan.values().length; ix++) {
        ret[ix] = SerializeSpan.values()[ix].value;
      }
      return ret;
    }

    /**
     * Return the actual enum for the given value
     *
     * @param value
     * @return
     */
    public static SerializeSpan getEnum(String value) {
      for (SerializeSpan e : SerializeSpan.values()) {
        if (e.value.equals(value))
          return e;
      }
      throw new IllegalArgumentException(
          String.format("Invalid value '%s'; possible values are: %s", value,
              Arrays.asList(SerializeSpan.values())));
    }
  }

  /*
   * COMMON CONFIGURATION PARAMETERS - Remember to update {@link equals()} when making changes
   */

  /**
   * The version of the configuration format, e.g., "1.0"
   */
  //// @JsonProperty(required=true)
  private String version = null;

  /**
   * The annotator identifier; a record with two fields key and version, both of type String
   */
  // @JsonProperty(required=true)
  private HashMap<String, String> annotator = null;

  /**
   * The text analytics runtime for executing this annotator, e.g., SystemT, UIMA, SIRE
   */
  // @JsonProperty(required=true)
  private String annotatorRuntime;

  /**
   * The input types accepted by this annotator, e.g., "text/plain"
   */
  // @JsonProperty(required=true)
  private List<String> acceptedContentTypes = null;

  /**
   * The input types expected by this annotator
   */
  private List<String> inputTypes = null;

  /**
   * The output types produced by this annotator
   */
  // @JsonProperty(required=true)
  private List<String> outputTypes = null;

  /**
   * Directory on HDFS or local FS where the annotator bundle has been deployed.
   */
  // @JsonProperty(required=true)
  private String location = null;


  /**
   * True to serialize the annotator info (including the key and the version) inside each output
   * annotation (i.e., the value of {@link #getAnnotator()} is copied to each output annotation); if
   * not specified, the default is false (no serialization)
   */
  private boolean serializeAnnotatorInfo = false;

  /**
   * The type of span serialization
   */
  private String serializeSpan = null;

  // Setters and Getters

  /**
   * @return the version of the configuration format, e.g., "1.0"
   */
  public String getVersion() {
    return version;
  }


  /**
   * @param version the version to set
   */
  public void setVersion(String version) {
    this.version = version;
  }

  /**
   * @return the annotator information, including the key and version
   */
  public HashMap<String, String> getAnnotator() {
    return annotator;
  }


  /**
   * @param annotator the annotator to set
   */
  public void setAnnotator(HashMap<String, String> annotator) {
    this.annotator = annotator;
  }


  /**
   * @return the text analytics runtime for executing this annotator, e.g., SystemT, UIMA, SIRE
   */
  @JsonIgnore
  public String getAnnotatorRuntime() {
    return annotatorRuntime;
  }


  /**
   * @param annotatorRuntime the annotatorRuntime to set
   * @throws AnnotationServiceException
   */
  public void setAnnotatorRuntime(String annotatorRuntime) {
    this.annotatorRuntime = annotatorRuntime;

  }


  /**
   * @return the input types accepted by this annotator, e.g., "text/plain"
   */
  public List<String> getAcceptedContentTypes() {
    return acceptedContentTypes;
  }


  /**
   * @param acceptedContentTypes the acceptedContentTypes to set
   */
  public void setAcceptedContentTypes(List<String> acceptedContentTypes) {
    this.acceptedContentTypes = acceptedContentTypes;
  }


  /**
   * @return the input types expected by this annotator
   */
  public List<String> getInputTypes() {
    return inputTypes;
  }


  /**
   * @param inputTypes the inputTypes to set
   */
  public void setInputTypes(List<String> inputTypes) {
    this.inputTypes = inputTypes;
  }


  /**
   * @return the output types produced by this annotator
   */
  public List<String> getOutputTypes() {
    return outputTypes;
  }


  /**
   * @param outputTypes the outputTypes to set
   */
  public void setOutputTypes(List<String> outputTypes) {
    this.outputTypes = outputTypes;
  }


  /**
   * @return the value of the version field of the annotator identifier
   */
  public boolean isSerializeAnnotatorInfo() {
    return serializeAnnotatorInfo;
  }


  /**
   * @param serializeAnnotatorInfo the serializeAnnotatorInfo to set
   */
  public void setSerializeAnnotatorInfo(boolean serializeAnnotatorInfo) {
    this.serializeAnnotatorInfo = serializeAnnotatorInfo;
  }


  /**
   * @return the directory (on HDFS or local FS) where the annotator bundle has been deployed
   */
  public String getLocation() {
    return location;
  }


  /**
   * @param location the location to set
   */
  public void setLocation(String location) {
    this.location = location;
  }

  /**
   * @return a relative path to the pear file.
   */
  abstract public String getPearFilePath();

  /**
   * @return a resolved/absolute path to the pear file.
   */
  abstract public String getPearFileResolvedPath();

  /**
   * @return the type of span serialization
   */
  public String getSerializeSpan() {
    return this.serializeSpan;
  }


  /*
   * PRIVATE METHODS GO HERE
   */

  /**
   * Compute the intersection of two lists
   *
   * @param list1
   * @param list2
   * @return
   */
  protected <T> List<T> intersection(List<T> list1, List<T> list2) {
    List<T> list = new ArrayList<T>();

    for (T t : list1) {
      if (list2.contains(t)) {
        list.add(t);
      }
    }

    return list;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result =
        prime * result + ((acceptedContentTypes == null) ? 0 : acceptedContentTypes.hashCode());
    result = prime * result + ((annotator == null) ? 0 : annotator.hashCode());
    result = prime * result + ((annotatorRuntime == null) ? 0 : annotatorRuntime.hashCode());
    result = prime * result + ((inputTypes == null) ? 0 : inputTypes.hashCode());
    result = prime * result + ((location == null) ? 0 : location.hashCode());
    result = prime * result + ((outputTypes == null) ? 0 : outputTypes.hashCode());
    result = prime * result + (serializeAnnotatorInfo ? 1231 : 1237);
    result = prime * result + ((version == null) ? 0 : version.hashCode());
    return result;
  }


  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    AnnotatorBundleConfig other = (AnnotatorBundleConfig) obj;
    if (acceptedContentTypes == null) {
      if (other.acceptedContentTypes != null)
        return false;
    } else if (!acceptedContentTypes.equals(other.acceptedContentTypes))
      return false;
    if (annotator == null) {
      if (other.annotator != null)
        return false;
    } else if (!annotator.equals(other.annotator))
      return false;
    if (annotatorRuntime == null) {
      if (other.annotatorRuntime != null)
        return false;
    } else if (!annotatorRuntime.equals(other.annotatorRuntime))
      return false;
    if (inputTypes == null) {
      if (other.inputTypes != null)
        return false;
    } else if (!inputTypes.equals(other.inputTypes))
      return false;
    if (location == null) {
      if (other.location != null)
        return false;
    } else if (!location.equals(other.location))
      return false;
    if (outputTypes == null) {
      if (other.outputTypes != null)
        return false;
    } else if (!outputTypes.equals(other.outputTypes))
      return false;
    if (serializeAnnotatorInfo != other.serializeAnnotatorInfo)
      return false;
    if (version == null) {
      if (other.version != null)
        return false;
    } else if (!version.equals(other.version))
      return false;
    if (serializeSpan == null) {
      if (other.serializeSpan != null)
        return false;
    } else if (!serializeSpan.equals(other.serializeSpan))
      return false;

    return true;
  }

  /**
   * Concatenate the two input paths using {@link File#pathSeparator}. This method does not validate
   * that either of the two input parameters, or the output parameter point to valid files or
   * directories that exists on disk
   *
   * @param parentPath the parent path
   * @param childPath the child path
   * @return a String consisting of the parent path, followed by {@link File#pathSeparator},
   *         followed by the child path; uses {@link String.format()} with format
   *         <code>%s%c%s</code>
   */
  protected String resolveRelativePath(String parentPath, String childPath) {
    return String.format("%s%c%s", parentPath, '/', childPath);
  }


}


