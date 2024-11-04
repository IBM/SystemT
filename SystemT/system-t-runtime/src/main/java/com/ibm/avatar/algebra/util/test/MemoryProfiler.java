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
package com.ibm.avatar.algebra.util.test;

/**
 * Class for tracking memory usage. The user calls {@link #before()} before every unit of work and
 * {@link #after()} after every unit of work, and the class takes care of the rest. Tracks the
 * number of objects allocated between trace points, as well as the working set size at trace
 * points.
 */
public class MemoryProfiler {

  /** State of the profiler, for making sure that the API is used correctly. */
  enum State {
    MEASURING, SKIPPING, IDLE
  }

  State state;

  long memBefore, memAfter;

  /**
   * Probability that will actually perform a measurement on a call to before()/after()
   */
  double sampleProb;

  public MemoryProfiler(double sampleProb) {
    this.state = State.IDLE;
    this.sampleProb = sampleProb;
  }

  public void before() {
    if (State.IDLE != this.state) {
      throw new RuntimeException("Called before() twice in a row.");
    }

    if (Math.random() <= sampleProb) {
      // Measure memory usage before the document.
      System.gc();
      memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
      totalLiveBytesBefore += memBefore;

      minLiveBytesBefore = myMin(minLiveBytesBefore, memBefore);
      maxLiveBytesBefore = myMax(maxLiveBytesBefore, memBefore);

      this.state = State.MEASURING;
    } else {
      this.state = State.SKIPPING;
    }
  }

  public void after() {
    if (State.MEASURING == this.state) {
      // Measure memory usage after the document; hopefully no garbage
      // collection has occurred.
      memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
      totalBytesAlloc += (memAfter - memBefore);

      numMeasurements++;
    }

    numTraces++;
    this.state = State.IDLE;
  }

  /**
   * Total bytes allocated across all calls to before()/after()
   */
  private long totalBytesAlloc = 0;

  /**
   * Total bytes of live objects across all calls to {@link #before()}
   */
  private long totalLiveBytesBefore = 0;

  /** Special value for high and low water marks to indicate "not yet set" */
  private static final long NOT_YET_SET = -1;

  // High and low water marks
  private long minLiveBytesBefore = NOT_YET_SET;

  private long maxLiveBytesBefore = NOT_YET_SET;

  /**
   * Version of {@link Math#min(long, long)} that takes into account a "special" value for unset
   * counters.
   */
  private static long myMin(long a, long b) {
    if (NOT_YET_SET == a) {
      return b;
    } else if (NOT_YET_SET == b) {
      return a;
    } else {
      return Math.min(a, b);
    }
  }

  private static long myMax(long a, long b) {
    if (NOT_YET_SET == a) {
      return b;
    } else if (NOT_YET_SET == b) {
      return a;
    } else {
      return Math.max(a, b);
    }
  }

  /**
   * Number of calls to before()/after()
   */
  @SuppressWarnings("all")
  private int numTraces = 0;

  /**
   * Number of calls to before()/after() when measurement took place.
   */
  private int numMeasurements = 0;

  public double getBytesPerTrace() {
    return (double) totalBytesAlloc / (double) numMeasurements;
  }

  /**
   * @return the average number of bytes of live objects measured in a call to before()
   */
  public double getAvgLiveBytesBefore() {
    return (double) totalLiveBytesBefore / (double) numMeasurements;
  }

  public long getMinLiveBytesBefore() {
    return minLiveBytesBefore;
  }

  public long getMaxLiveBytesBefore() {
    return maxLiveBytesBefore;
  }

  /**
   * Print memory usage to standard error. <b>NOTE:</b> Do <b>NOT</b> call this method in between
   * calls to {@link #before} and {@link #after}!
   * 
   * @param when string that describes the point in execution at which you're dumping memory usage
   */
  public static void dumpMemory(String when) {
    System.gc();
    System.gc();
    System.gc();
    long mem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    System.err.printf("%20s: %s bytes of live objects in heap\n", when, fmtWithCommas(mem));
  }

  /** Format a number in the form 1,234,567 */
  private static String fmtWithCommas(long num) {

    // First, generate the string.
    String numAsStr = String.valueOf(num);

    // Compute how far in the first comma happens.
    int commaOff = (numAsStr.length() - 1) % 3;

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < numAsStr.length(); i++) {
      sb.append(numAsStr.charAt(i));

      if (i < numAsStr.length() - 1 && commaOff == i % 3) {
        sb.append(',');
      }
    }

    // sb.append(String.format(" (%d)", num));

    return sb.toString();
  }

  /**
   * Print the total heap size, including dead objects.
   * 
   * @param when string that describes the point in execution at which you're dumping memory usage
   */
  public static void dumpHeapSize(String when) {
    long mem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    System.err.printf("%20s: Total heap size (including garbage) " + "is %s bytes\n", when,
        fmtWithCommas(mem));
  }

  /**
   * Print the total heap size, including dead objects.
   * 
   * @param when string that describes the point in execution at which you're dumping memory usage
   */
  public static String dumpHeapMemory(String when) {
    long mem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    System.err.printf("%20s: Total heap size (including garbage) " + "is %s bytes\n", when,
        fmtWithCommas(mem));
    String memString = String.valueOf(mem);
    // Long.toString(mem);
    return memString;
  }

}
