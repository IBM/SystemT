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

import java.io.CharConversionException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.CircularIncludeDependencyException;
import com.ibm.avatar.aql.planner.AnnotPlan;
import com.ibm.avatar.logging.Log;

/**
 * Base class for the AQL parser. We put functionality into this class so that we don't have to edit
 * the java in the JavaCC file.
 */
public abstract class AQLParserBase {

  /**
   * Set this flag to true to generate information about the stack of included files.
   */
  private static final boolean TRACE_FILES = false;

  /** File encoding to use if no other encoding is explicitly specified. */
  protected static final String DEFAULT_ENCODING = "UTF-8";

  /** Encoding of the file currently being parsed. */
  private String fileEncoding = DEFAULT_ENCODING;


  /** Various phases of parsing -- used only for v2.0+ */
  protected enum Phase {
    /**
     * First statement must be a module statement
     */
    MODULE_PHASE,
    /**
     * We've parsed the module statement, now can accept any statement
     */
    IMPORT_PHASE,
    /**
     * We've encountered a non-import statement, now disallow all import statements
     */
    NO_IMPORT_PHASE

  }

  protected Phase phase = Phase.MODULE_PHASE;

  /** whether the last statement parsed is an import statement */
  protected boolean importStatement;

  /**
   * Remembers the current module parsed by the parser.
   */
  protected String currentModule;

  /**
   * Hook for use by generated Java code, since the generated constructor will expect the base
   * class's constructor not to take any arguments.
   * 
   * @param fileEncoding encoding of the file to be parsed
   */
  public void setFileEncoding(String fileEncoding) {
    this.fileEncoding = fileEncoding;
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

  /**
   * Generate a ParseException with a token pointer and a message. We put this method here because
   * we can't add a new constructor to the generated class ParseException.
   * 
   * @param token token indicating the location of the problem
   * @param format format string, as in {@link String#format(String, Object...)}
   * @param args arguments for format string
   */
  public static ParseException makeException(Token token, String format, Object... args) {
    return makeException(null, token, format, args);
  }

  /**
   * Generate a ParseException with a token pointer, a root cause, and a formatted message.
   * 
   * @param cause exception that indicates the root cause of the problem
   * @param token token indicating the location of the problem
   * @param format format string, as in {@link String#format(String, Object...)}
   * @param args arguments for format string
   */
  public static ParseException makeException(Throwable cause, Token token, String format,
      Object... args) {
    String msg;
    if (null == format) {
      // Some internal errors result in this method being called with a null pointer for the format
      // string.
      // Handle this case gracefully.
      msg = "no error message";
    } else {
      msg = String.format(format, args);
    }
    ParseException ret = new ParseException(cause, msg);
    ret.currentToken = token;
    return ret;
  }

  /**
   * Generate an error location by appending information about the current file to the indicated
   * token.
   */
  protected ErrorLocation errLoc(Token token) {
    return new ErrorLocation(peekFile(), token);
  }

  /**
   * Search path for finding included files.
   */
  protected SearchPath includePath = new SearchPath(".");

  public void setIncludePath(String pathStr) {
    if (null == pathStr) {
      // System.err.printf("Setting include path to NULL\n");
      this.includePath = null;
    } else {
      // System.err.printf("Setting include path to '%s'\n", pathStr);
      this.includePath = new SearchPath(pathStr);
    }
  }

  /** Internal method for setting the include path of a sub-parser directly. */
  protected void setIncludePath(SearchPath includePath) {
    this.includePath = includePath;
  }

  /**
   * We use the following <b>global</b> variable get the current file name to the JavaCC-generated
   * tokenizer. Files are stored in a stack because we may be parsing multiple files at once. We
   * keep a separate stack for each thread, since the parser may be invoked from multiple threads.
   */
  protected static HashMap<Thread, Stack<File>> g_fileStacks = new HashMap<Thread, Stack<File>>();

  /**
   * @return global file stack for the calling thread
   */
  private static synchronized Stack<File> stackForThread() {
    Thread curThread = Thread.currentThread();
    Stack<File> ret = g_fileStacks.get(curThread);
    if (null == ret) {
      // No stack yet; create one.
      ret = new Stack<File>();
      g_fileStacks.put(curThread, ret);
    }
    return ret;
  }

  protected static void pushFile(File f) throws ParseException {

    if (TRACE_FILES) {
      Log.debug("pushFile(%s) called.", f);
    }

    // There could be multiple copies of the parser running, so every thread
    // gets its own file stack.
    Stack<File> fileStack = stackForThread();

    // Check for circular dependencies.
    for (int i = 0; i < fileStack.size(); i++) {
      if (f.equals(fileStack.get(i))) {

        // Uh-oh, the file we're trying to include is already being
        // parsed higher up in the stack; this means that there's a
        // circular dependency.
        // Collect up the chain of includes that led to this problem.
        ArrayList<String> includeChain = new ArrayList<String>();
        for (int j = i; j < fileStack.size(); j++) {
          File file = fileStack.get(j);
          String fileName;
          if (null != file) {
            fileName = file.toString();
          } else {
            fileName = "Java String";
          }
          includeChain.add(fileName);
        }
        includeChain.add(f.toString());

        // Generate a user-friendly error message.
        System.err.printf("****** Throwing exception\n");
        throw new CircularIncludeDependencyException(includeChain);
      }
    }

    // If we get here, we've passed the error-checking code; add the file to
    // the stack.
    stackForThread().push(f);
  }

  protected static File peekFile() {
    if (stackForThread().size() > 0) {
      return stackForThread().peek();
    } else {
      return null;
    }
  }

  protected String curFileName() {
    File curFile = peekFile();
    if (null == curFile) {
      // No file ==> Parsing a string
      return this.aqlFilename != null ? this.aqlFilename : "Java String";
    } else {
      return curFile.getPath();
    }
  }

  protected static void popFile() {
    if (stackForThread().size() > 0) {
      if (TRACE_FILES) {
        Log.debug("popFile(): Popping %s off the stack.", stackForThread().peek());
      }

      stackForThread().pop();
    } else {
      System.err.printf("Warning: File stack empty.\n");
    }
  }

  /**
   * Default constructor (the only kind of constructor we can support, due to JavaCC constraints;
   * the JavaCC-generated class will have a default constructor that expects the superclass argument
   * to take no arguments). Performs initialization of internal data structures for the base class.
   */
  protected AQLParserBase() {

  }

  /**
   * Clear out any entries in the file stack for the curren thread. This method should be called
   * every time you instantiate a parser on a top-level AQL file or string.
   */
  protected void clearFileStack() {
    stackForThread().clear();
  }

  /**
   * File that we will parse, if applicable; set to null if we're parsing a string directly.
   */
  protected void setFile(File file) throws ParseException {
    if (TRACE_FILES) {
      Log.debug("setFile(%s) called.", file);
    }

    // Add an entry to our global table of files.
    pushFile(file);

    if (null != file) {
      // Append the file's CWD to the include path
      // TODO: Should we be doing this?
      includePath.addEntry(file.getParentFile());
    }
  }

  /**
   * StatementList will contain ordered list of Parse tree nodes and ordered list of errors found
   * during parsing. parse() method will return the populated StatementList
   */
  protected StatementList statementList = new StatementList();

  /**
   * Name of the file to be parsed by the parser
   */
  protected String aqlFilename = null;

  /**
   * Flag to specify whether to parse included aql file or not from input aql file or String
   */
  protected boolean processIncludedFile = false;

  /**
   * Method to set/unset include file processor flag
   * 
   * @param processIncludedFile
   */
  protected final void setProcessIncludedFile(boolean processIncludedFile) {
    this.processIncludedFile = processIncludedFile;
  }

  /**
   * Remove quotes and escaped quotes from a quoted string returned by the lexer.
   * 
   * @param quotechar character (e.g. " or /) used to indicate start/end quotation
   * @param string string to be dequoted
   * @return string with quotes removed
   * @throws ParseException
   */
  protected static String dequoteStr(char quotechar, Token quotedString) throws ParseException {
    try {
      final boolean debug = false;

      String ret = StringUtils.dequoteStr(quotechar, quotedString.image);

      if (debug) {
        Log.debug("dequoteStr('%c', %s) returns %s", quotechar,
            StringUtils.quoteStr('"', quotedString.image, true, true),
            StringUtils.quoteStr('"', ret, true, true));
      }

      return ret;
    } catch (IllegalArgumentException e) {
      throw makeException(e.getMessage(), quotedString);
    }
  }

  /**
   * Remove quotes and escaped quotes from a quoted string returned by the lexer. AT the same time,
   * deescapes any unicode characters.
   * 
   * @param quotechar character (e.g. " or /) used to indicate start/end quotation
   * @param string string to be dequoted
   * @return string with quotes removed
   * @throws ParseException
   */
  protected static String dequoteAndDeescapeUnicodeStr(char quotechar, Token quotedString)
      throws ParseException {
    try {
      final boolean debug = false;

      String ret = StringUtils.dequoteAndDeescapeUnicodeStr(quotechar, quotedString.image);

      if (debug) {
        Log.debug("dequoteAndDeescapeUnicodeStr('%c', %s) returns %s", quotechar,
            StringUtils.quoteStr('"', quotedString.image, true, true),
            StringUtils.quoteStr('"', ret, true, true));
      }

      return ret;
    } catch (IllegalArgumentException e) {
      throw makeException(e.getMessage(), quotedString);
    }
  }

  /**
   * Removes the quotes from a quoted string as returned by the lexer and de-escapes escaped
   * characters from the string.
   * 
   * @param quotechar character (e.g. " or /) used to indicate start/end quotation
   * @param string string to be dequoted
   * @return the string, with with quotes removed and any escaped characters inside the string
   *         de-escaped.
   * @throws ParseException
   */
  protected static String dequoteAndDeescapeStr(char quotechar, Token quotedString)
      throws ParseException {
    try {
      final boolean debug = false;

      String ret = StringUtils.dequoteAndDeescapeStr(quotechar, quotedString.image);

      if (debug) {
        Log.debug("dequoteAndDeescapeStr('%c', %s) returns %s", quotechar,
            StringUtils.quoteStr('"', quotedString.image, true, true),
            StringUtils.quoteStr('"', ret, true, true));
      }

      return ret;
    } catch (IllegalArgumentException e) {
      throw makeException(e.getMessage(), quotedString);
    }
  }

  /** Generated code should call this method at the start of parsing. */
  protected void setUp() {
    // Create the catalog that will be used throughout parsing.
    // if (null == catalog) {
    // catalog = new DefaultCatalog();
    // }

    // Add the dummy "DocScan" view to the symbol table.
    // if (null == catalog.lookupView(AOGParseTree.DOC_SCAN_BUILTIN)) {
    // catalog.addView(DocScanDummyViewNode.singleton);
    // }
  }

  /**
   * Generated code should call this method at the end of parsing, right before returning the
   * completed parse tree.
   */
  protected void tearDown() {

    // Remove the entry we added to our global file table in
    // setUp();
    if (null == this.aqlFilename)
      popFile();
  }

  /** Placeholder for the main parser entry point in generated code. */
  public abstract void __inputInternal() throws ParseException;

  /**
   * @deprecated this method has been renamed to {@link #parse()}
   */
  @Deprecated
  public void Input() throws ParseException {
    parse();
  }

  /**
   * Wrapper around the main parser entry point; Return StatementList: Containing ordered lists of
   * parse tree nodes and parse exception.
   */
  public StatementList parse() {
    setUp();
    try {
      __inputInternal();
    } catch (ParseException e) {

    } catch (TokenMgrError lexerError) {
      if (TRACE_FILES) {
        Log.debug("Parser caught lexical error; current file is %s", peekFile());
      }

      try {
        error_skipto(convertLexerError(lexerError), AQLParserConstants.SEMICOLON);

        // Start parsing again from the next statement
        parse();
      } catch (TokenMgrError anotherLexErr) {
        // Reaching here implies that the lexer is unable to fetch the next token.Hence we cannot
        // proceed further with
        // parsing of the current AQL file.
        // Ignore this error- this is already added to the list of parse errors.
      }
    }

    tearDown();

    return statementList;
  }

  /**
   * Counter for generating anonymous names; shared with any parsers that this one invokes
   * recursively.
   */
  // private static class Counter {
  // int nextIx = 1;
  //
  // int next() {
  // return nextIx++;
  // }
  // }
  // protected Counter subqueryIxGen = new Counter();
  //
  // public void setSubqueryIxGen(Counter subqueryIxGen) {
  // this.subqueryIxGen = subqueryIxGen;
  // }
  /** Create a unique name for the next subquery view that we create. */

  // protected String makeSubqueryName() {
  // return String.format("__subquery%d", subqueryIxGen.next());
  // }
  /**
   * Utility function used in backward-compatibility code for the old table functions like
   * Dictionary and RegexTok. Wraps an extraction spec in an artificial nested view and returns the
   * top-level node of the nested view.
   * 
   * @param errTok location at which to report errors
   */
  protected FromListItemNode wrapExtractFn(ExtractionNode extraction, Token errTok)
      throws ParseException {
    ArrayList<SelectListItemNode> selectItems = new ArrayList<SelectListItemNode>();
    ExtractListNode extractList = new ExtractListNode(selectItems, extraction,
        extraction.getContainingFileName(), extraction.getOrigTok());
    /**
     * Fix for defect# 17124: Missing error location when Dictionary/RegexTok function refer to an
     * view not defined Adding token to the target view of the generated subquery view, this will
     * used while preparing error location
     */
    FromListItemNode targetView =
        new FromListItemViewRefNode(new NickNode(extraction.getTargetName().getTabname(),
            extraction.getContainingFileName(), errTok));

    // Table functions don't have any of the auxiliary clauses that an
    // EXTRACT statement can have.
    HavingClauseNode havingClause = null;
    ConsolidateClauseNode consolidateClause = null;
    IntNode maxTok = null;

    ExtractNode extract = new ExtractNode(extractList, targetView, havingClause, consolidateClause,
        maxTok, extraction.getContainingFileName(), errTok);

    // Wrap the extract statement in a subquery view
    /*
     * NickNode viewName = new NickNode(null, makeSubqueryName(), getCatalog()); CreateViewNode cvn
     * = new CreateViewNode(viewName, extract); catalog.addView(cvn); return new
     * FromListItemViewRefNode(viewName);
     */

    return new FromListItemSubqueryNode(extract, extract.getContainingFileName(),
        extract.getOrigTok());
  }

  /**
   * Verify that the indicated file is in the indicated encoding.
   * 
   * @param f target file
   * @param encoding expected file encoding
   */
  public static void verifyEncoding(File f, String encoding) throws ParseException {
    // Log.debug("Opening file %s to check encoding.", f);

    try {
      InputStreamReader r = new InputStreamReader(new FileInputStream(f), encoding);

      char[] cbuf = new char[1024];
      @SuppressWarnings("all")
      int totRead = 0;
      int nread;
      while ((nread = r.read(cbuf)) > 0) {
        totRead += nread;
      }
      r.close();
    } catch (CharConversionException e) {
      throw new ParseException(String.format("AQL file '%s' is not in %s encoding", f, encoding));
    } catch (IOException e) {
      throw new ParseException("Error opening file " + f);
    }

  }

  /**
   * Parse an included AQL file, recursively creating a new parser instance to handle the parsing.
   * 
   * @param f file to include
   * @param fileNameNode original parse tree node for the filename or pattern that matched the
   *        indicated file
   * @throws ParseException if an error occurs while parsing the included file
   */
  protected void processInclude(File f, StringNode fileNameNode) throws ParseException {
    try {
      boolean debug = false;
      if (debug) {
        Log.debug("Recursively parsing included file %s", f);
      }
      // Recursively parse the file...
      AQLParser subParser = new AQLParser(f, fileEncoding, true);
      subParser.setIncludePath(includePath);
      subParser.setBackwardCompatibilityMode(compatibilityMode);

      statementList.addStatementList(subParser.parse());
    } catch (IOException e) {
      throw makeException(e.toString(), fileNameNode.getOrigTok());
    }
  }

  /**
   * Method to get the next token. Implementation of this will provided by generated JAVACC parser
   * code.
   * 
   * @return Next token from the aql
   */
  public abstract Token getNextToken();

  /**
   * Method to skip to next token of the given kind most of the cases it will be SEMICOLON token.
   * This method also adds the parse error to the parser error list.
   * 
   * @param e
   * @param kind
   */
  protected void error_skipto(ParseException e, int kind) {
    statementList
        .addParseError(new ExtendedParseException(e, FileUtils.createValidatedFile(curFileName())));

    // Skip to next valid token kind or end of file
    Token t;
    do {
      t = getNextToken();
    } while (t.kind != kind && t.kind != AQLParserConstants.EOF);
  }

  // Flag to determine whether we are running in v1.3 compatibility mode
  protected boolean compatibilityMode = false;

  public void setBackwardCompatibilityMode(boolean modeSet) {
    compatibilityMode = modeSet;
  }

  public boolean isBackwardCompatibilityMode() {
    return this.compatibilityMode;
  }

  /**
   * Returns the qualified nick node based on the given module prefix.
   * 
   * @param modulePrefix module prefix string
   * @param originalNode nick node to be qualified
   * @return the qualified Nick node
   */
  public NickNode prepareQualifiedNode(String modulePrefix, NickNode originalNode) {
    if (null == modulePrefix)
      return originalNode;

    return new NickNode(
        String.format("%s%c%s", modulePrefix, Constants.MODULE_ELEMENT_SEPARATOR,
            originalNode.getNickname()),
        originalNode.getContainingFileName(), originalNode.getOrigTok());
  }

  /**
   * Verifies that the given element name does not contain a period in it.
   * 
   * @param elementType Type of the element used to prepare the message
   * @param node NickNode of the element being defined. Used to retrieve element name and token
   *        information
   */
  protected boolean elementNameContainsDot(String elementType, NickNode node) {
    if (node == null) {
      return false;
    }

    String nickName = node.getNickname();
    if (nickName != null && nickName.contains(".")) {
      ParseException pe =
          makeException(String.format("A %s name cannot contain a period in it.", elementType),
              node.getOrigTok());
      error_skipto(pe, AQLParserConstants.SEMICOLON);

      return true;
    }

    return false;

  }

  /**
   * Utility method to convert lexer error into a parse exception.
   * 
   * @param lexError encountered lexer error
   * @return converted parse exception object
   */
  private static ParseException convertLexerError(TokenMgrError lexError) {
    // errMsg initialized to message from TokenManagerError
    String errMsg = lexError.getMessage();
    // Error location initialized to beginning of the file
    int erroneousLineNumber = 1, erroneousColumnNumber = 1;

    // Pattern to extract erroneousLineNumber,erroneousColumnNumber and error message
    Pattern p = Pattern.compile("line (\\d+), column (\\d+).(.*)");
    Matcher m = p.matcher(errMsg);
    if (m.find()) {
      erroneousLineNumber = Integer.parseInt(m.group(1));
      erroneousColumnNumber = Integer.parseInt(m.group(2));
      // Strip out error location information from error message
      errMsg = m.group(3);
    }

    // Dummy token to report error location
    Token errToken = new Token();
    errToken.beginLine = erroneousLineNumber;
    errToken.beginColumn = erroneousColumnNumber;

    return makeException(errMsg.trim(), errToken);
  }

  /**
   * List of AQL reserved words used to determine whether Node.dump() should enclose a string in
   * double quotes
   */
  private static List<String> AQL_RESERVED_WORDS = null;

  static {
    AQL_RESERVED_WORDS = Arrays.asList(AQLParserConstants.tokenImage);
  }

  /**
   * Looks up a list of AQL keywords to determine whether the input word is an AQL reserved word.
   * 
   * @param word The text to compare against list of AQL reserved words
   * @return <code>true</code>, if the input word is an AQL reserved word; <code>false</code>,
   *         otherwise.
   */
  public static boolean isAQLReservedWord(String word) {
    // pad it so that we don't match substrings!
    String paddedWord = String.format("\"%s\"", word);
    return AQL_RESERVED_WORDS.contains(paddedWord);
  }

  /**
   * Enclose the input string in double quotes if it is an AQL reserved word or does *not* match
   * Nickname regex pattern
   * 
   * @param nickName input string to be enclosed in quotes
   * @return double-quoted string if the input string is an AQL reserved word or does *not* match
   *         Nickname regex pattern. Otherwise returns the input string as it is.
   */
  public static String quoteReservedWord(String nickName) {
    if (isEscapingRequired(nickName)) {
      return StringUtils.quoteStr('"', nickName, true, true);
    } else {
      return nickName;
    }
  }

  /**
   * Examines the input nickName to determine whether escaping with double quotes is required or not
   * 
   * @param nickName nick name string whose value is to be examined.
   * @return Returns <code>true</code> if the nick name is an AQL reserved word or does not match
   *         Nickname regex; <code>false</code> otherwise.
   */
  public static boolean isEscapingRequired(String nickName) {
    if (nickName == null)
      return false;

    // Escaping required if word is an AQL reserved word (or) if it does *not* match Nickname's
    // regex pattern
    return AQLParser.isAQLReservedWord(nickName)
        || (false == nickName.matches(AnnotPlan.NICKNAME_REGEX));
  }
}
