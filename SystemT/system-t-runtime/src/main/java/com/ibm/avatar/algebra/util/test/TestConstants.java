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
package com.ibm.avatar.algebra.util.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;

/** Global constants for regression tests. */
public class TestConstants {

  /** Name of the system property that may hold the base directory for tests. */
  public static final String TEST_DIR_PROPNAME = "avatar.test.dir";

  public static final String TEST_WORKING_DIR;

  static {
    // Use the user-specified working directory if possible.
    if (null != System.getProperty(TEST_DIR_PROPNAME)) {
      TEST_WORKING_DIR = System.getProperty(TEST_DIR_PROPNAME);
    } else {
      TEST_WORKING_DIR = ".";
    }
  }

  public static final String TESTDATA_DIR = TEST_WORKING_DIR + "/testdata";

  public static final String AOG_DIR = TESTDATA_DIR + "/aog";

  /** Directory containing AQL files for testing purposes. */
  public static final String AQL_DIR = TESTDATA_DIR + "/aql";

  /** Directory containing AQL from the Extractor Library annotators project */
  public static final String EXTRACTOR_LIB_DIR = AQL_DIR + "/annotators";
  // public static final String EXTRACTOR_LIB_DIR = TEST_WORKING_DIR + "/../ExtractorLibrary";

  /** Directory containing pre-compiled module files for testing purposes. */
  public static final String PRECOMPILED_MODULES_DIR = TESTDATA_DIR + "/tam";

  public static final String TEST_DOCS_DIR = TESTDATA_DIR + "/docs";

  public static final String DUMPS_DIR = TESTDATA_DIR + "/docs/common";

  public static final String RESOURCES_DIR = TESTDATA_DIR + "/resources";


  public static final TokenizerConfig STANDARD_TOKENIZER = new TokenizerConfig.Standard();

  /** A single-document input file, for memory profiling. */
  public static final String ENRON_1_DUMP = DUMPS_DIR + "/enron1.del";

  public static final String ENRON_100_DUMP = DUMPS_DIR + "/enron100.del";

  public static final String ENRON_1K_DUMP = DUMPS_DIR + "/enron1k.del";

  /**
   * Dump file containing the first 10000 documents of the Enron data set; created by
   * {@link CreateEnronDumps#main(String[])}
   */
  public static final String ENRON_10K_DUMP = DUMPS_DIR + "/enron10k.del";

  public static final String ENRON_37939_DUMP = DUMPS_DIR + "/enron38k.del";

  /** Dump file containing Enron emails in the 12-16K size range */
  public static final String ENRON_12_16_DUMP = DUMPS_DIR + "/enron12k16k.del";

  /** Dump file containing Enron emails in the 4-8K size range */
  public static final String ENRON_4_8_DUMP = DUMPS_DIR + "/enron4k8k.del";

  /**
   * Dump file containing roughly the first 2000 documents in the spock.com data set.
   */
  public static final String SPOCK_DUMP = DUMPS_DIR + "/spock.del";

  /**
   * First 100 documents of the Spock.com dump
   */
  public static final String SPOCK_100_DUMP = DUMPS_DIR + "/spock100.del";

  /**
   * Tarfile of web docs from Spock.com.
   */
  public static final String SPOCK_TARFILE = DUMPS_DIR + "/spockdocs.tgz";

  /**
   * Zip archive containing a sample of the enron dataset; note that this is a *different* sample
   * from the one in {@link #ENRON_37939_DUMP}.
   */
  public static final String ENRON_SAMPLE_ZIP = DUMPS_DIR + "/ensample.zip";

  /**
   * Zip archive containing a 25k document sample of the Enron data set (NOTE: Different sample from
   * ensample.zip!), with the original headers intact
   */
  public static final String ENRON_HDRS_25K_ZIP = DUMPS_DIR + "/enhdrs25k.zip";

  /**
   * Sub-sample of {@link #ENRON_SAMPLE_ZIP}
   */
  public static final String ENRON_SMALL_ZIP = DUMPS_DIR + "/ensmall.zip";

  /**
   * SPSS Artifacts
   */
  public static final String SPSS_DIR = "SPSS";
  public static final String SPSS_AQL_SRC_DIR = String.format("%s/%s", AQL_DIR, SPSS_DIR);
  public static final String SPSS_DOCS_FILE =
      String.format("%s/%S/food4students-comment.del", TEST_DOCS_DIR, SPSS_DIR);
  public static final String SPSS_PRECOMPILED_MODULE_PATH =
      String.format("%s/%s", PRECOMPILED_MODULES_DIR, SPSS_DIR);
  public static final String[] SPSS_INPUT_MODULES = new String[] {"concepts", "concepts_en",
      "excludes_en", "nonling_en", "nonlings", "pos_base_and_forced_en", "pos_en", "pos_forced_en",
      "pos_u_en", "pospatterns_en", "tla", "tla_en", "type_user_en"};
  public static final String SPSS_EXTERNAL_DICT_NAME = "abbrev_en.dict_abbrev_en";
  public static final String SPSS_EXTERNAL_DICT_PATH =
      String.format("%s/dictionaries/%s/abbreviations_en.dict", TESTDATA_DIR, SPSS_DIR);
  /**
   * Tarfile of four HTML documents from imdb.com.
   */
  public static final String IMDB_TARFILE = DUMPS_DIR + "/imdb.tar.gz";

  public static final String IOPES_EMAIL_DUMP = DUMPS_DIR + "/iopesemail.del";

  /** Zip file of 20 SEC 13 filings */
  public static final String SEC_13FILING_20_ZIP = DUMPS_DIR + "/13filingsSEC_sample_20.zip";

  /** 1000 Cisco logs */
  public static final String CISCO_LOGS_1000 = DUMPS_DIR + "/logs/cisco_syslog_oct_1.del";

  /** 1000 Twitter messages */
  public static final String TWITTER_MOVIE_1000 = DUMPS_DIR + "/twitterMovie1000.del";

  /** Default simple doc schema */
  public static final TupleSchema BASIC_DOC_SCHEMA = DocScanInternal.createOneColumnSchema();

  /**
   * Empty list of compiled dictionaries; this will be used to run AOGs without any reference to
   * dictionary
   */
  public static ArrayList<CompiledDictionary> EMPTY_CD_LIST = new ArrayList<CompiledDictionary>();

  /**
   * Empty list of compiled dictionaries; this will be used to run AOGs without any reference to
   * dictionary
   */
  public static Map<String, CompiledDictionary> EMPTY_CD_MAP =
      new HashMap<String, CompiledDictionary>();

}
