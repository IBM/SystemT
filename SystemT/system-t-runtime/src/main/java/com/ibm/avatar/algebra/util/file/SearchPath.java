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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.exceptions.AmbiguousPathRefException;
import com.ibm.avatar.logging.Log;
import com.ibm.avatar.logging.MsgType;

/** A search path (a la Java's classpath) for finding files on the filesystem. */
public class SearchPath {

  /**
   * Delimiter character for search paths. To keep config files consistent, we use a single
   * character across operating systems.
   */
  public static final char PATH_SEP_CHAR = ';';

  /**
   * String version of {@link #PATH_SEP_CHAR}
   */
  public static final String PATH_SEP_CHAR_STR = new String(new char[] {PATH_SEP_CHAR});

  /**
   * Regular expression for a directory separator character; OS-specific...
   */
  public static final String dirSepRegex = String.format("(/|\\%s)", File.separator);

  /**
   * List of wildcards supported currently for regex evaluation in 'include' statement . If a new
   * wildcard is added to this list then that wildcard must be removed from the wildcardList
   * declared above.
   */
  public static final String[] SUPPORTED_WILDCARDS = {"*"};

  /**
   * List of shell wildcard characters that we might start to support in the future. We currently
   * pass these characters through to the file search routines, though a future version may generate
   * an error if one of these chracters is found.
   */
  public static final String[] UNSUPPORTED_WILDCARDS = {"?"};

  /**
   * List of characters that have special meaning in Java regular expressions. These characters need
   * to be escaped when generating a regex from a string. NOTE: This list does not include the
   * backslash character \, and it does not include anything in {@link #SUPPORTED_WILDCARDS} or
   * {@link #UNSUPPORTED_WILDCARDS}. If {@link #SUPPORTED_WILDCARDS} or
   * {@link #UNSUPPORTED_WILDCARDS} changes, this list needs to be kept in sync.
   */
  public static final String[] REGEX_SPECIAL_CHARS =
      {".", "+", "^", "$", "{", "}", "[", "]", "|", "<", ">", "-", "(", ")"};

  protected ArrayList<File> entries = new ArrayList<File>();

  /**
   * Remembers the original pathStr passed to the constructor. Once set in constructor, do not
   * modify it anywhere else. This attribute is used in error reporting.
   */
  protected String origPathStr;

  /**
   * @param pathStr a colon-delimited list of search locations
   */
  public SearchPath(String pathStr) {

    // Keep a safe copy of pathStr
    this.origPathStr = pathStr;

    String[] paths = pathStr.split(Character.toString(PATH_SEP_CHAR));
    for (String path : paths) {
      // Java will happily reinterpret the location of our files as the
      // working directory changes, unless we force them to have absolute
      // paths.
      File tmp = FileUtils.createValidatedFile(path);
      try {
        entries.add(tmp.getCanonicalFile());
      } catch (IOException e) {
        Log.log(MsgType.AOGCompileWarning, "Cannot find search path element '%s'", path);
      }

      // This will remove duplicated from the data path entries.
      // Customers can by mistake add duplicates. In that case
      // resolveMuti api will throw always throw Ambiguity error
      Set<File> uniqueEntries = new HashSet<File>(entries);
      entries.clear();
      entries.addAll(uniqueEntries);
    }
  }

  /**
   * Add an additional entry onto the end of the search path.
   * 
   * @param entry new entry to add
   */
  public void addEntry(File entry) {
    // Java will happily reinterpret the location of our files as the
    // working directory changes, unless we force them to have absolute
    // paths.

    // Condition to avoid duplicates in search path entries
    if (!entries.contains(entry))
      entries.add(entry.getAbsoluteFile());
  }

  /**
   * @param name name of a file to find
   * @return the first such matching file found by prepending the entries in the path to the
   *         supplied name
   */
  public File resolve(String name) {

    for (File entry : entries) {
      File potentialMatch = FileUtils.createValidatedFile(entry, name);
      if (potentialMatch.exists() && potentialMatch.isFile()) {
        return potentialMatch;
      }
    }

    // No match found.
    return null;
  }

  /**
   * @param name name of a directory to find
   * @return the first such matching directory found by prepending the entries in the path to the
   *         supplied name
   */
  public File resolveDir(String name) {
    for (File entry : entries) {
      File potentialMatch = new File(entry, name);
      if (potentialMatch.exists() && potentialMatch.isDirectory()) {
        return potentialMatch;
      }
    }

    // No match found.
    return null;

  }

  /**
   * @param pattern expression that matches one or more files, relative to the search path; may
   *        contain wildcard characters
   * @param oneRoot If true, causes this this method to return an error when the pattern matches
   *        files relative to more than one entry in the path
   * @return a list of all files that match the pattern, in alphabetical order
   */
  public ArrayList<File> resolveMulti(String pattern, boolean oneRoot)
      throws AmbiguousPathRefException {

    ArrayList<File> ret = new ArrayList<File>();

    final boolean debug = false;

    // Start by splitting the pattern we're supposed to match into a
    // directory name part and a file name part.
    // Currently, only the file name can contain wildcard characters.
    String[] strs = pattern.split(dirSepRegex);

    // We can't use Arrays.copyOf -- it's a Java 6 method
    // String[] dirStrs = Arrays.copyOf(strs, strs.length - 1);
    String[] dirStrs = new String[strs.length - 1];
    System.arraycopy(strs, 0, dirStrs, 0, dirStrs.length);

    String filePattern = strs[strs.length - 1];

    // We don't currently support wildcard expansion on directories; make
    // sure that there aren't any wildcards there.
    for (String str : dirStrs) {
      if (containsShellWildcard(str)) {
        throw new AmbiguousPathRefException("Wildcard expansion in directory names not supported");
      }
    }

    String dirStr;
    if (strs.length > 1) {
      dirStr = StringUtils.join(dirStrs, File.separator);
    } else {
      dirStr = null;
    }

    if (debug) {
      Log.debug("resolveMulti: %s --> %s and %s", pattern, dirStr, filePattern);
    }

    // Generate a regular expression that implements the semantics of the
    // file name pattern.
    String fileExpr = filePatternToRegex(filePattern);

    // Go through each directory in turn, looking for matches.
    for (File entry : entries) {
      // Build up the matches for the current entry; at the end of this
      // iteration we'll merge into the main list of matches.
      ArrayList<File> matches = new ArrayList<File>();

      // String together the current search path entry with the directory
      // prefix from the pattern, if there is one.
      File curDir;
      if (null == dirStr) {
        curDir = entry;
      } else {
        curDir = new File(entry, dirStr);
      }

      if (curDir.isDirectory()) {
        // Appending the directory part of the original pattern to the
        // current path entry creates the name of a directory; check the
        // files in that directory to see if they match the file portion
        // of the pattern.

        if (debug) {
          Log.debug("resolveMulti: Checking files in %s", curDir);
        }

        File[] files = curDir.listFiles();

        // Process the files in order so that we always return them in
        // the same order, regardless of what order the OS returns them
        Arrays.sort(files);

        for (File file : files) {
          String name = file.getName();

          if (name.matches(fileExpr)) {
            if (debug) {
              Log.debug("resolveMulti: %s matches", file);
            }
            matches.add(file);
          }
        }

      } else {
        // There is no directory under the current entry that matches
        // the directory name.
        if (debug) {
          Log.debug(
              "resolveMulti: Path entry dir %s doesn't contain" + " a directory that matches '%s'",
              entry, dirStr);
        }
      }

      if (oneRoot && 0 != matches.size() && 0 != ret.size()) {
        throw new AmbiguousPathRefException(origPathStr, pattern, ret.get(0), matches.get(0));
      }

      ret.addAll(matches);
    }

    return ret;
  }

  /** @return a colon-delimited path string. */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < entries.size() - 1; i++) {
      sb.append(entries.get(i).getPath());
      sb.append(PATH_SEP_CHAR);
    }
    sb.append(entries.get(entries.size() - 1).getPath());
    return sb.toString();
  }

  /**
   * Returns File pointing to the given file name.
   * 
   * @param name
   * @return File
   */
  public static File resolvePath(String name) {

    File file = FileUtils.createValidatedFile(name);
    File potentialMatch = null;
    String path = file.getParent();
    // If path is null then assign path to current working directory
    if (null == path)
      potentialMatch =
          FileUtils.createValidatedFile(FileUtils.createValidatedFile(name).getAbsolutePath());
    else
      potentialMatch = file;

    return potentialMatch;
  }

  /*
   * INTERNAL UTILITY METHODS
   */

  /**
   * Validates given file path to check if it contains any of the wildcards.
   * 
   * @param path
   * @return false when any wildcards is encountered in the path else returns true;
   */
  public boolean validatePathForWildcards(String path) {

    boolean flag = false;
    for (String ivwc : SUPPORTED_WILDCARDS) {
      if (path.contains(ivwc)) {
        flag = false;
        break;
      } else
        flag = true;
    }
    return flag;
  }

  /**
   * @param str a string to be used in file or directory matching
   * @return true if the string contains a shell wildcard character like '*'; currently we only
   *         return true for the wildcards that this class knows how to expand.
   */
  private static boolean containsShellWildcard(String str) {
    for (String wildcard : SUPPORTED_WILDCARDS) {
      if (str.contains(wildcard)) {
        return true;
      }
    }

    // If desired, add additional code here to check against
    // UNSUPPORTED_WILDCARDS

    return false;
  }

  /**
   * @param filenamePattern shell-style filename pattern, such as '*.aql'
   * @return a regular expression string that matches filenames that match the input pattern
   */
  private static String filePatternToRegex(String filenamePattern) {

    // Start out with a copy of the pattern
    String exprStr = filenamePattern;

    // Escape any backslash characters first
    exprStr = exprStr.replace("\\", "\\\\");

    // Then escape other regular expression special characters.
    // Note that the wildcard characters like '*' that we deal with below
    // are NOT part of the REGEX_SPECIAL_CHARS list
    for (String wc : REGEX_SPECIAL_CHARS) {
      if (exprStr.contains(wc)) {
        exprStr = exprStr.replace(wc, String.format("\\%s", wc));
      }
    }

    // Implement the '*' wildcard by replacing it with .*
    exprStr = exprStr.replace("*", ".*");

    // --------------------------------------------------------------------
    // Implementation of additional wildcard characters like '?' goes here.

    return exprStr;
  }
}
