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

import junit.framework.Assert;

import org.junit.Test;

import com.ibm.avatar.algebra.util.dict.DictMemoization;
import com.ibm.avatar.algebra.util.dict.EncodeDecodeLanguageSet;
import com.ibm.avatar.algebra.util.lang.LanguageSet;

/**
 * Test harness containing regression tests covering, the encoding and decoding of dictionary entry
 * language set. All the tests here exercises {@link EncodeDecodeLanguageSet} class.
 * 
 */
public class DictEntryLangEncoderTests {
  /**
   * Test to encode various language set against a base language set.
   * 
   * @throws Exception
   */
  @Test
  public void encoderTest() throws Exception {
    DictMemoization dm = new DictMemoization();
    LanguageSet baseLangSet = LanguageSet.create("en,fr", dm);
    EncodeDecodeLanguageSet encoder = new EncodeDecodeLanguageSet(baseLangSet);

    int encodedVal = encoder.encode(LanguageSet.create("en", dm));
    Assert.assertEquals(encodedVal, 2);

    int encodedVal1 = encoder.encode(LanguageSet.create("fr", dm));
    Assert.assertEquals(encodedVal1, 1);

    int encodedVal2 = encoder.encode(LanguageSet.create("en,fr", dm));
    Assert.assertEquals(encodedVal2, 3);

    int encodedVal3 = encoder.encode(LanguageSet.create("fr,en", dm));
    Assert.assertEquals(encodedVal3, 3);

    int encodedVal4 = encoder.encode(LanguageSet.create("fr,fr", dm));
    Assert.assertEquals(encodedVal4, 1);

  }

  /**
   * Negative encoder test.
   * 
   * @throws Exception
   */
  @Test
  public void negativeEncoderTest() throws Exception {
    DictMemoization dm = new DictMemoization();

    LanguageSet baseLangSet = LanguageSet.create("en,fr", dm);
    EncodeDecodeLanguageSet encoder = new EncodeDecodeLanguageSet(baseLangSet);

    try {
      // Language set to encode is not subset of the base language set; 'es' is not part of base
      // language set
      encoder.encode(LanguageSet.create("es,en", dm));
      Assert.fail("Was expecting an exception;control should have never come here.");
    } catch (Exception e) {
      Assert.assertTrue(e.getMessage(), true);
    }
  }

  /**
   * Test to encode an entry language set against a base language set, each containing the maximum
   * number of codes, i.e. 31 codes.
   * 
   * @throws Exception
   */
  @Test
  public void encoderCornerCaseTest() throws Exception {
    DictMemoization dm = new DictMemoization();
    LanguageSet baseLangSet = LanguageSet.create(
        "sq,ar,bg,ca,uk,hr,cs,da,nl,en,et,fi,fr,de,el,he,hu,is,id,it,ja,kk,ko,lt,ms,no,fa,pl,pt,ro,ru",
        dm);
    EncodeDecodeLanguageSet encoder = new EncodeDecodeLanguageSet(baseLangSet);

    int encodedVal = encoder.encode(LanguageSet.create(
        "sq,ar,bg,ca,uk,hr,cs,da,nl,en,et,fi,fr,de,el,he,hu,is,id,it,ja,kk,ko,lt,ms,no,fa,pl,pt,ro,ru",
        dm));
    Assert.assertEquals(encodedVal, 2147483647);

  }

  /**
   * Test to decode against a base language set
   */
  @Test
  public void decoderTest() throws Exception {
    DictMemoization dm = new DictMemoization();

    LanguageSet baseLangSet = LanguageSet.create("en,fr", dm);
    EncodeDecodeLanguageSet decoder = new EncodeDecodeLanguageSet(baseLangSet);

    LanguageSet lagSet1 = decoder.decode(2);
    Assert.assertTrue(LanguageSet.create("en", dm).equals(lagSet1));

    LanguageSet lagSet2 = decoder.decode(1);
    Assert.assertTrue(LanguageSet.create("fr", dm).equals(lagSet2));

    LanguageSet lagSet3 = decoder.decode(3);
    Assert.assertTrue(LanguageSet.create("fr,en", dm).equals(lagSet3));
  }

  /**
   * Negative decoder test
   * 
   * @throws Exception
   */
  @Test
  public void negativeDecoderTest() throws Exception {
    DictMemoization dm = new DictMemoization();

    LanguageSet baseLangSet = LanguageSet.create("en,fr", dm);
    EncodeDecodeLanguageSet decoder = new EncodeDecodeLanguageSet(baseLangSet);

    try {
      // decoding something for which binary string length will be greater than size of base
      // language set; binary string
      // was 7 will be 111
      decoder.decode(7);
      Assert.fail("Was expecting an exception, the control should have never come here.");
    } catch (Exception e) {
      Assert.assertTrue(e.getMessage(), true);
    }

  }

  /**
   * Test to decode an entry language set against a base language set, each containing the maximum
   * number of codes, i.e. 31 codes.
   * 
   * @throws Exception
   */
  @Test
  public void decoderCornerCaseTest() throws Exception {

    DictMemoization dm = new DictMemoization();

    LanguageSet baseLangSet = LanguageSet.create(
        "sq,ar,bg,ca,uk,hr,cs,da,nl,en,et,fi,fr,de,el,he,hu,is,id,it,ja,kk,ko,lt,ms,no,fa,pl,pt,ro,ru",
        dm);
    EncodeDecodeLanguageSet decoder = new EncodeDecodeLanguageSet(baseLangSet);

    LanguageSet lagSet = decoder.decode(2147483647);
    Assert.assertTrue(LanguageSet.create(
        "sq,ar,bg,ca,uk,hr,cs,da,nl,en,et,fi,fr,de,el,he,hu,is,id,it,ja,kk,ko,lt,ms,no,fa,pl,pt,ro,ru",
        dm).equals(lagSet));

  }

  public static void main(String[] dj) throws Exception {
    DictMemoization dm = new DictMemoization();

    LanguageSet baseLangSet = LanguageSet.create("zh,en,fr,es,de", dm);
    EncodeDecodeLanguageSet encoder = new EncodeDecodeLanguageSet(baseLangSet);

    System.out.println(encoder.encode(LanguageSet.create("es,de", dm)));

    LanguageSet baseLangSet1 = LanguageSet.create("zh,en,es,de,fr", dm);
    EncodeDecodeLanguageSet decoder = new EncodeDecodeLanguageSet(baseLangSet1);
    System.out.println(decoder.decode(20));
  }
}
