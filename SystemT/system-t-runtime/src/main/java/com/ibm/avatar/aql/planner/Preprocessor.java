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
package com.ibm.avatar.aql.planner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.avatar.algebra.function.predicate.ContainsDict;
import com.ibm.avatar.algebra.function.predicate.ContainsDicts;
import com.ibm.avatar.algebra.function.predicate.ContainsRegex;
import com.ibm.avatar.algebra.function.predicate.MatchesDict;
import com.ibm.avatar.algebra.function.predicate.MatchesRegex;
import com.ibm.avatar.algebra.function.scalar.RegexConst;
import com.ibm.avatar.algebra.util.dict.DictFile;
import com.ibm.avatar.algebra.util.dict.DictParams;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.InvalidDictionaryFileFormatException;
import com.ibm.avatar.aql.AQLParserBase;
import com.ibm.avatar.aql.CreateDictNode;
import com.ibm.avatar.aql.CreateFunctionNode;
import com.ibm.avatar.aql.CreateViewNode;
import com.ibm.avatar.aql.DictExNode;
import com.ibm.avatar.aql.ExtendedParseException;
import com.ibm.avatar.aql.ExtractListNode;
import com.ibm.avatar.aql.ExtractNode;
import com.ibm.avatar.aql.ExtractionNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.FromListItemSubqueryNode;
import com.ibm.avatar.aql.FromListItemViewRefNode;
import com.ibm.avatar.aql.NickNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PatternExpressionNode;
import com.ibm.avatar.aql.PatternMultipleChildrenNode;
import com.ibm.avatar.aql.PatternSingleChildNode;
import com.ibm.avatar.aql.RValueNode;
import com.ibm.avatar.aql.RegexExNode;
import com.ibm.avatar.aql.ScalarFnCallNode;
import com.ibm.avatar.aql.SelectListItemNode;
import com.ibm.avatar.aql.SelectListNode;
import com.ibm.avatar.aql.SelectNode;
import com.ibm.avatar.aql.StringNode;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.aql.ViewBodyNode;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.DetagCatalogEntry;
import com.ibm.avatar.aql.catalog.DictCatalogEntry;
import com.ibm.avatar.aql.catalog.TableCatalogEntry;
import com.ibm.avatar.aql.compiler.CompilerWarning;
import com.ibm.avatar.aql.compiler.ParseToCatalog;
import com.ibm.avatar.logging.Log;
import com.ibm.avatar.logging.MsgType;
import com.ibm.systemt.regex.api.SimpleRegex;
import com.ibm.systemt.util.regex.FlagsString;

/**
 * Preprocessing stage of the AQL compiler. Performs various query rewrite tasks.
 * 
 */
public class Preprocessor extends AQLRewriter {

  /** Should we use regex strength-reduction during the rewrite phase? */
  private boolean performRSR = Planner.DEFAULT_RSR;

  public void setPerformRSR(boolean performRSR) {
    this.performRSR = performRSR;
  }

  /**
   * Main entry point for preprocessing
   * 
   * @param views all the views in the current module, in topologically sorted order
   * @param catalog AQL catalog, for looking up additional metadata
   * @throws CompilerException
   */
  public void preProcess(ArrayList<CreateViewNode> views, Catalog catalog)
      throws ParseException, CompilerException {

    final boolean debug = false;

    // Create the object that will perform various validation checks.
    AQLStatementValidator validator = new AQLStatementValidator(catalog);

    // Pass 1: Inline non-correlated subqueries
    inlineNonCorrelatedSubqueries(views, catalog);

    // Pass 1.5: Validate all "create function" statements (should happen before type inference)
    validator.validateFunctions();

    // Pass 2: Inline exists subqueries
    // TODO: Implement this pass!

    // Pass 2.5 (formerly pass 10): Classify FunctionNode to Scalar/Aggregate/Constant function
    // This classification will be used during AOG generation
    // No longer necessary after implementation of type inference
    // classifyFunctionNodes (views, catalog);

    // Pass 3: Type inference
    SchemaInferrer.inferTypes(catalog, views);

    // TODO: Ideally all validations are to appear in Pass 4 only. However, inferTypes() in previous
    // pass does some
    // validations to keep AQLCompileErrorTests happy. Need to revisit the ordering of validations
    // once all high
    // priority issues are fixed

    // Pass 4: Validate dictionary references
    validator.validateDictionaries();

    // Pass 5: Validate table references
    validator.validateTables();

    // Pass 6: Inline any dictionaries and tables that the AQL file says to
    // load from external files.
    inlineExternalFiles(views, catalog);

    /*
     * If there are errors after type inference and validation (done as a part of type inference),
     * do not proceed further with compilation. Throw compilation error and exit from the program
     * flow.
     */
    if (catalog.getExceptions().size() > 0) {
      CompilerException ce = new CompilerException(catalog.getSummary());
      for (Exception e : catalog.getExceptions()) {
        ce.addError(e);
      }
      throw ce;
    }

    // Pass 7: mark dictionaries as externally dependent when a) defined as external b) depends on
    // external table
    detectDictExternalDependency(catalog);

    // Pass 8: Rewrite Sequence Pattern statements
    rewriteSequencePatterns(views, catalog);

    // Pass 9: Qualify references only after all rewrites are done
    qualifyReferences(views, catalog);

    // Pass 10: Generate autoColumnName for all the SelectItemNode in select
    // list of SelectNode and ExtractNode
    generateAutoColumnName(views, catalog);

    // Pass 11: Regex Strength Reduction rewrite.
    if (performRSR) {
      if (debug) {
        Log.debug("performRSR is true; " + "performing regex strength reduction");
      }
      applyRSR(views, catalog);
    }

    // Pass 12: Determine candidates for Shared Dictionary Matching.
    // Note that we execute this step regardless of whether SDM will
    // actually be applied later on.
    labelSDMCandidates(views, catalog);

    // Pass 13: Update dictionaries declared without languages against which match has to be
    // performed, with default
    // dictionary language set
    updateDictMatchingLangSet(catalog);
  }

  /**
   * If a dictionary is marked as external (or) if it depends on external table, then mark the
   * DictCatalogEntry as "externally dependent"
   * 
   * @param catalog
   * @throws ParseException
   */
  private void detectDictExternalDependency(Catalog catalog) throws ParseException {
    for (CreateDictNode node : catalog.getCreateDictNodes()) {
      try {
        if (node instanceof CreateDictNode.Inline) {
          // check if the node corresponds to a 'create *external* dictionary'
          if (true == node.getParams().getIsExternal()) {

            // fetch dict catalog entry and mark it as externally dependent
            DictCatalogEntry dce = catalog.lookupDict(node.getDictname(), null, null);
            if (null != dce) {
              dce.setExternallyDependent(true);
            }
          }
        } // end: if node instanceof Inline

        // check if the dictionary was created out of an external table
        else if (node instanceof CreateDictNode.FromTable) {
          TableCatalogEntry tce =
              (TableCatalogEntry) catalog.lookupTable(node.getParams().getTabName());

          // check if Table Catalog Entry exists and was declared external
          if ((null != tce) && (null != tce.getParseTreeNode())
              && (true == tce.getParseTreeNode().getIsExternal())) {
            // fetch dict catalog entry and mark it as externally dependent
            DictCatalogEntry dce = catalog.lookupDict(node.getDictname(), null, null);
            if (null != dce) {
              dce.setExternallyDependent(true);
            }
          }
        } // end: if node instanceof FromTable
      } catch (ExtendedParseException epe) {
        // Already wrapped, just propagate
        throw epe;
      } catch (ParseException pe) {
        // Make wrapper exception
        throw ParseToCatalog.makeWrapperException(pe, node.getContainingFileName());
      }
    } // end: for each dict node
  }

  /**
   * Apply the Regex Strength Reduction (RSR) transformation to all applicable regular expressions
   * in the indicated views.
   */
  private void applyRSR(ArrayList<CreateViewNode> views, Catalog catalog) throws ParseException {
    // Basic regex strength reduction: Use the SimpleRegex engine in place
    // of the Java regex engine whenever possible.

    // STAGE 1: Rewrite any table functions.
    // This stage no longer exists, since the Regex and RegexTok table functions no longer exist.

    // Stage 2: Rewrite all EXTRACT statements.
    class extractCB extends extractRewriter {

      boolean debug = false;

      @Override
      public ExtractNode rewrite(ExtractNode orig) throws ParseException {
        try {
          ExtractListNode extractList = orig.getExtractList();
          ExtractionNode extraction = extractList.getExtractSpec();

          if (extraction instanceof RegexExNode) {
            RegexExNode regexEx = (RegexExNode) extraction;
            for (int i = 0; i < regexEx.getNumRegexes(); i++) {
              String pattern = regexEx.getRegex(i).getRegexStr();
              int flags = regexEx.getFlags();
              int ngrp = regexEx.getNumGroups();
              int firstGrp = regexEx.getGroupID(0);

              // We can use the SimpleRegex engine if:
              // a. The expression is compatible
              // b. There is no use of capturing groups in the regex
              // spec
              if (SimpleRegex.isSupported(pattern, flags, true) && 1 == ngrp && 0 == firstGrp) {
                if (debug) {
                  System.err.printf("%s: Applied RSR to extract regex /%s/)\n", curViewName,
                      pattern);
                }
                regexEx.setUseSimpleEngine(i, true);
              } else {
                printRSRWarning(curViewName, pattern, catalog, orig);
                // Log.log(MsgType.AQLPerfWarning,
                // "%s: Did NOT apply"
                // + " RSR to Regex(/%s/)", curViewName,
                // pattern);
              }
            }

          }

          // We modified the entry in place, so just return the
          // original.
          return orig;
        } catch (ParseException pe) {
          // Make wrapper exception to propagate error location info
          throw ParseToCatalog.makeWrapperException(pe, orig.getContainingFileName());
        }

      }
    }

    extractCB ecb = new extractCB();
    ecb.catalog = catalog;

    for (CreateViewNode cvn : views) {
      cvn.setBody(
          rewriteExtracts(ecb, cvn.getBody(), cvn.getModuleName(), cvn.getUnqualifiedName()));
    }

    // Stage 3: Rewrite all scalar functions and predicates
    class funcCB extends funcRewriter {

      @SuppressWarnings("unused")
      private static final boolean debug = false;

      @Override
      ScalarFnCallNode rewrite(ScalarFnCallNode orig) throws ParseException {

        // Currently, the only functions that evaluate regexes are
        // ContainsRegex() and MatchesRegex()
        if (ContainsRegex.FNAME.equals(orig.getFuncName())
            || MatchesRegex.FNAME.equals(orig.getFuncName())) {

          ArrayList<RValueNode> args = orig.getArgs();

          // check for null arguments -- we should never get this now that it's validated in the
          // parser
          if ((args == null) || (args.isEmpty())) {
            throw AQLParserBase.makeException(orig.getOrigTok(),
                "Expected a regular expression argument in function %s, but got no argument.",
                orig.getFuncName());
          }

          // Regex is always argument 0
          com.ibm.avatar.aql.RegexNode regex = (com.ibm.avatar.aql.RegexNode) args.get(0);

          String regexStr = regex.getRegexStr();

          int flags = ContainsRegex.DEFAULT_REGEX_FLAGS;
          if (3 == args.size()) {
            // 3 arguments --> flags string present, as arg 1
            String flagStr = ((StringNode) args.get(1)).getStr();
            try {
              flags = FlagsString.decode(null, flagStr);
            } catch (FunctionCallValidationException e) {
              // Convert AOG parse exception to AQL exception
              throw AQLParserBase.makeException(e.getMessage(), orig.getOrigTok());
            }
          }

          if (SimpleRegex.isSupported(regexStr, flags, true)) {
            // This regex is compatible with the SimpleRegex engine.
            regex.setEngineName(RegexConst.SIMPLE_REGEX_ENGINE_NAME);
          }
        }

        // We modified the node in place.
        return orig;
      }

    }

    funcCB fcb = new funcCB();
    fcb.catalog = catalog;

    for (CreateViewNode cvn : views) {
      rewriteScalarFuncs(fcb, cvn);
    }

  }

  private static final Pattern INLINED_VIEW_PATTERN =
      Pattern.compile("(.*)" + SUBQUERY_NAME_PUNCT + "\\d+");

  /**
   * Generate a warning message about being unable to apply Regex Strength Reduction to a regex.
   * 
   * @param origNode
   */
  private void printRSRWarning(String viewName, String pattern, Catalog catalog,
      ExtractNode origNode) {

    // Detect views that are generated by inlining subqueries, and
    // "un-inline" the view names.
    Matcher m = INLINED_VIEW_PATTERN.matcher(viewName);
    while (m.matches()) {
      viewName = m.group(1);
      m = INLINED_VIEW_PATTERN.matcher(viewName);
    }

    String template = "Regular expression in view '%s' not accelerated: %s.\n"
        + "This regular expression cannot be executed by the system's accelerated regex engine\n"
        + "and will be executed with Java's built-in regular expression engine instead.\n"
        + "In most cases, this incompatibility occurs because the regular expression \n"
        + "contains lookahead/lookbehind constructs like \n"
        + "\t\\A, \\Z, \\b, ^, $,  (?=X), (?!X), (?<=X), or (?<!X),\n"
        + "or because you are using capturing groups.";

    String fileName = origNode.getContainingFileName();
    Token origTok = origNode.getOrigTok();

    // Log the warning
    Log.log(MsgType.AQLPerfWarning, template, viewName,
        StringUtils.quoteStr('/', pattern, false, true));
    // Also, add the warning to the catalog
    // TODO: The file name of the AQL file is not available here.
    CompilerWarning compilerWarning =
        new CompilerWarning(CompilerWarning.WarningType.RSR_FOR_THIS_REGEX_NOT_SUPPORTED,
            String.format(template, viewName, StringUtils.quoteStr('/', pattern, false, true)),
            fileName, origTok.beginLine, origTok.beginColumn, origTok.endLine, origTok.endColumn);
    catalog.addCompilerWarning(compilerWarning);
  }

  /**
   * Inline non-correlated subqueries in the FROM clauses of all the indicated views. Each subquery
   * is rewritten as an internal view.
   */
  protected void inlineNonCorrelatedSubqueries(ArrayList<CreateViewNode> views, Catalog catalog)
      throws ParseException {

    final boolean debug = false;
    final boolean verboseDebug = false;

    if (debug) {
      Log.debug("Before inlining subqueries, views are: %s", viewNamesList(views));
    }

    // Create a callback to handle the rewriting.
    class subqueryCB extends fromItemRewriter {

      // remember the new views we create
      ArrayList<CreateViewNode> newInternalViews = new ArrayList<CreateViewNode>();

      @Override
      public FromListItemNode rewrite(FromListItemNode orig) throws ParseException {
        this.curModuleName = orig.getModuleName();
        ViewBodyNode subquery = ((FromListItemSubqueryNode) orig).getBody();

        // Create a new internal view from the subquery
        String newNick = String.format("%s", curViewName);

        if (debug && verboseDebug) {
          Log.debug("Inlining subquery " + "(%s) as new internal view:\n%s", newNick,
              subquery.dumpToStr(0));
        }

        NickNode viewName = new NickNode(newNick);
        CreateViewNode cvn =
            new CreateViewNode(viewName, subquery, orig.getContainingFileName(), orig.getOrigTok());
        cvn.setModuleName(curModuleName);

        // Add the new view to the catalog and remember it in the list
        // of new views
        catalog.addView(cvn);
        newInternalViews.add(cvn);

        if (debug && verboseDebug) {
          Log.debug("Inlined subquery (%s) as the " + "following view:\n%s", newNick,
              cvn.dumpToStr(0));
        }

        // Return a reference to the internal view we just created
        FromListItemViewRefNode viewRef = new FromListItemViewRefNode(viewName);
        viewRef.setAlias(orig.getAlias());
        return viewRef;
      }

    }

    subqueryCB sqcb = new subqueryCB();
    sqcb.catalog = catalog;

    for (CreateViewNode cvn : views) {
      rewriteSubqueries(sqcb, cvn);

      // if (debug && verboseDebug) {
      // Log.debug("%s: Finished rewriting"
      // + " as the following view:\n%s", cvn.getViewName(), cvn
      // .dumpToStr(0));
      // }

    }

    // Combine the new internal views and the existing views in a single
    // collection.
    // Add all new internal views before all existing views,
    // to maintain the topological order of the collection
    ArrayList<CreateViewNode> allViews = new ArrayList<CreateViewNode>();
    allViews.addAll(sqcb.newInternalViews);
    allViews.addAll(views);

    // Make sure that the views are sorted according to their dependencies,
    // so that the compilation will proceed in the correct order.
    allViews = catalog.topologicalSort(allViews);

    views.clear();
    views.addAll(allViews);

    if (debug) {
      Log.debug("After inlining subqueries, views are: %s", viewNamesList(views));
    }
  }

  /**
   * Label all the Dictionary table function calls that will be evaluated in a shared manner.
   */
  private void labelSDMCandidates(ArrayList<CreateViewNode> views, Catalog catalog)
      throws ParseException {

    final int MIN_DICT_FOR_SDM = 1;

    // Create a callback to count the number of dictionary extractions.
    class exCountCB extends extractRewriter {

      int dictCount = 0;

      @Override
      ExtractNode rewrite(ExtractNode orig) throws ParseException {
        if (orig.getExtractList().getExtractSpec() instanceof DictExNode) {
          dictCount++;
        }
        return orig;
      }
    }

    exCountCB eccb = new exCountCB();
    eccb.catalog = catalog;
    for (CreateViewNode cvn : views) {
      rewriteExtracts(eccb, cvn.getBody(), cvn.getModuleName(), cvn.getUnqualifiedName());
    }

    final int dictCount = eccb.dictCount;

    // System.err.printf ("Dictionary count is %d\n", dictCount);

    // Create a callback to handle the actual rewriting of extract nodes
    class ecb extends extractRewriter {

      public boolean debug = false;

      @Override
      ExtractNode rewrite(ExtractNode orig) throws ParseException {

        ExtractionNode extraction = orig.getExtractList().getExtractSpec();

        if (extraction instanceof DictExNode) {

          // Found a dictionary extraction.
          DictExNode dict = (DictExNode) extraction;

          for (int dictIx = 0; dictIx < dict.getNumDicts(); dictIx++) {

            // System.err.printf("Using SDM on %s index %d\n", dict,
            // dictIx);

            // For now, we just use SDM everywhere unless there are
            // MIN_DICT_FOR_SDM or fewer dictionary calls.
            //
            // When the statistics collector is in place, we will
            // use statistics about the dictionary to make a more
            // nuanced decision.
            if (dictCount > MIN_DICT_FOR_SDM) {
              dict.setUseSDM(dictIx, true);

              if (debug) {
                Log.debug("Labeled SDM for dictionary '%s'", dict.getDictName(dictIx).getStr());
              }
            } else {
              dict.setUseSDM(dictIx, false);
            }
          }
        }
        return orig;
      }

    }

    ecb callback = new ecb();
    for (CreateViewNode cvn : views) {
      rewriteExtracts(callback, cvn.getBody(), cvn.getModuleName(), cvn.getUnqualifiedName());
    }
  }

  /**
   * Inline any dictionaries and tables that the AQL file says to load from external files.
   */
  private void inlineExternalFiles(ArrayList<CreateViewNode> views, final Catalog catalog) {

    // Inline all CREATE DICTIONARY statements that reference external dictionary files
    try {
      for (CreateDictNode node : catalog.getCreateDictNodes()) {
        // Rewrite the "create dictionary" statements in place, so that we don't have to recreate
        // the list.
        String dictName = node.getDictname();
        Token origTok = node.getOrigTok();
        String containingFileName = node.getContainingFileName();

        try {
          inlineDictFile(dictName, catalog, origTok, containingFileName);
        } catch (ParseException pe) {
          catalog.addCompilerException(pe);
        }
      }
    } catch (ParseException pe) {
      catalog.addCompilerException(pe);
    }

    // Create a callback to locate and inline all the dictionary extractions.
    final class extractCB extends extractRewriter {

      @Override
      ExtractNode rewrite(ExtractNode orig) {
        ExtractionNode extract = orig.getExtractList().getExtractSpec();
        if (extract instanceof DictExNode) {
          DictExNode dictNode = (DictExNode) extract;

          String origFileName = orig.getContainingFileName();

          // Inline each of the dictionaries referenced in turn.
          int numDicts = dictNode.getNumDicts();
          for (int i = 0; i < numDicts; i++) {
            StringNode dictName = dictNode.getDictName(i);
            // Record the exception occured while inlining a dictionary from 'extract dictionaries'
            // list, and move on
            // with inlining of remaining dictionaries, this will help in identifying all the bad
            // dictionary references
            try {
              inlineDictFile(dictName.getStr(), catalog, dictName.getOrigTok(), origFileName);
            } catch (ParseException pe) {
              catalog.addCompilerException(pe);
            }
          }
        }
        return orig;
      }

    }

    // Use the callback to iterate over all the view definitions.
    extractCB ecb = new extractCB();
    ecb.catalog = catalog;
    for (CreateViewNode cvn : views) {
      try {
        rewriteExtracts(ecb, cvn.getBody(), cvn.getModuleName(), cvn.getUnqualifiedName());
      } catch (ParseException pe) {
        catalog.addCompilerException(pe);
      }
    }

    // Now do all of the scalar functions that reference dictionaries.
    final class funcCB extends funcRewriter {

      @Override
      ScalarFnCallNode rewrite(ScalarFnCallNode orig) {
        // Before re-writing, validate the function node; no need to record these errors, as they
        // are already captured
        // during parsing phase (specifically while populating catalog)
        List<ParseException> errors = orig.validate(catalog);
        if (errors.isEmpty()) {

          ArrayList<RValueNode> args = orig.getArgs();
          String funcName = orig.getFuncName();

          try {
            if (MatchesDict.FNAME.equals(funcName)) {
              // First argument of MatchesDict() is dictionary name.
              StringNode nameNode = (StringNode) args.get(0);
              catalog.addImplicitDict(orig.getModuleName(), nameNode.getStr(),
                  nameNode.getOrigTok(), nameNode.getContainingFileName());
              inlineDictFile(nameNode.getStr(), catalog, nameNode.getOrigTok(),
                  nameNode.getContainingFileName());
            } else if (ContainsDict.FNAME.equals(funcName)) {
              // First argument of ContainsDict() is dictionary name.
              StringNode nameNode = (StringNode) args.get(0);
              catalog.addImplicitDict(orig.getModuleName(), nameNode.getStr(),
                  nameNode.getOrigTok(), nameNode.getContainingFileName());
              inlineDictFile(nameNode.getStr(), catalog, nameNode.getOrigTok(),
                  nameNode.getContainingFileName());
            } else if (ContainsDicts.FNAME.equals(funcName)) {
              // First (n-2) arguments of ContainsDicts are dictionary
              // names
              for (int i = 0; i < args.size() - 2; i++) {
                StringNode nameNode = (StringNode) args.get(i);
                // Record the exception occured while inlining dictionary file pointed by current
                // argument, and move on
                // with inlining of remaining arguments, this will help in identifying all the bad
                // dictionary references
                try {
                  catalog.addImplicitDict(orig.getModuleName(), nameNode.getStr(),
                      nameNode.getOrigTok(), nameNode.getContainingFileName());
                  inlineDictFile(nameNode.getStr(), catalog, nameNode.getOrigTok(),
                      nameNode.getContainingFileName());
                } catch (ParseException pe) {
                  catalog.addCompilerException(pe);
                }
              }

              // Second-to-last argument could be either a boolean flag or
              // a dictionary name.
              RValueNode secondToLast = args.get(args.size() - 2);
              if (secondToLast instanceof StringNode) {
                StringNode nameNode = (StringNode) secondToLast;
                catalog.addImplicitDict(orig.getModuleName(), nameNode.getStr(),
                    nameNode.getOrigTok(), nameNode.getContainingFileName());
                inlineDictFile(nameNode.getStr(), catalog, nameNode.getOrigTok(),
                    nameNode.getContainingFileName());
              }
            }
          } catch (ParseException pe) {
            catalog.addCompilerException(pe);
          }
        }
        // This rewrite doesn't change the actual function calls.
        return orig;
      }

    }

    // Use the callback to iterate over all the view definitions.
    funcCB fcb = new funcCB();
    fcb.catalog = catalog;
    for (CreateViewNode cvn : views) {
      try {
        rewriteScalarFuncs(fcb, cvn);
      } catch (ParseException pe) {
        catalog.addCompilerException(pe);
      }
    }
  }

  /**
   * Subroutine of {@link #inlineExternalFiles(ArrayList, Catalog)} that handles a single dictionary
   * file.
   */
  private static void inlineDictFile(String name, final Catalog catalog, Token origTok,
      String origFileName) throws ParseException {
    final boolean debug = false;

    DictCatalogEntry catEntry = catalog.lookupDict(name, origTok, origFileName);

    // No need to inline imported dictionaries
    if (null != catEntry && false == catEntry.isImported()
        && catEntry instanceof DictCatalogEntry.OnDisk) {
      // Found a reference to an on-disk dictionary file.
      File file = ((DictCatalogEntry.OnDisk) catEntry).getDictFile();

      if (debug) {
        Log.debug("Inlining dictionary file " + "'%s'", file);
      }

      DictFile dict;
      try {
        dict = new DictFile(file);
      } catch (InvalidDictionaryFileFormatException idff) {
        throw ParseToCatalog.makeWrapperException(
            AQLParserBase.makeException(origTok, idff.getMessage()), origFileName);
      } catch (IOException e) {
        String message = e.getMessage();
        if (null == message) {
          // Exception didn't have a message
          message = e.toString();
        }

        if ("sun.io.MalformedInputException".equals(e.getClass().getName())) {
          // SPECIAL CASE: UTF-8 conversion error, on certain flavors
          // of Java
          throw ParseToCatalog.makeWrapperException(AQLParserBase.makeException(origTok,
              "Dictionary file '%s' contains a byte sequence that does not "
                  + "conform to the UTF-8 standard.  Convert the document to "
                  + "UTF-8 encoding.  The text editors vim and emacs can perform this conversion.",
              file, message), origFileName);

          // END SPECIAL CASE
        }

        throw ParseToCatalog.makeWrapperException(AQLParserBase.makeException(origTok,
            "Error reading dictionary file '%s': %s", file, message), origFileName);
      }

      // Create the parse tree node for the new
      // "create dictionary" statement we're generating.
      CreateDictNode.Inline rewritten =
          new CreateDictNode.Inline(catEntry.getOrigFileName(), catEntry.getOrigTok());
      rewritten.setEntries(dict.getEntries());
      rewritten.setModuleName(catEntry.getParseTreeNode().getModuleName());

      // Copy any dictionary parameters associated with the original
      // dictionary, changing the file-based dictionary to an inline one.
      DictParams newParams = new DictParams(catEntry.getParams());
      newParams.setFileName(null);
      newParams.setIsInline(true);
      rewritten.setParams(newParams);

      // Add the dictionary definition to the catalog.
      // We don't need to worry about order and
      // dependencies, since the catalog stores
      // dictionaries as a set.
      catalog.replaceDict(rewritten);
    }
  }

  protected static ArrayList<String> viewNamesList(ArrayList<CreateViewNode> views) {
    ArrayList<String> ret = new ArrayList<String>();
    for (CreateViewNode node : views) {
      ret.add(node.getViewName());
    }
    return ret;
  }

  /**
   * Rewrite the extract sequence pattern clauses.
   */
  protected void rewriteSequencePatterns(ArrayList<CreateViewNode> views, Catalog catalog)
      throws ParseException {

    final boolean debug = false;

    if (debug) {
      Log.debug("Before rewriting sequence patterns, views are: %s", viewNamesList(views));
    }

    // PHASE 1: Group Labeling
    for (CreateViewNode cvn : views) {
      labelGroups(cvn);
    }

    // PHASE 2: Label subexpressions with pass through attributes
    for (CreateViewNode cvn : views) {
      labelPassThroughCols(cvn, catalog);
    }

    // PHASE 3: Common Expression Detection
    class detectCommonSubExprCB extends patternExpressionRewriter {

      // Initialize the Common Sub-expression (CSE) Table
      HashMap<Integer, String> cseTable = new HashMap<Integer, String>();

      @Override
      public PatternExpressionNode rewrite(PatternExpressionNode orig) throws ParseException {

        Integer cseID;

        // Step 1: Process this node
        if (!orig.producesSameResultAsChild()) {

          // Reserve the next available cse ID for this node
          cseID = cseTable.size();
          orig.setCseID(cseID);
          cseTable.put(cseID, null);

          if (debug)
            Log.debug("Pattern %s: --- Set CSE id: %d\n", orig, cseID);
        }

        // Step 2: Process any child nodes
        if (orig instanceof PatternMultipleChildrenNode || orig instanceof PatternSingleChildNode)
          rewritePatternExpression(this, orig, curModuleName, curViewName);

        // Step 3: The rewrite happened in place, so just return the original node
        return orig;
      }
    }

    detectCommonSubExprCB csecb = new detectCommonSubExprCB();
    csecb.catalog = catalog;

    for (CreateViewNode cvn : views) {
      detectCSE(csecb, cvn);
    }

    // PHASE 4: Pattern Cleanup
    for (CreateViewNode cvn : views) {
      patternCleanup(cvn, catalog);
    }

    // PHASE 5: AQL Generation
    generateAqlCB gacb = new generateAqlCB();
    gacb.setCatalog(catalog);
    gacb.setCseTable(csecb.cseTable);

    for (CreateViewNode cvn : views) {
      generateAQL(gacb, cvn);
    }

    // Combine the new internal views and the existing views in a single collection.
    ArrayList<CreateViewNode> allViews = new ArrayList<CreateViewNode>();
    allViews.addAll(gacb.newInternalViews);
    allViews.addAll(views);

    // Make sure that the views are sorted according to their dependencies,
    // so that the compilation will proceed in the correct order.
    allViews = catalog.topologicalSort(allViews);

    views.clear();
    views.addAll(allViews);

    if (debug) {
      Log.debug("After rewriting sequence patterns, views are: %s", viewNamesList(views));
    }
  }

  /**
   * This method generates unique autoColumnName for each SelectListItemNode in SelectList.
   * 
   * @param views
   * @param catalog
   * @throws ParseException
   */
  void generateAutoColumnName(ArrayList<CreateViewNode> views, Catalog catalog)
      throws ParseException {

    // Callback to generate and update autoColumnName of a
    // SelectListItemNode
    final class cb extends selectExtractRewriter {

      @Override
      ViewBodyNode rewrite(ViewBodyNode orig) throws ParseException {

        SelectListNode selectList = null;
        if (orig instanceof SelectNode) {
          selectList = ((SelectNode) orig).getSelectList();
        } else if (orig instanceof ExtractNode) {
          selectList = ((ExtractNode) orig).getExtractList().getSelectList();
        }

        if (selectList != null) {
          int selectListSize = selectList.size();

          for (int i = 0; i < selectListSize; i++) {
            SelectListItemNode selectListItemNode = selectList.get(i);
            RValueNode value = selectListItemNode.getOrigValue();

            // Generating unique suffix for auto generated column
            // name in SelectListNode
            value.setAutoColumnName(this.curModuleName, this.curViewName,
                selectListItemNode.getAlias());
          }
        }

        // In-place update , no new Node object created
        return orig;
      }
    }

    cb callback = new cb();
    callback.catalog = catalog;

    for (CreateViewNode createViewNode : views) {
      rewriteSelectsAndExtracts(callback, createViewNode.getBody(), createViewNode.getModuleName(),
          createViewNode.getUnqualifiedName());
    }
  }

  /**
   * This method makes a pass over all the dictionaries in the catalog; and update the dictionary
   * matching language set to default language set: for the dictionaries declared without an
   * explicit 'with language clause', and all implicitly declared dictionaries. <br>
   * 
   * @param catalog catalog containing
   * @throws ParseException
   */
  private void updateDictMatchingLangSet(Catalog catalog) throws ParseException {
    ArrayList<CreateDictNode> createDictNodes = catalog.getCreateDictNodes();
    // Fetch the default dictionary language set from catalog
    String defaultLangString = catalog.getDefaultDictLangStr();

    // Iterate thru all the dictionaries in the catalog
    for (CreateDictNode createDictNode : createDictNodes) {
      DictParams params = createDictNode.getParams();

      // param.getLangStr() will null for:
      // 1)Implicitly declared dictionary: dictionaries referred thru file name
      // 2)Dictionaries declared without 'with language ...' clause
      // 3)Dictionaries create by sequence pattern re-write for string atom defined without explicit
      // [with language as
      // ...]
      if (null == params.getLangStr()) {
        // In memory update
        params.setLangStr(defaultLangString);
      }
    }
  }

  /**
   * Updates unqualified references to views, dictionaries and tables with their fully qualified
   * names
   * 
   * @param views List of views whose references should be qualified
   * @param catalog
   * @throws ParseException
   */
  private void qualifyReferences(ArrayList<CreateViewNode> views, Catalog catalog)
      throws ParseException {
    // Qualify references for each view
    for (CreateViewNode view : views) {
      view.qualifyReferences(catalog);
    }

    // Qualify the source table name, for dictionaries coming from table(internal/external)
    ArrayList<CreateDictNode> createDictNodes = catalog.getCreateDictNodes();
    for (CreateDictNode createDictNode : createDictNodes) {
      if (createDictNode instanceof CreateDictNode.FromTable) {
        createDictNode.qualifyReferences(catalog);
      }
    }

    // Qualify the target view in the detag statement, we allow referring a local views(views
    // belonging to the current
    // module) with their unqualified name.
    ArrayList<DetagCatalogEntry> detagCatalogEntries = catalog.getDetagCatalogEntries();
    for (DetagCatalogEntry detagCatalogEntry : detagCatalogEntries) {
      detagCatalogEntry.getParseTreeNode().qualifyReferences(catalog);
    }

    // Qualify jar file references in create function nodes.
    for (CreateFunctionNode funcNode : catalog.getCreateFunctionNodes()) {
      funcNode.qualifyJarRefs(catalog);
    }
  }

}
