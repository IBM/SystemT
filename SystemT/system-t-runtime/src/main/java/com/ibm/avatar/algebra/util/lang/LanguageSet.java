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
package com.ibm.avatar.algebra.util.lang;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

import com.ibm.avatar.algebra.util.dict.DictMemoization;
import com.ibm.avatar.algebra.util.string.FormatRTException;
import com.ibm.avatar.algebra.util.string.StringUtils;

/**
 * ADT for a set of languages, used for representing the set of languages that a dictionary covers.
 * Also contains a mechanism for creating singleton instances of this class.
 * 
 */
public class LanguageSet extends AbstractSet<LangCode> {

  /**
   * Number of bits that could be set in the bitmap representation of a langauge set. Must be kept
   * in sync with {@link LangCode#MAX_ORDINAL}.
   */
  private static final int MAX_BIT_POS = LangCode.values().length;

  private static final int BYTE_LENGTH = 8;

  /**
   * Internal bitmask; if the language X is in the set, then X.ordinal is set. Note that this
   * depends on there being fewer than 64 ({@link #MAX_BIT_POS} + 1) language codes. If the number
   * goes above that, we'll have to use a byte array or an instance of {@link java.util.BitSet}.
   */
  // private long bitmask = 0x0L;
  // Fix for bug 145456 : LangCode class is not robust for unknown langauges
  // bitmask representation changed to bytearray inorder to support all
  // languages of ISO-639.(i.e LanCode enum elements > 64 elements)
  private final byte[] bitmask;

  /**
   * Number of bits set in bitmask; kept to speed up {@link #size()}
   */
  private int numBitsSet = 0;

  private boolean bitIsSet(int bitIx) {
    // Make sure that we're ANDing the mask with a byte at appropriate
    // index!
    // long testBit = 0x1L << bitIx;
    int index = getIndex(bitIx);
    int bitPosition = getBitPosition(bitIx);
    long testBit = 0x1 << bitPosition;
    return (0 != (bitmask[index] & testBit));
    // return (0 != (bitmask & testBit));
  }

  /**
   * This constructor for use only by this class's static methods.
   * 
   * @param mask internal bitmask for the elements of the set
   */
  private LanguageSet(byte[] mask) {
    this.bitmask = mask;
    this.numBitsSet = countBits(mask);
  }

  /**
   * Count the number of bits set
   * 
   * @param mask a 64-bit mask
   * @return number of 1's in the mask
   */
  private int countBits(byte[] maskArray) {
    int ret = 0;
    // Iterate through all the 8 bits of every byte in the mask to check
    // if the LSB is set. Increment the count by 1 if the LSB is set.
    for (byte mask : maskArray) {
      if (mask != 0x0) {
        for (int cnt = 0; cnt < BYTE_LENGTH; cnt++) {
          if (0 != (mask & 0x1))
            ret++;
          // It's ok to modify mask, because it's just a copy
          // Shifting bits to the right by 1
          mask = (byte) (mask >>> 1);
        }
      }
    }
    return ret;
  }

  /*
   * Calculate the index position based on given enum's ordinal value
   */
  private static int getIndex(int ordinalVal) {
    return ordinalVal / BYTE_LENGTH;
  }

  /*
   * Calculate the bit position based on given enum's ordinal value
   */

  private static int getBitPosition(int ordinalVal) {
    return ordinalVal % BYTE_LENGTH;
  }

  /**
   * Create a new language set for the indicated languages. Will reuse sets if possible. Reentrant.
   * 
   * @param mask internal bit mask for the desired set of languages
   * @return a set with the indicated contents
   */
  public static LanguageSet create(byte[] mask, DictMemoization dm) {

    HashMap<byte[], LanguageSet> singletons = dm.getLangSetSingletons();

    // See if an identical set already exists.
    LanguageSet ret = singletons.get(mask);

    if (null == ret) {
      // No cached set found; create one and add it to the repository.
      ret = new LanguageSet(mask);
      singletons.put(mask, ret);
    }

    if (null == ret) {
      throw new RuntimeException("Internal error: Object is still NULL!");
    }

    return ret;

  }

  /**
   * Create a language set from a comma-delimited string. In the future, this method may support
   * shortcuts like "western".
   * 
   * @param langStr string containing a list of language codes
   */
  public static LanguageSet create(String langStr, DictMemoization dm) {
    String[] codes = StringUtils.split(langStr, ',');

    // Create a bitmask.
    // long mask = 0x0L;
    byte[] mask = new byte[LangCode.values().length];
    for (int i = 0; i < mask.length; i++)
      mask[i] = 0x0;
    for (String code : codes) {
      LangCode lcode = LangCode.strToLangCode(code);
      int index = getIndex(lcode.ordinal());
      int bitPosition = getBitPosition(lcode.ordinal());
      byte val = 0x1;
      // mask[index] |= (byte)(val << bitPosition);
      mask[index] = (byte) (mask[index] | (val << bitPosition));
      // mask |= (0x1L << lcode.ordinal());
    }

    return create(mask, dm);
  }

  /**
   * Convenience version of {@link #create(ArrayList)} for a single language.
   */
  public static LanguageSet create(LangCode lang, DictMemoization dm) {

    if (null == lang) {
      throw new RuntimeException("Null LangCode passed to LanguageSet.create()");
    }

    // Create a bitmask.
    byte[] mask = new byte[LangCode.values().length];
    for (int i = 0; i < mask.length; i++)
      mask[i] = 0x0;
    int index = getIndex(lang.ordinal());
    int bitPosition = getBitPosition(lang.ordinal());
    // mask[index] |= (byte)(0x1 << bitPosition);
    mask[index] = (byte) (mask[index] | (0x1 << bitPosition));
    // long mask = (0x1L << lang.ordinal());

    return create(mask, dm);
  }

  /**
   * Convenience version of {@link #create(ArrayList)} for array arguments.
   */
  public static LanguageSet create(LangCode[] langs, DictMemoization dm) {
    // Create a bitmask.
    // long mask = 0x0L;
    byte[] mask = new byte[LangCode.values().length];
    for (int i = 0; i < mask.length; i++)
      mask[i] = 0x0;
    for (LangCode lcode : langs) {
      int index = getIndex(lcode.ordinal());
      int bitPosition = getBitPosition(lcode.ordinal());
      // mask[index] |= (byte)(0x1 << bitPosition);
      mask[index] = (byte) (mask[index] | (0x1 << bitPosition));
      // mask |= (0x1L << lcode.ordinal());
    }

    LanguageSet ret = create(mask, dm);

    // SANITY CHECK: Make sure no NULL pointers found their way into the
    // language set.
    for (LangCode code : ret) {
      if (null == code) {
        throw new FormatRTException("Found NULL language code after creating language set for %s",
            Arrays.toString(langs));
      }
    }
    // END SANITY CHECK

    return ret;
  }

  /**
   * Create a new language set by adding one language to an existing set. Uses the class's internal
   * memoization to avoid creating duplicate sets.
   */
  public static LanguageSet create(LanguageSet orig, LangCode lang, DictMemoization dm) {

    if (orig.contains(lang)) {
      // Set already contains the target language; no-op!
      return orig;
    }

    // Compute a new bitmask

    byte[] mask = orig.bitmask;
    int index = getIndex(lang.ordinal());
    int bitPosition = getBitPosition(lang.ordinal());
    // mask[index] |= (byte)(0x1 << bitPosition);
    mask[index] = (byte) (mask[index] | (0x1 << bitPosition));
    // mask |= (0x1L << lang.ordinal());

    LanguageSet ret = create(mask, dm);

    return ret;
  }

  @Override
  public Iterator<LangCode> iterator() {
    return new Itr(this);
  }

  @Override
  public int size() {
    return numBitsSet;
  }

  @Override
  public boolean contains(Object obj) {
    if (false == (obj instanceof LangCode)) {
      throw new RuntimeException("Argument must be a LangCode");
    }

    LangCode lcode = (LangCode) obj;
    return bitIsSet(lcode.ordinal());
  }

  @Override
  public boolean add(LangCode lcode) {
    if (contains(lcode)) {
      // Spec for this method says we should return false if the element
      // is already there.
      return false;
    }

    // If we get here, need to add the element and increment our size
    // counter.
    // long bitToSet = (0x1L << (lcode.ordinal()));
    // bitmask |= bitToSet;
    int index = getIndex(lcode.ordinal());
    int bitPosition = getBitPosition(lcode.ordinal());
    // bitmask[index] |= (byte)(0x1 << bitPosition);
    bitmask[index] = (byte) (bitmask[index] | (0x1 << bitPosition));
    numBitsSet++;
    return true;
  }

  /** Iterator class */
  private static class Itr implements Iterator<LangCode> {
    public Itr(LanguageSet s) {
      this.set = s;

      nextBitPos = -1;
      advance();
    }

    LanguageSet set;
    int nextBitPos;

    @Override
    public boolean hasNext() {
      return (nextBitPos <= MAX_BIT_POS);
    }

    @Override
    public LangCode next() {
      LangCode ret = LangCode.ordinalToLangCode(nextBitPos);
      advance();
      return ret;
    }

    @Override
    public void remove() {
      nextBitPos = MAX_BIT_POS + 1;
      set = null;
    }

    private void advance() {
      // Move forward at least one bit position
      nextBitPos++;

      // Keep going until we reach a set bit or run out of bits
      while (nextBitPos <= MAX_BIT_POS && false == set.bitIsSet(nextBitPos)) {
        nextBitPos++;
      }
    }
  }

  @Override
  public int hashCode() {
    int retVal = 0;
    for (int i = 0; i < bitmask.length; i++) {
      retVal = retVal + bitmask[i];
    }
    // int msb = (int) (bitmask >>> 32);
    // int lsb = (int) bitmask;
    // return lsb + msb;
    return retVal;
  }

  @Override
  public boolean equals(Object object) {
    if (false == (object instanceof LanguageSet)) {
      return false;
    }

    LanguageSet o = (LanguageSet) object;
    if (bitmask.length != o.bitmask.length)
      return false;

    for (int i = 0; i < bitmask.length; i++) {
      if (bitmask[i] == 0) {
        if (o.bitmask[i] != 0)
          return false;
      } else {
        if (o.bitmask[i] == 0)
          return false;
        else if (!(o.bitmask[i] == bitmask[i]))
          return false;
      }
    }
    return true;

  }

}
