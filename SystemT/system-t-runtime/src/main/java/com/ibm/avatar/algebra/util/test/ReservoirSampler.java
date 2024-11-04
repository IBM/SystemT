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

import java.util.ArrayList;
import java.util.Random;

/**
 * Class to perform reservoir sampling, using a reservoir of configurable type. Adapted from Data
 * Triage reservoir sampler.
 * 
 */
public class ReservoirSampler<T> {

  /**
   * The reservoir. Grows up to size {@link #capacity}; after that, elements are evicted at random
   * to make room for new ones.
   */
  private ArrayList<T> reservoir = new ArrayList<T>();;

  // private int slotsUsed = 0;

  private int capacity = -1;

  private int objsSeen = 0;

  private Random rng;

  /**
   * Primary constructor.
   * 
   * @param size the size of the reservoir
   * @param seed seed for the random number generator
   */
  public ReservoirSampler(int size, long seed) {
    capacity = size;
    rng = new Random(seed);
  }

  /**
   * Insert the next object into the reservoir sample. We use Alan Waterman's naive reservoir
   * sampling algorithm. There are several more efficient algorithms, but the work we would save by
   * implementing one of them is pretty insignificant. For further reading, see Jeffrey Scott
   * Vitter's paper "Random Sampling with a Reservoir" in ACM Trans. Mathematical Software
   */
  public void add(T next) {

    objsSeen++;

    // SPECIAL CASE: If the reservoir isn't empty, make sure we keep the
    // filled slots contiguous.
    if (reservoir.size() < capacity) {
      reservoir.add(next);
      return;
    }
    // END SPECIAL CASE

    if (reservoir.size() != capacity) {
      throw new RuntimeException("Reservoir size should grow to *exactly* capacity.");
    }

    // Start with a random number from 0 to 1
    double randnum = rng.nextDouble();

    /*
     * The algorithm works by maintaining the invariant that the reservoir is always a random sample
     * of the tuples we've seen so far.
     */

    /*
     * The probability that the current tuple will go into the reservoir is (reservoir size) /
     * (number of tuples seen, including this one)
     */
    double prob_use_tup = (double) capacity / (double) objsSeen;

    if (randnum <= prob_use_tup) {
      /*
       * Choose a victim and replace the victim.
       */
      int victim_ix = (int) (capacity * rng.nextDouble());

      reservoir.set(victim_ix, next);
    }
  }

  /**
   * @return the current sample. This is a *shallow* copy of the reservoir and will change on every
   *         call to {@link #add()} until the next call to {@link #reset()}.
   */
  public ArrayList<T> getReservoir() {
    return reservoir;
  }

  /**
   * @return the number of elements that the reservoir is capable of holding.
   */
  public int getCapacity() {
    return capacity;
  }

  /**
   * @return a deep copy of the current sample; the object returned will not be affected by
   *         subsequent calls.
   */
  @SuppressWarnings("unchecked")
  public ArrayList<T> getSample() {
    return (ArrayList<T>) reservoir.clone();
  }

  /**
   * Prepare the reservoir for reuse. Does not affect results returned by previous calls to
   * {@link #getReservoir()}.
   */
  public void reset() {

    reservoir = new ArrayList<T>();
    objsSeen = 0;
  }

}
