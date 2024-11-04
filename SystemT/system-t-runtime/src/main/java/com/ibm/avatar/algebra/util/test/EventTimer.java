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

import java.util.Arrays;

/** Class for timing a repeatable but (possibly) very brief event. */
public class EventTimer {

  /**
   * How long a call to {@link System#nanoTime()} takes; measured via {@link #profileTimer(int)} in
   * constructor.
   */
  double nanoTimeNS;

  /** Clock tick interval, in nanoseconds. */
  double clockTickNS;

  /**
   * Minimum amount of time that the event must run for (in nanoseconds), regardless of clock tick
   * time.
   */
  long minNS = 100000000L;

  /**
   * Minimum amount of time that the event must run, in clock ticks.
   */
  int minTicks = 1000;

  /**
   * How far we aim to overshoot the minimum time when timing multiple runs of an event.
   */
  double safetyFactor = 1.5;

  /**
   * Overall minimum number of nanoseconds to run an event in order to get usable timing
   * information.
   */
  double globalMinNS = -1;

  private static boolean debug = false;

  public EventTimer() {
    nanoTimeNS = profileTimer(100000);
    clockTickNS = profileClockTick(nanoTimeNS);

    updateMinNS();
  }

  private void updateMinNS() {
    globalMinNS = Math.max(minNS, clockTickNS * minTicks);
  }

  /**
   * @param minNS minimum number of nanoseconds that an event is required to run for (1e9 ns = 1
   *        sec)
   */
  public void setMinNS(long minNS) {
    this.minNS = minNS;
    updateMinNS();
  }

  public long getMinNS() {
    return minNS;
  }

  /**
   * @param evt callback for performing the event once.
   * @return amount of time that a single instance of the event takes
   */
  public double timeEvent(Runnable evt) {
    // Start by measuring how many copies of the event we need to run to get
    // at least one clock tick.
    long startNs = System.nanoTime();
    long endNs = startNs;
    int numRepeat = 0;
    while (endNs <= startNs) {
      evt.run();
      numRepeat++;
      // Correct for time spent in nanoTime()
      endNs = (long) (System.nanoTime() - numRepeat * nanoTimeNS);
    }
    long elapsedNs = endNs - startNs;
    double nsPerEvt = (double) elapsedNs / (double) numRepeat;

    if (debug) {
      System.err.printf("Initial estimate: %1.1f ns/evt\n", nsPerEvt);
    }

    // Keep retrying until the event has run for long enough to get an
    // accurate timing.
    while (elapsedNs < globalMinNS) {

      // Compute how many times we should need to run the event to get the
      // required minimum amount of time.
      numRepeat = (int) (1.0 + (safetyFactor * globalMinNS / nsPerEvt));

      if (debug) {
        System.err.printf("Will run test %d times\n", numRepeat);
      }

      startNs = System.nanoTime();

      for (int i = 0; i < numRepeat; i++) {
        evt.run();
      }

      // Correct for the cost of the nanoTime() call.
      endNs = System.nanoTime() - (long) nanoTimeNS;

      elapsedNs = endNs - startNs;

      nsPerEvt = (double) elapsedNs / (double) numRepeat;

    }

    if (debug) {
      System.err.printf("%d events in %d ns --> %1.1f ns/evt\n", numRepeat, elapsedNs, nsPerEvt);
    }
    return nsPerEvt;

  }

  /**
   * Dummy variable that keeps the benchmarking loop in {@link #profileTimer(int)} from getting
   * optimized out of existence.
   */
  static long dummyAccum = 0;

  /**
   * Measure how long {@link java.lang.System#nanoTime()} takes.
   * 
   * @return time per call in nanoseconds
   */
  public static double profileTimer(int numcall) {

    long startNs = System.nanoTime();

    // Use (numiter - 1) iterations to account for the time we spend
    // measuring start and end.
    for (int i = 0; i < numcall - 1; i++) {
      // Incrementing the dummy counter keeps this loop from being
      // optimized out of existence.
      dummyAccum += System.nanoTime();
    }

    long endNs = System.nanoTime();

    long elapsedNs = endNs - startNs;

    double nsPerCall = (double) elapsedNs / (double) numcall;

    System.err.printf("%d calls to nanoTime()" + " in %d ns --> %1.3f ns/call\n", numcall,
        elapsedNs, nsPerCall);

    return nsPerCall;
  }

  /**
   * Measure the clock tick time for {@link java.lang.System#nanoTime()}.
   * 
   * @param nanoTimeNS how long a single call to {@link java.lang.System#nanoTime()} takes, as
   *        measured by {@link #profileTimer(int)}.
   */
  public static double profileClockTick(double nanoTimeNS) {
    // How many samples of the clock tick interval we acquire before
    // convincing ourselves we have the right answer.
    final int TICK_SAMP = 100;

    long[] times = new long[TICK_SAMP];

    for (int i = 0; i < TICK_SAMP; i++) {
      long startNs = System.nanoTime();

      // Keep polling until we see a change.
      long endNs = startNs;
      while (endNs <= startNs) {
        // Note that we *don't* correct for the cost of nanoTime().
        endNs = System.nanoTime();
      }

      long elapsedNs = Math.max(1, endNs - startNs);
      times[i] = elapsedNs;
    }

    // System.err.printf("Times are: %s\n", Arrays.toString(times));

    // Report the median time as the clock tick interval.
    Arrays.sort(times);
    double ret = (double) times[TICK_SAMP / 2];

    System.err.printf("Clock tick interval is %1.0f nanoseconds\n", ret);

    return ret;
  }

}
