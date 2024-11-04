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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import com.ibm.avatar.api.exceptions.FatalInternalError;

/**
 * We can't directly modify the generated class {@link ParseException}, so we create this subclass
 * that adds functionality for handling multiple files.
 */
public class ExtendedParseException extends ParseException {

  /**
   * Original exception that we're wrapping.
   */
  private ParseException origException;

  /**
   * Cached line number of the original exception, to prevent O(n^2) behavior if a deeply nested
   * chain of error handling code wraps a ParseException in a series of ExtendedParseExceptions
   */
  private int cachedLineNum;
  private int cachedColNum;

  /** AQL file name containing the error -- may be null */
  protected String fileName;

  /**
   * Dummy serialization ID.
   */
  private static final long serialVersionUID = 1L;

  public ExtendedParseException(ParseException e, File f) {
    // Pass through cause information so that the wrapped exception will have a useful stack trace.
    super(e.getCause(), "this string is not used because this class overrides getMessage()");

    try {
      if (null != f)
        this.fileName = f.getCanonicalPath();
    } catch (IOException e1) {
      // This exception will never occur
    }

    this.currentToken = e.currentToken;
    this.origException = e;

    // Cache line and column number to prevent O(N^2) behavior.
    this.cachedLineNum = origException.getLine();
    this.cachedColNum = origException.getColumn();

  }

  /** Add location info to an exception's message. */
  private static String extendedMsg(ParseException e, File f) {

    if (null == e) {
      throw new FatalInternalError("Null pointer passed to ExtendedParseException.extendedMsg()");
    }

    if (e instanceof ExtendedParseException) {
      // SPECIAL CASE: This exception has already been wrapped in an ExtendedParseException, but we
      // do not want a
      // cascaded error messages. So, work with the original exception.
      return extendedMsg(((ExtendedParseException) e).origException, f);
      // END SPECIAL CASE
    }

    // If we get here, we need to format the message ourselves with
    // file location information.
    String msg = e.getMessage();
    Token token = e.currentToken;
    if (null == token) {
      // No token.
      return msg;
    } else if (null != e.expectedTokenSequences) {
      // Parse exception from generated parsing code
      if (null == f) {
        // Parsing a raw string
        return msg;
      } else {
        return String.format("In %s: %s", f.getPath(), msg);
      }
    } else {
      ErrorLocation errLoc = new ErrorLocation(f, e.currentToken);
      errLoc.lineNum = errLoc.lineNum + e.lineAdjustment;
      return String.format("In %s: %s", errLoc.getLocStr(), msg);
    }
  }

  @Override
  /**
   * Returns the error message with file and location information If filename is null, we will
   * return a error message without file information.
   */
  public String getMessage() {
    // origException.printStackTrace ();
    if (fileName != null) {
      return extendedMsg(origException, new File(fileName));
    } else {
      return extendedMsg(origException, null);
    }
  }

  @Override
  public StackTraceElement[] getStackTrace() {
    // We want the *original* stack trace, not the stack trace from the
    // point where the exception was converted.
    return origException.getStackTrace();
  }

  @Override
  public void printStackTrace() {
    reallyPrintStackTrace(System.err);
  }

  @Override
  public void printStackTrace(PrintStream err) {
    reallyPrintStackTrace(err);
  }

  @Override
  public void printStackTrace(PrintWriter err) {
    reallyPrintStackTrace(err);
  }

  @Override
  public String getFileName() {
    return this.fileName;
  }

  /**
   * Updates the file name of the exception. Used by backward compatibility API to translate
   * exception objects referring to files in genericModule temp directory to their original file
   * names.
   * 
   * @param fileName new file name
   */
  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  /**
   * Return the original parse exception that is wrapped by the current exception object.
   * 
   * @return ParseException wrapped by the current exception.
   */
  public ParseException getWrappedException() {
    return origException;
  }

  @Override
  public String getErrorDescription() {
    return origException.getErrorDescription();
  }

  @Override
  public int getLine() {
    // if (origException.getLine () != 0) {
    // return origException.getLine ();
    // }
    if (cachedLineNum != 0) {
      return cachedLineNum + lineAdjustment;
    } else {
      return (null != this.currentToken ? (this.currentToken.beginLine + lineAdjustment) : 0);
    }
  }

  @Override
  public int getColumn() {
    // if (origException.getColumn () != 0) {
    // return origException.getColumn ();
    // }
    if (cachedColNum != 0) {
      return cachedColNum;
    } else {
      return (null != this.currentToken ? this.currentToken.beginColumn : 0);
    }
  }

  /**
   * This method replicates the stack trace printing that is built into Java's exception framework,
   * adjusting the stack trace so that it will make sense in the context of AQL.
   */
  private void reallyPrintStackTrace(Appendable err) {

    // Start by building up the chain of exceptions that led to the one being printed. That is,
    // follow the "caused by"
    // pointers.
    ArrayList<Throwable> rootCauseChain = new ArrayList<Throwable>();
    Throwable current = this.getCause();
    while (current != null) {
      rootCauseChain.add(current);
      if (current.getCause() == current) {
        throw new FatalInternalError("Cycle in root cause chain");
      }
      current = current.getCause();
    }

    try {
      StackTraceElement[] trace = origException.getStackTrace();

      // We want a stack trace with our custom message but the original
      // stack, so we need to generate it by hand.
      err.append(String.format("%s: %s\n", ParseException.class.getCanonicalName(), getMessage()));

      for (int i = 0; i < trace.length; i++) {
        StackTraceElement elem = trace[i];
        if ("makeException".equals(elem.getMethodName())) {
          // Strip out the makeException() call that parser methods
          // use to create an exception with a line number.
        } else {
          err.append(String.format("\tat %s.%s(%s:%d)\n", elem.getClassName(), elem.getMethodName(),
              elem.getFileName(), elem.getLineNumber()));
        }
      }

      // Next add root cause information. The built-in printStackTrace() methods don't work with
      // Appendable, so we need
      // to reimplement some functionality here.
      for (Throwable cause : rootCauseChain) {
        err.append(String.format("Caused by: %s\n", cause.toString()));

        for (StackTraceElement elem : cause.getStackTrace()) {
          err.append(String.format("\tat %s.%s(%s:%d)\n", elem.getClassName(), elem.getMethodName(),
              elem.getFileName(), elem.getLineNumber()));
        }
      }

    } catch (IOException e) {
      throw new FatalInternalError(e, "Error printing stack trace to %s", err);
    }
  }

  /**
   * Number of lines that we should adjust the line number with. This is helpful when working with
   * backward compatibile AQLs, since the regenerated genericModule AQLs have 2 additional lines
   * over the original non-modular AQL and hence line adjustment is necessary during error
   * reporting.
   * 
   * @param numLines Number of lines to adjust. Use negative numbers to subtract lines.
   */
  @Override
  public void adjustLineNumber(int numLines) {
    this.lineAdjustment = numLines;
    this.origException.adjustLineNumber(numLines);
  }
}
