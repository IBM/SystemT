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
package com.ibm.avatar.aql.tam;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.function.predicate.ContainsDict;
import com.ibm.avatar.algebra.function.predicate.ContainsDicts;
import com.ibm.avatar.algebra.function.predicate.MatchesDict;
import com.ibm.avatar.algebra.util.file.FileOperations;
import com.ibm.avatar.algebra.util.file.FileOperations.FileSystemType;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.AmbiguousPathRefException;
import com.ibm.avatar.api.exceptions.CircularDependencyException;
import com.ibm.avatar.api.exceptions.CircularIncludeDependencyException;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.DocSchemaMismatchException;
import com.ibm.avatar.api.exceptions.ModuleNotFoundException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.AQLParser;
import com.ibm.avatar.aql.AQLParserBase;
import com.ibm.avatar.aql.AbstractImportNode;
import com.ibm.avatar.aql.CreateDictNode;
import com.ibm.avatar.aql.CreateDictNode.FromFile;
import com.ibm.avatar.aql.CreateFunctionNode;
import com.ibm.avatar.aql.CreateViewNode;
import com.ibm.avatar.aql.DictExNode;
import com.ibm.avatar.aql.ExtendedParseException;
import com.ibm.avatar.aql.ExtractNode;
import com.ibm.avatar.aql.ExtractPatternNode;
import com.ibm.avatar.aql.ExtractionNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.FromListItemSubqueryNode;
import com.ibm.avatar.aql.FromListNode;
import com.ibm.avatar.aql.HavingClauseNode;
import com.ibm.avatar.aql.ImportModuleNode;
import com.ibm.avatar.aql.IncludeFileNode;
import com.ibm.avatar.aql.MinusNode;
import com.ibm.avatar.aql.NickNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.RValueNode;
import com.ibm.avatar.aql.ScalarFnCallNode;
import com.ibm.avatar.aql.SelectListItemNode;
import com.ibm.avatar.aql.SelectListNode;
import com.ibm.avatar.aql.SelectNode;
import com.ibm.avatar.aql.StatementList;
import com.ibm.avatar.aql.StringNode;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.aql.UnionAllNode;
import com.ibm.avatar.aql.ViewBodyNode;
import com.ibm.avatar.aql.WhereClauseNode;

/**
 * Several utility methods related to transforming v1.3 AQL to modular structure and helper
 * functions required to process modular AQLs
 * 
 */
public class ModuleUtils {
  /**
   * Creates a folder by name 'genericModule' and copy the entire tree of AQL files, dictionary
   * references and UDF references.
   * 
   * @param params Compiler parameters
   * @param aqlFilesMap Output parameter. Stores a map of temp file (under genericModule) Vs
   *        original AQL file. Used to translate file references in error messages from temp
   *        directory to original AQL file name.
   * @return The genericModule directory; the caller is responsible for deleting both this directory
   *         and the temporary directory above it.
   * @throws IOException
   * @throws CompilerException
   */
  public static File createGenericModule(CompileAQLParams params, Map<String, String> aqlFilesMap,
      ArrayList<Exception> errors) throws IOException, CompilerException {
    // Create a temporary directory under the system temp dir
    File tmpDir = File.createTempFile("moduleUtilsTmp", "");
    tmpDir.delete();
    tmpDir.mkdirs();

    File genModDir = createGenModDir(tmpDir, Constants.GENERIC_MODULE_NAME);

    File mainAQLFile = null;

    if (params.getInputStr() != null) {
      // User attempts to compile a string. So, write it into a temp aql file
      mainAQLFile = new File(tmpDir, "main.aql");
      FileUtils.strToFile(params.getInputStr(), mainAQLFile, "UTF-8");
    } else {
      mainAQLFile = params.getInputFile();
    }
    // recursively copy AQL files and their referenced include files and dicts
    String searchPath = params.getDataPath();
    ArrayList<ParseException> copyFileErrors = null;
    try {
      copyFileErrors = copyAQLFile(mainAQLFile, genModDir, searchPath, aqlFilesMap);

      // some of the 'include' statement related errors are caught only during copyAQLFile pass.
      // So, remember these errors.
      if (copyFileErrors.size() > 0) {
        errors.addAll(copyFileErrors);
      }
    } catch (ParseException e) {
      // do nothing. This error will be reported when compilation is invoked on 'genericModule'.
    }

    return genModDir;
  }

  /**
   * Copy all required files for compilation, starting from the main aql file, recursing down the
   * included file list, dict files and UDF jars.
   * 
   * @param aqlFile the main AQL file to copy.
   * @param genModDir genericModule directory.
   * @param datapath search path to locate include files, dictionaries and UDF jars.
   * @param aqlFilesMap a map from copies to the original files. Both key and value are canonical
   *        paths to files.
   * @return list of errors, if any
   * @throws IOException
   * @throws ParseException
   * @throws CompilerException
   */
  private static ArrayList<ParseException> copyAQLFile(File aqlFile, File genModDir,
      String datapath, Map<String, String> aqlFilesMap)
      throws IOException, ParseException, CompilerException {
    ArrayList<ParseException> errors = new ArrayList<ParseException>();

    ArrayList<File> includeFileList = new ArrayList<File>();
    ArrayList<String> dictFileRefs = new ArrayList<String>();
    ArrayList<String> udfJarRefs = new ArrayList<String>();

    // identify the components of the current AQL file. Many of the parameters are OUT parameters.
    // Refer to Java doc of
    // reapContents() method.
    ArrayList<ParseException> reapErrors =
        reapFileReferences(aqlFile, includeFileList, dictFileRefs, udfJarRefs, datapath);
    if (reapErrors.size() > 0) {
      errors.addAll(reapErrors);
    }

    // Step 1: If there are circular dependency errors, throw CompilerException
    if (errors.size() == 1 && errors.get(0) instanceof CircularIncludeDependencyException) {
      throw new CompilerException(errors, null);
    }

    // Step 2: Copy main AQL file
    File destAQLFile = new File(genModDir, aqlFile.getName());
    FileUtils.copyFile(aqlFile, destAQLFile);
    aqlFilesMap.put(destAQLFile.getCanonicalPath(), aqlFile.getCanonicalPath());

    // step 3: Copy included files
    for (File includedFile : includeFileList) {
      File destIncFile = new File(genModDir, includedFile.getName());

      // check if a file with same name is already copied to dest directory
      if (true == destIncFile.exists()) {

        errors.add(new ParseException(String.format(
            "The included AQL file %s can not be copied to 'genericModule' folder because a file with same name already exists. "
                + "Please note that the backward compability compile API copies all AQL files from non-modular folder structure to a single directory by name 'genericModule' "
                + "and hence no two include files with same name can exist in non-modular AQL. "
                + "To resolve this error, rename the included AQL files such that there are no duplicate AQL file names.",
            includedFile.getCanonicalPath())));
      }
      FileUtils.copyFile(includedFile.getAbsoluteFile(), destIncFile);
      aqlFilesMap.put(destIncFile.getCanonicalPath(), includedFile.getCanonicalPath());
    }

    SearchPath searchPath = new SearchPath(datapath);

    // step 4: copy dictionary files
    for (String strDictFile : dictFileRefs) {
      File srcDict = searchPath.resolve(strDictFile);
      File destDict = new File(genModDir, strDictFile);
      if (true == destDict.exists()) {
        errors.add(new ParseException(String.format(
            "The dictionary file %s can not be copied to 'genericModule' folder because a file with same name already exists. "
                + "Please note that the backward compatibility compile API copies all dictionary files from non-modular folder structure to a corresponding dictionary folder under 'genericModule' "
                + "and hence two dictionary files with same name and same directory structure relative to the search path can not exist. "
                + "To resolve this error, rename the dictionaries such that there are no duplicate dictionary file names.",
            srcDict.getCanonicalPath())));
      }

      FileUtils.copyFile(srcDict.getAbsoluteFile(), destDict);
    }

    // step 4: copy UDF jars
    for (String strUDF : udfJarRefs) {
      File srcUDF = searchPath.resolve(strUDF);
      File destUDF = new File(genModDir, strUDF);

      if (null == srcUDF) {
        // This problem should have been caught upstream.
        throw new RuntimeException(String.format("Couldn't find UDF jar %s in search path %s; "
            + "this problem should have been caught before the jar was added to the "
            + "list of files to copy", strUDF, searchPath));
      }

      if (true == destUDF.exists()) {
        errors.add(new ParseException(String.format(
            "The UDF jar file %s can not be copied to 'genericModule' folder because a file with same name already exists. "
                + "Please note that the backward compatibility compile API copies all UDF jar files from non-modular folder structure to a corresponding UDF jar folder under 'genericModule' "
                + "and hence two dictionary files with same name and same directory structure relative to the search path can not exist. "
                + "To resolve this error, rename the UDF jars such that there are no duplicate UDF jar file names.",
            srcUDF.getCanonicalPath())));
      }

      FileUtils.copyFile(srcUDF.getAbsoluteFile(), destUDF);
    }

    // step 5: Add module statements & remove "include" statements
    addModuleStmtAndRemoveIncludeStmts(genModDir);

    return errors;
  }

  /**
   * Adds module statement to the top of every AQL file under the genericModule directory. Also,
   * removes include statements from all AQL files.
   * 
   * @param genModDir Temp directory for genericModule
   */
  private static void addModuleStmtAndRemoveIncludeStmts(File genModDir) {
    File[] aqlFiles = genModDir.listFiles(new FilenameFilter() {

      @Override
      public boolean accept(File dir, String name) {
        return name.endsWith(Constants.AQL_FILE_EXTENSION);
      }
    });

    for (File file : aqlFiles) {
      try {
        StringBuilder buf = new StringBuilder();

        // add module statement at the beginning
        buf.append(String.format("module %s;\n\n", genModDir.getName()));

        String fileContents = FileUtils.fileToStr(file, Constants.ENCODING_UTF8);

        // Replace all include statements with empty string
        // We will do this in two passes. Two pass strategy is required because include statements
        // inside single
        // line comments are not properly replaced by REGEX #2

        // Pass #1: Replace all include statements within single line comment
        fileContents = fileContents.replaceAll("--[ \t]*include[ \t]*'[^']*'[ \t]*;*", "");

        // Pass #2: Replace all other forms of include statements, covering those that span multiple
        // lines
        // FIXME This regular expression does not in fact match multiple lines. One would see this
        // by looking at the
        // documentation for java.util.regex.Pattern. When the pattern is created with no flags, the
        // dot character
        // matches anything but new line. To make dot match new lines one must explicitly use the
        // DOTALL flags.
        // Furthermore, .* consumes ; Thus causing bug 29745: LA app fails with
        // java.lang.RuntimeException: Don't know
        // about output name 'Buzz_Output'. Options for fixing this problem properly involve not
        // using regexes to remove
        // include statements, and are listed in comment 25 of defect .

        // To address issue mentioned in defect (comment# 7), restricting the regular expression to
        // match only the
        // include
        // statement, including aql file.
        fileContents = fileContents.replaceAll("include\\s*'[^']*\\.aql'\\s*.*;*", "");

        buf.append(fileContents);

        FileUtils.strToFile(buf.toString(), file, Constants.ENCODING_UTF8);
      } catch (Exception e) {
        throw new RuntimeException("Error transforming AQLs to modular structure", e);
      }
    }

  }

  /**
   * Detects components of the AQL file and places them in various output parameters
   * 
   * @param aqlFile Source AQL file whose contents are to be reaped.
   * @param includeFileList Output parameter. Returns list of included file names.
   * @param dictFileRefs Output parameter. Returns list of dictionary file references.
   * @param udfJarRefs Output parameter. Returns list of UDF jar references.
   * @param searchPath Search path used to resolve included files, dictionaries and UDF jars
   * @throws IOException
   * @throws AmbiguousPathRefException
   */
  private static ArrayList<ParseException> reapFileReferences(File aqlFile,
      ArrayList<File> includeFileList, ArrayList<String> dictFileRefs, ArrayList<String> udfJarRefs,
      String searchPath) throws IOException {

    // List of ParseExceptions raised during reaping the contents of an AQL file
    ArrayList<ParseException> errors = new ArrayList<ParseException>();

    // Maintains the names of inline dicts and table based dicts. This list is helpful to identify
    // whether a dict
    // reference is for an inline/table based/file based dictionray.
    ArrayList<String> inlineAndTableDicts = new ArrayList<String>();

    try {
      // Parse the AQL file and fetch statement list
      AQLParser parser = new AQLParser(aqlFile);
      parser.setIncludePath(searchPath);
      parser.setBackwardCompatibilityMode(true);

      StatementList stmtList = parser.parse();

      // add parse errors to global error list
      if (stmtList.getParseErrors().size() > 0) {
        errors.addAll(stmtList.getParseErrors());
      }

      // Iterate over each parse tree node and fetch required details
      LinkedList<AQLParseTreeNode> nodes = stmtList.getParseTreeNodes();
      for (AQLParseTreeNode currNode : nodes) {
        reapFileRefsFromNode(currNode, errors, aqlFile, includeFileList, dictFileRefs, udfJarRefs,
            inlineAndTableDicts, new SearchPath(searchPath));
      }
    } // end: try
    catch (ParseException e) {
      // do nothing. This error will be reported when compilation is invoked on 'genericModule'.
    }

    return errors;
  }

  /**
   * Reaps references to dicts, UDF jars and included files from a given parse tree node
   * 
   * @param node The parse tree node whose file references are to be reaped
   * @param errors Output parameter. List of errors encountered during parsing.
   * @param aqlFile The file the parse tree node is a part of
   * @param includeFileList Output parameter. Included files in the given parse tree node, if any,
   *        are written out to this list
   * @param dictFileRefs Output parameter. Dictionary file references in the given parse tree node,
   *        if any, are written out to this list
   * @param udfJarRefs Output parameter. UDF jar file references in the given parse tree node, if
   *        any, are written out to this list
   * @param inlineAndTableDicts Output parameter. Names of Inline and Table based dictionaries
   *        encountered so far.
   * @param searchPath path used to resolve included files, dictionaries and UDF jars
   */
  private static void reapFileRefsFromNode(AQLParseTreeNode node, ArrayList<ParseException> errors,
      File aqlFile, ArrayList<File> includeFileList, ArrayList<String> dictFileRefs,
      ArrayList<String> udfJarRefs, ArrayList<String> inlineAndTableDicts, SearchPath searchPath) {
    if (node instanceof IncludeFileNode) {
      IncludeFileNode incNode = (IncludeFileNode) node;
      String inclFileName = incNode.getIncludedFileName().getStr();
      ArrayList<File> inclFiles = null;
      try {
        inclFiles = searchPath.resolveMulti(inclFileName, true);
      } catch (AmbiguousPathRefException e) {
        // do nothing. stmtList contains parse errors that are used to report this problem back to
        // the user
      }
      if (inclFiles != null && inclFiles.size() > 0) {
        for (File inclFile : inclFiles) {
          if (inclFile != null && inclFile.exists() == true) {
            includeFileList.add(inclFile);
          }
        }
      }
    } // end: IncludeFileNode
    else if (node instanceof CreateDictNode.FromTable || node instanceof CreateDictNode.Inline) {
      CreateDictNode dictNode = (CreateDictNode) node;
      inlineAndTableDicts.add(dictNode.getDictname());
    } // end: CreateDictNode.FromTable or CreateDictNode.Inline
    else if (node instanceof CreateDictNode.FromFile) {
      pickDictsInCreateDictNode((FromFile) node, dictFileRefs, searchPath, inlineAndTableDicts,
          errors, aqlFile);
    } // end: CreateDictNode.FromFile
    else if (node instanceof CreateViewNode) {
      CreateViewNode cvn = (CreateViewNode) node;
      pickDictsInViewBodyNode(cvn.getBody(), dictFileRefs, searchPath, inlineAndTableDicts, errors,
          aqlFile);

    } // end: CreateViewNode
    else if (node instanceof CreateFunctionNode) {
      CreateFunctionNode cfn = (CreateFunctionNode) node;
      if (false == udfJarRefs.contains(cfn.getJarName())) {
        // Make sure that the copy operation in the downstream code will succeed
        String jarNameStr = cfn.getJarName();
        File srcFile = searchPath.resolve(jarNameStr);
        if (null == srcFile) {
          ParseException pe = AQLParser.makeException(cfn.getOrigTok(),
              "Jar file %s not found in search path %s", jarNameStr, searchPath);
          errors.add(new ExtendedParseException(pe, aqlFile));
        } else {
          udfJarRefs.add(jarNameStr);
        }
      }
    } // end: CreateFunctionNode
  }

  /**
   * Looks for references to dictionaries within the given view body node.
   * 
   * @param viewBodyNode The node where dictionary references are to be picked up from
   * @param dictFileRefs Output parameter. Returns dictionary file references
   * @param searchPath Search path where dictionaries are to be located.
   * @param inlineAndTableDicts A list of inline dictionaries and table based dictionaries
   * @param errors Output parameter. List of errors.
   * @param aqlFile The file where dictionary references are to be reaped from
   */
  private static void pickDictsInViewBodyNode(ViewBodyNode viewBodyNode,
      ArrayList<String> dictFileRefs, SearchPath searchPath, ArrayList<String> inlineAndTableDicts,
      ArrayList<ParseException> errors, File aqlFile) {
    if (viewBodyNode instanceof ExtractNode) {
      ExtractNode extNode = (ExtractNode) viewBodyNode;
      pickDictsInExtractNode(extNode, dictFileRefs, searchPath, inlineAndTableDicts, errors,
          aqlFile);
    } // end: ExtractNode
    else if (viewBodyNode instanceof SelectNode) {
      SelectNode selNode = (SelectNode) viewBodyNode;
      pickDictsInSelectNode(selNode, dictFileRefs, searchPath, inlineAndTableDicts, errors,
          aqlFile);
    } // end: SelectNode
    else if (viewBodyNode instanceof ExtractPatternNode) {
      ExtractPatternNode patternNode = (ExtractPatternNode) viewBodyNode;
      pickDictsInHavingClauseNode(patternNode.getHavingClause(), dictFileRefs, searchPath,
          inlineAndTableDicts, errors, aqlFile);
      pickDictsInFromListNode(patternNode.getFromList(), dictFileRefs, searchPath,
          inlineAndTableDicts, errors, aqlFile);
      pickDictsInSelectListNode(patternNode.getSelectList(), dictFileRefs, searchPath,
          inlineAndTableDicts, errors, aqlFile);
    } // end: PatternNode
    else if (viewBodyNode instanceof UnionAllNode) {
      UnionAllNode unionNode = (UnionAllNode) viewBodyNode;
      for (int i = 0; i < unionNode.getNumStmts(); ++i) {
        ViewBodyNode vbn = unionNode.getStmt(i);
        pickDictsInViewBodyNode(vbn, dictFileRefs, searchPath, inlineAndTableDicts, errors,
            aqlFile);
        // if (vbn instanceof SelectNode) {
        // pickDictsInFromListNode (((SelectNode) vbn).getFromList (), dictFileRefs, searchPath,
        // inlineAndTableDicts,
        // errors, aqlFile);
        // }
        // else if (vbn instanceof ExtractNode) {
        // pickDictsInExtractNode ((ExtractNode) vbn, dictFileRefs, searchPath, inlineAndTableDicts,
        // errors, aqlFile);
        // }
      }
    } // end: UnionAllNode
    else if (viewBodyNode instanceof MinusNode) {
      MinusNode minusNode = (MinusNode) viewBodyNode;
      ViewBodyNode firstStmt = minusNode.getFirstStmt();
      ViewBodyNode secondStmt = minusNode.getSecondStmt();

      pickDictsInViewBodyNode(firstStmt, dictFileRefs, searchPath, inlineAndTableDicts, errors,
          aqlFile);
      pickDictsInViewBodyNode(secondStmt, dictFileRefs, searchPath, inlineAndTableDicts, errors,
          aqlFile);

    } // end: MinusNode
  }

  /**
   * picks dictionaries from select node
   */
  private static void pickDictsInSelectNode(SelectNode selNode, ArrayList<String> dictFileRefs,
      SearchPath searchPath, ArrayList<String> inlineAndTableDicts,
      ArrayList<ParseException> errors, File aqlFile) {
    pickDictsInFromListNode(selNode.getFromList(), dictFileRefs, searchPath, inlineAndTableDicts,
        errors, aqlFile);

    pickDictsInWhereClause(selNode.getWhereClause(), dictFileRefs, searchPath, inlineAndTableDicts,
        errors, aqlFile);

    FromListNode fromListNode = selNode.getFromList();
    ArrayList<FromListItemNode> items = fromListNode.getItems();
    for (FromListItemNode fromListItemNode : items) {
      pickDictsInFromListItemNode(fromListItemNode, dictFileRefs, searchPath, inlineAndTableDicts,
          errors, aqlFile);
    }
  }

  /**
   * picks dictionaries from where clause
   */
  private static void pickDictsInWhereClause(WhereClauseNode whereNode,
      ArrayList<String> dictFileRefs, SearchPath searchPath, ArrayList<String> inlineAndTableDicts,
      ArrayList<ParseException> errors, File aqlFile) {
    // WhereClauseNode whereNode = selNode.getWhereClause ();
    if (whereNode == null) {
      return;
    }

    ArrayList<PredicateNode> predNodes = whereNode.getPreds();
    if (predNodes == null || predNodes.size() == 0) {
      return;
    }
    for (PredicateNode predNode : predNodes) {
      pickDictsInFuncNode(predNode.getFunc(), dictFileRefs, searchPath, inlineAndTableDicts, errors,
          aqlFile);
    }

  }

  /**
   * picks dictionaries from function node
   */
  private static void pickDictsInFuncNode(ScalarFnCallNode funcNode, ArrayList<String> dictFileRefs,
      SearchPath searchPath, ArrayList<String> inlineAndTableDicts,
      ArrayList<ParseException> errors, File aqlFile) {
    String functionName = funcNode.getFuncName();

    ArrayList<RValueNode> args = funcNode.getArgs();

    if (null == args)
      return;

    boolean isContainsDictOrMatchesDictOrContainsDicts = false;

    // Special case: ContainsDict and MatchesDict - Arguments to these functions are dictionary
    // references
    if (ContainsDict.FNAME.equals(functionName) || MatchesDict.FNAME.equals(functionName)) {
      isContainsDictOrMatchesDictOrContainsDicts = true;

      try {
        // First argument of MatchesDict() and ContainsDict() function is dictionary name
        StringNode dictNameNode = (StringNode) args.get(0);
        String dictName = dictNameNode.getStr();
        resolveAndAddDict(args.get(0), dictName, searchPath, dictFileRefs, inlineAndTableDicts);
      } catch (AmbiguousPathRefException e) {
        // do nothing. This error will be reported when compilation is invoked on 'genericModule'.
      } catch (ParseException pe) {
        // do nothing. This error will be reported when compilation is invoked on 'genericModule'.
      } catch (IndexOutOfBoundsException ioobe) {
        // do nothing. This error will be reported when compilation is invoked on 'genericModule'.
      }

    }

    // Special case: ContainsDicts. First n-2 args are dictionary refs. Second to last could be a
    // dict ref.
    if (ContainsDicts.FNAME.equals(functionName)) {
      isContainsDictOrMatchesDictOrContainsDicts = true;

      // First (n-2) arguments of ContainsDicts are dictionary references

      try {
        for (int i = 0; i < args.size() - 2; ++i) {
          RValueNode arg = args.get(i);
          StringNode dictNameNode = (StringNode) args.get(i);
          String dictName = dictNameNode.getStr();
          resolveAndAddDict(arg, dictName, searchPath, dictFileRefs, inlineAndTableDicts);
        }
      } catch (AmbiguousPathRefException e) {
        // do nothing. This error will be reported when compilation is invoked on 'genericModule'.
      } catch (ParseException pe) {
        // do nothing. This error will be reported when compilation is invoked on 'genericModule'.
      } catch (IndexOutOfBoundsException ioobe) {
        // do nothing. This error will be reported when compilation is invoked on 'genericModule'.
      }

      // Second-to-last argument could be either a boolean flag or
      // a dictionary name.
      try {
        RValueNode secondToLast = args.get(args.size() - 2);
        if (secondToLast instanceof StringNode) {
          StringNode dictNameNode = (StringNode) secondToLast;
          resolveAndAddDict(secondToLast, dictNameNode.getStr(), searchPath, dictFileRefs,
              inlineAndTableDicts);
        }
      } catch (AmbiguousPathRefException e) {
        // do nothing. This error will be reported when compilation is invoked on 'genericModule'.
      } catch (ParseException pe) {
        // do nothing. This error will be reported when compilation is invoked on 'genericModule'.
      } catch (ArrayIndexOutOfBoundsException aie) {
        // do nothing. This error will be reported when compilation is invoked on 'genericModule'.
      }
    }

    // check if the current function node refers to another function and evalute recursively.
    // This case happens when the selection predicate looks something like:
    // "where NOT(ContainsDict('firstName.dict', x.y))"
    if (false == isContainsDictOrMatchesDictOrContainsDicts) {
      for (RValueNode arg : args) {
        if (arg != null && arg instanceof ScalarFnCallNode) {
          pickDictsInFuncNode((ScalarFnCallNode) arg, dictFileRefs, searchPath, inlineAndTableDicts,
              errors, aqlFile);
        }
      }
    }
  }

  /**
   * picks dictionaries from FromListNode
   */
  private static void pickDictsInFromListNode(FromListNode fromList, ArrayList<String> dictFileRefs,
      SearchPath searchPath, ArrayList<String> inlineAndTableDicts,
      ArrayList<ParseException> errors, File aqlFile) {
    // FromListNode fromList = selNode.getFromList ();
    ArrayList<FromListItemNode> fromListItems = fromList.getItems();
    for (FromListItemNode fromListItemNode : fromListItems) {
      if (fromListItemNode instanceof FromListItemSubqueryNode) {
        ViewBodyNode body = ((FromListItemSubqueryNode) fromListItemNode).getBody();
        pickDictsInViewBodyNode(body, dictFileRefs, searchPath, inlineAndTableDicts, errors,
            aqlFile);
        // if (body instanceof ExtractNode) {
        // pickDictsInExtractNode ((ExtractNode) body, dictFileRefs, searchPath,
        // inlineAndTableDicts, errors, aqlFile);
        // }
      }
    } // end: for each fromListItemNode
  }

  /**
   * Picks all dict file references from Create Dictionary statement.
   * 
   * @param dictNode The Create Dictionary node
   * @param dictFileRefs Output parameter.
   * @param searchPath The path used to resolve dict file references.
   * @param inlineAndTableDicts list of all dictionaries currently known to be either defined inline
   *        or based off a table
   * @param aqlFile AQL file currently being processed (for error reporting)
   * @param errors list of errors encountered so far in compilation
   */
  private static void pickDictsInCreateDictNode(CreateDictNode.FromFile dictNode,
      ArrayList<String> dictFileRefs, SearchPath searchPath, ArrayList<String> inlineAndTableDicts,
      ArrayList<ParseException> errors, File aqlFile) {
    String dictFile = dictNode.getParams().getFileName();
    try {
      resolveAndAddDict(dictNode, dictFile, searchPath, dictFileRefs, inlineAndTableDicts);
    }
    // Exceptions thrown by the above method should be reported back to the user
    catch (AmbiguousPathRefException e) {
      // do nothing. This error will be reported when compilation is invoked on 'genericModule'.
    } catch (ParseException pe) {
      // do nothing. This error will be reported when compilation is invoked on 'genericModule'.
    }

  }

  /**
   * Searches for the dictName in search path and adds to dictFileRefs output parameter
   */
  private static void resolveAndAddDict(AQLParseTreeNode dictRefNode, String dictName,
      SearchPath searchPath, ArrayList<String> dictFileRefs, ArrayList<String> inlineAndTableDicts)
      throws ParseException, AmbiguousPathRefException {
    if (inlineAndTableDicts.contains(dictName)) {
      // This is a reference to inline dict or a table based dict. So, just return, as there's
      // nothing to resolve.
      return;
    }

    // resolve the dictName in search path and add resolved entry to dictFileRefs output parameter
    ArrayList<File> multipleFilesOnDisk = searchPath.resolveMulti(dictName, true);
    if (multipleFilesOnDisk.size() == 0) {
      // do not bother about missing dictionaries for now. It is compiler's responsibility to catch
      // this error.
    } else if (multipleFilesOnDisk.size() == 1) {
      if (false == dictFileRefs.contains(dictName)) {

        dictFileRefs.add(dictName);
      }
    } else {
      // more than one dictionary file match found. Throw an error.

      // prepare a token first.
      Token token = null;
      if (dictRefNode instanceof DictExNode) {
        DictExNode dictExNode = (DictExNode) dictRefNode;
        token = getDictToken(dictExNode, dictName);
      } else if (dictRefNode instanceof CreateDictNode.FromFile) {
        token = ((CreateDictNode.FromFile) dictRefNode).getOrigTok();
      } else if (dictRefNode instanceof RValueNode) {
        token = ((RValueNode) dictRefNode).getOrigTok();
      }

      // Prepare a list of dictionary file paths for the list of matches found
      ArrayList<String> dictFilePaths = new ArrayList<String>();
      for (File dictFile : multipleFilesOnDisk) {
        dictFilePaths.add(dictFile.getAbsolutePath());
      }

      // prepare an exception & throw
      throw AQLParserBase.makeException(token,
          "More than one dictionary file '%s' found in dictionary path '%s'. The matches are: %s",
          dictName, searchPath, dictFilePaths.toString());
    }

  }

  /**
   * Helper method to retrieve dictionary token from DictExNode
   */
  private static Token getDictToken(DictExNode dictExNode, String dictName) {
    int ix = 0;
    for (ix = 0; ix < dictExNode.getNumDicts(); ++ix) {
      if (dictName.equals(dictExNode.getDictName(ix).getStr())) {
        break;
      }
    }
    return dictExNode.getDictName(ix).getOrigTok();
  }

  /**
   * Traverses the extract node and picks all dictionary references.
   * 
   * @param extNode Extract node to traverse.
   * @param dictFileRefs Output parameter.
   * @param searchPath Path used to resolve dictionary references.
   * @param inlineAndTableDicts
   * @param numLines
   * @param ix
   * @param errors
   */
  private static void pickDictsInExtractNode(ExtractNode extNode, ArrayList<String> dictFileRefs,
      SearchPath searchPath, ArrayList<String> inlineAndTableDicts,
      ArrayList<ParseException> errors, File aqlFile) {
    // Pass 1: pick dicts from select list
    SelectListNode selectListNode = extNode.getExtractList().getSelectList();
    pickDictsInSelectListNode(selectListNode, dictFileRefs, searchPath, inlineAndTableDicts, errors,
        aqlFile);

    // Pass 2: pick dicts from extraction node
    ExtractionNode extractionNode = extNode.getExtractList().getExtractSpec();
    pickDictsInExtractionNode(extractionNode, dictFileRefs, searchPath, inlineAndTableDicts, errors,
        aqlFile);

    // Pass 3: pick dicts from FromListItemNode (i.e target)
    FromListItemNode fromListItemNode = extNode.getTarget();
    pickDictsInFromListItemNode(fromListItemNode, dictFileRefs, searchPath, inlineAndTableDicts,
        errors, aqlFile);

    // Pass 4: pick dicts from having clause
    HavingClauseNode havingClauseNode = extNode.getHavingClause();
    pickDictsInHavingClauseNode(havingClauseNode, dictFileRefs, searchPath, inlineAndTableDicts,
        errors, aqlFile);
  }

  private static void pickDictsInExtractionNode(ExtractionNode extractionNode,
      ArrayList<String> dictFileRefs, SearchPath searchPath, ArrayList<String> inlineAndTableDicts,
      ArrayList<ParseException> errors, File aqlFile) {
    if (extractionNode instanceof DictExNode) {
      DictExNode dictExNode = (DictExNode) extractionNode;
      int numDicts = dictExNode.getNumDicts();
      for (int i = 0; i < numDicts; ++i) {
        String dictName = dictExNode.getDictName(i).getStr();
        // dict could be a file or a reference to a dictionary object. Resolve it by checking it's
        // existence
        // on file system
        try {
          resolveAndAddDict(dictExNode, dictName, searchPath, dictFileRefs, inlineAndTableDicts);
        } catch (AmbiguousPathRefException e) {
          // do nothing. This error will be reported when compilation is invoked on 'genericModule'.
        } catch (ParseException pe) {
          // do nothing. This error will be reported when compilation is invoked on 'genericModule'.
        }
      }
    }

  }

  /**
   * picks dictionaries from SelectListNode
   */
  private static void pickDictsInSelectListNode(SelectListNode selectListNode,
      ArrayList<String> dictFileRefs, SearchPath searchPath, ArrayList<String> inlineAndTableDicts,
      ArrayList<ParseException> errors, File aqlFile) {
    if (selectListNode == null) {
      return;
    }

    for (int i = 0; i < selectListNode.size(); ++i) {
      SelectListItemNode selListItemNode = selectListNode.get(i);

      try {
        RValueNode rvalNode = selListItemNode.getValue();
        if (rvalNode instanceof ScalarFnCallNode) {
          pickDictsInFuncNode((ScalarFnCallNode) rvalNode, dictFileRefs, searchPath,
              inlineAndTableDicts, errors, aqlFile);
        }
      } catch (ParseException e) {
        // do nothing. This error will be reported when compilation is invoked on 'genericModule'.
      }
    }
  }

  /**
   * picks dictionaries from HavingClauseNode
   */
  private static void pickDictsInHavingClauseNode(HavingClauseNode havingClauseNode,
      ArrayList<String> dictFileRefs, SearchPath searchPath, ArrayList<String> inlineAndTableDicts,
      ArrayList<ParseException> errors, File aqlFile) {
    if (havingClauseNode == null) {
      return;
    }
    ArrayList<PredicateNode> preds = havingClauseNode.getPreds();
    if (preds == null || preds.size() == 0) {
      return;
    }
    for (PredicateNode predNode : preds) {
      pickDictsInFuncNode(predNode.getFunc(), dictFileRefs, searchPath, inlineAndTableDicts, errors,
          aqlFile);
    }

  }

  /**
   * picks dictionaries from FromListItemNode
   */
  private static void pickDictsInFromListItemNode(FromListItemNode fromListItemNode,
      ArrayList<String> dictFileRefs, SearchPath searchPath, ArrayList<String> inlineAndTableDicts,
      ArrayList<ParseException> errors, File aqlFile) {
    if (false == (fromListItemNode instanceof FromListItemSubqueryNode)) {
      // We are not interested in any other form of FromListItemNode
      return;
    }

    FromListItemSubqueryNode subqueryNode = (FromListItemSubqueryNode) fromListItemNode;
    ViewBodyNode vbn = subqueryNode.getBody();
    pickDictsInViewBodyNode(vbn, dictFileRefs, searchPath, inlineAndTableDicts, errors, aqlFile);
  }

  /**
   * Creates directory for genericModule under <USER_TEMP_DIR>/moduleUtilsTmpNNNN
   * 
   * @param tmpDir <USER_TEMP_DIR>/moduleUtilsTmpNNNN
   * @param moduleName directory to create under tmpDir
   * @return descriptor for the module directory
   */
  private static File createGenModDir(File tmpDir, String moduleName) {
    if (!tmpDir.exists()) {
      tmpDir.mkdirs();
    }

    File ret = new File(tmpDir, moduleName);
    if (ret.exists()) {
      FileUtils.deleteContents(ret);
    } else {
      ret.mkdir();
    }
    return ret;
  }

  /**
   * Returns the module name part of the element name. Assumes that the string before the first
   * 'period' is module name and the rest of the string is element name. If there is no 'period'
   * because it's unqalified, returns the empty string.
   * 
   * @param elementName element name of the format &lt;moduleName&gt;.&lt;elementName&gt;
   * @return module name component of the qualified element name or "" if unqualified
   */
  public static String getModuleName(String elementName) {
    if (elementName == null || elementName.trim().length() == 0) {
      return elementName;
    }

    int index = elementName.indexOf(Constants.MODULE_ELEMENT_SEPARATOR);
    if (index < 0) {
      return "";
    }
    return elementName.substring(0, index);
  }

  /**
   * Returns the element name component of the fully qualified name.Assumes that the string before
   * the first 'period' is module name and the rest of the string is element name.
   * 
   * @param qualifiedElementName Fully qualified element name of the format
   *        &lt;moduleName&gt;.&lt;elementName&gt;
   * @return element name portion of the qualified element name
   */
  public static String getUnqualifiedElementName(String qualifiedElementName) {
    int index = qualifiedElementName.indexOf(Constants.MODULE_ELEMENT_SEPARATOR);
    if (index < 0) {
      return qualifiedElementName;
    } else {
      return qualifiedElementName.substring(index + 1);
    }
  }

  /**
   * Converts the given element name to a qualified one by adding module prefix.
   * 
   * @param moduleName
   * @param elemName
   * @return qualified element name
   */
  public static String prepareQualifiedName(String moduleName, String elemName) {
    if (elemName == null || elemName.trim().length() == 0) {
      return elemName;
    }

    String qualifiedName = elemName;
    if (moduleName != null && moduleName.trim().length() != 0) {
      String modulePrefix = String.format("%s%c", moduleName, Constants.MODULE_ELEMENT_SEPARATOR);
      if (false == Constants.GENERIC_MODULE_NAME.equals(moduleName)
          && false == elemName.startsWith(modulePrefix)) {
        qualifiedName =
            String.format("%s%c%s", moduleName, Constants.MODULE_ELEMENT_SEPARATOR, elemName);
      }
    }
    return qualifiedName;
  }

  /**
   * Reads the current JAR entry from the JarInputStream
   * 
   * @param jis the jar input stream from where current JAR entry is to be read
   * @return the contents of the current jar entry as a byte array.
   * @throws IOException
   */
  public static byte[] readCurrJarEntry(JarInputStream jis) throws IOException {
    return readCurrEntry(jis);
  }

  /**
   * Reads the current entry from the InputStream
   * 
   * @param is the input stream from where current entry is to be read
   * @return the contents of the current entry as a byte array.
   * @throws IOException
   */
  public static byte[] readCurrEntry(InputStream is) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] b = new byte[8192];
    int len = 0;

    while ((len = is.read(b)) > 0) {
      baos.write(b, 0, len);
    }

    byte[] ret = baos.toByteArray();
    baos.close();
    return ret;
  }

  /**
   * Reads the specified TAM file from the given JAR and returns an InputStream to the TAM file, if
   * the file is found under the given jar.
   * 
   * @param fileOps File System specific FileOperations instance
   * @param jarURI Absolute URI of the JAR where the TAM file is to be found
   * @param tamFileName Name of the TAM file to find
   * @return InputStream to the tam file, if found
   * @throws Exception
   */
  public static InputStream readTAMFromArchive(String jarURI, String tamFileName) throws Exception {
    // Iterate through every JAR entry and fetch an input stream for .tam file
    JarInputStream jis = new JarInputStream(FileOperations.getStream(jarURI));
    JarEntry entry = null;

    while ((entry = jis.getNextJarEntry()) != null) {
      if (entry.getName().equals(tamFileName)) {
        byte[] tamContents = ModuleUtils.readCurrJarEntry(jis);
        jis.close();
        return new ByteArrayInputStream(tamContents);
      }
    }

    // Specified .tam file is not found in the given archive
    jis.close();
    throw new Exception(
        String.format("TAM file %s is not found in archive %s", tamFileName, jarURI));
  }

  /**
   * Reads the specified TAM file from the given JAR and returns an InputStream to the TAM file, if
   * the file is found under the given jar.
   * 
   * @param fileOps File System specific FileOperations instance
   * @param jarURI Absolute URI of the JAR where the TAM file is to be found
   * @param tamFileName Name of the TAM file to find
   * @return InputStream to the tam file, if found
   * @throws Exception
   */
  public static InputStream readTAMFromArchive(URI jarURI, String tamFileName) throws Exception {
    FileSystemType fileOps = FileOperations.getFileSystemType(jarURI);
    // check if the file operation is local file system.
    if (false == fileOps.equals(FileSystemType.LOCAL_FS)) {
      return readTAMFromArchive(jarURI.toString(), tamFileName);
    }

    JarFile jarFile = new JarFile(jarURI.getPath());
    JarEntry entry = jarFile.getJarEntry(tamFileName);

    if (entry != null) {
      InputStream is = jarFile.getInputStream(entry);
      byte[] tamContents = ModuleUtils.readCurrEntry(is);
      is.close();
      jarFile.close();
      return new ByteArrayInputStream(tamContents);
    }

    // Specified .tam file is not found in the given archive
    jarFile.close();
    throw new Exception(
        String.format("TAM file %s is not found in archive %s", tamFileName, jarURI));
  }

  /**
   * Reads the specified TAM file from the given JAR and returns an InputStream to the TAM file, if
   * the file is found under the given jar. Otherwise, returns null.
   *
   * @param fileOps File System specific FileOperations instance
   * @param jarURI Absolute URI of the JAR where the TAM file is to be found
   * @param tamFileName Name of the TAM file to find
   * @return InputStream to the tam file, if found
   * @throws Exception
   */
  public static InputStream readTAMFromArchiveIfContains(String jarURI, String tamFileName)
      throws Exception {
    // Iterate through every JAR entry and fetch an input stream for .tam file
    JarInputStream jis = new JarInputStream(FileOperations.getStream(jarURI));
    JarEntry entry = null;

    while ((entry = jis.getNextJarEntry()) != null) {
      if (entry.getName().equals(tamFileName)) {
        byte[] tamContents = ModuleUtils.readCurrJarEntry(jis);
        jis.close();
        return new ByteArrayInputStream(tamContents);
      }
    }

    // Specified .tam file is not found in the given archive
    jis.close();
    return null;
  }

  /**
   * Checks whether the given module name is a genericModule (or) unqualified (i.e no module name)
   * 
   * @param moduleName
   * @return true, if moduleName is null, empty or matches the string 'genericModule'
   */
  public static boolean isGenericModule(String moduleName) {
    return (null == moduleName) || (0 == moduleName.trim().length())
        || (Constants.GENERIC_MODULE_NAME.equals(moduleName));
  }

  /**
   * Converts the given path to URI format. Generates only file: URIs
   * 
   * @param pathList A list of paths on local file system separated by OS specific path separator.
   *        This string might contain some paths that are already converted to URI. So, the method
   *        should handle a mix of both URIs and absolute paths on local file system.
   * @return A list of file: URIs separated by semi-colon ";"
   */
  public static String convertToURI(String pathList) {
    StringBuilder buf = new StringBuilder();
    String[] paths = pathList.split(Constants.OS_PATH_SEPARATOR);
    for (String path : paths) {
      if (path.startsWith(Constants.LOCALFS_URI_SCHEME)) {
        buf.append(path);
      } else {
        buf.append(FileUtils.createValidatedFile(path).getAbsoluteFile().toURI().toString());
      }
      buf.append(Constants.MODULEPATH_SEP_CHAR);
    }

    return buf.toString();
  }

  /**
   * Checks whether the given JAR/ZIP archive contains an entry with the specified file name
   * 
   * @param fileOps FileOperations object for Local File system or HDFS
   * @param archiveURI URI of the JAR / ZIP file within which an entry for fileName is to be
   *        searched for.
   * @param fileName Name of the file to search within the archive
   * @return true, if the an entry with given fileName is found within the archive, false otherwise.
   * @throws IOException
   * @throws Exception
   */
  public static boolean archiveContainsFile(String archiveURI, String fileName)
      throws IOException, Exception {
    // Iterate through every JAR entry and fetch an input stream for .tam file
    JarInputStream jis = new JarInputStream(FileOperations.getStream(archiveURI));
    JarEntry entry = null;

    while ((entry = jis.getNextJarEntry()) != null) {
      if (entry.getName().equals(fileName)) {
        jis.close();
        return true;
      }
    }
    // if control reaches here, then an entry is not found. Return false.
    jis.close();
    return false;
  }

  /**
   * Checks whether the given JAR/ZIP archive contains an entry with the specified file name
   * 
   * @param archiveURI URI of the JAR / ZIP file within which an entry for fileName is to be
   *        searched for.
   * @param fileName Name of the file to search within the archive
   * @return true, if the an entry with given fileName is found within the archive, false otherwise.
   * @throws IOException
   * @throws Exception
   */
  public static boolean archiveContainsFile(URI archiveURI, String fileName)
      throws IOException, Exception {
    FileSystemType fileOps = FileOperations.getFileSystemType(archiveURI);
    // check if the file operation is local file system.
    // if not on local file system, we read sequentially from the jar stream.
    if (false == fileOps.equals(FileSystemType.LOCAL_FS)) {
      return archiveContainsFile(archiveURI.toString(), fileName);
    }

    // on local file system, so access the entry using random access
    JarFile jarFile = new JarFile(archiveURI.getPath());
    JarEntry entry = jarFile.getJarEntry(fileName);
    boolean ret = entry != null;
    jarFile.close();
    return ret;
  }

  /**
   * Determines the correct order of compilation among the given set of modules to compile.
   * 
   * @param inputModules list of modules to compile
   * @param modulePath a semicolon-separated list of URIs that refer to absolute paths of locations
   *        where dependent modules can be located
   * @param ce compiler exception object to record all the errors encountered while determining
   *        compilation order of input modules
   * @return An array of module source URIs in the proper order of compilation
   * @throws Exception
   */
  public static String[] prepareCompileOrder(String[] inputModulesURI, String modulePath,
      CompilerException ce) {
    // BEGIN: SPECIAL CASE
    // simply return the input, if zero or one module is passed
    if (inputModulesURI == null || inputModulesURI.length == 0 || inputModulesURI.length == 1) {
      return inputModulesURI;
    }
    // END: SPECIAL CASE

    /**
     * Map of modules to compile(source modules) vs its dependent modules. Key is: Name of the
     * module to compile, Value is: Map of dependent module name and the list of parse tree nodes of
     * import statements, importing the dependent module. List of parse tree nodes of import
     * statement are used while error reporting, we extract error location from them, refer
     * {@link ModuleUtils#makeCircularDependencyErrorMessage(List, Map)} for more details<br>
     * Note: A module can be imported in different AQL files of the same module.
     */
    SortedMap<String, SortedMap<String, SortedSet<AQLParseTreeNode>>> dependencies =
        new TreeMap<String, SortedMap<String, SortedSet<AQLParseTreeNode>>>();

    /**
     * Map of moduleName Vs moduleSrcURI
     */
    SortedMap<String, String> module2SrcURIMap = new TreeMap<String, String>();

    // Pass 1: Get list of all dependents for each module
    for (String moduleSrcURI : inputModulesURI) {
      String moduleName = getModuleNameFromURI(moduleSrcURI);

      // find dependents
      try {
        SortedMap<String, SortedSet<AQLParseTreeNode>> dependsOn =
            getDependentModules(moduleSrcURI);
        // Source modules with URI pointing to the local file system directory are considered in the
        // following pass to
        // deduce compilation order; for other URIs (hdfs://) we report error

        // store moduleName Vs source URI
        module2SrcURIMap.put(moduleName, moduleSrcURI);
        dependencies.put(moduleName, dependsOn);
      } catch (Exception e) {
        ce.addError(e);
      }
    }

    // Pass 2: Remove non-existent(either as a source module, or in the module path) module from the
    // source module's
    // dependent list
    removeModulesWithoutSource(dependencies, modulePath, ce);

    // Pass 3: Identify all the cycles in the graph and break them
    identifyReportAndBreakCycles(dependencies, ce);

    // Pass 4: determine compilation order. Use toplogical sort algorithm
    ArrayList<String> compilationOrder = toplogicallySort(module2SrcURIMap, dependencies);
    return compilationOrder.toArray(new String[0]);
  }

  /**
   * This method removes the all the dependents of the module, which are neither found in the list
   * of source modules nor found in the specified module path. In addition, this method reports
   * error for all such non-existent modules.
   * 
   * @param dependencies map of input modules and its dependent modules (with error location info)
   * @param modulePath a semicolon-separated list of URIs that refer to absolute paths of locations
   *        where dependent modules can be alocated
   * @param ce compiler exception object to record all the errors encountered while determining
   *        compilation order of input modules
   */
  private static void removeModulesWithoutSource(
      SortedMap<String, SortedMap<String, SortedSet<AQLParseTreeNode>>> dependencies,
      String modulePath, CompilerException ce) {
    Set<String> sourceModuleList = dependencies.keySet();

    for (Entry<String, SortedMap<String, SortedSet<AQLParseTreeNode>>> sourceModule : dependencies
        .entrySet()) {
      String sourceModuleName = sourceModule.getKey();
      Map<String, SortedSet<AQLParseTreeNode>> dependentModules = sourceModule.getValue();

      // List of maintain non existent or pre-built dependent modules, that is non-source modules
      List<String> nonExistentModule = new ArrayList<String>();
      for (Entry<String, SortedSet<AQLParseTreeNode>> dependentModule : dependentModules
          .entrySet()) {
        boolean isSourceModule = sourceModuleList.contains(dependentModule.getKey());
        boolean isPreBuiltModule = false;

        // If current dependent module not found in the source modules list, let's look for the
        // dependent module in the
        // module path
        if (false == isSourceModule) {
          InputStream in = null;
          try {
            if (null != modulePath) {
              in = new ModuleResolver(modulePath).resolve(dependentModule.getKey());
              isPreBuiltModule = true;
            } else {
              isPreBuiltModule = false;
            }
          } catch (ModuleNotFoundException e) {
            isPreBuiltModule = false;
          } catch (Exception e) {
            ce.addError(e);
          } finally {
            if (in != null) {
              try {
                in.close();
              } catch (IOException e) {
                ce.addError(e);
              }
            }
          }

          // Report non-existent(either as a source module, or in the module path) dependent module
          if (false == isPreBuiltModule) {
            for (AQLParseTreeNode badImportStmt : dependentModule.getValue()) {
              Token errorLoc = null;
              String containingFileName = null;
              if (badImportStmt instanceof ImportModuleNode) {
                errorLoc = ((ImportModuleNode) badImportStmt).getImportedModuleName().getOrigTok();
                containingFileName = ((ImportModuleNode) badImportStmt).getContainingFileName();
              } else if (badImportStmt instanceof AbstractImportNode) {
                errorLoc = ((AbstractImportNode) badImportStmt).getFromModule().getOrigTok();
                containingFileName = ((AbstractImportNode) badImportStmt).getContainingFileName();
              }

              if (null != errorLoc && null != containingFileName) {
                ParseException pe = AQLParserBase.makeException(errorLoc,
                    "The module '%s' imports unknown module '%s'. Module '%s' is not found in the specified list of source modules or in the specified module path. Import modules available in the module path or from the list of source modules.",
                    sourceModuleName, dependentModule.getKey(), dependentModule.getKey());
                ce.addError(new ExtendedParseException(pe, new File(containingFileName)));
              }
            }
          }

          // if control comes here, then the source of current dependent module is unavailable
          nonExistentModule.add(dependentModule.getKey());
        }
      }

      // Filter out non-source modules from the list of dependent modules
      if (false == nonExistentModule.isEmpty()) {
        for (String nonExistentModuleName : nonExistentModule) {
          dependentModules.remove(nonExistentModuleName);
        }
      }

    }
  }

  /**
   * This method identifies all the circular dependencies between the specified modules, reports
   * them as errors, and additionaly break the cycle by removing the imports causing the cycle. <br>
   * e.g. if there is cycle like A -> B -> C-> A, then this method breaks the cycle by removing edge
   * C -> A, that is removing all imports of module A from module C.
   * 
   * @param dependencies map of input modules and its dependent modules (with error location info)
   * @param ce compiler exception object to record all the errors encountered while determining
   *        compilation order of input modules
   */
  private static void identifyReportAndBreakCycles(
      SortedMap<String, SortedMap<String, SortedSet<AQLParseTreeNode>>> dependencies,
      CompilerException ce) {
    // set of source modules to traverse to identify cycles
    Set<String> sourceModules = dependencies.keySet();

    // Stack to maintain the list of modules, whose dependents are yet to be visited
    Stack<String> stack = new Stack<String>();

    // List of visited modules, this is maintained to not re-visit. already visited modules
    List<String> visited = new ArrayList<String>();

    for (String startNode : sourceModules) {
      if (false == visited.contains(startNode))
        dfsGraphTraversal(startNode, stack, visited, dependencies, ce);
    }
  }

  /**
   * Helper method to traverse the graph (modules and their dependents) in depth-first order; this
   * method identifies the cycle, reports them and later breaks the cycle.
   * 
   * @param moduleToTraverse name of the module to be traversed
   * @param stack Stack to maintain the list of modules, whose dependents are yet to be visited
   * @param visited list of visited modules
   * @param dependencies map of input modules and its dependent modules (with error location info)
   * @param ce compiler exception object to record all the errors encountered while determining
   *        compilation order of input modules
   */
  private static void dfsGraphTraversal(String moduleToTraverse, Stack<String> stack,
      List<String> visited,
      SortedMap<String, SortedMap<String, SortedSet<AQLParseTreeNode>>> dependencies,
      CompilerException ce) {
    int foundAtIndex = stack.indexOf(moduleToTraverse);

    // We have cycle, report it and later break the cycle
    if (-1 != foundAtIndex) {
      // Report the cycle by recording the the import statements causing cycle
      ce.addError(makeCircularDependencyErrorMessage(stack.subList(foundAtIndex, stack.size()),
          dependencies));

      // Break the cycle
      String moduleWithImportsCausingCycle = stack.peek();
      Map<String, SortedSet<AQLParseTreeNode>> listOfImports =
          dependencies.get(moduleWithImportsCausingCycle);
      listOfImports.remove(moduleToTraverse);

      return;
    }
    stack.push(moduleToTraverse);
    visited.add(moduleToTraverse);

    Set<String> dependentModules = new TreeSet<String>(dependencies.get(moduleToTraverse).keySet());
    for (String dependentModule : dependentModules) {
      dfsGraphTraversal(dependentModule, stack, visited, dependencies, ce);
    }

    stack.pop();
  }

  /**
   * Uses toplogical sort algorithm to determine the compilation order
   * 
   * @param module2SrcURIMap Map of moduleName Vs Source URI
   * @param dependencies map of input modules and its dependent modules (with error location info)
   * @return Topologically sorted module source URIs, in the order of compilation
   */
  private static ArrayList<String> toplogicallySort(Map<String, String> module2SrcURIMap,
      SortedMap<String, SortedMap<String, SortedSet<AQLParseTreeNode>>> dependencies) {
    Set<String> modulesToSort = dependencies.keySet();

    // Creating a local copy, to avoid side effects to invoking methods
    SortedMap<String, String> todo = new TreeMap<String, String>(module2SrcURIMap);

    // Output goes here.
    ArrayList<String> ret = new ArrayList<String>();

    // Repeatedly run through the input list, generating the sorted output one layer at a time.
    // Note that this is potentially an O(n^2) algorithm, but probably O(n)in practice
    // (number of passes == number of levels of dependencies)
    while (todo.size() > 0) {

      for (String currModuleName : modulesToSort) {
        if (todo.containsKey(currModuleName)) {
          // Still haven't added this node to the output; see if it's now eligible.
          boolean dependsOnTodo = false;
          for (String dependentModule : dependencies.get(currModuleName).keySet()) {
            if (todo.containsKey(dependentModule)) {
              dependsOnTodo = true;
            }
          }

          if (false == dependsOnTodo) {
            // This node doesn't depend on any item in the current todo list, so we can add it to
            // the output.
            String currModuleURI = module2SrcURIMap.get(currModuleName);
            ret.add(currModuleURI);

            // remove currModuleName from todo
            todo.remove(currModuleName);
          }
        }
      } // end: foreach module in modulesToSort

    } // end: when todo.size() > 0

    return ret;
  }

  /**
   * Helper method to prepare CircularDependencyException object.
   */
  private static CircularDependencyException makeCircularDependencyErrorMessage(
      List<String> modulesInTheCycle,
      Map<String, SortedMap<String, SortedSet<AQLParseTreeNode>>> dependencies) {
    Map<String, List<Pair<String, Integer>>> moduleNameVserrorMarkers =
        new LinkedHashMap<String, List<Pair<String, Integer>>>();

    int cycleLength = modulesInTheCycle.size();

    for (int i = 0; i < cycleLength; i++) {
      String currentModule = modulesInTheCycle.get(i);
      // Module depends on the following module in the list; last module depends on the first module
      // in the list
      String dependentModule = modulesInTheCycle.get(i == cycleLength - 1 ? 0 : i + 1);
      Map<String, SortedSet<AQLParseTreeNode>> dependentModules = dependencies.get(currentModule);
      SortedSet<AQLParseTreeNode> importsInvolvedInCycle = dependentModules.get(dependentModule);

      List<Pair<String, Integer>> errorMarkers = new ArrayList<Pair<String, Integer>>();
      for (AQLParseTreeNode importNode : importsInvolvedInCycle) {
        int lineNumber;
        String fileName;
        lineNumber = importNode.getOrigTok().beginLine;
        fileName = importNode.getContainingFileName();

        errorMarkers.add(new Pair<String, Integer>(fileName, lineNumber));
      }
      moduleNameVserrorMarkers.put(currentModule, errorMarkers);
    }

    return new CircularDependencyException(moduleNameVserrorMarkers);
  }

  /**
   * Returns the module name part of the moduleSrcURI
   * 
   * @param moduleSrcURI absolute URI of module source directory
   * @return module name
   */
  private static String getModuleNameFromURI(String moduleSrcURI) {
    int idx = moduleSrcURI.lastIndexOf('/');
    if (idx == moduleSrcURI.length() - 1) {
      // URI ends with a '/'. So, remove the trailing '/' and call getModuleNameFromURI() on trimmed
      // URI, so that
      // multiple ending '/' characters are trimmed properly
      return getModuleNameFromURI(moduleSrcURI.substring(0, idx));
    } else {
      // return the last part of the URI, which is the module source folder name
      return moduleSrcURI.substring(idx + 1);
    }
  }

  /**
   * Returns the map of modules that the current module (specified by moduleSrcURI) depends on. The
   * key of the map is the name of the dependent module and value is the list of parse tree nodes
   * corresponding to imports of the dependent module.
   * 
   * @param moduleSrcURI URI containing AQL source files of the module whose dependencies are to be
   *        fetched
   * @return the map of modules that the current module (specified by moduleSrcURI) depends on
   * @throws Exception
   */
  // TODO: Migrate this method to FileOperations to support both local and HDFS
  // Can't use FileOperations now, because it does not support listFiles() method
  private static SortedMap<String, SortedSet<AQLParseTreeNode>> getDependentModules(
      String moduleSrcURI) throws Exception {
    SortedMap<String, SortedSet<AQLParseTreeNode>> dependentModules =
        new TreeMap<String, SortedSet<AQLParseTreeNode>>();

    File moduleSrcDir;
    try {
      moduleSrcDir = new File(new URI(moduleSrcURI));
    } catch (Throwable t) {
      throw new TextAnalyticsException(t,
          "Error converting module source URI '%s' to a local filesystem path", moduleSrcURI);
    }

    // Ensure that moduleSrcURI is a directory
    if (false == moduleSrcDir.isDirectory()) {
      throw new TextAnalyticsException(
          String.format("Module source directory does not exist or is not a directory: %s",
              moduleSrcDir.getAbsolutePath()));
    }

    // Get the list of all AQL files under moduleSrcDir
    File[] aqlFiles = moduleSrcDir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.endsWith(".aql");
      }
    });

    // Parse each AQL file and get the dependency
    if (aqlFiles != null && aqlFiles.length > 0) {
      for (File file : aqlFiles) {
        AQLParser parser = new AQLParser(file);
        StatementList stmtList = parser.parse();
        LinkedList<AQLParseTreeNode> nodes = stmtList.getParseTreeNodes();
        for (AQLParseTreeNode node : nodes) {
          // if it is 'import module' statement, then add the imported module to dependency list
          if (node instanceof ImportModuleNode) {
            ImportModuleNode importNode = (ImportModuleNode) node;
            String importedModuleName = importNode.getImportedModuleName().getNickname();
            if (null != importedModuleName) {
              if (false == dependentModules.containsKey(importedModuleName)) {
                SortedSet<AQLParseTreeNode> importStmtList = new TreeSet<AQLParseTreeNode>();
                importStmtList.add(importNode);
                dependentModules.put(importedModuleName, importStmtList);
              } else {
                SortedSet<AQLParseTreeNode> importStmtList =
                    dependentModules.get(importedModuleName);
                importStmtList.add(importNode);
              }
            }
          }
          // if it is 'import foo from module bar", then add the fromModule to dependency list
          else if (node instanceof AbstractImportNode) {
            AbstractImportNode importNode = (AbstractImportNode) node;
            NickNode fromModule = importNode.getFromModule();

            if (null != fromModule) {
              if (false == dependentModules.containsKey(fromModule.getNickname())) {
                SortedSet<AQLParseTreeNode> importStmtList = new TreeSet<AQLParseTreeNode>();
                importStmtList.add(importNode);
                dependentModules.put(fromModule.getNickname(), importStmtList);
              } else {
                SortedSet<AQLParseTreeNode> importStmtList =
                    dependentModules.get(fromModule.getNickname());
                importStmtList.add(importNode);

              }
            }
          } // end: if import node
        } // end: for each node
      } // end: for each AQL file

    } // end: if AQL file exists

    return dependentModules;

  }

  /**
   * Attempts to create a temporary directory under <USER TEMP> space.
   * 
   * @return File descriptor representing the temporary directory
   */
  // TODO: Refactor this to support both hdfs: and file: URIs
  public static File createCompilationTempDir() {
    try {
      File temp = File.createTempFile("compilationTempDir", "");
      temp.delete();
      temp.mkdirs();
      return temp;
    } catch (IOException e) {
      throw new RuntimeException("Error creating a directory under USER TEMP space", e);
    }
  }

  /**
   * Returns the qualified nick node based on the given module prefix.
   * 
   * @param modulePrefix module prefix string
   * @param originalNode nick node to be qualified
   * @return the qualified Nick node
   */
  public static NickNode prepareQualifiedNode(String modulePrefix, NickNode originalNode) {
    if (null == modulePrefix)
      return originalNode;

    return new NickNode(
        String.format("%s%c%s", modulePrefix, Constants.MODULE_ELEMENT_SEPARATOR,
            originalNode.getNickname()),
        originalNode.getContainingFileName(), originalNode.getOrigTok());
  }

  /**
   * Sorts a given schema so that it compares the same across platforms. This method works with
   * schema that contains non-parameterized types. The sorted schema is also of non-parameterized
   * types only, because presently in v2.0, the TypeInference & Metadata layers work with
   * non-parameterized types only.
   * 
   * @param schema the schema to sort
   * @return a sorted schema
   */
  public static TupleSchema sortSchemaWithNonParameterizedTypes(TupleSchema schema) {

    SortedMap<String, FieldType> fields = new TreeMap<String, FieldType>();

    /* iterate through each field and place the field name and field type into sorted map */
    for (int i = 0; i < schema.size(); i++) {
      String fieldName = schema.getFieldNameByIx(i);
      FieldType fieldType = schema.getFieldTypeByIx(i);

      fields.put(fieldName, fieldType);
    }

    // Create a TupleSchema with non-parameterized types
    String[] colNames = fields.keySet().toArray(new String[0]);
    FieldType[] colTypes = fields.values().toArray(new FieldType[0]);
    TupleSchema ret = new TupleSchema(colNames, colTypes);
    ret.setName(schema.getName());

    return ret;
  }

  /**
   * Replaces "." with "__". Since views/dicts/tables/functions can not contain dots in their names,
   * we need to encode them
   * 
   * @param elemName input string whose dots are to be replaced with double underscore
   * @return encoded string
   */
  public static String encodeElemName(String elemName) {
    if (elemName == null || elemName.trim().isEmpty()) {
      return elemName;
    }

    if (elemName.contains(".")) {
      return elemName.replace(".", "__");
    } else
      return elemName;
  }

  /**
   * Create a Document schema with field names sorted alphabetically.
   * 
   * @return a new schema with the name "Document" based on sorting the input schema parameter.
   * @param schema the schema (of view Document) whose fields are to be sorted
   */
  public static TupleSchema createSortedDocSchema(TupleSchema schema) {
    // trivial case: 1-column schemas are "sorted"
    if (schema.size() == 1) {
      schema.setName(Constants.DEFAULT_DOC_TYPE_NAME);
      return schema;
    }

    // create a map between a column's name and its field type
    Map<String, FieldType> fieldMap = new TreeMap<String, FieldType>();

    // determine the correct order of the schema fields and types
    for (int i = 0; i < schema.size(); i++) {
      fieldMap.put(schema.getFieldNameByIx(i), schema.getFieldTypeByIx(i));
    }

    String[] sortedColumns = new String[schema.size()];
    FieldType[] sortedTypes = new FieldType[schema.size()];

    int i = 0;
    // iterate over the map
    for (Map.Entry<String, FieldType> entry : fieldMap.entrySet()) {
      sortedColumns[i] = entry.getKey();
      sortedTypes[i] = entry.getValue();
      i++;
    }

    // create and return a new TupleSchema object with the sorted names and types to make sure
    // the accessors are pointing to the right fields
    TupleSchema ret = new TupleSchema(sortedColumns, sortedTypes);
    ret.setName(Constants.DEFAULT_DOC_TYPE_NAME);
    return ret;
  }

  /**
   * Internal interface to return the unionized schema of the input document schemas contained with
   * the input parameter map
   * 
   * @param moduleNameToSchema a map of module names to their document schemas
   * @return the unionized schema of the
   */
  public static TupleSchema computeMergedSchema(Map<String, TupleSchema> moduleNameToSchema) {
    TupleSchema schema = null;

    try {
      Map<String, List<String>> fieldNameToModules = new HashMap<String, List<String>>();

      // add each schema to the document schema in turn
      for (String moduleName : moduleNameToSchema.keySet()) {
        TupleSchema schemaToAdd = moduleNameToSchema.get(moduleName);

        schema = mergeSchema(schema, schemaToAdd, moduleName, fieldNameToModules);
      }

    } catch (DocSchemaMismatchException dsme) {
      throw new RuntimeException(dsme);
    }

    return schema;
  }

  /**
   * Adds the names and fields of docSchema to the existing schema. Useful for merging two schemas
   * (say, from different modules.)
   * 
   * @param existingSchema old document schema
   * @param schemaToAdd new schema to merge into the existing one
   * @param moduleName name of the module associated with schemaToAdd for error messages
   * @throws DocSchemaMismatchException
   */
  protected static TupleSchema mergeSchema(TupleSchema existingSchema, TupleSchema schemaToAdd,
      String moduleName, Map<String, List<String>> fieldNameToModules)
      throws DocSchemaMismatchException {
    // can't add a null to schema,
    if (schemaToAdd == null) {
      throw new DocSchemaMismatchException("Attempted to merge a document with null schema");
    }

    // handle the first schema in a sequence of schemas to merge
    if (existingSchema == null)
      return ModuleUtils.createSortedDocSchema(schemaToAdd);

    // schemas are the same, do nothing
    if (schemaToAdd.compareTo(existingSchema) == 0) {
      // capture the module name for the fields before returning, so that correct error message
      // could be reported, in
      // case we encounter document schema mismatch
      mapFieldsToModules(fieldNameToModules, schemaToAdd, moduleName);
      return existingSchema;
    }

    // If we get here, we need to union together the two schemas.
    // Generate the new full set of column names and types, checking for conflicts along the way.
    ArrayList<String> newColNames = new ArrayList<String>();
    ArrayList<FieldType> newColTypes = new ArrayList<FieldType>();

    // populate the arguments with the first schema, splitting by text and non-text types
    for (int i = 0; i < existingSchema.size(); i++) {
      newColNames.add(existingSchema.getFieldNameByIx(i));
      newColTypes.add(existingSchema.getFieldTypeByIx(i));
    }

    // Now look through the fields of the other schema and check for fields that aren't present in
    // our current schema.
    for (String otherFieldName : schemaToAdd.getFieldNames()) {

      if (existingSchema.containsField(otherFieldName)) {
        // duplicate found, check for clashing type
        FieldType originalFieldType = existingSchema.getFieldTypeByName(otherFieldName);
        FieldType newFieldType = schemaToAdd.getFieldTypeByName(otherFieldName);

        if (originalFieldType.equals(newFieldType)) {
          // do nothing
        } else {
          // provide short name for type Text, instead of 'Text in Document.text'
          FieldType newDocSchemaFieldType = schemaToAdd.getFieldTypeByName(otherFieldName);
          String strNewDocSchemaFieldType =
              newDocSchemaFieldType.getIsText() ? FieldType.TEXT_TYPE.getTypeName()
                  : newDocSchemaFieldType.getTypeName();

          FieldType mergedSchemaFieldType = existingSchema.getFieldTypeByName(otherFieldName);
          String strMergedSchemaFieldType =
              mergedSchemaFieldType.getIsText() ? FieldType.TEXT_TYPE.getTypeName()
                  : mergedSchemaFieldType.getTypeName();

          // types clash, throw exception (that prints out a meaningful explanation)
          throw new DocSchemaMismatchException(otherFieldName, moduleName,
              fieldNameToModules.get(otherFieldName).toString(), strNewDocSchemaFieldType,
              strMergedSchemaFieldType);

        }
      } else {
        // merge this column in with the first schema
        newColNames.add(otherFieldName);
        newColTypes.add(schemaToAdd.getFieldTypeByName(otherFieldName));
      }
    }

    // replace the schema with the new, merged schema
    String[] newColNamesArr = new String[newColNames.size()];
    FieldType[] newColTypesArr = new FieldType[newColTypes.size()];
    newColNamesArr = newColNames.toArray(newColNamesArr);
    newColTypesArr = newColTypes.toArray(newColTypesArr);

    existingSchema =
        ModuleUtils.createSortedDocSchema(new TupleSchema(newColNamesArr, newColTypesArr));
    existingSchema.setName(Constants.DEFAULT_DOC_TYPE_NAME);

    // populate fieldNameToModules map
    mapFieldsToModules(fieldNameToModules, schemaToAdd, moduleName);

    return existingSchema;
  }

  private static void mapFieldsToModules(Map<String, List<String>> fieldNameToModules,
      TupleSchema docSchema, String moduleName) {
    String[] fieldNames = docSchema.getFieldNames();
    for (String fieldName : fieldNames) {
      // get a reference to list of already encountered modules whose docschema contains the given
      // fieldName
      List<String> modules = fieldNameToModules.get(fieldName);

      // create the list, if the given module name is the first one to have the given fieldName
      if (modules == null) {
        modules = new ArrayList<String>();
        fieldNameToModules.put(fieldName, modules);
      }

      // add the moduleName to the list
      if (false == modules.contains(moduleName)) {
        modules.add(moduleName);
      }
    }
  }
}
