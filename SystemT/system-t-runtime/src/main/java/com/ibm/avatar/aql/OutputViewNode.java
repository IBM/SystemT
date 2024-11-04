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

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.ibm.avatar.api.Constants;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.CatalogEntry;
import com.ibm.avatar.aql.catalog.DetagCatalogEntry;
import com.ibm.avatar.aql.catalog.ExternalViewCatalogEntry;
import com.ibm.avatar.aql.catalog.TableCatalogEntry;
import com.ibm.avatar.aql.catalog.ViewCatalogEntry;
import com.ibm.avatar.aql.compiler.ParseToCatalog;

/**
 * Top-level parse tree node to represent
 * 
 * <pre>
 * output view
 * </pre>
 * 
 * statement.
 */
public class OutputViewNode extends TopLevelParseTreeNode {

  /**
   * Module Prefix node, when the output view statement is referring to a view from different module
   */
  private NickNode modulePrefixNickNode;

  /** View name without qualification. Consumed by Eclipse Tooling Indexer */
  private NickNode unqualifiedViewNameNickNode;

  /** Name of the view referred in output statement */
  private NickNode qualifiedViewname;

  /** Optional alternate name under which to output the view. */
  private final StringNode altname;

  public OutputViewNode(NickNode viewname, StringNode altname, String containingFileName,
      Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.qualifiedViewname = viewname;
    this.altname = altname;
  }

  public final NickNode getViewname() {
    return qualifiedViewname;
  }

  public final String getAltnameStr() {
    if (null == altname) {
      return null;
    } else {
      return altname.str;
    }
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("output view ");
    qualifiedViewname.dump(stream, 0);
    stream.print(";\n");
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    OutputViewNode outViewNode = (OutputViewNode) o;
    return this.qualifiedViewname.compareTo(outViewNode.qualifiedViewname);
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    String viewName = getViewname().getNickname();

    if (viewName.equals(Constants.DEFAULT_DOC_TYPE_NAME)) {
      errors.add(makeException("Outputting the built-in view " + viewName
          + " is not directly supported. You can achieve the same effect "
          + "by defining a view on top of the Document view and outputting that second view instead.",
          getOrigTok()));
    }

    if (false == catalog.isValidViewReference(viewName)
        && false == catalog.isValidTableReference(viewName)) {
      errors.add(AQLParserBase.makeException(getViewname().getOrigTok(),
          "View name '%s' is not a valid reference. Ensure that the View or Table is defined and is visible in the current module, accessible by the given name.",
          viewName));
    }

    // Restriction introduced to fix defect# 26281(Comment#6) and 29011- remove this, once we have a
    // better solutions
    if (null != altname) {
      String aliasName = altname.getStr();
      Token errLoc = altname.getOrigTok();

      if (true == catalog.isExportedView(aliasName)) {
        errors.add(AQLParserBase.makeException(altname.getOrigTok(),
            "The output alias '%s' is identical to the name of an exported view. An output alias name cannot coincide with the qualified name of an exported view.",
            aliasName, errLoc));
      } else if (true == catalog.isExternalViewExternalName(altname.getStr())) {
        errors.add(AQLParserBase.makeException(altname.getOrigTok(),
            "The output alias '%s' is identical to the name of an external view's external name. An output alias name cannot coincide with an external view's external name.",
            aliasName, errLoc));
      } else
        try {
          if (true == catalog.isOutputView(aliasName, null)) {
            errors.add(AQLParserBase.makeException(altname.getOrigTok(),
                "The output alias '%s' is identical to the name of a view, which is marked as an output view. Starting with v2.1, an output alias name cannot coincide with the qualified name of a view, which is marked as an output view.",
                aliasName, errLoc));
          }
        } catch (ParseException pe) {
          // Control reaching here implies, current module does not contain a view whose name
          // coincide with current
          // output alias name.
        }

    }

    return ParseToCatalog.makeWrapperException(errors, getContainingFileName());
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.Node#setState(com.ibm.avatar.aql.catalog.Catalog)
   */
  @Override
  public void setState(Catalog catalog) throws ParseException {
    // Do not set state for "output view Document" statment. The validate() method would throw an
    // exception for
    // "output view Document;" statement
    if (qualifiedViewname.getNickname().equals(Constants.DEFAULT_DOC_TYPE_NAME)) {
      return;
    }

    // Convert the view to an output view.
    CatalogEntry entry =
        catalog.lookupView(qualifiedViewname.getNickname(), qualifiedViewname.getOrigTok());

    if (entry instanceof ViewCatalogEntry) {
      ViewCatalogEntry ce = (ViewCatalogEntry) entry;
      if (ce.getIsOutput()) {
        throw makeException(
            String.format("View '%s' is already being output," + " as specified at%s",
                qualifiedViewname.getNickname(), ce.getWhereOutputSet().getLocStr()),
            getOrigTok());
      }

      ce.setIsOutput(new ErrorLocation(new File(getContainingFileName()), getOrigTok()), true,
          getAltnameStr());
    } else if (entry instanceof DetagCatalogEntry) {
      DetagCatalogEntry de = (DetagCatalogEntry) entry;
      DetagDocNode node = de.getParseTreeNode();

      // Note that this method will check for enabling the same output
      // twice.
      try {
        node.enableOutput(qualifiedViewname);
      } catch (ParseException e1) {
        // FIXME: Why overwrite the token with getOrigTok()?
        e1.currentToken = getOrigTok();
        throw e1;
      }
    } else if (entry instanceof ExternalViewCatalogEntry) {

      ExternalViewCatalogEntry ce = (ExternalViewCatalogEntry) entry;
      if (ce.getIsOutput()) {
        throw makeException(
            String.format("View '%s' is already being output," + " as specified at %s",
                qualifiedViewname.getNickname(), ce.getWhereOutputSet().getLocStr()),
            getOrigTok());
      }

      ce.setIsOutput(new ErrorLocation(new File(getContainingFileName()), getOrigTok()), true,
          getAltnameStr());
    } else if (entry instanceof TableCatalogEntry) {

      ParseException pe = new ParseException(String
          .format("Attempted to output table %s as a view. ", qualifiedViewname.getNickname()));

      // Set current token info, so the parser wraps the message
      // in an ExtendedParseException containing AQL file information.
      pe.currentToken = qualifiedViewname.getOrigTok();
      throw pe;

    } else if (null != entry) {

      ParseException pe = new ParseException(
          String.format("Unexpected type of Catalog entry for view %s. This should never happen.",
              qualifiedViewname.getNickname()));

      // Set current token info, so the parser wraps the message
      // in an ExtendedParseException containing AQL file information.
      pe.currentToken = qualifiedViewname.getOrigTok();
      throw pe;
    }
  }

  /**
   * Generate a ParseException with a token pointer and a message. We put this method here because
   * we can't add a new constructor to the generated class ParseException.
   */
  public static ParseException makeException(String msg, Token token) {
    ParseException ret = new ParseException(msg);
    ret.currentToken = token;
    return ret;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.Node#setContainingModuleName(java.lang.String)
   */
  @Override
  public void setModuleName(String containingModuleName) {
    super.setModuleName(containingModuleName);

    qualifiedViewname = new NickNode(prepareQualifiedName(qualifiedViewname.getNickname()),
        qualifiedViewname.getContainingFileName(), qualifiedViewname.getOrigTok());
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    qualifiedViewname =
        new NickNode(catalog.getQualifiedViewOrTableName(qualifiedViewname.getNickname()),
            qualifiedViewname.getContainingFileName(), qualifiedViewname.getOrigTok());
  }

  /**
   * Returns the module prefix nick node, when the output view references a view from another module
   * 
   * @return NickNode of module prefix
   */
  public NickNode getModulePrefixNickNode() {
    return modulePrefixNickNode;
  }

  /**
   * Sets the module prefix nick node, when the output view references a view from another module
   * 
   * @param modulePrefixNickNode
   */
  public void setModulePrefixNickNode(NickNode modulePrefixNickNode) {
    this.modulePrefixNickNode = modulePrefixNickNode;
  }

  /**
   * Returns the nick node of unqualified view name. When the output view refers to a view defined
   * in current module, then the value returned by this method is same as the one returned by
   * {@link #getViewname()}
   * 
   * @return Nick node of unqualified view name
   */
  public NickNode getUnqualifiedViewNameNickNode() {
    return unqualifiedViewNameNickNode;
  }

  /**
   * Sets the nick node of unqualified view name.
   * 
   * @param unqualifiedViewNameNickNode
   */
  public void setUnqualifiedViewNameNickNode(NickNode unqualifiedViewNameNickNode) {
    this.unqualifiedViewNameNickNode = unqualifiedViewNameNickNode;
  }
}
