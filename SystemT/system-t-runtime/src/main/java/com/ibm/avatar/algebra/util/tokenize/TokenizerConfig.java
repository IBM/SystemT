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
package com.ibm.avatar.algebra.util.tokenize;

import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * This class encapsulates the configuration of a tokenizer.
 */

// Inner classes define various types of Text Analytics tokenization configurations
public abstract class TokenizerConfig {

  /**
   * Return the name of the tokenizer encapsulated by this configuration. Used to persist in the
   * compiled representation of AQL modules (i.e., in the module metadata persisted inside the
   * compiled .tam files)
   */
  public abstract String getName();

  /**
   * Returns configured {@link com.ibm.avatar.algebra.util.tokenize.Tokenizer} object based on this
   * TokenizerConfig.
   *
   * @return Configured {@link com.ibm.avatar.algebra.util.tokenize.Tokenizer} object.
   * @throws TextAnalyticsException for unchecked exceptions that occur during tokenizer creation
   */
  public abstract Tokenizer makeTokenizer() throws TextAnalyticsException;

  /**
   * Returns configured {@link com.ibm.avatar.algebra.util.tokenize.Tokenizer} object based on this
   * TokenizerConfig. If returnLemma is true this method will return an instance of tokenizer, which
   * will return lemma string in addition to other token info; if false the tokenizer instance
   * returned will not return lemma string.
   *
   * @param returnLemma If the tokenizer returns lemma or not
   * @return Configured {@link com.ibm.avatar.algebra.util.tokenize.Tokenizer} object.
   * @throws TextAnalyticsException for unchecked exceptions that occur during tokenizer creation
   */
  public abstract Tokenizer makeTokenizer(boolean returnLemma) throws TextAnalyticsException;

  /**
   * Returns configured {@link com.ibm.avatar.algebra.util.tokenize.Tokenizer} object based on this
   * TokenizerConfig. If the return flag is false, the tokenizer object will not return specified
   * features. 
   * 
   * @see com.ibm.avatar.algebra.util.tokenize.TokenizerConfig#makeTokenizer(boolean)
   * @param returnLemma If the tokenizer returns lemma or not
   * @param returnPartOfSpeech If the tokenizer returns part of speech or not
   * @return Configured {@link com.ibm.avatar.algebra.util.tokenize.Tokenizer} object.
   * @throws TextAnalyticsException for unchecked exceptions that occur during tokenizer creation
   */
  public abstract Tokenizer makeTokenizer(boolean returnLemma, boolean returnPartOfSpeech)
      throws TextAnalyticsException;

  /**
   * {@inheritDoc} This method forces subclasses to print various tokenizer configurations
   */
  @Override
  public abstract String toString();

  /**
   * Validates tokenizer configuration. Throws
   * {@link com.ibm.avatar.api.exceptions.TextAnalyticsException} if the configuration is invalid.
   *
   * @throws TextAnalyticsException for unchecked exceptions that occur during tokenizer validation
   */
  public abstract void validate() throws TextAnalyticsException;

  /**
   * Container for the tokenization configuration of the Standard tokenizer. Use
   * {@link #makeTokenizer()} to create an instance of the Standard tokenizer. <br>
   * <br>
   * The Standard tokenizer splits text on white space and punctuation. Therefore, this tokenizer is
   * not suitable to process text in languages without word boundary indicators, such as Chinese or
   * Japanese. Also, the Standard tokenizer does not support the AQL statement
   * <code>extract part_of_speech</code>. If your extractor uses the extract part_of_speech
   * statement, or is applied to text in languages without word boundary indicators such as Chinese
   * or Japanese, use the {@link Multilingual} tokenizer configuration instead.
   * 
   */
  public static class Standard extends TokenizerConfig {

    /** Name of the tokenizer encapsulated by this configuration */
    private final static String STANDARD_TOKENIZER_NAME = "STANDARD";

    /** Share FastTokenizer instance */
    private static Tokenizer cacheTokenizerInstance = new StandardTokenizer();

    /**
     * Return an instance of the Standard tokenizer.
     * 
     * @return an instance of Standard tokenizer.
     * @throws TextAnalyticsException for unchecked exceptions that occur during tokenizer creation
     */
    @Override
    public Tokenizer makeTokenizer() throws TextAnalyticsException {
      return cacheTokenizerInstance;
    }

    @Override
    public void validate() throws TextAnalyticsException {
      // Nothing to validate for a Standard tokenizer
    }

    @Override
    public String toString() {
      return "Standard tokenizer. Splits text on white space and punctuation. Does not support the AQL statement 'extract part_of_speech'.";
    }

    @Override
    public String getName() {
      return STANDARD_TOKENIZER_NAME;
    }

    /**
     * Not applicable for standard tokenizer
     *
     * @see com.ibm.avatar.algebra.util.tokenize.TokenizerConfig#makeTokenizer(boolean)
     */
    @Override
    public Tokenizer makeTokenizer(boolean returnLemma) throws TextAnalyticsException {
      throw new TextAnalyticsException("No standard tokenizer returns lemma.");
    }

    @Override
    public Tokenizer makeTokenizer(boolean returnLemma, boolean returnPartOfSpeech)
        throws TextAnalyticsException {
      throw new TextAnalyticsException("No standard tokenizer returns lemma and partOfSpeech.");
    }

  }

}
