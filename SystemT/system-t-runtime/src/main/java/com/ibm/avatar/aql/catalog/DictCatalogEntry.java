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

import com.ibm.avatar.algebra.util.dict.DictParams;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.api.exceptions.AmbiguousPathRefException;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.CreateDictNode;
import com.ibm.avatar.aql.ExtendedParseException;
import com.ibm.avatar.aql.ImportNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.Token;

/**
 * Catalog entries for inline dictionaries and dictionaries based out of on-disk dictionary files.
 */
public class DictCatalogEntry extends CatalogEntry {

  /**
   * Location of the create dictionary statement or the first reference to an external dictionary
   * file.
   */
  private Token origTok;

  /**
   * Name of the file in which the dictionary was first created or referred to.
   */
  private String origFileName;

  /**
   * Original parse tree node for the dictionary creation.
   */
  private CreateDictNode parseTreeNode = null;

  /**
   * Original map holding all the different parameters for the dictionary.
   */
  private final DictParams params;

  public Token getOrigTok() {
    return origTok;
  }

  public String getOrigFileName() {
    return origFileName;
  }

  public DictParams getParams() {
    return params;
  }

  public CreateDictNode getParseTreeNode() {
    return parseTreeNode;
  }

  /**
   * For use by inner classes that need to generate their own fake "parse tree nodes"
   */
  protected void setParseTreeNode(CreateDictNode fakeNode) {
    parseTreeNode = fakeNode;
  }

  /**
   * Flag to indicate, if the dictionary depends on external artifacts; by default all dictionary
   * are independent.
   */
  private boolean externallyDependent = false;

  public boolean isExternallyDependent() {
    return externallyDependent;
  }

  /**
   * Method to mark a dictionary dependent on external artifacts.
   * 
   * @param externallyDependent
   */
  public void setExternallyDependent(boolean externallyDependent) {
    this.externallyDependent = externallyDependent;
  }

  /**
   * Constructor to create dummy catalog entry for imported dictionaries.
   * 
   * @param dictName fully qualified name of the imported dictionary
   * @importNode the import dictionary node
   */
  DictCatalogEntry(String dictName, ImportNode importNode) {
    super(dictName);

    params = new DictParams();
    params.setDictName(dictName);
    setImportingNode(importNode);
  }

  /** Constructor for use by inner classes. */
  protected DictCatalogEntry(CreateDictNode parseTreeNode) {
    super(parseTreeNode.getDictname());

    this.parseTreeNode = parseTreeNode;
    params = parseTreeNode.getParams();
    origTok = parseTreeNode.getOrigTok();
    origFileName = parseTreeNode.getContainingFileName();
  }

  /**
   * Constructor for implicit dictionary creation, where there is no parse tree node.
   */
  protected DictCatalogEntry(String name, Token origTok, String origFileName) {
    super(name);

    params = new DictParams();
    params.setDictName(name);
    params.setFileName(name);
    this.origTok = origTok;
    this.origFileName = origFileName;
  }

  /** Catalog entry for an inline dictionary. */
  public static class Inline extends DictCatalogEntry {

    public Inline(CreateDictNode.Inline node) {
      super(node);
    }

  }

  /** Catalog entry for a dictionary created from a table. */
  public static class FromTable extends DictCatalogEntry {

    /**
     * @param name name of the dictionary
     * @param tableName name of the table that provides entries for the dictionary
     * @param origTok location in AQL file where this dictionary was first referenced
     * @param origFileName file in which this dictionary was first referenced
     * @param catalog a pointer back to the catalog, for checking on the table
     * @throws ParseException if the indicated file does not exist in the dictionary path
     */
    public FromTable(CreateDictNode.FromTable node, Catalog catalog) throws ParseException {
      super(node);

      // validation moved to pre-processor

      // detection of external table dependency moved to pre-processor
    }

  }

  /** Catalog entry for a dictionary file on disk. */
  public static class OnDisk extends DictCatalogEntry {

    private File dictFile;

    public File getDictFile() {
      return dictFile;
    }

    /**
     * @param origTok location in AQL file where this file was first referenced
     * @param origFileName file in which this dictionary was first referenced
     * @param dictPath path for finding dictionary files on disk
     * @throws ParseException if the indicated file does not exist in the dictionary path
     */
    public OnDisk(CreateDictNode node, SearchPath dictPath) throws ParseException {
      super(node);

      // Try to find the dictionary file.
      String fileName = node.getParams().getFileName();

      // Fix of defect# 14246: dictionary file name resolves to first matching dictionary from
      // multiple matching dictionary in data path. Modified the code to throw ambiguity error
      // multiple matches are found.
      // dictFile = dictsPath.resolve(filename);

      try {
        dictFile = getDictFileFromDisk(fileName, dictPath);
      } catch (AmbiguousPathRefException e) {
        throw makeException(getOrigTok(), getOrigFileName(), e.getMessage());
      }

      if (null == dictFile) {
        throw makeException(getOrigTok(), getOrigFileName(),
            "Dictionary file '%s' (for dictionary '%s') not found " + "in dictionary path '%s'",
            fileName, node.getDictname(), dictPath);
      }

    }

    /**
     * Constructor for implicit dictionary creation.
     * 
     * @param name dictionary name and file name, all in one
     * @param errorLoc location at which to report any errors that occur with this dictionary
     * @param errorFile file at which to report any errors that occur with this dictionary
     * @param dictsPath search path for finding dictionaries
     * @throws ParseException
     */
    public OnDisk(String name, Token errorLoc, String errorFile, SearchPath dictsPath)
        throws ParseException {
      super(name, errorLoc, errorFile);

      // Try to find the dictionary file.
      // Fix of defect# 14246: dictionary file name resolves to first matching dictionary from
      // multiple matching dictionary in data path. Modified the code to throw ambiguity error
      // multiple matches are found.
      // dictFile = dictsPath.resolve(name);
      try {
        dictFile = getDictFileFromDisk(name, dictsPath);
      } catch (AmbiguousPathRefException e) {
        throw makeException(errorLoc, errorFile, e.getMessage());
      }

      if (null == dictFile) {
        throw makeException(getOrigTok(), getOrigFileName(),
            "Dictionary file '%s' not found " + "in dictionary path '%s'", name, dictsPath);
      }

      // Generate a fake parse tree node for the "create dictionary"
      // statement the user ought to have used instead of implicitly
      // referencing the dictionary.
      CreateDictNode.FromFile fakeNode = new CreateDictNode.FromFile(errorFile, errorLoc);
      DictParams params = new DictParams();
      params.setDictName(name);
      // params.setFileName(dictFile.getAbsolutePath());
      // Use the original file name for now, at least until we're using
      // absolute paths everywhere.
      params.setFileName(name);
      params.setIsInline(false);
      fakeNode.setParams(params);
      super.setParseTreeNode(fakeNode);
    }

    /**
     * Method to fetch the dictionary file from the disk
     * 
     * @param dictFileName
     * @param dictPath
     * @return
     * @throws AmbiguousPathRefException
     */
    private File getDictFileFromDisk(String dictFileName, SearchPath dictPath)
        throws AmbiguousPathRefException {
      ArrayList<File> multipleFilesOnDisk = dictPath.resolveMulti(dictFileName, true);
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

  @Override
  public boolean getIsView() {
    return false;
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
  public ArrayList<String> getColNames() throws ParseException {
    // Column names don't make sense for a dictionary
    throw new UnsupportedOperationException("This method not implemented");
  }

  @Override
  protected AQLParseTreeNode getNode() {
    return parseTreeNode;
  }
}
