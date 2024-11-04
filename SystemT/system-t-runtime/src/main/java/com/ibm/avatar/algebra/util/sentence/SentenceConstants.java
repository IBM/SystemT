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
package com.ibm.avatar.algebra.util.sentence;

import java.util.regex.Pattern;

public class SentenceConstants {

  public static final int maxListElementLength = 30;
  public static final int minBlockSize = 4;

  // Pattern validSentenceBoundaryPattern = Pattern.compile("\\s[\\.\\?!]$");
  // static Pattern validSentenceBoundaryPattern = Pattern.compile("[^A-Za-z][\\.\\?!]$");
  static Pattern validSentenceBoundaryPattern =
      Pattern.compile("(([^A-Za-z][\\.\\?!])|(\\n\\s*\\n))$");

  static Pattern abbrevPattern = Pattern.compile("\\b[A-Za-z]\\.$");

  static Pattern capsPattern = Pattern.compile("\\b*[A-Z]+.*");
  static Pattern punctuationPattern = Pattern.compile("^\\s*[!#&%\\+\\-\\/:;@\\?\\~\\_\\|,].*");

  // Pattern lastWordPattern = Pattern.compile("[^\\s]+[\\.!\\?]$");
  static Pattern lastWordPattern = Pattern.compile("\\p{Alnum}+[\\.!\\?]$");

  // static Pattern sentenceEndingPattern = Pattern.compile("[\\.\\?!]\\s");
  // static Pattern sentenceEndingPattern = Pattern.compile("([\\.\\?!]\\s)|(\\n\\s{1,6}\\n)|(.$)");
  static Pattern sentenceEndingPattern = Pattern.compile("([\\.\\?!]\\s)|(\\n\\s*\\n)|(.$)");
  static Pattern newlinePattern = Pattern.compile("(\r)|(\n)");

}
