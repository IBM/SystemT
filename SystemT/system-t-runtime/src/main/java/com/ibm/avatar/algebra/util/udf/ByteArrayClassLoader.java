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
package com.ibm.avatar.algebra.util.udf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

/**
 * Classloader for loading classes from bytecode stored in one or more arrays of bytes. Uses the
 * default system classloader to instantiate any classes it doesn't know about.
 * 
 */
public class ByteArrayClassLoader extends ClassLoader {

  private static final String CLASS_FILE_SUFFIX = ".class";

  private static final String DIR_SUFFIX = "/";

  /**
   * Bytecode for classes that this instance knows how to load, indexed by fully-qualified class
   * name.
   */
  private HashMap<String, byte[]> classNameToBytes = null;

  /**
   * Bytecode for other resources that this instance knows how to load This class splits the
   * resources into class/non-class so that findClass won't load the wrong resource
   */
  private HashMap<String, ArrayList<byte[]>> resourceNameToBytes = null;

  /**
   * Main constructor.
   * 
   * @param classNameToBytes Bytecode for classes that this instance knows how to load, indexed by
   *        fully-qualified class name
   * @param parent classloader to defer to for any classes that this loader doesn't know about, or
   *        null to throw a {@link ClassNotFoundException} for those classes.
   */
  public ByteArrayClassLoader(HashMap<String, byte[]> classNameToBytes,
      HashMap<String, ArrayList<byte[]>> resourceNameToBytes, ClassLoader parent) {
    super(parent);
    if (null == classNameToBytes) {
      throw new NullPointerException();
    }
    this.classNameToBytes = classNameToBytes;

    if (null == resourceNameToBytes) {
      throw new NullPointerException();
    }
    this.resourceNameToBytes = resourceNameToBytes;
  }

  /**
   * Initialize a class loader instance from the (binary) contents of a jar file, where these
   * contents are stored in an array in memory.
   * 
   * @param jarContents the jar file, loaded into a buffer
   * @param parent class loader to use for loading any classes not in the jar, or null to indicate
   *        that only classes in the jar should be loaded
   * @return class loader that will use the contents of the jar to load classes when possible, but
   *         will defer to the system class loader otherwise.
   * @throws IOException if it can't decode the jar file contents from what's in the byte array
   */
  public static ByteArrayClassLoader fromJarContents(byte[] jarContents, ClassLoader parent)
      throws IOException {
    HashMap<String, byte[]> classNameToBytes = new HashMap<String, byte[]>();
    HashMap<String, ArrayList<byte[]>> resourceNameToBytes =
        new HashMap<String, ArrayList<byte[]>>();

    // Open up the jar file
    JarInputStream jarStream = new JarInputStream(new ByteArrayInputStream(jarContents));

    ZipEntry entry = null;

    // Iterate through the contents of the jar file, detecting class files
    // by file extension.
    while (null != (entry = jarStream.getNextEntry())) {

      String name = entry.getName();

      if (name.endsWith(DIR_SUFFIX)) {
        // Do nothing if the entry is a directory

        // Read the next file
        byte[] buf = readEntry(jarStream, entry);

      } else if (name.endsWith(CLASS_FILE_SUFFIX)) {

        // Read the next file
        byte[] buf = readEntry(jarStream, entry);

        // If we get here, the entry appears to be a class file, and its
        // name ends in ".class"

        // Strip off the ".class" at the end of the name to get the name of
        // the class.
        String className = name.substring(0, name.length() - CLASS_FILE_SUFFIX.length());

        // Change the slashes to dots.
        className = className.replace('/', '.');

        classNameToBytes.put(className, buf);
      } else {
        // This non-directory entry doesn't end in ".class"; assume it's a source file
        // or resource or something.

        // Read the next file
        ArrayList<byte[]> bufs = readResourceEntry(jarStream, entry);

        resourceNameToBytes.put(name, bufs);
      }
    }

    jarStream.close();

    return new ByteArrayClassLoader(classNameToBytes, resourceNameToBytes, parent);
  }

  /**
   * This is where the magic happens. Override the default implementation of findClass to use the
   * byte arrays in this class loader instance as a source of bytecode.
   */
  @SuppressWarnings({"all"})
  @Override
  public Class findClass(String name) throws ClassNotFoundException {
    if (classNameToBytes.containsKey(name)) {
      // This instance knows about the indicated class; load the class
      // directly from a byte array.
      byte[] b = classNameToBytes.get(name);
      return defineClass(name, b, 0, b.length);
    } else {
      // This instance knows nothing about the indicated class; ask the
      // superclass.
      return super.findClass(name);
    }

  }

  /**
   * Override the default implementation of getResourceAsStream to use the byte arrays in this class
   * loader instance as a source of bytecode.
   */
  @Override
  public InputStream getResourceAsStream(String name) {
    if (classNameToBytes.containsKey(name)) {
      // This instance knows about the indicated class; return the byte
      // array as a stream.
      byte[] b = classNameToBytes.get(name);
      return new ByteArrayInputStream(b);
    } else if (resourceNameToBytes.containsKey(name)) {
      // This instance knows about the indicated resource; return the byte
      // array as a stream.
      ArrayList<byte[]> bParts = resourceNameToBytes.get(name);
      ArrayList<ByteArrayInputStream> seqByteArrays =
          new ArrayList<ByteArrayInputStream>(bParts.size());
      for (byte[] b : bParts)
        seqByteArrays.add(new ByteArrayInputStream(b));
      return new SequenceInputStream(Collections.enumeration(seqByteArrays));
    } else {
      // This instance knows nothing about the indicated class/resource; ask the
      // superclass.
      return super.getResourceAsStream(name);
    }

  }

  /*
   * UTILITY METHODS
   */

  /**
   * Method to read a single entry from a jar file.
   * 
   * @return the content of the entry, as an array (no padding on the end of the array)
   */
  private static byte[] readEntry(JarInputStream jarStream, ZipEntry entry) throws IOException {
    byte[] buf;
    long bufsz = entry.getSize();

    if (-1 == bufsz) {
      // SPECIAL CASE: No buffer size in the entry.
      bufsz = 4096;
      // END SPECIAL CASE
    }
    buf = new byte[(int) bufsz];

    // Calls to read() may not return all the bytes, so keep trying
    // until we get everything.
    int off = 0;
    for (;;) {
      int nread = jarStream.read(buf, off, (int) (bufsz - off));
      if (nread <= 0) {
        break;
      }

      off += nread;
      if (off > buf.length) {
        // This should never happen.
        throw new RuntimeException("Exceeded buffer size");
      } else if (buf.length == off) {
        // Ran out of space. Lengthen the buffer.
        byte[] newBuf = new byte[buf.length * 2];
        System.arraycopy(buf, 0, newBuf, 0, buf.length);
        buf = newBuf;
        // Need to reset bufsz as buffer size increases.
        bufsz = newBuf.length;
      }
    }

    if (off < buf.length) {
      // Buf has extra space at the end. Shrink it down.
      byte[] newBuf = new byte[off];
      System.arraycopy(buf, 0, newBuf, 0, off);
      buf = newBuf;
    }

    return buf;
  }

  /**
   * Method to read a single entry from a jar file.
   * 
   * @return the content of the entry, as an array of byte arrays (no padding on the end of each of
   *         the byte arrays); using an array of byte arrays, in order to allow loading resources
   *         that are larger than Integer.MAX_VALUE (~2GB)
   */
  private static ArrayList<byte[]> readResourceEntry(JarInputStream jarStream, ZipEntry entry)
      throws IOException {
    ArrayList<byte[]> ret = new ArrayList<byte[]>();
    byte[] buf;
    long bufsz = entry.getSize();

    if (-1 == bufsz || bufsz > Integer.MAX_VALUE / 2) {
      // SPECIAL CASE: No buffer size in the entry or the entry is larger than the max size of a
      // Java array, start small
      // and we increase it progressively later
      bufsz = 4096;
      // END SPECIAL CASE
    }
    buf = new byte[(int) bufsz];

    // Calls to read() may not return all the bytes, so keep trying
    // until we get everything.
    int off = 0;
    for (;;) {
      int nread = jarStream.read(buf, off, (int) (bufsz - off));
      if (nread <= 0) {
        break;
      }

      off += nread;

      if (off > buf.length) {
        // This should never happen.
        throw new RuntimeException("Exceeded buffer size");
      } else if (buf.length == off) {
        // Ran out of space. Lengthen the buffer, or start a new buffer
        if (buf.length > Integer.MAX_VALUE / 2) {
          // If we double the buffer size, we exceed the max size of a Java array

          // Add the new byte array to our return list
          ret.add(buf);

          // Reset the buf
          bufsz = 4096;
          buf = new byte[(int) bufsz];
          off = 0;
        } else {
          // We can safely double the buffer size
          byte[] newBuf = new byte[buf.length * 2];
          System.arraycopy(buf, 0, newBuf, 0, buf.length);
          buf = newBuf;
          // Need to reset bufsz as buffer size increases.
          bufsz = newBuf.length;
        }
      }
    }

    if (off < buf.length) {
      // Buf has extra space at the end. Shrink it down.
      byte[] newBuf = new byte[off];
      System.arraycopy(buf, 0, newBuf, 0, off);
      buf = newBuf;
    }

    // Add the new byte array to our return list
    ret.add(buf);

    return ret;
  }
}
