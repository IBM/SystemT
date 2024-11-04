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

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.joinpred.ContainedWithinMP;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.aql.Token;

/**
 * A version of the Contains predicate with its arguments reversed. This class is never actually
 * used at runtime; we just need it around so that we can instantiate {@link ContainedWithinMP}.
 * 
 */
public class ContainedWithin extends Contains {

  // The loading code checks whether these three variables are defined in the class itself, so we
  // can't just override
  // the fields that change here
  public static final String[] ARG_NAMES = {"span1", "span2"};
  public static final FieldType[] ARG_TYPES = {FieldType.SPAN_TYPE, FieldType.SPAN_TYPE};
  public static final String[] ARG_DESCRIPTIONS = {"inner span", "outer span"};

  public ContainedWithin(Token origTok, AQLFunc[] args) throws ParseException {
    // Just reverse the arguments and let the superclass take care of
    // everything else.
    super(origTok, new AQLFunc[] {args[1], args[0]});
  }
}
