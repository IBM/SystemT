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
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.api.exceptions.CatalogMergeError;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.AQLParserBase;
import com.ibm.avatar.aql.ErrorLocation;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.aql.catalog.Catalog.CatalogEntryTypeName;
import com.ibm.avatar.logging.Log;

/**
 * Handles the namespace for different element types such as functions, dictionaries, and
 * views/tables. <br/>
 * 
 */

public class NamespaceTable<T extends CatalogEntry> {
  /**
   * Every object has a "canonical" name, usually the fully-qualified name. This table maps these
   * canonical names to their associated catalog entries and types.
   */
  TreeMap<String, Pair<T, CatalogEntryTypeName>> canonicalNameToEntry;

  /** Map from alias to canonical name. */
  HashMap<String, String> nameSpace;

  /** turns on debugging messages */
  boolean debugNamespace = false;

  public NamespaceTable() {
    canonicalNameToEntry = new TreeMap<String, Pair<T, CatalogEntryTypeName>>();
    nameSpace = new HashMap<String, String>();
  }

  // constructor that turns the debugging output on just for this namespace
  public NamespaceTable(boolean debug) {
    this();

    debugNamespace = debug;
  }

  /**
   * Populates the maps, checking for duplicates in the process. If we attempt to store a duplicate
   * but it points to the same value, nothing happens. Only throw an error if we attempt to store a
   * duplicate and it points to a different value.
   * 
   * @param alias the name, qualified or unqualified, that can be used to refer to an element
   * @param canonicalName the fully qualified name of the element in its original namespace
   * @param associatedEntry the catalog entry associated to a canonical name
   * @param entryType type of catalog entry (function, view, etc.) for clearer error messages
   * @param token parse tree token associated with the element added
   * @param isImportedElement specifies whether the entry being added is for an imported element
   *        (or) an element definition. This flag is used to choose the appropriate validation rule
   *        to check for conflicting aliases. See {@link #isConflictingAlias(String, String)}
   * @throws ParseException
   */
  public void add(String alias, String canonicalName, T associatedEntry,
      CatalogEntryTypeName entryType, Token token, boolean isImportedElement)
      throws ParseException {

    if (debugNamespace)
      Log.debug("storing entry : " + canonicalName);

    // create a new entry in the canonicalNameToEntry map only if it doesn't already exist
    if (canonicalNameToEntry.containsKey(canonicalName)) {
      Pair<T, CatalogEntryTypeName> oldEntry = canonicalNameToEntry.get(canonicalName);

      if (oldEntry.first.getName().equals(associatedEntry.getName())) {
        // we're attempting to store the same catalog entry again, do nothing
        if (debugNamespace) {
          Log.debug(
              "Attempted to store the same catalog entry again under '" + canonicalName + "'");
        }
      } else {
        throw new ParseException(String.format(
            "Internal error: attempted to store %s and %s under canonical name %s at %s.",
            associatedEntry.toString(), canonicalNameToEntry.get(canonicalName).first.toString(),
            canonicalName, associatedEntry.getErrorLoc()));
      }
    } else {
      canonicalNameToEntry.put(canonicalName,
          new Pair<T, CatalogEntryTypeName>(associatedEntry, entryType));
    }

    // link the alias to the newly created entry
    linkAliasToCanonicalName(alias, canonicalName, token, isImportedElement);
  }

  /**
   * Helper function to call {@link NamespaceTable.add} without a token. (Used for built-in
   * functions)
   */
  public void add(String alias, String canonicalName, T associatedEntry,
      CatalogEntryTypeName entryType) throws ParseException {
    add(alias, canonicalName, associatedEntry, entryType, null, false);
  }

  /**
   * Attempts to overwrite an existing namespace table entry, instead of adding. <br/>
   * If the namespace table entry does not exist or does not match names, throw an error. <br/>
   * Currently only used for overwriting FromFile dictionary entries with Inline dictionary entries.
   * 
   * @param canonicalName the fully qualified name of the element in its original namespace
   * @param associatedEntry the new catalog entry to associate to the canonical name
   * @param entryType the type of the new catalog entry
   * @param token parse tree token associated with the element added
   */
  public void overwrite(String canonicalName, T associatedEntry, CatalogEntryTypeName entryType,
      Token token) {

    if (debugNamespace)
      Log.debug(String.format("Overwriting %s entry : %s", entryType.getText(), canonicalName));

    // check to see if we have something to overwrite
    if (canonicalNameToEntry.containsKey(canonicalName)) {

      Pair<T, CatalogEntryTypeName> oldEntry = canonicalNameToEntry.get(canonicalName);

      if (oldEntry.first.getName().equals(associatedEntry.getName())) {
        // we're overwriting a catalog entry with the same name -- this is the expected behavior
        canonicalNameToEntry.put(canonicalName,
            new Pair<T, CatalogEntryTypeName>(associatedEntry, entryType));
      } else {
        // we're overwriting a catalog entry with a different name -- throw an error
        throw new FatalInternalError(
            "%s: Attempted to overwrite dictionary %s with dictionary %s under canonical name %s.",
            associatedEntry.getErrorLoc(), associatedEntry.toString(),
            canonicalNameToEntry.get(canonicalName).first.toString(), canonicalName);
      }
    } else {
      // no catalog entry under that canonical name exists to be overwritten -- throw an error
      throw new FatalInternalError(
          "%s: Attempted to overwrite dictionary %s under canonical name %s but there was no dictionary to overwrite.",
          associatedEntry.getErrorLoc(), associatedEntry.toString(), canonicalName);
    }

  }

  /**
   * Merges all the entries in a second table with the entry set in this one.
   * <p>
   * Allows overlap on both canonical names and nicknames, but does sanity checks for certain kinds
   * of conflicts in the targets of names.
   * 
   * @param other other namespace table containing a possibly overlapping set of names
   * @throws CatalogMergeError if it detects conflicting targets
   */
  public void mergeNames(NamespaceTable<T> other) throws CatalogMergeError {
    // First merge canonical names --> entry tables
    for (Entry<String, Pair<T, CatalogEntryTypeName>> entry : other.canonicalNameToEntry
        .entrySet()) {
      String canonName = entry.getKey();

      Pair<T, CatalogEntryTypeName> existingValue = canonicalNameToEntry.get(canonName);

      if (null == existingValue) {
        // No existing entry for this canonical name, so no conflicts.
        canonicalNameToEntry.put(canonName, entry.getValue());
      } else {
        // We already have an entry under canonName; make sure it's of the same type
        Pair<T, CatalogEntryTypeName> otherValue = entry.getValue();
        if (false == existingValue.second.equals(otherValue.second)) {
          throw new FatalInternalError(
              "Entry types across catalogs for canonical name '%s' do not match (got %s and %s)",
              canonName, existingValue.second, otherValue.second);
        }

        // Also make sure that the entry has the same type of implementing class.
        if (false == existingValue.first.getClass().equals(otherValue.first.getClass())) {
          throw new FatalInternalError(
              "Catalog entry implementation classes across catalogs for canonical name '%s' "
                  + "do not match (got %s and %s)",
              canonName, existingValue.first.getClass().getName(),
              otherValue.second.getClass().getName());
        }
      }
    }

    // Now merge nicknames
    for (Entry<String, String> entry : other.nameSpace.entrySet()) {
      String nickName = entry.getKey();
      String canonName = entry.getValue();

      String existingCanonName = nameSpace.get(nickName);

      if (null == existingCanonName) {
        // No existing entry --> no conflict
        nameSpace.put(nickName, canonName);
      } else {
        // Found an existing entry. Make sure that both catalogs map it to the same canonical names.
        if (false == existingCanonName.equals(canonName)) {
          throw new FatalInternalError(
              "Canonical name of object '%s' does not match "
                  + "across catalogs being merged (canonical names are '%s' and '%s')",
              nickName, canonName, existingCanonName);
        }
      }
    }
  }

  /**
   * Links an element alias to a canonical name. If the alias is already linked to a different
   * canonical name, throw an exception
   * 
   * @param alias the name, qualified or unqualified, that can be used to refer to an element
   * @param canonicalName the fully qualified name of the element in its original namespace
   * @param token AQL parser token; tells what location any ParseExceptions should be linked to
   * @param isImportedElement specifies whether the linking of alias to canonical name is done for
   *        an imported element (or) an element definition. This flag is used to choose the
   *        validation rule to check for conflicting aliases. See
   *        {@link #isConflictingAlias(String, String)}
   * @throws ParseException
   */
  public void linkAliasToCanonicalName(String alias, String canonicalName, Token token,
      boolean isImportedElement) throws ParseException {
    if (debugNamespace)
      Log.debug("linking: " + alias + " to: " + canonicalName);

    // first see if we're linking to something
    if (!canonicalNameToEntry.containsKey(canonicalName)) {
      throw new ParseException(String.format(
          "Internal error: alias '%s' cannot be linked to non-existent catalog element %s.", alias,
          canonicalName));
    } else {
      // Case 1: We're linking an element definition
      if (false == isImportedElement) {
        if (this.containsName(alias)) {
          handleDuplicateElement(alias, canonicalName, token);
          return;
        }
      } else {
        // Case 2: We're linking an imported element
        if (this.isConflictingAlias(alias, canonicalName)) {
          handleDuplicateElement(alias, canonicalName, token);
          return;
        }
      }

      // All checks passed. OK to link the alias with canonical name.
      nameSpace.put(alias, canonicalName);
    }
  }

  /**
   * Error handler for duplicate elements.
   * 
   * @param alias the name, qualified or unqualified, that can be used to refer to an element
   * @param canonicalName the fully qualified name of the element in its original namespace
   * @param token AQL parser token; tells what location any ParseExceptions should be linked to
   * @throws ParseException
   */
  private void handleDuplicateElement(String alias, String canonicalName, Token token)
      throws ParseException {
    CatalogEntryTypeName entryType = getType(getCanonicalNameFromAlias(alias));
    if (entryType.equals(CatalogEntryTypeName.FUNCTION)) {
      this.handleDuplicateFunction(alias);
    } else if (entryType.equals(CatalogEntryTypeName.DICTIONARY)) {
      if (token == null) {
        throw new FatalInternalError(
            "No token associated with duplicate dictionary declaration of '%s'.", alias);
      }
      this.handleDuplicateDictionary(alias, token);
    } else if ((entryType.equals(CatalogEntryTypeName.TABLE))
        || (entryType.equals(CatalogEntryTypeName.VIEW))
        || (entryType.equals(CatalogEntryTypeName.EXTERNAL_VIEW))
        || (entryType.equals(CatalogEntryTypeName.DETAG_VIEW))) {
      if (token == null) {
        throw new ParseException(String.format(
            "Internal error: no token associated with duplicate view declaration of '%s'.", alias));
      } else {
        this.handleDuplicateViewOrTable(alias, entryType, token);
      }
    } else {
      throw new ParseException(String.format("%s alias '%s' is already defined at %s.",
          this.getType(canonicalName).getText(), alias, this.getCatalogEntry(alias).getErrorLoc()));
    }
  }

  /**
   * Checks if an alias name is already linked to a different element. In other words, checks if a
   * different element has been already imported into the namespace using the same alias name.
   * Attempt to import the same element more than once is *not* flagged as a conflict.
   * 
   * @param alias the name that the invoker wishes to link to the canonical name of the imported
   *        element
   * @param canonicalName the fully qualified name of the imported element in its original namespace
   * @return <code>true</code>, if the given alias is already linked to a different canonical name;
   *         <code>false</code>, otherwise.
   */
  public boolean isConflictingAlias(String alias, String canonicalName) {
    if (this.containsName(alias)) {
      String prevImportedCanonicalName = getCanonicalNameFromAlias(alias);

      // Flag as conflict only if the previously imported element & the current element being
      // imported are different!
      return !canonicalName.equals(prevImportedCanonicalName);
    } else {
      return false;
    }
  }

  /**
   * Helper function to call {@link NamespaceTable.linkAliasToCanonicalName} without a token. (Used
   * for functions.)
   */
  public void linkAliasToCanonicalName(String alias, String canonicalName) throws ParseException {
    linkAliasToCanonicalName(alias, canonicalName, null, false);
  }

  /**
   * Checks if the name (fully qualified or unqualified alias) is declared in the namespace.
   * 
   * @param name the name, qualified or unqualified, that can be used to refer to an element
   */
  public boolean containsName(String name) {
    if (nameSpace.containsKey(name)) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Derive the canonical name from an alias. If the alias does not exist, return null.
   * 
   * @param alias the name, qualified or unqualified, that can be used to refer to an element
   * @return
   */
  public String getCanonicalNameFromAlias(String alias) {
    if (this.containsName(alias)) {
      return nameSpace.get(alias);
    } else {
      return null;
    }

  }

  /**
   * Returns a list of all aliases associated with a canonical name.
   * 
   * @param canonicalName the fully qualified name of the element in its original namespace
   * @return list of all aliases associated with canonical name
   */
  public ArrayList<String> getAliasesFromName(String canonicalName) {
    ArrayList<String> list = new ArrayList<String>();

    for (String key : nameSpace.keySet()) {
      if (nameSpace.get(key).equals(canonicalName)) {
        list.add(key);
      }
    }

    return list;
  }

  /**
   * Returns the catalog entry associated with an alias in this namespace. <br/>
   * If this entry does not exist, return null.
   * 
   * @param alias the name, qualified or unqualified, that can be used to refer to an element
   * @return
   */
  public T getCatalogEntry(String alias) {
    return getEntryFromCanonicalName(getCanonicalNameFromAlias(alias));
  }

  /**
   * Returns the catalog entry associated with a canonical name in this namespace. <br/>
   * Can return null if the entry is not found or if null is passed in.
   * 
   * @param canonicalName the fully qualified name of the element in its original namespace
   * @return
   */
  public T getEntryFromCanonicalName(String canonicalName) {
    if (canonicalName == null)
      return null;
    if (canonicalNameToEntry.containsKey(canonicalName)) {
      return canonicalNameToEntry.get(canonicalName).first;
    } else {
      return null;
    }
  }

  /**
   * Returns the element type (in a string) associated with a canonical name in this namespace.
   * <br/>
   * Note that namespaces can have elements of different types (i.e., views and tables).
   * 
   * @param canonicalName
   * @return
   * @throws ParseException
   */
  public CatalogEntryTypeName getType(String canonicalName) throws ParseException {
    if (canonicalNameToEntry.containsKey(canonicalName)) {
      return canonicalNameToEntry.get(canonicalName).second;
    } else {
      throw new ParseException(String.format("Element '%s' not found in catalog.", canonicalName));
    }
  }

  /**
   * @return a list of all the catalog entries in this namespace
   */
  public ArrayList<T> getAllEntries() {
    ArrayList<T> ret = new ArrayList<T>();

    for (Pair<T, CatalogEntryTypeName> entryPair : canonicalNameToEntry.values()) {
      ret.add(entryPair.first);
    }

    return ret;
  }

  /**
   * Handles error messages for duplicate functions in a clear fashion. <br/>
   * This conforms to the design that UDFs, scalar functions, and aggregate functions share the same
   * namespace. <br/>
   * 
   * @param funcName the name of the duplicate function
   * @throws ParseException
   */
  private void handleDuplicateFunction(String funcName) throws ParseException {
    CatalogEntry oldFunc = null;
    if (this.containsName(funcName)) {
      // a function with the same name is already defined, get it if it is builtin or UDF
      oldFunc = this.getCatalogEntry(funcName);

      if (oldFunc instanceof ScalarUDFCatalogEntry) {
        AQLParseTreeNode node = ((ScalarUDFCatalogEntry) oldFunc).getParseTreeNode();

        ErrorLocation oldFuncLoc;
        oldFuncLoc = new ErrorLocation(new File(node.getContainingFileName()), node.getOrigTok());

        throw new ParseException(String.format("Function name '%s' is already defined at %s.",
            funcName, oldFuncLoc.getLocStr()));
      } else if (oldFunc instanceof TableUDFCatalogEntry) {
        AQLParseTreeNode node = ((TableUDFCatalogEntry) oldFunc).getParseTreeNode();

        ErrorLocation oldFuncLoc;
        oldFuncLoc = new ErrorLocation(new File(node.getContainingFileName()), node.getOrigTok());

        throw new ParseException(String.format("Function name '%s' is already defined at %s.",
            funcName, oldFuncLoc.getLocStr()));
      } else if (oldFunc instanceof ScalarFuncCatalogEntry) {
        throw new ParseException(String.format(
            "'%s' is the name of a built-in scalar or predicate function.  Please create a function with a different, non-reserved name.",
            funcName));
      } else if (oldFunc instanceof AggFuncCatalogEntry) {
        throw new ParseException(String.format(
            "'%s' is the name of a built-in aggregate function.  Please create a function with a different, non-reserved name.",
            funcName));
      } else {
        throw new ParseException(String.format(
            "'%s' shares the same name as an unknown element.  (We should never see this.)",
            funcName));
      }
    }

  }

  /**
   * Handles error messages for duplicate dictionaries. <br/>
   * 
   * @param dictName the name of the duplicate dictionary
   * @param token the error location token corresponding to the dictionary catalog entry to be added
   * @throws ParseException
   */
  private void handleDuplicateDictionary(String dictName, Token token) throws ParseException {
    // check to see if we already have a dict with the same name
    if (this.containsName(dictName)) {
      CatalogEntry ce = this.getCatalogEntry(dictName);

      // determine the type and location information of the old view/table
      ErrorLocation oldElementLoc = ce.getErrorLoc();

      // throw a simple error message if we don't have the old location info
      if (oldElementLoc == null) {
        throw AQLParserBase
            .makeException(String.format("Dictionary '%s' already defined", dictName), token);
      } else {
        throw AQLParserBase.makeException(
            String.format("Dictionary '%s' already defined at %s", dictName, oldElementLoc), token);
      }
    }
  }

  /**
   * Handles error messages for duplicate views/tables in a clear fashion. <br/>
   * This conforms to the design that tables and views share the same namespace. <br/>
   * Supports all possible catalog entry types (Table, View, Detag View Output, External View).
   * 
   * @param fqName the fully qualified name of the view/table element to be added
   * @param elementType the type of the catalog entry to be added
   * @param dupErrorToken the error location token corresponding to the catalog entry to be added
   * @throws ParseException
   */
  private void handleDuplicateViewOrTable(String fqName, CatalogEntryTypeName elementType,
      Token token) throws ParseException {
    // check to see if we already have a view/table with the same name
    if (this.containsName(fqName)) {
      CatalogEntry ce = this.getCatalogEntry(fqName);

      // determine the type and location information of the old view/table
      CatalogEntryTypeName oldElementType;
      ErrorLocation oldElementLoc;
      if (ce instanceof TableCatalogEntry) {
        oldElementType = CatalogEntryTypeName.TABLE;
        oldElementLoc = ce.getErrorLoc();
      } else if (ce instanceof ViewCatalogEntry) {
        oldElementType = CatalogEntryTypeName.VIEW;
        oldElementLoc = ce.getErrorLoc();
      } else if (ce instanceof DetagCatalogEntry) {
        oldElementType = CatalogEntryTypeName.DETAG_VIEW;
        oldElementLoc = ce.getErrorLoc();
      } else if (ce instanceof ExternalViewCatalogEntry) {
        oldElementType = CatalogEntryTypeName.EXTERNAL_VIEW;
        oldElementLoc = ce.getErrorLoc();
      } else {
        oldElementType = CatalogEntryTypeName.UNKNOWN;
        oldElementLoc = null;
      }

      // use a different verb depending on whether the previous name is imported or defined
      String verb;
      if (ce.isImported()) {
        verb = "imported";
      } else {
        verb = "defined";
      }

      // throw a simple error message if we don't have the old location info
      if (oldElementLoc == null) {
        throw AQLParserBase.makeException(
            String.format("%s '%s' already %s.", elementType.getText(), fqName, verb), token);
      } else {
        // use less confusing error message if the two clashing elements are of the same type
        if (elementType == oldElementType) {
          throw AQLParserBase.makeException(String.format("%s '%s' already %s at %s.",
              elementType.getText(), fqName, verb, oldElementLoc.getLocStr()), token);
        } else {
          throw AQLParserBase
              .makeException(String.format("%s '%s' cannot have the same name as %s '%s' %s at %s.",
                  elementType.getText(), fqName, oldElementType.getText().toLowerCase(), fqName,
                  verb, oldElementLoc.getLocStr()), token);
        }
      }
    }
  }
}
