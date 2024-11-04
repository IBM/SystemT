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
package com.ibm.avatar.aql;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ibm.avatar.aog.AOGOpTree;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.DictCatalogEntry;
import com.ibm.avatar.aql.planner.DictInvocation;

/** Parse tree node for a dictionary extraction specification. */
public class DictExNode extends ExtractionNode {

  /**
   * Set this flag to TRUE to check each dictionary reference for validity, using the Catalog. This
   * flag should be hard-coded to TRUE once the new compile API is in place.
   */
  private final static boolean VALIDATE_DICTIONARIES = true;

  /**
   * If no flags string is specified, we use the following flags to control dictionary matching:
   */
  public static final String DEFAULT_FLAGS = AOGOpTree.DictionaryOp.DEFAULT_MATCH_STR;

  /** Names of the dictionaries (inline dicts or external files) */
  private final ArrayList<StringNode> dicts;

  /** String that describes the flags to pass to the regex engine. */
  private final StringNode flagsStr;

  /** Name of column in output tuples where dictionary results go. */
  private final NickNode outputCol;

  /**
   * Set of flags that indicate whether to use the shared dictionary matching for each dictionary in
   * {@link #dicts}.
   */
  private final boolean[] useSDM;

  public DictExNode(ArrayList<StringNode> dicts, StringNode flagsStr, ColNameNode targetName,
      NickNode outputCol, String containingFileName, Token origTok) throws ParseException {
    super(targetName, outputCol, containingFileName, origTok);

    if (null == flagsStr) {
      // No flags provided; use the default.
      flagsStr = new StringNode(DEFAULT_FLAGS);
    }

    this.dicts = dicts;
    this.flagsStr = flagsStr;
    this.outputCol = outputCol;

    this.useSDM = new boolean[dicts.size()];
    Arrays.fill(useSDM, true);
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    // Make sure that the requested dictionaries exist.
    for (StringNode dict : dicts) {
      // The following method will throw an exception if the indicated
      // dictionary does not exist.
      if (VALIDATE_DICTIONARIES) {
        try {
          DictCatalogEntry dictCatalogEntry =
              catalog.lookupDict(dict.getStr(), dict.getOrigTok(), dict.getContainingFileName());

          // dictCatalogEntry will be null for the dictionary file encountered for the first time.
          // For such implicitly declared dictionary: add entry in the catalog
          if (null == dictCatalogEntry) {
            catalog.addImplicitDict(getModuleName(), dict.getStr(), dict.getOrigTok(),
                dict.getContainingFileName());
          }

          // ensure that the dict name is a valid name. Do this validation after implicit dictionary
          // references are
          // added to catalog
          if (false == catalog.isValidDictionaryReference(dict.getStr())) {
            errors.add(AQLParserBase.makeException(dict.getOrigTok(), String.format(
                "Dictionary '%s' is not a valid reference. Ensure that the dictionary is defined and is visible in the current module, accessible by the given name.",
                dict.getStr())));
          }
        } catch (ParseException e) {
          errors.add(e);
        }

      }
    }

    // Make sure that the specified matching flags are valid.
    if (AOGOpTree.DictionaryOp.EXACT_MATCH_STR.equals(flagsStr.getStr())
        || AOGOpTree.DictionaryOp.IGNORE_CASE_STR.equals(flagsStr.getStr())
        || DEFAULT_FLAGS.equals(flagsStr.getStr())) {
      // Flags are ok.
    } else {
      errors.add(AQLParser.makeException(
          String.format("Don't understand flags '%s'", flagsStr.getStr()), flagsStr.getOrigTok()));

    }

    return errors;
  }

  public int getNumDicts() {
    return dicts.size();
  }

  public StringNode getDictName(int ix) {
    return this.dicts.get(ix);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    stream.print(1 == dicts.size() ? "dictionary " : "dictionaries ");
    for (int i = 0; i < dicts.size(); i++) {
      if (i > 0) {
        printIndent(stream, indent + 7);
        stream.print(" and ");
      }
      dicts.get(i).dump(stream, 0);
      stream.print("\n");
    }

    // flagsStr is optional. Check for null before calling dump() method
    if ((flagsStr != null) && (DEFAULT_FLAGS.equals(flagsStr.getStr()) == false)) {
      printIndent(stream, indent);
      stream.print("with flags ");
      flagsStr.dump(stream, 0);
      stream.print("\n");
    }

    printIndent(stream, indent);
    stream.printf("on %s as ", super.getTargetName().getColName());
    outputCol.dump(stream, 0);
    stream.printf("\n");
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    DictExNode other = (DictExNode) o;

    int val = super.compareTarget(other);
    if (0 != val) {
      return val;
    }

    val = dicts.size() - other.dicts.size();
    if (0 != val) {
      return val;
    }
    for (int i = 0; i < dicts.size(); i++) {
      val = dicts.get(i).compareTo(other.dicts.get(i));
    }
    return val;
  }

  public StringNode getFlagsStr() {
    return flagsStr;
  }

  public void setUseSDM(int ix, boolean useSDM) {
    this.useSDM[ix] = useSDM;
  }

  /**
   * @param ix index of one of the dictionaries in this extraction
   * @return true if this dictionary should be evaluated using Shared Dictionary Matching if
   *         possible; false otherwise.
   */
  public boolean getUseSDM(int ix) {
    return useSDM[ix];
  }

  @Override
  public int getNumInputCopies() {
    return getNumDicts();
  }

  public DictInvocation toDictInvocation(int dictIx) {

    // System.err.printf(
    // "Creating dictionary invocation for element %d (%s) at:\n", dictIx,
    // dicts.get(dictIx).getStr());
    // new Throwable().printStackTrace();

    return new DictInvocation(dicts.get(dictIx).getStr(), flagsStr.getStr());
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // qualify all the dictionary references
    for (StringNode dict : this.dicts) {
      dict.str = catalog.getQualifiedDictName(dict.str);
    }
  }
}
