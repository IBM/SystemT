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
package com.ibm.avatar.algebra.util.pmml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.TreeMap;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;

import com.ibm.avatar.api.exceptions.FatalInternalError;

public class CompilerOutputStub extends ForwardingJavaFileManager<JavaFileManager> {

  private TreeMap<String, OutFileStub> outputMap =
      new TreeMap<String, CompilerOutputStub.OutFileStub>();

  /**
   * Main constructor.
   * 
   * @param fm main file manager, used for all calls that are not intercepted.
   */
  public CompilerOutputStub(JavaFileManager fm) {
    super(fm);
  }

  /**
   * Make sure the compiler is finished executing before calling this method! Retrieves data that
   * the compiler passed to callbacks while it was running.
   * 
   * @param packageName name of the package containing the target class
   * @param className name of one of the classes that the compiler compiled while attached to this
   *        stub
   * @return the bytecode (class file contents) that the compiler generated for the indicated class.
   */
  public byte[] getClassBytes(String packageName, String className) {
    String key = String.format("%s.%s", packageName, className);

    // Check for failure to compile
    if (false == outputMap.containsKey(key)) {
      throw new FatalInternalError("Java compiler did not generate code for temporary class %s",
          key);
    }

    return outputMap.get(key).buf.toByteArray();
  }

  // We override this API call so as to capture the compiler's attempts to write Java-related
  // outputs.
  @Override
  public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind,
      FileObject sibling) throws IOException {
    // System.err.printf ("Compiler called getJavaFileForOutput(%s, %s, %s, %s)\n", location,
    // className, kind, sibling);

    if (Kind.CLASS.equals(kind)) {
      // Create a callback object and pass that object back to the compiler.
      OutFileStub stub = new OutFileStub(className);
      outputMap.put(className, stub);
      return stub;
    } else {
      // Non-class outputs of the compiler shouldn't be captured.
      return super.getJavaFileForOutput(location, className, kind, sibling);
    }
  }

  /** Stub for the compiler's file handle object. Captures binary writes in a byte buffer. */
  private static class OutFileStub extends SimpleJavaFileObject {

    // private String className;

    /**
     * Internal buffer; initialized when the compiler attempts to write binary data to the "file".
     */
    private ByteArrayOutputStream buf = null;

    protected OutFileStub(String className) {
      // Generate a URI with the "string" type. Note how we replace the dots in the package name
      // with slashes.
      super(URI.create("string:///" + className.replace('.', '/') + Kind.CLASS.extension),
          Kind.CLASS);
      // this.className = className;
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
      // Intercept calls to this API and substitute our internal buffer.
      // System.err.printf ("openOutputStream(%s)\n", className);

      // Compiler may be multithreaded
      synchronized (this) {
        buf = new ByteArrayOutputStream();
        return buf;
      }
    }

  }

}
