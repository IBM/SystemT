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

import java.util.TreeSet;

import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Interface that encompasses functions available in certain parse tree nodes that compute
 * information about referenced views and columns.
 */
public interface NodeWithRefInfo {
  /**
   * @param accum set for accumulating the names of columns that this node depends on
   * @param catalog pointer to the AQL catalog, for looking up function information
   * @throws ParseException if a syntax error is found
   */
  public void getReferencedCols(TreeSet<String> accum, Catalog catalog) throws ParseException;

  /**
   * @param accum set for accumulating the names of views that this node depends on via record
   *        locator arguments.
   * @param catalog pointer to the AQL catalog, for looking up function information
   * @throws ParseException if a syntax error is found while generating the set of views.
   */
  public void getReferencedViews(TreeSet<String> accum, Catalog catalog) throws ParseException;
}
