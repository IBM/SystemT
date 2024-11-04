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
package com.ibm.avatar.algebra.oldscan;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Pattern;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.logging.Log;

/**
 * Document scan operator that reads documents out of a directory. Files are assumed to be UTF-8,
 * and any file in the subtree below the target directory will be returned. All subdirectories are
 * processed in alphabetical order.
 * 
 */
public class DirDocScan extends DocScanInternal {

  public static final String FILE_ENCODING = "UTF-8";

  /** Regular expression for the names of directories that we ignore. */
  private static final Pattern DIR_SKIP_NAME_REGEX = Pattern.compile("\\.svn");

  private final File dir;

  /**
   * Stack used during our traversal of the directory tree. At any point, the top element of this
   * stack represents the current directory being traversed. Each element iterates over the
   * files/directories in the directory, in alphabetical order
   */
  protected ArrayList<LookaheadItr<File>> filestack = new ArrayList<LookaheadItr<File>>();

  /**
   * Accessor for putting the document label in place.
   */
  protected FieldSetter<String> docLabelAcc;

  /**
   * Main constructor.
   * 
   * @param dir top-level directory; any file underneath this directory will be scanned.
   */
  public DirDocScan(File dir) throws IOException {
    this(dir, DocScanInternal.createLabeledSchema());
  }

  /**
   * Create a {@link DirDocScan} operator for the specified directory and expected output schema.
   * 
   * @param dir the directory to be scanned
   * @param docSchema expected output schema of the operator
   * @throws IOException if the specified {@link File} is not a directory
   */
  public DirDocScan(File dir, AbstractTupleSchema docSchema) throws IOException {
    super();

    if (false == dir.isDirectory()) {
      throw new IOException(dir + " is not a directory");
    }

    this.dir = dir;
    this.docSchema = docSchema;
  }

  /**
   * We need to override this method so that we can add the "label" column.
   */
  @Override
  protected AbstractTupleSchema createOutputSchema() {

    docTextAcc = docSchema.textSetter(getTextColName());
    docTextGetters.add(docSchema.textAcc(getTextColName()));

    // Create accessor for label, only if it is part of specified document schema
    if (docSchema.containsField(Constants.LABEL_COL_NAME)) {
      docLabelAcc = docSchema.textSetter(Constants.LABEL_COL_NAME);
      docTextGetters.add(docSchema.textAcc(Constants.LABEL_COL_NAME));
    }

    return docSchema;
  }

  @Override
  protected Tuple getNextDoc(MemoizationTable mt) throws Exception {

    // Get a handle on the current document file.
    File docFile = filestack.get(filestack.size() - 1).peek();

    InputStreamReader in = new InputStreamReader(new FileInputStream(docFile), FILE_ENCODING);
    String docText = getDocText(in);

    in.close();

    // Create a tuple out of the text we've read into memory.
    Tuple ret = createOutputTup();
    docTextAcc.setVal(ret, docText);

    // label may or may not be the part of the specified document schema
    if (docSchema.containsField(Constants.LABEL_COL_NAME))
      docLabelAcc.setVal(ret, relativePath(dir, docFile));

    // Prepare for the next call to this method. Pass the flag
    // 'checkCurrentElem' as false, since current file pointed by the
    // iterator has already been sent to the user.
    advanceToNextFile(mt, false);

    return ret;
  }

  /**
   * @param root root of a directory tree
   * @param child a node within the directory tree
   * @return path of the child, relative to the root
   */
  private String relativePath(File root, File child) {
    String rawRelPathStr = child.getAbsolutePath().substring(root.getAbsolutePath().length());

    // Make sure we use forward slash as a path separator, so that regression tests pass on all OS's
    if ('/' != File.separatorChar) {
      String ret = rawRelPathStr.replace(File.separatorChar, '/');
      // System.err.printf("%s --> %s\n", rawRelPathStr, ret);
      return ret;
    } else {
      return rawRelPathStr;
    }
  }

  /**
   * Move our stack of iterators forward util the top one is pointing at a document or we run out.
   */
  private void advanceToNextFile(MemoizationTable mt, boolean checkCurrentElem) {

    LookaheadItr<File> itr = filestack.get(filestack.size() - 1);

    while (true) {
      while (false == itr.hasNext()) {
        // Ran out of files in this directory; pop an element off the
        // stack.
        filestack.remove(filestack.size() - 1);

        if (0 == filestack.size()) {
          // Ran completely out of directories.
          mt.setEndOfInput();
          return;
        }

        itr = filestack.get(filestack.size() - 1);
      }

      // do not move ahead if the current element pointed by the iterator
      // has not been used. This avoids missing the first directory listed
      // for given parent.
      if (!checkCurrentElem) {
        itr.next();
      }
      if (itr.hasNext()) {
        if (itr.peek().isDirectory()) {
          // Found the next *directory*; scan its contents, in sorted
          // order.
          LookaheadItr<File> parentItr = itr;
          itr = pushDir(itr);

          // if the method returns the same iterator, then the
          // directory that we tried to load must be empty. Mark the
          // flag so that the same directory is not checked again and
          // we avoid going into endless loop
          if (itr.equals(parentItr)) {
            checkCurrentElem = false;
          } else {
            checkCurrentElem = true;
          }
        }

        // check if iterator has next element to avoid null pointer
        // later on.
        if (itr.hasNext()) {
          if (itr.peek().isFile()) {
            // Found the next file; we're done.
            // Log.debug("At end of advance(), itr points to %s",
            // itr
            // .peek());

            return;
          } else {
            // Log.debug("itr is NOT a file.");
          }
        }
      }
    }
  }

  private LookaheadItr<File> pushDir(LookaheadItr<File> itr) {

    boolean debug = false;

    if (debug) {
      Log.debug("Scanning directory %s", itr.peek());
    }

    ArrayList<File> contents = new ArrayList<File>();
    for (File file : itr.peek().listFiles()) {
      contents.add(file);
    }

    // if there are no contents in the directory to be loaded, then return
    // the given iterator back
    if (contents.isEmpty()) {
      return itr;
    }

    // SPECIAL CASE: Skip certain special directories, such as SVN metadata.
    String dirName = itr.peek().getName();
    if (DIR_SKIP_NAME_REGEX.matcher(dirName).matches()) {
      if (debug) {
        Log.debug("DirDocScan skipping directory %s.", itr.peek());
      }
      return itr;
    }
    // END SPECIAL CASE

    if (debug) {
      Log.debug("Before sorting: %s", contents);
    }

    Collections.sort(contents);

    if (debug) {
      Log.debug("After sorting: %s", contents);
    }

    itr = new LookaheadItrImpl<File>(contents.iterator());
    filestack.add(itr);

    if (debug) {
      Log.debug("After stack insertion, iterator points to: %s", itr.peek());
    }

    return itr;
  }

  @Override
  protected void startScan(MemoizationTable mt) throws Exception {
    // Position ourselves before the first file.
    ArrayList<File> list = new ArrayList<File>();
    list.add(dir);
    LookaheadItr<File> itr = new LookaheadItrImpl<File>(list.iterator());
    filestack.add(itr);
    itr = pushDir(itr);

    if (false == itr.peek().isFile()) {
      advanceToNextFile(mt, true);
    }

    // itr = filestack.get(filestack.size() - 1);
    // Log.debug("After startScan() iterator points to %s", itr.peek());
  }

  @Override
  protected void reallyCheckEndOfInput(MemoizationTable mt) throws Exception {
    if (0 == filestack.size()) {
      // Ran out of files.
      mt.setEndOfInput();
    }
  }

}
