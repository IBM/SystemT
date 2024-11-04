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

/**
 * Interface for a callback that divides large documents into smaller chunks for processing. This
 * division should occur in a way that does not affect the annotations produced.
 */
public interface Chunker {

  /**
   * <p>
   * getNextBoundary.
   * </p>
   *
   * @param doctext the full document text
   * @param startPos starting position within doctext
   * @return location of the next split point in the document text after startPos. For example, if
   *         the document should be split between characters 3 and 4, return 4.
   */
  int getNextBoundary(CharSequence doctext, int startPos);

  /**
   * <p>
   * makeClone.
   * </p>
   *
   * @return a new object that supports the same chunking semantics as this one. This new object
   *         will be called from a different thread, so it must not share a mutable state with the
   *         original!
   */
  public Chunker makeClone();

}
