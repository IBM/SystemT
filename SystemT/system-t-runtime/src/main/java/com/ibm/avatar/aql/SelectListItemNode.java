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

import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Parse tree node for an entry in a query's SELECT list. Such an entry could be in any of the
 * following forms:
 * <ul>
 * <li>table.column as alias
 * <li>table.*
 * </ul>
 * This class implements "as alias" part and delegates the remaining part to subclasses of
 * RValueNode.
 */
public class SelectListItemNode extends AbstractAQLParseTreeNode implements NodeWithRefInfo {

  /** The original RValue */
  protected RValueNode value;

  protected String alias;

  /** Flag that is set to true if this item is in the table.* format. */
  protected boolean isDotStar = false;

  private final NickNode aliasNode;

  /**
   * Constructor for a list item in the form "[rvalue] as alias"
   * 
   * @param value the rvalue -- can be a function call or an expression like Table.col
   * @param aliasNode internal name, or null if one is not specified
   */
  public SelectListItemNode(RValueNode value, NickNode aliasNode) throws ParseException {
    // set error location info
    super(value.getContainingFileName(), value.getOrigTok());

    this.value = value;
    this.aliasNode = aliasNode;
    if (null != aliasNode)
      this.alias = aliasNode.getNickname();
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    List<ParseException> valueErrors = value.validate(catalog);
    if (null != valueErrors && valueErrors.size() > 0)
      errors.addAll(valueErrors);

    if (null == aliasNode) {
      // No alias provided; make sure the select list item is in the
      // form <table>.<col>
      // Log.debug("Type is %s", value.getClass().getName());
      if (value instanceof ColNameNode || value instanceof NickNode
          || value instanceof ScalarFnCallNode) {
        // Everything's ok, and SelectNode.inferAliases() will take care
        // of filling in this.alias
      } else {
        // This type of value requires a nickname;
        // {@link SelectListNode.inferAliases()} will take care of throwing the exception
        // fixes defect
      }
    } else {
      alias = aliasNode.getNickname();
    }

    return errors;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    ((AQLParseTreeNode) value).dump(stream, indent);

    if (isDotStar) {
      stream.printf(".* ");
    }

    if (null != alias) {
      // Fix for defect : From list example from AQL Reference gives provenance rewrite error when I
      // output view
      // it. The alias is a NickNodes. In the AQLParser, Nicknodes can be either composed
      // of a-z, A-Z, 0-9, _ characters, or they can be double quoted string literals. When parsing
      // the latter form of
      // NickNode, the AQLParser dequotes the string of the NickNode: that is, removes the leading
      // and trailing
      // double-quotes, and de-escapes any double quotes inside the string. So if we want to dump
      // back valid AQL, we
      // need to (re)quote, i.e., use NickNode.dump(), instead of dumping the tab name and column
      // name directly.
      // stream.printf (getColName ());

      stream.printf(" as ");

      if (null != aliasNode) {
        // Dump the original alias NickNode if there was one
        aliasNode.dump(stream, 0);
      } else {
        // Otherwise, maybe the alias was filled in later during alias inference. Dump the auto
        // generated string as a
        // valid NickLiteral
        NickNode.dumpStrNick(stream, alias);
      }
    }
  }

  public void setIsDotStar(boolean isDotStar) {
    this.isDotStar = isDotStar;
  }

  public boolean getIsDotStar() {
    return isDotStar;
  }

  /**
   * @return the raw parse tree node returned by the parser, as opposed to the massaged version that
   *         {@link #getValue()} returns.
   */
  public RValueNode getOrigValue() {
    return value;
  }

  /**
   * Change the value of this select list item; used during query rewrite.
   */
  public void replaceValue(RValueNode newVal) {
    value = newVal;
  }

  /**
   * @return the value of this select list item, converting any constant expressions to function
   *         calls
   */
  public RValueNode getValue() throws ParseException {
    if (isDotStar) {
      // SPECIAL CASE: DotStar --> return the nickname that comes before
      // the star
      return value;
      // END SPECIAL CASE
    } else if (value instanceof ColNameNode) {
      // Column names just get passed directly through, so we don't
      // generate lots of spurious GetCol() calls.
      return value;
    } else {
      // Everything else in a select list becomes a function call.

      ScalarFnCallNode asFunction = value.asFunction();

      // If function is a constant function then copy the autoColumnName from
      // Original value to the Constant function node
      if (asFunction instanceof ConstAOGFunctNode && value.getColName() != null) {
        asFunction.__setAutoColumnNameDirectly(value.getColName());
      }

      return asFunction;
    }
  }

  public String getAlias() {
    if (null == alias && null != aliasNode)
      alias = aliasNode.getNickname();
    return alias;
  }

  /**
   * This method only for the use of SelectListNode during select list cleanup.
   * 
   * @param alias new value for the "as [alias]" part of the select list item
   */
  protected void setAlias(String alias) {
    this.alias = alias;
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    SelectListItemNode other = (SelectListItemNode) o;
    if (alias == null && other.alias == null) {
      return 0;
    }
    if (alias == null)
      return -1;
    if (other.alias == null)
      return 1;
    int val = alias.compareTo(other.alias);
    if (val != 0) {
      return val;
    }

    val = Boolean.valueOf(isDotStar).compareTo(Boolean.valueOf(other.isDotStar));
    if (val != 0) {
      return val;
    }

    return value.compareTo(other.value);
  }

  @Override
  public void getReferencedCols(TreeSet<String> accum, Catalog catalog) throws ParseException {
    value.getReferencedCols(accum, catalog);
  }

  @Override
  public void getReferencedViews(TreeSet<String> accum, Catalog catalog) throws ParseException {
    value.getReferencedViews(accum, catalog);
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    value.qualifyReferences(catalog);

    // Sanity check: Make sure that qualifyReferences at least produced a table name.
    if (value instanceof ColNameNode) {
      ColNameNode cnn = (ColNameNode) value;
      String tabName = cnn.getTabname();
      if (null == tabName) {
        throw new FatalInternalError("ColNameNode.qualifyReferences() on %s produced "
            + "a ColNameNode object with no table name", cnn);
      }
    }
  }
}
