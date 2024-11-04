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
import java.util.TreeSet;

import com.ibm.avatar.algebra.consolidate.ConsolidateImpl;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Parse tree node for the "consolidate on" clause of a select/extract statement.
 */
public class ConsolidateClauseNode extends AbstractAQLParseTreeNode implements NodeWithRefInfo {

  /**
   * Column from which the set of spans that serve as the input to consolidation comes from.
   */
  RValueNode target;

  /** What type of consolidation to use. */
  StringNode type;

  /**
   * Column name in the (optional) "with priority from" clause of the consolidate statement
   */
  RValueNode priorityTarget;

  /** String that tells how to order different values of the priority target. */
  StringNode priorityDirection;

  /**
   * Main constructor.
   * 
   * @param target What column of the tuple to consolidate on.
   * @param type What type of consolidation to use.
   * @param priorityTarget column name in the (optional) "with priority from" clause of the
   *        consolidate statement
   * @param priorityDirection String that tells how to order different values of the priority
   *        target.
   */
  public ConsolidateClauseNode(RValueNode target, StringNode type, RValueNode priorityTarget,
      StringNode priorityDirection, String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    if (null == target) {
      throw new FatalInternalError("Null ptr passed for target argument of ConsolidateClauseNode");
    }
    if (null != priorityDirection && null == priorityTarget) {
      throw new FatalInternalError(
          "ConsolidateClauseNode constructor received null pointer for "
              + "priorityTarget argument but value %s for priorityDirection argument",
          priorityDirection);
    }

    this.target = target;
    this.type = type;
    if (priorityTarget == null) {
      priorityDirection = null;
    }
    this.priorityTarget = priorityTarget;
    this.priorityDirection = priorityDirection;

  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    if (null != getTypeNode()) {
      String typeName = getTypeStr();
      // Validate the consolidation type string by constructing (and immediately throwing away) a
      // Consolidate operator's
      // internal implementation. We do not have the actual ScalarFunc to pass to makeImpl. To still
      // use makeImpl for
      // validation, we will use the value of null for priorityDirection to denote that no priority
      // is used (
      // priorityTarget is null). We will perform additional validation below to make sure the
      // priority target is only
      // used with LeftToRight consolidation.
      String priorityString;
      if (priorityTarget == null)
        priorityString = null;
      else
        priorityString = getPriorityDirectionStr();
      try {
        ConsolidateImpl.makeImpl(typeName, null, null, priorityString);
      } catch (IllegalArgumentException e) {

        /*
         * throw AQLParserBase.makeException(consolidateClause .getTypeNode().getOrigTok(),
         * "Invalid consolidation type '%s'", typeName);
         */
        errors.add(AQLParserBase.makeException(getTypeNode().getOrigTok(), e.getMessage()));
      }
    }
    // The target can be a scalar function. Validate it!
    if (target != null) {
      List<ParseException> targetErrors = target.validate(catalog);
      if (null != targetErrors && targetErrors.size() > 0)
        errors.addAll(target.validate(catalog));
    }

    // The priority target can be a scalar function. Validate it!
    if (priorityTarget != null) {
      List<ParseException> targetErrors = priorityTarget.validate(catalog);
      if (null != targetErrors && targetErrors.size() > 0)
        errors.addAll(priorityTarget.validate(catalog));
    }

    return errors;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {

    target.dump(stream, 0);

    stream.printf(" using '%s'", getTypeStr());

    if (priorityTarget != null) {
      stream.printf("\n");
      printIndent(stream, indent);
      stream.printf(" with priority from ");
      priorityTarget.dump(stream, 0);
      stream.printf(" using '%s'", getPriorityDirectionStr());
    }

  }

  public RValueNode getTarget() {
    return target;
  }

  public StringNode getTypeNode() {
    return type;
  }

  public RValueNode getPriorityTarget() {
    return priorityTarget;
  }

  public StringNode getPriorityDirection() {
    return priorityDirection;
  }

  public String getTypeStr() {
    if (null != type) {
      return type.getStr();
    } else {
      return ConsolidateImpl.DEFAULT_CONSOLIDATION_TYPE;
    }
  }

  public String getPriorityDirectionStr() {
    if (null != priorityDirection) {
      return priorityDirection.getStr();
    } else {
      return null;
    }
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    ConsolidateClauseNode other = (ConsolidateClauseNode) o;
    int val;

    val = target.compareTo(other.target);
    if (val != 0) {
      return val;
    }

    val = getTypeStr().compareTo(other.getTypeStr());
    if (val != 0) {
      return val;
    }
    if (priorityTarget != null && other.priorityTarget != null) {
      val = priorityTarget.compareTo(other.priorityTarget);
      if (val != 0) {
        return val;
      }
    }

    if (getPriorityDirectionStr() != null && other.getPriorityDirectionStr() != null) {
      val = getPriorityDirectionStr().compareTo(other.getPriorityDirectionStr());
    }
    return val;
  }

  /**
   * Replace the parse tree node for the target of the consolidate statement with a rewritten
   * version. Called during the pre-processing phase when the preprocessor runs this tree of
   * function calls through the normal function call rewrite.
   * 
   * @param newTarget replacement for the current target node
   */
  public void replaceTarget(ScalarFnCallNode newTarget) {
    this.target = newTarget;
  }

  /**
   * Replace the parse tree node for the priorityTarget of the consolidate statement with a
   * rewritten version. Called during the pre-processing phase when the preprocessor runs this tree
   * of function calls through the normal function call rewrite.
   * 
   * @param newPriorityTarget replacement for the current priority target node
   */
  public void replacePriorityTarget(ScalarFnCallNode newPriorityTarget) {
    this.priorityTarget = newPriorityTarget;
  }

  @Override
  public void getReferencedCols(TreeSet<String> accum, Catalog catalog) throws ParseException {
    target.getReferencedCols(accum, catalog);
    if (null != priorityTarget) {
      priorityTarget.getReferencedCols(accum, catalog);
    }
  }

  @Override
  public void getReferencedViews(TreeSet<String> accum, Catalog catalog) throws ParseException {
    target.getReferencedViews(accum, catalog);
    if (null != priorityTarget) {
      priorityTarget.getReferencedViews(accum, catalog);
    }
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    if (target != null)
      target.qualifyReferences(catalog);
    if (priorityTarget != null)
      priorityTarget.qualifyReferences(catalog);
  }
}
