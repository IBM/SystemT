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
package com.ibm.avatar.algebra.output;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.Tee;
import com.ibm.avatar.algebra.base.Tee.TeeOutput;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;

/**
 * Operator that pulls inputs from any number of local analysis operators, making sure that all
 * operators stay in sync, and discards everything it gets. For each input document, outputs a
 * single tuple with an integer equal to the number of interior tuples read and discarded. Useful
 * for when the primary means of producing output of your query plan is via one or more "persist"
 * operators embedded in the plan. This operator also allows its inputs to be selectively
 * "disabled"; subsequent calls to getNext() will ignore the disabled inputs. Use this feature at
 * your own risk.
 * 
 */
public class Sink extends Operator {

  /** Name of the single integer column of the operator's output tuples. */
  public static final String OUTPUT_COL_NAME = "tupleCount";

  /** Internal counter of how many interior tuples we've thrown away. */
  private int totDiscarded = 0;

  /** Setter for putting our tuple counts into output tuples. */
  private FieldSetter<Integer> outAcc;

  /** Special for retrieving tuples from the Sink's inputs. */
  // FieldGetter<Object> inAcc = new BypassTypeChecks<Object>(0);

  /**
   * For each input to this Sink, a list of which Tee outputs to clear if the input is disabled.
   */
  TeeOutput[][] toClear = null;

  /**
   * Default constructor;
   * 
   * @param inputs roots of the subtrees that feed into this sink.
   */
  public Sink(Operator[] inputs) {
    super(inputs);

    // Charge this operator's overhead to "Output"
    super.profRecord.viewName = "Output";
  }

  @Override
  protected void initStateInternal(MemoizationTable mt) {
    // Sink operators need to initialize their "which outputs are enabled"
    // state
    boolean[] enabled = new boolean[inputs.length];
    java.util.Arrays.fill(enabled, true);
    mt.cacheEnabledFlags(this, enabled);

    // Build up the list of which Tee outputs to clear when skipping an
    // input.
    toClear = new TeeOutput[inputs.length][];
    for (int i = 0; i < inputs.length; i++) {
      toClear[i] = Tee.getBufClearTargets(inputs[i]);
    }
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {

    // Output schema always consists of a single integer column. There is no
    // boxed set of interior tuples.
    AbstractTupleSchema ret = new TupleSchema(OUTPUT_COL_NAME, FieldType.INT_TYPE);

    // Set up accessors
    outAcc = ret.intSetter(OUTPUT_COL_NAME);
    return ret;
  }

  /** @return the number of interior tuples this sink has discarded. */
  public int getNumDiscarded() {
    return totDiscarded;
  }

  public void enableAllOutputs(MemoizationTable mt) {
    for (int i = 0; i < inputs.length; i++) {
      setOutputEnabled(mt, i, true);
    }
  }

  public void disableAllOutputs(MemoizationTable mt) {
    for (int i = 0; i < inputs.length; i++) {
      setOutputEnabled(mt, i, false);
    }
  }

  public void setOutputEnabled(MemoizationTable mt, int ix, boolean val) {
    boolean[] enabled = mt.getEnabledFlags(this);
    enabled[ix] = val;
  }

  public boolean getOutputEnabled(MemoizationTable mt, int ix) {
    boolean[] enabled = mt.getEnabledFlags(this);
    return enabled[ix];
  }

  @Override
  protected void getNextInternal(MemoizationTable mt) throws Exception {
    boolean[] enabled = mt.getEnabledFlags(this);

    // SPECIAL CASE: All inputs are disabled. Don't check for EOF, just
    // return.
    {
      boolean noInputsEnabled = true;
      for (int i = 0; i < inputs.length; i++) {
        if (enabled[i]) {
          noInputsEnabled = false;
        }
      }

      if (noInputsEnabled) {
        return;
      }
    }
    // END SPECIAL CASE

    // Discard input tuples, counting how many interior tuples we discarded.
    int ndiscard = 0;
    // boolean haveMoreData = false;
    for (int i = 0; i < inputs.length; i++) {
      Operator in = inputs[i];

      if (enabled[i]) {

        // System.err.printf("Sink: %s is enabled.\n", in);

        // System.err.printf("*****Sink calling child: %s\n", in);

        TupleList inTups = in.getNext(mt);
        ndiscard += inTups.size();

      } else {
        // System.err.printf("*****Sink NOT calling child: %s\n", in);

        // Even though the output is disabled, we still need to tell it
        // to clear any buffers it may have used.
        if (null != toClear[i]) {
          for (TeeOutput todo : toClear[i]) {
            todo.clearCachedState(mt);
          }
        }
      }
    }

    totDiscarded += ndiscard;

    Tuple ret = getOutputSchema().createTup();
    outAcc.setVal(ret, ndiscard);
    addResultTup(ret, mt);
  }

}
