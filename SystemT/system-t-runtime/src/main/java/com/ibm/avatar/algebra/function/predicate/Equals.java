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
import com.ibm.avatar.algebra.datamodel.ScalarComparator;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.SelectionPredicate;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.aog.SymbolTable;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Selection predicate that tests whether its two inputs match, according to the comparison classes
 * in {@link ScalarComparator}. <br>
 * Note that Equals is not a LogicalExpression because it handles non-logical inputs as well as
 * logical ones.
 */
public class Equals extends SelectionPredicate {
  public static final String[] ARG_NAMES = {"arg1", "arg2"};

  /** Object that knows how to compare our inputs. */
  private ScalarComparator comp = null;

  /**
   * Main constructor
   * 
   * @param args arguments, from the parse tree
   * @param symtab AOG parser's symbol table
   * @param Catalog AQL catalog
   * @throws ParseException if the arguments are invalid
   */
  public Equals(Token origTok, AQLFunc[] args, SymbolTable symtab, Catalog catalog)
      throws ParseException {
    super(origTok, args);
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    if (2 != argTypes.size()) {
      throw new FunctionCallValidationException(this, "Received %d arguments instead of 2",
          argTypes.size());
    }

    FieldType firstType = argTypes.get(0);
    FieldType secondType = argTypes.get(1);

    if (!firstType.comparableTo(secondType)) {
      throw new FunctionCallValidationException(this, "Can't compare types '%s' and '%s'",
          firstType, secondType);
    }

  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {

    // Now that we've bound the arguments, we can create a comparator.
    FieldType compareType = (getSFArg(0).returnType());
    if (false == compareType.getIsNullType()) {
      comp = ScalarComparator.createComparator(compareType);
    }
  }

  /**
   * Checks equality of two attributes in a tuple. <br>
   * Equals (null, anything) => null and is handled in ScalarFunc.evaluate() before this method gets
   * called
   * 
   * @return true if entity identities are the same (or Integer/String value equality for paths
   *         referring to primitives)
   * @throws TextAnalyticsException
   */
  @Override
  protected Boolean matches(Tuple tuple, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    Object first = evaluatedArgs[0];
    Object second = evaluatedArgs[1];

    // System.err.printf("Equals: Comparing '%s' with '%s'\n", first, second);

    // result is boxed into a Boolean
    return (0 == comp.compare(first, second));
  }

}
