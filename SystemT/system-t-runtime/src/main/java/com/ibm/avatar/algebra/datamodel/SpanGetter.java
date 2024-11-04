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
 * Special accessor for getting Span fields. The input field can be either Text or Span. If you do
 * not want automatic conversion, use FieldGettter<Span> directly.
 * 
 */
public class SpanGetter extends FieldGetter<Span> {

  private final FieldGetter<?> getter;

  /**
   * Constructor to create a getter for a Text field.
   * 
   * @param schema schema of tuples that this object will be used to set the fields of
   * @param colix index of the input field to set; this is an index into the SCHEMA, and not
   *        necessarily a physical index of the actual run-time tuple
   */
  protected SpanGetter(AbstractTupleSchema schema, int colix) {
    super(schema, colix);
    getter = new FieldGetter<Object>(schema, colix);
  }

  /**
   * Entry point for compatibility with {@link FieldGetter}.
   * 
   * @param tup tuple to set the text attribute of
   * @param str string to turn into a Text object
   */
  @Override
  public Span getVal(Tuple tup) {
    Object val = getter.getVal(tup);
    Span text = Span.convert(val);
    return text;
  }

}
