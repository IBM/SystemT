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
package com.ibm.avatar.algebra.util.dict;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.ProfileRecord;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.SpanText;
import com.ibm.avatar.algebra.util.dict.DictParams.CaseSensitivityType;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.aog.StringTable;

/**
 * Experimental compressed main-memory implementation of dictionary matching. Currently, this class
 * is mostly a placeholder.
 * 
 */
public class CompressedDictImpl extends DictImpl {

  public CompressedDictImpl(DictFile[] dicts, CaseSensitivityType[] cases, Tokenizer tokenizer,
      DictMemoization dm, ProfileRecord tokRecord) {
    super(dicts, cases, tokenizer, dm, tokRecord);
  }

  @Override
  public boolean containsMatch(Span span, MemoizationTable mt) {
    throw new RuntimeException("Not yet implemented.");
  }

  @Override
  public void findMatchesTok(SpanText target, MemoizationTable mt, BaseOffsetsList output) {
    throw new RuntimeException("Not yet implemented.");
  }

  @Override
  protected void init(String[] canonEntryStrs, DictEntry[] entryData, DictMemoization dm,
      StringTable globalStringTable, boolean requireLemmaMatch) {
    throw new RuntimeException("Not yet implemented.");
  }

}
