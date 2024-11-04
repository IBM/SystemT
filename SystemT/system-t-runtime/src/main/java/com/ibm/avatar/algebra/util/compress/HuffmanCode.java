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

/**
 * Object that encapsulates a Huffman code string.
 */
public class HuffmanCode {
  // Construct a code out of the prefix of a bit string.
  public HuffmanCode(boolean[] allbits, int depth) {
    bits = new boolean[depth];
    System.arraycopy(allbits, 0, bits, 0, depth);

    // Pack the bits into a 64-bit int.
    packedBits = 0x0L;
    for (int i = 0; i < bits.length; i++) {
      long val = bits[i] ? 1L : 0L;
      packedBits = (packedBits << 1) | val;
    }
  }

  /** Bit sequence that makes up the code. */
  boolean[] bits;

  /** The bit sequence, packed into the low-order bits of a long. */
  long packedBits;

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < bits.length; i++) {
      if (bits[i]) {
        sb.append('1');
      } else {
        sb.append('0');
      }

    }
    return sb.toString();
  }

  public int getLength() {
    return bits.length;
  }

  public long getPackedBits() {
    return packedBits;
  }

  public boolean[] getBits() {
    return bits;
  }
}
