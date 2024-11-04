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
package com.ibm.avatar.algebra.test.experimental;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.TreeMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.util.compress.CHuffmanEntry;
import com.ibm.avatar.algebra.util.compress.HuffmanCode;
import com.ibm.avatar.algebra.util.dict.DictFile;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.algebra.util.tokenize.StandardTokenizer;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.logging.Log;

/**
 * Test cases and experiments dealing with compressed in-memory dictionaries.
 * 
 */
public class CompressedDictTests extends RuntimeTestHarness {
  //
  // /**
  // * Directory where outputs of the tests in this class are sent.
  // */
  // public static final String OUTPUT_DIR = TestUtils.DEFAULT_OUTPUT_DIR + "/compDictTests";
  // public static final String EXPECTED_DIR = TestUtils.DEFAULT_EXPECTED_DIR + "/compDictTests";

  /** main method for running just one test at a time. */
  public static void main(String[] args) {
    try {

      CompressedDictTests t = new CompressedDictTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      // t.countChars();
      t.huffmanTest();
      // t.utf8Test();
      // t.doubleHuffmanTest();
      // t.compressToksTest();
      // t.skewTest();
      // t.firstTokLenTest();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

      Log.info("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  Tokenizer tokenizer;

  @Before
  public void setUp() throws Exception {
    tokenizer = new StandardTokenizer();
  }

  @After
  public void tearDown() throws Exception {}

  /**
   * Count the number of times each distinct character occurs in a dictionary.
   */
  @Test
  public void countChars() throws Exception {

    startTest();

    final String DICT_FILE_NAME =
        TestConstants.TESTDATA_DIR + "/dictionaries/lotus/organization_full_case.dict";

    final String OUT_FILE_NAME = "counts.txt";

    DictFile dict = new DictFile(DICT_FILE_NAME);

    // We'll build up a set of counts, one for each character that occurs at
    // least once in a dictionary entry.
    TreeMap<Character, Integer> charCounts = dict.computeCharCounts(tokenizer, LangCode.en);

    // For now, just output the counts.
    String outfileName = getCurOutputDir() + "/" + OUT_FILE_NAME;
    OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(outfileName), "UTF-8");

    out.append(String.format("%10s %s\n" + "----------------------------------------------\n",
        "Character", "Count"));
    int totalCount = 0;
    for (char c : charCounts.keySet()) {

      int count = charCounts.get(c);
      totalCount += count;

      if ('\n' == c) {
        // SPECIAL CASE: Escape newlines.
        out.append(String.format("%10s %d\n", "\\n", count));
        // END SPECIAL CASE
      } else {
        String charStr = Character.toString(c);
        out.append(String.format("%10s %d\n", charStr, count));
      }

    }

    Log.info("%d chars total (%d bytes uncompressed)\n", totalCount, totalCount * 2);
    out.close();

    compareAgainstExpected(true);

  }

  /** Compute Huffman codes for the characters that occur in a dictionary. */
  @Test
  public void huffmanTest() throws Exception {

    startTest();

    final String OUT_FILE_NAME = "codes.txt";

    // Directory containing dictionaries to test.
    final File DICT_DIR = new File(TestConstants.TESTDATA_DIR, "dictionaries");

    final List<File> rawDictFiles = FileUtils.getAllChildFiles(DICT_DIR);

    Log.info("Reading dictionary files...");
    ArrayList<DictFile> dictFiles = new ArrayList<DictFile>();
    for (File f : rawDictFiles) {
      dictFiles.add(new DictFile(f));
    }

    // We'll build up a set of counts, one for each character that occurs at
    // least once in a dictionary entry.
    Log.info("Computing character counts...");
    TreeMap<Character, Integer> charCounts = new TreeMap<Character, Integer>();
    for (DictFile dictFile : dictFiles) {
      TreeMap<Character, Integer> localCounts = computeCharCounts(dictFile, tokenizer, LangCode.en);
      DictFile.mergeCharCounts(localCounts, charCounts);
    }

    TreeMap<Character, HuffmanCode> codeTable = computeHuffmanCodes(charCounts);

    // Make sure that every character is accounted for.
    assertEquals(codeTable.keySet(), charCounts.keySet());

    // Write out a code table.
    // For now, just output the counts.
    String outfileName = getCurOutputDir() + "/" + OUT_FILE_NAME;
    OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(outfileName), "UTF-8");
    out.append(String.format("%10s %10s %s\n" + "----------------------------------------------\n",
        "Character", "Count", "Code"));
    int totalCount = 0;
    int totalBits = 0;
    for (char c : charCounts.keySet()) {

      int count = charCounts.get(c);
      totalCount += count;

      HuffmanCode code = codeTable.get(c);

      // Compute how many bits will be used encoding this character.
      int codeLen = code.getLength();
      totalBits += (count * codeLen);

      out.append(String.format("%10c %10d %s\n", c, count, code));
    }

    // Try compressing the dictionary entries, and collect some statistics.
    int totalChars = 0;
    int numEntries = 0;
    int minCompChars = Integer.MAX_VALUE;
    int maxCompChars = 0;

    int[] lengthsHist = new int[108];

    Log.info("Computing character count histogram...");
    for (DictFile dictFile : dictFiles) {
      ArrayList<String> entries = dictFile.getCanonEntries(tokenizer, LangCode.en);

      for (String entry : entries) {
        char[] compressedChars = compress(entry, codeTable);

        // Log.info("Entry '%s' compresses to %d chars: %s", firstTok,
        // compressedChars.length, Arrays.toString(compressedChars));

        totalChars += compressedChars.length;
        numEntries++;

        minCompChars = Math.min(minCompChars, compressedChars.length);
        maxCompChars = Math.max(maxCompChars, compressedChars.length);

        lengthsHist[compressedChars.length]++;
      }
    }

    double avgCompChars = (double) totalChars / (double) numEntries;

    Log.info("%d chars total (%d bytes uncompressed," + " %d bits / %d bytes compressed)\n",
        totalCount, totalCount * 2, totalBits, totalChars * 2);

    Log.info("%1.1f chars/entry on average (range from %d to %d)\n", avgCompChars, minCompChars,
        maxCompChars);

    // Now print out the histogram we've collected
    Log.info("%10s %10s %10s %s\n" + "--------------------------------------------------", "Length",
        "Count", "Percentage", "Cumulative %");

    double totalPct = 0.0;
    for (int i = 0; i < lengthsHist.length; i++) {
      int count = lengthsHist[i];
      double percentage = 100.0 * (double) count / (double) numEntries;
      totalPct += percentage;

      Log.info("%10d %10d %10.1f %10.1f", i, count, percentage, totalPct);
    }

    // Compute the size of a compressed dictionary, assuming character
    // alignment.
    int compressedDictChars = 0;
    for (int i = 0; i < lengthsHist.length; i++) {

      // We assume that there are two tables:
      // Anything less than 64 bits (4 chars) is stored in a table of
      // longs.
      // Longer strings are stored with a two-stage table:
      // first, a 128-bit value -- 96 bits, plus an overflow pointer,
      // next, an overflow array.

      int entryChars;
      if (i <= 4) {
        // Short entry
        entryChars = 4;
      } else {
        // Long entry.
        int overflowChars = Math.max(0, (i - 6) + 1);
        entryChars = 8 + overflowChars;
      }

      // First, compute the number of bytes needed for a dictionary entry
      // when the compressed string is i chars long.
      // We need 8 chars for the base entry, plus (i - 6) + 1 chars
      // of overflow.
      compressedDictChars += entryChars * lengthsHist[i];
    }

    Log.info("Dictionary data structure would require %d bytes\n", compressedDictChars * 2);

    out.close();

    // Output is OS-dependent.
    // compareAgainstExpected(false);
  }

  /**
   * Compute Huffman codes for the characters that occur in a set of dictionaries, then generate a
   * histogram of the length of the first token.
   */
  @Test
  public void firstTokLenTest() throws Exception {

    // Directory containing dictionaries to test.
    final File DICT_DIR = new File(TestConstants.TESTDATA_DIR, "dictionaries");

    final List<File> rawDictFiles = FileUtils.getAllChildFiles(DICT_DIR);

    Log.info("Reading dictionary files...");
    ArrayList<DictFile> dictFiles = new ArrayList<DictFile>();
    for (File f : rawDictFiles) {
      // Log.info("Opening dictionary %s", f);
      dictFiles.add(new DictFile(f));
    }

    // We'll build up a set of counts, one for each character that occurs at
    // least once in a dictionary entry.
    Log.info("Computing character counts...");
    TreeMap<Character, Integer> charCounts = new TreeMap<Character, Integer>();
    for (DictFile dictFile : dictFiles) {
      // Log.info("Computing char counts for dictionary %s", dictFile
      // .getName());
      TreeMap<Character, Integer> localCounts = computeCharCounts(dictFile, tokenizer, LangCode.en);
      DictFile.mergeCharCounts(localCounts, charCounts);
    }

    TreeMap<Character, HuffmanCode> codeTable = computeHuffmanCodes(charCounts);

    // Make sure that every character is accounted for.
    assertEquals(codeTable.keySet(), charCounts.keySet());

    // Compress the first token of each dictionary entry,
    // and collect a histogram of lengths.
    int[] lengthsHist = new int[25];

    Log.info("Computing character count histogram...");
    for (DictFile dictFile : dictFiles) {
      ArrayList<String> entries = dictFile.getCanonEntries(tokenizer, LangCode.en);

      for (String entry : entries) {
        // Pull out the first token; the canonicalized form of the entry
        // already has token boundaries inserted.
        int firstTokEnd = entry.indexOf(DictFile.TOKEN_DELIM);
        if (-1 == firstTokEnd) {
          firstTokEnd = entry.length();
        }
        String firstTok = entry.substring(0, firstTokEnd);

        char[] compressedChars = compress(firstTok, codeTable);

        // Log.info("Token '%s' compresses to %d chars: %s", firstTok,
        // compressedChars.length, Arrays.toString(compressedChars));

        lengthsHist[compressedChars.length]++;
      }
    }

    // Now print out the histogram we've collected
    Log.info("%10s %10s %10s %s\n" + "--------------------------------------------------", "Length",
        "Count", "Percentage", "Cumulative %");

    int totalCount = 0;
    for (int i = 0; i < lengthsHist.length; i++) {
      totalCount += lengthsHist[i];
    }

    double totalPct = 0.0;
    for (int i = 0; i < lengthsHist.length; i++) {
      int count = lengthsHist[i];
      double percentage = 100.0 * (double) count / (double) totalCount;
      totalPct += percentage;

      Log.info("%10d %10d %10.1f %10.1f", i, count, percentage, totalPct);
    }
  }

  /** Compute Huffman codes for characters, then compute codes for the codes. */
  @Test
  public void doubleHuffmanTest() throws Exception {

    final String DICT_FILE_NAME =
        TestConstants.TESTDATA_DIR + "/dictionaries/lotus/organization_full_case.dict";

    DictFile dict = new DictFile(DICT_FILE_NAME);

    // Start by computing the first level of codes.
    TreeMap<Character, Integer> charCounts = computeCharCounts(dict, tokenizer, LangCode.en);
    TreeMap<Character, HuffmanCode> charToCode = computeHuffmanCodes(charCounts);

    // Now compress each dictionary entry using the first-level table, and
    // build up a new set of character counts.
    TreeMap<Character, Integer> codeCounts = new TreeMap<Character, Integer>();

    {
      Iterator<String> itr = dict.getEntriesItr();
      BaseOffsetsList offsets = new BaseOffsetsList();
      while (itr.hasNext()) {
        String entry = itr.next();
        String canonEntry = canonicalizeWithTerm(tokenizer, offsets, entry, LangCode.en);

        char[] compressedChars = compress(canonEntry, charToCode);

        // Increment the appropriate character counts for the compressed
        // chars table.
        for (int i = 0; i < compressedChars.length; i++) {
          char c = compressedChars[i];

          if (codeCounts.containsKey(c)) {
            int prevCount = codeCounts.get(c);
            codeCounts.put(c, prevCount + 1);
          } else {
            codeCounts.put(c, 1);
          }
        }
      }
    }

    // Now we can generate the second-level Huffman table.
    TreeMap<Character, HuffmanCode> codeToCode = computeHuffmanCodes(codeCounts);

    // Now try compressing the dictionary entries to see how small we get.
    // Try compressing the dictionary entries.
    {
      int totalEntryChars = 0;
      int totalChars = 0;
      Iterator<String> itr = dict.getEntriesItr();
      BaseOffsetsList offsets = new BaseOffsetsList();

      while (itr.hasNext()) {
        String entry = itr.next();
        totalEntryChars += entry.length();

        String canonEntry = canonicalizeWithTerm(tokenizer, offsets, entry, LangCode.en);
        String intermediate = new String(compress(canonEntry, charToCode));
        String compressed = new String(compress(intermediate, codeToCode));
        totalChars += compressed.length();
      }

      Log.info("%d chars total (%d bytes uncompressed, %d bytes compressed)\n", totalEntryChars,
          totalEntryChars * 2, totalChars * 2);
    }

  }

  /**
   * See how long it takes to compress all the tokens in the Enron10K data set.
   */
  @Test
  public void compressToksTest() throws Exception {

    final String DICT_FILE_NAME = TestConstants.TESTDATA_DIR
        // + "/dictionaries/lotus/names.dict";
        + "/dictionaries/lotus/organization_full_case.dict";

    final String DUMP_FILE_NAME = TestConstants.ENRON_10K_DUMP;

    // Build up a Huffman code table for the indicated dictionary file.
    DictFile dict = new DictFile(DICT_FILE_NAME);
    TreeMap<Character, Integer> charCounts = computeCharCounts(dict, tokenizer, LangCode.en);
    TreeMap<Character, HuffmanCode> codeMap = computeHuffmanCodes(charCounts);

    // Convert the code table into an array for performance.
    HuffmanCode[] codeTable = new HuffmanCode[Character.MAX_VALUE];
    for (int i = 0; i < codeTable.length; i++) {
      codeTable[i] = codeMap.get((char) i);
    }

    // Read through the documents, tokenizing and compressing the tokens.
    // DBDumpFileScan scan = new DBDumpFileScan(DUMP_FILE_NAME);
    // MemoizationTable mt = new MemoizationTable(scan);
    DocReader reader = new DocReader(new File(DUMP_FILE_NAME));

    FieldGetter<Text> getText = reader.getDocSchema().textAcc(Constants.DOCTEXT_COL);

    int ndoc = 0;
    int ntok = 0;

    long startMsec = System.currentTimeMillis();

    // The original test, using the old DocScan API,
    // directly grabbed the tokenizer from the memoization table
    // in the scan. Since the memoization table is no longer exposed, I am assuming that
    // the tokenizer in use is the Standard tokenizer -- eyhung
    Tokenizer stdTokenizer = new StandardTokenizer();

    while (reader.hasNext()) {
      // Unpack and tokenize the text;
      Tuple doc = reader.next();
      Text t = getText.getVal(doc);
      String docText = t.getText();

      OffsetsList tokens = stdTokenizer.tokenize(t);

      for (int i = 0; i < tokens.size(); i++) {
        String token = docText.substring(tokens.begin(i), tokens.end(i));
        compress(token, codeTable);
      }

      ndoc++;
      ntok += tokens.size();

      if (0 == ndoc % 1000) {
        Log.info("Processed %d tokens from %d documents.\n", ntok, ndoc);
      }

    }

    long endMsec = System.currentTimeMillis();

    double elapsedSec = (double) ((endMsec - startMsec)) / 1000.0;

    Log.info("Processed %d tokens from %d documents in %1.1f sec.\n", ntok, ndoc, elapsedSec);

    // Close the document reader
    reader.remove();
    // QUESTION: This test doesn't do any performance checking, so it always passes -- eyhung
  }

  /**
   * Measure the amount of skew in the histogram of the initial 16 bits of entries in a dictionary.
   */
  @Test
  public void skewTest() throws Exception {

    final String DICT_FILE_NAME = TestConstants.TESTDATA_DIR
        // + "/dictionaries/lotus/names.dict";
        + "/dictionaries/lotus/organization_full_case.dict";

    // Build up a Huffman code table for the indicated dictionary file.
    DictFile dict = new DictFile(DICT_FILE_NAME);
    TreeMap<Character, Integer> charCounts = computeCharCounts(dict, tokenizer, LangCode.en);
    TreeMap<Character, HuffmanCode> codeMap = computeHuffmanCodes(charCounts);

    // Convert the code table into an array for performance.
    HuffmanCode[] codeTable = new HuffmanCode[Character.MAX_VALUE];
    for (int i = 0; i < codeTable.length; i++) {
      codeTable[i] = codeMap.get((char) i);
    }

    // Create a table of counts; index is 16-bit character (unsigned)
    int[] charHist = new int[65536];

    // Make a second pass through the dictionary file, compressing entries.
    BaseOffsetsList tokens = new BaseOffsetsList();
    for (String entry : dict.getEntries()) {

      // Generate the canonical version of the entry.
      String canonEntry = canonicalizeWithTerm(tokenizer, tokens, entry, LangCode.en);

      char[] chars = compress(canonEntry, codeTable);
      char firstChar = chars[0];
      charHist[firstChar]++;
    }

    // Print out the histogram.
    Log.info("%15s %s\n--------------------------\n", "Char", "Count)");

    int nzero = 0;

    for (int i = 0; i < charHist.length; i++) {
      if (0 == charHist[i]) {
        nzero++;
      } else {
        Log.info("%15x %d\n", i, charHist[i]);
      }
    }

    Log.info("%d values have count zero.\n", nzero);
  }

  /**
   * Use the indicated table of Huffman codes to compress the indicated string.
   * 
   * @param str the string to compress
   * @param charToCode mapping from characters to Huffman codes.
   * @return the code, or null if no code exists.
   */
  private static char[] compress(CharSequence str, TreeMap<Character, HuffmanCode> charToCode) {

    char[] buf = new char[128];

    // How many characters in our buffer we've used.
    int bufUsed = 0;

    // The next bit position we will fill in the accumulator
    int nextBitPos = 0;

    // Accumulator for building up the first character. We keep data
    // *left*-justified in here.
    long accum = 0L;

    // Compress each character in the input string.
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);

      HuffmanCode code = charToCode.get(c);

      if (null == code) {
        // String contains a character that we don't have a code for.
        return null;
      }

      // The data in the accumulator is *left*-justified.
      accum |= (code.getPackedBits() << (64 - code.getLength() - nextBitPos));
      nextBitPos += code.getLength();

      // Create characters out of the leftmost bits in our accumulator.
      while (nextBitPos > 16) {
        // This conversion works because chars are UNSIGNED
        buf[bufUsed] = (char) (accum >> 48);
        bufUsed++;
        accum <<= 16;
        nextBitPos -= 16;
      }
    }

    // If there's anything left in the accumulator, stick it into the
    // buffer.
    if (nextBitPos > 0) {
      buf[bufUsed++] = (char) (accum >> 48);
    }

    // Generate a minimum-length result array.
    char[] ret = new char[bufUsed];
    System.arraycopy(buf, 0, ret, 0, bufUsed);
    return ret;
  }

  /**
   * Version of compress() that encodes its table with an array.
   * 
   * @param str the string to compress
   * @param charToCode mapping from characters to Huffman codes.
   * @return the code for each character in the string, or null if no code exists.
   */
  private static char[] compress(CharSequence str, HuffmanCode[] charToCode) {

    char[] buf = new char[128];

    // How many characters in our buffer we've used.
    int bufUsed = 0;

    // The next bit position we will fill in the accumulator
    int nextBitPos = 0;

    // Accumulator for building up the first character. We keep data
    // *left*-justified in here.
    long accum = 0L;

    // Compress each character in the input string.
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);

      HuffmanCode code = charToCode[c];

      if (null == code) {
        // String contains a character that we don't have a code for.
        // Log.debug("Don't have a code for character '%c'", c);

        return null;
      }

      // The data in the accumulator is *left*-justified.
      accum |= (code.getPackedBits() << (64 - code.getLength() - nextBitPos));
      nextBitPos += code.getLength();

      // Create characters out of the leftmost bits in our accumulator.
      while (nextBitPos > 16) {
        // This conversion works because chars are UNSIGNED
        buf[bufUsed] = (char) (accum >> 48);
        bufUsed++;
        accum <<= 16;
        nextBitPos -= 16;
      }
    }

    // If there's anything left in the accumulator, stick it into the
    // buffer.
    if (nextBitPos > 0) {
      buf[bufUsed++] = (char) (accum >> 48);
    }

    // Generate a minimum-length result array.
    char[] ret = new char[bufUsed];
    System.arraycopy(buf, 0, ret, 0, bufUsed);
    return ret;
  }

  /**
   * Use Huffman's algorithm to compute prefix-free character codes.
   * 
   * @param charCounts table of counts for individual characters
   * @return a map from character to compressed code
   */
  public TreeMap<Character, HuffmanCode> computeHuffmanCodes(
      TreeMap<Character, Integer> charCounts) {
    // Create a sorted list of leaf entries.
    ArrayList<CHuffmanEntry> leavesArr = new ArrayList<CHuffmanEntry>();
    for (char c : charCounts.keySet()) {
      int count = charCounts.get(c);
      leavesArr.add(new CHuffmanEntry(c, count));
    }
    Collections.sort(leavesArr);

    // Drop the leaves into a queue.
    Queue<CHuffmanEntry> leaves = new LinkedList<CHuffmanEntry>();
    leaves.addAll(leavesArr);

    // Create an empty queue for the nodes.
    Queue<CHuffmanEntry> nodes = new LinkedList<CHuffmanEntry>();

    // Two-queue version of Huffman's algorithm:

    // while (queues not empty) do
    // choose pair from the leaf and interior queues with lowest count
    // merge the chosen entries
    // done

    while (leaves.size() > 0 || nodes.size() > 1) {

      // Choose the pair of entries with lowest total count.
      // To minimize code length, break ties in favor of the leaves.
      CHuffmanEntry[] pair = new CHuffmanEntry[2];

      for (int i = 0; i < 2; i++) {
        if (0 == leaves.size()) {
          // Ran out of leaves.
          pair[i] = nodes.poll();
        } else if (nodes.size() <= 1) {
          // No nodes
          pair[i] = leaves.poll();
        } else {
          CHuffmanEntry firstLeaf = leaves.peek();
          CHuffmanEntry firstNode = nodes.peek();

          if (firstLeaf.compareTo(firstNode) <= 0) {
            pair[i] = leaves.poll();
          } else {
            pair[i] = nodes.poll();
          }
        }
      }

      // Add a new node for the chosen pair.
      CHuffmanEntry merged = new CHuffmanEntry(pair[0], pair[1]);
      // Log.info("Merged %s and %s to produce %s\n", pair[0],
      // pair[1], merged);

      nodes.add(merged);
      // Log.info(" --> leafOff %d; nodes %d of %d\n", leafOff,
      // nodeOff, nodeSize);
    }

    // Now that we've got the tree, extract the codes.
    CHuffmanEntry root = nodes.peek();
    // Log.info("Root is %s\n", root);
    TreeMap<Character, HuffmanCode> codeTable = root.computeCodes();
    return codeTable;
  }

  /** Try encoding as UTF-8 as an alternative to Huffman. */
  @Test
  public void utf8Test() throws Exception {

    final String DICT_FILE_NAME =
        TestConstants.TESTDATA_DIR + "/dictionaries/lotus/organization_full_case.dict";

    DictFile dict = new DictFile(DICT_FILE_NAME);

    Iterator<String> itr = dict.getEntriesItr();
    BaseOffsetsList offsets = new BaseOffsetsList();

    int totChars = 0;
    int totBytes = 0;

    Tokenizer tokenizer = new StandardTokenizer();

    while (itr.hasNext()) {
      String entry = itr.next();

      tokenizer.tokenizeStr(entry, LangCode.en, offsets);

      // "Canonicalize" the entry into a string; that is:
      // Construct a new string that puts a space between tokens and a
      // newline at the end.
      String canonEntry = canonicalizeWithTerm(tokenizer, offsets, entry, LangCode.en);

      // Convert to UTF-8 format.
      byte[] utf8Bytes = canonEntry.getBytes("UTF-8");
      totBytes += utf8Bytes.length;
      totChars += canonEntry.length();
    }

    Log.info("%d chars total (%d bytes in UTF-8)\n", totChars, totBytes);

  }

  /** Special character that delimits dictionary entries when encoding. */
  public static final char ENTRY_DELIM = '\u2620';

  // U+2620 SKULL AND CROSSBONES (☠)

  /**
   * Add a terminator to the canonical representation of the dictionary entry.
   */
  private static String canonicalizeWithTerm(Tokenizer tokenizer, BaseOffsetsList offsets,
      String entry, LangCode language) {
    String ret = DictFile.canonicalizeEntry(tokenizer, offsets, entry, language);
    return ret + ENTRY_DELIM;
  }

  /**
   * Compensate for the terminators we add to the dictionary entries when generating the entries.
   */
  private static TreeMap<Character, Integer> computeCharCounts(DictFile dict, Tokenizer tokenizer,
      LangCode language) {
    TreeMap<Character, Integer> ret = dict.computeCharCounts(tokenizer, language);

    // Compensate for the entry delimiter going after every entry.
    ret.put(ENTRY_DELIM, dict.getEntries().size());
    return ret;
  }
}
