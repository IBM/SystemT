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
package com.ibm.avatar.algebra.extract;

import java.util.ArrayList;
import java.util.Arrays;

import com.ibm.avatar.algebra.base.ExtractionOp;
import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.systemt.regex.api.Regex;
import com.ibm.systemt.regex.api.RegexMatcher;

/**
 * Evaluates a regular expression on existing annotations, creating a new tuple for each match. Uses
 * either SimpleRegex or Java regular expression matchers.
 * 
 */
public class RegularExpression extends ExtractionOp {

  /** Index into the input tuples where the child annotation is located */
  String col;

  /**
   * Which capturing groups we make annotations for and what outputs they go to.
   */
  protected ArrayList<Pair<Integer, String>> groups;

  /** Default name for the output column of a RegularExpression operator. */
  public static final String DEFAULT_OUTPUT_COL_NAME = "match";

  /**
   * A "default" set of groups (e.g. group 0 becomes the output called "match"), for convenience.
   */
  public static final ArrayList<Pair<Integer, String>> JUST_GROUP_ZERO;
  static {
    JUST_GROUP_ZERO = new ArrayList<Pair<Integer, String>>();
    JUST_GROUP_ZERO.add(new Pair<Integer, String>(0, DEFAULT_OUTPUT_COL_NAME));
  }

  /** Accessors for writing the results of the capturing groups. */
  protected FieldSetter<Span> groupAcc[];

  /** Flag for turning on debug messages */
  private static final boolean debug = false;

  Regex regex;

  /**
   * Create a new RegularExpression operator that annotates a user-configured set of capturing
   * groups.
   * 
   * @param child
   * @param col index into the input tuples where the child annotation is located
   * @param regex the regular expression
   * @param groups which capturing groups in the pattern to annotate.
   */
  public RegularExpression(Operator child, String col, Regex regex,
      ArrayList<Pair<Integer, String>> groups) {
    // Pass in NULL as the output name; we'll do our own schema generation.
    super(col, null, child);

    // Unpack the ArrayList into arrays to reduce our runtime overhead.

    this.groups = groups;

    this.col = col;
    this.regex = regex;
  }

  /**
   * Convenience constructor that annotates the entire match as "match"
   * 
   * @param child
   * @param col index into the input tuples where the child annotation is located
   * @param regex the regular expresssion
   */
  public RegularExpression(Operator child, String col, Regex regex) {
    this(child, col, regex, JUST_GROUP_ZERO);
  }

  /**
   * Override the superclass's implementation to add an annotation column for each capturing group.
   */
  @SuppressWarnings("unchecked")
  @Override
  protected AbstractTupleSchema createOutputSchema() {

    AbstractTupleSchema inputSchema = getInputOp(0).getOutputSchema();

    // Our output schema needs to have a column for each capturing group.
    FieldType[] grpColTypes = new FieldType[groups.size()];
    Arrays.fill(grpColTypes, FieldType.SPAN_TYPE);
    String[] grpNames = new String[groups.size()];
    for (int i = 0; i < groups.size(); i++) {
      grpNames[i] = groups.get(i).second;
    }

    // We pass through all the columns of the input schema, as they may be
    // needed by the EXTRACT list of the enclosing extract statement.
    AbstractTupleSchema ret = new TupleSchema(inputSchema, grpNames, grpColTypes);

    // Bind our accessors to the schema.
    inputAcc = inputSchema.asSpanAcc(col);
    copier = ret.fieldCopier(inputSchema);
    groupAcc = new FieldSetter[groups.size()];
    for (int i = 0; i < groups.size(); i++) {
      groupAcc[i] = ret.spanSetter(groups.get(i).second);
    }

    // System.out.println ("RegularExpression.createOutputSchema: " + ret);
    return ret;
  }

  @Override
  protected void extract(MemoizationTable mt, Tuple inputTup, Span inputSpan) throws Exception {

    // the inputSpan is expected to be non-null because ExtractionOp.reallyEvaluate() already
    // filters
    // out null tuples
    CharSequence text = inputSpan.getText();

    RegexMatcher matcher = mt.getMatcher(regex);
    matcher.reset(text);

    while (matcher.find()) {
      Tuple resultTuple = createOutputTup();

      copier.copyVals(inputTup, resultTuple);

      for (int i = 0; i < groups.size(); i++) {
        int grp = groups.get(i).first;
        int begin = matcher.start(grp);
        int end = matcher.end(grp);

        if (debug) {
          System.err.printf("Match from %d to %d of %d-char string '%s'\n", begin, end,
              text.length(), text);
        }

        Span span;
        if (-1 == begin) {
          // SPECIAL CASE: No match for this particular capturing
          // group.
          span = null;
          // END SPECIAL CASDE
        } else {
          span = Span.makeSubSpan(inputSpan, begin, end);
        }
        groupAcc[i].setVal(resultTuple, span);
      }
      addResultTup(resultTuple, mt);
    }
  }
}
