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
package com.ibm.avatar.algebra.util.file;

import java.io.File;
import java.io.FileFilter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** File filtering using a regex */
public class RegexFileFilter implements FileFilter {
  Pattern regex;

  public RegexFileFilter(String regexstr) {
    regex = Pattern.compile(regexstr);
  }

  @Override
  public boolean accept(File pathname) {
    Matcher m = regex.matcher(pathname.getName());
    return (m.matches());
  }

}
