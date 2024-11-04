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
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.OutputBuffer;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.api.Constants;

/**
 * Document scanner for pushing documents into an operator graph one at a time.
 * 
 */
public class DocScan extends Operator {

  /**
   * Output buffer object; used for storing the document tuples that are pushed into this operator.
   * We use the MemoizationTable's buffering facilities to ensure that this operator has no
   * persistent state.
   */
  private static class docBuf extends OutputBuffer {

    Tuple docTup;

    private docBuf(Tuple docTup) {
      this.docTup = docTup;
    }

    @Override
    public void close() {
      // Nothing to do here.
    }

  }

  /**
   * Schema of our document tuples. Can be changed by subclasses by using
   * {@link #DocScan(AbstractTupleSchema)}.
   */
  private AbstractTupleSchema docSchema;

  /** Constructor that also sets up a custom document schema. */
  // @SuppressWarnings("deprecation")
  public DocScan(AbstractTupleSchema docSchema) {
    super(new Operator[0]);

    this.docSchema = docSchema;

    // Set up the view name for profiling purposes to "DocScan"
    // TODO: Long-term, should rename the view name to "Document",
    // but we are keeping it as "DocScan" for bkwds compatibility -- eyhung
    // setViewName (AOGParseTree.DOC_SCAN_BUILTIN, true);
    // setViewName(AOGParseTree.DOC_TYPE_NAME);
  }

  /**
   * Put in place the next document for processing.
   * 
   * @param text to be wrapped in a temporary document object
   */
  public void setDoc(Tuple docTup, MemoizationTable mt) {
    // System.err.printf("%s setting doc %s\n", this, docTup.getOid());
    mt.cacheOutputBuf(this, new docBuf(docTup), true);
  }

  /**
   * Return the schema this docscan was instantiated with.
   */
  @Override
  protected AbstractTupleSchema createOutputSchema() {
    return docSchema;
  }

  /**
   * Return the schema this docscan was instantiated with
   */
  public TupleSchema getDocSchema() {
    return (TupleSchema) createOutputSchema();
  }

  @Deprecated
  /**
   * This function is no longer guaranteed to return a correct text column since the document schema
   * is specified in AQL.
   */
  public String getTextColName() {
    return Constants.DOCTEXT_COL;
  }

  @Override
  /**
   * Function that grabs the next document in the scan.
   * 
   * @param mt
   */
  protected void getNextInternal(MemoizationTable mt) throws Exception {

    docBuf buf = (docBuf) mt.getOutputBuf(this);

    if (null == buf) {
      throw new RuntimeException(
          String.format("Output buffer for DocScan not properly initialized"));
    }

    Tuple docTup = buf.docTup;

    if (null == docTup) {
      throw new RuntimeException(String.format("DocScan.getNextInternal() "
          + "called without passing in a valid document " + "tuple first"));
    }

    // System.err.printf("%s returning doc %s\n", this, docTup.getOid());

    addResultTup(docTup, mt);

  }

}
