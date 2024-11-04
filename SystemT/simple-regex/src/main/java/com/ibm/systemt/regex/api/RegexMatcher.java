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
package com.ibm.systemt.regex.api;

/**
 * Generalized regular expression matcher object. API is based on {@link java.util.regex.Matcher}.
 */
public abstract class RegexMatcher {

  public abstract boolean find();

  public abstract boolean find(int pos);

  public abstract int start(int i);

  public abstract int end(int i);

  public abstract int start();

  public abstract boolean matches();

  public abstract boolean lookingAt();

  public abstract void region(int start, int end);

  public abstract int end();

  public abstract void reset(CharSequence text);

}
