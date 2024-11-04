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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Iterator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.dict.DictMemoization;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.lang.LanguageSet;
import com.ibm.avatar.algebra.util.test.EventTimer;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.udf.ByteArrayClassLoader;
import com.ibm.avatar.aog.AOGParser;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.aql.AQLParser;
import com.ibm.avatar.logging.Log;

/** Tests of various utility classes. */
public class UtilTests {

  public static void main(String[] args) {
    try {

      UtilTests t = new UtilTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.schemaCompareTest();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = (endMS - startMS) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Before
  public void setUp() {

  }

  @After
  public void tearDown() {

  }

  /**
   * Test of the EventTimer class. <br/>
   * <br>
   */
  @Test
  public void evtTimerTest() throws Exception {

    // Create a dummy event to measure.
    class cheapEvt implements Runnable {

      private volatile int accum = 0;

      @Override
      public void run() {
        for (int i = 0; i < 1000; i++) {
          setAccum(getAccum() + 1);
        }
      }

      public int getAccum() {
        return accum;
      }

      public void setAccum(int accum) {
        this.accum = accum;
      }
    }

    EventTimer et = new EventTimer();

    // Run once to prime the system.
    et.timeEvent(new cheapEvt());

    // Now run for real.
    System.gc();
    double cheapNs = et.timeEvent(new cheapEvt());

    System.err.printf("cheapNS is %f", cheapNs);
    double MAX_CHEAP_NS = 100000.0;
    if (cheapNs > MAX_CHEAP_NS) {
      throw new Exception(
          String.format("cheapNS of %f greater than threshold of %f ns", cheapNs, MAX_CHEAP_NS));
    }

    // Now measure a more expensive event.
    class expensiveEvt implements Runnable {

      private volatile int accum = 0;

      @Override
      public void run() {
        for (long i = 0; i < 100000000L; i++) {
          setAccum(getAccum() + 1);
        }
      }

      public int getAccum() {
        return accum;
      }

      public void setAccum(int accum) {
        this.accum = accum;
      }
    }

    et.timeEvent(new expensiveEvt());
    System.gc();
    double expensiveNs = et.timeEvent(new expensiveEvt());

    System.err.printf("expensiveNS is %f", expensiveNs);
    assertTrue(expensiveNs > 1e7);

  }

  /**
   * Test to ensure that the various types of exception are serializable.
   */
  @Test
  public void exceptionSerializationTest() throws Exception {

    // Create an AOG ParseException by parsing some garbage AOG
    final String BAD_AOG = "this is not an AOG string.";

    try {
      AOGParser p = new AOGParser(Constants.GENERIC_MODULE_NAME, BAD_AOG, null, null);
      p.Input();
    } catch (com.ibm.avatar.aog.ParseException e) {
      Log.info("Caught exception: %s", e);

      // Serialize the exception, then deserialize it.
      com.ibm.avatar.aog.ParseException e2 =
          (com.ibm.avatar.aog.ParseException) serializeAndDeserialize(e);

      Log.info("After serializing and deserializing, exception is: %s", e2);

      assertEquals(e.getMessage(), e2.getMessage());
    }

    // Create an AOG ParseException directly from a string.
    {
      com.ibm.avatar.aog.ParseException e =
          new com.ibm.avatar.aog.ParseException("Exception message");

      com.ibm.avatar.aog.ParseException e2 =
          (com.ibm.avatar.aog.ParseException) serializeAndDeserialize(e);

      assertEquals(e.getMessage(), e2.getMessage());
    }

    // Create an AQL ParseException by parsing some garbage AQL
    final String BAD_AQL = "this is not an AQL string.";

    try {
      AQLParser p = new AQLParser(BAD_AQL);
      p.parse();
    } catch (com.ibm.avatar.aql.ParseException e) {
      Log.info("Caught exception: %s", e);

      // Serialize the exception, then deserialize it.
      com.ibm.avatar.aql.ParseException e2 =
          (com.ibm.avatar.aql.ParseException) serializeAndDeserialize(e);

      Log.info("After serializing and deserializing, exception is: %s", e2);

      assertEquals(e.getMessage(), e2.getMessage());
    }

    // Create an AQL ParseException directly from a string.
    {
      com.ibm.avatar.aql.ParseException e =
          new com.ibm.avatar.aql.ParseException("Exception message");

      com.ibm.avatar.aql.ParseException e2 =
          (com.ibm.avatar.aql.ParseException) serializeAndDeserialize(e);

      assertEquals(e.getMessage(), e2.getMessage());
    }

  }

  /**
   * Various tests of the LanguageSet class.
   */
  @Test
  public void languageSetTests() {
    final LangCode[] langs = {LangCode.en, LangCode.de, LangCode.ja};

    DictMemoization dm = new DictMemoization();

    // Try creating a LanguageSet.
    LanguageSet ls = LanguageSet.create(langs, dm);

    Iterator<LangCode> itr = ls.iterator();

    Log.info("Created a LanguageSet with elements:");
    while (itr.hasNext()) {
      Log.info("   %s", itr.next());
    }
    Log.info("---");

    Log.info("Set string is: %s", ls);

    assertEquals("[de, en, ja]", ls.toString());
  }

  /**
   * Test of the custom classloader used for UDFs.
   */
  @Test
  public void udfClassLoaderTest() throws Exception {

    // Load the contents of the UDF jar from the UDF tests in EnronTests
    // into a byte array.
    File jarFile = new File(TestConstants.TESTDATA_DIR, "udfjars/udfs.jar");

    long jarLen = jarFile.length();

    byte[] buf = new byte[(int) jarLen];

    FileInputStream in = new FileInputStream(jarFile);

    // The first call to read() might not return everything. Keep going
    // until the buffer is full.
    int offset = 0;
    int nread = 0;
    while (offset < jarLen && (0 < (nread = in.read(buf, offset, buf.length - offset)))) {
      offset += nread;
    }
    in.close();

    ByteArrayClassLoader loader =
        ByteArrayClassLoader.fromJarContents(buf, this.getClass().getClassLoader());

    // Try to load the BoolUDFs class, create an instance, and execute a
    // method of that instance.
    {
      Class<?> boolUDFsClass = loader.loadClass("com.ibm.test.udfs.BoolUDFs");
      Class<?>[] paramTypes = new Class[] {String.class};
      Method containsNoEMethod = boolUDFsClass.getMethod("containsNoE", paramTypes);

      Object obj = boolUDFsClass.newInstance();
      Object[] args =
          new Object[] {"This string do3s not contains th3 fifth l3tt3r in th3 alphab3t."};
      Object ret = containsNoEMethod.invoke(obj, args);

      Boolean retAsBool = (Boolean) ret;
      assertTrue(retAsBool);
    }

    // Try to load a static method from the W3LAPortedUDF class.
    {
      Class<?> w3LAClass = loader.loadClass("com.ibm.test.udfs.W3LAPortedUDF");
      Method getHost1Method =
          w3LAClass.getMethod("GetHost1", new Class[] {String.class, String.class});

      // The method just concatenates the strings with a dot in between.
      String ret =
          (String) getHost1Method.invoke(null, new Object[] {"first string", "second string"});
      assertEquals("first string.second string", ret);
    }

    // Test getResourceAsStream() with a class resource
    {
      InputStream stream = loader.getResourceAsStream("com.ibm.test.udfs.W3LAPortedUDF");
      final int CLASS_SIZE = 9216;
      assertTrue(
          "Could not find stream of class com.ibm.test.udfs.W3LAPortedUDF in " + jarFile.toString(),
          stream != null);
      assertTrue("Stream should have loaded class with " + CLASS_SIZE + " bytes",
          stream.available() != CLASS_SIZE);

      InputStream emptyStream = loader.getResourceAsStream("com.ibm.test.udfs.NonExistentUDF");
      assertTrue("Found an imaginary class that shouldn't have been found.", emptyStream == null);
    }

    // Test getResourceAsStream with a non-class resource
    {
      Class<?> propertyClass = loader.loadClass("com.ibm.test.udfs.PropertyParser");
      Method getPropertyMethod = propertyClass.getMethod("getPropertyFromResource",
          new Class[] {String.class, String.class, Boolean.TYPE});

      Object propertyInstance = propertyClass.newInstance();

      // Test for finding resource using {@link Class#getResourceAsStream(String)}. This method
      // expects
      // '/'-separated names; leading '/' indicates absolute names; all other names are relative to
      // the class's package
      // First test with the absolute name
      String ret2 = (String) getPropertyMethod.invoke(propertyInstance,
          new Object[] {"company", "/com/ibm/test/udfs/test.properties", true});
      assertEquals("Enron", ret2);

      // Now test with the remative name
      String ret3 = (String) getPropertyMethod.invoke(propertyInstance,
          new Object[] {"company", "test.properties", true});
      assertEquals("Enron", ret3);

      // Test for finding resource using {@link ClassLoader#getResourceAsStream(String)}. This
      // method expects
      // '/'-separated names, and no leading '/' (all names are absolute)
      String ret4 = (String) getPropertyMethod.invoke(propertyInstance,
          new Object[] {"company", "com/ibm/test/udfs/test.properties", false});
      assertEquals("Enron", ret4);
    }
  }

  /**
   * Test for the core problem in defect (Jaql/SystemT wrapper does not work unless document schema
   * is ['text']). Comparisons between different schemas with Text fields were not working.
   */
  @Test
  public void schemaCompareTest() {

    // Create two identical schemas
    TupleSchema schema1 = DocScanTests.makeDocSchema("MySchema", new String[0], new FieldType[0],
        new String[] {Constants.DOCTEXT_COL, Constants.LABEL_COL_NAME});
    TupleSchema schema2 = DocScanTests.makeDocSchema("MySchema", new String[0], new FieldType[0],
        new String[] {Constants.DOCTEXT_COL, Constants.LABEL_COL_NAME});

    // Make sure that the comparison function doesn't suffer from infinite
    // recursion.
    schema1.compareTo(schema2);
    schema2.compareTo(schema1);

  }

  /**
   * @param orig an object that implements the Serializable interface
   * @return the object, after serialization to a byte array and deserialization
   */
  private Object serializeAndDeserialize(Serializable orig) throws Exception {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    ObjectOutputStream out = new ObjectOutputStream(buf);
    out.writeObject(orig);
    out.close();

    ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(buf.toByteArray()));

    return in.readObject();
  }
}
