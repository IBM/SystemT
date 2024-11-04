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
package com.ibm.avatar.api;

/**
 * Container for various constants used by the Text Analytics Java API.
 */
public interface Constants {
  /**
   * Default name of the document text column.
   */
  public static final String DOCTEXT_COL = "text";

  /**
   * Default name of the document label column, when such a label exists.
   */
  public static final String LABEL_COL_NAME = "label";

  /**
   * Default name of the document tuple type.
   */
  public static final String DEFAULT_DOC_TYPE_NAME = "Document";

  public static final char SINGLE_QUOTE = '\'';
  public static final String NEW_LINE = "\n";
  public static final String SEMI_COLON = ";";

  // String constants for field types
  // public static final String STRING_TYPE = "String";
  // public static final String INT_TYPE = "Integer";
  // public static final String FLOAT_TYPE = "Float";
  // public static final String BOOLEAN_TYPE = "Boolean";
  // public static final String SPAN_TYPE = "Span";
  // public static final String REGEX_TYPE = "Regex";
  // public static final String TEXT_TYPE = "Text";
  // public static final String SCALAR_LIST_TYPE = "ScalarList";
  // public static final String UNKNOWN_TYPE = "Unknown";

  public static final char COMMA = ',';

  /** File extension for compiled dictionary files */
  public static final String COMPILED_DICT_FILE_EXT = ".cd";

  /** Default name given to modules created out of non-modular AQL */
  public static final String GENERIC_MODULE_NAME = "genericModule";

  /** Supported filesystem schemes */
  public static final String LOCALFS_URI_SCHEME = "file:";
  public static final String HDFS_URI_SCHEME = "hdfs:";
  public static final String GPFS_URI_SCHEME = "gpfs:";

  /**
   * Delimiter character for search paths. To keep config files consistent, we use a single
   * character across operating systems.
   */
  public static final char MODULEPATH_SEP_CHAR = ';';

  /**
   * Path separator of the operating system
   */
  public static final String OS_PATH_SEPARATOR = System.getProperty("path.separator");

  /**
   * Default field separator for CSV files
   */
  public static final char DEFAULT_CSV_FIELD_SEPARATOR = ',';

  /** Extension of AQL files */
  public static final String AQL_FILE_EXTENSION = ".aql";

  /** Encoding string for UTF-8 */
  public static final String ENCODING_UTF8 = "UTF-8";

  /** Module name and object(view/dictionary/table/function) name separator */
  public static final char MODULE_ELEMENT_SEPARATOR = '.';

  /** Extension for JSON input files */
  public static final String JSON_EXTENSION = ".json";

  /** Extension for CSV input files */
  public static final String CSV_EXTENSION = ".csv";

  /** Extension for the jar file */
  public static final String JAR_EXTENSION = ".jar";

  /** Extension for the zip file */
  public static final String ZIP_EXTENSION = ".zip";

  /** Extension for the compiled text analytics module(TAM) file */
  public static final String TAM_EXTENSION = ".tam";

  /**
   * Name of the file that contains the comments to be attached to a single module. This file should
   * be located directly in the module directory. If the module directory does not contain any file
   * by this name, we assume there is no module comment. If such a file exists, we copy its entire
   * content as module comment and store it in the catalog. Later on, the module comment is
   * serialized inside the module metadata.
   */
  public static final String MODULE_AQL_DOC_COMMENT_FILE_NAME = "module.info";

  /** Value to be used in metadata.xml, when the host name is not known */
  public static final String UNKNOWN_HOSTNAME = "Unknown";

  /**
   * Release version number of text analytics -- semantic version is ignored. <br>
   * Starting with v3.0.1, compiled dictionaries also use this numbering scheme.
   */
  public static final String PRODUCT_VERSION = "4.1";

  /** How many simultaneous threads we allow to access an operator graph. */
  public static final int NUM_THREAD_SLOTS = 16;

}
