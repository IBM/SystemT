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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

/**
 * API to serialize/de-serialize compiled dictionary.
 * 
 */
public interface DictionarySerializer {

  /**
   * This method serializes the compiled dictionary to given stream.
   * 
   * @param compiledDictionary dictionary in compiled form
   * @param streamToSerialize stream to which dictionary should be serialized
   * @throws UnsupportedEncodingException
   * @throws IOException
   */
  public void serialize(CompiledDictionary compiledDictionary, OutputStream streamToSerialize)
      throws IOException;

  /**
   * This method de-serializes the compiled dictionary from the given stream.
   * 
   * @param dictionaryStream stream to compiled dictionary
   * @return compiled dictionary object
   * @throws IOException
   * @throws Exception
   */
  public CompiledDictionary deSerialize(InputStream dictionaryStream) throws Exception;
}
