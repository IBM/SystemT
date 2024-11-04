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

import com.ibm.avatar.algebra.base.ExtractionOp;
import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.RSEOperator;
import com.ibm.avatar.algebra.datamodel.RSEBindings;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.dict.DictImpl;
import com.ibm.avatar.algebra.util.dict.DictParams.CaseSensitivityType;
import com.ibm.avatar.algebra.util.dict.HashDictImpl;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.aog.SymbolTable;
import com.ibm.avatar.api.exceptions.FatalInternalError;

/**
 * RSE-compatible operator that does dictionary extraction with a single dictionary.
 */
public class Dictionary extends ExtractionOp implements RSEOperator {

  /** Actual implementation of dictionary matching */
  private DictImpl dict;

  /**
   * What kind of matching to do -- case sensitive vs. case-insensitive, etc.
   */
  private CaseSensitivityType caseType;

  /**
   * The contents of the dictionary; removed after {@link #dict} is initialized }
   */
  private CompiledDictionary dictFile;

  private static final int NOT_AN_INDEX = -1;

  /**
   * Index in the MemoizationTable where we can find the reusable list of offsets of the current
   * dictionary matches. Initialized in {@link #initStateInternal(MemoizationTable)}.
   */
  private int matchesIndex = NOT_AN_INDEX;

  private SymbolTable symbolTable = null;

  public Dictionary(String inCol, String outCol, Operator child, CompiledDictionary dictFile,
      CaseSensitivityType caseType, SymbolTable symbolTable) {
    super(inCol, outCol, child);

    this.dictFile = dictFile;
    this.caseType = caseType;
    this.symbolTable = symbolTable;
  }

  @Override
  protected void initStateInternal(MemoizationTable mt) {
    matchesIndex = mt.createOffsetsList();

    if (null == dict) {
      // On the first call to this method, tokenize the dictionary entries
      // and set up the hashtable
      dict = new HashDictImpl(dictFile, caseType, mt.getDictMemoization(),
          symbolTable.getStringTable(), tokRecord);
      dictFile = null;
      caseType = null;

      // trash the symbol table since it's not needed any more
      symbolTable = null;
    }
  }

  @Override
  protected void extract(MemoizationTable mt, Tuple inputTup, Span inputSpan) throws Exception {

    // Tokenize the input span, using cached tokens if possible.
    mt.profileEnter(tokRecord);
    OffsetsList tokens = mt.getTokenizer().tokenize(inputSpan);
    mt.profileLeave(tokRecord);

    // Do dictionary matching...
    BaseOffsetsList matches = (BaseOffsetsList) mt.getOffsetsList(matchesIndex);
    dict.findMatchesTok(inputSpan, mt, matches);

    // Generate a result tuple for each of the matches found.
    for (int i = 0; i < matches.size(); i++) {

      int beginTok = matches.begin(i);
      int endTok = matches.end(i);
      int beginOff = tokens.begin(beginTok);
      int endOff = tokens.end(endTok);

      super.addResultAnnot(inputTup, beginOff, endOff, inputSpan, mt);
    }
  }

  /**
   * Some operators that inherit this interface do not implement its functionality. This method
   * tells whether an given operator is really RSE-capable.
   */
  @Override
  public boolean implementsRSE() {
    return true;
  }

  @Override
  protected void extractRSE(MemoizationTable mt, Tuple inputTup, Span inputSpan, RSEBindings b)
      throws Exception {
    // TODO: Write this method!
  }

  @Override
  protected boolean requiresLemmaInternal() {
    // dictFiles is set to null after dict is created so we have use one of them to determine
    // lemma_match accordingly
    if (dictFile != null) {
      return dictFile.getLemmaMatch();
    } else if (dict != null) {
      return dict.requireLemmaMatch();
    } else {
      throw new FatalInternalError(
          "Internal error in Dictionaries: dict and dictFiles cannot all be null");
    }
  }
}
