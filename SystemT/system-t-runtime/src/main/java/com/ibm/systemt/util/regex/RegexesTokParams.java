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
package com.ibm.systemt.util.regex;

import java.io.PrintWriter;

import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aog.AOGOpTree;
import com.ibm.avatar.aog.ColumnRef;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.aog.Token;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.aql.RegexNode;

/**
 * Class that encapsulates the parameters of the RegexesTok operator, for a particular one of the
 * expressions it evaluates.
 */
public class RegexesTokParams implements Comparable<RegexesTokParams> {
  // The regular expression
  private RegexNode regex;

  // Name of the output column for regex matches
  private ColumnRef outputCol;

  private String flagsStr;

  // Flags that control matching
  private int flags;

  // Minimum number of tokens to match
  private int minTok;

  // Maximum number of tokens to match
  private int maxTok;

  // Internal name for this expression (currently only used in the
  // compiler)
  // private String name;

  public RegexesTokParams(RegexNode regex, ColumnRef outputCol, String flagsStr, int minTok,
      int maxTok) throws ParseException {
    this.regex = regex;
    this.outputCol = outputCol;
    this.flagsStr = flagsStr;
    try {
      this.flags = FlagsString.decode(null, flagsStr);
    } catch (FunctionCallValidationException e) {
      throw new ParseException(
          String.format("At line %d of AOG, error decoding regex flags string '%s'",
              regex.getOrigTok().beginLine, flagsStr),
          e);
    }
    this.minTok = minTok;
    this.maxTok = maxTok;
  }

  /**
   * @return Internal name for this expression (currently only used in the AQL compiler)
   */
  // public String getName() {
  // return name;
  // }

  /**
   * @param name Internal name for this expression (currently only used in the AQL compiler)
   */
  // public void setName(String name) {
  // this.name = name;
  // }

  public String getRegexStr() {
    return regex.getRegexStr();
  }

  /**
   * @return perl-style regex string, enclosed in forward slashes; the regex "foo/bar" becomes
   *         /foo\/bar/
   */
  public String getPerlRegexStr() {
    return regex.getPerlRegexStr();
  }

  public String getOutputColName() {
    return outputCol.getColName();
  }

  public int getFlags() {
    return flags;
  }

  /**
   * @return the regex evaluation flags, in a format that {@link FlagsString#decode(String, Token)}
   *         will understand.
   */
  public CharSequence getFlagsStr() {
    return FlagsString.encode(flags);
  }

  public int getMinTok() {
    return minTok;
  }

  public int getMaxTok() {
    return maxTok;
  }

  /**
   * Print out the parameters, in the same format that the parser parsed them.
   */
  public void dump(PrintWriter stream, int indent) throws ParseException {

    // Format: (regex, flags, min, max) => "name"

    // Put the regex on a separate line, with the opening paren.
    AOGOpTree.printIndent(stream, indent);
    stream.print("(");
    regex.dump(stream, 0);
    stream.print(",\n");

    // Remaining arguments go on second line.
    AOGOpTree.printIndent(stream, indent + 1);
    stream.printf("%s, %d, %d) => %s", StringUtils.quoteStr('"', flagsStr), minTok, maxTok,
        StringUtils.quoteStr('"', getOutputColName()));

  }

  @Override
  public int hashCode() {
    int accum = 0;
    accum += flags * 31;
    accum += flagsStr.hashCode();
    accum <<= 1;
    accum += maxTok;
    accum <<= 1;
    accum += minTok;
    // accum += name.hashCode();
    // accum <<= 1;
    accum += outputCol.hashCode();
    accum += regex.hashCode();
    return accum;
  }

  @Override
  public boolean equals(Object o) {
    if (false == (o instanceof RegexesTokParams)) {
      return false;
    }

    RegexesTokParams other = (RegexesTokParams) o;

    if (flags != other.flags) {
      return false;
    }

    if (false == flagsStr.equals(other.flagsStr)) {
      return false;
    }

    if (minTok != other.minTok) {
      return false;
    }

    if (maxTok != other.maxTok) {
      return false;
    }

    // if (null == name) {
    // if (null != other.name) {
    // return false;
    // }
    // } else {
    // if (false == name.equals(other.name)) {
    // return false;
    // }
    // }

    if (false == outputCol.equals(other.outputCol)) {
      return false;
    }

    if (false == regex.equals(other.regex)) {
      return false;
    }

    return true;
  }

  @Override
  public int compareTo(RegexesTokParams other) {

    int val = regex.compareTo(other.regex);
    if (0 != val)
      return val;

    val = outputCol.compareTo(other.outputCol);
    if (0 != val)
      return val;

    val = flags - other.flags;
    if (0 != val)
      return val;

    val = flagsStr.compareTo(other.flagsStr);
    if (0 != val)
      return val;

    val = minTok - other.minTok;
    if (0 != val)
      return val;

    val = maxTok - other.maxTok;
    if (0 != val)
      return val;

    // Comparisons on additional fields go here

    // If we get here, the two parameters are equal
    return 0;
  }


}
