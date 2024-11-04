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

import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.api.exceptions.FatalInternalError;

/**
 * Special accessor for setting text fields. Takes in a string and performs the additional
 * operations needed to create the auxiliary data structures behind a Text field.
 */
public class TextSetter extends FieldSetter<String> {
  /**
   * If the target schema is named (i.e. the schema of a view, not of an intermediate operator), a
   * pair of (view name, column name) identifying the target column/view that this accessor sets.
   * Null otherwise. This variable is used to set {@link Text#viewAndColumnName} in Text objects
   * that this accessor puts into place.
   */
  private Pair<String, String> viewAndColumnName = null;

  /**
   * Constructor to create a setter for a Text field.
   * 
   * @param schema schema of tuples that this object will be used to set the fields of
   * @param colix index of the Text field to set; this is an index into the SCHEMA, and not
   *        necessarily a physical index of the actual run-time tuple
   */
  protected TextSetter(TupleSchema schema, int colix) {
    super(schema, colix);

    new FieldGetter<Text>(schema, colix);

    // If the target schema is a view schema, put the view and column name in place.
    if (schema.getHasName()) {
      viewAndColumnName =
          new Pair<String, String>(schema.getName(), schema.getFieldNameByIx(colix));
    }
  }

  /**
   * Tell this accessor to use a particular view and column name, even though the target schema is
   * not named.
   * <p>
   * This method is used by ApplyScalarFunc to replace the encoded view/column name with the real
   * AQL view and column name, even though the output schema for an ApplyScalarFunc operator is a
   * temporary intermediate schema.
   * 
   * @param val value to use for the view and column name that is attached to each Text object set
   *        up by this accessor.
   */
  public void setViewAndColumnName(Pair<String, String> val) {
    if (null != viewAndColumnName) {
      // We shouldn't be calling this method if the target schema is a top-level view schema.
      throw new FatalInternalError(
          "setViewAndColumnName() called when view and column name are already set.  Old value: %s.  New value: %s",
          viewAndColumnName, val);
    }

    viewAndColumnName = val;

  }

  /**
   * Entry point for compatibility with {@link FieldSetter}. Does not set the language of the text,
   * which is assumed to be the default language (en).
   * 
   * @param tup tuple to set the text attribute of
   * @param str string to turn into a Text object
   */
  @Override
  public void setVal(Tuple tup, String str) {
    setVal(tup, str, null);
  }

  /**
   * Sets a text value with an explicitly specified language.
   * 
   * @param tup tuple to set the text attribute of
   * @param str string to turn into a Text object
   * @param language language of the text in the string
   * @return actual Text object created
   */
  public Text setVal(Tuple tup, String str, LangCode language) {
    if (null == str) {
      // SPECIAL CASE: Null string --> null text field
      tup.fields[colix] = null;
      return null;
      // END SPECIAL CASE
    }
    Text t = new Text(str, language);
    t.setViewAndColumnName(viewAndColumnName);

    tup.fields[colix] = t;

    return t;
  }

  /**
   * Special method for setting up a derived Text; puts in place all the necessary metadata for
   * translating offsets back to the original Text. <br/>
   * <br/>
   * Use this method when you want to construct Text objects for which you want to recover the
   * original text, for example, using the AQL <code>Remap()</code> builtin function.
   * 
   * @return actual Text object created
   */
  public Text setVal(Tuple tup, String str, Text source, int[] offsetsTable) {
    if (null == str) {
      // SPECIAL CASE: Null string --> null text field
      tup.fields[colix] = null;
      return null;
      // END SPECIAL CASE
    }
    Text t = new Text(str, source, offsetsTable);
    t.setViewAndColumnName(viewAndColumnName);

    tup.fields[colix] = t;
    return t;
  }

  public Text setVal(Tuple tup, Text text) {
    tup.fields[colix] = text;
    return text;
  }

  public Pair<String, String> getViewAndColumnName() {
    return this.viewAndColumnName;
  }

}
