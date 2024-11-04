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
package com.ibm.avatar.algebra.test.stable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.output.ToBuffer;
import com.ibm.avatar.algebra.relational.Project;
import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.dict.DictFile;
import com.ibm.avatar.algebra.util.dict.DictMemoization;
import com.ibm.avatar.algebra.util.dict.DictParams;
import com.ibm.avatar.algebra.util.document.ToHTMLOutput;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.tokenize.StandardTokenizer;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.aog.AOGOpTree;
import com.ibm.avatar.aog.AOGOutputExpr;
import com.ibm.avatar.aog.AOGParseTree;
import com.ibm.avatar.aog.AOGParser;
import com.ibm.avatar.aog.AOGPlan;
import com.ibm.avatar.aog.BufferOutputFactory;
import com.ibm.avatar.aog.SymbolTable;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.OperatorGraphImpl;
import com.ibm.avatar.aql.tam.ModuleMetadataImpl;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;

/** Tests of the Avatar Object Graph file parser. */
public class AOGParserTests extends RuntimeTestHarness {

  /**
   * Directory containing AOG files referenced by the test cases in this class.
   */
  public static final String AOG_FILES_DIR = TestConstants.AOG_DIR + "/AOGParserTests";

  /**
   * Default input document file for this test suite
   */
  public static final File INPUT_FILE = new File(TestConstants.DUMPS_DIR, "enron1k.del");

  public static final File TMP_INPUT_FILE = new File(TestConstants.TEST_DOCS_DIR, "tmp.del");

  public static void main(String[] args) throws Exception {

    AOGParserTests pt = new AOGParserTests();

    pt.setUp();

    long startMS = System.currentTimeMillis();

    pt.externalViewTest();

    long endMS = System.currentTimeMillis();

    pt.tearDown();

    double elapsedSec = (endMS - startMS) / 1000.0;

    System.err.printf("Test took %1.3f sec.\n", elapsedSec);

  }

  @Before
  public void setUp() throws Exception {

    // For now, don't put any character set information into the header of
    // our output HTML.
    setWriteCharsetInfo(false);

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    // scan = new DBDumpFileScan(TestConstants.ENRON_1K_DUMP);
    // scan = new DBDumpFileScan("testdata/docs/tmp.del");
  }

  @After
  public void tearDown() {
    // scan = null;

    // Deregister the Derby driver so that other tests can connect to other
    // databases.
    try {
      DriverManager.getConnection("jdbc:derby:;shutdown=true");
    } catch (SQLException e) {
      // The shutdown command always raises a SQLException
      // See http://db.apache.org/derby/docs/10.2/devguide/
    }
    System.gc();
  }

  /** Baseline test that just scans documents and echoes them to an HTML file. */
  @Test
  public void passthroughTest() throws Exception {
    startTest();
    String AOG = "$Document = DocScan(\n( \"text\" => \"Text\")\n); Output: $Document;\n";

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(true);

    runAOGString(INPUT_FILE, AOG, null);
    truncateExpectedFiles();
    truncateOutputFiles(true);
    compareAgainstExpected(true);
    endTest();

  }

  /** Open up the "personphone" file and just parse it. */
  @Test
  public void basicParseTest() throws Exception {
    File in = new File(AOG_FILES_DIR, "/personphone.aog");

    // prepare a dummy map of compiled dictionary object
    CompiledDictionary cd =
        new CompiledDictionary("dictionaries/first.dict", null, null, null, null);
    Map<String, CompiledDictionary> compiledDictMap = new HashMap<String, CompiledDictionary>();
    compiledDictMap.put("dictionaries/first.dict", cd);

    AOGParser parser = new AOGParser(Constants.GENERIC_MODULE_NAME, in, compiledDictMap);
    AOGParseTree parsetree = parser.Input();

    // There should be four productions: PersonPart, Person, PersonRC, and
    // PersonPhone
    System.err.print("Parse tree is:\n");
    parsetree.dump(System.err);
    assertEquals(4, parsetree.getSubtrees().size());

  }

  /** Parse the "personphone" file and convert to a plan. Then run the plan. */
  @Test
  public void codegenTest() throws Exception {

    startTest();

    File in = new File(AOG_FILES_DIR, "/personphone.aog");
    // File in = new File("testdata/simple.aog");

    // prepare dictionary parameter
    DictParams params = new DictParams();
    params.setLangStr("de,es,en,fr,it,x_unspecified");
    params.setDictName("dictionaries/first.dict");

    // Load and compile the dictionary
    DictFile dictFile = new DictFile(
        new File(String.format("%s/%s", TestConstants.TESTDATA_DIR, "dictionaries/first.dict")),
        params);
    CompiledDictionary compiledDictionary =
        dictFile.compile(new StandardTokenizer(), new DictMemoization());

    // Map of compiled dictionaries
    Map<String, CompiledDictionary> compiledDictMap = new HashMap<String, CompiledDictionary>();
    compiledDictMap.put(compiledDictionary.getCompiledDictName(), compiledDictionary);

    AOGParser parser = new AOGParser(Constants.GENERIC_MODULE_NAME, in, compiledDictMap);
    AOGParseTree parsetree = parser.Input();

    // System.err.print("Parse tree is:\n");
    // parsetree.dump(System.err);

    // scan = new DBDumpFileScan(TestConstants.TESTDATA_DIR +
    // "/docs/tmp.del");

    // Get an operator tree version of the file.
    // Sink root = parsetree.toOpTree(scan, new outputFactory());

    // Convert from ParseTree to a AOGPlan
    parsetree.convertSubtrees();
    parsetree.buildSymTab();
    ArrayList<AOGOpTree> subtrees = parsetree.getConvertedSubTrees();
    SymbolTable plan_symtab = parsetree.getSymTab();
    AOGOutputExpr plan_outputs = parsetree.getOutputs();
    AOGPlan plan = OperatorGraphImpl.toPlan(new BufferOutputFactory()//
        , null // AnnotationReaderFactory readerFactory
        , null // TokenizerConfig tokenizerCfg
        , plan_symtab, parser.getCatalog(), subtrees, plan_outputs);

    // System.err.print("Operator tree is:\n");
    // plan.dump(System.err, 0);

    // //////////////////////////////////////////////
    // Perform some validation on the operator tree.
    // Add more checks here as bugs turn up.

    // Pull out the output operators.
    String[] OUTPUT_NAMES = {"Person", "Person_Phone"};
    ToBuffer[] outputs = new ToBuffer[OUTPUT_NAMES.length];
    for (int i = 0; i < outputs.length; i++) {
      outputs[i] = (ToBuffer) plan.getOutputOp(OUTPUT_NAMES[i]);
    }

    for (int i = 0; i < outputs.length; i++) {
      Operator childOp = outputs[i].getInputOp(0);

      assertTrue(childOp instanceof Project);
    }

    String[][] dictInfo = {{"dictionaries/first.dict", "testdata/dictionaries/first.dict"}};

    setDisableOutput(false);
    setWriteCharsetInfo(true);
    runAOGFile(INPUT_FILE, in, dictInfo);

    // /////////////////////////////////
    // See if the plan actually works.

    //
    // int ndoc = 0;
    // int nannot = 0;
    //
    // while (plan.hasNextDoc ()) {
    // // Tuple tup = root.getNext();
    // // assertEquals(1, tup.size());
    //
    //
    // plan.nextDoc (0);
    //
    // nannot += plan.getCurNumTups ();
    // ndoc++;
    //
    // if (0 == ndoc % 1000) {
    // System.err.printf ("Produced %d annotations over %d documents.\n", nannot, ndoc);
    // }
    // }
    //
    // System.err.printf ("Processed %d documents total " + "and produced %d annotations.\n", ndoc,
    // nannot);

    System.err.printf("Comparing output files against expected results.\n");
    truncateOutputFiles(true);

    compareAgainstExpected("Person.htm", true);
    compareAgainstExpected("Person_Phone.htm", true);

  }

  /**
   * Parse and run the file that tests multiple-nickname expressions.
   */
  @Test
  public void boysAndGirlsTest() throws Exception {

    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(false);

    // prepare list of dict file being referred in the aog
    String[][] dictInfo =
        {{"dictionaries/male.first.dict", "testdata/dictionaries/male.first.dict"},
            {"dictionaries/female.first.dict", "testdata/dictionaries/female.first.dict"},};

    runAOGFile(INPUT_FILE, dictInfo);
    truncateOutputFiles(true);
    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /** Scratchpad for trying out AOG expressions. */
  public void scratch() throws Exception {
    startTest();

    setDumpPlan(true);
    setDisableOutput(false);
    setPrintTups(true);

    runAOGFile(INPUT_FILE, null);
    endTest();
  }

  /**
   * A test of group extraction with the Regex operator in AOG
   */
  @Test
  public void regexGroupTest() throws Exception {

    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(false);

    runAOGFile(INPUT_FILE, null);
    truncateOutputFiles(true);
    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();

  }

  /**
   * Test to make sure that the strings the parser generates when dumping input back to a stream are
   * valid AOG.
   */
  @Test
  public void graphDumpTest() throws Exception {
    startTest();

    // We use a big test file in the hopes of catching more problems.
    File aqlFile = new File(TestConstants.AQL_DIR, "lotus/namedentity.aql");

    // First compile an AQL file into AOG.
    File tamOrig = new File(getCurOutputDir(), "original");
    tamOrig.mkdirs();
    CompileAQLParams compileParam =
        new CompileAQLParams(aqlFile, tamOrig.toURI().toString(), TestConstants.TESTDATA_DIR);
    compileParam.setTokenizerConfig(getTokenizerConfig());
    CompileAQL.compile(compileParam);

    // Load tam
    TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME, compileParam.getOutputURI());

    String origaog = tam.getAog();
    Map<String, CompiledDictionary> allCompiledDicts = tam.getAllDicts();

    // Dump the original AOG to a file for checking.
    File origdump = new File(getCurOutputDir(), "graphDumpOrig.aog");
    FileWriter origWriter = new FileWriter(origdump);
    origWriter.write(origaog);
    origWriter.close();

    // Now parse the AOG and write it out again.
    AOGParser parser =
        new AOGParser(Constants.GENERIC_MODULE_NAME, origaog, allCompiledDicts, null);
    // parser.setDictsPath(TestConstants.TESTDATA_DIR);
    AOGParseTree parsetree = parser.Input();

    // System.err.printf("Parse tree has %d subtrees.\n", parsetree
    // .getSubtrees().size());

    // Dump the parse tree to a buffer.
    CharArrayWriter buf = new CharArrayWriter();
    parsetree.dump(new PrintWriter(buf));

    // Dump the resulting AOG back to a file.
    File aogdump = new File(getCurOutputDir(), "graphDump.aog");
    OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(aogdump), "UTF-8");
    w.write(buf.toString());
    w.close();

    System.err.printf("AOG dump produces:\n%s\n", buf.toString());

    // Make sure the graph dump is as we expected (also don't truncate it)
    compareAgainstExpected("graphDump.aog", false);

    // Read the AOG from the file we just dumped and use it to replace the original AOG inside the
    // TAM
    String aog = FileUtils.fileToStr(aogdump, "UTF-8");
    tam.setAog(aog);

    // Dump the new TAM back to disk
    TAMSerializer.serialize(tam, getCurOutputDir().toURI());

    // Run the resulting extractor.
    // runAOGFile (new File (TestConstants.ENRON_1K_DUMP),
    // new File (getCurOutputDir () + "/graphDump.aog"), dictInfo);
    OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
        getCurOutputDir().toURI().toString(), null, getTokenizerConfig());

    this.setWriteCharsetInfo(true);
    annotateAndPrint(new File(TestConstants.ENRON_1K_DUMP), og);

    // Now we can compare with the expected output files.
    truncateOutputFiles(true);
    // truncateExpectedFiles ();
    compareAgainstExpected("AllCities.htm", true);
    compareAgainstExpected("AllStates.htm", true);
    compareAgainstExpected("InternetEmail.htm", true);
    compareAgainstExpected("NotesEmail.htm", true);
    compareAgainstExpected("Organization.htm", true);
    compareAgainstExpected("Person.htm", true);
    compareAgainstExpected("PersonalNotesEmail.htm", true);
    compareAgainstExpected("PersonSingleToken.htm", true);
    compareAgainstExpected("PhoneNumber.htm", true);
    compareAgainstExpected("Place.htm", true);
    compareAgainstExpected("URL.htm", true);
    compareAgainstExpected("Zipcodes.htm", true);

  }

  /**
   * A test of the RegexTok operator.
   */
  @Test
  public void regexTokTest() throws Exception {
    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(false);

    // This test uses a custom input file, based off of the first 200 docs
    // of ENRON_1K_DUMP, but with some additional test documents.
    File REGEX_INPUT = new File(TestConstants.TEST_DOCS_DIR + "/aqlEnronTests/regexTokTest.del");

    runAOGFile(REGEX_INPUT, null);

    System.err.printf("Comparing output files.\n");
    truncateOutputFiles(true);
    compareAgainstExpected(true);

    endTest();
  }

  /**
   * Test of hinted regexes
   */
  @Test
  public void hintingTest() throws Exception {
    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(false);

    runAOGFile(INPUT_FILE, null);

    truncateOutputFiles(true);
    compareAgainstExpected(true);

    endTest();

  }

  /** Test of the syntax for the Split operator. */
  @Test
  public void splitTest() throws Exception {
    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(false);

    runAOGFile(INPUT_FILE, null);

    truncateOutputFiles(true);
    compareAgainstExpected(true);

    endTest();
  }

  /** Test of lookup tables. */
  @Test
  public void lookupTableTest() throws Exception {
    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(true);

    // prepare list of dict file being referred in the aog
    String[][] dictInfo = {{"dictionaries/aogParserTests/companyDict.dict",
        "testdata/dictionaries/aogParserTests/companyDict.dict"}};
    runAOGFile(INPUT_FILE, dictInfo);

    truncateOutputFiles(true);
    compareAgainstExpected(true);

    endTest();

  }

  /**
   * Test of external views. <br/>
   */
  @Test
  public void externalViewTest() throws Exception {
    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(true);

    String className = getClass().getSimpleName();

    // Read the AOG
    // Compute the location of the current test case's top-level AQL file.
    File aogDir = new File(TestConstants.AOG_DIR, className);
    File aogFile = new File(aogDir, String.format("%s.aog", getCurPrefix()));

    if (false == aogFile.exists()) {
      throw new Exception(String.format("AOG file %s not found.", aogFile));
    }

    String aog = FileUtils.fileToStr(aogFile, "UTF-8");

    String externalViewName = "DocStats";

    // Open up the documents so that we can get at their schema information;
    // we'll keep them open so that we can scan them later on.
    DocReader docs = new DocReader(INPUT_FILE);
    TupleSchema docSchema = docs.getDocSchema();
    FieldGetter<Text> docTextGetter = docSchema.textAcc(Constants.DOCTEXT_COL);

    // Generate a TAM file.
    final String moduleName = Constants.GENERIC_MODULE_NAME;
    TAM tam = new TAM(moduleName);
    tam.setAog(aog);

    // create dummy empty meta data object; this is to make loader happy
    ModuleMetadataImpl dummyMD = ModuleMetadataImpl.createEmptyMDInstance();

    // we compile dictionaries from old testcases using the built-in whitespace tokenizer
    TokenizerConfig tokenizerCfg = new TokenizerConfig.Standard();
    dummyMD.setTokenizerType(tokenizerCfg.getName());
    tam.setMetadata(dummyMD);

    TAMSerializer.serialize(tam, getCurOutputDir().toURI());

    // Instantiate the resulting operator graph.
    String modulePath = getCurOutputDir().toURI().toString();
    System.err.printf("Using module path '%s'\n", modulePath);

    OperatorGraph og = OperatorGraph.createOG(new String[] {moduleName}, modulePath, null, null);

    // Get the list of views and their schemas from the operator graph
    Map<String, TupleSchema> outputViews = og.getOutputTypeNamesAndSchema();

    // Initialize the utility object to write the output HTML files
    ToHTMLOutput toHtmlOut =
        new ToHTMLOutput(outputViews, getCurOutputDir(), true, false, false, docSchema);

    int ndoc = 0;
    TupleList externalViewTups;
    Tuple externalViewTup;
    TupleSchema schema = og.getSchema(externalViewName);
    TextSetter textSetter = schema.textSetter(schema.getFieldNameByIx(0));
    FieldSetter<Integer> intSetter = schema.intSetter(schema.getFieldNameByIx(1));

    while (docs.hasNext()) {
      Tuple docTup = docs.next();

      // Push some tuples into the external view
      externalViewTups = new TupleList(schema);
      externalViewTup = schema.createTup();
      textSetter.setVal(externalViewTup, "" + ndoc);
      intSetter.setVal(externalViewTup, docTextGetter.getVal(docTup).getLength());
      externalViewTups.add(externalViewTup);

      Map<String, TupleList> extViewTupsMap = new HashMap<String, TupleList>();
      extViewTupsMap.put(externalViewName, externalViewTups);

      // Execute
      Map<String, TupleList> annots = og.execute(docTup, null, extViewTupsMap);

      // Append the results to the output HTML files
      toHtmlOut.write(docTup, annots);
    }

    // Close the output files.
    toHtmlOut.close();

    // Close the document reader
    docs.remove();

    // truncateOutputFiles (true);
    compareAgainstExpected(true);

    endTest();

  }

  /** Test of non-correlated subquery inlining. */
  @Test
  public void subqueryTest() throws Exception {

    startTest();

    // String filename = AOG_FILES_DIR + "/subqueryTest.aog";

    setDataPath(TestConstants.TESTDATA_DIR);
    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(true);

    // prepare list of dict file being referred in the aog
    String[][] dictInfo = {{"dictionaries/first.dict", "testdata/dictionaries/first.dict"}};

    runAOGFile(INPUT_FILE, dictInfo);

    truncateOutputFiles(true);
    compareAgainstExpected(true);

    endTest();
  }

  /** Test of new dictionary syntax in AOG. */
  @Test
  public void newDictTest() throws Exception {

    startTest();

    // String filename = AOG_FILES_DIR + "/newDictTest.aog";

    setDataPath(TestConstants.TESTDATA_DIR);
    setDumpPlan(true);
    setDisableOutput(false);
    setPrintTups(true);

    // prepare list of dict file being referred in the aog
    List<String> dictFilePaths = new ArrayList<String>();
    dictFilePaths.add("dictionaries/aogParserTests/companyDict.dict");
    dictFilePaths.add("dictionaries/first.dict");
    dictFilePaths.add("dictionaries/aogParserTests/CountryDict.dict");

    String[][] dictInfo = {{"dictionaries/first.dict", "testdata/dictionaries/first.dict"},
        {"dictionaries/aogParserTests/companyDict.dict",
            "testdata/dictionaries/aogParserTests/companyDict.dict"},
        {"dictionaries/aogParserTests/CountryDict.dict",
            "testdata/dictionaries/aogParserTests/CountryDict.dict"}};

    // printAOGFile(INPUT_FILE);
    runAOGFile(INPUT_FILE, dictInfo);

    truncateOutputFiles(true);
    compareAgainstExpected(true);

    endTest();

  }

  /**
   * Test of the Limit() operator in AOG.
   */
  @Test
  public void limitTest() throws Exception {
    startTest();

    // String filename = AOG_FILES_DIR + "/limitTest.aog";

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(false);

    // prepare list of dict file being referred in the aog
    String[][] dictInfo = {{"dictionaries/first.dict", "testdata/dictionaries/first.dict"}};

    runAOGFile(INPUT_FILE, dictInfo);

    truncateOutputFiles(true);
    compareAgainstExpected(true);

    endTest();

  }

}
