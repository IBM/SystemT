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
package com.ibm.avatar.provenance;

import java.util.ArrayList;

/**
 * A low level change, which is returned from a ChangeGenerator
 */
public class LowLevelChange implements Comparable<Object> {

  // list of tuples that this change helps to add/remove from the view that it is applied (not
  // necessarily the output
  // view).
  private ArrayList<Integer> removedLocalNeg;
  // list of tuples that shouldn't have been added/removed from the view that it is applied
  private ArrayList<Integer> removedLocalPos;
  // the purpose of this change: remove or add tuple
  boolean isRemove;
  // this is for human reading
  private String changeString;

  private static int count = 0;

  private final int id;
  private int hlc; // id of the corresponding HLC

  // a penalty value in the scale of 0-1.
  private double penalty = 0;

  // performance gain
  private double gain;
  private final double fWeight;

  public LowLevelChange(ArrayList<Integer> removedNegatives, ArrayList<Integer> casualities,
      boolean isRemove, String changeString, double gain, double penalty, double fWeight) {
    this.removedLocalNeg = removedNegatives;
    this.removedLocalPos = casualities;
    this.isRemove = isRemove;
    this.changeString = changeString;
    this.gain = gain;
    this.penalty = penalty;
    this.fWeight = fWeight;
    id = count;
    count++;
  }

  // compare based on gain
  @Override
  public int compareTo(Object llc) {
    double score1, score2;
    score1 = getGain() * fWeight + getPenalty() * (1 - fWeight);
    score2 = ((LowLevelChange) llc).getGain() * fWeight
        + ((LowLevelChange) llc).getPenalty() * (1 - fWeight);

    double result = score1 - score2;

    int status = 2;
    if (result > 0.000001)
      status = 1;
    if (Math.abs(result) <= 0.000001)
      status = 0;
    if (result < -0.000001)
      status = -1;
    return status;
  }

  @Override
  public String toString() {
    String result = "LLC ID = " + id + ": " + changeString + "\n";

    result += "Removed local negative tuples: ";

    if (removedLocalNeg != null) {
      if (AQLRefine.isDEBUG())
        for (int i : removedLocalNeg) {
          result += i + " ";
        }
      else
        result += removedLocalNeg.size() + " tuples.";
    }

    result += "\n Removed local positive tuples: ";

    if (removedLocalPos != null) {
      if (AQLRefine.isDEBUG())
        for (int i : removedLocalPos) {
          result += i + " ";
        }
      else
        result += removedLocalPos.size() + " tuples.";
    }
    result += "\n Gain in Fscore from training: " + gain + "\n\n";

    return result;
  }

  public String toPrettyString() {
    return String.format("%s\nImprovement in F1-measure: %.2f%%", changeString, gain * 100);
  }

  public ArrayList<Integer> getRemovedLocalNeg() {
    return removedLocalNeg;
  }

  public void setRemovedLocalNeg(ArrayList<Integer> removedLocalneg) {
    this.removedLocalNeg = removedLocalneg;
  }

  public ArrayList<Integer> getRemovedLocalPos() {
    return removedLocalPos;
  }

  public void setRemovedLocalPos(ArrayList<Integer> casualities) {
    this.removedLocalPos = casualities;
  }

  public String getChangeString() {
    return changeString;
  }

  public void setChangeString(String changeString) {
    this.changeString = changeString;
  }

  public double getGain() {
    return gain;
  }

  public void setGain(double gain) {
    this.gain = gain;
  }

  public int getId() {
    return id;
  }

  public int getHlc() {
    return hlc;
  }

  public void setHlc(int hlc) {
    this.hlc = hlc;
  }

  public double getPenalty() {
    return penalty;
  }

  public void setPenalty(double penalty) {
    this.penalty = penalty;
  }
}
