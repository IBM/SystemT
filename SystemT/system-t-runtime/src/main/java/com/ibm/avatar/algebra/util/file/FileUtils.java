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
package com.ibm.avatar.algebra.util.file;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.logging.Log;

/**
 */
public class FileUtils {

  public static boolean ensure_dir(String dirname) throws IOException {
    return ensure_dir(createValidatedFile(dirname));
  }

  public static boolean ensure_dir(File dir) throws IOException {
    if (!dir.exists()) {
      boolean success = dir.mkdirs();
      if (!success)
        throw new IOException("Failed to create dir " + dir.getAbsolutePath());
    }
    return true;
  }

  @Deprecated
  public static boolean ensureReadableDir(File dir) throws IOException {
    return ensure_dir(dir) && dir.canRead();
  }

  @Deprecated
  public static boolean ensureWritableDir(File dir) throws IOException {
    return ensure_dir(dir) && dir.canWrite();
  }

  @Deprecated
  public static boolean ensureReadWritableDir(File dir) throws IOException {
    return ensure_dir(dir) && dir.canRead() && dir.canWrite();
  }

  @Deprecated
  public static boolean ensure_file(String dirname) throws IOException {
    return ensure_file(createValidatedFile(dirname));
  }

  @Deprecated
  public static boolean ensure_file(File file) throws IOException {
    if (!file.exists()) {
      file.createNewFile();
    }
    return true;
  }

  @Deprecated
  public static String get_dirname(String filename) {
    File file = createValidatedFile(filename);
    String dirname = file.getParent();
    if (dirname == null) {
      return ".";
    }
    return dirname;
  }

  @Deprecated
  public static String get_relative_filename(String filename) {
    File file = createValidatedFile(filename);
    return file.getName();

  }

  @Deprecated
  public static String read_string(String filename) throws IOException {
    return read_string(createValidatedFile(filename));
  }

  /**
   * @param file
   * @return
   * @throws IOException
   */
  @Deprecated
  public static String read_string(File file) throws IOException {
    Reader fr = new FileReader(file);
    BufferedReader br = new BufferedReader(fr);
    StringBuffer sb = new StringBuffer();
    String line;
    while ((line = br.readLine()) != null) {
      sb.append(line);
      sb.append("\n");
    }
    fr.close();
    br.close();
    return sb.toString();
  }

  public static String[] read_lines(String filename) throws IOException {
    return read_lines(createValidatedFile(filename));
  }

  public static String[] read_lines(File file) throws IOException {
    Reader fr = new FileReader(file);
    BufferedReader br = new BufferedReader(fr);
    ArrayList<String> lines = new ArrayList<String>();
    String line = null;
    while (true) {
      try {
        if ((line = br.readLine()) == null)
          break;
      } catch (IOException e) {
        e.printStackTrace();
        Log.info("Error reading file: " + file.getAbsolutePath());
        Log.info("Line before problem: " + line);
        br.close();
        throw e;
      }
      lines.add(line);
    }
    fr.close();
    br.close();
    String[] t = new String[0];
    return (lines.toArray(t));
  }

  /**
   * @param filename
   * @return
   * @throws IOException
   */
  public static String[] read_lines_trim(String filename) throws IOException {
    return read_lines_trim(createValidatedFile(filename));
  }

  /**
   * @param file
   * @return
   * @throws IOException
   */
  public static String[] read_lines_trim(File file) throws IOException {
    Reader fr = new FileReader(file);
    BufferedReader br = new BufferedReader(fr);
    ArrayList<String> lines = new ArrayList<String>();
    String line;
    while ((line = br.readLine()) != null) {
      lines.add(line.trim());
    }
    fr.close();
    br.close();
    String[] t = new String[0];
    return (lines.toArray(t));
  }

  /**
   * @param filename
   * @return
   * @throws IOException
   */
  public static Set<String> read_set(String filename) throws IOException {
    Reader fr = new FileReader(filename);
    BufferedReader br = new BufferedReader(fr);
    Set<String> set = new HashSet<String>();
    String line;
    while ((line = br.readLine()) != null) {
      set.add(line);
    }
    fr.close();
    br.close();
    return set;
  }

  /**
   * @param filename
   * @return
   * @throws IOException
   */
  @Deprecated
  public static Map<String, String> read_map(String filename) throws IOException {
    Reader fr = new FileReader(filename);
    BufferedReader br = new BufferedReader(fr);
    Map<String, String> map = new HashMap<String, String>();
    String line;
    while ((line = br.readLine()) != null) {
      line = line.trim();
      if (line.length() == 0)
        continue; // Skip blank lines
      String[] words = line.trim().split("\\t");
      if (words.length != 2) {
        br.close();
        throw new IOException("Can't parse line into (key, val): " + line);
      }
      map.put(words[0], words[1]);
    }
    fr.close();
    br.close();
    return map;
  }

  /**
   * @param filename
   * @param iter
   * @param formatter
   * @throws IOException
   */
  @Deprecated
  public static void print_lines(String filename, Iterator<String> iter) throws IOException {
    File file = createValidatedFile(filename);
    Writer fw = new FileWriter(file);
    BufferedWriter bw = new BufferedWriter(fw);
    PrintWriter output = new PrintWriter(fw);
    while (iter.hasNext()) {
      String line = iter.next().toString();
      output.print(line);
      output.print("\n");
    }
    output.close();
    bw.close();
    fw.close();
  }

  public static boolean deleteContents(File directory) {

    if (directory.isDirectory()) {
      String[] children = directory.list();
      for (int i = 0; i < children.length; i++) {
        boolean success = deleteDirectory(new File(directory, children[i]));
        if (!success) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }

  }

  public static boolean deleteDirectory(File directory) {
    if (directory.isDirectory()) {
      String[] children = directory.list();
      for (int i = 0; i < children.length; i++) {
        boolean success = deleteDirectory(new File(directory, children[i]));
        if (!success) {
          return false;
        }
      }
    }

    // The directory is now empty so delete it
    return directory.delete();
  }

  public static String fixPathString(String path) {
    path = path.replaceAll("\\\"|\\\'", "");
    char lastChar = path.charAt(path.length() - 1);
    if (lastChar == '\\') {
      return fixPathString(path.substring(0, path.length() - 1));
    }
    return path;
  }

  public static void deleteSubDirs(String dirName, Set<String> excludedNames) {
    File dir = createValidatedFile(dirName);
    String[] children = dir.list();
    if (children == null)
      return;
    for (String child : children) {
      if (!excludedNames.contains(child))
        deleteDirectory(new File(dir, child));
    }
  }

  static String read_string_as_bytes(String filename) throws IOException {
    FileInputStream stream = new FileInputStream(filename);
    byte b[] = new byte[stream.available()];
    stream.read(b);
    stream.close();
    return new String(b);
  }

  /**
   * @param parentDir a directory
   * @return a list of all files to be found below the indicated directory.
   */
  public static List<File> getAllChildFiles(File parentDir) {

    LinkedList<File> todo = new LinkedList<File>();

    List<File> ret = new LinkedList<File>();

    // Go through our todo list, adding files and recursively processing
    // directories, until we run out of work.
    todo.add(parentDir);

    while (todo.size() > 0) {
      File f = todo.poll();

      if (f.isFile()) {
        ret.add(f);
      } else if (f.isDirectory()) {
        for (File child : f.listFiles()) {
          todo.add(child);
        }

      } else {
        throw new RuntimeException("f should be either a" + " file or a directory!");
      }
    }

    return ret;

  }

  /**
   * Read the contents of a file into a Java string.
   * 
   * @param file the file to read
   * @param encoding encoding to use when reading the file
   */
  public static String fileToStr(File file, String encoding) throws Exception {

    StringBuilder sb = new StringBuilder();
    char[] buf = new char[1024];
    InputStreamReader in = new InputStreamReader(new FileInputStream(file), encoding);
    int nread;
    while (0 < (nread = in.read(buf))) {
      sb.append(buf, 0, nread);
    }
    in.close();
    String fileContents = sb.toString();
    return fileContents;
  }

  /**
   * Read the contents of a file into an array of bytes. Only works on files that are less than 2GB
   * in length. Assumes that the file does not change while the function is reading it.
   * 
   * @param file the file to read
   * @return an array containing the contents of the file. The returned array will be exactly the
   *         right size to hold the file's contents.
   * @throws IOException if anything goes wrong while reading the file
   */
  public static byte[] fileToBytes(File file) throws IOException {
    long fileSz = file.length();
    if (fileSz > Integer.MAX_VALUE) {
      throw new IOException(
          String.format("Attempted to read a file of more than 2GB into a byte array; "
              + "file is %s, size is %d", file, fileSz));
    }

    byte[] ret = new byte[(int) file.length()];

    FileInputStream in = new FileInputStream(file);
    int nread;
    int curOff = 0;
    while (0 < (nread = in.read(ret, curOff, ret.length - curOff))) {
      curOff += nread;
    }
    in.close();

    in.close();
    return ret;
  }

  /**
   * Write a string to a file, using the specified encoding. Traps any exceptions that may occur in
   * the process.
   * 
   * @param str the string to write
   * @param file where to write it
   * @param encoding name of the encoding to use
   * @return false on success; true if an exception occurred
   */
  public static boolean strToFile(String str, File file, String encoding) {
    try {
      OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file), encoding);
      out.append(str);
      out.close();
    } catch (IOException e) {
      return true;
    }
    return false;
  }

  /**
   * Read a resource out of the classpath as a string, assuming UTF-8 encoding. Turns any exceptions
   * it encounters into RuntimeExceptions.
   * 
   * @param rsrcPath path to the resource
   * @return string version of the resource
   */
  public static String readResource(String rsrcPath) {

    URL rsrc = ClassLoader.getSystemResource(rsrcPath);

    final String ENCODING_NAME = "UTF-8";

    if (null == rsrc) {
      throw new RuntimeException(String.format("Couldn't read system resource '%s'", rsrcPath));
    }

    try {

      InputStreamReader in = new InputStreamReader(rsrc.openStream(), ENCODING_NAME);

      StringBuilder sb = new StringBuilder();
      char[] buf = new char[4096];
      int nread;

      while (0 < (nread = in.read(buf))) {
        sb.append(buf, 0, nread);
      }

      return sb.toString();
    } catch (Exception e) {
      // Make the exception fatal.
      throw new RuntimeException(e);
    }
  }

  /**
   * Copies a file from source location to destination location
   * 
   * @param src Source file to copy
   * @param dest Destination file
   * @throws IOException
   */
  public static void copyFile(File src, File dest) throws IOException {
    // ensure that the destination directory exists
    File destParent = dest.getParentFile();
    if (!destParent.exists()) {
      destParent.mkdirs();
    }

    BufferedInputStream in = null;
    BufferedOutputStream out = null;
    try {
      in = new BufferedInputStream(new FileInputStream(src));
      out = new BufferedOutputStream(new FileOutputStream(dest));

      copyStreamToStream(in, out);
    } finally {

      if (in != null)
        in.close();
      if (out != null)
        out.close();
    }

  }

  /**
   * Copy the contents of one stream to another.
   * 
   * @param in stream containing data to copy; positioned at the beginning of said data
   * @param out stream where data should be written
   * @throws IOException if there is a problem executing the copy
   */
  public static void copyStreamToStream(InputStream in, OutputStream out) throws IOException {
    byte[] buf = new byte[1024];
    int nread = 0;
    while (0 < (nread = in.read(buf))) {
      out.write(buf, 0, nread);
    }
  }

  /**
   * Copies src directory to dest directory. This method expects that both src and dest are
   * directories and they exist.
   * 
   * @param src
   * @param dest
   * @throws IOException
   */
  public static void copyDir(File src, File dest) throws IOException {
    if (src.isDirectory() && dest.isDirectory()) {
      File[] contents = src.listFiles();
      for (File entry : contents) {
        File destEntry = new File(dest, entry.getName());
        if (entry.isDirectory()) {
          destEntry.mkdir();
          copyDir(entry, destEntry);
        } else if (entry.isFile()) {
          copyFile(entry, destEntry);
        }
      }
    } else {
      throw new IOException(String.format(
          "Both src and dest parameters must be directories and they must exist. src = %s, dest = %s",
          src.getAbsolutePath(), dest.getAbsolutePath()));
    }
  }

  /**
   * Create a temporary directory on the local filesystem. Use with caution; current implementation
   * has some thread-safety issues.
   * 
   * @param prefix prefix for the directory name
   * @return location of the temporary directory
   * @throws IOException if the creation fails
   */
  public static File makeTempDir(String prefix) throws IOException {
    File systemTmp = new File(System.getProperty("java.io.tmpdir"));

    File ret = null;

    int numTries = 0;

    Random r = new Random();

    while (null == ret && numTries < 10) {

      String dirName = String.format("%s%d", prefix, r.nextInt());
      ret = new File(systemTmp, dirName);

      if (ret.exists()) {
        // We picked the same name as someone else; try again
        ret = null;
      } else {

        // Note that there's a possible race condition here; two threads/processes could create
        // directories with the
        // same name at the same time.
        // TODO: Add locking, or verify that, if two processes call File.mkdir() at the same time,
        // exactly one of them
        // will see a return value of true.

        boolean succeeded = ret.mkdir();
        if (succeeded && ret.exists() && ret.isDirectory()) {
          return ret;
        } else {
          // Creation failed; try again
          ret = null;
        }

        numTries++;
      }
    }

    if (numTries >= 10) {
      throw new IOException(String
          .format("Failed to create a temporary directory under system temp dir %s", systemTmp));
    }

    return ret;
  }

  /**
   * Creates file handles in a safe manner. First, we validate that the filename does not contain
   * any disallowed characters such as escape sequences or leading dashes. Then, we create the file,
   * but return the handle to the canonical form of the file name.
   * 
   * @param pathname An unvalidated pathname string
   */
  public static File createValidatedFile(String pathname) {

    File ret = null;

    String validatedName = validateName(pathname);

    // Path name consists of only allowed characters; create it using the canonical form.
    try {
      ret = new File(validatedName).getCanonicalFile();
    } catch (IOException e) {
      throw new FatalInternalError(e, "Error in creating canonical file from file name %s.",
          pathname);
    }

    return ret;
  }

  /**
   * Creates file handles in a safe manner. First, we validate that the filename does not contain
   * any disallowed characters such as escape sequences or leading dashes. Then, we create the file,
   * but return the handle to the canonical form of the file name.
   * 
   * @param parent The parent pathname string
   * @param child The unvalidated child pathname string
   */
  public static File createValidatedFile(String parent, String child) {
    File ret = null;

    String validatedChild = validateName(child);
    String validatedParent = validateName(parent);

    // Path name now consists of only allowed characters; create it using the canonical form.
    try {
      ret = new File(validatedParent, validatedChild).getCanonicalFile();
    } catch (IOException e) {
      throw new FatalInternalError(e,
          "Error in creating canonical file from parent path %s and file name %s.", parent, child);
    }

    return ret;
  }

  /**
   * Creates file handles in a safe manner. First, we validate that the filename does not contain
   * any disallowed characters such as escape sequences or leading dashes. Then, we create the file,
   * but return the handle to the canonical form of the file name.
   * 
   * @param parent The parent abstract pathname
   * @param child The unvalidated child pathname string
   */
  public static File createValidatedFile(File parent, String child) {
    File ret = null;

    String validatedChild = validateName(child);

    // Path name now consists of only allowed characters; create it using the canonical form.
    try {
      ret = new File(parent, validatedChild).getCanonicalFile();
    } catch (IOException e) {
      throw new FatalInternalError(
          String.format("Error in creating canonical file from parent path %s and file name %s.",
              parent.getPath(), child));
    }

    return ret;
  }

  /**
   * Creates file handles in a safe manner. First, we validate that the filename does not contain
   * any disallowed characters such as escape sequences or leading dashes. Then, we create the file,
   * but return the handle to the canonical form of the file name.
   * 
   * @param uri An absolute, hierarchical URI with a scheme equal to "file", a non-empty path
   *        component, and undefined authority, query, and fragment components
   */
  public static File createValidatedFile(URI uri) {
    File ret = null;

    URI validatedChild = validateName(uri);

    // Path name now consists of only allowed characters; create it using the canonical form.
    try {
      ret = new File(validatedChild).getCanonicalFile();
    } catch (IOException e) {
      throw new FatalInternalError(
          String.format("Error in creating canonical file from URI %s.", uri.toString()));
    }

    return ret;
  }

  /*
   * Helper method to validate a URI using its String analog.
   */
  private static URI validateName(URI uri) {
    URI ret = null;

    String validatedUriString = validateName(uri.toString());

    // Path name now consists of only allowed characters; create it using the canonical form.
    try {
      ret = new URI(validatedUriString);
    } catch (URISyntaxException e) {
      throw new FatalInternalError(
          String.format("Error in creating URI from string %s.", validatedUriString));
    }
    return ret;
  }

  /*
   * Check that the file name only uses characters within an allowed subset. Currently, we only test
   * to ensure that the filename does not begin with a dash or contain a newline or carriage return.
   */
  private static String validateName(String pathname) {
    // make sure string does not contain invalid control characters
    String disallowedChars = "[\n\r]";
    Pattern pattern = Pattern.compile(disallowedChars);
    Matcher matcher = pattern.matcher(pathname);
    if (matcher.find()) {
      // Path name contains disallowed chars; throw error
      throw new IllegalArgumentException(String.format(
          "File name %s contains disallowed character %s.  Remove the disallowed character.",
          pathname, matcher.group()));
    }

    String fileSeparator = System.getProperty("file.separator");

    // extract the filename from the path name
    String filename = pathname.substring(pathname.lastIndexOf(fileSeparator) + 1);
    if (filename.startsWith("-")) {
      // File name should not start with a leading dash, because the first characters may be
      // interpreted as an option
      // switch
      throw new IllegalArgumentException(String.format(
          "File name %s starts with a dash.  Rename the file so that it does not begin with a dash.",
          pathname));
    }

    return pathname;
  }
}
