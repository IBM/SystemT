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
package com.ibm.avatar.api;

import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;

/**
 * Utility to compile external dictionary file.This API will be useful to pre-compile external
 * dictionaries, and hence reduce load time of module's containing reference to external
 * dictionaries. Later use the {@link ExternalTypeInfo} API to pass this compile dictionaries to
 * loader.
 */
public class DictionaryUtil {
  /**
   * This method will compile the given dictionary file, based on the dictionary definition from
   * compiled module and given tokenization configuration.Post compilation, this API will serialize
   * the compiled dictionary to the given compile dictionary file URI.
   * 
   * @param dictionaryName fully qualified name of the dictionary to be compiled
   * @param dictFileURI URI of the file containing the entries for the dictionary to be compiled
   * @param tokenizerConfig tokenization configuration to be used for compiling
   * @param compileDictFileURI URI of the file in which compiled dictionary entries should be
   *        serialized
   * @exception If given module does not contain the definition for dictionary to be compiled or if
   *            given dictionary, is not marked external<br>
   *            If any of the given URIs are incorrect
   */
  public static void compileExternalDictionary(String dictionaryName, String dictFileURI,
      TokenizerConfig tokenizerConfig, String compileDictFileURI) throws Exception {

  }

}
