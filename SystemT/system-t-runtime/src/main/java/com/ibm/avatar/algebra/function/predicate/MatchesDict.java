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
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.aog.SymbolTable;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.logging.Log;

/**
 * Selection predicate that takes a string as input and returns true if the string matches an entry
 * in a dictionary, normalizing whitespace. Arguments are:
 * <ul>
 * <li>Dictionary file
 * <li>(optional) boolean argument that tells whether to ignore case
 * <li>Span input
 * </ul>
 */
public class MatchesDict extends SpanSelectionPredicate {

  public static final String FNAME = "MatchesDict";

  public static final String USAGE = "Usage: " + FNAME + "(dictname, ['flags'], input)";

  /** Actual implementation of dictionary matching */
  protected DictImpl dict = null;

  private CaseSensitivityType caseType;

  /** Internal name (unique ID) of the dictionary; computed at bind time. */
  private String dictName;

  /**
   * The contents of the dictionary; removed after {@link #dict} is initialized }
   */
  // private CompiledDictionary dictFile;

  /** AOG parser symbol table, for looking up the dictionary info */
  private SymbolTable symtab;

  /**
   * Main constructor
   * 
   * @param origTok location information for error handling
   * @param args arguments, converted from the parse tree nodes
   * @param symtab AOG parser symbol table, for finding dictionaries
   * @throws ParseException if the arguments are invalid
   */
  public MatchesDict(Token origTok, AQLFunc[] args, SymbolTable symtab) throws ParseException {
    super(origTok, args);

    this.symtab = symtab;
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    if (2 == argTypes.size()) {
      // Optional argument left out
      if (!argTypes.get(0).getIsText()) {
        throw new FunctionCallValidationException(this,
            "First argument returns %s instead of a string", argTypes.get(0));
      }
      if (!FieldType.SPANTEXT_TYPE.accepts(argTypes.get(1))) {
        throw new FunctionCallValidationException(this,
            "Second argument returns %s instead of a span or text", argTypes.get(1));
      }
    } else if (3 == argTypes.size()) {

      if (!argTypes.get(0).getIsText()) {
        throw new FunctionCallValidationException(this,
            "First argument returns %s instead of a string", argTypes.get(0));
      }
      if (!argTypes.get(1).getIsText()) {
        throw new FunctionCallValidationException(this,
            "Second argument returns %s instead of a string", argTypes.get(1));
      }
      if (!FieldType.SPANTEXT_TYPE.accepts(argTypes.get(2))) {
        throw new FunctionCallValidationException(this,
            "Third argument returns %s instead of a span or text", argTypes.get(2));
      }

    } else {
      throw new FunctionCallValidationException(this,
          "Wrong number of arguments (%d); should be 2 or 3", argTypes.size());
    }
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {

    // Last argument is the target of the predicate.
    this.arg = getSFArg(args.length - 1);

    dictName = Text.convertToString(getSFArg(0).evaluateConst());

    String flagStr = "";
    if (3 == args.length) {
      // Flags string provided
      flagStr = Text.convertToString(getSFArg(1).evaluateConst());
    }

    caseType = CaseSensitivityType.decodeStr(this, flagStr);

  }

  @Override
  public void initStateInternal(MemoizationTable mt) {
    // The first call to this method initializes the dictionary; subsequent
    // calls shouldn't need to do anything.
    // Note: Make sure that this method is idempotent!!!
    if (null == dict) {
      CompiledDictionary dictFile = symtab.lookupDict(dictName);
      dict = new HashDictImpl(dictFile, caseType, mt.getDictMemoization(), symtab.getStringTable(),
          tokRecord);

      // Don't want to keep the symbol table's data sitting in memory.
      symtab = null;
    }
  }

  /**
   * Auxiliary constructor for testing.
   */

  // public MatchesDict (String col, CompiledDictionary dictFile, CaseSensitivityType caseType)
  // throws ParseException
  // {
  // super (null, null);
  // this.arg = new GetCol (col);
  // // toScalarFunc (col, null, null);
  // this.caseType = caseType;
  // //this.dictFile = dictFile;
  // // this.dict = new HashDictImpl(dictFile, ignoreCase);
  // }

  @Override
  protected boolean spanMatches(Span span, MemoizationTable mt) {

    final boolean debug = false;

    // For now, create a new OffsetsList on each call; if this function is
    // used a lot, we may want to consider memoizing.
    BaseOffsetsList offsets = new BaseOffsetsList();

    // Find all matches of the dictionary.
    dict.findMatches(span, mt, offsets);

    // Check whether any of them match the entire span, ignoring leading or
    // trailing whitespace.
    String text = span.getText();
    int begin = 0;
    int end = text.length();
    while (begin < end && Character.isWhitespace(text.charAt(begin))) {
      begin++;
    }
    while (begin < end && Character.isWhitespace(text.charAt(end - 1))) {
      end--;
    }

    for (int i = 0; i < offsets.size(); i++) {
      if (debug) {
        Log.debug("MatchesDict: Found match from %d to %d (out of %d-%d)", offsets.begin(i),
            offsets.end(i), begin, end);
      }

      if (offsets.begin(i) == begin && offsets.end(i) == end) {
        return true;
      }
    }

    // If we get here, nothing matched.
    return false;
  }

  @Override
  protected boolean requiresLemmaInternal() {
    if (null == dict) {
      CompiledDictionary dictFile = symtab.lookupDict(dictName);
      return dictFile.getLemmaMatch();
    } else {
      // dict is not null
      return dict.requireLemmaMatch();
    }
  }

}
