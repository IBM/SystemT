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

import java.util.regex.Matcher;


/** Adapter to shoehorn the Java Matcher class into the RegexMatcher interface. */
public class JavaRegexMatcher extends RegexMatcher {

  private Matcher m;

  public JavaRegexMatcher(Matcher m) {
    this.m = m;
  }

  @Override
  public int end(int i) {
    return m.end(i);
  }

  @Override
  public boolean find() {
    return m.find();
  }

  @Override
  public int start(int i) {
    return m.start(i);
  }

  @Override
  public int start() {
    return m.start();
  }

  @Override
  public int end() {
    return m.end();
  }

  @Override
  public boolean find(int pos) {
    return m.find(pos);
  }

  @Override
  public boolean matches() {
    return m.matches();
  }

  @Override
  public boolean lookingAt() {
    return m.lookingAt();
  }

  @Override
  public void region(int start, int end) {
    m.region(start, end);
  }

  @Override
  public void reset(CharSequence text) {
    m.reset(text);
  }



}
