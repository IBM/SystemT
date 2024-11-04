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
package com.ibm.avatar.aog;

import java.util.Collection;
import java.util.TreeMap;

import com.ibm.avatar.algebra.datamodel.ExternalView;
import com.ibm.avatar.algebra.datamodel.Table;
import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.logging.Log;

/**
 * Symbol table for the AOG parser. Contains entries for nicknames and inline dictionaries.
 */
public class SymbolTable {

  /** Mapping from nickname to the corresponding subtree of the parse tree. */
  private TreeMap<String, AOGOpTree> nicknames = new TreeMap<String, AOGOpTree>();

  /**
   * Lookup tables defined in the AOG file. Stored in an ordered map to avoid screwing up regression
   * test results.
   */
  private TreeMap<String, Table> tables = new TreeMap<String, Table>();

  /**
   * External views defined in the AOG file. Stored in an ordered map to avoid screwing up
   * regression test results.
   */
  private TreeMap<String, ExternalView> externalViews = new TreeMap<String, ExternalView>();

  /**
   * Container for the collection of unique string objects read during parsing
   */
  protected StringTable stringTable = new StringTable();

  /**
   * Dictionaries defined in the AOG file. Stored in an ordered map to avoid screwing up regression
   * test results.
   */
  private TreeMap<String, CompiledDictionary> dicts = new TreeMap<String, CompiledDictionary>();

  /**
   * UDFs defined in the AOG file.
   */
  // private TreeMap<String, UDFunction> udfs = new TreeMap<String, UDFunction> ();

  // private TreeMap<String, List<UDFunction>> jarToUDF = new TreeMap<String, List<UDFunction>> ();

  /** The factory object used to produce annotation scans. */
  private AnnotationReaderFactory readerFactory = null;

  /*
   * BEGIN METHODS
   */

  public void addNick(String nick, AOGOpTree tree) throws ParseException {

    if (nicknames.containsKey(nick)) {
      throw new ParseException(
          String.format("Nickname $%s appears twice in operator graph spec", nick));
    }
    nicknames.put(nick, tree);
  }

  public void remove(String nick) {
    nicknames.remove(nick);
  }

  public AOGOpTree lookupNick(String key) {
    return nicknames.get(key);
  }

  public Collection<AOGOpTree> optrees() {
    return nicknames.values();
  }

  public boolean containsNick(String nick) {
    return nicknames.containsKey(nick);
  }

  private static final boolean debugTables = false;

  /**
   * Add a new inline lookup table definition.
   * 
   * @param table parse tree node for the table definition
   */
  public void addTable(Table table) {

    String tableName = table.getName();
    if (debugTables) {
      Log.debug("SymbolTable: Adding info on table %s", tableName);
    }

    tables.put(tableName, table);
  }

  /**
   * Retrieve a table previously added with {@link #addTable(Table)}
   * 
   * @param tabName name of the table
   * @return the table, if found
   * @throws ParseException if the table is not found
   */
  public Table getTable(String tabName) throws ParseException {
    Table ret = tables.get(tabName);

    if (null == ret) {
      throw new ParseException(String.format("Table '%s' not found", tabName));
    }

    return ret;
  }

  private static final boolean debugExternalViews = false;

  /**
   * Add a new external view definition.
   * 
   * @param table parse tree node for the external view definition
   */
  public void addExternalView(ExternalView view) {

    if (debugExternalViews) {
      Log.debug("SymbolTable: Adding info on external view %s", view.getName());
    }

    externalViews.put(view.getName(), view);
  }

  /**
   * Retrieve an external view previously added with {@link #addExternalView(ExternalView)}
   * 
   * @param viewName name of the external view
   * @return the external view, if found
   * @throws ParseException if the external view is not found
   */
  public ExternalView getExternalView(String viewName) throws ParseException {
    ExternalView ret = externalViews.get(viewName);

    if (null == ret) {
      throw new ParseException(String.format("External view '%s' not found", viewName));
    }

    return ret;
  }

  /**
   * Retrieve the unique strings table.
   * 
   * @return
   */
  public StringTable getStringTable() {
    return stringTable;
  }

  /**
   * Add a new dictionary definition.
   * 
   * @param dict compiled dictionary.
   */
  public void addDict(CompiledDictionary dict) {
    final boolean debug = false;

    if (null == dict) {
      throw new RuntimeException("Null pointer passed to addDict()");
    }

    if (debug) {
      Log.debug("Adding information about dictionary '%s'", dict.getCompiledDictName());
    }

    dicts.put(dict.getCompiledDictName(), dict);
  }

  // @Deprecated
  // public void addDicts(Map<String, DictFile> dicts) {
  // inlineDicts.putAll(dicts);
  // }

  public CompiledDictionary lookupDict(String name) {
    return dicts.get(name);
  }

  /**
   * Add a new UDF definition.
   * 
   * @param dict parse tree node for the UD Function.
   */
  // public void addUDFunction (UDFunction func)
  // {
  // final boolean debug = false;
  //
  // if (debug) {
  // Log.debug ("Adding information about UDF '%s'", func.getName ());
  // }
  //
  // // UDF information now stored in the catalog
  // //udfs.put (func.getName (), func);
  //
  // String jarName = func.getParams ().getJarName ();
  // List<UDFunction> udflist = jarToUDF.get (jarName);
  // if (udflist == null) {
  // udflist = new ArrayList<UDFunction> ();
  // jarToUDF.put (jarName, udflist);
  // }
  // udflist.add (func);
  // }

  /**
   * Lookup UDF definition.
   * 
   * @param name name of the UD Function.
   */
  // public UDFunction lookupUDFunction (String name)
  // {
  // return udfs.get (name);
  // }

  /**
   * Set jar file for UDF functions
   * 
   * @param jarName the name of the jar file
   * @param jarContents the contents of the jar file
   * @throws ParseException
   */
  // public void setJarFile (String jarName, byte[] jarContents) throws ParseException
  // {
  //
  // ByteArrayClassLoader loader;
  //
  // // The ByteArrayClassLoader needs a backup classloader. Use whatever
  // // this instance of the SymbolTable has available (don't want to use the
  // // system classloader -- it might belong to a framework like UIMA that
  // // is invoking SystemT)
  // ClassLoader parentClassLoader = this.getClass ().getClassLoader ();
  //
  // try {
  // loader = ByteArrayClassLoader.fromJarContents (jarContents, parentClassLoader);
  // }
  // catch (IOException e) {
  // throw new ParseException (String.format ("Error reading contents of jar file '%s' "
  // + "out of operator graph descriptor: %s", jarName, e.getMessage ()));
  // }
  //
  // // Pull up all the UDFs that are in the jar, and have each of them load
  // // up their implementation classes.
  // List<UDFunction> udflist = jarToUDF.get (jarName);
  // if (udflist != null) {
  // for (UDFunction func : udflist) {
  // func.loadClass (loader);
  // }
  // }
  // }

  public AnnotationReaderFactory getAnnotReaderFactory() {
    return this.readerFactory;
  }

  public void setAnnotReaderFactory(AnnotationReaderFactory factory) {
    this.readerFactory = factory;
  }

  /**
   * @return all symbol table entries for dictionaries, in alphabetical order by dictionary name
   */
  public Collection<CompiledDictionary> getDicts() {
    return dicts.values();
  }

  /**
   * @return all symbol table entries for lookup tables, in alphabetical order by table name
   */
  public Collection<Table> getTables() {
    return tables.values();
  }

  /**
   * @return all symbol table entries for external views, in alphabetical order by view name
   */
  public Collection<ExternalView> getExternalViews() {
    return externalViews.values();
  }

  /**
   * Adds the incoming symbol table entries to the current symbol table
   * 
   * @param symtab The symbol table whose contents are to be added to the current symbol table
   * @throws ParseException
   */
  public void add(SymbolTable symtab) throws ParseException {
    // unionize dicts
    for (CompiledDictionary dict : symtab.getDicts()) {
      this.addDict(dict);
    }

    // unionize ext views
    for (ExternalView externalView : symtab.getExternalViews()) {
      this.addExternalView(externalView);
    }

    // jar to UDFs
    // jarToUDF.putAll (symtab.jarToUDF);

    // unionize optrees
    for (AOGOpTree aogOpTree : symtab.optrees()) {
      // SPECIAL CASE: Do not add Document twice
      if ("Document".equals(aogOpTree.getNickname())) {
        if (nicknames.containsKey("Document")) {
          continue;
        }
      }

      // SPECIAL CASE: SRM node will be added post merging
      if (aogOpTree.getNickname().startsWith("SRM_")) {
        continue;
      }

      // SPECIAL CASE: SDM node will be added post merging
      if (aogOpTree.getNickname().startsWith("SDM_")) {
        continue;
      }

      // NORMAL CASE: add aogOpTree to symbol table
      this.addNick(aogOpTree.getNickname(), aogOpTree);
    }

    // unionize string table
    stringTable.add(symtab.stringTable);

    // unionize tables
    for (Table table : symtab.getTables()) {
      this.addTable(table);
    }

    // unionize UDFs
    // udfs.putAll (symtab.udfs);
  }

}
