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

package com.ibm.avatar.api.exceptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.ibm.avatar.api.CompilationSummary;
import com.ibm.avatar.api.CompilationSummaryImpl;
import com.ibm.avatar.aql.ParseException;

/**
 * Wrapper for a list of compiler errors encountered during AQL compilation.
 */
public class CompilerException extends TextAnalyticsException {

  private static final long serialVersionUID = 1L;

  private final List<Exception> compileErrors = new ArrayList<Exception>();

  private CompilationSummary compileSummary;

  /**
   * Default constructor for creating an empty list of errors
   *
   * @param compileSummary
   */
  public CompilerException(CompilationSummary compileSummary) {
    if (compileSummary == null) {
      Thread.dumpStack();
      throw new RuntimeException(
          "Attempted to create a compiler exception, but the compilation summary is null.");
    }
    this.compileSummary = compileSummary;
  }

  /**
   * Constructor that initializes the CompilerException with a list of known errors. The resulting
   * CompilerException can have additional errors added to it as needed.
   *
   * @param errors initial set of errors; this argument is NOT modified but is copied instead
   */
  public CompilerException(List<? extends Exception> errors,
      CompilationSummaryImpl compileSummary) {
    this(compileSummary);
    // Don't modify the argument
    compileErrors.addAll(errors);
  }

  /**
   * Creates a wrapper for a list of AQL compiler errors, and instantiates it with a specific error
   *
   * @param fmt format string, as in {@link java.lang.String#format(String, Object...)}
   * @param args arguments for format string
   */
  public CompilerException(String fmt, Object[] args, CompilationSummaryImpl compileSummary) {
    this(compileSummary);
    Exception e = new Exception(String.format(fmt, args));
    addError(e);
  }

  /**
   * Method to add the compile error/exception to the global list of compiler errors.
   *
   * @param e compile error/exception.
   */
  public void addError(Exception e) {
    Exception exception = e;

    // wrap messages with Null Message
    if (e.getMessage() == null) {
      String msg = TextAnalyticsException.findNonNullMsg(exception);
      exception = new TextAnalyticsException(e, msg);
    }
    compileErrors.add(exception);
  }

  /**
   * Method to add a list of compile errors and exceptions to the global list of compiler errors.
   *
   * @param linkedList list of compile error/exception.
   */
  public void addErrors(List<? extends Exception> linkedList) {
    compileErrors.addAll(linkedList);
  }

  /**
   * Returns list of all the errors and exceptions encountered during compilation.
   *
   * @return list of all the errors and exceptions encountered during compilation.
   */
  public List<Exception> getAllCompileErrors() {
    return compileErrors;
  }

  /**
   * Returns a sorted list of compiler exceptions. The list is sorted first by AQL file name, then
   * by line number and finally by column number.
   *
   * @return sorted list of compilation errors
   */
  public List<Exception> getSortedCompileErrors() {

    ArrayList<Exception> ret = new ArrayList<Exception>();

    // Pass 1: Segregate ParseExceptions, CircularDependencyException and other exceptions
    ArrayList<ParseException> parseExceptions = new ArrayList<ParseException>();
    ArrayList<CircularDependencyException> cdExceptions =
        new ArrayList<CircularDependencyException>();
    ArrayList<Exception> nonParseExceptions = new ArrayList<Exception>();

    for (Exception e : compileErrors) {
      if (e instanceof ParseException) {
        parseExceptions.add((ParseException) e);
      } else if (e instanceof CircularDependencyException) {
        cdExceptions.add((CircularDependencyException) e);
      } else {
        nonParseExceptions.add(e);
      }
    }

    // Pass2: Sort ParseExceptions by File name and line number
    Collections.sort(parseExceptions, new ParseExceptionComparator());

    // Pass 3: Add all the CircularDependencyException, followed by sorted ParseExceptions to the
    // return list, followed
    // by non-parse exceptions
    ret.addAll(cdExceptions);
    ret.addAll(parseExceptions);
    if (nonParseExceptions.size() > 0) {
      ret.addAll(nonParseExceptions);
    }

    return ret;
  }

  /**
   * Get the compile summary object.
   *
   * @return compile summary object
   */
  public CompilationSummary getCompileSummary() {
    return compileSummary;
  }

  /**
   * Set the compile summary object
   *
   * @param compileSummary
   */
  public void setCompileSummary(CompilationSummaryImpl compileSummary) {
    this.compileSummary = compileSummary;
  }

  /**
   * Class that compares ParseException objects
   */
  private class ParseExceptionComparator implements Comparator<ParseException> {

    @Override
    public int compare(ParseException pe1, ParseException pe2) {
      // both are same, if they are null
      if (pe1 == null && pe2 == null) {
        return 0;
      }

      // pe2 is greater if it is not null
      if (pe1 == null && pe2 != null) {
        return 1;
      }

      // pe1 is greater if it is not null
      if (pe2 == null && pe1 != null) {
        return -1;
      }

      // both exceptions are non-null -- ignore findbugs warning
      String pe1FileName = pe1.getFileName();
      String pe2FileName = pe2.getFileName();

      // both file names are null, so both are equal
      if (pe1FileName == null && pe2FileName == null) {
        return 0;
      }

      // pe1 sorts lower if pe1's file name is null, but pe2's file name is not null
      if (pe1FileName == null && pe2FileName != null) {
        return 1;
      }

      // pe1 sorts higher if its file name is not null, but pe2's file name is null
      if (pe1FileName != null && pe2FileName == null) {
        return -1;
      }

      // at this point, neither filename is null
      // normalize the filename directory separator to prevent compare errors across platforms
      // This fixes defect - Order of ParseException in Windows and RHEL is different ...
      //
      String fileSeparator = System.getProperty("file.separator");
      if (fileSeparator != null) {
        pe1FileName = pe1FileName.replace(fileSeparator, "/");
        pe2FileName = pe2FileName.replace(fileSeparator, "/");
      }

      // compare line & column numbers if file names are equal -- ignore findbugs warning
      int fileComparison = pe1FileName.compareTo(pe2FileName);
      if (fileComparison == 0) {
        int lineComparison = pe1.getLine() - pe2.getLine();
        if (lineComparison == 0) {
          return pe1.getColumn() - pe2.getColumn();
        } else {
          return lineComparison;
        }
      } else {
        return fileComparison;
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * This method prints all compile errors in a readable format.
   */
  // Override default behavior so that original compilation errors are surfaced to the user.
  @Override
  public String getMessage() {
    if (compileErrors.size() > 0) {

      // Obtain a list of messages from all the compiler errors.
      StringBuffer sb = new StringBuffer();
      // for (Exception e : compileErrors) {
      for (Exception e : getSortedCompileErrors()) {
        sb.append("\n");
        sb.append(e.getMessage());
      }
      return String.format("Compiling AQL encountered %d errors: %s", compileErrors.size(),
          sb.toString());
    }

    return "No compilation errors";
  }

}
