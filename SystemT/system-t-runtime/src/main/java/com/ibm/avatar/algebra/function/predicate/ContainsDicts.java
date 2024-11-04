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
package com.ibm.avatar.algebra.function.predicate;

import java.util.ArrayList;
import java.util.Arrays;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.SpanSelectionPredicate;
import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.dict.DictImpl;
import com.ibm.avatar.algebra.util.dict.DictParams.CaseSensitivityType;
import com.ibm.avatar.algebra.util.dict.HashDictImpl;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.aog.SymbolTable;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.aql.Token;

/**
 * A version of the ContainsDict() selection predicate that takes a Arguments:
 * <ul>
 * <li>dictionary files
 * <li>(optional) ignoreCase -- should we do case-insensitive dictionary matching?
 * <li>input source
 * </ul>
 */
public class ContainsDicts extends SpanSelectionPredicate {

  public static final String FNAME = "ContainsDicts";

  public static final String USAGE =
      "Usage: " + FNAME + "(dictfile_1, dictfile_2, ..., dictfile_n, [ignoreCase], input)";

  /** Actual implementation of dictionary matching */
  private DictImpl dict;

  /**
   * Type of matching to perform for each dictionary; overrides the dictionary's default if present.
   */
  private CaseSensitivityType[] cases;

  /**
   * The contents of the dictionaries; removed after {@link #dict} is initialized }
   */
  private CompiledDictionary[] dictFiles;

  private SymbolTable symtab;

  /**
   * Main constructor
   * 
   * @param origTok
   * @param args arguments, from the parse tree
   * @param symtab symbol table for decoding dictionary argument.
   * @throws ParseException if the arguments are invalid
   */
  public ContainsDicts(Token origTok, AQLFunc[] args, SymbolTable symtab) throws ParseException {
    super(origTok, args);

    this.symtab = symtab;
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    int numArgs = argTypes.size();

    if (numArgs < 2) {
      throw new FunctionCallValidationException(this, "Must have at least 2 arguments.");
    }

    // Last argument must always return span.
    if (!FieldType.SPANTEXT_TYPE.accepts(argTypes.get(numArgs - 1))) {
      throw new FunctionCallValidationException(this,
          "Last argument returns %s instead of Span or Text", argTypes.get(numArgs - 1));
    }

    // Second to last argument can be either the "ignore case" flag or the final dictionary
    FieldType secondToLastType = argTypes.get(numArgs - 2);
    boolean haveCaseFlag = secondToLastType.getIsBooleanType();

    if (haveCaseFlag && numArgs < 3) {
      throw new FunctionCallValidationException(this, "No dictionary names provided.");
    }

    // Now verify that the dictionary names are strings.
    for (int i = 0; i < (haveCaseFlag ? numArgs - 2 : numArgs - 1); i++) {
      FieldType argType = argTypes.get(i);
      if (!argType.getIsText()) {
        throw new FunctionCallValidationException(this, "Argument %d returns %s instead of string",
            i, argType);
      }
    }
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    // Check whether we have a case sensitivity flag.
    boolean haveFlag = (FieldType.BOOL_TYPE == getSFArg(args.length - 2).returnType());

    int numDicts = args.length - (haveFlag ? 2 : 1);

    // Default to ignore case if no "ignore case" flag is provided
    boolean ignoreCase = true;
    if (haveFlag) {
      // System.err.printf ("%s.evaluateConst() returns %s\n", args[args.length - 2],
      // args[args.length - 2].evaluateConst ());
      ignoreCase = (Boolean) getSFArg(args.length - 2).evaluateConst();
    }
    CaseSensitivityType caseType =
        ignoreCase ? CaseSensitivityType.insensitive : CaseSensitivityType.exact;
    cases = new CaseSensitivityType[numDicts];
    Arrays.fill(cases, caseType);

    // Bind, but do not load, the dictionaries.
    if (null == symtab) {
      // SPECIAL CASE: No AOG symbol table because we're instantiating this function at compile
      // time.
      dictFiles = null;
      // END SPECIAL CASE
    } else {
      dictFiles = new CompiledDictionary[numDicts];
      for (int i = 0; i < numDicts; i++) {
        String dictName = Text.convertToString(getSFArg(i).evaluateConst());
        dictFiles[i] = symtab.lookupDict(dictName);
      }
    }

    // Tell the superclass what the volatile argument is
    arg = getSFArg(args.length - 1);

  }

  @Override
  protected boolean spanMatches(Span span, MemoizationTable mt) {
    return dict.containsMatch(span, mt);
  }

  @Override
  public void initStateInternal(MemoizationTable mt) {
    // The first call to this method initializes the dictionary; subsequent
    // calls shouldn't need to do anything.
    // Note: Make sure that this method is idempotent!!!
    if (null == dict) {
      dict = new HashDictImpl(dictFiles, cases, mt.getDictMemoization(), symtab.getStringTable(),
          tokRecord);
      dictFiles = null;
      cases = null;

      // Don't want to keep the symbol table's data sitting in memory.
      symtab = null;
    }
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
      throw new FatalInternalError("Cannot obtain information about lemma matching.");
    }
  }

}
