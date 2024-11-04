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
package com.ibm.avatar.algebra.util.test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Enumeration;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.tam.ModuleMetadata;
import com.ibm.avatar.aql.tam.ModuleMetadataImpl;
import com.ibm.avatar.logging.Log;

/**
 * Various convenience functions that are shared among the regression tests.
 * 
 */
public class TestUtils {

  /**
   * String that we add to the end of an HTML dump file after truncating it.
   */
  public static final String HTML_TAIL = "</body>\n</html>\n";

  private static final Pattern stringTypePattern = Pattern.compile(" => \"String\"");

  /**
   * The expected output files are truncated to save space. This constant holds the number of lines
   * the files are truncated to.
   */
  public static int EXPECTED_RESULTS_FILE_NUM_LINES = 1000;

  /**
   * Compare two files. At some point, this should be replaced with the Netbeans JUnit module, which
   * includes file comparison functionality.
   * 
   * @param linesToSkip how many lines to ignore (from both input files) before beginning the
   *        comparison; for use when the files are expected to differ slightly in the header
   * @param linesToCompare how many lines of the file to compare with each other, or -1 to look at
   *        both files to the end
   * @throws Exception
   */
  public static void compareFiles(File expectedFile, File actualFile, int linesToSkip,
      int linesToCompare) throws Exception {
    BufferedReader expectedIn = null;
    BufferedReader actualIn = null;
    try {
      final String ENCODING = Constants.ENCODING_UTF8;

      System.err.printf("Comparing file '%s' against expected file '%s'\n", actualFile.getPath(),
          expectedFile.getPath());

      expectedIn =
          new BufferedReader(new InputStreamReader(new FileInputStream(expectedFile), ENCODING));

      actualIn =
          new BufferedReader(new InputStreamReader(new FileInputStream(actualFile), ENCODING));

      int lineno = 0;

      if (-1 == linesToCompare) {
        linesToCompare = Integer.MAX_VALUE;
      }

      while (expectedIn.ready() && lineno < linesToCompare) {
        if (!actualIn.ready()) {
          // Ran out of input.
          throw new Exception(
              String.format("File '%s' truncated at line %d", actualFile.getPath(), lineno));
        }

        // Surround our read operations with try blocks so that we can
        // report more helpful error messages.
        String expectedLine, actualLine;
        try {
          expectedLine = expectedIn.readLine();
        } catch (Throwable t) {
          throw new Exception(String.format("Error reading line %d of 'expected' file '%s'", lineno,
              expectedFile.getPath()), t);
        }

        try {
          actualLine = actualIn.readLine();
        } catch (Throwable t) {
          throw new Exception(String.format("Error reading line %d of 'actual' file '%s'", lineno,
              actualFile.getPath()), t);
        }

        if ((lineno >= linesToSkip) && !expectedLine.equals(actualLine)) {
          Log.debug("Expected line:'%s'", StringUtils.escapeForPrinting(expectedLine));
          Log.debug("  Actual line:'%s'", StringUtils.escapeForPrinting(actualLine));
          throw new Exception(
              String.format("File '%s' differs from comparison file '%s'" + " on line %d",
                  actualFile.getPath(), expectedFile.getPath(), lineno));
        }
        lineno++;
      }

      // Check whether the results file has too many lines.
      if (lineno < linesToCompare && actualIn.ready()) {
        throw new Exception(String.format("File '%s' has more lines than comparison file '%s'",
            actualFile.getPath(), expectedFile.getPath()));
      }

    } finally {
      if (null != expectedIn)
        expectedIn.close();
      if (null != actualIn)
        actualIn.close();
    }
  }

  /**
   * Truncate an HTML dump file to the indicated number of lines, then add some HTML code to close
   * out the file.
   * 
   * @param f the file to truncate
   * @param nlines how many lines to leave after the initial truncate operation; the final number of
   *        lines in the file will be about 3 more, due to the additional "tail" code added.
   * @throws IOException
   */
  public static void truncateHTML(File f, int nlines) throws IOException {

    BufferedReader in =
        new BufferedReader(new InputStreamReader(new FileInputStream(f), Constants.ENCODING_UTF8));

    // Create a temp file
    File tmp = File.createTempFile("truncate", ".htm");

    Writer out =
        new OutputStreamWriter(new FileOutputStream(tmp.getPath(), true), Constants.ENCODING_UTF8);

    int lineno = 0;
    boolean keepGoing = true;
    while (keepGoing && lineno < nlines) {

      // System.err.printf("Read line %d/%d of input file '%s'\n",
      // lineno, nlines, f.getName());

      String line = in.readLine();
      if (null == line) {
        keepGoing = false;
      } else {
        out.write(line + "\n");
        lineno++;
      }
    }

    // If we truncated the file, add the tail that keeps the HTML valid.
    if (keepGoing) {
      out.write(HTML_TAIL);
    }
    out.close();
    in.close();

    // Now we can replace the original file with the temp file.
    // Need to do a second copy in order for this to work cross-platform.
    // System.err.printf("Moving %s to %s\n", tmp, f);

    boolean ret = f.delete();
    if (false == ret) {
      // System.err.printf("Couldn't delete file '%s'\n", f);
      throw new RuntimeException("Couldn't delete file " + f);
    }

    copyFile(tmp, f);
    tmp.delete();
    // if (!tmp.renameTo(f)) {
    // throw new IOException(String.format("Couldn't rename '%s' to '%s'",
    // tmp, f));
    // }
  }

  /**
   * Copy a file from one location to another; will not replace its target.
   * 
   * @param src source
   * @param dest destination location; must NOT exist
   * @throws IOException
   */
  private static void copyFile(File src, File dest) throws IOException {
    if (dest.exists()) {
      throw new IOException("Destination " + dest + " exists");
    }

    // Use FileInputStream and FileOutputStream to ensure a byte-for-byte
    // copy.
    FileInputStream in = new FileInputStream(src);
    FileOutputStream out = new FileOutputStream(dest);

    byte[] buf = new byte[1024];
    int nread;
    while (0 < (nread = in.read(buf))) {
      out.write(buf, 0, nread);
    }
    out.close();
    in.close();
  }

  // /**
  // * This method compares the given TAM files.
  // */
  // public static void compareTAMFiles (File expectedTAMFile, File actualTAMFile) throws Exception
  // {
  // File extractedActualTAM = null;
  // File extractedExpectedTAM = null;
  // File baseTempDir = null;
  // try {
  //
  // System.err.printf ("Comparing actual TAM file '%s' against expected TAM file '%s'\n",
  // actualTAMFile.getPath (),
  // expectedTAMFile.getPath ());
  //
  // String expectedModuleName = expectedTAMFile.getName ().substring (0, expectedTAMFile.getName
  // ().lastIndexOf ('.'));
  // String actualModuleName = actualTAMFile.getName ().substring (0, actualTAMFile.getName
  // ().lastIndexOf ('.'));
  //
  // // First compare module name
  // if (!expectedModuleName.equals (actualModuleName)) { throw new Exception ("TAMs belong to
  // different modules"); }
  //
  // baseTempDir = File.createTempFile ("TestUtils", "TAMCompare");
  // baseTempDir.delete ();
  // baseTempDir.mkdirs ();
  //
  // // Unjar expected TAM file under $temp/expected directory
  // extractedExpectedTAM = new File (baseTempDir, String.format ("expected%s%s", FILE_SEPARATOR,
  // expectedModuleName));
  // unJar (expectedTAMFile, extractedExpectedTAM);
  // List<File> expectedTAMContainedFiles = FileUtils.getAllChildFiles (extractedExpectedTAM);
  // Collections.sort (expectedTAMContainedFiles);
  //
  // // Unjar actual TAM file in $temp/actual directory
  // extractedActualTAM = new File (baseTempDir, String.format ("actual%s%s", FILE_SEPARATOR,
  // actualModuleName)).getCanonicalFile ();
  // unJar (actualTAMFile, extractedActualTAM);
  // List<File> actualTAMContainedFiles = FileUtils.getAllChildFiles (extractedActualTAM);
  // Collections.sort (actualTAMContainedFiles);
  //
  // // Iterate over files contained in both the TAMs, and compare there
  // // names and content.
  // for (int i = 0; i < expectedTAMContainedFiles.size (); i++) {
  //
  // String expectedEntry = expectedTAMContainedFiles.get (i).getName ();
  // String actualEntry = actualTAMContainedFiles.get (i).getName ();
  //
  // // Compare entry name
  // if (!expectedEntry.equals (actualEntry)) { throw new Exception (String.format (
  // "Actual TAM contains unexpected entry %s", actualEntry)); }
  //
  // // Compare contents of the entry
  // if (expectedEntry.equals ("metadata.xml")) {
  // compareModuleMetadata (expectedTAMContainedFiles.get (i), actualTAMContainedFiles.get (i));
  // }
  // else if (expectedEntry.endsWith (".jar")) {
  // System.out.println ("Not comparing Jar file: " + expectedEntry);
  // }
  // else {
  //
  // // TODO: WARNING: Special case and temporary.
  // // Added by Huaiyu Zhu 2013-09. Normalize return types for String and Text
  // // All occurrences of ' => "String"' changed to '=> "Text"' in aog files
  // // This piece of code should be removed once the new type system is established.
  // boolean convert_return_type_in_aom_from_string_to_text = false;
  //
  // if (convert_return_type_in_aom_from_string_to_text)
  // if (expectedEntry.endsWith (".aog")) {
  // convertStringTypeInAOGFile (extractedExpectedTAM, expectedEntry);
  // convertStringTypeInAOGFile (extractedActualTAM, actualEntry);
  // }
  // // End special case for string to text conversion.
  //
  // try {
  // compareFiles (expectedTAMContainedFiles.get (i), actualTAMContainedFiles.get (i), 0, -1);
  // }
  // catch (Exception e) {
  // throw new TextAnalyticsException (e, "Error while comparing TAM files %s and %s.",
  // expectedTAMFile,
  // actualTAMFile);
  // }
  // }
  // }
  // }
  // finally {
  // // delete extracted TAMs
  // if (null != extractedExpectedTAM) FileUtils.deleteDirectory (extractedExpectedTAM.getParentFile
  // ());
  // if (null != extractedActualTAM) FileUtils.deleteDirectory (extractedActualTAM.getParentFile
  // ());
  //
  // // delete the directory
  // if (baseTempDir != null) FileUtils.deleteDirectory (baseTempDir);
  // }
  // }
  //
  // /**
  // * Convert an AOG file to change String type to Text type.
  // *
  // * @param dir
  // * @param fileName
  // * @throws IOException
  // */
  // private static void convertStringTypeInAOGFile (File dir, String fileName) throws IOException
  // {
  // String string = FileUtils.read_string (new File (dir, fileName));
  // string = stringTypePattern.matcher (string).replaceAll (" => \"Text\"");
  // FileWriter out = new FileWriter (new File (dir, fileName));
  // out.write (string);
  // out.close ();
  // }

  /**
   * Compares contents of two metadata files, by performing an object comparison.
   * 
   * @param expected Expected metadata.xml file
   * @param actual Actual metadata.xml file
   * @throws Exception
   */
  public static void compareModuleMetadata(File expected, File actual) throws Exception {
    FileInputStream fisExpected = null;
    FileInputStream fisActual = null;

    try {
      fisExpected = new FileInputStream(expected);
      ModuleMetadata expectedMetadata = ModuleMetadataImpl.deserialize(fisExpected);

      fisActual = new FileInputStream(actual);
      ModuleMetadata actualMetadata = ModuleMetadataImpl.deserialize(fisActual);

      System.err.printf(
          "Comparing metadata object constructed from file '%s' against expected metadata object constructed from file '%s'\n",
          actual.getPath(), expected.getPath());

      // the following call would throw ModuleMetadataMismatchException, if the two metadata are not
      // equal
      expectedMetadata.equals(actualMetadata);
    } finally {
      if (fisExpected != null)
        fisExpected.close();
      if (fisActual != null)
        fisActual.close();

    }
  }

  /**
   * Compare two jar_index.csv files.
   *
   * @param linesToCompare how many lines of the file to compare with each other, or -1 to look at
   *        both files to the end
   * @throws Exception
   */
  public static void compareJarIndexCsv(File expectedFile, File actualFile, int linesToCompare)
      throws Exception {
    BufferedReader expectedIn = null;
    BufferedReader actualIn = null;
    try {
      final String ENCODING = Constants.ENCODING_UTF8;

      System.err.printf("Comparing file '%s' against expected file '%s'\n", actualFile.getPath(),
          expectedFile.getPath());

      expectedIn =
          new BufferedReader(new InputStreamReader(new FileInputStream(expectedFile), ENCODING));

      actualIn =
          new BufferedReader(new InputStreamReader(new FileInputStream(actualFile), ENCODING));

      int lineno = 0;

      if (-1 == linesToCompare) {
        linesToCompare = Integer.MAX_VALUE;
      }

      while (expectedIn.ready() && lineno < linesToCompare) {
        if (!actualIn.ready()) {
          // Ran out of input.
          throw new Exception(
              String.format("File '%s' truncated at line %d", actualFile.getPath(), lineno));
        }

        // Surround our read operations with try blocks so that we can
        // report more helpful error messages.
        String expectedLine, actualLine;
        try {
          expectedLine = expectedIn.readLine();
        } catch (Throwable t) {
          throw new Exception(String.format("Error reading line %d of 'expected' file '%s'", lineno,
              expectedFile.getPath()), t);
        }

        try {
          actualLine = actualIn.readLine();
        } catch (Throwable t) {
          throw new Exception(String.format("Error reading line %d of 'actual' file '%s'", lineno,
              actualFile.getPath()), t);
        }

        try {
          String[] expectedColumns = expectedLine.split(",");
          String[] actualColumns = actualLine.split(",");
          assert (expectedColumns.length == 2);
          assert (actualColumns.length == 2);
          assert (expectedColumns[0].equals(actualColumns[0]));

          String expectedFileName =
              expectedColumns[1].substring(1, expectedColumns[1].length() - 1);
          String actualFileName = actualColumns[1].substring(1, actualColumns[1].length() - 1);

          assert (TestHarness.removeUUIDAndExtension(expectedFileName)
              .equals(TestHarness.removeUUIDAndExtension(actualFileName)));
        } catch (Exception e) {
          Log.debug("Expected line:'%s'", StringUtils.escapeForPrinting(expectedLine));
          Log.debug("  Actual line:'%s'", StringUtils.escapeForPrinting(actualLine));
          throw new Exception(
              String.format("File '%s' differs from comparison file '%s'" + " on line %d",
                  actualFile.getPath(), expectedFile.getPath(), lineno));
        }
        lineno++;
      }

      // Check whether the results file has too many lines.
      if (lineno < linesToCompare && actualIn.ready()) {
        throw new Exception(String.format("File '%s' has more lines than comparison file '%s'",
            actualFile.getPath(), expectedFile.getPath()));
      }

    } finally {
      if (null != expectedIn)
        expectedIn.close();
      if (null != actualIn)
        actualIn.close();
    }
  }

  /**
   * Method to unjar the TAM file in the given location.
   * 
   * @throws IOException
   */
  public static void unJar(File tamFile, File targetLocation) throws IOException {
    // create directory to extract TAM
    targetLocation.mkdir();

    // extract tam
    JarFile jarFile = new JarFile(tamFile);
    Enumeration<JarEntry> e = jarFile.entries();

    while (e.hasMoreElements()) {
      JarEntry entry = e.nextElement();
      File destinationFilePath = new File(targetLocation, entry.getName());

      // create directories if required.
      destinationFilePath.getParentFile().mkdirs();

      // if the entry is directory, leave it. Otherwise extract it.
      if (entry.isDirectory()) {
        continue;
      } else {
        // Get the InputStream for current entry of the jar file using
        // InputStream getInputStream(Entry entry) method.
        BufferedInputStream bis = new BufferedInputStream(jarFile.getInputStream(entry));

        int b;
        byte buffer[] = new byte[1024];

        // read the current entry from the jar file, extract it and
        // write the extracted file.
        FileOutputStream fos = new FileOutputStream(destinationFilePath);
        BufferedOutputStream bos = new BufferedOutputStream(fos, 1024);

        while ((b = bis.read(buffer, 0, 1024)) != -1) {
          bos.write(buffer, 0, b);
        }

        // flush the output stream and close it.
        bos.flush();
        bos.close();

        // close the input stream.
        bis.close();
      }
    }
  }

  /**
   * Process a set of arguments, in the form "-x", "-x [value]", or "--word [value]", to a main
   * method.
   * 
   * @param args the raw arguments to the main method
   * @param possibleFlags a list of all the flags (in the form "-x" or "--word") that the program
   *        expects to see
   * @param argsExpected true if the corresponding flag should be followed by an argument string
   * @return a map from each flag to its value (if no value is provided, as in "-x", then the value
   *         returned is null)
   * @throws Exception on error
   */
  public static TreeMap<String, String> parseArgs(String[] args, final String[] possibleFlags,
      final boolean[] argsExpected) throws Exception {
    TreeMap<String, String> ret = new TreeMap<String, String>();

    // We can be in one of two states while running through the arguments
    // lisxt.
    final int READING_FLAG = 0;
    final int READING_FLAG_ARG = 1;
    int state = READING_FLAG;

    int flagIx = -1;

    int i = 0;
    while (i < args.length) {
      String arg = args[i];

      if (READING_FLAG == state) {

        // We're looking for one of the flags to be passed to the
        // program.
        boolean foundMatch = false;
        for (int j = 0; j < possibleFlags.length; j++) {
          if (possibleFlags[j].equals(arg)) {
            foundMatch = true;
            flagIx = j;
          }
        }

        if (false == foundMatch) {
          throw new Exception(String.format("Don't understand argument '%s'", arg));
        }

        String flag = possibleFlags[flagIx];
        if (ret.containsKey(flag)) {
          throw new Exception(String.format("Flag '%s' specified twice", flag));
        }

        // If we get here, we found a flag; advance to the next
        // argument.
        i++;
        if (argsExpected[flagIx]) {
          if (i >= args.length) {
            throw new Exception(String.format(
                "Ran off end of " + "program args looking for argument" + " to '%s' flag", flag));
          }

          state = READING_FLAG_ARG;
        } else {

          // No arguments expected for this flag.
          ret.put(possibleFlags[flagIx], null);
        }

      } else if (READING_FLAG_ARG == state) {
        // Looking for argument to the flag.
        String flag = possibleFlags[flagIx];
        ret.put(flag, arg);
        i++;
        state = READING_FLAG;

      } else {
        throw new RuntimeException("Don't know about state " + state);
      }
    }

    return ret;

  }

  /**
   * Returns the path of the given resource from the given classpath
   * 
   * @param classpath
   * @param resourceName
   * @return
   */
  public static String getResourcePath(String classpath, String resourceName) {

    // the path separator character is platform-dependent
    String[] paths = classpath.split(System.getProperty("path.separator"));
    for (String path : paths) {
      File f = FileUtils.createValidatedFile(path);
      if (f.getName().equalsIgnoreCase(resourceName))
        return path;
    }
    return null;

  }

}
