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
package com.ibm.avatar.aql;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.api.exceptions.FatalInternalError;

/**
 * Object that holds information about a column of a relation that a sequence pattern passes through
 * to the output of the sequence pattern. Extend as necessary.
 * 
 */
public class PatternPassThroughColumn implements Comparable<PatternPassThroughColumn> {
  /**
   * Format string used in generating internal names for pass through columns in the sequence
   * pattern rewrite.
   */
  private static final String PASS_THROUGH_COL_NAME_FORMAT = "%s_%s";

  /**
   * Local (in the namespace of the extract pattern stmt) name for the view that contains the column
   */
  private String localViewName;

  /**
   * Name of the column within the source view
   */
  private String colName;

  /**
   * Type of the column, specifically the type of the column in the source relation that produces
   * values of the column.
   */
  private FieldType type;

  /**
   * Main constructor.
   * 
   * @param qualifiedColumnName Name of the column to pass through. If qualified is TRUE, this name
   *        is in the form viewAlias.attributeName.
   * @param type Type of the column, specifically the type of the column in the source relation
   * @param qualified true if the name is qualified, false if it is unqualified
   */
  public PatternPassThroughColumn(String columnName, FieldType type, boolean qualified) {
    // Split the reference into table and column, if applicable
    if (qualified) {
      final int pos = columnName.indexOf('.');
      if (pos < 0) {
        // Should never happen because the pattern validation code ensures all col refs in the
        // select list are
        // of the form <viewname>.<attrname>
        throw new FatalInternalError(
            "Don't understand column reference '%s'. The column reference must be of the form 'viewname.colname'.",
            columnName);
      }
      localViewName = columnName.substring(0, pos);
      colName = columnName.substring(pos + 1);
    } else {
      localViewName = null;
      colName = columnName;
    }
    this.type = type;
  }

  /**
   * @return Locally qualified name of the column, in the form viewAlias.attributeName, if the name
   *         is qualified; otherwise just the column name
   */
  public String getQualifiedName() {
    if (null != localViewName) {
      return localViewName + "." + colName;
    } else {
      return colName;
    }
  }

  /**
   * @return the local (to the extract pattern statement) alias for the view containing this column
   */
  public String getViewAlias() {
    return localViewName;
  }

  /**
   * @return name of the column within the context of the target view (i.e. unqualified name of the
   *         column)
   */
  public String getColName() {
    return colName;
  }

  /**
   * Construct an alias for a pass through column in the select list of an EXTRACT PATTERN
   * statement. Used during the pattern query rewrite for passing through the appropriate values
   * from the bottom to the top level of the pattern expression.
   * 
   * @return generated name for this column, to be used during the sequence pattern rewrite
   * @throws ParseException if the qualified name stored in this object cannot be parsed
   */
  public String getGeneratedName() throws ParseException {
    return String.format(PASS_THROUGH_COL_NAME_FORMAT, localViewName, colName);
  }

  /**
   * Override the current type information
   * 
   * @param type new type to record for the field being passed through by the extract pattern
   *        statement
   */
  public void setType(FieldType type) {
    this.type = type;
  }

  /**
   * @return Type of the column, specifically the type of the column in the source relation
   */
  public FieldType getType() {
    return type;
  }

  @Override
  public String toString() {
    return getQualifiedName() + " " + type.toString();
  }

  /**
   * equals() method so that we can put these objects into hash tables. Considers all objects with
   * the same column name to be the same, but checks to ensure that they have the same type.
   */
  @Override
  public boolean equals(Object obj) {
    if (null == obj || false == obj instanceof PatternPassThroughColumn) {
      return false;
    }

    PatternPassThroughColumn pobj = (PatternPassThroughColumn) obj;

    // localViewName can be false
    if (false == FieldType.comparePtrs(localViewName, pobj.localViewName)) {
      return false;
    }

    if (false == pobj.colName.equals(colName)) {
      return false;
    }

    if (false == pobj.type.equals(type)) {
      throw new FatalInternalError(
          "Detected comparison of incompatible field types on pass through column specs %s and %s",
          this, pobj);
    }

    return true;
  }

  /**
   * hashCode() method so that we can put these objects into hash tables.
   */
  @Override
  public int hashCode() {
    // Hash only on the name, not the type
    int accum = 0;
    if (null != localViewName) {
      accum += localViewName.hashCode();
    }
    accum += colName.hashCode();
    return accum;
  }

  /**
   * compareTo() method so that we can put these objects into tree maps/sets. Sorts by name and
   * disallows comparisons on just type.
   */
  @Override
  public int compareTo(PatternPassThroughColumn other) {
    if (null == other) {
      return -1;
    }

    // Sort on name first.
    int value = FieldType.compareComparablePtrs(localViewName, other.localViewName);
    if (0 != value) {
      return value;
    }

    value = colName.compareTo(other.colName);
    if (0 != value) {
      return value;
    }

    // There should only be one of these objects for a given type
    value = type.compareTo(other.type);
    if (0 != value) {
      throw new FatalInternalError(
          "Detected comparison of incompatible field types on passthrough column specs %s and %s",
          this, other);
    }

    return 0;
  }

}
