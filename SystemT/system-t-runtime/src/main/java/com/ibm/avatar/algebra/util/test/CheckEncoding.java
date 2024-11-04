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
package com.ibm.avatar.algebra.util.test;

import java.util.ArrayList;
import java.util.Locale;

/**
 * A small program for diagnosing problems with character encodings. Paste some text into the
 * command line, and it will print it out.
 * 
 */
public class CheckEncoding {

  /**
   * @param args
   */
  public static void main(String[] args) throws Exception {

    // Store bytes as ints to avoid overflow on the sign bit.
    ArrayList<Integer> bytes = new ArrayList<Integer>();

    System.err.print("Type some text, followed by return, " + "followed by the EOF character\n"
        + "(Control-Z on windows, Control-D on UNIX)\n");
    int b;
    while (-1 != (b = System.in.read())) {

      // System.err.printf("Got byte %d\n", b);

      bytes.add(b);
    }

    // Now we have the raw byte string. Write it out in several different
    // ways.

    System.err.printf("Raw bytes:\n");
    System.err.printf("%10s", "Positions:");
    for (int i = 0; i < bytes.size(); i++) {
      System.err.printf("%3d ", i);
    }
    System.err.print("\n");

    System.err.printf("%10s", "Bytes:");
    for (int i = 0; i < bytes.size(); i++) {
      System.err.printf("%3d ", bytes.get(i));
    }
    System.err.print("\n");

    System.err.printf("%10s", "As chars:");
    for (int i = 0; i < bytes.size(); i++) {
      String cstr = escapeChar(bytes.get(i));

      System.err.printf("%3s ", cstr);
    }
    System.err.print("\n");

    System.err.printf("%10s", "As UTF-8:");
    byte[] byteArr = new byte[bytes.size()];
    for (int i = 0; i < bytes.size(); i++) {
      byteArr[i] = bytes.get(i).byteValue();
    }
    String str = new String(byteArr);
    byte[] utf8 = str.getBytes("UTF-8");

    for (int i = 0; i < utf8.length; i++) {
      byte curByte = utf8[i];
      String cstr = escapeChar((int) curByte);

      System.err.printf("%3s ", cstr);
    }
    System.err.print("\n");
  }

  /** Escape non-printing characters. */
  private static String escapeChar(int c) {

    if (c < 0) {
      // Assume negative characters are caused by the MSB being
      // interpreted as a sign bit.
      c = c + 255;
    }

    switch (c) {

      case ' ':
        return "SPC";

      case '\n':
        return "\\n";

      case '\r':
        return "\\r";

      default:
        return String.format(Locale.GERMAN, "%c", c);
    }
  }

}
