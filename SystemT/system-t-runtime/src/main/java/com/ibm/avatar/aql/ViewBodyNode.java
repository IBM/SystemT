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
import com.ibm.avatar.aql.compiler.ParseToCatalog;

/**
 * Base class for parse tree nodes that can be the body of a CREATE VIEW statement.
 */
public abstract class ViewBodyNode extends AbstractAQLParseTreeNode {

  protected ViewBodyNode(String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);

  }

  /**
   * @param accum set for building up the names of all other views that this view depends on.
   * @param catalog AQL catalog, for looking up information that wasn't available at parse time
   * @throws ParseException if an error in the AQL is detected while finding dependencies
   */
  public abstract void getDeps(TreeSet<String> accum, Catalog catalog) throws ParseException;

  /**
   * Compare two objects a la compareTo(), but returning -1 if the first is null and the second is
   * non-null.
   */
  protected static <T> int compareAndCheckNull(Comparable<T> a, T b) {
    if (null == a) {
      if (null != b) {
        // Non-null sorts higher.
        return -1;
      } else {
        return 0;
      }
    } else {
      return a.compareTo(b);
    }
  }

  /**
   * This method will recursively expand wildcard(*) and infer missing aliases from select list.
   * 
   * @param catalog reference to the catalog, for recursively fetching the schemas of any views this
   *        view references.
   * @throws ParseException
   */
  public void expandWildcardAndInferAlias(Catalog catalog) throws ParseException {
    // BEGIN try block to add location information to any exceptions that are missing it
    try {

      if (this instanceof SelectNode) {
        SelectNode selectNode = (SelectNode) this;
        FromListNode fromList = selectNode.getFromList();
        for (int i = 0; i < fromList.size(); i++) {
          // There can be wildcards or missing aliases in sub queries also
          if (fromList.get(i) instanceof FromListItemSubqueryNode) {

            ViewBodyNode body = ((FromListItemSubqueryNode) fromList.get(i)).getBody();
            // Build schema for sub queries also
            body.expandWildcardAndInferAlias(catalog);
          }
        }

        // Expand any wildcards and inder missing aliases locally in the select list of this node.
        selectNode.expandWildcardAndInferAliasLocal(catalog);
      } else if (this instanceof ExtractNode) {
        ExtractNode extractNode = (ExtractNode) this;

        if (extractNode.getTarget() instanceof FromListItemSubqueryNode) {
          ViewBodyNode body = ((FromListItemSubqueryNode) extractNode.getTarget()).getBody();
          // Build schema for sub queries also
          body.expandWildcardAndInferAlias(catalog);
        }

        // Expand any wildcards and inder missing aliases locally in the select list of this node.
        extractNode.expandWildcardAndInferAliasLocal(catalog);
      } else if (this instanceof ExtractPatternNode) {
        ExtractPatternNode patternNode = (ExtractPatternNode) this;
        FromListNode fromList = patternNode.getFromList();

        for (int i = 0; i < fromList.size(); i++) {
          // There can be wildcards or missing aliases in sub queries also
          if (fromList.get(i) instanceof FromListItemSubqueryNode) {

            ViewBodyNode body = ((FromListItemSubqueryNode) fromList.get(i)).getBody();
            // Build schema for subqueries also
            body.expandWildcardAndInferAlias(catalog);
          }
        }

        // Expand any wildcards and inder missing aliases locally in the select list of this node.
        patternNode.expandWildcardAndInferAliasLocal(catalog);

      } else if (this instanceof UnionAllNode) {
        UnionAllNode unionNode = (UnionAllNode) this;
        for (int i = 0; i < unionNode.getNumStmts(); i++) {
          unionNode.getStmt(i).expandWildcardAndInferAlias(catalog);
        }
      } else if (this instanceof MinusNode) {
        MinusNode minusNode = (MinusNode) this;

        minusNode.getFirstStmt().expandWildcardAndInferAlias(catalog);
        minusNode.getSecondStmt().expandWildcardAndInferAlias(catalog);
      }

      // END try block to add location information
    } catch (ParseException e) {
      // If the preceding code threw an exception without any AQL location information, add the
      // location of the view as
      // a best guess.
      if (false == (e instanceof ExtendedParseException) && null == e.currentToken) {

        // Add location within the file by modifying the exception in place.

        e.currentToken = getOrigTok();

        // Add file information by wrapping the entire exception.
        ParseException wrappedException =
            ParseToCatalog.makeWrapperException(e, getContainingFileName());
        throw wrappedException;
      } else {
        // Exception already has location information; just rethrow it.
        throw e;
      }
    }
  }

}
