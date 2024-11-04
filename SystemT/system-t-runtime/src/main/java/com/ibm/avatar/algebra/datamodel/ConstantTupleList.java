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

package com.ibm.avatar.algebra.datamodel;

/**
 * Represents a constant TupleList. Operators that produce constant output use an instance of this
 * class to cache the constant tuple list so that each thread accessing the cached tuple list would
 * receive its own iterator. An instance of this class should be immutable. So, all mutators are
 * overriden to throw UnsupportedOperationException.
 * 
 */
public class ConstantTupleList extends TupleList {

  private static final String UNMODIFIABLE_EXCEPTION_MESSAGE =
      "An instance of ConstantTupleList cannot be modified. The operation you requested for is not supported on this object: %s";

  protected ConstantTupleList() {
    // dummy constructor for internal use
  }

  /**
   * Constructor to create TupleList object from the given TupleList. This constructor performs deep
   * copy.
   * 
   * @param other source TupleList
   */
  public ConstantTupleList(TupleList other) {
    super(other);
  }

  public static ConstantTupleList shallowCopy(TupleList other) {
    ConstantTupleList ret = new ConstantTupleList();
    ret.schema = other.schema;
    ret.ntups = other.ntups;
    ret.tuples = other.tuples;
    return ret;
  }

  @Override
  public TLIter iterator() {
    // return a new iterator every time, since multiple threads might access the ConstantTupleList
    // concurrently.
    return newIterator();
  }

  @Override
  public boolean add(Tuple tuple) throws IllegalArgumentException {
    throw new UnsupportedOperationException(
        String.format(UNMODIFIABLE_EXCEPTION_MESSAGE, "add(Tuple tuple)"));
  }

  @Override
  public void addAll(TupleList tupleset) throws IllegalArgumentException {
    throw new UnsupportedOperationException(
        String.format(UNMODIFIABLE_EXCEPTION_MESSAGE, "addAll(TupleList tupleset)"));
  }

  @Override
  public void addAllNoChecks(TupleList tupleset) {
    throw new UnsupportedOperationException(
        String.format(UNMODIFIABLE_EXCEPTION_MESSAGE, "addAllNoChecks(TupleList tupleset)"));
  }

  @Override
  public void reset(AbstractTupleSchema newSchema) {
    throw new UnsupportedOperationException(
        String.format(UNMODIFIABLE_EXCEPTION_MESSAGE, "reset(AbstractTupleSchema newSchema)"));
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException(
        String.format(UNMODIFIABLE_EXCEPTION_MESSAGE, "clear()"));
  }
}
