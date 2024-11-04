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
package com.ibm.avatar.algebra.datamodel;

/**
 * Class that encapsulates a single set of bindings for restricted span evaluation. Currently, these
 * bindings indicate a single span (range of characters) in an RSE-compatible operator's input.
 */
public class RSEBindings {

  /**
   * Begin offset, measured in characters; this is an offset into the document, not any span over
   * the document.
   */
  public int begin;

  /**
   * End offset, measured in characters; this is an offset into the document, not any span over the
   * document.
   */
  public int end;

  /**
   * Indicates how {@link #begin} and {@link #end} should be interpreted. Value drawn from constants
   * in this class.
   */
  public int type;

  /**
   * Value for {@link #type} that indicates that {@link #begin} and {@link #end} should be
   * interpreted as the range where the inner span can start.
   */
  public static final int STARTS_AT = 1;

  public static final int ENDS_AT = 2;

}
