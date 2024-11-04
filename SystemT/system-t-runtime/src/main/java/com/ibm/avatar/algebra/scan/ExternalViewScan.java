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
package com.ibm.avatar.algebra.scan;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.MultiInputOperator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.ExternalView;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;

/**
 * A scan over an external view.
 * 
 */
public class ExternalViewScan extends MultiInputOperator {

  private boolean debug = false;

  /**
   * Id of buffer in the memoization table where we store the tuples of this external view operator.
   */
  private int tupsBufIx;

  @Override
  public boolean outputIsAlwaysTheSame() {
    // ExternalViewScan does not produce the same output.
    return false;
  }

  /** The actual external view to scan. */
  private ExternalView view;

  /**
   * Main constructor.
   */
  public ExternalViewScan(ExternalView view) {
    super();
    this.view = view;
  }

  @Override
  protected void initStateInternal(MemoizationTable mt) {

    // Create a fresh buffer in the memoization table
    // where the tuples of this external view operator are to be stored
    tupsBufIx = mt.createResultsBuf(view.getSchema());

    if (debug) {
      System.err.printf("***Created result buffer for ExternalViewScan '%s'\n", view);
      System.err.printf("***ExternalViewScan '%s' tupBufIx=%d, resultBufIx=%d\n", view, tupsBufIx,
          resultsBufIx);
    }
  }

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList[] childResults) throws Exception {

    // Obtain a pointer to this operator's results buffer
    TupleList results = mt.getResultsBuf(resultsBufIx);

    // Retrieve the tuples from the associated buffer in the memoization table
    // and store them in the results buffer
    results.addAllNoChecks(mt.getResultsBuf(tupsBufIx));

    if (debug)
      System.err.printf("***ExternalViewScan returning:\n%s\n", results.toPrettyString());
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {
    return view.getSchema();
  }

  /**
   * Store the tuples of the external view.
   * 
   * @param mt
   * @param tups
   */
  public void setTups(MemoizationTable mt, TupleList tups) {

    // Retrieve the buffer in the memoization table where we store our tuples.
    TupleList viewTups = mt.getResultsBuf(tupsBufIx);

    // Clear the buffer and store the tuples.
    viewTups.clear();
    viewTups.addAllNoChecks(tups);

    if (debug)
      System.err.printf("***ExternalViewScan tuples set to:\n%s\n", viewTups.toPrettyString());
  }

  /**
   * Clear the tuples of the external view.
   * 
   * @param mt
   * @param tups
   */
  public void clearTups(MemoizationTable mt) {

    // Retrieve the buffer in the memoization table where we store our tuples.
    TupleList viewTups = mt.getResultsBuf(tupsBufIx);

    // Clear the buffer.
    viewTups.clear();

    if (debug)
      System.err.printf("***ExternalViewScan tuples set to:\n%s\n", viewTups.toPrettyString());
  }

  /**
   * Returns the external name of the external view of this operator.
   * 
   * @return
   */
  public String getViewNameExternal() {
    return view.getExternalName();
  }

  /**
   * Returns the schema of the associated external view object, as declared via the create external
   * view statement in AQL. Needed in order to order to populate tuples of the external view via the
   * UIMA API. This is because the schema returned via {@link #getOutputSchema()} might have been
   * overridden by {@link #addProjection()}.
   * 
   * @return
   */
  public TupleSchema getExternalViewSchema() {
    return view.getSchema();
  }

}
