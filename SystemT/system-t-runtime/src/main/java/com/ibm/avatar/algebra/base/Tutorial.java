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
package com.ibm.avatar.algebra.base;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;

/**
 * Sample operator intended to function as a tutorial for making new operators. See comments
 * throughout the file for information about the various parts that make up this operator. The
 * Tutorial operator itself annotates individual lowercase words within the input document or
 * annotation.
 * 
 */
public class Tutorial
    // Every ADI operator is implemented as a Java class.

    extends SingleArgAnnotator {
  // Most SystemT operators extend SingleArgAnnotator, which provides facilities
  // to build an operator that reads in a set of input tuples (all
  // corresponding to annotations within a single document) and, for each
  // input tuple, creates zero or more output tuples.
  //
  // The output tuples of a SingleArgAnnotator have the same schema as the
  // input tuples, with the addition of a single Span column.
  //
  // To implement an operator with a different output schema (say, adding more
  // than one Span column), create an operator that derives directly
  // from Operator, the superclass of SingleArgAnnotator.

  /**
   * The constructor for the Tutorial operator instantiates the operator and connects it to its
   * source subtree.
   * 
   * @param col the name of the tuple column from which this operator will read its input.
   * @param outCol the name of the output column that we add to our input tuples to hold the output
   *        spans
   * @param child the root of the operator subtree that produces the inputs for this Tutorial
   *        operator.
   */
  public Tutorial(String col, String outCol, Operator child) {
    super(col, outCol, child);
    // The constructor for the superclass, SingleArgAnnotator, takes a
    // single argument, the child subtree.
    // The superclass will put <child> into the variable <this.inputs[0]>.

  }

  /**
   * The reallyEvaluate() function is the core of any operator that extends SingleArgAnnotator.
   * Implementations of reallyEvaluate() should read all the tuples for the current document and
   * produce all result tuples that are relevant to the current document.
   */
  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList childResults) throws Exception {

    // We start out by fetching the input tuples. All these tuples are
    // guaranteed to correspond to the same input document.
    TLIter tupleIterator = childResults.iterator();

    // Now we can iterate through our input tuples, producing output tuples
    // as we find places to annotate.
    while (tupleIterator.hasNext()) {
      Tuple childTuple = tupleIterator.next();

      // Use the accessor to get the target span out of the input tuple.
      Span targetSpan = Span.convert(inputAcc.getVal(childTuple));

      // Every Span has a "text" attribute that indicates the
      // portion of the original document annotated. If childAnnotation is
      // actually the original document, then its text attribute is the
      // entire text of the document.
      String childAnnotationText = targetSpan.getText();

      // The Tutorial class tries to find words. Here's a simple regular
      // expression for lowercase words.
      Pattern wordRegex = Pattern.compile("\\b[a-z]+\\b");
      Matcher matcher = wordRegex.matcher(childAnnotationText);

      while (matcher.find()) {

        // If we get here, our regular expression matcher has found a
        // word in the input text. Get the boundaries of that word.
        int wordBeginOffset = matcher.start();
        int wordEndOffset = matcher.end();

        // Now that we have a location in the input text, we can create
        // an annotation. The superclass, SingleArgAnnotator, has a
        // convenience method, addResultAnnot(), that simplifies the
        // process. Note that targetSpan is being used as the
        // "source" of our new output annotation.
        addResultAnnot(childTuple, wordBeginOffset, wordEndOffset, targetSpan, mt);

      }

    }

    // When reallyEvaluate() is done producing output for the current
    // document's set of tuples, it simply returns.
  }
}
