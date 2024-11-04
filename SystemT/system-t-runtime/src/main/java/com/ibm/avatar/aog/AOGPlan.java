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
package com.ibm.avatar.aog;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.ProfileRecord;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.output.Sink;
import com.ibm.avatar.algebra.scan.DocScan;
import com.ibm.avatar.algebra.scan.ExternalViewScan;
import com.ibm.avatar.algebra.util.tokenize.StandardTokenizer;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig.Standard;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.OperatorGraphImpl;
import com.ibm.avatar.api.exceptions.InvalidOutputNameException;

/**
 * Class that encapsulates an AOG query plan. To instantiate, call AOGParseTree.toPlan()
 * 
 */
public class AOGPlan {
  /* The document scan operator */
  private DocScan docScan;

  /** Root of the operator DAG that this object encapsulates. */
  private Sink root;

  /**
   * Accessor for reading annotation counts out of the tuples that the root returns.
   */
  private FieldGetter<Integer> getCount;

  /**
   * Memoization table used for storing the operators' temporary data.
   */
  // private MemoizationTable mt;
  /**
   * Memoization tables (one per thread) used for storing the operators' temporary data.
   */
  private MemoizationTable[] mt = null;

  /**
   * Accessor for getting at the "number of tuples" column that the root node produces.
   */
  // private FieldGetter<Integer> numTupAcc;
  /** Number of tuples from the current document. */
  private int ntups = -1;

  /** The output operators of the plan, indexed by output nickname. */
  private Map<String, Operator> outputs;

  /** Map from output name to index of output in the inputs of the root node. */
  private Map<String, Integer> outputNameToIx;

  /**
   * List of all subtree nicknames (e.g. AQL view names) that occur in the plan.
   */
  private ArrayList<String> allNames;

  /**
   * Map from external view name to corresponding external view scan operator.
   */
  Map<String, ExternalViewScan> externalViewScanOps;

  /**
   * If we are supposed to be using an external tokenizer, the configuration file for said
   * tokenizer; otherwise, null to indicate that we should use the built-in tokenizer.
   */
  private TokenizerConfig tokenizerCfg = null;

  /**
   * @return list of all view names that occur in the operators of the plan.
   */
  public ArrayList<String> getViewNames() {
    return allNames;
  }

  /**
   * Constructor for use by AOGParseTree class only.
   * 
   * @param allNames list of all subtree nicknames used in the plan.
   * @param tokenizerCfg factory object for creating tokenizer instances
   */
  protected AOGPlan(Sink root, Map<String, Operator> outputs, Map<String, Integer> outputNameToIx,
      ArrayList<String> allNames, TokenizerConfig tokenizerCfg) {
    this.root = root;
    this.outputs = outputs;
    this.outputNameToIx = outputNameToIx;
    this.allNames = allNames;
    // TupleSchema rootSchema = root.getOutputSchema();
    // numTupAcc = rootSchema.intAcc(Sink.OUTPUT_COL_NAME);

    this.tokenizerCfg = tokenizerCfg;

    // Create MemoizationTable slots for all the thread slots, and initialize the MemoizationTable
    // for *only* the first
    // thread slot; for other thread slots, MemoizationTable instance will be created on-demand,
    // when OG.execute()
    // method
    // is invoked.
    try {
      this.mt = new MemoizationTable[Constants.NUM_THREAD_SLOTS];
      mt[0] = getMemoizationTable(0);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    if (outputNameToIx.size() != root.getNumInputs()) {
      throw new RuntimeException(
          String.format("Output name map has %d entries, " + "but root of plan has %d inputs",
              outputNameToIx.size(), root.getNumInputs()));
    }

    // build a map of external view name -> external scan op
    externalViewScanOps = new HashMap<String, ExternalViewScan>();
    HashSet<Operator> visitedOps = new HashSet<Operator>();
    collectExternalViewScanOps(root, visitedOps);

    // traverse the optrees to find and set the docscan
    visitedOps = new HashSet<Operator>();
    findDocScanOp(root, visitedOps);

    // Set up the accessor for getting at the annotation counts the root
    // produces.
    getCount = root.getOutputSchema().intAcc(Sink.OUTPUT_COL_NAME);
  }

  /**
   * Builds a map from external view names to associated ExternalViewScan operators.
   * 
   * @param op input operator
   * @param visitedOps HashSet of operators visited already
   */
  private void collectExternalViewScanOps(Operator op, HashSet<Operator> visitedOps) {

    // Avoid visiting the same operator twice
    if (visitedOps.contains(op))
      return;

    // Mark the operator as visited
    visitedOps.add(op);

    // Check if this is an ExternalViewScan operator
    if (op instanceof ExternalViewScan) {
      externalViewScanOps.put(op.getViewName(), (ExternalViewScan) op);
    } else {
      // Recursively visit the inputs of the operator
      for (int i = 0; i < op.getNumInputs(); i++)
        collectExternalViewScanOps(op.getInputOp(i), visitedOps);
    }
  }

  /**
   * Searches the operator trees for the document scan and sets it in the plan.
   * 
   * @param op input operator
   * @param visitedOps HashSet of operators visited already
   */
  private void findDocScanOp(Operator op, HashSet<Operator> visitedOps) {

    // Avoid visiting the same operator twice
    if (visitedOps.contains(op))
      return;

    // Mark the operator as visited
    visitedOps.add(op);

    // Check if this is an DocScan operator
    if (op instanceof DocScan) {
      if (docScan == null) {
        docScan = (DocScan) op;
      } else {
        throw new RuntimeException("Error: two docscan operators found in AOG plan");
      }
    } else {
      // Recursively visit the inputs of the operator
      for (int i = 0; i < op.getNumInputs(); i++)
        findDocScanOp(op.getInputOp(i), visitedOps);
    }
  }

  /**
   * Given the name (in AQL) of an external view, returns the associated ExternalViewScan operator.
   * 
   * @param externalView
   * @return
   */
  public ExternalViewScan getExternalViewScanOp(String externalView) {
    return externalViewScanOps.get(externalView);
  }

  /**
   * Returns the names (in AQL) of all external views.
   * 
   * @return
   */
  public String[] getExternalViewNames() {
    String[] externalViews = new String[externalViewScanOps.size()];
    externalViewScanOps.keySet().toArray(externalViews);
    return externalViews;
  }

  private synchronized MemoizationTable makeMemoizationTable() {
    try {
      // Make sure that every MemoizationTable is using the correct
      // tokenizer implementation.
      // We need to set up the tokenizer first, because the
      // MemoizationTable will try to initialize the dictionaries.
      Tokenizer t;

      // Log.debug("Using tokenizer config file '%s'",
      // tokenizerCfgFile);

      if (null == tokenizerCfg) {
        // Default internal implementation
        t = new StandardTokenizer();
      } else {
        // External tokenizer.
        boolean lemmaReference = hasLemmaReference(); // if lemma is used anywhere
        // Log.debug ("AOG Lemma Reference = %s", lemmaReference);
        if (!(tokenizerCfg instanceof Standard)) {
          boolean partOfSpeechReference = hasPartOfSpeechReference();
          t = tokenizerCfg.makeTokenizer(lemmaReference, partOfSpeechReference);
        } else {
          if (lemmaReference)
            t = tokenizerCfg.makeTokenizer(true);
          else
            t = tokenizerCfg.makeTokenizer();
        }
      }

      return new MemoizationTable(root, t);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public int getNumThreads() {
    return mt.length;
  }

  /**
   * Advance all operators in the plan to the next document.
   */
  // public void nextDoc() throws Exception {
  // nextDoc(0);
  // }
  /**
   * Advance all operators in the plan to the next document.
   * 
   * @param threadIx index of the thread (and corresponding MemoizationTable) that we're being
   *        called from.
   */
  public void nextDoc(int threadIx) throws Exception {
    MemoizationTable mt = getMemoizationTable(threadIx);
    mt.resetCache();
    TupleList tups = root.getNext(mt);

    // The root will return a single tuple, containing one field: The number
    // of annotations it just received.
    if (tups.size() > 0) {
      Tuple tup = tups.getElemAtIndex(0);
      ntups = getCount.getVal(tup);
    } else {
      ntups = 0;
    }
  }

  /**
   * @return true if the plan has more input documents/annotations to consume.
   */
  public boolean hasNextDoc() {
    return !getMemoizationTable(0).endOfInput();
  }

  /**
   * @return the total number of output tuples for the current document.
   */
  public int getCurNumTups() {
    return ntups;
  }

  /**
   * @param nickname a nickname that was mapped to an output in the input AOG file.
   * @return the corresponding instantiated output operator
   */
  public Operator getOutputOp(String nickname) throws InvalidOutputNameException {
    Operator ret = outputs.get(nickname);
    if (null == ret) {
      throw new InvalidOutputNameException(nickname, getValidOutputNames());
    }
    return ret;
  }

  /**
   * @return map from name to output operator
   */
  public Map<String, Operator> getOutputOps() {
    return outputs;
  }

  /**
   * Pretty-print the plan to your favorite output stream.
   * 
   * @param stream where to send output
   * @param indent how far to indent the top level of the plan (in increments of 2 spaces)
   */
  public void dump(PrintWriter stream, int indent) throws ParseException {
    root.dump(stream, indent);
  }

  /**
   * Toggle whether a particular output of the plan is enabled. If an output is disabled,
   * computation that is necessary to compute it will be ignored.
   * 
   * @param outputName name of the output
   * @param val true to enable to output, false to disable it
   */
  // public void setOutputEnabled(String outputName, boolean val) {
  // setOutputEnabled(outputName, val, 0);
  // }
  /**
   * Toggle whether a particular output of the plan is enabled. If an output is disabled,
   * computation that is necessary to compute it will be ignored.
   * 
   * @param outputName name of the output
   * @param val true to enable to output, false to disable it
   * @param threadIx TODO
   */
  public void setOutputEnabled(String outputName, boolean val, int threadIx) {
    if (outputNameToIx.containsKey(outputName)) {
      int ix = outputNameToIx.get(outputName);
      // root.getInputOp(ix).setEnabled(val);
      root.setOutputEnabled(getMemoizationTable(threadIx), ix, val);
    } else {
      throw new RuntimeException(String.format("Don't know about output name '%s'", outputName));
    }

  }

  // public void enableAllOutputs() {
  // enableAllOutputs(0);
  // }

  /**
   * Make sure that every output is enabled. If {@link #DISABLE_UNUSED_VIEWS} is true, does
   * <b>not</b> enable the (dummy) outputs corresponding to unused views.
   * 
   * @param threadIx index of the calling thread (controls which memoization table is used)
   */
  public void enableAllOutputs(int threadIx) {
    root.enableAllOutputs(getMemoizationTable(threadIx));
  }

  /**
   * @return all outputs of the graph, even those that don't go anywhere
   */
  public ArrayList<String> getAllOutputNames() {
    ArrayList<String> ret = new ArrayList<String>();
    ret.addAll(outputNameToIx.keySet());
    return ret;
  }

  /**
   * @return outputs of the graph that will go to an output operator
   */
  public ArrayList<String> getValidOutputNames() {
    ArrayList<String> ret = new ArrayList<String>();
    ret.addAll(outputs.keySet());
    return ret;
  }

  // public void disableAllOutputs() {
  // disableAllOutputs(0);
  // }

  /**
   * Equivalent to calling {@link #setOutputEnabled(String, boolean, int)} for every output name.
   * 
   * @param threadIx index of the calling thread (controls which memoization table is used)
   */
  public void disableAllOutputs(int threadIx) {
    root.disableAllOutputs(getMemoizationTable(threadIx));
  }

  /**
   * Single-threaded version of {@link #getOutputEnabled(String, int)}
   */
  // public boolean getOutputEnabled(String outputName) {
  // return getOutputEnabled(outputName, 0);
  // }
  /**
   * @param outputName name of one of the outputs of the encapsulated operator graph
   * @param threadIx index of the calling thread (controls which memoization table is used)
   * @return true if the indicated output is enabled for the indicated thread
   */
  public boolean getOutputEnabled(String outputName, int threadIx) {
    if (outputNameToIx.containsKey(outputName)) {
      int ix = outputNameToIx.get(outputName);
      // return root.getInputOp(ix).getEnabled();
      return root.getOutputEnabled(getMemoizationTable(threadIx), ix);
    } else {
      throw new RuntimeException(String.format("Don't know about output name '%s'", outputName));
    }
  }

  /** Provide access for OperatorGraphRunner. Also used internally. */
  public final MemoizationTable getMemoizationTable(int threadIx) {

    // Don't acquire a mutex unless we suspect the memoization table hasn't
    // been created.
    if (null == mt[threadIx]) {
      synchronized (this) {
        // Use a critical section and repeat the check to ensure that
        // the memoization table doesn't get initialized more than once.
        if (null == mt[threadIx]) {
          mt[threadIx] = makeMemoizationTable();
        }
      }
    }
    return mt[threadIx];
  }

  public void closeOutputs(int threadIx) {
    getMemoizationTable(threadIx).closeOutputs();
  }

  public void clearBuffers(int threadIx) {
    getMemoizationTable(threadIx).resetCache();
  }

  /**
   * Mark all iterators of result buffers used by this thread as not in use. This method is only
   * called on exceptions in {@link OperatorGraphImpl#execute(Tuple, String[], Map)} to ensure that
   * the OperatorGraph state is completely reset and there are no tuple lists left in use when the
   * next document comes in this thread.
   * 
   * @param threadIx
   */
  public void markResultsBufDone(int threadIx) {
    getMemoizationTable(threadIx).markResultsBufDone();
  }

  public ProfileRecord getCurCodeLoc(int threadIx) {
    return getMemoizationTable(threadIx).profilePeek();
  }

  /**
   * @param docScan the docScan to set
   */
  public void setDocScan(DocScan docScan) {
    this.docScan = docScan;
  }

  /**
   * @return the docScan
   */
  public DocScan getDocScan() {
    return docScan;
  }

  /**
   * Return true if there is any dictionary in this operator graph that requires lemma match or any
   * call to GetLemma function. Otherwise, return false.
   * 
   * @throws Exception
   */
  private synchronized boolean hasLemmaReference() throws Exception {
    HashSet<Operator> visited = new HashSet<Operator>();
    return root.requiresLemma(visited);
  }

  /**
   * Return true if there is any partOfSpeech exists in this operator graph. Otherwise, return
   * false.
   */
  private synchronized boolean hasPartOfSpeechReference() throws Exception {
    HashSet<Operator> visited = new HashSet<>();
    return root.requiresPartOfSpeech(visited);
  }

}
