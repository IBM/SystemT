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
package com.ibm.avatar.aql.catalog;

import java.io.File;
import java.util.ArrayList;

import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.table.TableParams;
import com.ibm.avatar.api.exceptions.AmbiguousPathRefException;
import com.ibm.avatar.api.tam.TableMetadata;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.CreateTableNode;
import com.ibm.avatar.aql.ExtendedParseException;
import com.ibm.avatar.aql.ImportNode;
import com.ibm.avatar.aql.NickNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.Token;

/**
 * Catalog entry for a lookup table in AQL. Currently these tables are created with the "create
 * table" statement.
 */
public class TableCatalogEntry extends AbstractRelationCatalogEntry {

  /**
   * Parse tree node for the table declaration.
   */
  private CreateTableNode node = null;

  /** Schema of the table */
  private TupleSchema tableSchema = null;

  /** Constructor for the entry for a lookup table. */
  public TableCatalogEntry(CreateTableNode node) {
    super(node.getTableName());
    this.node = node;
  }

  /**
   * Original object holding all the different parameters for the table.
   */
  public TableParams params;

  public TableParams getParams() {
    return params;
  }

  /**
   * Location of the create table statement or the first reference to an external table file.
   */
  private Token origTok;

  /**
   * Name of the file in which the table was first created or referred to.
   */
  private String origFileName;

  /**
   * Constructor to create catalog entry for imported tables; created entry is marked imported.
   * 
   * @param name fully qualified name of the imported table
   * @param tmd meta data of the imported table
   * @param importingNode the import table node
   */
  protected TableCatalogEntry(String name, TableMetadata tmd, ImportNode importingNode) {
    super(name);
    this.tableSchema = tmd.getTableSchema();
    setImportingNode(importingNode);
  }

  /**
   * Override this method to return column names for external types.
   * 
   * @return names of output columns in this view
   */
  @Override
  public ArrayList<String> getColNames() {
    ArrayList<String> ret = new ArrayList<String>();
    // There won't be any parse tree node for entries created for imported tables
    if (null != node) {
      for (NickNode nick : node.getColNames()) {
        ret.add(nick.getNickname());
      }
      return ret;
    }
    // if here, entry in hand is for imported table
    if (null != tableSchema) {
      String[] fieldNames = tableSchema.getFieldNames();
      for (int i = 0; i < fieldNames.length; i++) {
        ret.add(fieldNames[i]);
      }
    }
    return ret;
  }

  @Override
  public boolean getIsExternal() {
    return false;
  }

  @Override
  public boolean getIsDetag() {
    return false;
  }

  @Override
  public boolean getIsView() {
    return false;
  }

  public void setTableSchema(TupleSchema tableSchema) {
    this.tableSchema = tableSchema;
  }

  @Override
  public TupleSchema getSchema() {
    return tableSchema;
  }

  @Override
  public CreateTableNode getParseTreeNode() {
    return node;
  }

  @Override
  protected AQLParseTreeNode getNode() {
    return node;
  }

  public Token getOrigTok() {
    return origTok;
  }

  public String getOrigFileName() {
    return origFileName;
  }

  /** Catalog entry for an inline table. */
  public static class Inline extends TableCatalogEntry {

    public Inline(CreateTableNode.Inline node) {
      super(node);
    }

  }

  /** Catalog entry for a table file on disk. */
  public static class OnDisk extends TableCatalogEntry {

    private File tableFile;

    public File getTableFile() {
      return tableFile;
    }

    public String getTableURI() {
      return tableFile.toString();
    }

    /**
     * @param origTok location in AQL file where this file was first referenced
     * @param origFileName file in which this table was first referenced
     * @param dictPath path for finding table files on disk
     * @throws ParseException if the indicated file does not exist in the table path
     */
    public OnDisk(CreateTableNode node, SearchPath tablePath) throws ParseException {
      super(node);

      // Try to find the table file.
      String fileName = node.getTableFileName();

      params = new TableParams();
      params.setTableName(node.getTableName());
      params.setFileName(node.getTableFileName());

      try {
        tableFile = getTableFileFromDisk(fileName, tablePath);
      } catch (AmbiguousPathRefException e) {
        throw makeException(getOrigTok(), getOrigFileName(), e.getMessage());
      }

      if (null == tableFile) {
        throw makeException(getOrigTok(), getOrigFileName(),
            "Table file '%s' (for table '%s') not found " + "in table path '%s'", fileName,
            node.getTableName(), tablePath);
      }

    }

    /**
     * Method to fetch the table file from the disk
     * 
     * @param tableFileName
     * @param tablePath
     * @return
     * @throws AmbiguousPathRefException
     */
    private File getTableFileFromDisk(String tableFileName, SearchPath tablePath)
        throws AmbiguousPathRefException {
      ArrayList<File> multipleFilesOnDisk = tablePath.resolveMulti(tableFileName, true);
      if (null != multipleFilesOnDisk && multipleFilesOnDisk.size() == 1)
        return multipleFilesOnDisk.get(0);

      return null;
    }

  }

  /**
   * We create our own wrapper for building exceptions, since this code will not always be called
   * from within the parser.
   * 
   * @param errorTok token indicating the location of the error
   * @param errorFileName name of the file where the error occurred, or null if no file is involved
   * @param format format string
   * @param args arguments to format string
   * @return exception that will properly print out the location of the error
   */
  public static ParseException makeException(Token errorTok, String errorFileName, String format,
      Object... args) {

    ParseException pe = new ParseException(String.format(format, args));
    pe.currentToken = errorTok;

    File errorFile = (null == errorFileName) ? null : FileUtils.createValidatedFile(errorFileName);

    return new ExtendedParseException(pe, errorFile);
  }

  /**
   * We create our own wrapper for building exceptions, since this code will not always be called
   * from within the parser.
   * 
   * @param errorTok token indicating the location of the error
   * @param errorFileName name of the file where the error occurred, or null if no file is involved
   * @param format format string
   * @param args arguments to format string
   * @return exception that will properly print out the location of the error
   */
  public static ParseException makeException(Token errorTok, String errorFileName, String message) {

    ParseException pe = new ParseException(message);
    pe.currentToken = errorTok;

    File errorFile = (null == errorFileName) ? null : FileUtils.createValidatedFile(errorFileName);

    return new ExtendedParseException(pe, errorFile);

  }

}
