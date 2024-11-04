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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.ibm.avatar.aql.catalog.Catalog;

/** Parse tree node for the SELECT list in a select or select into statement. */
public class ExtractListNode extends AbstractAQLParseTreeNode {

  /** The first (n-1) items of an n-item extract list act as a select list. */
  private SelectListNode selectList;

  /** The last item defines what extraction to perform. */
  private ExtractionNode extraction;

  /**
   * Constructor for the normal case list.
   */
  public ExtractListNode(ArrayList<SelectListItemNode> items, ExtractionNode extraction,
      String containingFileName, Token origTok) {
    super(containingFileName, origTok);

    this.selectList = new SelectListNode(items, containingFileName, origTok);
    this.extraction = extraction;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    if (null != selectList) {
      List<ParseException> selectErrors = selectList.validate(catalog);
      if (null != selectErrors && selectErrors.size() > 0)
        errors.addAll(selectErrors);
    }
    if (null != extraction) {
      List<ParseException> extractionErrors = extraction.validate(catalog);
      if (null != extractionErrors && extractionErrors.size() > 0)
        errors.addAll(extractionErrors);

      // Make sure the select list does not contain any aggregates
      errors.addAll(selectList.ensureNoAggFunc(catalog));
    }
    return errors;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    // select list is optional. Check for null.
    if (selectList != null) {
      selectList.dump(stream, indent);
      if (selectList.size() > 0) {
        stream.print(",\n");
        printIndent(stream, indent);
      }
    }
    extraction.dump(stream, indent);
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    ExtractListNode other = (ExtractListNode) o;

    int val = selectList.compareTo(other.selectList);
    if (val != 0) {
      return val;
    }
    val = extraction.compareTo(other.extraction);
    return val;
  }

  public SelectListNode getSelectList() {
    return this.selectList;
  }

  public ExtractionNode getExtractSpec() {
    return this.extraction;
  }

  public void setExtractSpec(ExtractionNode extraction) {
    this.extraction = extraction;
  }

  /**
   * Expand wildcards and infer missing aliases in the select list of this node. Does not recurse to
   * subqueries, and assumes wild card expansion and alias inference for subqueries has been already
   * done. This method is called from {@link ViewBodyNode#expandWildcardAndInferAlias(Catalog)}
   * which recurses to subqueries prior to calling this method.
   * 
   * @param catalog
   * @throws ParseException
   */
  protected void expandWildcardAndInferAliasLocal(FromListNode fromList, Catalog catalog)
      throws ParseException {
    // Nothing to expand or infer
    if (null == selectList)
      return;

    // Replace the select list with another list created by expanding wildcards
    selectList = new SelectListNode(selectList.expandWildcards(fromList, catalog),
        selectList.getContainingFileName(), selectList.getOrigTok());

    // Infer any missing aliases in the select list
    selectList.inferAliases();
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    selectList.qualifyReferences(catalog);
    extraction.qualifyReferences(catalog);
  }

}
