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
package com.ibm.avatar.spss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import com.ibm.avatar.algebra.datamodel.Pair;

/**
 * This class models a document with custom tokens and annotations. Its main purpose is as example
 * container for storing information about SPSS documents and their annotations generated in TA
 * Extraction. NOTE: for SPSS TLA translation purposes, this class assumes that the tokeen and
 * annotation offsets are correct. For example, each annotation is a single token. It is the
 * responsibility of the caller to ensure that offsets are correct, this class does not provide any
 * validation in this respect.
 * 
 */
public class SPSSDocument {

  // The label of the document, should be the filename
  private String documentLabel;

  // Content of the document
  private String text;

  // Tokens for the document represented as a sorted list of (begin, end)
  // pairs.
  private ArrayList<Pair<Integer, Integer>> tokens;

  // A map (viewName -> list of annotations) representing a set of annotations
  // indexed by their view name (or type).
  private HashMap<String, ArrayList<SPSSAnnotation>> annotations;

  public SPSSDocument(String docLabel) {
    this.annotations = new HashMap<String, ArrayList<SPSSAnnotation>>();
    this.tokens = new ArrayList<Pair<Integer, Integer>>();
    this.documentLabel = docLabel;
  }

  public void setText(String text) {
    if (text == null)
      throw new RuntimeException("Document text is null");
    this.text = text;
  }

  public String getText() {
    return this.text;
  }

  public String getLabel() {
    return this.documentLabel;
  }

  public void setLabel(String documentLabel) {
    this.documentLabel = documentLabel;
  }

  /**
   * SETTERS AND GETTERS FOR TOKENS OFFSETS
   */

  /**
   * Insert tokens from a list of begin offset and a list of end offsets. Throw an exception if the
   * two arrays are of different sizes.
   * 
   * @param beginOffsets
   * @param endOffsets
   */
  public void setTokens(int[] beginOffsets, int[] endOffsets) {
    if (beginOffsets != null && endOffsets != null) {
      if (beginOffsets.length == endOffsets.length) {
        for (int i = 0; i < beginOffsets.length; i++) {
          tokens.add(new Pair<Integer, Integer>(beginOffsets[i], endOffsets[i]));
        }
      } else {
        throw new RuntimeException(String.format(
            "Mismatch in sizes of token offsets: "
                + "begin token lists has size %d and end token list has size %d.\n",
            beginOffsets.length, endOffsets.length));
      }
    } else
      throw new RuntimeException("Invalid input: Null tokens offsets.");
  }

  /**
   * Insert all token pairs at once
   * 
   * @param tokenPairs
   */
  public void setTokens(ArrayList<Pair<Integer, Integer>> tokenPairs) {
    if (tokenPairs == null) {
      throw new RuntimeException("Invalid input: Null token offests.");
    }
    this.tokens = tokenPairs;
  }

  /**
   * Incremental insert for tokens
   * 
   * @param tokenPair
   */
  public void addToken(Pair<Integer, Integer> tokenPair) {
    if (tokenPair == null) {
      throw new RuntimeException("Invalid input: Null token offsets.");
    }
    tokens.add(tokenPair);
  }

  public ArrayList<Pair<Integer, Integer>> getTokens() {
    return tokens;
  }

  public Boolean hasTokens() {
    if (this.tokens.isEmpty())
      return false;
    return true;
  }

  /**
   * SETTERS AND GETTERS FOR ANNOTATIONS
   */

  public Boolean hasAnnotations(String viewName) {
    if (this.annotations.get(viewName) != null)
      return true;
    return false;
  }

  public Boolean hasAnnotations() {
    if (!this.annotations.isEmpty())
      return true;
    return false;
  }

  /**
   * Retrieve all annotations for all views
   * 
   * @return
   */
  public HashMap<String, ArrayList<SPSSAnnotation>> getAnnotations() {
    return this.annotations;
  }

  /**
   * Retrieve all annotations (begin and end offsets) for the input view.
   * 
   * @param viewName
   * @return
   */
  public ArrayList<SPSSAnnotation> getAnnotations(String viewName) {
    if (this.annotations.get(viewName) != null)
      return this.annotations.get(viewName);
    else
      return null;
  }

  /**
   * Add a new set of annotations, for the input view.
   * 
   * @param viewname
   * @param annots
   */
  public void addViewAnnotations(String viewName, ArrayList<SPSSAnnotation> annots) {
    // the viewName cannot be null
    if (viewName == null)
      throw new RuntimeException("Invalid annotations: name of view is null.");
    if (annots == null)
      throw new RuntimeException("Invalid annotations: no annotations associated with the view.");
    this.annotations.put(viewName, annots);
  }

  /**
   * Retrieve the names of all views that have annotations.
   * 
   * @return
   */
  public Set<String> getViewNames() {
    return this.annotations.keySet();
  }

  /**
   * Pretty-print the set of tokens and annotations for this document. Used mostly for debugging.
   */
  @Override
  public String toString() {
    String toprint = "";
    toprint += "\n" + "Label: " + getLabel();
    if (tokens != null) {
      toprint += "\n" + "Tokens: ";
      for (int i = 0; i < tokens.size(); i++) {
        Pair<Integer, Integer> token = tokens.get(i);
        toprint += "[" + token.first + " ," + token.second + "]" + "\t";
      }
    }
    if (annotations != null) {
      toprint += "\n" + "Annotations:";
      Iterator<String> itr = getViewNames().iterator();
      while (itr.hasNext()) {
        String viewName = itr.next();
        ArrayList<SPSSAnnotation> annots = annotations.get(viewName);
        if (hasAnnotations(viewName)) {
          toprint += "\n\t" + viewName;
          for (int i = 0; i < annots.size(); i++) {
            SPSSAnnotation current = annots.get(i);
            toprint += "\t" + "[" + current.getBegin() + ", " + current.getEnd() + ", "
                + current.getInfo() + "]";
          }

        }
      }
    }

    return toprint;
  }
}
