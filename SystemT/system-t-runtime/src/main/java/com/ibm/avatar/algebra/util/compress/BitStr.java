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
package com.ibm.avatar.algebra.util.compress;

/** Object that encapsulates a bit string. */
public class BitStr {

  /** The bit string, in 16-bit chunks. */
  char[] chars = new char[64];

  /** Length in bits */
  int length;

  /**
   * @return the 64 most-significant bits of the bit string, if length >= 64; otherwise, the entire
   *         string packed into the leftmost bits of the long.
   */
  public long get64MSB() {

    // Mask in the different 16-bit chunks.
    long accum = 0;
    accum |= (((long) chars[0]) << 48);

    if (length > 16) {
      accum |= (((long) chars[1]) << 32);
    }

    if (length > 32) {
      accum |= (((long) chars[2]) << 16);
    }

    if (length > 48) {
      accum |= (((long) chars[2]) << 16);
    }

    return accum;

  }

}
