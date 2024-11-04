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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.test.TestUtils;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.compiler.Compiler;
import com.ibm.avatar.aql.tam.ModuleUtils;
import com.ibm.avatar.logging.Log;

/**
 * This class provides APIs to compile both modular and non-modular AQL files. The AQL files are
 * read from the local filesystem, and written back into the local filesystem as compiled module
 * files. Modular AQL files are compiled into Text Analytics Module files named after the module
 * name, as in <code>&lt;moduleName&gt;.tam</code>. Non-modular AQL files are compiled into
 * <code>genericModule.tam</code><br>
 * This class can also be invoked through its {@link #main(String[])} method. For sample usage,
 * invoke the <code>main()</code> method with no arguments.<br>
 * <b>Note</b>: Presently, there is no support for compiling AQL source code stored in a Hadoop
 * Distributed File System (HDFS).
 * <p>
 * <b>WARNING</b>: Support for non-modular AQL is retained for backward compatibility. However, it
 * may be removed in a future release.
 *
 */
public class CompileAQL {

  /**
   * Flag to turn on debug messages in {@link #compile(CompileAQLParams)}
   */
  private static final boolean debug = false;

  /**
   * Default tokenizer name that indicates 'use the standard (whitespace) tokenizer, not the
   * Multilingual tokenizer' when invoking the main method of this class
   */
  private static final String DEFAULT_TOKENIZER = TokenizerConfig.Standard.class.getName();

  /**
   * Default module path when invoking the main method of this class.
   */
  private static final String DEFAULT_MODULE_PATH = ".";

  /**
   * Usage for invoking the main method of this class.
   */
  private static final String USAGE = //
      String.format("Usage: java %s \n" //
          + "                   -s inputModules [ -p modulePath ] -o outputURI \n"//
          + "                 [ -t tokenizer ]  \n"//
          + "Where:\n"//
          + "      inputModules  is a list of one or more URIs of the source module(s)\n"//
          + "                      to compile, separated by '%c'.\n"//
          + "      modulePath    is a search path (one or more entries, separated by '%c')\n"//
          + "                      for finding dependent compiled modules.\n"//
          + "                      (default is '%s'). \n"//
          + "      outputURI     path to directory or .jar/.zip file on the local \n"//
          + "                      file system where the compiled module representation \n"//
          + "                      (.tam file) of each input module is sent to. \n"//
          + "      tokenizer     is the tokenizer configuration used for compiling dictionaries. \n"//
          + "                      Can be either:\n"//
          + "                      a. '%s' to use the Standard tokenizer, or\n"//
          + "                      b. The full name (in the form packageName.className) of a custom TokenizerConfig class \n"//
          + "                         for a Multilingual tokenizer/POS tagger\n",
          CompileAQL.class.getName(), //
          SearchPath.PATH_SEP_CHAR, //
          SearchPath.PATH_SEP_CHAR, //
          DEFAULT_MODULE_PATH, //
          DEFAULT_TOKENIZER);

  /**
   * Possible flags for the main method of this class.
   */
  private static final String[] POSSIBLE_FLAGS = {"-s", "-p", "-t", "-o"};

  /**
   * An array of boolean values specifying whether or not an argument is expected to follow the
   * corresponding flag; true at position i means that an argument is expected to follow after the
   * flag POSSIBLE_FLAGS[i].
   */
  private static final boolean[] ARGS_EXPECTED = {true, true, true, true, true};

  /**
   * Main method for invoking this class from a script. For sample usage, invoke the method with no
   * arguments.
   *
   * @param args
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
      String tokenizer = DEFAULT_TOKENIZER;
      String outputURI = null;
      String inputModules = null;
      String modulePath = new File(DEFAULT_MODULE_PATH).toURI().toString();

      // ...and override the defaults as requested.
      for (Map.Entry<String, String> e : argMap.entrySet()) {
        String flag = e.getKey();
        String arg = e.getValue();

        if ("-s".equals(flag)) {
          inputModules = arg;
        } else if ("-p".equals(flag)) {
          modulePath = arg;
        } else if ("-t".equals(flag)) {
          tokenizer = arg;
        } else if ("-o".equals(flag)) {
          outputURI = arg;
        } else {
          throw new IllegalArgumentException("Unexpected flag: " + flag);
        }
      }

      // Validation, first we need input modules
      if (null == inputModules) {
        throw new IllegalArgumentException("inputModules is a required argument.");
      }

      // Validate that an outputURI is provided, but don't validate whether it exists.
      // {@link CompileAQL#compile(CompileAQLParams)} does the validation for us
      if (null == outputURI) {
        throw new IllegalArgumentException("outputURI is a required argument.");
      }

      // Create the tokenizer configuration
      TokenizerConfig tokenizerConfig = null;
      try {
        Class<?> tokenizerConfigClass = Class.forName(tokenizer);
        tokenizerConfig = (TokenizerConfig) tokenizerConfigClass.newInstance();
      } catch (final Exception e) {
        throw new TextAnalyticsException(e);
      }

      // Now put all the requested arguments into place
      CompileAQLParams params = new CompileAQLParams();
      String[] inputModuleURIs = StringUtils.split(inputModules, SearchPath.PATH_SEP_CHAR);
      params.setInputModules(inputModuleURIs);
      params.setModulePath(modulePath);
      params.setOutputURI(outputURI);
      params.setTokenizerConfig(tokenizerConfig);

      // Compile
      Log.info("Compiling AQL with parameters:\n%s", params);
      compile(params);

    } catch (Exception e) {
      // Catching exception just to print usage info before throwing it again
      System.err.printf("\n%s\n", USAGE);
      e.printStackTrace();
      throw e;
    }
  }

  /**
   * Compiles both modular and non-modular AQLs and writes the compiled modules(in TAM file format)
   * to the location returned by {@link com.ibm.avatar.api.CompileAQLParams#getOutputURI()}. For
   * modular AQLs, the source of the AQL module(s) to compile is specified through the
   * {@link com.ibm.avatar.api.CompileAQLParams#setInputModules(String[])} API. For non-modular
   * AQLs, the source is specified through
   * {@link com.ibm.avatar.api.CompileAQLParams#setInputFile(File)} or the
   * {@link com.ibm.avatar.api.CompileAQLParams#setInputStr(String)} API, depending upon whether the
   * top-level AQL is contained in a {@link java.io.File} or a {@link java.lang.String}. Compilation
   * of modular and non-modular AQLs is mutually exclusive; that is, both modular and non-modular
   * AQLs cannot be compiled together. Non-modular AQLs are compiled into a special compiled module
   * called 'genericModule'.
   *
   * @param params parameters specifying the input, output, and various flags used during
   *        compilation
   * @return a {@link com.ibm.avatar.api.CompilationSummary} object containing: name of modules
   *         compiled, total number of views compiled, and all the encountered compilation warnings
   * @throws CompilerException for checked exceptions that occur during the compile phase
   * @throws TextAnalyticsException for unchecked exceptions
   */
  public static CompilationSummary compile(CompileAQLParams params)
      throws CompilerException, TextAnalyticsException {

    File compilationTempDir = null;
    try {
      // Validation specific to CompileAQLParams: at least one of inputFile, inputStr and
      // inputModules is non-null;
      // inputFile and inputModules are mutually exclusive; outputURI is non-null
      if (debug) {
        Log.debug("Validating compiler parameters: \n%s", params.toString());
      }
      params.validate();

      // Create the summary object for the given compilation request
      CompilationSummaryImpl summary = new CompilationSummaryImpl(params);

      Compiler compiler = null;

      if (true == params.isBackwardCompatibilityMode()) {

        if (debug) {
          Log.debug("Starting compilation in backward compatibility mode ...");
        }
        try {
          compiler = new Compiler();
          compiler.setSummary(summary);
          compiler.compile(params);
          compiler.updateSummary();
        } finally {
          if (compiler != null) {
            compiler.deleteTempDirectory();
          }
        }
      } else {

        if (debug) {
          Log.debug("Starting compilation in modular mode ...");
        }

        // Container to hold all the errors encountered during compilation
        CompilerException compilerErrors = new CompilerException(summary);

        // Prepare compilation order
        if (debug) {
          Log.debug("Preparing compilation order ...");
        }
        String[] inputModules = null;

        inputModules = ModuleUtils.prepareCompileOrder(params.getInputModules(),
            params.getModulePath(), compilerErrors);

        // Temporary directory shared between compilation of all modules passed to the current
        // compile() API call.
        // Dependent modules are looked up first within this temporary directory, before being
        // looked up in modulePath
        compilationTempDir = ModuleUtils.createCompilationTempDir();

        String tempDirURI = compilationTempDir.toURI().toString();
        if (debug) {
          Log.debug("Created compilation temp directory: %s", compilationTempDir);
        }

        // Create a compiler instance for each inputModule, because each compiler instance has its
        // own catalog object
        // and we do not want multiple modules to share same catalog during compilation.
        // In essence, we compile one module at a time with a new Compiler instance.
        compiler = null;
        for (String inputModule : inputModules) {
          try {

            if (debug) {
              Log.debug("Compiling module: %s", inputModule);
            }

            // Prepare compile param object with just one input module at a time.
            CompileAQLParams clonedParams = (CompileAQLParams) params.clone();
            clonedParams.setInputModules(new String[] {inputModule});

            compiler = new Compiler(tempDirURI);
            compiler.setSummary(summary);
            compiler.compile(clonedParams);
            compiler.updateSummary();
          } catch (CompilerException ce) {
            // Do not throw it now. Add them all to a global list of CompilerExceptions
            List<Exception> errors = ce.getAllCompileErrors();
            for (Exception error : errors) {
              compilerErrors.addError(error);
            }
          } catch (Exception e) {
            compilerErrors.addError(e);
          }

        }

        // we have a bunch of TAMs in the temp dir now
        // if the output URI is an archive file, pack them into the archive,
        // else the output URI is a directory, copy the temporary TAMs to the directory
        if (debug) {
          Log.debug("Packing compiled modules ...", tempDirURI);
        }
        try {
          String outputURI = params.getOutputURI();
          if (outputURI.endsWith(".jar") || outputURI.endsWith(".zip")) {
            CompileAQL.packDirToArchive(compilationTempDir, outputURI);
          } else {
            FileUtils.copyDir(compilationTempDir, new File(new URI(outputURI)));
          }
        } catch (Exception e) {
          compilerErrors.addError(e);
        }

        if (debug) {
          Log.debug("Deleting compilation temp directory ...", compilationTempDir);
        }
        // delete the intermediate temporary directory created to hold compiled modules
        FileUtils.deleteDirectory(compilationTempDir);

        // Throw Compiler Errors, if any
        if (compilerErrors.getAllCompileErrors().size() > 0) {
          // sort the list before throwing compilerErrors
          List<Exception> sortedErrors = compilerErrors.getSortedCompileErrors();

          compilerErrors.getAllCompileErrors().clear();

          for (Exception exception : sortedErrors) {
            compilerErrors.addError(exception);
          }

          throw compilerErrors;
        }
      }

      if (debug) {
        Log.debug("Done.");
      }

      return summary;
    } catch (Throwable t) {
      throw TextAnalyticsException.convertToTextAnalyticsException(t,
          TextAnalyticsException.ExceptionType.COMPILE_ERROR);
    } finally {
      // delete the temporary directory, if not deleted yet because of an unchecked exception
      if (null != compilationTempDir && compilationTempDir.exists())
        FileUtils.deleteDirectory(compilationTempDir);
    }
  }

  /**
   * Packs all the files in a directory into a JAR or ZIP file specified by the URI
   * 
   * @param tamsDir directory containing TAMs to put into JAR or ZIP file
   * @param destURI JAR or ZIP file URI (overwritten if already existing)
   * @throws IOException if an input or output exception occurred
   */
  private static void packDirToArchive(File tamsDir, String destURI) throws IOException {
    File dest;

    try {
      dest = new File(new URI(destURI));
    } catch (IllegalArgumentException e) {
      // Make the exception(s) thrown by File() more user-friendly
      throw new IOException(
          String.format("Error converting destination URI %s into a location on the filesystem: %s",
              destURI, e.getMessage()),
          e);
    } catch (URISyntaxException e) {
      // Make the exception(s) thrown by URI() more user-friendly
      throw new IOException(String.format("Invalid syntax for destination URI %s", destURI), e);
    }

    if (true == dest.exists()) {
      Log.info("destination URI file " + destURI + " already exists, overwriting...");
      dest.delete();
    }

    boolean jarOutput;
    if (destURI.endsWith(".jar")) {
      jarOutput = true;
    } else {
      jarOutput = false;
    }

    byte[] buf = new byte[1024];
    ZipOutputStream out;
    if (jarOutput) {
      out = new JarOutputStream(new FileOutputStream(dest));
    } else {
      out = new ZipOutputStream(new FileOutputStream(dest));
    }

    // compress the files
    for (File tam : tamsDir.listFiles()) {
      FileInputStream in = new FileInputStream(tam);

      // Add JAR Entry to output stream
      if (jarOutput) {
        out.putNextEntry(new JarEntry(tam.getName()));
      } else {
        out.putNextEntry(new ZipEntry(tam.getName()));
      }

      // Transfer bytes from the file to the ZIP file
      int len;
      while ((len = in.read(buf)) > 0) {
        out.write(buf, 0, len);
      }

      // Complete the entry
      out.closeEntry();
      in.close();

    }

    out.close();
  }

}
