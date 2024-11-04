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
/**
 * 
 */
package com.ibm.avatar.algebra.function.predicate;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.SelectionPredicate;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Selection predicate that returns true if the text of the first argument completely contains the
 * text of the second argument.
 * 
 */
public class ContainsText extends SelectionPredicate {

  public static final String[] ARG_NAMES = {"arg1", "arg2"};
  public static final FieldType[] ARG_TYPES = {FieldType.SPANTEXT_TYPE, FieldType.SPANTEXT_TYPE};
  public static final String[] ARG_DESCRIPTIONS =
      {"the container (Text or Span)", "the containee (Text or Span)"};

  public ContainsText(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  protected Boolean matches(Tuple tuple, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    Text container = Text.convert(evaluatedArgs[0]);
    Text containee = Text.convert(evaluatedArgs[1]);

    if (container.contains(containee)) {
      return Boolean.TRUE;
    } else {
      return Boolean.FALSE;
    }
  }

}
