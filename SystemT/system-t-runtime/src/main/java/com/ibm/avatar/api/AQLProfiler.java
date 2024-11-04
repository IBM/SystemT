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
package com.ibm.avatar.api;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.ProfileRecord;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextGetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.document.DocUtils;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.test.TestUtils;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.compiler.CompilerWarning;
import com.ibm.avatar.logging.Log;
import com.ibm.avatar.logging.MsgType;

/**
 * Utility class for profiling a modular extractor. Breaks down execution time by statement. This
 * utility is capable of profiling source modules, compiled modules, or a loaded
 * {@link OperatorGraph} instance. This class does not profile extractors in non-modular AQL form.
 * To profile such extractors, compile the extractor into a generic module and profile the generic
 * module.
 */
public class AQLProfiler {
  /*
   * DRIVER PROGRAM For use by driver scripts in the SystemT distribution.
   */

  /**
   * Default tokenizer name that indicates 'use the standard (whitespace) tokenizer, not the
   * Multilingual tokenizer'
   */
  private static final String DEFAULT_TOKENIZER = TokenizerConfig.Standard.class.getName();

  public static final String DEFAULT_DOCS_FILE = null;

  public static final String DEFAULT_RUNTIME_SEC = "10";
  public static final String DEFAULT_LANGUAGE = "en";

  public static final String USAGE = //
      String.format("Usage: java %s \n" //
          + "                   -d docsFile \n"//
          + "                 ( -s moduleSrcURIs | -m moduleNames )\n"//
          + "                 [ -p modulePath ] [ -o outputTypes ]\n"//
          + "                 [ -t tokenizer ]  \n"//
          + "                 [ -l langCode ]  [-T minTimeToRun ]\n"//
          + "                 [ -c CSV separator ]\n"//
          + "Where:\n"//
          + "      docsFile       is a file or directory containing documents to annotate.\n" //
          + "      moduleSrcURIs  is a list of one or more URIs to the module source\n"//
          + "                       to be profiled, separated by '%c'.\n"//
          + "      moduleNames    is a list of one or more module names to profile,\n" //
          + "                       separated by '%c'.\n"//
          + "      modulePath     is a search path (one or more entries, separated by '%c')\n"//
          + "                       for finding dependent compiled modules. \n"//
          + "      outputTypes    is a list consisting of one or more entries, \n" //
          + "                       separated by '%c' of output types enabled when annotating\n"//
          + "                       documents (by default, all outputs of the extractor\n"//
          + "                       are enabled).\n"//
          + "      tokenizer      is the tokenizer configuration used for compiling and executing. \n"//
          + "                      Can be either:\n"//
          + "                      a. '%s' to use the Standard tokenizer, or\n"//
          + "                      b. The full name (in the form packageName.className) of a custom TokenizerConfig class \n"//
          + "                         for a Multilingual tokenizer/POS tagger\n"//
          + "      langCode       is the language code of the input collection \n"//
          + "                       (optional, defaults to en)\n"//
          + "      minTimeToRun   is the minimum number of seconds that the profiler should\n"//
          + "                       be run (optional, defaults to 10 seconds) \n" //
          + "      CSV separator  is the character used to separate fields in a CSV input \n" //
          + "                       document collection (optional, defaults to '%s')\n", //
          AQLProfiler.class.getName(), //
          SearchPath.PATH_SEP_CHAR, //
          SearchPath.PATH_SEP_CHAR, //
          SearchPath.PATH_SEP_CHAR, //
          SearchPath.PATH_SEP_CHAR, //
          DEFAULT_TOKENIZER, Constants.DEFAULT_CSV_FIELD_SEPARATOR);

  /**
   * Possible flags for the main method of this class.
   */
  public static final String[] POSSIBLE_FLAGS =
      {"-d", "-s", "-m", "-p", "-t", "-l", "-T", "-o", "-c"};

  /**
   * An array of boolean values specifying whether or not an argument is expected to follow the
   * corresponding flag; true at position i means that an argument is expected to follow after the
   * flag POSSIBLE_FLAGS[i].
   */
  public static final boolean[] ARGS_EXPECTED =
      {true, true, true, true, true, true, true, true, true};

  /**
   * Main method for invoking this class from a script. Invoke the method with no arguments for
   * example usage.
   * 
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {

    if (0 == args.length) {
      System.err.printf("\n%s\n", USAGE);
      return;
    }

    // Try block for catching the IllegalArgumentExceptions that we throw
    // when there is a problem with the args to the driver program.
    try {

      TreeMap<String, String> argMap;

      // Parse the argument strings
      try {
        argMap = TestUtils.parseArgs(args, POSSIBLE_FLAGS, ARGS_EXPECTED);
      } catch (Exception e) {
        throw new IllegalArgumentException(e.getMessage());
      }

      // Now we put the arguments into place; start with default values...
      String docsFileName = DEFAULT_DOCS_FILE;
      String tokenizer = DEFAULT_TOKENIZER;
      String runtimeSec = DEFAULT_RUNTIME_SEC;
      String language = DEFAULT_LANGUAGE;
      String outputTypes = null;
      String moduleSrcLoc = null;
      String moduleNames = null;
      String modulePath = null;
      char csvFieldSeparator = Constants.DEFAULT_CSV_FIELD_SEPARATOR;

      // ...and override the defaults as requested.
      for (Map.Entry<String, String> e : argMap.entrySet()) {
        String flag = e.getKey();
        String arg = e.getValue();

        if ("-d".equals(flag)) {
          docsFileName = arg;
        } else if ("-s".equals(flag)) {
          moduleSrcLoc = arg;
        } else if ("-m".equals(flag)) {
          moduleNames = arg;
        } else if ("-p".equals(flag)) {
          modulePath = arg;
        } else if ("-t".equals(flag)) {
          tokenizer = arg;
        } else if ("-l".equals(flag)) {
          language = arg;
        } else if ("-T".equals(flag)) {
          runtimeSec = arg;
        } else if ("-o".equals(flag)) {
          outputTypes = arg;
        } else if ("-c".equals(flag)) {
          if (arg != null) {
            csvFieldSeparator = arg.charAt(0);
          } else {
            throw new IllegalArgumentException("Null CSV field separator.");
          }
        } else {
          throw new IllegalArgumentException("Unexpected flag: " + flag);
        }
      }

      // Validations
      if (null == docsFileName) {
        throw new IllegalArgumentException("No documents specified.");
      }

      if (null != moduleSrcLoc && null != moduleNames) {
        throw new IllegalArgumentException(
            "moduleSrcURIs and moduleNames are mutually exclusive parameters. Provide a value for exactly one of them.");
      }

      // Verify paths.
      File docsFile = new File(docsFileName);
      if (!docsFile.exists()) {
        throw new IllegalArgumentException(
            String.format("Documents file %s does not exist", docsFileName));
      }

      // Create tokenizer configuration
      TokenizerConfig tokenizerConfig = null;
      try {
        Class<?> tokenizerConfigClass = Class.forName(tokenizer);
        tokenizerConfig = (TokenizerConfig) tokenizerConfigClass.newInstance();
      } catch (final Exception e) {
        throw new TextAnalyticsException(e);
      }

      // Now instantiate the AQLProfiler class and put all the requested
      // arguments into place.
      AQLProfiler profiler = null;
      if (null != moduleSrcLoc) { // instantiate profiler, to profile modules in source form
        String[] inputModuleURIs = StringUtils.split(moduleSrcLoc, SearchPath.PATH_SEP_CHAR);

        // TODO: provide support to populate external artifacts
        profiler = AQLProfiler.createSourceModulesProfiler(inputModuleURIs, modulePath, null,
            tokenizerConfig);
      } else if (null != moduleNames) {
        // Ah, No idea where to load the given module name from
        if (null == modulePath) {
          throw new IllegalArgumentException(
              "While profiling compiled modules, modulePath is a required parameter.");
        }

        String[] moduleNameList = StringUtils.split(moduleNames, SearchPath.PATH_SEP_CHAR);

        // TODO: provide support to populate external artifacts
        profiler = AQLProfiler.createCompiledModulesProfiler(moduleNameList, modulePath, null,
            tokenizerConfig);
      } else {
        throw new IllegalArgumentException(
            "There is nothing to profile, both moduleSrcURIs and <moduleNames> parameters are null.");
      }

      // Initialize profiler
      profiler.setMinRuntimeSec(Integer.parseInt(runtimeSec));

      // The user has requested to enable a specific set of outputs
      String[] outTypes = null;
      if (null != outputTypes) {
        outTypes = outputTypes.split(";");
      }

      // Now that everything is set up, we can run the profiler.
      profiler.run(docsFile, LangCode.strToLangCode(language), outTypes, csvFieldSeparator);
      profiler.dumpTopViews();

      Log.info("%d char in %1.2f sec --> %1.2f kchar/sec\n", profiler.getTotalChar(),
          profiler.getRuntimeSec(), //
          profiler.getCharPerSec() / 1024);

    } catch (IllegalArgumentException e) {
      System.err.printf("\n%s\n", e.getMessage());
      System.err.printf("\n%s\n", USAGE);
    }
  }

  /*
   * IMPLEMENTATION
   */

  private static final int SAMPLE_INTERVAL_MS = 1;

  /** How many views we print stats for. */
  private static final int NUM_VIEWS_TO_PRINT = 25;

  /** Default value of {@link #minRuntimeSec} */
  private static final int DEFAULT_MIN_RUNTIME_SEC = 1;

  /**
   * Minimum time (in seconds) that a profiling run should take. If the profiler runs out of
   * documents before this time has elapsed, it will start the document set over.
   */
  private int minRuntimeSec = DEFAULT_MIN_RUNTIME_SEC;

  /**
   * list of URIs, where each URI points to the top-level source directory of a module that is to be
   * compiled.
   */
  private String[] inputModuleURIs;

  /**
   * a semicolon-separated list of directories and/or .jar/.zip archives containing all the
   * module(s).
   */
  private String modulePath;

  /** list of module names to be loaded by the profiler. */
  private String[] moduleNames;

  /** Operator graph to profile. */
  private OperatorGraph og;

  private TokenizerConfig tokenizerCfg = new TokenizerConfig.Standard();

  /** External artifacts consumed by the operator graph. */
  private ExternalTypeInfo eti;

  /**
   * Object to use to divide long documents into shorter ones, or NULL to keep all documents intact.
   */
  private Chunker chunker = null;

  /** Average execution time on each document, in seconds. */
  private double[] docTimesSec;

  /**
   * String labels of the documents; indexes are the same as those in docTimesSec.
   */
  private String[] docLabels;

  /**
   * Sizes of the documents in characters; indexes are the same as those in docTimesSec.
   */
  private int[] docLengths;

  private String fileEncoding = "UTF-8";

  private int progressInterval = 1000;

  /** Background thread that collects samples of execution time. */
  private Sampler sampler = null;
  private Thread sampThread = null;

  /** If true, use shared regex matching. */
  private boolean useSRM = true;

  /**
   * Flag to enable stopping the profiler in the middle of execution. Volatile because it is
   * accessed by multiple threads. There is no API to restart the profiler once it has been stopped.
   */
  private volatile boolean stopProfiling = false;

  /** Flag to signal that the extractor to profile is in compiled or source form. */
  private boolean isCompiled;

  /** Suppress profiler instance creation. */
  private AQLProfiler() {}

  /**
   * This method returns an instance of profiler, to profile given loaded {@link OperatorGraph}
   * instance.
   * 
   * @param operatorGraph loaded Operator graph instance
   * @return an instance of profiler
   */
  public static AQLProfiler createProfilerFromOG(OperatorGraph operatorGraph) {
    if (null == operatorGraph)
      throw new IllegalArgumentException("Operator graph cannot be null");

    // create profiler instance
    AQLProfiler newProfiler = new AQLProfiler();
    newProfiler.og = operatorGraph;
    newProfiler.tokenizerCfg = operatorGraph.getTokenizerConfig();
    return newProfiler;
  }

  /**
   * This method returns an instance of profiler, which is capable of profiling compiled modules.
   * 
   * @param moduleNames name of the module(s) to be loaded. (Additional modules may be loaded if
   *        they are required by modules in moduleNames)
   * @param modulePath a semicolon-separated list of directories and/or .jar/.zip archives
   *        containing all the module(s) to be loaded
   * @param externalTypeInfo information encapsulating the content of all external dictionaries and
   *        tables used by the module(s)
   * @param tokenizerConfig tokenization configuration to be used while performing extraction; if
   *        <code>null</code> system will use the default built-in white space tokenization
   *        configuration
   * @return an instance of profiler
   */
  public static AQLProfiler createCompiledModulesProfiler(String[] moduleNames, String modulePath,
      ExternalTypeInfo externalTypeInfo, TokenizerConfig tokenizerConfig) {
    // Validate arguments
    if (null == moduleNames || moduleNames.length == 0) {
      throw new IllegalArgumentException(
          "At least one module is required to profile; moduleNames argument cannot be null or empty.");
    }
    if (null == modulePath) {
      throw new IllegalArgumentException("Provide non null value for modulePath argument");
    }

    // create profiler instance
    AQLProfiler newProfiler = new AQLProfiler();
    newProfiler.moduleNames = moduleNames;
    newProfiler.modulePath = modulePath;
    if (null != tokenizerConfig)
      newProfiler.tokenizerCfg = tokenizerConfig;
    newProfiler.eti = externalTypeInfo;

    // Set isCompiled 'true', to signal that the extractor to profile is in compiled form.
    newProfiler.isCompiled = true;

    return newProfiler;
  }

  /**
   * This method returns an instance of profiler, which is capable of profiling modular AQL source.
   * 
   * @param inputModuleURIs uri(s) to the directory containing module source
   * @param modulePath a semicolon-separated list of directories and/or .jar/.zip archives
   *        containing dependent modules referred during compilation
   * @param externalTypeInfo information encapsulating the content of all external dictionaries and
   *        tables used by the module(s)
   * @param tokenizerConfig profiler configuration to be used for module compilation, and later
   *        extraction; if <code>null</code> system will use the default built-in white space
   *        tokenization configuration
   * @return an instance of profiler
   */
  public static AQLProfiler createSourceModulesProfiler(String[] inputModuleURIs, String modulePath,
      ExternalTypeInfo externalTypeInfo, TokenizerConfig tokenizerConfig) {
    // Validate argument
    if (null == inputModuleURIs || inputModuleURIs.length == 0) {
      throw new IllegalArgumentException(
          "At least one URI to module source is required to profile; inputModuleURIs argument cannot be null or empty.");
    }

    // Create profiler instance
    AQLProfiler newProfiler = new AQLProfiler();
    newProfiler.inputModuleURIs = inputModuleURIs;
    newProfiler.modulePath = modulePath;
    if (null != tokenizerConfig)
      newProfiler.tokenizerCfg = tokenizerConfig;
    newProfiler.eti = externalTypeInfo;

    // Set isCompiled 'false', to signal that the extractor to profile is in source form.
    newProfiler.isCompiled = false;

    return newProfiler;
  }

  /**
   * Set the minimum duration (in seconds) for which profiler should run.If the profiler runs out of
   * documents before this time has elapsed, it will start the document set over.
   * 
   * @param minRuntimeSec minimum time (in seconds) that a profiling run should run
   */
  public void setMinRuntimeSec(int minRuntimeSec) {
    this.minRuntimeSec = minRuntimeSec;
  }

  /**
   * Set the byte encoding for the document collection to profile.
   * 
   * @param fileEncoding byte encoding to use for the input file for profiling
   */
  public void setFileEncoding(String fileEncoding) {
    this.fileEncoding = fileEncoding;
  }

  /**
   * Set the interval in which profiler reports the progress.
   * 
   * @param progressInterval how often (in documents) to report extractors progress
   */
  public void setProgressInterval(int progressInterval) {
    this.progressInterval = progressInterval;
  }

  /**
   * Use this method to provide the object, to divide long documents into smaller ones.
   * 
   * @param chunker Object to use to divide long documents into shorter ones, or NULL to keep all
   *        documents intact
   */
  public void setChunker(Chunker chunker) {
    this.chunker = chunker;
  }

  /**
   * Use this method to turn on shared regex matching when compiling modules.
   * 
   * @param useSRM true to use Shared Regex Matching when compiling the AQL modules for profiling
   */
  public void setUseSRM(boolean useSRM) {
    this.useSRM = useSRM;
  }

  /**
   * Instruct the profiler to stop profiling. Causes the {#run()} method to return either before
   * starting the sampler, or once the current document has been pushed through the operator graph.
   */
  public void stop() {
    stopProfiling = true;
  }

  /**
   * The time (in NS) that the most recent run started. Shared by the profiler and its background
   * stats collection thread.
   */
  private long startNs;

  /** Wall-clock time (in NS) of the most recent run. */
  private long wallNs;

  /** Number of characters of documents processed on the most recent run. */
  private long numChar;

  /** List of compiler warnings generated on the most recent run. */
  private ArrayList<CompilerWarning> warnings = new ArrayList<CompilerWarning>();

  /** Number of AQL statements that the Profiler compiled on the most recent run. */
  private int numStmts;

  public int getNumStmts() {
    return numStmts;
  }

  public double getRuntimeSec() {
    return wallNs / 1.0e9;
  }

  /**
   * @return number of characters processed on the last run
   */
  public long getTotalChar() {
    return numChar;
  }

  public double getCharPerSec() {
    return numChar / getRuntimeSec();
  }

  public static class RuntimeRecord {
    int numSamples = 0;
    double numSec = 0.0;
  }

  /**
   * @return performance counters from the most recent run, broken down by view
   */
  public HashMap<String, RuntimeRecord> getRuntimeByView() {
    HashMap<String, RuntimeRecord> ret = new HashMap<String, RuntimeRecord>();

    final boolean debug = false;

    // Sum up all the samples.
    for (ProfileRecord record : sampler.samples) {
      String viewName = record.viewName;

      if (debug && "Unlabeled Operators".equals(viewName)) {
        // Track missing entries.
        Log.debug("Got an unlabeled operator: %s", record.operator);
      }

      RuntimeRecord rr = ret.get(viewName);
      if (null == rr) {
        rr = new RuntimeRecord();
        ret.put(viewName, rr);
      }

      rr.numSamples++;
    }

    if (debug) {
      Log.debug("%d samples found no operator stack", sampler.getNumNullRecords());
    }

    double wallSec = wallNs / 1e9;
    double secPerSample = wallSec / sampler.getNumSamples();

    // Fill in the estimated wall-clock times.
    for (Entry<String, RuntimeRecord> entry : ret.entrySet()) {
      RuntimeRecord rr = entry.getValue();
      rr.numSec = rr.numSamples * secPerSample;
    }

    return ret;
  }

  /**
   * @return performance counters from the most recent run, broken down by operator type
   */
  public HashMap<String, RuntimeRecord> getRuntimeByOp() {
    HashMap<String, RuntimeRecord> ret = new HashMap<String, RuntimeRecord>();

    final boolean debug = true;

    // Sum up all the samples.
    for (ProfileRecord record : sampler.samples) {

      String opClassName;

      if (Operator.TOKENIZATION_OVERHEAD_NAME.equals(record.viewName)) {

        opClassName = Operator.TOKENIZATION_OVERHEAD_NAME;

      } else {
        opClassName = record.operator.getClass().getName();
      }

      RuntimeRecord rr = ret.get(opClassName);
      if (null == rr) {
        rr = new RuntimeRecord();
        ret.put(opClassName, rr);
      }

      rr.numSamples++;

    }

    if (debug) {
      Log.debug("%d samples found no operator stack", sampler.getNumNullRecords());
    }

    double wallSec = wallNs / 1e9;
    double secPerSample = wallSec / sampler.getNumSamples();

    // Fill in the estimated wall-clock times.
    for (Entry<String, RuntimeRecord> entry : ret.entrySet()) {
      RuntimeRecord rr = entry.getValue();
      rr.numSec = rr.numSamples * secPerSample;
    }

    return ret;
  }

  /**
   * Internal method that actually runs and profiles the extractor.<br/>
   * If required the method compiles the modules, and load them into operator graph.
   */
  private void run(File docsFile, LangCode language, String[] outputTypes, char csvFieldSeparator)
      throws Exception {
    boolean oldPrintWarnings = Log.getMsgTypeEnabled(MsgType.AQLPerfWarning);
    Log.setMsgTypeEnabled(MsgType.AQLPerfWarning, true);

    // Check that someone has not stopped the profiler
    if (stopProfiling) {
      return;
    }

    // Create a unique temporary directory under the system temp dir to compile module source.
    // Unique directory name is to avoid conflicts betweeen parallel profile invocations.
    File tmpDir = null;
    try {
      // Load extractor,if not loaded
      if (null == og) {
        String modulePath = this.modulePath;
        String[] moduleNames = this.moduleNames;

        // Compile extractor,if not compiled
        if (!isCompiled) {
          Log.info("Compiling modules: %s...", Arrays.toString(inputModuleURIs));

          tmpDir = File.createTempFile(System.currentTimeMillis() + "", "");
          tmpDir.delete();
          tmpDir.mkdirs();

          // prepare compilation parameters
          CompileAQLParams params = new CompileAQLParams();
          params.setInputModules(inputModuleURIs);
          params.setModulePath(this.modulePath);
          params.setPerformSRM(this.useSRM);
          params.setInEncoding(this.fileEncoding);
          // compile the input modules into temporary directory
          params.setOutputURI(tmpDir.toURI().toString());
          params.setTokenizerConfig(tokenizerCfg);

          CompilationSummary compilationSummary = CompileAQL.compile(params);

          // Store the number of statements and any compiler warnings that were generated
          this.numStmts = compilationSummary.getNumberOfViewsCompiled();
          this.warnings = (ArrayList<CompilerWarning>) compilationSummary.getCompilerWarning();
          this.numStmts = compilationSummary.getNumberOfViewsCompiled();

          Log.info("Compiled modules %s with %s AQL statements.",
              compilationSummary.getCompiledModules(), this.numStmts);
          this.dumpWarnings();

          // Add location to the recently compiled modules into module path
          if (null != modulePath)
            modulePath = String.format("%s;%s", modulePath, tmpDir.toURI().toString());
          else
            modulePath = tmpDir.toURI().toString();

          // reaching here implies,that the compilation succeeded;lets fetch the compiled module
          // names from the
          // temporary directory
          File[] files = tmpDir.listFiles();
          List<String> compiledModuleNames = new ArrayList<String>();
          for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".tam")) {
              // we expect module name and tam name to be same
              String tamName = file.getName();
              String moduleName = tamName.substring(0, tamName.indexOf(".tam"));
              compiledModuleNames.add(moduleName);
            }
          }

          moduleNames = compiledModuleNames.toArray(new String[0]);
        }

        // load compiled extractor
        og = OperatorGraph.createOG(moduleNames, modulePath, eti, tokenizerCfg);
      }
    } finally {
      if (null != tmpDir) {
        FileUtils.deleteDirectory(tmpDir);
      }
    }

    // Check that someone has not stopped the profiler
    if (stopProfiling) {
      return;
    }

    if (null != chunker) {
      og.setChunker(chunker);
    }

    // Scan through the documents to count them and save their names.
    int totalCharInDocs = countCharacters(docsFile, og, csvFieldSeparator);

    // Check that someone has not stopped the profiler
    if (stopProfiling) {
      return;
    }

    // Start off the background thread that will do the sampling.
    startSampling();

    Log.info("Starting profiling run.  Will run for at least %d seconds.", minRuntimeSec);

    // Start off a timer so that we can calibrate samples to seconds.
    // The timer is also used to ensure that our profiling run is
    // long enough.
    long elapsedNs = 0;
    int ndoc = 0;
    numChar = 0;

    int numIter = 0;

    while (elapsedNs < minRuntimeSec * 1e9) {

      int docIx = 0;

      // Open up a scan on the documents.
      DocReader docs = null;

      // Schema of the document, as expected by the loaded extractor
      TupleSchema expectedDocSchema = og.getDocumentSchema();

      // Flag to signal that the given document collection can contain tuples for external views
      boolean hasExtViewTuples = false;

      // Tuples for external views can come along with input document in json format, hence passing
      // the schema for
      // external views to document reader
      if (docsFile.getName().endsWith(Constants.JSON_EXTENSION)) {
        docs = new DocReader(docsFile, expectedDocSchema, getExternalViewsSchema(og));
        hasExtViewTuples = true;
      } else {
        docs = new DocReader(docsFile, expectedDocSchema, null, csvFieldSeparator);
      }

      if (language != null)
        docs.overrideLanguage(language);

      while (docs.hasNext()) {

        long beforeNs = System.nanoTime();

        // Read document tuple and associated external views tuples; external views are per
        // document.
        Tuple docTuple = docs.next();

        Map<String, TupleList> extViewTups = null;
        if (hasExtViewTuples) {
          extViewTups = docs.getExtViewTups();
        }

        // Extract
        og.execute(docTuple, outputTypes, extViewTups);

        // Increment the time counter for the current document
        docTimesSec[docIx] += (System.nanoTime() - beforeNs) / 1e9;

        ndoc++;
        docIx++;
        if (0 == ndoc % progressInterval) {
          long curNs = System.nanoTime();
          double curSec = (curNs - startNs) / 1e9;
          Log.info("Processed %d docs in %1.2f sec", ndoc, curSec);
        }

        // Check that someone has not stopped the profiler
        if (stopProfiling)
          break;
      }
      docs.remove();

      long endNs = System.nanoTime();
      elapsedNs = endNs - startNs;

      numChar += totalCharInDocs;

      numIter++;

      // Check that someone has not stopped the profiler. We make sure that if the sampler was
      // started, at least one
      // document is processed to avoid exceptions in the code that computes statistics
      if (stopProfiling)
        break;
    }

    // Stop the background thread and collect profiling information.
    stopSampling();

    Log.info("Gathered %d samples in %1.2f sec", sampler.getNumSamples(), wallNs / 1e9);

    Log.info("Total %d post-rewrite views with at least one sample", getRuntimeByView().size());

    // Normalize document running times to the number of times we ran
    // through the docs.
    for (int i = 0; i < docTimesSec.length; i++) {
      docTimesSec[i] /= numIter;
    }

    // Restore global settings.
    Log.setMsgTypeEnabled(MsgType.AQLPerfWarning, oldPrintWarnings);
    // MatchesRegex.PRINT_RSR_WARNINGS = oldPrintWarnings;
  }

  /**
   * Count the number of characters in each document of the input document collection. Returns the
   * count of characters in all fields of type Text, including those in external views.
   */
  private int countCharacters(File docsFile, OperatorGraph og, char csvFieldSeparator)
      throws Exception {
    int totalCharInDocs = 0;

    // Open up a scan on the documents.
    DocReader docs = null;
    boolean jsonDocs = false, csvDocs = false;

    // for JSON document collection, there is a special reader to read tuples for external views
    if (docsFile.getName().endsWith(Constants.JSON_EXTENSION)) {
      jsonDocs = true;
      docs = new DocReader(docsFile, og.getDocumentSchema(), getExternalViewsSchema(og));
    } else if (docsFile.getName().endsWith(Constants.CSV_EXTENSION)) {
      csvDocs = true;
      docs = new DocReader(docsFile, og.getDocumentSchema(), null, csvFieldSeparator);
    } else {
      // For non json/csv document collection- its better to read with the default document schema
      // (with label),instead
      // of custom schema( from OG- which may or may not have label); this document label will be
      // used while dumping top
      // 25 document by measured running time
      docs = new DocReader(docsFile);
    }

    TupleSchema docSchema = docs.getDocSchema();

    // a list of accessors for all fields of type Text in the schema
    ArrayList<TextGetter> textGetters = DocUtils.getAllTextGetters(docSchema);

    FieldGetter<Text> getLabel = null;
    if (docSchema.containsField(Constants.LABEL_COL_NAME)) {
      getLabel = docSchema.textAcc(Constants.LABEL_COL_NAME);
    }

    ArrayList<String> labelsList = new ArrayList<String>();
    ArrayList<Integer> lengthsList = new ArrayList<Integer>();

    Log.info("Counting documents in '%s'...", docsFile);
    int docIndex = 0;
    while (docs.hasNext()) {
      docIndex++;
      Tuple docTup = docs.next();

      String docText = null;
      int docTextLen = 0;

      // iterate over every field of type Text, counting characters
      for (TextGetter textGetter : textGetters) {
        docText = textGetter.getVal(docTup).getText();
        docTextLen += docText.length();
      }

      // iterate over every field of type Text in external views, counting characters
      if (jsonDocs) {
        Map<String, TupleList> extViewTups = docs.getExtViewTups();

        if (extViewTups != null) {
          for (TupleList tuples : extViewTups.values()) {
            docTextLen += countTupleChars(tuples);
          }
        }
      }

      // docTextLen now contains the sum of the lengths of all fields of type Text for this document
      // tuple + associated
      // external views
      lengthsList.add(docTextLen);

      totalCharInDocs += docTextLen;

      String docLabel = null;

      // For json/csv document, we always use index of the json/csv record in the document as the
      // label, irrespective of
      // the label provided in the json/csv record
      if (jsonDocs || csvDocs) {
        docLabel = Integer.toString(docIndex);
      } else {
        if (null == getLabel) {
          // Even for two column del file, we use the index of the record in the del file as the
          // label
          docLabel = Integer.toString(docIndex);
        } else {
          Text labelSpan = getLabel.getVal(docTup);
          if (null != labelSpan) {
            docLabel = getLabel.getVal(docTup).getText();
          } else {
            docLabel = Integer.toString(docIndex);
          }
        }
      }
      labelsList.add(docLabel);
    }
    docs.remove();

    docLabels = new String[labelsList.size()];
    labelsList.toArray(docLabels);

    docLengths = new int[lengthsList.size()];
    for (int i = 0; i < docLengths.length; i++) {
      docLengths[i] = lengthsList.get(i);
    }

    docTimesSec = new double[labelsList.size()];
    return totalCharInDocs;
  }

  /** Start off the profiler's background sampling thread. */
  public void startSampling() {
    sampler = new Sampler(og);
    sampThread = new Thread(sampler);
    sampThread.start();

    startNs = System.nanoTime();
  }

  /** Stop the background thread that collects samples of execution time. */
  public void stopSampling() {
    if (null == sampler) {
      throw new RuntimeException("Called stopSampling() without calling startSampling()");
    }

    if (null == sampThread) {
      throw new RuntimeException("Called stopSampling() after sampling thread already stopped");
    }

    sampler.setDone();
    try {
      sampThread.join();
      sampThread = null;
    } catch (InterruptedException e) {
      Log.log(MsgType.AQLRuntimeWarning, "Couldn't stop sampling thread");
      e.printStackTrace();
    }

    long curNs = System.nanoTime();
    wallNs = curNs - startNs;
  }

  private static class Sampler implements Runnable {

    private final ArrayList<ProfileRecord> samples = new ArrayList<ProfileRecord>();

    /** Number of times we got no record back when we asked for a sample. */
    private int numNullRecords = 0;

    /** The actual operator graph that we collect information from. */
    private final OperatorGraph og;

    /** Flag that is set to false when the plan has finished executing */
    private volatile boolean done = false;

    public Sampler(OperatorGraph og) {
      this.og = og;
    }

    public void setDone() {
      this.done = true;
    }

    public int getNumSamples() {
      return samples.size() + numNullRecords;
    }

    public int getNumNullRecords() {
      return numNullRecords;
    }

    @Override
    public void run() {

      Log.info("Sampler starting...");

      while (false == done) {
        ProfileRecord sample = ((OperatorGraphImpl) og).getProfileRecAtLoc(0);
        if (null == sample) {
          numNullRecords++;
        } else {
          samples.add(sample);
        }

        try {
          Thread.sleep(SAMPLE_INTERVAL_MS);
        } catch (InterruptedException e) {
          // Just keep going.
        }
      }

      Log.info("Sampler finished.");
    }
  }

  /**
   * Comparator for sorting profile records by corrected exec time, then number of calls. Used in
   * {@link #profile()} method.
   */
  static class compareByTime implements Comparator<Entry<String, RuntimeRecord>> {

    @Override
    public int compare(Entry<String, RuntimeRecord> p1, Entry<String, RuntimeRecord> p2) {
      return p1.getValue().numSamples - p2.getValue().numSamples;
    }
  }

  /**
   * Print out the top k views from the last profiling run to standard err. Calls
   * {@link#dumpTopViews(StringBuffer)} to do all the wrok.
   */
  public void dumpTopViews() {
    StringBuffer sb = new StringBuffer();
    dumpTopViews(sb);
    Log.info("\n%s", sb.toString());
  }

  /**
   * Print out the top k views from the last profiling run in a StringBuffer.
   */
  public void dumpTopViews(StringBuffer sb) {

    if (null != sampThread || false == sampler.done) {
      throw new RuntimeException("Tried to dump top views without stopping sampling thread.");
    }

    // Gather statistics from the run.
    HashMap<String, RuntimeRecord> times = getRuntimeByView();

    // Sort by execution time.
    ArrayList<Entry<String, RuntimeRecord>> sorted = new ArrayList<Entry<String, RuntimeRecord>>();
    sorted.addAll(times.entrySet());

    Collections.sort(sorted, new compareByTime());

    // Count up the total number of samples, including the ones for which
    // there was no record.
    int numSamples = sampler.getNumSamples();
    int numNonNullSamples = sampler.getNumSamples() - sampler.getNumNullRecords();

    int numViewsToPrint = Math.min(sorted.size(), NUM_VIEWS_TO_PRINT);
    String title = String.format("Top %d (max %s) Post-rewrite Views by Execution Time:",
        numViewsToPrint, NUM_VIEWS_TO_PRINT);
    for (int i = 0; i < title.length(); i++) {
      // System.err.print("-");
      sb.append("-");
    }
    // System.err.print("\n");
    sb.append("\n");
    // System.err.printf("%s\n", title);
    sb.append(String.format("%s\n", title));
    for (int i = 0; i < title.length(); i++) {
      // System.err.print("-");
      sb.append("-");
    }
    // System.err.print("\n");
    sb.append("\n");

    // Print out the header.
    // System.err.printf("%50s %8s %8s %10s\n", "View Name", "Samples",
    // "Seconds", "% of Time");
    sb.append(String.format("%50s %8s %8s %10s\n", "View Name", "Samples", "Seconds", "% of Time"));

    // System.err.printf("%50s------------------------------\n",
    // "---------");
    sb.append(String.format("%50s------------------------------\n", "---------"));

    // Print out the top k running times.
    int countedSamp = 0;
    for (int i = 0; i < numViewsToPrint; i++) {
      // for (Entry<String, RuntimeRecord> entry : sorted) {
      Entry<String, RuntimeRecord> entry = sorted.get(sorted.size() - numViewsToPrint + i);

      String viewName = entry.getKey();
      double sec = entry.getValue().numSec;
      int samp = entry.getValue().numSamples;
      double pct = 100.0 * samp / numNonNullSamples;
      // System.err.printf("%50s %8d %8.2f %10.2f\n", viewName, samp, sec,
      // pct);
      sb.append(String.format("%50s %8d %8.2f %10.2f\n", viewName, samp, sec, pct));

      countedSamp += entry.getValue().numSamples;
    }

    dumpFooter(sb, numSamples, numNonNullSamples, numViewsToPrint, countedSamp);
  }

  /**
   * Print out the top k operator types from the last profiling run to standard err. Calls
   * {@link#dumpTopOperators(StringBuffer)} to do all the wrok.
   */
  public void dumpTopOperators() {
    StringBuffer sb = new StringBuffer();
    dumpTopOperators(sb);
    Log.info("\n%s", sb.toString());
  }

  /**
   * Print out the top k operator types from the last profiling run in a StringBuffer.
   */
  public void dumpTopOperators(StringBuffer sb) {

    // Gather statistics from the run.
    HashMap<String, RuntimeRecord> times = getRuntimeByOp();

    // Sort by execution time.
    ArrayList<Entry<String, RuntimeRecord>> sorted = new ArrayList<Entry<String, RuntimeRecord>>();
    sorted.addAll(times.entrySet());

    Collections.sort(sorted, new compareByTime());

    String title = String.format("Top Operators by Execution Time");
    for (int i = 0; i < title.length(); i++) {
      // System.err.print("-");
      sb.append("-");
    }
    sb.append(String.format("\n%s\n", title));
    for (int i = 0; i < title.length(); i++) {
      // System.err.print("-");
      sb.append("-");
    }
    sb.append("\n");

    // Count up the total number of samples, including the ones for which
    // there was no record.
    int numSamples = sampler.getNumSamples();
    int numNonNullSamples = sampler.getNumSamples() - sampler.getNumNullRecords();

    // Print out the header.
    // System.err.printf("%50s %8s %8s %10s\n", "Operator", "Samples",
    // "Seconds", "% of Time");
    sb.append(String.format("%50s %8s %8s %10s\n", "Operator", "Samples", "Seconds", "% of Time"));

    // System.err.printf("%50s-----------------------------\n",
    // "---------");
    sb.append(String.format("%50s-----------------------------\n", "---------"));

    int numOpTypes = 0;

    // Print out the top k running times.
    int countedSamp = 0;
    for (Entry<String, RuntimeRecord> entry : sorted) {
      String viewName = entry.getKey();
      double sec = entry.getValue().numSec;
      int samp = entry.getValue().numSamples;
      double pct = 100.0 * samp / numSamples;
      // System.err.printf("%50s %8d %8.2f %10.2f\n", viewName, samp,
      // sec,pct);

      sb.append(String.format("%50s %8d %8.2f %10.2f\n", viewName, samp, sec, pct));

      countedSamp += entry.getValue().numSamples;
      numOpTypes++;
    }

    dumpFooter(sb, numSamples, numNonNullSamples, numOpTypes, countedSamp);
  }

  /**
   * Print the footer (with a summary of total samples) that goes at the bottom of a "top views" or
   * "top operators" report.
   */
  private void dumpFooter(StringBuffer sb, int numSamples, int numNonNullSamples, int numHotspots,
      int countedSamp) {
    // Determine how many of the samples were outside SystemT.
    int numOutsideSamp = numSamples - numNonNullSamples;
    double outsidePct = 100.0 * numOutsideSamp / numSamples;

    int numNonHotspotSamp = numNonNullSamples - countedSamp;
    double nonHotspotPct = 100.0 * numNonHotspotSamp / countedSamp;

    // Put together the footer for the profile report.
    String firstLine =
        String.format("%d samples total; %d samples (%1.1f%%) outside Text Analytics engine.",
            numSamples, numOutsideSamp, outsidePct);
    String secondLine = String.format(
        "%d samples (%1.1f%%) inside Text Analytics engine but outside top %d hot spots.",
        numNonHotspotSamp, nonHotspotPct, numHotspots);

    int footerLen = Math.max(firstLine.length(), secondLine.length());
    for (int i = 0; i < footerLen; i++) {
      // System.err.print("-");
      sb.append("-");
    }
    // System.err.printf("\n%s\n%s\n", firstLine, secondLine);
    sb.append(String.format("\n%s\n%s\n", firstLine, secondLine));
    for (int i = 0; i < footerLen; i++) {
      // System.err.print("-");
      sb.append("-");
    }
    // System.err.print("\n");
    sb.append("\n");
  }

  /**
   * Print out to standard err the compiler warnings generated when compiling input AQL. Calls
   * {@link#dumpTopDocuments(StringBuffer)} to do all the work.
   */
  public void dumpWarnings() {
    StringBuffer sb = new StringBuffer();
    dumpWarnings(sb);
    Log.info("%s", sb.toString());
  }

  /**
   * Print out to standard err the compiler warnings generated when compiling input AQL.
   */
  public void dumpWarnings(StringBuffer sb) {
    for (CompilerWarning w : this.warnings) {
      sb.append(w);
      sb.append("\n");
    }

  }

  /**
   * Print out to standard err the top 25 documents by measured running time. Note that, in order to
   * get an accurate picture from this report, you will probably need to run the documents through
   * 10 or more times. Calls {@link#dumpTopDocuments(StringBuffer)} to do all the work.
   */
  public void dumpTopDocuments() {
    StringBuffer sb = new StringBuffer();
    dumpTopDocuments(sb);
    Log.info("\n%s", sb.toString());
  }

  /**
   * Print out the top 25 documents by measured running time. Note that, in order to get an accurate
   * picture from this report, you will probably need to run the documents through 10 or more times.
   */
  public void dumpTopDocuments(StringBuffer sb) {

    // Sort the documents by running time. There may be ties, so we do
    // things the hard way, using arrays and custom comparison functions.
    // First, create an array where each element represents an index of
    // this.docTimesSec.
    Integer[] docIndices = new Integer[docLabels.length];
    for (int i = 0; i < docIndices.length; i++) {
      docIndices[i] = i;
    }

    // K, as in "top K"
    final int K = 25;

    // Print out the top K documents by average running time
    {
      // Sort the array with a custom comparison function that
      // actually compares time measurements.
      Comparator<Integer> avgTimeComp = new Comparator<Integer>() {
        @Override
        public int compare(Integer o1, Integer o2) {
          double val1 = docTimesSec[o1];
          double val2 = docTimesSec[o2];

          // We want to sort in descending order
          if (val1 < val2) {
            return 1;
          } else if (val1 > val2) {
            return -1;
          } else {
            return 0;
          }
        }
      };

      Arrays.sort(docIndices, avgTimeComp);

      // Print out the header.
      // System.err.printf("Top %d documents by running time:\n", K);
      String title = String.format("\nTop %d documents by running time:\n", K);
      for (int i = 0; i < title.length(); i++) {
        sb.append("-");
      }
      sb.append(String.format("%s", title));
      for (int i = 0; i < title.length(); i++) {
        sb.append("-");
      }
      sb.append("\n");

      // System.err.printf("%6s %55s %16s\n", "Rank", "Document Label",
      // "Avg. time (sec)");
      sb.append(String.format("%6s %55s %16s\n", "Rank", "Document Label", "Avg. time (sec)"));
      // System.err.printf("%6s %55s ----------------\n", "----",
      // "-----------------");
      sb.append(String.format("%6s %55s ----------------\n", "----", "-----------------"));

      // Print out the top k documents. We sorted in descending order, so
      // the
      // first 25 are the most expensive.
      for (int i = 0; i < K && i < docIndices.length; i++) {

        int docIx = docIndices[i];
        double runningTimeSec = docTimesSec[docIx];
        String label = docLabels[docIx];

        // System.err.printf("%6s %55s %16.2f\n", i + 1, label,
        // runningTimeSec);
        sb.append(String.format("%6s %55s %16.2f\n", i + 1, label, runningTimeSec));

      }
    }

    // Print out the top K documents by sec/kb
    {
      // Use a comparator that compares normalized running times
      Comparator<Integer> normTimeComp = new Comparator<Integer>() {
        @Override
        public int compare(Integer o1, Integer o2) {
          double time1 = docTimesSec[o1];
          double time2 = docTimesSec[o2];
          double len1 = docLengths[o1];
          double len2 = docLengths[o2];

          double norm1 = time1 / len1;
          double norm2 = time2 / len2;

          // We want to sort in descending order
          if (norm1 < norm2) {
            return 1;
          } else if (norm1 > norm2) {
            return -1;
          } else {
            return 0;
          }
        }
      };

      Arrays.sort(docIndices, normTimeComp);

      // Filter out documents that are < 1 kb
      ArrayList<Integer> topK = new ArrayList<Integer>();
      int ix = 0;
      while (ix < docIndices.length && topK.size() < K) {
        int docIx = docIndices[ix];
        int len = docLengths[docIx];
        if (len >= 1024) {
          topK.add(docIx);
        }
        ix++;
      }

      // Print out the header.
      // System.err.printf(
      // "\nTop %d documents over 1K by normalized running time:\n",
      // K);
      String title = String.format("\nTop %d documents over 1KB by normalized running time:\n", K);
      sb.append("\n");
      for (int i = 0; i < title.length(); i++) {
        sb.append("-");
      }
      sb.append(String.format("%s", title));
      for (int i = 0; i < title.length(); i++) {
        sb.append("-");
      }
      sb.append("\n");

      // System.err.printf("%6s %55s %16s\n", "Rank", "Document Label",
      // "Sec per MB");
      sb.append(String.format("%6s %55s %16s\n", "Rank", "Document Label", "Sec per MB"));
      // System.err.printf("%6s %55s ----------------\n", "----",
      // "-----------------");
      sb.append(String.format("%6s %55s ----------------\n", "----", "-----------------"));

      // Print out the top k documents. We sorted in descending order, so
      // the first K are the most expensive.
      for (int i = 0; i < K && i < topK.size(); i++) {

        int docIx = topK.get(i);
        double runningTimeSec = docTimesSec[docIx];
        double len = docLengths[docIx];
        String label = docLabels[docIx];

        double normRunTime = runningTimeSec / len * 1e6;

        // System.err.printf("%6s %55s %16.2f\n", i + 1, label,
        // normRunTime);
        sb.append(String.format("%6s %55s %16.2f\n", i + 1, label, normRunTime));
      }
    }
  }

  /**
   * Main "public" entry point to this class when calling it from Java code. Does a profiling run
   * and prints out the top 25 views by execution time.
   * 
   * @param docsFile document collection on which extraction should be performed
   * @param language language in which document should be read
   * @param outputTypes list of which output types to return, or null to return all output types
   * @throws Exception
   */
  public void profile(File docsFile, LangCode language, String[] outputTypes) throws Exception {
    profile(docsFile, language, outputTypes, Constants.DEFAULT_CSV_FIELD_SEPARATOR);
  }

  /**
   * Entry point to this class for being called by Java code. Used when running on in a CSV input
   * collection with a custom field separator. Does a profiling run and prints out the top 25 views
   * by execution time.
   * 
   * @param docsFile document collection on which extraction should be performed
   * @param language language in which document should be read
   * @param outputTypes list of which output types to return, or null to return all output types
   * @param csvFieldSeparator character used to separate fields in CSV input document collection
   * @throws Exception
   */
  public void profile(File docsFile, LangCode language, String[] outputTypes,
      char csvFieldSeparator) throws Exception {
    run(docsFile, language, outputTypes, csvFieldSeparator);

    dumpTopViews();
  }

  /**
   * Dump the plan for the current AQL file to an AOG file.
   * 
   * @param file where to dump the plan
   */
  // FIXME: Not sure what to dump here; need to discuss in today's meeting
  public void dumpPlanToFile(File file) throws Exception {

    // StringBuffer dataPathBuf = new StringBuffer ();
    //
    // if (null != dictPath) {
    // dataPathBuf.append (dictPath);
    // }
    //
    // if (null != includePath) {
    // dataPathBuf.append (";" + includePath);
    // }
    //
    // if (null != udfJarPath) {
    // dataPathBuf.append (";" + udfJarPath);
    // }
    //
    // String dataPath = dataPathBuf.length () > 0 ? dataPathBuf.toString () : null;
    //
    // // prepare Compiler parameters
    // CompileAQLParams compileParam = new CompileAQLParams (aqlfile, file, dataPath);
    //
    // // Compile
    // CompileAQL.compile (compileParam);

  }

  public class ProfileSummary {

    HashMap<String, RuntimeRecord> timesByView;
    HashMap<String, RuntimeRecord> timesByOp;

    /** Average execution time on each document, in seconds. */
    double[] docTimesSec;

    /**
     * String labels of the documents; indexes are the same as those in docTimesSec.
     */
    String[] docLabels;

    /**
     * Sizes of the documents in characters; indexes are the same as those in docTimesSec.
     */
    int[] docLengths;

    /** Number of samples collected by the sampling thread */
    public int numSamples;

    /** Number of statements compiled. */
    public int numStmts;
  }

  /**
   * Collect a summary of the profiler output. Note that these are raw outputs and are not sorted.
   * The caller is responsible for sorting and displaying the summary in the appropriate format.
   * 
   * @return
   */
  public ProfileSummary collectSummary() {
    ProfileSummary profSummary = new ProfileSummary();

    profSummary.numStmts = getNumStmts();

    profSummary.numSamples = sampler.getNumSamples();

    // Running time by view
    profSummary.timesByView = getRuntimeByView();

    // Running time by operator
    profSummary.timesByOp = getRuntimeByOp();

    /** Average execution time on each document, in seconds. */
    profSummary.docTimesSec = new double[docTimesSec.length];
    System.arraycopy(docTimesSec, 0, profSummary.docTimesSec, 0, docTimesSec.length);

    /**
     * String labels of the documents; indexes are the same as those in docTimesSec.
     */
    profSummary.docLabels = new String[docLabels.length];
    System.arraycopy(docLabels, 0, profSummary.docLabels, 0, docLabels.length);

    /**
     * Sizes of the documents in characters; indexes are the same as those in docTimesSec.
     */
    profSummary.docLengths = new int[docLengths.length];
    System.arraycopy(docLengths, 0, profSummary.docLengths, 0, docLengths.length);

    return profSummary;
  }

  /**
   * @return map of external view name vs their tuple schema.Where external view name pair consist
   *         of: <br>
   *         (1) view's aql name as defined in 'create external view ...' statement <br>
   *         (2) view's external name as defined in external_name clause
   * @throws Exception
   */
  private Map<Pair<String, String>, TupleSchema> getExternalViewsSchema(OperatorGraph og)
      throws Exception {
    Map<Pair<String, String>, TupleSchema> retVal =
        new HashMap<Pair<String, String>, TupleSchema>();
    String[] externalViewNames = og.getExternalViewNames();
    for (String evn : externalViewNames) {
      Pair<String, String> evnPair =
          new Pair<String, String>(evn, og.getExternalViewExternalName(evn));
      retVal.put(evnPair, og.getExternalViewSchema(evn));
    }

    return retVal;
  }

  /**
   * Sums up all the characters in the Text objects contained within an input TupleList
   * 
   * @param tl the TupleList whose characters we want to sum
   * @return computed sum of all characters in the Text objects within the input TupleList
   */
  private int countTupleChars(TupleList tl) {
    int sum = 0;

    AbstractTupleSchema schema = tl.getSchema();

    TLIter itr = tl.iterator();

    while (itr.hasNext()) {
      // Grab the next tuple from the tuple list
      Tuple tup = itr.next();

      // iterate through all fields of the tuple
      for (int i = 0; i < tup.size(); i++) {
        Object fieldData = schema.getCol(tup, i);

        // only add the number of characters in Text objects
        if (fieldData instanceof Text) {
          sum += ((Text) fieldData).toString().length();
        }
      }
    }

    return sum;
  }
}
