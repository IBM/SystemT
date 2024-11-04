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

package com.ibm.avatar.aql.compiler;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import com.ibm.avatar.algebra.util.file.FileOperations;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.AQLParser;
import com.ibm.avatar.aql.AQLParserBase;
import com.ibm.avatar.aql.AbstractExportNode;
import com.ibm.avatar.aql.CreateDictNode;
import com.ibm.avatar.aql.CreateExternalViewNode;
import com.ibm.avatar.aql.CreateFunctionNode;
import com.ibm.avatar.aql.CreateTableNode;
import com.ibm.avatar.aql.CreateViewNode;
import com.ibm.avatar.aql.DetagDocNode;
import com.ibm.avatar.aql.ExportDictNode;
import com.ibm.avatar.aql.ExportFuncNode;
import com.ibm.avatar.aql.ExportTableNode;
import com.ibm.avatar.aql.ExportViewNode;
import com.ibm.avatar.aql.ExtendedParseException;
import com.ibm.avatar.aql.ImportDictNode;
import com.ibm.avatar.aql.ImportFuncNode;
import com.ibm.avatar.aql.ImportModuleNode;
import com.ibm.avatar.aql.ImportTableNode;
import com.ibm.avatar.aql.ImportViewNode;
import com.ibm.avatar.aql.ModuleNode;
import com.ibm.avatar.aql.OutputViewNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.RequireColumnsNode;
import com.ibm.avatar.aql.SetDefaultDictLangNode;
import com.ibm.avatar.aql.StatementList;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.doc.AQLDocComment;
import com.ibm.avatar.logging.Log;
import com.ibm.avatar.logging.MsgType;

/**
 * Class for parsing AQL and preparing the catalog. This class does not generate a compiled operator
 * graph (AOG). To generate the AOG, use {@link Compiler}.
 * 
 */
public class ParseToCatalog {

  @SuppressWarnings("unused")
  private static final boolean debug = false;

  /**
   * Catalog of available views, external types, and functions.
   */
  private Catalog catalog = new Catalog();

  /**
   * Set the catalog. Use this method when you want to use a custom catalog implementation instead
   * of the default catalog {@link com.ibm.avatar.catalog.DefaultCatalog}.
   * 
   * @param catalog
   */
  public void setCatalog(Catalog catalog) {
    if (null != catalog)
      this.catalog = catalog;
    else
      throw new IllegalArgumentException(
          "Passed a null value for catalog. The catalog cannot be null.");
  }

  /**
   * @return the catalog
   */
  public Catalog getCatalog() {
    return this.catalog;
  }

  /**
   * Parse the input AQL and return a populated catalog. This method will not generate the compiled
   * operator graph (AOG).
   * 
   * @param params
   * @return
   * @throws CompilerException
   * @throws IOException
   */
  public Catalog parse(CompileAQLParams params) throws CompilerException, IOException {
    // The parser cannot parse non-modular AQL anymore.
    if (null != params.getInputFile())
      throw new IllegalArgumentException(
          "ParseToCatalog.parse() called with non-modular AQL code.");

    if (params.getModulePath() != null) {
      catalog.setModulePath(params.getModulePath());
    } else {
      // do not default modulePath to some value. Always let the user set it. Setting modulePath to
      // some default value
      // such as outputURI might result in loading of dependent modules from incorrect locations
      // and/or loading of
      // incorrect versions of dependent modules.
    }

    if (null != params.getInputStr() && null == params.getInputModules()) {
      // user is attempting to parse a string
      // We should never reach this section, this should be caught in params.validate()

      throw new IllegalArgumentException(
          "ParseToCatalog.parse() called with non-null inputStr parameter -- not supported with modular AQL code.");
    } else {
      String[] inputModules = params.getInputModules();
      if (inputModules != null) {
        for (String moduleURI : inputModules) {
          // Try block so that we can capture the module name when there is a fatal internal error
          // parsing a module.
          try {
            parseModule(params, moduleURI);
          } catch (Error e) {
            // Wrap non-checked exceptions in a FatalInternalError that includes the module URI
            throw new FatalInternalError(e, "Error parsing module at %s (%s)", moduleURI,
                e.toString());
          } catch (RuntimeException e) {
            throw new FatalInternalError(e, "Error parsing module at %s (%s)", moduleURI,
                e.toString());
          }
        }
      }
    }
    // If we get here, we have a populated catalog
    return getCatalog();
    // }
  }

  /**
   * Parse a single module and populates the catalog. At the same time, also remembers a mapping
   * between AQL files and parse tree nodes defined in that file.
   * 
   * @param params compilation parameters
   * @param moduleURI the module URI
   * @throws IOException
   * @throws CompilerException
   */
  protected void parseModule(CompileAQLParams params, String moduleURI)
      throws IOException, CompilerException {

    // Mapping between AQL file names and ParseTreeNodes in that file, for the provenance rewrite.
    // Use a TreeMap to ensure consistent view name generation in the provenance rewrite
    TreeMap<String, List<AQLParseTreeNode>> file2Nodes =
        new TreeMap<String, List<AQLParseTreeNode>>();

    File moduleDir = null;
    try {

      if (!FileOperations.exists(moduleURI)) {
        throw new IOException("Module URI does not exist: " + moduleURI);
      }

      moduleDir = new File(FileOperations.createURI(moduleURI));

    } catch (IllegalArgumentException e) {
      // Wrap the unhelpful unchecked exception that the File constructor throws on an invalid URI
      throw new IOException(
          String.format("Error converting URI string '%s' to a filesystem path: %s", moduleURI,
              e.getMessage()),
          e);
    } catch (URISyntaxException e) {
      throw new IOException(
          String.format("Error parsing module URI string '%s': %s", moduleURI, e.getMessage()), e);
    } catch (Exception e) {
      throw new IOException(
          String.format("Error parsing module URI string '%s': %s", moduleURI, e.getMessage()), e);
    }

    File[] aqlFiles = moduleDir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.endsWith(".aql");
      }
    });

    if (0 == aqlFiles.length) {
      throw new IOException(String.format("Module directory %s does not contain any AQL files",
          moduleDir.getAbsolutePath()));
    }

    // sort the array of AQL files in a case-insensitive fashion, so that we parse the files
    // in deterministic order across both Unix and Windows
    Arrays.sort(aqlFiles, new Comparator<File>() {
      @Override
      public int compare(File a, File b) {
        return a.getName().toUpperCase().compareTo(b.getName().toUpperCase());
      }
    });

    // List of validation error from all the parse tree nodes
    List<ParseException> errors = new ArrayList<ParseException>();

    // Parse every AQL file within the current module and get a list of nodes from each one of them

    ArrayList<AQLParseTreeNode> allNodes = new ArrayList<AQLParseTreeNode>();
    for (File aqlFile : aqlFiles) {
      List<AQLParseTreeNode> nodes = parseSingleFile(params, aqlFile, errors);
      allNodes.addAll(nodes);

      // Remember what parse tree nodes we parsed from this file
      file2Nodes.put(aqlFile.getName(), nodes);
    }

    // Remember the mapping between AQL files and parse tree nodes in that file, for the provenance
    // rewrite
    catalog.setFile2NodesMapping(file2Nodes);

    // add all nodes of a given module to catalog only after all AQL files of the module are parsed.
    // By doing so, we
    // do not have to bother about the parse order of AQL files.
    for (AQLParseTreeNode node : allNodes) {
      try {
        addToCatalog(node);
      } catch (ParseException e) {
        errors.add(e);
      }
    }

    // validate parse tree nodes of all AQL files within the current module
    ArrayList<AQLParseTreeNode> validNodes = new ArrayList<AQLParseTreeNode>();
    for (AQLParseTreeNode node : allNodes) {
      List<ParseException> nodeErrors = node.validate(catalog);
      if (null != nodeErrors) {
        if (nodeErrors.size() == 0) {
          validNodes.add(node);
        }
        if (nodeErrors.size() > 0)
          errors.addAll(nodeErrors);
      }
    }

    // set state for parse tree nodes
    setStateForNodes(validNodes, catalog, errors);

    if (errors.size() > 0) {
      // Add the compiler exceptions to the catalog
      for (Exception e : errors) {
        catalog.addCompilerException(e);
      }
      // Note: do not throw any errors yet. Allow the preprocessor to run,
      // which does type inference and validation.
    }

    // Finally, add any AQL comment for the module from our special module.info file
    // At this point aqlFiles is guaranteed to have at least one member.
    // We use the first
    readModuleComment(params, moduleURI, aqlFiles[0]);
  }

  /**
   * Presently, sets the state of DetagNode, OutputViewNodes and ExportXXXNode. When other nodes
   * need to be included in this method in future, we might have to consider the oder in which the
   * nodes are to be processed as the dependency between them would dictate the order.
   * 
   * @param validNodes List of all valid parse tree nodes
   * @param catalog Catalog used by the current instance of the compiler
   * @param errors
   */
  private void setStateForNodes(ArrayList<AQLParseTreeNode> validNodes, Catalog catalog,
      List<ParseException> errors) {
    // Pass 1: Segregate DetagDocNodes, ExportXXXNodes and OutputViewNodes
    List<DetagDocNode> detagList = new ArrayList<DetagDocNode>();
    List<OutputViewNode> outputList = new ArrayList<OutputViewNode>();
    List<AbstractExportNode> exportList = new ArrayList<AbstractExportNode>();

    for (AQLParseTreeNode node : validNodes) {
      if (node instanceof DetagDocNode) {
        detagList.add((DetagDocNode) node);
      } else if (node instanceof OutputViewNode) {
        outputList.add((OutputViewNode) node);
      } else if (node instanceof AbstractExportNode) {
        exportList.add((AbstractExportNode) node);
      }
    }

    // Pass 2: invoke setState() on all DetagDocNode objects
    for (DetagDocNode detagNode : detagList) {
      try {
        detagNode.setState(catalog);
      } catch (ParseException e) {
        errors.add(e);
      }
    }

    // Pass 3: invoke setState() on all OutputViewNode objects
    for (OutputViewNode outputViewNode : outputList) {
      try {
        outputViewNode.setState(catalog);
      } catch (ParseException e) {
        errors.add(makeWrapperException(e, outputViewNode.getContainingFileName()));
      }
    }

    // Pass 4: invoke setState() on all ExportXXXNode objects
    for (AbstractExportNode exportNode : exportList) {
      try {
        exportNode.setState(catalog);
      } catch (ParseException e) {
        errors.add(makeWrapperException(e, exportNode.getContainingFileName()));
      }
    }

  }

  /**
   * Read the comment for the module from the file called
   * {@link AQLDocComment#MODULE_AQL_DOC_COMMENT_FILE_NAME} and stores the unaltered comment in the
   * catalog. If the file does not exists or is a directory, no comment is stored. Adds a warning to
   * the catalog if the file exists but the comment cannot be read, either because the file is a
   * directory or because some other exception encountered during read.
   * 
   * @param params
   * @param moduleURI
   * @param aqlFile In case of warning, the AQL file to be referenced
   */
  private void readModuleComment(CompileAQLParams params, String moduleURI, File aqlFile)
      throws IOException {

    URI uri;
    try {
      uri = FileOperations.createURI(moduleURI);
    } catch (Throwable e) {
      throw new IOException(
          String.format("Error parsing module URI string '%s': %s", moduleURI, e.getMessage()), e);
    }

    // Location of the comment file under the module URI
    String commentFileStr = String.format("%s/%s", uri, Constants.MODULE_AQL_DOC_COMMENT_FILE_NAME);

    try {
      if (FileOperations.exists(commentFileStr)) {
        if (FileOperations.isDirectory(commentFileStr)) {
          // The file exists but is a directory, therefore we can't read the comment.
          // Add a warning to the catalog to let the user know.
          CompilerWarning w = new CompilerWarning(
              CompilerWarning.WarningType.MODULE_COMMENT_LOCATION_IS_DIRECTORY,
              String.format(
                  "Could not read module comment for module '%s' because the location '%s' is a directory, not a file.",
                  moduleURI, Constants.MODULE_AQL_DOC_COMMENT_FILE_NAME),
              aqlFile.getCanonicalPath());
          catalog.addCompilerWarning(w);
        } else {

          // Read the comment as a string from the module.info file, process it and add it to the
          // catalog
          StringBuilder sb = new StringBuilder();
          char[] buf = new char[1024];
          InputStreamReader in = new InputStreamReader(FileOperations.getStream(commentFileStr),
              params.getInEncoding());
          int nread;
          while (0 < (nread = in.read(buf))) {
            sb.append(buf, 0, nread);
          }
          in.close();
          String moduleComment = sb.toString();
          AQLDocComment comment = new AQLDocComment(0, 0, 0, 0, moduleComment.trim());
          catalog.setComment(comment);

        }
      }
    } catch (Exception e) {
      // Compilation should not fail because we can't read the module comment.
      // Catch any exception and add a warning to the catalog
      CompilerWarning w = new CompilerWarning(
          CompilerWarning.WarningType.MODULE_COMMENT_READ_FAILED,
          String.format(
              "Exception encountered when processing module comment for module '%s' from file '%s': %s",
              moduleURI, Constants.MODULE_AQL_DOC_COMMENT_FILE_NAME, e),
          aqlFile.getCanonicalPath());
      catalog.addCompilerWarning(w);
    }
  }

  @SuppressWarnings("deprecation")
  private List<AQLParseTreeNode> parseSingleFile(CompileAQLParams params, File inputFile,
      List<ParseException> errors) throws IOException, CompilerException {
    /*
     * ALERT!! ALERT!!. In v2.0, all aql files are expected to be directly under module folder.
     * Also, all dictionaries and UDF jars are resolved relative to module directory. So, to
     * determine the datapath, just query the parent of inputFile. If this rule changes in future,
     * the following code need to be modified.
     */
    String dataPath = inputFile.getParentFile().getAbsolutePath();

    if (null != dataPath) {
      catalog.setDictsPath(dataPath);
      catalog.setUDFJarPath(dataPath);
    }

    // Step# 1 Parse the input AQL
    AQLParser parser;
    StatementList statementList = null;
    try {
      parser = new AQLParser(inputFile, params.getInEncoding());
      statementList = parser.parse();
    } catch (ParseException e) {
      // TODO: added by Jayatheerthan: AQLParser.parse() does not throw any parse exceptions, but
      // AQLParser(file,
      // encoding) constructor declares that it throws ParseException, though I do not see any
      // statement in it that
      // could potentially throw ParseException. I am leaving it as-is for now, since there is very
      // little time left to
      // make the changes in both runtime and tooling components before end of DCUT.
    }

    if (statementList == null) {
      throw new RuntimeException("Internal error -- no statement list received from parser.");
    }

    // proceed with compilation only if there are no parse errors
    if (statementList.getParseErrors().size() > 0) {
      // Store the parse exceptions to the catalog
      for (Exception e : statementList.getParseErrors()) {
        catalog.addCompilerException(e);
      }
    }

    // Step# 2 Validate parse tree nodes for semantics and prepare the
    // catalog
    LinkedList<AQLParseTreeNode> parseTreeNodes = statementList.getParseTreeNodes();
    if (parseTreeNodes.size() > 0) {

      for (AQLParseTreeNode node : parseTreeNodes) {
        if (node instanceof ModuleNode) {
          String moduleName = ((ModuleNode) node).getModuleName();
          String aqlFileName = inputFile.getName();
          String parentDirName = inputFile.getParentFile().getName();
          if (false == moduleName.equals(parentDirName)) {
            errors.add(makeWrapperException(AQLParserBase.makeException(String.format(
                "The module name '%s' declared in AQL file %s does not match with the parent directory name '%s'",
                moduleName, aqlFileName, parentDirName), ((ModuleNode) node).getOrigTok()),
                inputFile.getAbsolutePath()));
          }
          continue;
        } else if (node instanceof CreateDictNode) {
          if (((CreateDictNode) node).usesAllowEmpty()) {
            String dictName = ((CreateDictNode) node).getUnqualifiedName();
            String aqlFileName = inputFile.getAbsolutePath();
            Token origTok = node.getOrigTok();

            String template = String.format(
                "The external dictionary '%s' uses the deprecated 'allow_empty' flag. Use the 'required' flag instead.",
                dictName);

            Log.log(MsgType.AQLCompileWarning, template);

            // Add a warning to the catalog if the deprecated 'allow_empty' flag is used
            CompilerWarning w = new CompilerWarning(
                CompilerWarning.WarningType.DEPRECATED_FLAG_USED, template, aqlFileName,
                origTok.beginLine, origTok.beginColumn, origTok.endLine, origTok.endColumn);
            catalog.addCompilerWarning(w);
          }
        } else if (node instanceof CreateTableNode) {
          if (((CreateTableNode) node).getIsExternal()
              && ((CreateTableNode) node).isAllowEmpty() != null) {
            String tableName = ((CreateTableNode) node).getUnqualifiedName();
            String aqlFileName = inputFile.getName();
            Token origTok = node.getOrigTok();

            String template = String.format(
                "The external table '%s' uses the deprecated 'allow_empty' flag. Use the 'required' flag instead.",
                tableName);

            Log.log(MsgType.AQLCompileWarning, template);

            // Add a warning to the catalog if the deprecated 'allow_empty' flag is used
            CompilerWarning w = new CompilerWarning(
                CompilerWarning.WarningType.DEPRECATED_FLAG_USED, template, aqlFileName,
                origTok.beginLine, origTok.beginColumn, origTok.endLine, origTok.endColumn);
            catalog.addCompilerWarning(w);
          }
        }

      }

    }

    return parseTreeNodes;
  }

  private void addToCatalog(AQLParseTreeNode node) throws ParseException {
    Token token = null;
    String containingAQLFile = null;
    try {
      token = node.getOrigTok();
      containingAQLFile = node.getContainingFileName();

      if (node instanceof ModuleNode) {
        catalog.setModuleName(node.getModuleName());
      } else if (node instanceof CreateViewNode) {
        catalog.addView((CreateViewNode) node);
      } else if (node instanceof CreateTableNode) {
        catalog.addTable((CreateTableNode) node);
      } else if (node instanceof CreateExternalViewNode) {
        catalog.addExternalView((CreateExternalViewNode) node);
      } else if (node instanceof CreateFunctionNode) {
        catalog.addFunction((CreateFunctionNode) node);
      } else if (node instanceof CreateDictNode) {
        catalog.addDict((CreateDictNode) node);
      } else if (node instanceof OutputViewNode) {
        OutputViewNode ovn = (OutputViewNode) node;
        catalog.addOutputView(ovn.getViewname().getNickname(), ovn.getAltnameStr());
      } else if (node instanceof DetagDocNode) {
        catalog.addDetag((DetagDocNode) node);
      } else if (node instanceof RequireColumnsNode) {
        catalog.addRequiredColumns((RequireColumnsNode) node);
      } else if (node instanceof SetDefaultDictLangNode) {
        catalog.setDefaultDictLang(((SetDefaultDictLangNode) node));
      } else if (node instanceof ImportModuleNode) {
        catalog.addImportModuleNode((ImportModuleNode) node);
      } else if (node instanceof ImportViewNode) {
        catalog.addImportElementNode((ImportViewNode) node);
      } else if (node instanceof ImportDictNode) {
        catalog.addImportElementNode((ImportDictNode) node);
      } else if (node instanceof ImportTableNode) {
        catalog.addImportElementNode((ImportTableNode) node);
      } else if (node instanceof ImportFuncNode) {
        catalog.addImportElementNode((ImportFuncNode) node);
      } else if (node instanceof ExportViewNode) {
        catalog.addExportedNode((AbstractExportNode) node);
      } else if (node instanceof ExportDictNode) {
        catalog.addExportedNode((AbstractExportNode) node);
      } else if (node instanceof ExportTableNode) {
        catalog.addExportedNode((AbstractExportNode) node);
      } else if (node instanceof ExportFuncNode) {
        catalog.addExportedNode((AbstractExportNode) node);
      }

    } catch (ParseException pe) {
      if (pe instanceof ExtendedParseException) {
        throw pe;
      } else {
        // preserve the currentToken if it is not null. Set it to 'token' if null
        if (pe.currentToken == null) {
          pe.currentToken = token;
        }
        throw makeWrapperException(pe, containingAQLFile);
      }
    } catch (RuntimeException re) {
      if (null != token) {
        // Capture the location from which the RuntimeException was thrown.
        // This code currently wraps RuntimeException in ParseException.
        // Should we be using FatalInternalError here?
        ParseException e = makeWrapperException(AQLParserBase.makeException(re, token,
            "Internal error adding parse tree node %s to catalog", node), containingAQLFile);
        throw e;
      } else {
        // this will never happen
        throw new RuntimeException("null token -- should never happen", re);
        // throw makeWrapperException (new ParseException (re.getMessage ()), containingAQLFile);
      }
    }
  }

  /**
   * Utility function to create wrapper exceptions containing the name of the AQL file where the
   * compile error occurred.
   * 
   * @param ParseException list of parse exceptions
   * @param errorFileName name of the file where the error occurred, or null if no file is involved
   * @return List of exceptions that will properly print out the location of the error
   */
  public static List<ParseException> makeWrapperException(List<ParseException> pes,
      String errorFileName) {
    List<ParseException> extendedParseExceptions = new ArrayList<ParseException>(pes.size());
    File errorFile = (null == errorFileName) ? null : FileUtils.createValidatedFile(errorFileName);

    for (ParseException pe : pes) {
      extendedParseExceptions.add(new ExtendedParseException(pe, errorFile));
    }

    return extendedParseExceptions;
  }

  /**
   * Utility function to create wrapper exception containing the name of the AQL file where the
   * compile error occurred.
   * 
   * @param ParseException
   * @param errorFileName name of the file where the error occurred, or null if no file is involved
   * @return Exception that will properly print out the location of the error
   */
  public static ParseException makeWrapperException(ParseException pe, String errorFileName) {
    File errorFile = (null == errorFileName) ? null : FileUtils.createValidatedFile(errorFileName);
    return new ExtendedParseException(pe, errorFile);
  }
}
