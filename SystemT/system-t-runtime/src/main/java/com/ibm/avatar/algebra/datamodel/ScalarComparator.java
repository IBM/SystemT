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

import java.text.Collator;
import java.util.Comparator;

/**
 * Comparators that are used when sorting or comparing individual scalar field values.
 */
public abstract class ScalarComparator implements Comparator<Object> {

  /**
   * Performs any binding of the internal function objects to tuple fields.
   * 
   * @throws ParseException
   */
  // public abstract void bind(TupleSchema ts) throws ParseException;

  /**
   * Returns the singleton comparator of the appropriate type.
   * 
   * @param type type to compare
   * @return a comparator for comparing two objects of the indicated type
   */
  public static ScalarComparator createComparator(FieldType type) {

    // if (FieldType.STRING_TYPE == type) {
    // return FastLexicalComp.singleton;
    // }
    // else
    if (FieldType.INT_TYPE == type) {
      return IntegerComp.singleton;
    } else if (FieldType.BOOL_TYPE == type) {
      return BooleanComp.singleton;
    } else if (FieldType.FLOAT_TYPE == type) {
      return FloatComp.singleton;
    } else if (FieldType.NULL_TYPE == type) {
      return NullComp.singleton;
    } else if (type.getIsSpan()) {
      return SpanComp.singleton;
    } else if (type.getIsText()) {
      return TextComp.singleton;
    } else if (type.getIsScalarListType()) {
      return ScalarListComp.singleton;
    } else {
      // This error should be caught in the optimizer.
      throw new RuntimeException(String.format("Don't know how to sort " + " type '%s'", type));
    }
  }

  /**
   * Compare two strings lexicographically. Currently unused because it throws off an enormous
   * number of temporary objects.
   */
  @SuppressWarnings("unused")
  private static class LexicalComp extends ScalarComparator {

    private static final LexicalComp singleton = new LexicalComp();

    /** Locale-sensitive comparison function, wrapped in an object. */
    private final Collator comp = Collator.getInstance();

    @Override
    public int compare(Object o1, Object o2) {
      String s1 = (String) o1;
      String s2 = (String) o2;

      int result = comp.compare(s1, s2);

      // System.err.printf("Comparing '%s' and '%s'; result is %s.\n",
      // s1, s2, result);

      return result;
    }
  }

  /**
   * Compare two strings lexicographically, using Java character comparison instead of a fancy (and
   * slow) localized comparison function.
   */
  @SuppressWarnings("unused")
  private static class FastLexicalComp extends ScalarComparator {

    private static final FastLexicalComp singleton = new FastLexicalComp();

    @Override
    public int compare(Object o1, Object o2) {

      if (o1 == null) {
        if (o2 == null)
          return 0;
        else
          return -1;
      }

      if (o2 == null)
        return 1;

      CharSequence s1 = Text.convertToString(o1);
      CharSequence s2 = Text.convertToString(o2);

      // CharSequence.length() is expensive on IBM Java 5.
      int len1 = s1.length();
      int len2 = s2.length();

      for (int i = 0; i < len1; i++) {
        if (i >= len2) {
          // s2 is a prefix of s1.
          return 1;
        }

        // Cast chars to int so we don't do unsigned subtraction by
        // accident.
        int c1 = s1.charAt(i);
        int c2 = s2.charAt(i);

        int val = c1 - c2;
        if (val != 0) {
          return val;
        }
      }

      // At this point, we've compared the first len1 chars of both
      // strings. If there are more chars of string 2, then string 1 is a
      // prefix of string 2.
      if (len2 > len1) {
        return -1;
      }

      // If we get here, the strings must be equal.
      return 0;

    }
  }

  /** Compare two integers. */
  private static class IntegerComp extends ScalarComparator {

    private static final IntegerComp singleton = new IntegerComp();

    @Override
    public int compare(Object o1, Object o2) {

      if (o1 == null) {
        if (o2 == null)
          return 0;
        else
          return -1;
      }

      if (o2 == null)
        return 1;

      Integer i1 = (Integer) o1;
      Integer i2 = (Integer) o2;
      return i1.compareTo(i2);
    }
  }

  /** Compare two Boolean values. */
  private static class BooleanComp extends ScalarComparator {

    private static final BooleanComp singleton = new BooleanComp();

    @Override
    public int compare(Object o1, Object o2) {

      if (o1 == null) {
        if (o2 == null)
          return 0;
        else
          return -1;
      }

      if (o2 == null)
        return 1;

      Boolean i1 = (Boolean) o1;
      Boolean i2 = (Boolean) o2;
      return i1.compareTo(i2);
    }
  }

  /** Compare two floats. */
  private static class FloatComp extends ScalarComparator {

    private static final FloatComp singleton = new FloatComp();

    @Override
    public int compare(Object o1, Object o2) {

      if (o1 == null) {
        if (o2 == null)
          return 0;
        else
          return -1;
      }

      if (o2 == null)
        return 1;

      Float i1 = (Float) o1;
      Float i2 = (Float) o2;
      return i1.compareTo(i2);
    }
  }

  /** Compare two nulls. They are always equal. */
  private static class NullComp extends ScalarComparator {
    private static final NullComp singleton = new NullComp();

    @Override
    public int compare(Object o1, Object o2) {
      return 0;
    }
  }

  /**
   * Compare two spans, using the built-in comparison function in the Span class.
   * 
   * @see Span#compareTo(Span)
   */
  private static class SpanComp extends ScalarComparator {
    private static final SpanComp singleton = new SpanComp();

    @Override
    public int compare(Object o1, Object o2) {

      if (o1 == null) {
        if (o2 == null)
          return 0;
        else
          return -1;
      }

      if (o2 == null)
        return 1;

      Span s1 = Span.convert(o1);
      Span s2 = Span.convert(o2);

      return s1.compareTo(s2);
    }
  }

  /**
   * Compare two Texts, using the built-in comparison function in the Text class.
   * 
   * @see Text#compareTo(Text)
   */
  private static class TextComp extends ScalarComparator {
    private static final TextComp singleton = new TextComp();

    @Override
    public int compare(Object o1, Object o2) {

      if (o1 == null) {
        if (o2 == null)
          return 0;
        else
          return -1;
      }

      if (o2 == null)
        return 1;

      Text s1 = Text.convert(o1);
      Text s2 = Text.convert(o2);

      return s1.compareTo(s2);
    }
  }

  /**
   * Compare two scalar lists, using the built-in comparison function in the ScalarList class.
   */
  private static class ScalarListComp extends ScalarComparator {
    private static final ScalarListComp singleton = new ScalarListComp();

    @Override
    @SuppressWarnings({"all"})
    public int compare(Object o1, Object o2) {

      if (o1 == null) {
        if (o2 == null)
          return 0;
        else
          // null always sorts lower
          return -1;
      }

      if (o2 == null)
        return 1;

      ScalarList l1 = (ScalarList) o1;
      ScalarList l2 = (ScalarList) o2;

      return l1.compareTo(l2);
    }
  }

}
