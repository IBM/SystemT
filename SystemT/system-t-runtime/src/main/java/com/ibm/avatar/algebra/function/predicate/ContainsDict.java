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

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.aog.SymbolTable;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.logging.Log;

/**
 * Selection predicate that takes string as input and returns true if the string contains a match of
 * a dictionary. Arguments:
 * <ul>
 * <li>dictionary file
 * <li>(optional) ignoreCase -- should we do case-insensitive dictionary matching?
 * <li>input source
 * </ul>
 * <p>
 * Gets most of its initialization logic from MatchesDict
 */
public class ContainsDict extends MatchesDict {

  // We need to define these fields here even though the superclass defines them. AQLFunc expects
  // every function
  // implementation class to define these fields itself.
  public static final String FNAME = "ContainsDict";
  public static final String USAGE = "Usage: " + FNAME + "(dictname, ['flags'], input)";

  // All necessary fields are kept in the superclass

  /**
   * Main constructor
   * 
   * @param origTok
   * @param args arguments, from the parse tree
   * @param symtab symbol table for decoding dictionary argument.
   * @throws ParseException if the arguments are invalid
   */
  public ContainsDict(Token origTok, AQLFunc[] args, SymbolTable symtab) throws ParseException {
    super(origTok, args, symtab);
  }

  @Override
  protected boolean spanMatches(Span span, MemoizationTable mt) {
    final boolean debug = false;

    if (debug) {
      Log.debug("ContainsDict(%x): Dictionary is %s", this.hashCode(), dict);
    }

    return dict.containsMatch(span, mt);
  }

}
