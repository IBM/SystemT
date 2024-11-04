/*******************************************************************************
* Copyright IBM
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/
package com.ibm.test.udfs;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.InputStream;

import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.udf.ByteArrayClassLoader;

/**
  * User-defined function that interacts with an external resource. Exemplifies two possible ways to obtain resources from a UDF jar:
  * <ul>
  * <li>{@link Class#getResourceAsStream(String)} - this method expects '/'-separated names; 
  * leading '/' indicates absolute names; all other names are relative to the class's package. 
  * We show how to use both absolute and relative names.
  * </li>
  * <li>{@link ClassLoader#getResourceAsStream(String)} - This method expects '/'-separated names; 
  * also, no leading '/' (all names are absolute)
  * </li>
  * </ul>
  *
  * 
  */
public class PropertyParser
{
  
  /*
   * CONSTANTS
   */
  

  /** Absolute path to the properties file to be used with {@link Class#getResourceAsStream(String)} 
   *  Starts with a leading '/', includes the package name with all '.' converted to '/', and the resource name). */
  private static final String PROPERTIES_FILE_ABSOLUTE_PATH_USE_WITH_CLASS = "/com/ibm/test/udfs/test.properties";
  
  /** Absolute path to the properties file to be used with {@link ClassLoader#getResourceAsStream(String)} 
   *  Includes the package name with all '.' converted to '/', and the resource name.
   *  Does not have a leading '/' (all names are absolute) */
  private static final String PROPERTIES_FILE_ABSOLUTE_PATH_USE_WITH_CLASSLOADER = "com/ibm/test/udfs/test.properties";
  
  /** Path to the properties file relative to the package name to be used with {@link Class#getResourceAsStream(String)}. */
  private static final String PROPERTIES_FILE_RELATIVE_PATH_USE_WITH_CLASS = "test.properties";
  
  /**
   * Test for loading a resource using {@link Class#getResourceAsStream(String)} when the input is an absolute path. 
   * According to Java Doc of {@link Class#getResourceAsStream(String)}: This method delegates the call to its class loader, 
   * after making these changes to the resource name: if the resource name starts with "/", it is unchanged; 
   * otherwise, the package name is prepended to the resource name after converting "." to "/". 
   * If this object was loaded by the bootstrap loader, the call is delegated to {@link ClassLoader#getSystemResource}.
   * 
   * @param propertyName a Span that contains the name of the property we want to retrieve
   */
  public String getResourceFromClassWithAbsolutePath (Span propertyName)
  {
    return getPropertyFromResource (propertyName.getText ().toLowerCase (), PROPERTIES_FILE_ABSOLUTE_PATH_USE_WITH_CLASS, true);
  }

  
  /**
   * Test for loading a resource using {@link Class#getResourceAsStream(String)} when the input is an absolute path. 
   * According to Java Doc of {@link Class#getResourceAsStream(String)}: This method delegates the call to its class loader, 
   * after making these changes to the resource name: if the resource name starts with "/", it is unchanged; 
   * otherwise, the package name is prepended to the resource name after converting "." to "/". 
   * If this object was loaded by the bootstrap loader, the call is delegated to {@link ClassLoader#getSystemResource}.
   * 
   * @param propertyName a Span that contains the name of the property we want to retrieve
   */
  public String getResourceFromClassWithRelativePath (Span propertyName)
  {
    return getPropertyFromResource (propertyName.getText ().toLowerCase (), PROPERTIES_FILE_RELATIVE_PATH_USE_WITH_CLASS, true);
  }
  
  
  /**
   * Test for loading a resource using {@link ClassLoader#getResourceAsStream(String)} when the input is an absolute path.
   * When using the ClassLoader functionality, the input resource name can only be absolute.
   * 
   * @param propertyName a Span that contains the name of the property we want to retrieve
   */
  public String getResourceFromClassLoaderWithAbsolutePath (Span propertyName)
  {
    return getPropertyFromResource (propertyName.getText ().toLowerCase (), PROPERTIES_FILE_ABSOLUTE_PATH_USE_WITH_CLASSLOADER, false);
  }

  /**
   * Example user-defined unary predicate that loads a resource pointing to a properties file, 
   * and retieves the name of a propety from that resource.
   * 
   * @param propertyName a string that contains the name of the property we want to retrieve
   * @param resName a string that contains the name of the resource from where to load the property
   * @param fromClass <code>true</code> to load the resource using {@link Class#getResourceAsStream()},
   *        <code>false</code> to load using {@link ClassLoader#getResourceAsStream()}
   */
  public String getPropertyFromResource (String propertyName, String resName, boolean fromClass)
  {
    try {
      
      Properties prop = new Properties ();
      
      InputStream propStream = fromClass ? this.getClass().getResourceAsStream (resName) 
        : this.getClass().getClassLoader ().getResourceAsStream (resName);
      
      if (propStream != null) {
        prop.load (propStream);
        return prop.getProperty(propertyName);
      }
      else {
        return "Didn't find the resource, should never happen.";
      }
    }
    catch (Exception ex) {
      ex.printStackTrace ();
    }
    return "Exception encountered when loading resource, should never happen.";
  }

}
