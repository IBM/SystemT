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

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.function.scalar.RegexConst;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aog.ConstFuncNode;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * A regular expression literal. Used both in AQL compilation and in AOG parsing.
 */
public class RegexNode extends RValueNode {

  public static final String TYPE_NAME = "Regex";

  @SuppressWarnings("unused")
  private final boolean debug = false;

  // Token origTok;

  /** The regular expression. */
  private final String regex;

  /** Number of groups in the regular expression. */
  private final int numberOfGroups;

  /**
   * Name of the regex engine to use; set by optimizer during Regex Strength Reduction.
   */
  private String engineName = RegexConst.JAVA_REGEX_ENGINE_NAME;

  public RegexNode(String regex, int numberOfGroups, String containingFileName, Token origTok) {
    // set error location info
    super(TYPE_NAME, containingFileName, origTok);

    this.regex = regex;
    this.numberOfGroups = numberOfGroups;
  }

  public RegexNode(String regex, int numberOfGroups) {
    // set error location info
    super(TYPE_NAME, null, null);

    this.regex = regex;
    this.numberOfGroups = numberOfGroups;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.print(getPerlRegexStr());
  }

  @Override
  public String toString() {
    return getPerlRegexStr();
  }

  public String getRegexStr() {
    return regex;
  }

  public int getNumberOfGroups() {
    return numberOfGroups;
  }

  /**
   * @return perl-style regex string, enclosed in forward slashes; the regex "foo/bar" becomes
   *         /foo\/bar/
   */
  public String getPerlRegexStr() {
    String ret = StringUtils.quoteStr('/', regex, false, true);
    // System.err.printf("Regex %s turns into %s when quoted.\n", regex,
    // ret);
    return ret;
  }

  @Override
  public int reallyCompareTo(AQLParseTreeNode o) {
    RegexNode other = (RegexNode) o;
    return regex.compareTo(other.regex);
  }

  /*
   * @Override public void toAOG(PrintStream stream, int indent) { printIndent(stream, indent); //
   * RegexConst() expects its input in Perl syntax. stream.print(getPerlRegexStr()); }
   */

  @Override
  public Object toAOGNode(Catalog catalog) {
    // try {
    // return asFunction().toAOGNode();
    // } catch (ParseException e) {
    // // We should never catch a ParseException here!
    // throw new RuntimeException(e);
    // }

    return new ConstFuncNode.Regex(this, getEngineName());
  }

  @Override
  public ScalarFnCallNode asFunction() throws ParseException {
    throw new UnsupportedOperationException("This method should never be called");

    // Put together the arguments to create a function parse tree node
    // In particular, create a parse tree node for:
    // RegexConst( <regex>, <engineName> )
    // NickNode fname = new NickNode(RegexConst.FNAME, catalog);
    // ArrayList<RValueNode> args = new ArrayList<RValueNode>();
    // args.add(new StringNode(regex, catalog));
    // args.add(new StringNode(engineName, catalog));
    //
    // FunctionNode ret = new FunctionNode(fname, args, catalog);
    //
    // if (debug) {
    // Log.debug("Returning %s", ret);
    // }
    //
    //
    //
    // return ret;

    // return new FunctionNode(RegexConst.FNAME, this, catalog);
  }

  @Override
  public int hashCode() {
    return regex.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (false == (o instanceof RegexNode)) {
      return false;
    }

    RegexNode other = (RegexNode) o;

    return (regex.equals(other.regex));
  }

  public String getEngineName() {
    return engineName;
  }

  public void setEngineName(String engineName) {
    this.engineName = engineName;
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action

  }

  @Override
  public FieldType getType(Catalog c, AbstractTupleSchema schema) {
    // Calling code should not be trying to convert a regex literal to a scalar type.
    throw new FatalInternalError("This method should never be called.");
  }

}
