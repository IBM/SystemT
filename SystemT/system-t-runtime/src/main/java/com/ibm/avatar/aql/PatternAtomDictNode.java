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
import java.util.List;

import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.util.data.StringPairList;
import com.ibm.avatar.algebra.util.dict.DictParams;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.catalog.Catalog;

public class PatternAtomDictNode extends PatternAtomNode {

  private final StringNode entry;
  private StringPairList paramsStrs;

  public PatternAtomDictNode(StringNode entry) {
    // set the error location info
    super(entry.getContainingFileName(), entry.getOrigTok());

    this.entry = entry;
    paramsStrs = new StringPairList();
  }

  public StringNode getEntry() {
    return entry;
  }

  public void setParams(StringPairList paramsStrs) throws ParseException {
    this.paramsStrs = paramsStrs;
  }

  public StringPairList getParams() {
    return paramsStrs;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    Pair<String, String> param;

    for (int i = 0; i < paramsStrs.size(); i++) {
      param = paramsStrs.get(i);

      if (param.first.equals(DictParams.CASE_PARAM)) {
        // Validity ensured by the parser
      } else if (param.first.equals(DictParams.LANG_PARAM)) {
        // Validate the language code
        LangCode.strToLangCode(param.second);
      } else if (param.first.equals(DictParams.LEMMA_MATCH)) {
        // Validity ensure by the parser
      } else
        errors.add(AQLParserBase.makeException(entry.getOrigTok(),
            "Invalid dictionary match parameter '%s' in sequence pattern specification",
            param.first));
    }

    return errors;
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    PatternAtomDictNode other = (PatternAtomDictNode) o;

    int val = entry.compareTo(other.entry);
    if (val != 0)
      return val;

    return compareNodeLists(paramsStrs, other.paramsStrs);
  }

  @Override
  public ArrayList<ColNameNode> getCols() {
    return new ArrayList<ColNameNode>();
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    // Fix for RTC 58105 - Escape single quote characters
    String entryStr = StringUtils.quoteStr('\'', entry.getStr(), false, false);

    // No parameters to write out
    if (paramsStrs.size() == 0) {
      stream.printf("%s", entryStr);
    } else {

      // Write out the entry string and the parameters
      stream.print("<");

      // Write the entry string
      stream.printf("%s", entryStr);

      // Write the parameters
      Pair<String, String> param;

      // with
      // [case (exact | insensitive | folding | from <column name>)]
      // [and language (as '<language code(s)>' | from <column name>)]
      // [and lemma_match [from <column name>]

      stream.print("[with ");
      for (int i = 0; i < paramsStrs.size(); i++) {
        if (i > 0)
          stream.print(" and ");

        param = paramsStrs.get(i);

        if (param.first.equals(DictParams.CASE_PARAM))
          stream.printf("case %s", param.second);
        else if (param.first.equals(DictParams.LEMMA_MATCH))
          stream.printf("%s", DictParams.LEMMA_MATCH);
        else if (param.first.equals(DictParams.LANG_PARAM))
          stream.printf("language as %s", StringUtils.quoteStr('\'', param.second));
        else {
          throw new FatalInternalError("Don't know about dictionary parameter " + param.first);
        }
      }
      stream.print("]");

      stream.print(">");
    }
  }

  @Override
  public String toString() {

    String entryStr = StringUtils.quoteStr('\'', entry.getStr(), true, true);

    // No parameters to write out
    if (paramsStrs.size() == 0)
      return entryStr;

    // Write out the entry string and the parameters
    StringBuilder out = new StringBuilder();
    out.append("<");

    // Write the entry string
    out.append(entryStr);

    // Write the parameters
    out.append("[");
    for (int i = 0; i < paramsStrs.size(); i++) {
      if (i > 0)
        out.append(", ");
      out.append(paramsStrs.get(i));
    }
    out.append("]");

    out.append(">");

    return out.toString();
  }

  @Override
  public boolean matchesEmpty() {
    // return entry.getStr().isEmpty();
    // Changed by Fred for Java 5 compatibility
    return (0 == (entry.getStr().length()));
  }

  /**
   * @return Names of pass through columns that must be projected out by this node, in the form
   *         <viewAlias>.<attributeName>
   */
  @Override
  public ArrayList<PatternPassThroughColumn> getPassThroughCols() {
    // No columns to pass through from a dictionary node
    return new ArrayList<PatternPassThroughColumn>();
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action
  }

}
