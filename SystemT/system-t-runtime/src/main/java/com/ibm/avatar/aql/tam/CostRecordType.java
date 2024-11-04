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
package com.ibm.avatar.aql.tam;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.planner.CostRecord;

/**
 * JAXB class to represent {@link CostRecord} object
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CostRecordType", namespace = "http://www.ibm.com/aql")
public class CostRecordType implements Serializable {
  /**
   * 
   */
  private static final long serialVersionUID = 4663450378726255582L;
  @XmlAttribute
  protected double numJavaRegexes;
  @XmlAttribute
  protected double numSimpleRegexes;
  @XmlAttribute
  protected double numMultiRegexes;
  @XmlAttribute
  protected double numNLJoins;
  @XmlAttribute
  protected double numMergeJoins;
  @XmlAttribute
  protected double numHashJoins;

  /**
   * @return the numJavaRegexes
   */
  public double getNumJavaRegexes() {
    return numJavaRegexes;
  }

  /**
   * @param numJavaRegexes the numJavaRegexes to set
   */
  public void setNumJavaRegexes(double numJavaRegexes) {
    this.numJavaRegexes = numJavaRegexes;
  }

  /**
   * @return the numSimpleRegexes
   */
  public double getNumSimpleRegexes() {
    return numSimpleRegexes;
  }

  /**
   * @param numSimpleRegexes the numSimpleRegexes to set
   */
  public void setNumSimpleRegexes(double numSimpleRegexes) {
    this.numSimpleRegexes = numSimpleRegexes;
  }

  /**
   * @return the numMultiRegexes
   */
  public double getNumMultiRegexes() {
    return numMultiRegexes;
  }

  /**
   * @param numMultiRegexes the numMultiRegexes to set
   */
  public void setNumMultiRegexes(double numMultiRegexes) {
    this.numMultiRegexes = numMultiRegexes;
  }

  /**
   * @return the numNLJoins
   */
  public double getNumNLJoins() {
    return numNLJoins;
  }

  /**
   * @param numNLJoins the numNLJoins to set
   */
  public void setNumNLJoins(double numNLJoins) {
    this.numNLJoins = numNLJoins;
  }

  /**
   * @return the numMergeJoins
   */
  public double getNumMergeJoins() {
    return numMergeJoins;
  }

  /**
   * @param numMergeJoins the numMergeJoins to set
   */
  public void setNumMergeJoins(double numMergeJoins) {
    this.numMergeJoins = numMergeJoins;
  }

  /**
   * @return the numHashJoins
   */
  public double getNumHashJoins() {
    return numHashJoins;
  }

  /**
   * @param numHashJoins the numHashJoins to set
   */
  public void setNumHashJoins(double numHashJoins) {
    this.numHashJoins = numHashJoins;
  }

  /**
   * Returns true, if the contents of current object are same as the one passed in as parameter;
   * false, otherwise.
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (false == obj instanceof CostRecordType)
      return false;

    CostRecordType other = (CostRecordType) obj;

    // use null for <code>moduleName</code> in calls to ModuleMetadataMismatchException()
    // constructor below, as we do
    // not know the module name at this point. ModuleMetadataImpl.equals() would set the
    // <code>moduleName</code> before
    // re-throwing this exception to the consumers.

    // numHashJoins
    if (this.numHashJoins != other.numHashJoins)
      throw new ModuleMetadataMismatchException(null, "view.costRecord.numHashJoins",
          String.valueOf(this.numHashJoins), String.valueOf(other.numHashJoins));

    // numJavaRegexes
    if (this.numJavaRegexes != other.numJavaRegexes)
      throw new ModuleMetadataMismatchException(null, "view.costRecord.numJavaRegexes",
          String.valueOf(this.numJavaRegexes), String.valueOf(other.numJavaRegexes));

    // numMergeJoins
    if (this.numMergeJoins != other.numMergeJoins)
      throw new ModuleMetadataMismatchException(null, "view.costRecord.numMergeJoins",
          String.valueOf(this.numMergeJoins), String.valueOf(other.numMergeJoins));

    // numMultiRegexes
    if (this.numMultiRegexes != other.numMultiRegexes)
      throw new ModuleMetadataMismatchException(null, "view.costRecord.numMultiRegexes",
          String.valueOf(this.numMultiRegexes), String.valueOf(other.numMultiRegexes));

    // numNLJoins
    if (this.numNLJoins != other.numNLJoins)
      throw new ModuleMetadataMismatchException(null, "view.costRecord.numNLJoins",
          String.valueOf(this.numNLJoins), String.valueOf(other.numNLJoins));

    // numSimpleRegexes
    if (this.numSimpleRegexes != other.numSimpleRegexes)
      throw new ModuleMetadataMismatchException(null, "view.costRecord.numSimpleRegexes",
          String.valueOf(this.numSimpleRegexes), String.valueOf(other.numSimpleRegexes));

    // return true, if all tests pass
    return true;
  }

  @Override
  public int hashCode() {
    throw new FatalInternalError("Hashcode not implemented for class %s.",
        this.getClass().getSimpleName());
  }

  /**
   * Return String representation of the object.
   */
  @Override
  public String toString() {
    return String.format(
        "numHashJoins: %s, numMergeJoins: %s, numNLJoins: %s, numSimpleRegexes: %s, numJavaRegexes: %s, numMultiRegexes %s",
        numHashJoins, numMergeJoins, numNLJoins, numSimpleRegexes, numJavaRegexes, numMultiRegexes);
  }
}
