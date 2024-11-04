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

import java.io.IOException;
import java.io.Writer;

/** A writer that just sends everything it gets to the bit bucket. */
public class DevNull extends Writer {

  @Override
  public void close() throws IOException {

  }

  @Override
  public void flush() throws IOException {

  }

  @Override
  public void write(char[] arg0, int arg1, int arg2) throws IOException {

  }

}
