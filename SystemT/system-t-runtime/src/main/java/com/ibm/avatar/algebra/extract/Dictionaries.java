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

import java.util.Arrays;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.MultiOutputOperator;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldCopier;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.SpanGetter;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.dict.DictImpl;
import com.ibm.avatar.algebra.util.dict.DictParams.CaseSensitivityType;
import com.ibm.avatar.algebra.util.dict.HashDictImpl;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.aog.SymbolTable;
import com.ibm.avatar.api.exceptions.FatalInternalError;

/**
 * Dictionary annotator. Takes multiple dictionaries as its input, and sends the results for each
 * dictionary to a different output.
 * 
 */
public class Dictionaries extends MultiOutputOperator {

  /**
   * String used to identify the operation of this operator in profiles.
   */
  private static final String DICTS_PERF_COUNTER_NAME = "Shared Dictionary Matching";

  /** Currently, we use the same name for all outputs. */
  // public static final String OUTPUT_COL_NAME = "match";
  /** Actual implementation of dictionary matching */
  private DictImpl dict = null;

  /**
   * What kind of matching to do for each dictionary.
   */
  private CaseSensitivityType[] cases;

  /**
   * The contents of the dictionaries; removed after {@link #dict} is initialized }
   */
  private CompiledDictionary[] dictFiles;

  /** Name of the field from which our annotation sources come. */
  private final String textCol;

  /**
   * Name of the column for our outputs that holds the dictionary matches; currently we use the same
   * column name across all outputs.
   */
  private final String matchCol;

  /** Accessor for getting at the input field of the interior tuples. */
  private SpanGetter inputAcc;

  /** Accessor for setting the output field of our interior output tuples. */
  private FieldSetter<Span> outputAcc;

  /**
   * Accessor for copying the unchanged attributes from the input tuples to the output tuples.
   */
  private FieldCopier copier;

  private static final int NOT_AN_INDEX = -1;

  /**
   * Index in the MemoizationTable where we can find the reusable list of offsets of the current
   * dictionary matches. Initialized in {@link #initStateInternal(MemoizationTable)}.
   */
  private int matchesIx = NOT_AN_INDEX;

  private SymbolTable symtab;

  /** Used by the toString method to log various contained dictionaries. */
  private final String[] dictNames;

  /**
   * @param input root of the operator tree that produces spans for matching against
   * @param col name of the target column for matching
   * @param outputCol name of the column where dictionary outputs go
   * @param dicts the actual dictionary objects; may be files or in-memory data structures
   * @param casesTypes type of case-sensitivity to use when matching, or NULL to use whatever is the
   *        default for the dictionary.
   */
  public Dictionaries(Operator input, String col, String outputCol, CompiledDictionary[] dicts,
      CaseSensitivityType[] cases, SymbolTable symtab) {
    super(input, dicts.length);

    this.textCol = col;
    this.matchCol = outputCol;

    dictFiles = dicts;
    this.cases = cases;
    this.symtab = symtab;

    // this.dict = new HashDictImpl(dicts, ignoreCase);
    this.dictNames = getDictNames(dicts);

    super.profRecord.viewName = DICTS_PERF_COUNTER_NAME;
  }

  /**
   * Convenience constructor for when you're using the same case sensitivity for all entries.
   * 
   * @param globalIgnoreCase value of ignoreCase to be used for every dict file
   */
  public Dictionaries(Operator input, String col, String outputCol, CompiledDictionary[] dicts,
      boolean globalIgnoreCase, SymbolTable symtab) {
    super(input, dicts.length);

    this.textCol = col;
    this.matchCol = outputCol;
    this.symtab = symtab;

    dictFiles = dicts;
    cases = new CaseSensitivityType[dicts.length];

    if (globalIgnoreCase) {
      Arrays.fill(cases, CaseSensitivityType.insensitive);
    } else {
      Arrays.fill(cases, CaseSensitivityType.exact);
    }
    this.dictNames = getDictNames(dicts);

    super.profRecord.viewName = DICTS_PERF_COUNTER_NAME;
  }

  @Override
  protected AbstractTupleSchema createOutputSchema(int ix) {
    AbstractTupleSchema inputSchema = child.getOutputSchema();

    // Pass through span source of input.
    TupleSchema outputSchema = new TupleSchema(inputSchema, matchCol, FieldType.SPAN_TYPE);

    // Set up accessors for getting at the elements of the schemas.
    inputAcc = inputSchema.asSpanAcc(textCol);
    copier = outputSchema.fieldCopier(inputSchema);
    outputAcc = outputSchema.spanSetter(matchCol);

    return outputSchema;
  }

  @Override
  protected void initStateInternal(MemoizationTable mt) {
    // The superclass (Tee) has some additional state that needs to be initialized.
    super.initStateInternal(mt);

    // Create reusable token buffers
    if (NOT_AN_INDEX == matchesIx) {
      matchesIx = mt.createOffsetsList();
    }

    if (null == dict) {
      // On the first call to this method, tokenize the dictionary entries
      // and set up the hashtable
      dict = new HashDictImpl(dictFiles, cases, mt.getDictMemoization(), symtab.getStringTable(),
          tokRecord);
      dictFiles = null;
      cases = null;

      // trash the symbol table since it's not needed any more
      symtab = null;
    }
  }

  @Override
  protected void advanceAllInternal(MemoizationTable mt, TupleList childTups,
      TupleList[] outputLists) throws Exception {

    TLIter itr = childTups.iterator();

    while (itr.hasNext()) {
      // Grab the next interior input tuple from the top-level tuple's
      // set-valued attribute.
      Tuple interiorInTup = itr.next();

      Span src = inputAcc.getVal(interiorInTup);
      if (src == null)
        continue;
      // String text = src.getText();

      // System.err.printf("Text is of length %d.\n", text.length());

      BaseOffsetsList matches = mt.getOffsetsList(matchesIx);

      // Tokenize using this operator's own (reusable) token buffer, if no
      // one else has done so already.
      // We also make a note to the profiler that the overhead of
      // tokenization should be charged to the tokenizer, not this
      // operator.
      mt.profileEnter(tokRecord);
      OffsetsList tokens = mt.getTokenizer().tokenize(src);
      mt.profileLeave(tokRecord);

      // Find the dictionary matches in the text. This version of
      // findMatches() caches tokens and reports matches with TOKEN
      // offsets.
      dict.findMatchesTok(src, mt, matches);

      for (int i = 0; i < matches.size(); i++) {

        // All offsets that findMatchesTok() returns are TOKEN offsets!
        int beginTok = matches.begin(i);
        int endTok = matches.end(i);
        int beginOff = tokens.begin(beginTok);
        int endOff = tokens.end(endTok);

        // Retrieve the span and dictionary index for dictionary match i
        Span result = Span.makeSubSpan(src, beginOff, endOff);
        int dictIx = matches.index(i);

        // // Add token offsets, but only if the source is base text.
        // if (src instanceof Text) {
        // result.setBeginTok (beginTok);
        // result.setEndTok (endTok);
        // }

        // Build up an interior tuple to send to the appropriate output
        // of the Dictionary operator.
        Tuple interiorOutTup = createOutputTup(dictIx);
        copier.copyVals(interiorInTup, interiorOutTup);
        outputAcc.setVal(interiorOutTup, result);
        outputLists[dictIx].add(interiorOutTup);
      }

    }
  }

  @Override
  public String toString() {
    return Arrays.toString(this.dictNames);
  }

  /**
   * Fetch dictionary names from the specified compiled dictionary array.
   * 
   * @param dicts array of compiled dictionaries
   * @return
   */
  private String[] getDictNames(CompiledDictionary[] dicts) {
    String[] dictNames = new String[dicts.length];
    for (int i = 0; i < dicts.length; i++) {
      dictNames[i] = dicts[i].getCompiledDictName();
    }

    return dictNames;
  }


  @Override
  protected boolean requiresLemmaInternal() {
    // dictFiles is set to null after dict is created so we have use one of them to determine
    // lemma_match accordingly
    if (dictFiles != null) {
      for (CompiledDictionary dictFile : dictFiles) {
        if (dictFile != null && dictFile.getLemmaMatch())
          return true;
      }
      return false;
    } else if (dict != null) {
      return dict.requireLemmaMatch();
    } else {
      throw new FatalInternalError(
          "Internal error in Dictionaries: dict and dictFiles cannot all be null");
    }
  }

}
