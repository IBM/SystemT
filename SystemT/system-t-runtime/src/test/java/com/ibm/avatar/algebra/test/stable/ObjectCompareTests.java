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
package com.ibm.avatar.algebra.test.stable;

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.util.lang.LangCode;

/**
 * Tests to verify the object comparison logic
 * 
 */
public class ObjectCompareTests {

  public static void main(String[] args) {
    try {

      ObjectCompareTests t = new ObjectCompareTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.compareTextTest();

      long endMS = System.currentTimeMillis();

      double elapsedSec = (endMS - startMS) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Before
  public void setUp() throws Exception {

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

  }

  @After
  public void tearDown() {

  }

  /**
   * Test the comparison logic among Text objects
   * <ul>
   * <li>A null object of Text is sorted lower than other objects of the same type.
   * 
   * <li>Text objects are first sorted according to the lexical order of their string values.
   * 
   * <li>Text objects with equal string values are then sorted by lexical order of their original
   * strings
   * 
   * <li>If these are equal, then by the sorting order of the mapping tables. The mapping table
   * sorting order is guaranteed to be stable and compatible with equality, but the actual order is
   * internal and should not be relied upon.
   * </ul>
   */
  @Test
  public void compareTextTest() throws Exception {
    Text a = new Text("First string", LangCode.en);
    Text a1 = new Text("First string", LangCode.en);
    Text b = new Text("Second string", LangCode.en);

    assertTrue("compare a > null ", a.compareTo(null) > 0);

    assertTrue("compare a = a ", a.compareTo(a) == 0);
    assertTrue("compare a = a1 ", a.compareTo(a1) == 0);
    assertTrue("compare a < b ", a.compareTo(b) < 0);
    assertTrue("compare b > a ", b.compareTo(a) > 0);
  }

  /**
   * Test the comparison logic among Span objects
   */
  @Test
  public void compareSpanTest() throws Exception {
    // Prepare doc texts.
    Text t1 = new Text("First string", LangCode.en);
    Text t1a = new Text("First string", LangCode.en);
    Text t2 = new Text("Second string", LangCode.en);

    // Prepare span objects
    Span s11 = Span.makeBaseSpan(t1, 3, 5);
    Span s11a = Span.makeBaseSpan(t1, 3, 5);
    Span s1a1 = Span.makeBaseSpan(t1a, 3, 5);
    Span s1a1a = Span.makeBaseSpan(t1a, 3, 5);

    Span s1a2 = Span.makeBaseSpan(t1a, 4, 5);
    Span s1a3 = Span.makeBaseSpan(t1a, 2, 5);
    Span s1a4 = Span.makeBaseSpan(t1a, 3, 6);
    Span s1a5 = Span.makeBaseSpan(t1a, 3, 4);
    Span s1a6 = Span.makeBaseSpan(t1a, 4, 4);
    Span s1a7 = Span.makeBaseSpan(t1a, 2, 6);

    Span s21 = Span.makeBaseSpan(t2, 3, 5);

    // Compare among different types
    assertTrue("compare s11 > null ", s11.compareTo(null) > 0);

    assertTrue("compare s11 > t1 ", s11.compareTo(t1) > 0);
    assertTrue("compare s11 > t1a ", s11.compareTo(t1a) > 0);
    assertTrue("compare s11 > t2 ", s11.compareTo(t2) > 0);

    assertTrue("compare t1 < s11 ", t1.compareTo(s11) < 0);
    assertTrue("compare t1a < s11 ", t1a.compareTo(s11) < 0);
    assertTrue("compare t2 < s11 ", t2.compareTo(s11) < 0);

    // Equal docText and equal span
    assertTrue("compare s11 = s11 ", s11.compareTo(s11) == 0);
    assertTrue("compare s11 = s11a ", s11.compareTo(s11a) == 0);
    assertTrue("compare s11 = s1a1 ", s11.compareTo(s1a1) == 0);
    assertTrue("compare s11 = s1a1a ", s11.compareTo(s1a1a) == 0);

    // Equal docText, unequal spans.
    assertTrue("compare s11 < s1a2 ", s11.compareTo(s1a2) < 0);
    assertTrue("compare s11 > s1a3 ", s11.compareTo(s1a3) > 0);
    assertTrue("compare s11 < s1a4 ", s11.compareTo(s1a4) < 0);
    assertTrue("compare s11 > s1a5 ", s11.compareTo(s1a5) > 0);
    assertTrue("compare s11 < s1a6 ", s11.compareTo(s1a6) < 0);
    assertTrue("compare s11 > s1a7 ", s11.compareTo(s1a7) > 0);

    // Unequal docText
    assertTrue("compare s21 > s11 ", s21.compareTo(s11) > 0);
    assertTrue("compare s21 > s11a ", s21.compareTo(s11a) > 0);
    assertTrue("compare s21 > s1a1 ", s21.compareTo(s1a1) > 0);
    assertTrue("compare s21 > s1a1a ", s21.compareTo(s1a1a) > 0);

    // Equal docText, unequal spans.
    assertTrue("compare s21 > s1a2 ", s21.compareTo(s1a2) > 0);
    assertTrue("compare s21 > s1a3 ", s21.compareTo(s1a3) > 0);
    assertTrue("compare s21 > s1a4 ", s21.compareTo(s1a4) > 0);
    assertTrue("compare s21 > s1a5 ", s21.compareTo(s1a5) > 0);
    assertTrue("compare s21 < s1a6 ", s21.compareTo(s1a6) > 0);
    assertTrue("compare s21 > s1a7 ", s21.compareTo(s1a7) > 0);

  }

}
