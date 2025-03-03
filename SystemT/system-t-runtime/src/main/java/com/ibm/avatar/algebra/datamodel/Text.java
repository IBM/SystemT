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
package com.ibm.avatar.algebra.datamodel;

import java.util.HashMap;
import java.util.Map;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.logging.Log;

/**
 * Wrapper for raw document text that provides the Span API. This API allows operators to run
 * directly on top of the document text as if it were a span.
 * 
 */
public class Text implements SpanText {

  private static final Map<String, Text> cachedTexts = new HashMap<String, Text>();

  /**
   * Cached Span for type conversion
   */
  private Span cachedSpan;

  /**
   * The actual text string
   */
  private final String text;

  /**
   * Language of the text in the string.
   */
  private LangCode language = LangCode.DEFAULT_LANG_CODE;

  /**
   * True if the string this Text object wraps was derived from a region of another text string.
   */
  private final boolean isDerived;

  /**
   * If this Text object was derived from another Text object, the original Text object.
   */
  private final Text source;

  /**
   * If this Text object was derived from another Text object, a mapping from offsets in this
   * object's string to offsets in the original string. Index i contains the position of character i
   * from this string in the original string.
   */
  private final int[] offsetsTable;

  /**
   * Cached hashCode for this Text object.
   */
  private Integer hash;

  /**
   * Cached tokenization of *this* span, if applicable.
   */
  // Cached tokenization of *this* span (as generated by {@link FastTokenizer} ), if applicable.
  private OffsetsList cachedTokens;

  /**
   * If this Text object has been attached to a tuple of a view, the name of the view and the column
   * within the view containing this Text object. If the object is associated with more than one
   * tuple (or multiple fields within the same tuple), the first assignment will set this field and
   * remaining assignments will leave this field alone. Null if this Text object has not been
   * associated with any view.
   */
  private Pair<String, String> viewAndColumnName = null;

  /**
   * @return If this Text object has been attached to a tuple of a view, the name of the view and
   *         the column within the view containing this Text object. If the object is associated
   *         with more than one tuple (or multiple fields within the same tuple), the first
   *         assignment will set this field and remaining assignments will leave this field
   *         alone.Null if this Text object has not been associated with any view.
   */
  public Pair<String, String> getViewAndColumnName() {
    return viewAndColumnName;
  }

  /**
   * Internal method for generating unique identifiers
   * 
   * @return next value of an internal counter shared across all Text objects
   */
  /**
   * Associate the Text object with a particular column of a particular view. If the object is
   * associated with more than one tuple (or multiple fields within the same tuple), the first
   * assignment will "stick" and remaining assignments will be ignored.
   * 
   * @param viewAndColumnName If this Text object has been attached to a tuple of a view, the name
   *        of the view and the column within the view containing this Text object.
   */
  public void setViewAndColumnName(Pair<String, String> viewAndColumnName) {
    if (null == this.viewAndColumnName) {
      this.viewAndColumnName = viewAndColumnName;
    }
  }

  /**
   * Constructor to create a Text object for a given text.
   * 
   * @param text the actual string to wrap in this object
   * @param language language of the text in the string
   */
  public Text(String text, LangCode language) {
    this.text = text;

    if (null != language) {
      this.language = language;
    }

    this.isDerived = false;
    this.source = null;
    this.offsetsTable = null;
  }

  /**
   * Constructor for derived text.
   * 
   * @param text the string to wrap
   * @param source object containing the original string from which our string was derived
   * @param offsetsTable table that maps offsets in the indicated string to offsets into the
   *        original document
   */
  public Text(String text, Text source, int[] offsetsTable) {
    // Sanity check
    if (text.length() != offsetsTable.length) {
      throw new RuntimeException(
          String.format("Offsets table has %d entries, but string length is %d",
              offsetsTable.length, text.length()));
    }

    this.text = text;

    this.isDerived = true;
    this.source = source;
    this.offsetsTable = offsetsTable;
    this.language = source.language;
  }

  /**
   * Returns the wrapped text.
   * 
   * @return the wrapped text
   */
  @Override
  public String getText() {
    return text;
  }

  /**
   * Returns <code>true</code> if this span is over text that is derived from another source.
   */
  public boolean getIsDerived() {
    return isDerived;
  }

  /**
   * Returns the language of the text in this span.
   */
  @Override
  public LangCode getLanguage() {
    return language;
  }

  public OffsetsList getCachedTokens() {
    return cachedTokens;
  }

  public void resetCachedTokens() {
    cachedTokens = null;
  }

  public void setCachedTokens(OffsetsList cachedTokens) {
    this.cachedTokens = cachedTokens;
  }

  /**
   * Override the language of this Text object with a custom value.
   * 
   * @param newLang new value for language
   */
  public void overrideLanguage(LangCode newLang) {
    language = newLang;
  }

  /**
   * {@inheritDoc} Generate a readable version of this Text object for the purpose of debugging and
   * result viewing.
   */
  @Override
  public String toString() {
    // Get the text onto one line. Avoid String.format() because it's expensive and the UI calls
    // Text.toString() a lot.
    // CharSequence shortenedText = StringUtils.shortenForPrinting (getText (), 40);
    // CharSequence shortenedText =
    // StringUtils.escapeForPrinting (TestUtils.shorten (getText (), 40, false));
    CharSequence shortenedText = shorten(getText(), 40, true);
    return "'" + shortenedText + "'";

    // String shortenedText = TestUtils.shorten (getText (), 40, true);
    // return StringUtils.quoteStr ('\'', shortenedText);
    // return String.format ("Text: '%s'", shortenedText);
  }

  /**
   * Utility function to shorten a string to at most the indicated length, optionally escaping
   * newlines. TODO: This should be replaced by or merged with StringUtils.shorten. It is moved here
   * from TestUtils for Q4 2013. This is different from StringUtils.shorten, and affect many tests.
   */
  private static String shorten(String in, int maxlen, boolean stripNewlines) {
    if (stripNewlines) {
      in = in.replace("\n", "\\n");
      in = in.replace("\r", "\\r");
    }

    if (maxlen >= in.length()) {
      return in;
    } else {
      String ellipsis = "...";
      String head = in.substring(0, (maxlen - ellipsis.length()));
      return head + ellipsis;
    }
  }

  /**
   * If this text was derived from another one, translate the indicated offset of the current string
   * into the corresponding offset of the original string. For example, the first character of the
   * current string may be the fifth character of the original string, so this would translate 1 to
   * 5. <br/>
   * <br/>
   * The offset table maps the offset in the derived text to the offset in the original text. <br/>
   * <br/>
   * <b>Example:</b> <br/>
   * Original text: "A /* comment *&#47;short"<br/>
   * Derived text (with comments stripped): "A short"<br/>
   * Offset table = {0, 1, 15, 16, 17, 18, 19}. <br/>
   * <br/>
   * 
   * <pre>
   * <table border="1">
   * <tr>
   * <td>offset in derived text</td>
   * <td>offset in original text</td>
   * <td>character at the offset in both texts</td>
   * </tr>
   * <tr> <td>ix</td> <td>offsetTable (ix)</td> <td></td> </tr>
   * 
   * <tr><td>0</td> <td>0</td>  <td>A</td></tr>
   * <tr><td>1</td> <td>1</td>  <td>' ' (space)</td></tr>
   * <tr><td>2</td> <td>15</td> <td>s</td></tr>
   * <tr><td>3</td> <td>16</td> <td>h</td></tr>
   * <tr><td>4</td> <td>17</td> <td>o</td></tr>
   * <tr><td>5</td> <td>18</td> <td>r</td></tr>
   * <tr><td>6</td> <td>19</td> <td>t</td></tr>
   * </table>
   * </pre>
   * 
   * @param offset offset of current string
   * @return corresponding offset of original string
   */
  public int translateOffset(int offset) {

    // Do some sanity checks first
    if (false == isDerived) {
      throw new RuntimeException("Called translateOffset() on a non-derived Text object");
    }

    // SPECIAL CASE: For empty html document and document which detag's to
    // empty string, there won't be any entry in offset table
    // returning zero as the offset for them
    if (offsetsTable.length == 0) {
      return 0;
    }
    // END SPECIAL CASE

    if (offsetsTable.length == offset) {
      // SPECIAL CASE: Offset one character past the end of the document.
      return 1 + offsetsTable[offset - 1];
      // END SPECIAL CASE
    }

    if (offset > offsetsTable.length) {
      throw new RuntimeException(
          String.format("Tried to translate offset %d, " + "but offsets table only goes up to %d",
              offset, offsetsTable.length - 1));
    }

    if (offset < 0) {
      throw new RuntimeException("Negative offset");
    }

    return offsetsTable[offset];
  }

  /**
   * Returns the original source Text object if this Text is derived.
   * 
   * @return if the current Text object was derived, the original "source" object, and
   *         <code>null</code> otherwise
   */
  public Text getSource() {
    return source;
  }

  /**
   * {@inheritDoc} Check whether the given Text object is equal to this Text object.
   */
  @Override
  public boolean equals(Object o) {
    if (false == (o instanceof Text)) {
      return false;
    }

    Text other = (Text) o;

    // Shortcut on pointer equality.
    if (other == this) {
      return true;
    }

    // Since this is not null
    if (null == other) {
      return false;
    }

    // Shortcut using hashCode
    if (this.hashCode() != other.hashCode()) {
      return false;
    }

    // Otherwise defer to compareTo
    return this.compareTo(other) == 0;

  }

  /**
   * Special comparison function for two Text types. This prevents
   * {@link com.ibm.avatar.algebra.datamodel.Span#compareTo(SpanText)} from having infinite
   * recursion.
   */
  public static final int compareTexts(Text t1, Text t2) {
    if (t1 == t2) {
      // Fast-path: Check for pointer equality.
      return 0;
    }

    // null is sorted before any other object
    if (null == t1) {
      return -1;
    }

    return t1.compareTo(t2);
  }

  /**
   * Generate hash code for Text object consistent with the object relations.
   * <p>
   * If the text object is not derived, use the string hash code.
   * <p>
   * If the text object is derived, use the combination of its string hash code as well as the hash
   * code of the source, and the first element of mapping table.
   */
  @Override
  public int hashCode() {
    // Use cached hash code if available
    if (null != this.hash) {
      return hash;
    }

    // null object hash to zero
    if (null == this.text) {
      this.hash = 0;
      return hash;
    }

    // use hash code of text string
    this.hash = this.text.hashCode();

    if (isDerived) {
      int[] t = this.getMappingTable();
      int sourceHash = this.source.hashCode();
      this.hash ^= (sourceHash << 8) ^ (sourceHash >> 8) ^ t.length;
      if (t.length > 0)
        this.hash ^= t[0];
    }
    return hash;
  }

  /**
   * Compare two Text objects, according to the following list of rules. These rules are examined in
   * the given order, and first unequal result is returned.
   * <ul>
   * <li>A null object is ordered lower than other objects.
   * <li>The objects are compared by the lexical order of their string values.
   * <li>If neither of the objects are derived, they compare equal.
   * <li>If only one of the objects is derived, it is ordered higher than the other.
   * <li>The objects are compared by the lexical order of their source string values
   * <li>The objects are compared by the sorting order of the mapping tables, which is guaranteed to
   * be stable and compatible with equality, but the actual order is internal and should not be
   * relied upon.
   * </ul>
   * 
   * @param other the text to be compared against the current text
   * @return 1 if the current text sorts higher than the input text, -1 if the input text sorts
   *         higher than the current text, 0 if they are equal
   */
  @Override
  public int compareTo(SpanText other) {
    // Text object ranked lower than span objects
    if (other instanceof Span) {
      return -1;
    }
    Text otherText = (Text) other;

    // short-cut
    if (otherText == this)
      return 0;

    // check if the other is null
    if (null == otherText)
      return 1;

    // check if the string values are unequal
    int ret = this.getText().compareTo(otherText.getText());
    if (ret != 0)
      return ret;

    // check if neither objects are derived
    if ((!this.isDerived) && (!otherText.isDerived))
      return 0;

    // check if one of them is not derived, (the other must be derived;
    if ((!this.isDerived) || (!otherText.isDerived)) {
      if (this.isDerived)
        return -1;
      else
        return 1;
    }

    // check length of mapping table
    int[] t1 = this.getMappingTable();
    int[] t2 = otherText.getMappingTable();
    ret = t1.length - t2.length;
    if (ret != 0)
      return ret;

    // zero length mapping table. (should have returned earlier)
    if (t1.length == 0)
      return 0;

    // compare the first element.
    // TODO: This is not guaranteed to be correct. Cost estimation is necessary.
    ret = t1[0] - t2[0];
    return ret;

  }

  private int[] getMappingTable() {
    return offsetsTable;
  }

  @Override
  public int getLength() {
    return getText().length();
  }

  @Override
  public String getDocText() {
    return getText();
  }

  @Override
  public Text getDocTextObj() {
    return this;
  }

  @Override
  public Span chomp() {

    CharSequence text = getText();

    // Trim off whitespace by first moving the beginning of the span
    // forward, then moving the end back.
    int begin = 0;
    int end = getLength();

    // System.err.printf("Chomping %s\n", this);

    while (begin < end && Character.isWhitespace(text.charAt(begin))) {
      begin++;
      // System.err.printf("Set begin to %d\n", begin);
    }

    while (begin < end && Character.isWhitespace(text.charAt(end - 1))) {
      end--;
    }

    return Span.makeBaseSpan(this, begin, end);
  }

  /**
   * Generate a span object from this Text object covering entire region. The object is cached for
   * reuse.
   * 
   * @return span object generated from this text object
   */
  public Span toSpan() {
    if (null == cachedSpan) {
      cachedSpan = Span.makeBaseSpan(this, 0, getLength());
    }
    return cachedSpan;
  }

  /**
   * Generate a Text object from a String, using cached version if available.
   * <p>
   * Note: Not using the cache as it creates a memory leak.
   * 
   * @param str input String object
   * @return Text object created from input string
   */
  public static Text fromString(String str) {
    // if (!cachedTexts.containsKey (str)) {
    // Text newText = new Text (str, LangCode.en);
    // cachedTexts.put (str, newText);
    // }
    // return cachedTexts.get (str);
    return new Text(str, null);
  }

  /**
   * Convert the given object to Text. The given object must be one String, Text or Span. In case of
   * Text it will be passed through. <br>
   * If the input to this method is null, return null.
   * 
   * @param obj String, Text, or Span object to convert
   * @return Text object converted from source object
   */
  public static Text convert(Object obj) {
    if (obj instanceof String) {
      return fromString((String) obj);
    }
    if (obj instanceof Span) {
      return ((Span) obj).toText();
    }
    return (Text) obj;
  }

  /**
   * Convert the given object to String. The given object must be one of String, Text or Span. In
   * case of String it will be passed through. This method would naturally be called String.convert
   * if Java allowed us to add a method to String class.
   * 
   * @param obj String, Text, or Span object to be converted to a String
   * @return String object converted from source obj
   */
  public static String convertToString(Object obj) {
    if (obj instanceof SpanText)
      return ((SpanText) obj).getText();

    if (obj instanceof String)
      return (String) obj;

    return (String) obj;

    // throw new RuntimeException ("object is not a span , text or string: " + obj.toString ());

  }

  public static void verifyNotString(Object obj, Object culprit) {
    if (obj instanceof String) {
      String msg;
      msg = String.format("Strings are not allowed in tuple.  The culprit is %s\n", culprit);
      throw new RuntimeException(msg);
    }
  }

  @Override
  public String getLemma(MemoizationTable mt) {
    if (!mt.getTokenizer().supportLemmatization()) {
      throw new RuntimeException(
          "The tokenizer does not support lemmatization that is required by GetLemma function");
    }

    boolean debug = false;
    if (debug)
      Log.debug("---------------------GetLemma---------------------");

    Tokenizer t = mt.getTokenizer();
    t.tokenize(this);

    // cachedTokens can't be null
    // and if this is a Span not a Text, beginTok and endTok have to be computed by now
    // for Text, beginTok is 0 and endTok is size()-1 so they have to be computed
    if ((cachedTokens == null)) {
      throw new RuntimeException("Tokenization failed in GetLemma function");
    }

    OffsetsList tokens = cachedTokens;
    int _startToken, _endToken;
    _startToken = 0;
    _endToken = tokens.size() - 1;

    StringBuilder lemmaStr = new StringBuilder();
    for (int tokenIndex = _startToken; tokenIndex <= _endToken; tokenIndex++) {
      if (lemmaStr.length() > 0)
        lemmaStr.append(" ");
      String lemma = tokens.getLemma(tokenIndex);
      lemma = lemma.replaceAll(" ", "\\\\ ");// escape whitespaces inside a lemma
      if (debug)
        Log.debug("%d %s", tokenIndex, lemma);
      lemmaStr.append(lemma);
    }

    if (debug) {
      Log.debug("input: '%s'\nlemma: '%s'", getText(), lemmaStr);
      Log.debug("--------------------------------------------------\n\n");
      Log.disableAllMsgTypes();
    }

    return lemmaStr.toString();
  }

  public Boolean contains(Text other) {
    return this.text.contains(other.text);
  }

  /**
   * Test if this Text object overlaps with the other text object. This is defined as one of the
   * following cases:
   * <ul>
   * <li>this contains the other
   * <li>the other contains this
   * <li>this.tail equals other.head
   * <li>this.head equals other.tail
   * </ul>
   * 
   * @param text2
   * @return
   */
  public boolean overlaps(Text other) {
    // (this contains other) or (other contains this)
    if (this.text.contains(other.text) || other.text.contains(this.text))
      return true;

    // Overlapping without containment.
    // We start at the shortest overlap and stop early.
    String text1;
    String text2;
    if (this.getLength() < other.getLength()) {
      text1 = this.text;
      text2 = other.text;
    } else {
      text1 = other.text;
      text2 = this.text;
    }
    int n1 = text1.length();
    int n2 = text2.length();
    for (int i = 1; i < text1.length(); i++) {
      // (this.head equals other.tail)
      if (text1.substring(0, i).equals(text2.substring(n2 - i)))
        return true;
      // (this.tail equals other.head)
      if (text1.substring(i).equals(text2.substring(0, n1 - i)))
        return true;
    }

    // All other cases return false
    return false;
  }

  public static void main(String[] args) {
    Text text1 = new Text("234", null);
    Text text2 = new Text("45", null);
    Text text3 = new Text("12", null);
    System.out.println(text1.overlaps(text2));
    System.out.println(text1.overlaps(text3));
  }

}
