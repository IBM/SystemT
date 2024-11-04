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

import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeMap;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.logging.Log;

/**
 * This class provides a container for an ordered bag of Tuple objects. Set-valued attributes are
 * implemented using this class.
 *
 */
public class TupleList
// implements Iterable<Tuple>, Collection<Tuple>
{

  /** Constant <code>COLLECT_STATS=false</code> */
  public static final boolean COLLECT_STATS = false;

  /**
   * A histogram of final list sizes; only generated if COLLECT_STATS is true.
   */
  private static TreeMap<Integer, Integer> sizeHist = new TreeMap<Integer, Integer>();

  private static void recordListSize(int size) {
    Integer sizeObj = new Integer(size);
    Integer countObj = sizeHist.get(sizeObj);

    if (null == countObj) {
      countObj = 1;
    } else {
      countObj = countObj + 1;
    }

    sizeHist.put(sizeObj, countObj);
  }

  /**
   * <p>
   * dumpListStats.
   * </p>
   */
  public static void dumpListStats() {
    System.err.printf("%15s %s\n", "TupleList Length", "Count");
    for (int size : sizeHist.keySet()) {
      System.err.printf("%15d %d\n", size, sizeHist.get(size));
    }
  }

  // private static final int STARTING_ARRAY_SIZE = 1;

  /** Schema for the tuples that this object contains; used for comparison functions. */
  protected AbstractTupleSchema schema;

  /**
   * The first {@link #ntups} elements of this array contain the actual tuples. This array is not
   * allocated until the first tuple is added.
   */
  protected Tuple[] tuples = null;

  protected int ntups = 0;

  /**
   * Flag that is set to true when this list is finalized. Currently used only for collecting
   * overall list length stats.
   */
  protected boolean finalized = false;

  /** Reusable singleton iterator over this tupleList. */
  private final TLIter iterator = new TLIter(this);

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {

    if (!(obj instanceof TupleList)) {
      return false;
    }

    if (obj == this) {
      return true;
    }

    TupleList other = (TupleList) obj;

    if (false == (schema.equals(other.schema))) {
      return false;
    }

    if (ntups != other.ntups) {
      return false;
    }

    for (int i = 0; i < ntups; i++) {
      Tuple tup = tuples[i];
      Tuple otherTup = other.tuples[i];

      if (false == tup.equals(otherTup)) {
        return false;
      }
    }

    return true;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    throw new FatalInternalError("Hashcode not implemented for class %s.",
        this.getClass().getSimpleName());
  }

  /**
   * This constructor is not for customer use. Use the public constructor
   * {@link #TupleList(AbstractTupleSchema)}.
   */
  protected TupleList() {
    this.schema = null;
  }

  /**
   * Constructor to create an empty TupleList object with the given schema.
   *
   * @param schema schema for the tuples in the TupleList
   */
  public TupleList(AbstractTupleSchema schema) {
    this.schema = schema;
  }

  /**
   * Constructor to create TupleList object from the given TupleList. This constructor performs deep
   * copy.
   *
   * @param other source TupleList
   */
  public TupleList(TupleList other) {
    this.schema = other.schema;

    // The tuples array is allocated lazily, so other may not have one.
    if (null != other.tuples) {
      this.tuples = new Tuple[other.tuples.length];
      System.arraycopy(other.tuples, 0, this.tuples, 0, other.ntups);
    }
    this.ntups = other.ntups;

  }

  /**
   * Returns the schema of the tuples in this TupleList.
   *
   * @return schema of the tuples in this TupleList
   */
  public AbstractTupleSchema getSchema() {
    return schema;
  }

  /**
   * Returns a readable version of the set that is formatted for printing.
   *
   * @return a readable version of the set, formatted for printing
   */
  public String toPrettyString() {
    return internalToString(true, false);
  }

  /**
   * Returns a readable version of the set that is formatted for printing and shortened, for error
   * reporting purposes
   *
   * @return a readable version of the set, formatted for printing and restricted to a maximum of 3
   *         tuples
   */
  public String toShortString() {
    return internalToString(true, true);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return internalToString(false, false);
  }

  /**
   * Internal method to convert the tuple list to string representation, for various purposes
   * 
   * @param pretty true to pretty print the tuples (one per line)
   * @param truncate true to restrict the number of tuples printed to a maximum of 3
   * @return
   */
  private String internalToString(boolean pretty, boolean truncate) {
    StringBuffer buf = new StringBuffer();
    buf.append("{");

    if (pretty)
      buf.append("\n");

    // Truncate the number of inputs to print to maximum 3 if required
    int ntupsToPrint = (true == truncate) ? Math.min(ntups, 3) : ntups;

    for (int i = 0; i < ntupsToPrint; i++) {
      Tuple t = tuples[i];

      if (pretty) {
        // Indent
        buf.append("    ");
      }

      buf.append(t.toString());

      if (i < ntups - 1) {
        buf.append(",");
      }

      if (pretty)
        buf.append("\n");
    }

    if (ntups > ntupsToPrint) {
      if (pretty) {
        // Indent
        buf.append("    ");
      }
      buf.append("...");
      if (pretty)
        buf.append("\n");
    }

    buf.append("}");

    return buf.toString();
  }

  /**
   * Adds a tuple to this list.
   *
   * @param tuple tuple to be added
   * @return <code>true</code> if the tuple has been successfully added
   * @throws IllegalArgumentException
   */
  public boolean add(Tuple tuple) throws IllegalArgumentException {
    if (null == tuples || ntups >= tuples.length) {
      // Ran out of space.
      resize(roundUp(ntups + 1));
    }

    tuples[ntups++] = tuple;
    return true;
  }

  /**
   * Increase the size of the tuples array.
   * 
   * @param newCapacity size of the array after the resize operation
   */
  private void resize(int newCapacity) {
    if (newCapacity < ntups) {
      throw new RuntimeException("Array must be large enough to hold all tuples.");
    }

    Tuple[] tmp = new Tuple[newCapacity];
    if (null != tuples) {
      System.arraycopy(tuples, 0, tmp, 0, ntups);
    }
    tuples = tmp;
  }

  /**
   * Method to add all tuples from the given TupleList to this TupleList.
   *
   * @param tupleset source TupleList from which tuples are to be added to this TupleList
   * @throws IllegalArgumentException if the source tuples do not adhere to this TupleList schema
   */
  public void addAll(TupleList tupleset) throws IllegalArgumentException {
    if (null == tupleset) {
      throw new IllegalArgumentException("Null pointer passed to TupleList.addAll()");
    }
    if (!tupleset.schema.equals(schema)) {
      throw new IllegalArgumentException(
          String.format("Tuple schema (%s) does not match" + " the TupleList's schema (%s).",
              schema, tupleset.schema));
    }

    addAllNoChecks(tupleset);
  }

  /**
   * Method to add all tuples from a source TupleList to this TupleList. This method adds all
   * tuples, bypassing type checks. Use with caution, or use {@link #addAll(TupleList)} instead.
   *
   * @param tupleset source TupleList from which tuples are to be added to this TupleList
   */
  public void addAllNoChecks(TupleList tupleset) {
    if (0 == tupleset.ntups) {
      return;
    }

    if (null == tuples || ntups + tupleset.ntups > tuples.length) {
      // Round up to the next power of 2 for good worst-case performance.
      resize(roundUp(ntups + tupleset.ntups));
    }

    System.arraycopy(tupleset.tuples, 0, tuples, ntups, tupleset.ntups);
    ntups += tupleset.ntups;
  }

  /**
   * @param input a positive integer
   * @return input, rounded up to the next power of 2
   */
  private int roundUp(int input) {
    final boolean debug = false;

    // int retExp = (int) (1.0 + Math.log(input) / Math.log(2.0));
    // int ret = 1 << retExp;

    // Integer-based implementation, since IBM decided to slow down
    // floating-point math for their Java 6...
    int ret = input;
    ret--;
    ret |= ret >> 1;
    ret |= ret >> 2;
    ret |= ret >> 4;
    ret |= ret >> 8;
    ret |= ret >> 16;
    ret++;

    if (debug) {
      Log.debug("Rounding %d up to next power of 2, %d", input, ret);
    }
    return ret;
  }

  /**
   * Provides random access within the list. In general, it is recommended to use the
   * {@link #iterator()} interface instead; use this method only if you absolutely need random
   * access.
   *
   * @param index index into the list
   * @return tuple at the indicated index
   */
  public Tuple getElemAtIndex(int index) {
    return tuples[index];
  }

  /**
   * <p>
   * size.
   * </p>
   */
  public int size() {
    return ntups;
  }

  /**
   * Prepare this list for reuse by clearing out any tuples and using a new schema.
   *
   * @param newSchema schema for tuples that will be added to this list after this call
   */
  public void reset(AbstractTupleSchema newSchema) {
    clear();

    schema = newSchema;
  }

  /**
   * Remove all the tuples from this TupleList.
   */
  public void clear() {
    // Make sure that old tuples will be garbage-collected.
    if (null != tuples) {
      Arrays.fill(tuples, 0, ntups, null);
    }

    finalized = false;
    ntups = 0;
  }

  /**
   * Identifies the singleton iterator as unused without clearing the tuples in this list. To clear
   * the tuples, use {@link #clear()}.
   */
  protected void done() {
    iterator.done();
  }

  /**
   * Returns a pointer to the singleton iterator over this TupleList.
   *
   * @return pointer to the singleton iterator over this TupleList; the caller is responsible for
   *         resetting the iterator when finished
   */
  public TLIter iterator() {
    // System.err.printf("Iterating over %s\n", this);
    // Thread.dumpStack();

    if (false == finalized) {
      finalized = true;

      if (COLLECT_STATS) {
        recordListSize(ntups);
      }
    }

    iterator.reset();
    return iterator;
  }

  /**
   * Returns a new iterator that is not associated with this TupleList and can be disposed of freely
   * by the caller.
   *
   * @return a new iterator that is not associated with this TupleList and can be disposed of freely
   *         by the caller
   */
  public TLIter newIterator() {
    return new TLIter(this);
  }

  /**
   * Method to set the {@link java.util.Comparator} that is used to sort the tuples in this
   * TupleList.
   *
   * @param sortOrder tuple comparator to sort the tuples in the TupleList
   */
  public void sort(Comparator<Tuple> sortOrder) {
    if (null == tuples) {
      return;
    }
    Arrays.sort(tuples, 0, ntups, sortOrder);
  }

  /**
   * Sort the tuples according to an arbitrary sort order, including sort orders that require access
   * to a thread-local state.  Performs necessary locking and state management to ensure that the
   * sort operation is thread-safe, even if the comparator is shared among multiple threads.
   *
   * @param comp comparator object for comparing pairs of tuples  
   * @param mt MemoizationTable object; currently, this object is only needed if comp is an instance
   *        of {@link com.ibm.avatar.algebra.datamodel.SlowComparator}
   */
  public void sort(Comparator<Tuple> comp, MemoizationTable mt) {
    if (comp instanceof SlowComparator) {
      // Slow path: Pass in memoization table pointer and acquire a lock
      SlowComparator comparator = ((SlowComparator) comp);
      synchronized (comparator) {
        comparator.setMemoizationTable(mt);
        sort(comparator);

        /*
         * Once sorting is done, reset memoization table to null, so that other threads sharing the
         * comparator do not access stale copy of mt. This ensures that threads requiring mt for
         * sorting are forced to set it to their current value of mt before invoking sort.
         */
        comparator.setMemoizationTable(null);
      }
    } else {
      // Fast path: No MemoizationTable required
      sort(comp);
    }
  }

  /**
   * Factory method to create a TupleList.
   *
   * @return a new instance of TupleList
   */
  public static TupleList createDummyTupleList() {
    return new TupleList();
  }

  /**
   * Reinterpret the tuples of this TupleList as being from a different schema. Allows one to
   * perform "virtual" projection operations over tuples.
   * <p>
   * <b>NOTE:</b> This method creates a <b>SHALLOW<b> copy of the internal list of tuples.
   *
   * @param newSchema new schema to attach to the tuples.
   * @return this object, if the schemas are the same; otherwise, a shallow copy of this TupleList,
   *         with a different schema pointer.
   */
  public TupleList overrideSchema(AbstractTupleSchema newSchema) {
    if (newSchema == schema) {
      // No schema change --> no new TupleList object
      return this;
    }

    // If we get here, there is a schema change. Leave everything else the same.
    // NOTE THE SHALLOW COPY
    TupleList ret = new TupleList();
    ret.finalized = finalized;
    ret.ntups = ntups;
    ret.schema = newSchema;
    ret.tuples = tuples;

    return ret;

  }

}
