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
package com.ibm.avatar.algebra.util.dict;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.util.TreeMap;

import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.util.data.StringPairList;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aog.AOGConversionException;
import com.ibm.avatar.aog.AOGOpTree;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.aql.AbstractAQLParseTreeNode;
import com.ibm.avatar.aql.tam.ModuleUtils;
import com.ibm.avatar.logging.Log;

/**
 * Class that encapsulates the matching parameters associated with a given AQL dictionary.
 */
public class DictParams implements Comparable<DictParams> {

  /*
   * CONSTANTS
   */

  /**
   * Different types of case-sensitivity
   */
  public enum CaseSensitivityType {
    exact, // Case-sensitive matching
    insensitive, // Standard case-insensitive matching
    folding, // Unicode case folding
    ;

    /**
     * Decode the old "dictionary match type" strings that we used to use in the old syntax.
     * 
     * @throws AOGConversionException
     */
    public static CaseSensitivityType decodeStr(AQLFunc func, String matchType)
        throws FunctionCallValidationException {
      if (AOGOpTree.DictionaryOp.EXACT_MATCH_STR.equals(matchType)) {
        return exact;
      } else if (AOGOpTree.DictionaryOp.IGNORE_CASE_STR.equals(matchType)) {
        return insensitive;
      } else if (null == matchType || 0 == matchType.length()
          || AOGOpTree.DictionaryOp.DEFAULT_MATCH_STR.equals(matchType)) {
        return null;
      } else {
        throw new FunctionCallValidationException(func,
            String.format("Don't understand dictionary" + " match mode '%s'", matchType));
      }
    }
  }

  // Key values for the key-value mapping used to encode dict params

  // Module name of the dictionary
  public static final String MODULE_PARAM = "module";

  // AQL name of the dictionary
  public static final String NAME_PARAM = "name";

  // Name of the lookup table used to create the dictionary
  public static final String TABLE_PARAM = "table";

  // Column in lookup table in which to find dictionary entries
  public static final String COLUMN_PARAM = "column";

  // Name of the external file containing the dictionary entries
  public static final String FILE_PARAM = "file";

  // Whether to perform case-sensitive matching by default
  public static final String CASE_PARAM = "case";

  // What language(s) to use for matching
  public static final String LANG_PARAM = "language";

  // Flag to indicate external dictionary
  public static final String IS_EXTERNAL = "isExternal";

  // Flag to indicate, if empty external dictionary is allowed.
  public static final String ALLOW_EMPTY = "allowEmpty";

  // Flag to indicate if external dictionary filename is required.
  public static final String REQUIRED = "required";

  // Flag to indicate that the dictionary matching should be performed against lemmatized form of
  // the document
  public static final String LEMMA_MATCH = "lemma_match";

  /*
   * FIELDS
   */

  /** AQL name of the dictionary */
  private String dictName = null;

  /** Name of the module where CreateDictionary statement is defined */
  private String moduleName = null;

  /**
   * Returns the fully qualified name of the dictionary
   * 
   * @return fully qualified name of the dictionary
   */
  public String getDictName() {
    return ModuleUtils.prepareQualifiedName(moduleName, dictName);
  }

  /**
   * Returns the unqualified name of the dictionary
   * 
   * @return unqualified name of the dictionary
   */
  public String getUnqualifiedName() {
    return dictName;
  }

  /**
   * Sets the unqualified name of the dictionary
   * 
   * @param dictName unqualified name of the dictionary
   */
  public void setDictName(String dictName) {
    this.dictName = dictName;
  }

  /**
   * Sets the module name of the dictionary
   * 
   * @param moduleName name of the module where CreateDictionary statement is defined
   */
  public void setModuleName(String moduleName) {
    this.moduleName = moduleName;
  }

  /** Name of the backing table for the dictionary, if any. */
  private String tabName = null;

  public String getTabName() {
    return tabName;
  }

  public void setTabName(String tabName) {
    this.tabName = tabName;
  }

  /** Name of column in backing table containing dictionary entries. */
  private String tabColName = null;

  public String getTabColName() {
    return tabColName;
  }

  public void setTabColName(String tabColName) {
    this.tabColName = tabColName;
  }

  /** Name of dictionary file, if any. */
  private String fileName = null;

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  /** Default case-sensitivity parameter. */
  private CaseSensitivityType defaultCase = CaseSensitivityType.insensitive;

  public CaseSensitivityType getDefaultCase() {
    return defaultCase;
  }

  public void setCase(CaseSensitivityType caseCond) {
    defaultCase = caseCond;
  }

  /**
   * Flag that is set to TRUE if this dictionary is defined inline inside the "create dictionary"
   * statement.
   */
  private boolean isInline = false;

  public void setIsInline(boolean isInline) {
    this.isInline = isInline;
  }

  public boolean getIsInline() {
    return isInline;
  }

  /**
   * String that describes what language(s) to perform matching over.
   */
  private String langStr = null;

  public String getLangStr() {
    return langStr;
  }

  public void setLangStr(String langStr) {
    this.langStr = langStr;
  }

  /**
   * This flag, when set<code>true</code> marks the dictionary external; By default dictionary is
   * marked internal
   */
  private boolean isExternal = false;

  /**
   * Method to mark dictionary external.When marked external entries for dictionary are provided
   * while loading extractor.
   * 
   * @param isExternal - if <code>true</code>, marks the dictionary external.
   */
  public void setIsExternal(boolean isExternal) {
    this.isExternal = isExternal;
  }

  /**
   * @return returns true, if the dictionary is declared external.
   */
  public boolean getIsExternal() {
    return this.isExternal;
  }

  /**
   * This flag, when set to <code>true</code> indicates that empty external dictionaries are
   * allowed; if set <code>false</code> loader will throw error for empty external dictionary.<br>
   * Deprecated As of v3.0.1 -- use the "required" flag instead
   */
  @Deprecated
  private Boolean allowEmpty = null;

  /**
   * Method to mark that empty external dictionary is allowed. Allow empty flag is applicable only
   * for external dictionary; setting this flag for internal dictionary will throw exception.
   * 
   * @param allowEmpty - if <code>true</code>, marks that empty external dictionary is allowed.
   * @UnsupportedOperationException - if invoked for internal dictionary.
   * @deprecated As of v3.0.1, use {@link #setRequired(boolean)} with the inverse logical value
   *             instead
   */
  @Deprecated
  public void setAllowEmpty(boolean allowEmpty) {

    if (!this.isExternal)
      throw new UnsupportedOperationException(
          "Allow empty flag is applicable only for external dictionaries");

    this.allowEmpty = allowEmpty;

  }

  /**
   * This method returns <code>true</code>, if empty external dictionary is allowed.
   * 
   * @return <code>true</code> if empty external dictionary is allowed.
   * @UnsupportedOperationException - if invoked for internal dictionary.
   * @deprecated As of v3.0.1, use {@link #isRequired()} instead
   */
  @Deprecated
  public Boolean isAllowEmpty() {
    if (!this.isExternal)
      throw new UnsupportedOperationException(
          "Allow empty flag is applicable only for external dictionaries");

    return this.allowEmpty;
  }

  /**
   * This flag, when set to <code>true</code> indicates that a dictionary file must be specified
   * when the operator graph is instantiated, or loader will throw error for missing external
   * dictionary file. The file may be empty.<br>
   * The flag can be set either via {@link setRequired} or {@link setAllowEmpty}.
   */
  private Boolean required = null;

  /**
   * Method to mark that external dictionary file name is required to be specified. Required flag is
   * applicable only for external dictionary; setting this flag for internal dictionary will throw
   * exception.
   * 
   * @param required - if <code>true</code>, marks that external dictionary file name must be
   *        specified.
   * @UnsupportedOperationException - if invoked for internal dictionary.
   */
  public void setRequired(boolean required) {
    if (!this.isExternal)
      throw new UnsupportedOperationException(
          "Required flag is applicable only for external dictionaries");

    this.required = required;
  }

  /**
   * This method returns <code>true</code>, if external dictionary filename is required.
   * 
   * @return <code>true</code> if external dictionary filename is required.
   * @UnsupportedOperationException - if invoked for internal dictionary.
   */
  public Boolean isRequired() {
    if (!this.isExternal)
      throw new UnsupportedOperationException(
          "Required flag is applicable only for external dictionaries");

    return this.required;
  }

  private boolean supportLemmaMatch = false;

  public void setSupportLemmaMatch(boolean supportLemmaMatch) {
    this.supportLemmaMatch = supportLemmaMatch;
  }

  public boolean supportLemmaMatch() {
    return supportLemmaMatch;
  }

  /**
   * Main constructor
   * 
   * @param paramStrs list of name-value pairs containing config params for this dictionary
   */
  public DictParams(StringPairList paramStrs) {
    final boolean debug = false;

    TreeMap<String, String> paramsMap = paramStrs.toMap();

    // Read the values of the parameters.
    if (paramsMap.containsKey(MODULE_PARAM)) {
      moduleName = paramsMap.get(MODULE_PARAM);
    }
    if (paramsMap.containsKey(NAME_PARAM)) {
      dictName = paramsMap.get(NAME_PARAM);
    }
    if (paramsMap.containsKey(TABLE_PARAM)) {
      tabName = paramsMap.get(TABLE_PARAM);
    }
    if (paramsMap.containsKey(COLUMN_PARAM)) {
      tabColName = paramsMap.get(COLUMN_PARAM);
    }
    if (paramsMap.containsKey(FILE_PARAM)) {
      fileName = paramsMap.get(FILE_PARAM);
    }
    if (paramsMap.containsKey(CASE_PARAM)) {
      defaultCase = CaseSensitivityType.valueOf(paramsMap.get(CASE_PARAM));
    }
    if (paramsMap.containsKey(LANG_PARAM)) {
      langStr = paramsMap.get(LANG_PARAM);
    }
    if (paramsMap.containsKey(IS_EXTERNAL)) {
      isExternal = Boolean.valueOf(paramsMap.get(IS_EXTERNAL));
    }
    if (paramsMap.containsKey(ALLOW_EMPTY)) {
      allowEmpty = Boolean.valueOf(paramsMap.get(ALLOW_EMPTY));
    }
    if (paramsMap.containsKey(REQUIRED)) {
      required = Boolean.valueOf(paramsMap.get(REQUIRED));
    }
    if (paramsMap.containsKey(LEMMA_MATCH)) {
      supportLemmaMatch = Boolean.valueOf(paramsMap.get(LEMMA_MATCH));
    }
    // Add additional parameters here.

    if (debug) {
      Log.debug("Dictionary params:\n"//
          + "    module     = %s\n"//
          + "    name       = %s\n"//
          + "    tabName    = %s\n"//
          + "    tabColName = %s\n"//
          + "    fileName   = %s\n" //
          + "    caseType   = %s\n" //
          + "    langStr    = %s\n" //
          + "    isExternal    = %s\n" //
          + "    allowEmpty    = %s\n" //
          + "    required      = %s\n" //
          + "    supportLemmaMatch = %s\n" //
          , moduleName, dictName, tabName, tabColName, fileName, defaultCase, langStr, isExternal,
          allowEmpty, required, supportLemmaMatch);
    }
  }

  /**
   * Convert this object's parameter settings into a set of key-value pairs.
   */
  public StringPairList toKeyValuePairs() {
    StringPairList ret = new StringPairList();

    addIfNotNull(ret, NAME_PARAM, getDictName());
    addIfNotNull(ret, TABLE_PARAM, tabName);
    addIfNotNull(ret, COLUMN_PARAM, tabColName);
    addIfNotNull(ret, FILE_PARAM, fileName);
    addIfNotNull(ret, LANG_PARAM, langStr);
    // Add additional parameters here.

    // Always add the default case.
    ret.add(CASE_PARAM, defaultCase.toString());
    // Always add flag to mark dictionary external or internal.
    ret.add(IS_EXTERNAL, Boolean.toString(this.isExternal));

    // allow empty and required flags are only applicable to external dictionaries, and
    // only one flag is ever set
    if (this.isExternal) {
      if (isAllowEmpty() != null) {
        ret.add(ALLOW_EMPTY, Boolean.toString(isAllowEmpty()));
      } else {
        ret.add(REQUIRED, Boolean.toString(isRequired()));
      }
    }
    // Always add flag to mark lemma match or not.
    ret.add(LEMMA_MATCH, Boolean.toString(this.supportLemmaMatch));

    return ret;
  }

  private static void addIfNotNull(StringPairList list, String key, String val) {
    if (null != val) {
      list.add(key, val);
    }
  }

  /** Ensure that this set of parameters is valid. */
  public void validate() {
    // Sanity check
    if (null == dictName) {
      throw new IllegalArgumentException("No dictionary name provided");
    }

    // Get at the tuples by the appropriate method.
    if (isInline) {
      // Dictionary is defined inline
    } else if (null != fileName) {
      // Dictionary is in a file.
      if (null != tabName) {
        throw new IllegalArgumentException(
            "Dictionary declaration specifies both" + " table and external file");
      }
    } else {
      // Dictionary comes from a table
      if (null == tabName) {
        throw new IllegalArgumentException("Dictionary declaration specifies neither"
            + " table nor external file and is not inline");
      }
    }

    // Verify that the given language codes string contains only supported language codes
    if (null != langStr) {

      // Validate: Given language codes string should be non empty
      if (langStr.trim().length() == 0) {
        throw new IllegalArgumentException("No language code specified.");
      }

      // Validate: Given language codes string should contain only the allowed number of language
      // codes and supported
      // language codes
      LangCode.validateLangStr(langStr);
    }

  }

  /**
   * Constructor for when dictionary parameters need to be created by hand.
   */
  public DictParams() {}

  /** Copy constructor. */
  public DictParams(DictParams o) {
    defaultCase = o.defaultCase;
    moduleName = o.moduleName;
    dictName = o.dictName;
    fileName = o.fileName;
    isInline = o.isInline;
    langStr = o.langStr;
    tabColName = o.tabColName;
    tabName = o.tabName;
    supportLemmaMatch = o.supportLemmaMatch;

    // Sanity check
    if (0 != compareTo(o)) {
      throw new RuntimeException("Copy constructor failed to create an exact copy");
    }
  }

  /**
   * Generate the AQL boilerplate to specify the parts of this set of parameters that go in the
   * "with" clause of a create dictionary statement.
   * 
   * @param stream where to print to
   * @param indent how much to indent what we print.
   */
  public void dumpAQL(PrintWriter stream, int indent) {

    // with
    // [case (exact | insensitive | folding | from <column name>)]
    // [and language (as '<language code(s)>' | from <column name>)]
    // [and lemma_match [from <column name>]

    // with entries from column
    if (tabColName != null) {
      AbstractAQLParseTreeNode.printIndent(stream, indent);
      stream.printf("with entries from %s", tabColName);
    }

    // Always print case argument
    String caseStr;
    if (defaultCase == CaseSensitivityType.exact) {
      caseStr = "exact";
    } else if (defaultCase == CaseSensitivityType.insensitive) {
      caseStr = "insensitive";
    } else if (defaultCase == CaseSensitivityType.folding) {
      caseStr = "folding";
    } else {
      throw new RuntimeException("Don't know about case type " + defaultCase);
    }

    AbstractAQLParseTreeNode.printIndent(stream, indent);
    if (tabColName != null)
      stream.printf("\nand case %s", caseStr);
    else
      stream.printf("with case %s", caseStr);

    // Other options may or may not be there.
    if (null != langStr) {
      stream.printf("\n");
      AbstractAQLParseTreeNode.printIndent(stream, indent + 1);
      stream.printf("and language as %s", StringUtils.quoteStr('\'', langStr));
    }

    if (supportLemmaMatch) {
      stream.printf("\n");
      AbstractAQLParseTreeNode.printIndent(stream, indent + 1);
      stream.printf("and lemma_match");
    }
  }

  @Override
  public int compareTo(DictParams o) {

    // Try to keep these comparisons in alphabetical order.
    int val = compareWithNulls(defaultCase, o.defaultCase);
    if (val != 0) {
      return val;
    }

    val = compareWithNulls(moduleName, o.moduleName);
    if (val != 0) {
      return val;
    }

    val = compareWithNulls(dictName, o.dictName);
    if (val != 0) {
      return val;
    }

    val = compareWithNulls(fileName, o.fileName);
    if (val != 0) {
      return val;
    }

    if (isInline && false == o.isInline) {
      return 1;
    }
    if (false == isInline && o.isInline) {
      return -1;
    }

    val = compareWithNulls(langStr, o.langStr);
    if (val != 0) {
      return val;
    }

    val = compareWithNulls(tabColName, o.tabColName);
    if (val != 0) {
      return val;
    }

    val = compareWithNulls(tabName, o.tabName);
    if (val != 0) {
      return val;
    }

    val = compareWithNulls(supportLemmaMatch, o.supportLemmaMatch);
    if (val != 0) {
      return val;
    }

    // If we get here, the two inputs are identical.
    return 0;
  }

  /** A version of compareTo() that handles null values properly. */
  @SuppressWarnings("unchecked")
  private <T> int compareWithNulls(T first, T second) {

    if (null == first) {
      if (null == second) {
        return 0;
      } else {
        // Nulls sort lower.
        return -1;
      }
    } else {
      return ((Comparable<T>) first).compareTo(second);
    }
  }

  /** @return a human-readable version of this set of parameters. */
  @Override
  public String toString() {
    CharArrayWriter buf = new CharArrayWriter();
    toKeyValuePairs().toPerlHash(new PrintWriter(buf), 0, false);

    return String.format("DictParams(%s)", buf.toString());
  }
}
