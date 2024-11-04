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

import java.util.Arrays;

import com.ibm.avatar.api.Constants;

/**
 * Exception class to notify the consumer when one or more of Tokenizer configuration parameters are
 * invalid. Some of the scenarios when this exception could occur are: <br/>
 * <ul>
 * <li>The tokenizer configuration file is not found at the specified path</li>
 * <li>The tokenizer configuration file is corrupt</li>
 * <li>The UIMA data path is not found or incorrect</li>
 * </ul>
 * 
 */
public class InvalidTokenizerConfigException extends TextAnalyticsException {
  private static final long serialVersionUID = -2750090806268471131L;

  /** Top-level UIMA config file for invoking multi-lingual tokenizer */
  protected String uimaConfigFile;

  /**
   * Value that UIMA's data path must be set to in order for the multi-lingual tokenizer config file
   * to work.
   */
  protected String uimaDataPath;

  /**
   * Additional classpath used to load resources needed for multi-lingual processing, if they are
   * not already in the Java classpath of the application
   */
  protected String extendedClasspath;

  /**
   * List of languages for which the tokenizer could not successfully tokenize an input string due
   * to incorrect configuration
   */
  protected String[] langCodesWithIncorrectConfig = null;

  /**
   * Create InvalidTokenizerConfigException instance capturing the combination of configuration
   * parameters that resulted in this exception.
   * 
   * @param cause Original exception thrown when Text Analytics runtime attempted to instantiate
   *        tokenizer
   * @param uimaConfigFile Absolute path of the UIMA configuration file used to instantiate
   *        tokenizer
   * @param uimaDataPath Data path for UIMA
   * @param extendedClasspath Additional classpath used to load resources needed for multi-lingual
   *        processing
   */
  public InvalidTokenizerConfigException(Throwable cause, String uimaConfigFile,
      String uimaDataPath, String extendedClasspath) {
    super(cause, prepareMessage(uimaConfigFile, uimaDataPath, extendedClasspath));
    this.uimaConfigFile = uimaConfigFile;
    this.uimaDataPath = uimaDataPath;
    this.extendedClasspath = extendedClasspath;
  }

  /**
   * Create InvalidTokenizerConfigException instance capturing the combination of configuration
   * parameters that resulted in this exception. This constructor allows the caller to pass an
   * additional error message.
   * 
   * @param cause Original exception thrown when Text Analytics runtime attempted to instantiate
   *        tokenizer
   * @param uimaConfigFile Absolute path of the UIMA configuration file used to instantiate
   *        tokenizer
   * @param uimaDataPath Data path for UIMA
   * @param extendedClasspath Additional classpath used to load resources needed for multi-lingual
   *        processing
   * @param additionalMessage additional message to append to the original exception information
   */
  public InvalidTokenizerConfigException(Throwable cause, String uimaConfigFile,
      String uimaDataPath, String extendedClasspath, String additionalMessage) {
    super(cause,
        prepareMessage(uimaConfigFile, uimaDataPath, extendedClasspath, additionalMessage));
    this.uimaConfigFile = uimaConfigFile;
    this.uimaDataPath = uimaDataPath;
    this.extendedClasspath = extendedClasspath;
  }

  /**
   * Create InvalidTokenizerConfigException instance capturing the combination of configuration
   * parameters that resulted in this exception. This exception captures the scenario when the UIMA
   * config file claimed to support a set of langauges, but tokenization failed for a subset of
   * them.
   * 
   * @param cause Original exception thrown when Text Analytics runtime attempted to instantiate
   *        tokenizer
   * @param uimaConfigFile Absolute path of the UIMA configuration file used to instantiate
   *        tokenizer
   * @param uimaDataPath Data path for UIMA
   * @param extendedClasspath Additional classpath used to load resources needed for multi-lingual
   *        processing
   * @param supportedLangCodes List of languages claimed to be supported by the uimaConfigFile
   * @param problematicLangCodes List of languages for which the tokenizer could not successfully
   *        tokenize an input string
   */
  public InvalidTokenizerConfigException(String uimaConfigFile, String uimaDataPath,
      String extendedClasspath, String[] supportedLangCodes, String[] problematicLangCodes) {
    super(prepareMessage(uimaConfigFile, uimaDataPath, extendedClasspath, supportedLangCodes,
        problematicLangCodes));
    this.uimaConfigFile = uimaConfigFile;
    this.uimaDataPath = uimaDataPath;
    this.extendedClasspath = extendedClasspath;
    this.langCodesWithIncorrectConfig = problematicLangCodes;
  }

  /**
   * Returns the list of language codes whose configuraiton is incorrect
   * 
   * @return the list of language codes whose configuraiton is incorrect
   */
  public String[] getLangCodesWithIncorrectConfig() {
    return langCodesWithIncorrectConfig;
  }

  // Prepares a message detailing the combination of UIMA config parameters
  private static String prepareMessage(String uimaConfigFile, String uimaDataPath,
      String extendedClasspath) {
    return String.format(
        "Failed to instantiate multi-lingual tokenizer with the specified parameters. Verify that the following parameters are valid.\nUIMA Config file: %s\nUIMA Datapath: %s\nExtended classpath: %s",
        uimaConfigFile, uimaDataPath, extendedClasspath);
  }

  // Prepares a message detailing the combination of tokenizer config parameters and an additional
  // message
  private static String prepareMessage(String uimaConfigFile, String uimaDataPath,
      String extendedClasspath, String additionalMessage) {
    StringBuilder message =
        new StringBuilder(prepareMessage(uimaConfigFile, uimaDataPath, extendedClasspath));
    if (additionalMessage != null && additionalMessage.length() > 0) {
      message.append(Constants.NEW_LINE);
      message.append("Additional message:\n");
      message.append(additionalMessage);
    }

    return message.toString();
  }

  // Prepares a message detailing the combination of UIMA config parameters and the list of
  // supported languages and
  // problematic languages
  private static String prepareMessage(String uimaConfigFile, String uimaDataPath,
      String extendedClasspath, String[] supportedLangCodes, String[] problematicLangCodes) {
    // Prepare the basic message first
    StringBuilder message =
        new StringBuilder(prepareMessage(uimaConfigFile, uimaDataPath, extendedClasspath));

    // Add details about supported languages, if not null
    if (supportedLangCodes != null && supportedLangCodes.length > 0) {
      message.append(Constants.NEW_LINE);
      message.append(
          "Following is the list of languages configured in the UIMA configuration file: [");

      // sort the lang codes first
      Arrays.sort(supportedLangCodes);

      for (String supportedLang : supportedLangCodes) {
        message.append(supportedLang).append(" ");
      }
      message.append(" ]");

      // Add details about problematic languages, if not null
      if (problematicLangCodes != null && problematicLangCodes.length > 0) {
        message.append(Constants.NEW_LINE);
        message.append("The configuration for the following languages is incorrect: [ ");

        // sort the lang codes first
        Arrays.sort(problematicLangCodes);

        for (String probLangCode : problematicLangCodes) {
          message.append(probLangCode).append(" ");
        }
        message.append("]");
      }
    }

    return message.toString();
  }
}
