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

import java.io.Serializable;

import com.ibm.wcs.annotationservice.exceptions.AnnotationServiceException;
import com.ibm.wcs.annotationservice.models.json.AnnotatorBundleConfig;

/**
 * Class that encapsulates various parameters for executing {@link AnnotatorBundle#invoke()} on a
 * single document.
 * 
 */
public class ExecuteParams implements Serializable {

  private static final long serialVersionUID = 2377005342364764026L;

  /**
   * Timeout value in milliseconds that the annotator is allowed to spend on a single document
   * before being interrupted; by default, it is {@link AnnotationServiceConstants#NO_TIMEOUT}
   */
  private Long timeoutMS = AnnotationServiceConstants.NO_TIMEOUT;

  /**
   * 2-letter language code of the input document; used for all text fields of the input document;
   * this field and the langCodeFieldName field are mutually exclusive; specifying non-null values
   * for both these fields results in an exception
   */
  private String langCodeValue = null;

  /**
   * Field name on the input document that contains the 2-letter language code of all fields of the
   * input document; this field and the langCodeValue attribute are mutually exclusive; specifying
   * non-null values for both these fields results in an exception
   */
  private String langCodeFieldName = null;

  /**
   * Content type of the input document text as one of the two values: 'text/plain' and 'text/html';
   * used for all text fields of the input document; this field and the contentTypeFieldName field
   * are mutually exclusive: specifying non-null values for both of them results in an exception
   */
  private String contentTypeValue = null;

  /**
   * field name on the input document that contains the content type of the input document text as
   * one of the two values: 'text/plain' and 'text/html'; this field and the contentTypeValue field
   * are mutually exclusive: specifying non-null values for both of them results in an exception
   */
  private String contentTypeFieldName = null;

  /**
   * Default constructor.
   * 
   * @param timeoutMS timeout value in milliseconds requested by the user; if null, then
   *        {@link AnnotationServiceConstants#NO_TIMEOUT} is used by default
   * @param langCodeValue 2-letter language code of the input document; used for all text fields of
   *        the input document; this field and the langCodeFieldName field are mutually exclusive:
   *        an exception is thrown if both values are non-null; if none of the two fields are
   *        specified, 'en' will be used by default
   * @param langCodeFieldName field name on the input document that contains the 2-letter language
   *        code of all fields of the input document; this field and the langCodeValue field are
   *        mutually exclusive: an exception is thrown when both values are non-null; if none of the
   *        values are specified, the default used is 'en'
   * @param contentTypeValue content type of the input document text as one of the two values:
   *        'text/plain' and 'text/html'; used for all text fields of the input document; this field
   *        and the contentTypeFieldName field are mutually exclusive: an exception is thrown when
   *        both values are non-null; if none of the values are specified, the default used is
   *        'text/html'
   * @param contentTypeFieldName field name on the input document that contains the content type of
   *        the input document text as one of the two values: 'text/plain' and 'text/html'; this
   *        field and the contentTypeValue field are mutually exclusive: an exception is thrown when
   *        both values are non-null; if none of the values are specified, the default content type
   *        of the input document is assumed to be 'text/html'
   * @throws AnnotationServiceException if (1) both langCodeValue and langCodeFieldName arguments
   *         are non-null, or (2) if both contentTypeValue and contentTypeFieldName arguments are
   *         non-null
   */
  public ExecuteParams(Long timeoutMS, String langCodeValue, String langCodeFieldName,
      String contentTypeValue, String contentTypeFieldName) throws AnnotationServiceException {
    if (null != timeoutMS)
      this.timeoutMS = timeoutMS;
    if (this.timeoutMS == 0)
      this.timeoutMS = -1L;

    // Obtain and validate the language code
    if (null == langCodeValue && null == langCodeFieldName) {
      // No language specified, either as a code or from a document attribute; assume English by
      // default
      this.langCodeValue = AnnotationServiceConstants.DOCUMENT_DEFAULT_LANGUAGE_CODE;
    } else if (null != langCodeValue) {
      // A specific language code was specified as input
      this.langCodeValue = langCodeValue;
    } else if (null != langCodeFieldName) {
      // Language code comes from the input document
      this.langCodeFieldName = langCodeFieldName;
    } else {
      // Both fields specified: throw an exception
      throw new AnnotationServiceException(
          "Language code value specified twice: once explicitly as '%s', and once as coming from the input document field '%s'; the language code should be specified only once",
          langCodeValue, langCodeFieldName);
    }

    // Obtain the content type info
    if (null == contentTypeValue && null == contentTypeFieldName) {
      // No content type specified, either as a code or from a document attribute; assume text/html
      // by default
      // TODO: in v2 we could specify an 'autodetect' option
      this.contentTypeValue = AnnotationServiceConstants.DOCUMENT_DEFAULT_CONTENT_TYPE;
    } else if (null != contentTypeValue) {
      // A specific content type value was specified as input
      this.contentTypeValue = contentTypeValue;

      // Validate that the content type is one of the two supported values
      if (!(contentTypeValue.equals(AnnotatorBundleConfig.ContentType.HTML.getValue())
          || contentTypeValue.equals(AnnotatorBundleConfig.ContentType.PLAIN.getValue())))
        throw new AnnotationServiceException(
            "Content type value '%s' is not supported; supported values are: '%s' and '%s'",
            contentTypeValue, AnnotatorBundleConfig.ContentType.HTML.getValue(),
            AnnotatorBundleConfig.ContentType.PLAIN.getValue());
    } else if (null != contentTypeFieldName) {
      // Content type comes from the input document
      this.contentTypeFieldName = contentTypeFieldName;
    } else {
      // Both fields specified: throw an exception
      throw new AnnotationServiceException(
          "Content type specified twice: once explicitly as '%s', and once as coming from the input document field '%s'; the content type value should be specified only once",
          contentTypeValue, contentTypeFieldName);
    }

  }

  /* GETTERS AND SETTERS */

  /**
   * @return the timeout in milliseconds
   */
  public long getTimeoutMS() {
    return timeoutMS;
  }

  /**
   * @return
   */
  public String getLangCodeValue() {
    return langCodeValue;
  }

  /**
   * @return
   */
  public String getLangCodeFieldName() {
    return langCodeFieldName;
  }

  /**
   * @return
   */
  public String getContentTypeValue() {
    return contentTypeValue;
  }

  /**
   * @return
   */
  public String getContentTypeFieldName() {
    return contentTypeFieldName;
  }

  @Override
  public String toString() {
    String ret = String.format(
        "timeoutMS=%d\nlangCodeValue=%s\nlangCodeFieldName=%s\ncontentTypeValue=%s\ncontentTypeFieldName=%s",
        timeoutMS, langCodeValue, langCodeFieldName, contentTypeValue, contentTypeFieldName);
    return ret;
  }

}
