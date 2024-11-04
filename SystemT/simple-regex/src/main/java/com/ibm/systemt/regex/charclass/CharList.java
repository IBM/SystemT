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
package com.ibm.systemt.regex.charclass;

import java.util.Arrays;

/** A drop-in replacement for ArrayList that is *way* more memory-efficient. */
public class CharList {

  char[] list = new char[1];

  int size = 0;

  public void add(char c) {
    if (size >= list.length) {
      // Ran out of space.
      char[] tmp = new char[list.length * 2];
      System.arraycopy(list, 0, tmp, 0, size);
      list = tmp;
    }

    // System.err.printf("Length is %d; setting element %d\n", list.length,
    // size);
    list[size++] = c;
  }

  public int size() {
    return size;
  }

  public char get(int ix) {
    if (ix < 0 || ix >= size) {
      throw new IndexOutOfBoundsException();
    }
    return list[ix];
  }

  /** Sort the contents of the list. */
  public void sort() {
    Arrays.sort(list, 0, size);
  }


  public int[] toIntArray() {
    int[] ret = new int[size];
    for (int i = 0; i < size; i++) {
      ret[i] = list[i];
    }
    return ret;
  }


}
