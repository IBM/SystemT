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
import com.ibm.avatar.algebra.datamodel.ScalarList;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.SelectionPredicate;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Predicate that returns true if the list from its first argument contains an element equal to the
 * second argument.
 * 
 */
public class ListContains extends SelectionPredicate {
  public static final String[] ARG_NAMES = {"list", "scalar"};
  public static final String[] ARG_DESCRIPTIONS =
      {"list of scalar values", "value to search for in the list"};

  /** Object that knows how to compare our inputs. */
  private ScalarComparator comp = null;

  public ListContains(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);

  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    if (2 != argTypes.size()) {
      throw new FunctionCallValidationException(this, "Number of arguments is %d instead of 2",
          argTypes.size());
    }

    if (false == (argTypes.get(0).getIsScalarListType())) {
      throw new FunctionCallValidationException(this,
          "First argument returns %s instead of a scalar list", argTypes.get(0));
    }
    if (false == (argTypes.get(1).getIsScalarType())) {
      throw new FunctionCallValidationException(this,
          "Second argument returns %s instead of a scalar value", argTypes.get(1));
    }

    // Perform a more specific compatibility check
    FieldType firstType = argTypes.get(0).getScalarListType();
    FieldType secondType = argTypes.get(1);

    // Use the looser notion of type "equality" implemented by the accepts() method, since we want
    // to be able to mix
    // various representations of strings.
    if ((false == (firstType.accepts(secondType))) && (false == (secondType.accepts(firstType)))) {
      throw new FunctionCallValidationException(this, "Can't compare types '%s' and '%s'",
          firstType, secondType);
    }

  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    comp = ScalarComparator.createComparator(getSFArg(1).returnType());
  }

  @Override
  protected Boolean matches(Tuple tuple, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    // TODO error handling
    @SuppressWarnings("all")
    ScalarList first = (ScalarList) evaluatedArgs[0];
    Object second = evaluatedArgs[1];

    Object elem;
    int size = first.size();
    for (int idx = 0; idx < size; idx++) {
      elem = first.get(idx);
      if (elem == null) {
        continue;
      }
      if (comp.compare(elem, second) == 0) {
        return Boolean.TRUE;
      }
    }
    return Boolean.FALSE;
  }

}
