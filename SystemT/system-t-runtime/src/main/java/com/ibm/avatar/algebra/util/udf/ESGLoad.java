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

import com.ibm.avatar.logging.Log;

/**
 * 
 * makes ESG libs load in a single classloader.
 *
 */
public class ESGLoad {
  public static ClassLoader sharedClassLoader = null;

  static {
    Log.info("Loaded shared ESG class %s", ESGLoad.class.getName());
    Log.info("Classloader for ESG class %s", ESGLoad.class.getClassLoader());
  }

}
