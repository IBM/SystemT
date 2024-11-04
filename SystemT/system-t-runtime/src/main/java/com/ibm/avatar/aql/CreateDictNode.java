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

import com.ibm.avatar.algebra.util.dict.DictParams;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.compiler.ParseToCatalog;
import com.ibm.avatar.aql.doc.AQLDocComment;
import com.ibm.avatar.aql.tam.ModuleUtils;

/**
 * Top-level parse tree node for a
 * 
 * <pre>
 * create dictionary
 * </pre>
 * 
 * statement. Each of the three types of dictionary node are defined in inner classes.
 */
public abstract class CreateDictNode extends TopLevelParseTreeNode {

  private static final int INLINE_ID = 1;
  private static final int FROMTABLE_ID = 1;
  private static final int FROMFILE_ID = 1;

  /** Dictionary matching settings for this dictionary. */
  protected DictParams params;

  /** AQL Doc comment attached to this statement. */
  private AQLDocComment comment;

  /**
   * NickNode for the dictionary name token. Used by F3 and refactoring features of Eclipse tooling
   * to identify the location of dictionary name token
   */
  private NickNode dictNameNode;

  public CreateDictNode(String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);
  }

  public void setParams(DictParams params) {
    this.params = params;

    // set moduleName to dictParams only when it is not a generic module
    if (false == ModuleUtils.isGenericModule(moduleName)) {
      this.params.setModuleName(moduleName);
    }
  }

  @Override
  public void setModuleName(String moduleName) {
    super.setModuleName(moduleName);

    // cascade the module name to DictParams
    if (this.params != null && (false == ModuleUtils.isGenericModule(moduleName))) {
      this.params.setModuleName(moduleName);
    }
  }

  @Deprecated
  public String getOrigFileName() {
    return this.getContainingFileName();
  }

  /**
   * Returns fully qualified name of the dictionary
   * 
   * @return fully qualified name of the dictionary
   */
  public String getDictname() {
    if (null == params) {
      throw new RuntimeException(String.format("%s has no parameters", this));
    }

    return params.getDictName();
  }

  /**
   * Return the original dictionary name (unqualified by the module name) as it appears in the
   * create dictionary statement.
   * 
   * @return unqualified name
   */
  public String getUnqualifiedName() {
    return params.getUnqualifiedName();
  }

  public DictParams getParams() {
    return params;
  }

  /**
   * @return the NickNode of the token representing dictionary name
   */
  public NickNode getDictNameNode() {
    return dictNameNode;
  }

  /**
   * Sets the NickNode of the token representing dictionary name
   * 
   * @param dictNameNode dictionary name token's NickNode
   */
  public void setDictNameNode(NickNode dictNameNode) {
    this.dictNameNode = dictNameNode;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    try {
      params.validate();
    } catch (RuntimeException e) {
      errors.add(AQLParserBase.makeException(e.getMessage(), getOrigTok()));
    }

    return ParseToCatalog.makeWrapperException(errors, getContainingFileName());
  }

  @Override
  public int reallyCompareTo(AQLParseTreeNode o) {
    CreateDictNode other = (CreateDictNode) o;

    int val = declTypeID() - other.declTypeID();
    if (0 != val) {
      return val;
    }

    val = compareToInternal(other);
    if (0 != val) {
      return val;
    }

    return params.compareTo(other.params);
  }

  /**
   * Set the AQL Doc comment attached to this statement.
   * 
   * @param comment the AQL doc comment attached to this statement
   */
  public void setComment(AQLDocComment comment) {
    this.comment = comment;
  }

  /**
   * Get the AQL Doc comment attached to this node.
   * 
   * @return the AQL Doc comment attached to this node; null if this node does not have an AQL Doc
   *         comment
   */
  public AQLDocComment getComment() {
    return comment;
  }

  /*
   * ABSTRACT METHODS TO BE IMPLEMENTED BY INNER CLASSES
   */

  /**
   * Print out the AQL that would generate this parse tree node.
   */
  @Override
  public abstract void dump(PrintWriter stream, int indent);

  /**
   * Serialize dictionary declaration for all three type of dictionary to given stream.Override this
   * method if there is a different AOG representation for a dictionary type.
   * 
   * @param stream where to dump the AOG
   * @param indent how far to indent the lines we print out
   * @throws ParseException
   */
  public void toAOG(PrintWriter stream, int indent) throws ParseException {
    // Serialize dictionary definition
    printIndent(stream, indent);
    stream.print("CreateDict(\n");

    // Dump the dictionary parameters as the body of the AOG CreateDict() stmt
    params.toKeyValuePairs().toPerlHash(stream, indent + 1, false);

    printIndent(stream, indent);
    stream.print(");\n");
    stream.print("\n");
  }

  /**
   * Internal method for comparing with other create dict nodes of the same type.
   * 
   * @param o what to compare against
   * @return a consistent ordering
   */
  protected abstract int compareToInternal(CreateDictNode o);

  /** Assign an integer to each type of dictionary declaration. */
  protected abstract int declTypeID();

  /*
   * INNER CLASSES
   */

  /** Parse tree node to represent Inline dictionary and External dictionary definition. */
  public static class Inline extends CreateDictNode {
    public Inline(String containingFileName, Token origTok) {
      // set error location info
      super(containingFileName, origTok);
    }

    /** Entries (words or phrases) in the dictionary */
    protected List<String> entries = null;

    public void setEntries(List<String> list) {
      this.entries = list;
    }

    /**
     * This method return all the dictionary entries.
     * 
     * @return all the dictionary entries.
     */
    public List<String> getEntries() {
      return entries;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void dump(PrintWriter stream, int indent) {
      printIndent(stream, indent);

      if (params.getIsExternal()) {
        stream.printf("create external dictionary %s\n",
            StringUtils.quoteStr('"', getUnqualifiedName()));

        // AQL parser enforces that either allow_empty or required is not null.
        if (params.isAllowEmpty() != null) {
          stream.printf("allow_empty %s\n", String.valueOf(params.isAllowEmpty()));
        } else {
          stream.printf("required %s\n", String.valueOf(params.isRequired()));
        }
        params.dumpAQL(stream, indent);
        stream.print(";\n");
      } else {
        stream.printf("create dictionary %s\n", StringUtils.quoteStr('"', getUnqualifiedName()));

        params.dumpAQL(stream, indent);

        stream.print("\n");
        printIndent(stream, indent);
        stream.print("as (\n");
        for (int i = 0; i < entries.size(); i++) {
          printIndent(stream, indent + 1);
          stream.printf("'%s'", StringUtils.escapeForAOG(entries.get(i)));
          if (i == entries.size() - 1) {
            stream.print("\n");
          } else {
            stream.print(",\n");
          }
        }
        printIndent(stream, indent);
        stream.print(");\n");
      }
    }

    @Override
    protected int declTypeID() {
      return INLINE_ID;
    }

    @Override
    protected int compareToInternal(CreateDictNode o) {
      // The superclass should do all the type-checking for us.
      // Inline other = (Inline)o;

      // For now, just assume all inline dictionaries are the same.
      return 0;
    }
  }

  /** Dictionary created from a file. */
  public static class FromFile extends CreateDictNode {
    public FromFile(String containingFileName, Token origTok) {
      // set error location info
      super(containingFileName, origTok);
    }

    @Override
    public void dump(PrintWriter stream, int indent) {
      printIndent(stream, indent);
      stream.printf("create dictionary %s from file %s\n",
          StringUtils.quoteStr('"', getUnqualifiedName()),
          StringUtils.quoteStr('\'', params.getFileName()));

      params.dumpAQL(stream, indent);

      stream.print(";\n");
    }

    @Override
    protected int declTypeID() {
      return FROMFILE_ID;
    }

    @Override
    protected int compareToInternal(CreateDictNode o) {
      // The superclass should do all the type-checking for us.
      // Inline other = (Inline)o;

      // For now, just assume all table-based dictionaries are the same.
      return 0;
    }
  }

  /** Dictionary created from a table. */
  public static class FromTable extends CreateDictNode {

    public FromTable(String containingFileName, Token origTok) {
      // set error location info
      super(containingFileName, origTok);
    }

    @Override
    public void dump(PrintWriter stream, int indent) {
      printIndent(stream, indent);
      stream.printf("create dictionary %s from table %s\n",
          StringUtils.quoteStr('"', getUnqualifiedName()),
          StringUtils.quoteStr('"', params.getTabName()));

      params.dumpAQL(stream, indent);
      stream.print(";");
    }

    @Override
    protected int declTypeID() {
      return FROMTABLE_ID;
    }

    @Override
    protected int compareToInternal(CreateDictNode o) {
      // The superclass should do all the type-checking for us.
      // FromTable other = (FromTable)o;

      // For now, just assume all table-based dictionaries are the same.
      return 0;
    }

    /**
     * This method qualifies the source table name.
     */
    @Override
    public void qualifyReferences(Catalog catalog) {
      DictParams dictParams = getParams();
      String unqualifiedTabName = dictParams.getTabName();
      dictParams.setTabName(catalog.getQualifiedViewOrTableName(unqualifiedTabName));
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.Node#qualifyReferences(com.ibm.avatar.aql.catalog.Catalog)
   */
  @Override
  public void qualifyReferences(Catalog catalog) {
    // Dummy implementation. The FromTable subclass has some action to perform.

  }

  @SuppressWarnings("deprecation")
  /**
   * Tests to see if this parse tree node is using a deprecated flag, so we can throw a compiler
   * warning
   * 
   * @return true if using a deprecated flag, false if not
   */
  public boolean usesAllowEmpty() {
    if (params.getIsExternal() && params.isAllowEmpty() != null) {
      return true;
    } else
      return false;
  }
}
