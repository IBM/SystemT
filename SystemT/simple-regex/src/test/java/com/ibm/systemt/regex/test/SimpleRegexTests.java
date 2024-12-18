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
package com.ibm.systemt.regex.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Random;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.systemt.regex.api.JavaRegex;
import com.ibm.systemt.regex.api.RegexMatcher;
import com.ibm.systemt.regex.api.SimpleRegex;
import com.ibm.systemt.regex.api.SimpleRegexMatcher;
import com.ibm.systemt.regex.charclass.CharIDMap;
import com.ibm.systemt.regex.util.TestHarness;

/** End-to-end tests of the regex library. */
public class SimpleRegexTests extends TestHarness {

  private static final int DEFAULT_FLAGS = 0x0;

  public static void main(String[] args) throws Exception {

    SimpleRegexTests t = new SimpleRegexTests();

    t.setUp();

    long startMS = System.currentTimeMillis();

    // t.doubleBackslashTest();
    // t.currencyTest();
    // t.nonCapturingGroupTest();
    t.longRegexTest();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

    System.err.printf("Test took %1.3f sec.\n", elapsedSec);

  }

  @Before
  public void setUp() throws Exception {

  }

  @After
  public void tearDown() throws Exception {

  }

  /** A test of parsing and compiling simple regular expressions. */
  @Test
  public void simpleRegexParseTest() throws Exception {

    // A version of the CapsPerson regex with counts decreased.
    String pattern = "\\p{Lu}(\\p{L}){0,5}([\\'\\-][\\p{Lu}])?(\\p{L})*";
    // String pattern = "\\p{Lu}(\\p{L}){2,3}";
    // String pattern = "\\p{Lu}(\\p{L})*";
    // String pattern = "(\\p{L})+";

    PrintStream out = System.err;

    out.printf("Orig pattern is: %s\n\n", pattern);

    SimpleRegex re = new SimpleRegex(pattern, 0x0, true);

    out.printf("Parsed regex is: %s\n\n", re.getExpr());

    // The pattern should be able to print itself out verbatim.
    assertEquals(pattern, re.getExpr());

    out.printf("Parse tree is:\n");
    re.dump(out, 0);
    out.printf("\n--\n");

    // Try running the regex.
    String target = "Joe O'Flannigan and Mary Smith-Wesson were here.";

    SimpleRegexMatcher m = new SimpleRegexMatcher(re, target);

    System.err.printf("Matching over: '%s'\n", target);
    System.err.printf("Matches are:\n");

    int[] EXPECTED_STARTS = {0, 4, 20, 25};
    int[] EXPECTED_ENDS = {3, 15, 24, 37};

    int count = 0;
    while (m.find()) {
      System.err.printf("   [%d, %d]: %s\n", m.start(), m.end(),
          target.subSequence(m.start(), m.end()));
      assertEquals(EXPECTED_STARTS[count], m.start());
      assertEquals(EXPECTED_ENDS[count], m.end());
      count++;
    }
    assertEquals(4, count);
  }

  /** Test of the first phone number regex. */
  @Test
  public void phone1Test() throws Exception {

    String pattern = "\\+?\\([1-9][0-9]{2}\\)[\\-]?[0-9]{3}[\\-\\.]?[0-9]{4}";
    // String pattern = "\\([1-9]\\)";

    String target = "For a good time, call (510)-555-1212 " + "x1234 or +(888)-123-4567.";

    int[] expectedStarts = {22, 46};
    int[] expectedEnds = {36, 61};

    genericRegexTest(pattern, target, expectedStarts, expectedEnds);

  }

  /** Test of the WeakInitialWord regex. */
  @Test
  public void weakInitialWordTest() throws Exception {

    String pattern = "([\\p{Upper}]\\.?\\s*){1,5}";

    String target = "I can't wait to have a place of my own! " + "Apartment life is horrible. ";

    int[] expectedStarts = {0, 40};
    int[] expectedEnds = {2, 41};

    genericRegexTest(pattern, target, expectedStarts, expectedEnds);

  }

  /** Test of a problematic regex in the directions annotator. */
  @Test
  public void directionsTest() throws Exception {

    String pattern = "(take\\s+(([A-Za-z]+\\s*-?\\s*\\s+)"
        + "|(\\s+\\s*-?\\s*[A-Za-z]+))(\\s+exit)?\\s*[from|to])([ A-Za-z0-9\\t,])*";

    int flags = Pattern.CASE_INSENSITIVE;

    // We're mostly interested in whether the regex will compile.
    String target = "";

    int[] expectedStarts = null;
    int[] expectedEnds = null;

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * Test of a problematic regex from a selection predicate in personOrgFast.aql.
   */
  @Test
  public void selectionTest() throws Exception {

    String pattern = "(.|\\n|\\r)*,[ \\t]*(\\p{Lu}\\p{M}*(\\p{L}\\p{M}*|[-'.])*[ \\t]*){0,2}";

    int flags = 0x0;

    // We're mostly interested in whether the regex will compile.
    String target = "Hello, World-Leaders";

    int[] expectedStarts = {0};
    int[] expectedEnds = {20};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /** Test of a problematic URL regex (URL2 in namedentity-sekar.aql) */
  @Test
  public void urlTest() throws Exception {

    // The original pattern.
    @SuppressWarnings("unused")
    final String fullPattern = "(" + "((([\\w]+:)\\/\\/)|(w\\w+\\.))"
        + "(([\\d\\w]|%[a-fA-f\\d]{2,2})+(:([\\d\\w]|%[a-fA-f\\d]{2,2})+)?@)?"
        + "([\\d\\w][-\\d\\w]{0,253}[\\d\\w]\\.)+" + "[\\w]{2,4}(:[\\d]+)?" +
        // Begin problematic section
        "(\\/([-+_~.\\d\\w]|%[a-fA-f\\d]{2,2})*)*" +
        // End problematic section
        "(\\?(&?([-+_~.\\d\\w]|%[a-fA-f\\d]{2,2})=?)*)?" + "(#([-+_~.\\d\\w]|%[a-fA-f\\d]{2,2})*)?"
        + ")";

    // The piece that causes problems
    final String pattern = "(\\/([-+_~.\\d\\w]|%[a-fA-f\\d]{2,2})*)*";

    int flags = DEFAULT_FLAGS;

    String target = "http://finance.yahoo.com/q?s=dyn&d=t> - news "
        + "<http://biz.yahoo.com/n/d/dyn.html>) had what sounded like a "
        + "great idea: Buy Enron's flagship trading business in natural gas "
        + "and electricity, and immediately become the dominant force in "
        + "those markets. Dynegy fled when Enron started to collapse under "
        + "an avalanche of scandal. Now, Zurich [Switzerland]-based UBS "
        + "Warburg (NYSE:UBS <http://finance.yahoo.com/q?s=ubs&d=t> - "
        + "news <http://biz.yahoo.com/n/u/ubs.html>) is making the same "
        + "bet, albeit for a lot less money.";

    int[] expectedStarts = null;
    int[] expectedEnds = null;

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /** Test of Java's behavior when given a dot inside a char class descriptor. */
  @Test
  public void dotClassTest() throws Exception {

    final String pattern = "[.]";

    int flags = DEFAULT_FLAGS;

    String target = "Hello, world.  How are you today?";

    int[] expectedStarts = null;
    int[] expectedEnds = null;

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /** Test of a regex that matches the empty string. */
  @Test
  public void matchesEmptyTest() throws Exception {

    final String pattern = "w*";

    int flags = 0x0;

    String target = "Hello, world.";

    int[] expectedStarts = null;
    int[] expectedEnds = null;

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /** Test of a regex in the NonPhoneNum rule that crashed SimpleRegex. */
  @Test
  public void nonPhoneNumTest() throws Exception {

    final String pattern = "\\s*\\:*\\s*.{0,10}\\s*\\+*\\s*";

    int flags = DEFAULT_FLAGS;

    // Nine spaces...
    String target = "          ";

    int[] expectedStarts = {0, 10};
    int[] expectedEnds = {10, 10};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /** Test of a bug in case sensitivity handling. */
  @Test
  public void caseTest() throws Exception {

    final String pattern = "ext\\s*[\\.\\-\\:]?\\s*\\d{3,5}";

    int flags = Pattern.CASE_INSENSITIVE;

    // Nine spaces...
    String target = "Ext. 12345 ext. 12345 etc.";

    int[] expectedStarts = {0, 11};
    int[] expectedEnds = {10, 21};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * Test case for another bug in case-insensitive matching for SimpleRegex
   */
  @Test
  public void caseTest2() throws Exception {

    String pattern = "[A-Z][a-z]*";

    int flags = Pattern.CASE_INSENSITIVE;

    // We're mostly interested in whether the regex will compile.
    String target = "fooBar";

    int[] expectedStarts = {0};
    int[] expectedEnds = {6};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /** Test for handling non-capturing groups. */
  @Test
  public void nonCapturingGroupTest() throws Exception {

    final String pattern = "(?:hello)";

    int flags = Pattern.CASE_INSENSITIVE;

    // Nine spaces...
    String target = "hello";

    int[] expectedStarts = {0};
    int[] expectedEnds = {5};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /** Test of a bug in case sensitivity handling. */
  @Test
  public void currencyTest() throws Exception {

    final String pattern =
        "(francs? congolais|pounds? sterling|(united states |u\\.s\\. |us |singapore |singaporean |sg | australian? |bahamian |bahamas |barbados |belizean |belize |bermudan? |brunei |canadian |canada |cayman islands |cayman |east caribbean |fiji |fijan |guyanese |guyana |hong kong |hg |hk |jamaican? |liberian? |namibian? |new (taiwanese|taiwan|zealand) |solomon islands |solomon |suriname |trinidad and tobago |t&t |kiwi |zimbabwean |zimbabwe )?dollars?|(algerian? |bahraini? |iraqi? |jordanian |jordan |kuwaiti? |libyan? |serbian? |tunisian? )?dinars?|(burundian |burundi |comorian |comores |djiboutian |djibouti |french pacific |guinean? |rwandan? |swiss |congo )?francs?|(argentinan |argentine |chilean |chile |colombian? |cuban? (convertible )?|dominican? |mexican |mexico |philippine |uruguayan |uruguay )?pesos?|(kenyan? |somalian |somali |tanzanian? |ugandan? )?shillings?|(cypriot |cyprus |egyptian |egypt |gibraltar |lebanese |lebanon |saint helena |sudanese |sudan |syrian? |british )pounds?|(czech |slovakian? |slovak )?korunas?|(danish |denmark |norwegian? )?krones?|(icelandic |iceland |swedish |sweden )?kronas?|(tajikistani? )?somonis?|(indian? |mauritian |mauritius |nepalese |nepal |pakistani? |seychelles |sri lankan? )?rupees?|(maltese |malta |(new )?turkish |turkey )?liras?|(moldovan |moldovia |romanian? )?leus?|(qatari? |saudi arabian? )?riyals?|(azerbaijani? |turkmenistani? )?manats?|(belarusian? |russian? )?rubles?|(thailand |thai offshore |thai )?bahts?|(moroccan |morocco |united arab emirates |u.a.e. |u.a.e |uae )?dirhams?|(iranian |iran |omani? |yemeni? )?rials?|euros?|(japanese |japan |jp )?yens?|deutsche marks?|french francs?|(afghanistan |afghan )?afghanis?|(albanian? )?leks?|(angolan? )?kwanzas?|(armenian? )?drams?|(aruban? )?florins?|(bangladeshi? )?takas?|(bhutanese |bhutan )?ngultrums?|(bolivian? )?bolivianos?|bosnia-herzegovina (convertible )?marks?|(botswanan? )?pulas?|(brazilian |brazil )?reale?s?|(bulgarian? )?levs?|(cambodian? )?riels?|(cape verde )?escudos?|west african cfas?|central african cfas?|(chilean|chile) unidade?s? de fomento|(chinese |china )?yuans?|(costa rican? )?colons?|(croatian? )?kunas?|(eritrean? )?nakfas?|(estonian? )?kroons?|(ethiopian? )?birrs?|(gambian? )?dalasis?|(georgian? )?laris?|(ghanaian |ghana )?cedis?|(guatemalan? )?quetzals?|(haitian |haiti )?gourdes?|(honduran |honduras )?lempiras?|(hungarian |hungary )?forints?|(indonesian? )?rupiahs?|(israeli? )?(new )?shekels?|(kazakhstani? )?tenges?|(korean? )?wons?|(kyrgyzstani? |uzbekistani? )?soms?|(lao )?kips?|(latvian? )?lats?|(lesotho |lesothan )?lotis?|(lithuanian? )?litas?|(macau )?patacas?|(macedonian? )?denars?|(malagasy )?(ariaries|ariary)|(malawian? |zambian? )?kwachas?|(malaysian? )?ringgits?|(maldives )?rufiyaas?|(mauritanian? )?ouguiyas?|(mexican|mexico) unidade?s? de inversion|(mongolian? )?tugriks?|(myanmar )?kyats?|(netherlands antillean )?guilders?|(new mozambican? )?meticals?|nicaraguan? cordoba oros?|(nigerian? )?nairas?|(panamanian |panama )?balboas?|(papua new guinea )?kinas?|(paraguay )?guaranis?|(peruvian |peru )?nuevos? sole?s?|(polish |poland )?zlotys?|(samoan? )?talas?|(sao tome )?dobras?|(sierra leonean |sierre leone )?leones?|(south african? )?rands?|special drawing rights?|(swazi )?lilangenis?|(tongan? )?pa'angas?|(ukrainian? )?hryvnias?|(vanuatu )?vatus?|(venezuelan? )?bolivars?|cents?|(viet nam |vietnam |vietnamese )?dongs?|ounces? of (aluminum|copper|gold|palladium|platinum|silver))";
    int flags = Pattern.CASE_INSENSITIVE;

    // Nine spaces...
    String target = "Sudo give me 1000 dollars.";

    int[] expectedStarts = {18};
    int[] expectedEnds = {25};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /** Test of the DOTALL flag. */
  @Test
  public void dotAllTest() throws Exception {

    final String pattern = "foo.bar";

    int flags = Pattern.DOTALL | Pattern.MULTILINE;

    // Nine spaces...
    String target = "foo\r\nbar foo\nbar foo bar";

    int[] expectedStarts = {9, 17};
    int[] expectedEnds = {16, 24};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * Test of the "\x" escape.
   */
  @Test
  public void backslashXTest() throws Exception {

    // Look for a single double-quote
    final String pattern = "\\x22bar\\x22";

    int flags = 0x0;

    // Nine spaces...
    String target = "foo\"bar\"fab";

    int[] expectedStarts = {3};
    int[] expectedEnds = {8};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * Test of the Unicode (\\u) escape.
   */
  @Test
  public void backslashUTest() throws Exception {

    // Look for a single double-quote
    final String pattern = "\\u00a9bar[\\u00a9]";

    int flags = 0x0;

    // Nine spaces...
    String target = "foo©bar©fab";

    int[] expectedStarts = {3};
    int[] expectedEnds = {8};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * Test of the "\0" escape.
   */
  @Test
  public void backslash0Test() throws Exception {

    // Look for a single double-quote
    final String pattern = "\\0100bar[\\0100]";

    int flags = 0x0;

    // Nine spaces...
    String target = "foo@bar@fab";

    int[] expectedStarts = {3};
    int[] expectedEnds = {8};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * Test of negation in character classes.
   */
  @Test
  public void charClassNegationTest() throws Exception {

    // Look for 'a' followed by something that is not 'b'
    final String pattern = "a[^b]";

    int flags = 0x0;

    String target = "foo bar fab";

    int[] expectedStarts = {5};
    int[] expectedEnds = {7};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * A test open-ended repeat expressions like {2,}, related to bug #141715
   */
  // @Test
  public void infiniteRepeatTest() throws Exception {

    // Look for any character
    final String pattern = "\\w{2,}";

    int flags = 0x0;

    String target = "foo bar fab";

    // TODO: Once the expression compiles, correct these guys
    int[] expectedStarts = {0, 3, 7};
    int[] expectedEnds = {2, 6, 10};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * Test case for bug #141716: SimpleRegex can't handle the "\S" escape
   */
  @Test
  public void backslashSTest() throws Exception {

    // Look for any non whitespace character
    final String pattern = "\\S+";

    int flags = 0x0;

    String target = "foo bar fab";

    int[] expectedStarts = {0, 4, 8};
    int[] expectedEnds = {3, 7, 11};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * Test case for bug #141888: SimpleRegex can't handle the combination "{x,y}?"
   */
  // @Test
  public void optionalRepeatTest() throws Exception {

    // Regex from the forwardBlock annotator
    final String pattern = "Quoting\\s*.{1,120}?:\\s*(\\n)+\\s*(>\\s*)";

    int flags = 0x0;

    String target = "Quoting Mr. Smarty: \n" + "> I'm smart...\n";

    // TODO: Once the expression compiles, correct these guys
    int[] expectedStarts = {0};
    int[] expectedEnds = {22};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * Test case for bug #141891: SimpleRegex can't handle escaped backslashes
   */
  @Test
  public void doubleBackslashTest() throws Exception {

    // Look for backslashes...
    final String pattern = "\\\\";

    int flags = 0x0;

    String target = "This is a blackslash: \\ ;-\\";

    int[] expectedStarts = {22, 26};
    int[] expectedEnds = {23, 27};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * Test of the CharIDMap data structure.
   */
  @Test
  public void charIDMapTest() throws Exception {

    // Generate some random intervals.
    Random r = new Random(42);

    char[] starts = new char[65536];
    char[] ids = new char[65536];
    int numIntervals = 0;
    {
      int maxStart = 0;

      while (maxStart < 65536) {
        maxStart += r.nextInt(1024);
        if (maxStart < 65536) {
          starts[numIntervals] = (char) maxStart;
          ids[numIntervals] = (char) r.nextInt(1024);

          // System.err
          // .printf("Chars starting from %d get id %d\n",
          // (int) starts[numIntervals],
          // (int) ids[numIntervals]);
          numIntervals++;
        }
      }
    }

    // Encode the intervals in a CharIDMap.
    CharIDMap map = new CharIDMap(numIntervals);
    for (int i = 0; i < numIntervals; i++) {
      map.addInterval(starts[i], ids[i]);
    }

    // Look up each character in turn, and verify that it has the correct
    // value.
    int numLookups = 0;
    long startMs = System.currentTimeMillis();
    for (int count = 0; count < 50; count++) {
      for (int i = 0; i < numIntervals; i++) {
        for (int c = starts[i]; c < 65536 & c < starts[i + 1]; c++) {
          // System.err.printf("Trying char %d\n", (int)c);
          assertEquals((int) ids[i], (int) map.lookup((char) c));
          numLookups++;
        }
      }
    }
    long endMs = System.currentTimeMillis();

    long elapsedMs = endMs - startMs;

    System.err.printf("%d lookups in %d msec --> %1.1f lookups/sec\n", numLookups, elapsedMs,
        1000.0 * (double) numLookups / (double) elapsedMs);
  }

  /**
   * Test case for a bug in multi-regex mode. When a pair of similar regexes appeared in the input
   * to the SimpleRegex compiler, matches from only one of them were produced.
   */
  @Test
  public void repeatedRegexTest() throws Exception {
    final String[] PATTERNS = {"a", "a",};

    final int[] FLAGS = new int[PATTERNS.length];
    Arrays.fill(FLAGS, Pattern.DOTALL);

    final int[][] EXPECTED_BEGINS = {{0}, {0}};

    final int[][] EXPECTED_ENDS = {{1}, {1}};

    // Text against which to perform matching.
    final String TARGET = "a";

    genericMultiRegexTest(PATTERNS, FLAGS, TARGET, EXPECTED_BEGINS, EXPECTED_ENDS);
  }

  /**
   * Test for bug #167305 - SImpleRegex throws ArrayIndexOutOfBounds on long regexes. Currently
   * disabled because it fails. Whoever fixes this bug should re-enable the testcase.
   */
  @Test
  public void longRegexTest() throws Exception {

    // The regex is *really* long, so we need to express it as an
    // array of string constants, then join them together inside the
    // test case.
    final String[] patternPieces = {
        "(propanaminium|bromo|methylsulfonylbenzamide|butyrolactone|induced|bromohexyl|isoindolinyl|propiolyl|octyloxybiphenyl|heptanediol|benzocyclohepten|triazatetracyclo|chloroacetate|cyclohexylmethyl|methyloxazol|dimethylacetal|diglycidyl|fluorenylmethyloxycarbonyl|naphthylazo|tolyl|phenylbutane|dihydrochloride|lactate|isopropanol|indazol|hypoxanthine|butadiene|succinyl|propenylamino|amyl|methylpyridazin|bipyridine|amidino|xylo|dihydroxybiphenyl|dimethoxypyrimidin|butyloxyphenyl|fluorobenzyloxy|oxaspiro|glycero|xylofuranose|diamin|salt|oxocyclopentyl|ethylaminoethyl|phenoxyethyl|gold|ethylacetamide|ornithyl|cyclopentoxy|aceticacid|phenoxazin|phenylalaninamide|difluorobenzamide|thiocarbamoyl|butoxymethyl|hydroxycyclohexylamino|methylphenoxy|glutamine|thioxomethyl|methosulfate|pyrroline|chlorobenzylidene|cholamidopropyl|fluoromethyl|thiazinan|methylphosphonate|cyclobutanecarboxylate|pentene|nitrite|indium|orthophosphate|thioxanthone|oxazin|chlorobenzyloxy|phenylboronic|triphenylphosphine|carbonyloxymethyl|monohydrate|sulfo|methoxypropyloxy|myristyl|butenoyl|endcapped|chlorophenyl|tin|ferric|butanoic|pyridinylcarbonyl|pyrimidineamine|ditrifluoroacetate|propoxyl|aminoprop|pyrazoline|acetoacetate|azepanyl|dichlorobenzamide|piperazinecarboxylate|methoxyphenylamino|hydroxyethoxy|phenethylamine|diazaacenaphthylene|phenylpropionic|methylpropylthio|dichlorobenzamido|anisole|fluorophenoxy|oxazepine|phenethylamino|thiocyanate|undecan|dioxabicyclo|ylcarboxylic|acenaphthene|oxazepino|alanin|hexahydrophthalazin|thiocyanato|pentamethylpiperidin|isatin|chinoxalin|chlor|cis|phenoxybenzyl|cyclohexene|thiopyrano|benzazepin|trihydroxyphenyl|oxyacetic|oxopropyl|butylidenebis|zirconium|aminopyrrolidin|cyclopropanecarbonyl|norpregna|acetoacetic|diethylaniline|dibenzyloxy|benzodioxane|thioglycolate|butyldimethylsilyloxyethyl|azapentalene|aminoethyl|tetrahydroisoquinoline|isonicotinoyl|bromopropyl|aspartyl|nitroaniline|benzothiazole|glucitol|anthracen|hydrazin|phenylquinolin|added|phenylthiophenyl|nitroanilino|methoxycarbonylbenzyl|phthalazinyl|bicyclo|silacyclohexyl|tetramethylene|trifluoropropane|anthracenyl|dibromophenyl|methylbenzoyl|methoxybenzofuran|phosphonooxy|octadecane|carbomethoxyphenyl|osmium|difluoropropyl|propionyloxy|nicotinamid|maleimide|dimethoxyquinoline|benzothiazolyl|pyrimidone|octanyl|epoxytricosa|oxazolidinecarboxamide|dimethylammonio|maleimido|dichloride|trimethoxystyryl|pyrimido|dichlorobenzoic|phenylalanin|undecen|onyl|benzoat|triethylamine|trityloxy|dimethylpropionyl|potassium|trifluoromethylsulfonyl|benzothiazolinone|propionyloxymethyl|butanone|hexenyloxy|dioxepin|oxazol|dimaleate|triflate|tbutoxycarbonyl|iodonium|biphenol|tetrahydrothiophenium|carbonitrile|trifluoromethylbenzamide|isothiazolin|benzhydryloxy|aminopyrimidine|fluorobenzoyl|pyrrolidinyloxy|tolylamide|sulfanyl|benzonitril|methylthiochroman|trifluoromethanesulfonamide|silylpropyl|ammonium|oxooxazolidin|isopropoxymethyl|dibenzyl|dimethylbenzyloxy|ureidomethyl|carbamat|bipiperidinyl|pentafluoropentyl|dimethylbutoxy|ylbenzyl|furylmethyl|pyran|heptadiene|naphthyridin|difluorophenylacetyl|bromophenol|bipyrrolidinyl|hydrazinecarboximidamide|bromoacetate|trifluromethyl|pyridone|trifluoride|methylpyrrolidin|dihydropyridine|benzimidazo|dimethylcyclopropane|dodecylamine|carboxymethyloxy|tetrafluorobenzyl|dodecylamino|quinolyloxy|cyclohexan|indane|phenylglycinyl|theophylline|morpholinylcarbonyl|ylethinyl|oxygen|chloropyrazin|piperazin|trimethoxysilylpropyl|butylsulfinyl|pentachlorethyl|disulfide|homoserine|tri|methylsulfonyl|succinamic|boronate|methylpropan|hexadecyl|trifluoromethylbenzonitrile|decahydro|difluorobutyl|ylcyclopropyl|phenyloxazol|methylaminophenyl|sulfate|hydrazon|diaminoethane|tetraen|benzoazepine|morpholinylmethyl|oxatricyclo|trifluoromethane|carbonyldi|thioureidol|butoxyethoxy|carbamoyl|carboxyethyl|methylphenylamino|amidinophenoxy|phosphinyl|dimethylindole|phenylpropionamide|pyridylmethoxy|octanamide|azepan|pyridinemethanol|fluoroethoxy|xylopyranoside|benzotriazolyl|diethylaminocarbonyl|triisopropylsilyloxy|diiso|methylphenylsulfonyl|dicarboxylate|butyramide|leucinate|dimethylglycyl|quinol|itaconate|hydroxyacrylamide|blocked|butyramido|benzyloxybenzyl|hexafluoropropane|methylpropanoate|aminobutyric|methylpyrimidine|diaminodiphenylmethane|dithian|ethynyl|fluoropyrrolidin|phenylbenzimidazole|ethylbenzyl|pentofuranosyl|functionalized|cyclohexen|hpyrrole|trihydropurine|benzoic|cyanoimino|sulfinamide|hpyrrolo|trimellitate|benzoin|phenone|ethoxide|furazan|methoxybenzyloxy|platinum|ylmethylidene|chlorobenzothiazol|diolate|dimethoxynaphthalene|pyrimidinium|acetylaminomethyl|methylcoumarin|succinimidyl|naphthacene|naphthalimide|dihydropyrazin|sulfooxy|butoxyethyl|pyrone|xanthen|hexanoylamino|diphenylmethylene|sulfoxide|glycolate|dipalladium|benzthiazole|dilithium|anisoyl|oxobutanoate|calcium|oxopyrrolidin|stearoyl|ylideneamino|carbamic|dihydrobenzo|rhodium|maleimidophenyl|nitrobenzene|diethylamide|tritylsulfanyl|sulfinic|hydroxyacetic|carboxymethylamino|dimethylcyclopropanecarboxylate|carbonat|piperidinoethyl|methylpiperazin|pyrid|pyrrolidinomethyl|hydrazinecarboxylic|nicotinamide|cyclopropanecarbonitrile|dioxolane|thiophenecarboxylic|amidinobenzoyl|carbonyloxy|amylperoxy|chlorosulfonyl|hydrochloric|hydrochlorid|diethylene|diaminophenoxy|dithien|nitrate|biphenyl|tertbutoxycarbonylamino|urethane|benzothiazepin|quinazolinedione|dipyridine|tetrahydrocarbazol|dimethoxyquinazoline|cyclohexadien|dichlorophenoxy|dodecyloxy|cyclohexylimino|isopropoxycarbonyl|methylcarbamoyl|tributyl|nitrostyrene|acetohydroxamic|methylbis|nitrobiphenyl|butylcarbonyl|butanoyl|pyridylmethyloxy|tolylamino|argininamide|azaspiro|phenylpropionyl|methylsulphonylamino|butylcyclopentadienyl|alanyl|difluorobenzothiazol|methoxybenzonitrile|phenylacetate|isocyanurate|hexylene|propylurea|zirconate|hexoxy|methylbenzenesulfonamide|bromophenyl|triphenylamine|tetracarboxylate|benzopyrano|oxopropanoate|galactopyranose|dioxaphosphorinan|xanthin|hydroxybutoxy|benzeneacetamide|guanosine|triethoxysilyl|undec|azepin|modified|tritylaminothiazol|pregn|propan|benzisothiazole|tetrahydrofurfuryl|lanthanum|methacryloxyethyl|tribromomethyl|difluorophenyl|quinolylmethyl|homopiperazin|hexahydrothieno|methylindenyl|octyloxycarbonyl|phenylethanamine|naphthoate|pentamethyl|ylcarbamate|cetyl|butylbenzoate|butynyl|methoxyiminoacetamide|tetrahydrobenz|ylmethoxycarbonylamino|cyclohexanecarboxylic|phthalic|dihydropyrazol|dithiin|methoxyiminoacetamido|cyanomethoxy|sulfonylcarbamoyl|disodium|hydroxynaphthalene|dichloroethyl|rhenium|distearyl|benzofuranyl|benzoxadiazol|acryloyloxypropyl|pivalate|phenylpyrrolidine|pyridinecarboxylate|oxepine|diallyl|chloropropane|oxepino|hexenyl|sulfosuccinate|ribofuranoside|hydroxyindole|bistrifluoromethyl|thiopyran|erythro|butylstannyl|phenylalanyl|mercaptopropionate|glutamate|napthyl|bistrifluoroacetate|benzeneamine|methoxyethoxymethoxy|dichlorobenzoyl|pyrrolidinecarboxylate|kalium|dimethylaminomethylphenyl|cinnamoyl|imidazolidinon|cyclopropylsulfonyl|benzotriazepin|benzenediazonium|diazobicyclo|isothiazolyl|catalysed|dimethoxybenzoic|oxoimidazol|benzothiophen|chlorobenzo|azetidinone|trisulfonic|ethanediol|ylimidazo|phenylpropan|chlorophenylamino|carbonic|pentylbiphenyl|triazin|furo|propen|triene|terephthalic|isoquinolinone|fluorobutyl|trifluoromethylpyrazol|tetrahydroimidazo|triethanolamine|dimethylhydantoin|cyanomethylcarbamoyl|aminoethane|dihydroxybenzoate|acetylpiperidin|dimethoxybenzyl|difluoropiperidin|anti|prop|chlorid|triethylsilyl|pentafluoroethyl|glycinamide|pyrimidin|perfluorophenyl|phenoxy|thiomorpholin|acenaphthyl|methoxybenzoic|furanone|methylheptane|ethoxysilane|dimethylbutanoic|nitroindole|hexanediol|sulfonylphenyl|tetraphenyl|cyclopropylethyl|quinuclidinyl|carboxylate|methylpyridin|difuran|benzocycloheptene|naphthylthio|diethylamine|caprolactone|pyridylthio|ethylmethylamino|dipentyl|phenolate|olate|diethylamino|phenylmethanone|azepinyl|undecyl|octahydrofluorenyl|fluorobenzofur|acryl|cyclopentylpiperazin|trisodium|propionylamino|heptanedione|dithiol|methylsulfanylphenyl|imidazolidinone|ortho|isopropoxide|methylbutanoic|decanoic|phenyloxy|yloxy|cyclobutylpiperazin|pyrrolidinoethoxy|pentylphenyl|norbornen|furoate|dichloroethane|fluoropyridin|oxoazetidin|nonanoic|dienamide|diaminobutane|pyridyldithio|toluenesulphonyl|thien|para|chlorethyl|cyclobutylcarbamoyl|maleic|ethylcyclohexyl|pyrazolidone|ethoxypropyl|aminobutyryl|isobutyl|triphosphate|octadecyl|butylaminosulfonyl|methylpropionamide|pyrrolidinylcarbonyl|cresol|chlorobenzenesulfonamide|oxopentanoic|thiouracil|butylaniline|tetrasulfide|methyltetrahydro|oximino|benzoxy|triazol|benzimidazolone|trimethylsilylethynyl|methylbut|dec|trifluoromethylsulfanyl|benzothiepin|bicyclohexyl|methyloxazole|phenylcarbonylamino|dimethylpyrrolidin|benzamidine|hydroxycoumarin|glyoxylamide|oxopropanoic|benzoyl|dihydroquinolin|methoxybenzenesulfonyl|cyclohexylthio|nitrovinyl|ethylenedioxy|riboside|triazinyl|anhydride|methylsilane|proline|isophthalamide|trihydro|naphthalenesulfonic|tocopherol|trimethylstannyl|guanidine|glucopyranoside|anthraquinone|methylpyrimidin|sulfophenylazo|dimethylureido|valeramide|mannopyranosyl|perfluorohexyl|guanidino|dimethylthieno|benzisothiazol|butyldimethyl|ethylpentyl|butylfluorenyl|quinoline|butylisoxazol|thiolate|deoxyadenosine|enylamino|dimethylammonium|fluoroindole|isocyano|tosylate|carbamyl|dibenzothiophene|diphosphonic|cycloprop|trifluoromethoxyphenoxy|diaminobiphenyl|sulfinyl|hydroxyacetyl|octahydropyrrolo|propylpyrrolidin|hexylphenyl|propylaminomethyl|imidazolidinyl|ascorbic|hydroxypyrrolidine|trichloromethyl|hexakis|benzoimidazole|aminocarbonylphenyl|tert|allyl|dimethylpropanoate|ethanediyl|isopropylphenol|methylsulfanyl|dimethylpyrimidine|trifluormethoxy|cyclohexyl|oxiran|methylpyrrole|methylammonium|isocyanide|ethanoic|difluoropyrrolidin|hexan|benzothiophene|methylpyrrolo|dimethoxyphenoxy|oxazolidinyl|indolizine|isoleucine|methylpropyl|trichloroethoxycarbonylamino|methylpentanamide|tropane|dicyclopenta|heptyloxy|diphenylacetyl|indolizino|forming|silanol|diethylcarbamoyl|diaza|tetrahydropyridazin|ethylthiomethyl|cyclobutane|chloroethyl|quinolinol|ylmethylene|trimethylbenzyl|trimethylsilylethyl|isopropoxypropanoic|methylimidazolidine|diazo|alaninate|ethynylphenyl|eicosyl|ethylpiperazin|methylpiperazinyl|propionat|azabicyclo|oxotetrahydro|thioacetate|methansulfonyl|norleucine|morpholin|piperidinomethyl|methoxyphenoxy|valinate|oxim|hydrazinyl|iodoethyl|fmoc|benzimidazolinyl|ethylcarbamoyl|acrylonitrile|pentafluorethyl|pyrimidinamine|methylguanidine|phenylpyrazol|oxycarbonyl|furan|methylguanidino|trifluoromethoxybenzoyl|furancarboxamide|propylphenoxy|methylquinoline|benzenamine|butyldiphenylsilyl|phenylacetonitrile|hexen|tetrazol|formylphenyl|cyclohexylsulfonyl|methylaminosulfonyl|butylidene|thiol|quinazolinyl|water|oxobut|propylxanthine|methylphenol|propoxyethyl|ethanone|trifluoromethylbiphenyl|naphthalenesulfonate|anilinofluoran|morpholinocarbonyl|iodopropyl|trifluoroethane|pyridinesulfonamide|acryloxy|anisidine|cresyl|fluorobenzenesulfonamide|niobium|dimethoxybenzoyl|cyclohexanedicarboxylate|pyrimidinediamine|thiomethyl|acryloyl|carbonyl|cyclohexylamide|silane|methylbenzimidazole|cyclohexylbenzimidazole|formylhydroxyamino|tetradecanoyl|isopropoxy|methoxybenzophenone|aspartate|glutaric|fluorophenoxymethyl|phenylalanine|pyridylmethyl|threonyl|butylbenzene|benzeneethanamine|pyrimidyl|ylbenzamide|methylheptyloxy|difluorobenzo|dioleoyl|acryloxyethyl|dimethylbiphenyl|omega|dihydroxypyrimidine|pyrazole|fructose|tetramethylchroman|piperidone|trioctyl|methoxychinazolin|mannopyranoside|anilinium|methoxybenzoyl|tributyltin|pyrazolo|chlorobenzoylamino|methylcarbamic|undecene|aminedihydrochloride|dimethylbutanoyl|dimethoxybiphenyl|phenylallyl|ethylpropoxy|cyclobutylmethyl|propylaminocarbonyl|tetramethylcyclopentadienyl|methoxycarbonylethyl|methylmorpholin|hydroxybutanoic|methylisoquinolin|aminomethylphenyl|benzoate|dansyl|methoxide|palmitate|methanesulfonyloxyethyl|bisulfite|benzodiazepine|methylbiphenyl|difluorocyclopentyl|propanedioate|aalpha|bipiperidin|aminopiperidine|dichloropyridin|propionic|diimide|caprolactam|phenylpropane|sulfonamide|isothiazol|sulphide|nitrobenzoate|tetrahydrochinolin|ethoxyphenyl|sulfonamido|methylvinyl|isopropylphenyl|isobutylamino|nalpha|oxopyrido|cyclohexyloxycarbonyl|dicarbamate|dihydropyrazino|decanoyl|methylbutanoyl|phenanthridine|trienoic|propyl|tetrazine|propyn|valerylamino|carbobenzoxy|dihydronaphtho|chlorobenzyl|nonanoyl|fluorenone|cyclohexanone|hydroxamic|cyclohepta|dihydrobromide|sulfuric|silanyl|methanesulfonamide|indenyl|dioxan|quinolinyl|thiadiazole|methanesulfonamido|dodecene|thiadiazolo|isopropylpiperidin|diphenylpiperidin|phenylethanol|benzyloxycarbonylamino|hydroxy|ethylaminomethyl|glycidyloxy|acetoxyphenyl|methoxypropan|deoxyuridine|cyclododecyl|monoamide|phenylfuran|butoxycarbonylaminoethyl|norbornane|naphthoquinonediazide|formaldehyde|hydroxyethylamino|fumarate|dihexyl|oxopropanoyl|dioxoimidazolidin|nitropyrimidin|acetamid|carboxypropyl|trimethoxy|linked|methylbenzoyloxy|naphthalenesulfonyl|dihydroxypropoxy|piperidinoethoxy|cyclopropanecarboxamide|pentyloxy|tetrahydroquinazolin|trimethylhexyl|dichlorobiphenyl|triethyleneglycol|methylbenzonitrile|dideoxy|phenylpropyl|butanoylamino|norbornyl|brom|sulfamoylphenyl|methacryloxypropyl|penicillanate|picolinamide|dimethylcarbamate|mercaptopropylamino|isobenzofuran|tolylene|propyloxy|naphthalenedicarboxylic|triazine|phenol|methoxycarbonylmethoxy|methylpiperidine|dimethoxyquinazolin|triazino|tetraazabenzo|methylpiperidino|arginyl|phenoxymethyl|methylcyclohexane|pyrimidinecarboxamide|isopropylbenzene|phenoxyacetate|methylphenyl|dinitrate|propanenitrile|methylethylidene|furancarboxylic|ylpiperazine|phenylmethane|trifluoromethylsulfonyloxy|hydroxypropoxy|hydroformate|methylpropionic|chlorobiphenyl|ascorbyl|cyanopropyl|trimethylbenzene|xanthene|nitrophenyloxy|neopentyl|glucopyranose|pentylthio|phenylbutyrate|cyclopropa|phenylsulfonyloxy|butyrylamino|diphenylphosphine|ylcarbamoylmethyl|cyclohexylamine|isophthalic|heptane|ditert|dicyanophenyl|methacryloyloxypropyl|isopropyl|ethanoyl|cyclopropoxy|benzoquinone|diphenylphosphino|cyclohexylamino|ethylpyrimidin|dimethyltetrahydro|dihydroxypropyl|pyrrolidinol|methoxycarbonylmethyl|pyridin|azetidinylmethoxy|hydroxyphenylglycine|biotin|piperazinylmethyl|imidazolidin|oxyimino|propene|acetoacetylamino|silacyclohexane|butyloxycarbonylamino|aminoacetate|dipropylamino|indolinone|triphenylsulfonium|gallium|hydroxysuccinimidyl|glutathione|ylidene|thiadiazin|hydroxylethyl|phenylphenol|oxamide|cyclopropylisoxazole|pyridylcarbonyl|thiazolidinone|oxyl|allyloxy|phenoxyacetamide|phenoxyacetamido|stearamide|methylbutylamino|difluoroethyl|propynyl|methanesulfonylphenyl|isooctyl|dihydroquinazolin|indazole|dioxin|dihydrobenzothiophene|pivaloyloxymethyl|chloropurine|tetrazolyl|trimethylpentyl|benzoxepine|acenaphthylen|diimino|dodecanol|naphthalenamine|terephthalamide|methylpentyloxy|nitroquinoline|dimethyl|iodophenyl|pentadeca|dioxepine|benzoxepino|caproic|isoxazol|hydroxybut|azonia|ylmethyl|oxazinan|trimethylsilylethoxymethyl|hexyne|dimethylsiloxy|triazacyclononane|fluorobenzo|aziridine|niobate|quinazolin|oxazole|imide|oxopiperidine|butylene|propylamide|heptyloxyphenyl|oxazolo|fluorophenylcarbamoyl|cyclopropylmethylamino|dichlorphenyl|anilinocarbonyl|tetraazacyclododecane|phenylen|dimethacrylate|trimethylcyclopent|carbo|cyclohexanoyl|isocyanatopropyl|enenitrile|ethoxyimino|fluorophenylmethyl|piperidylcarbonyl|benzothiazoline|cycloheptylamino|glucosamine|ibuprofen|methylcarbonylamino|triphenylmethylamino|cyclobutylmethoxy|pyridon|nitrobenzoic|morpholinomethyl|quino|pyrrolidinocarbonyl|diphenoxy|trinitro|chloroaniline|formate|methylbenzofuran|chloroanilino|pentoxy|diheptyl|phosphoramidite|dimethylpent|carbonylmethyl|carbodiimide|glycidoxy|thiadiazol|anisyl|diaminobenzoate|hydroxybiphenyl|benzyloxymethyl|hexyl|hexyn|xylene|methylsulfinyl|phenyl|tryptophan|carbamoylamino|hexyloxyphenyl|dimethylene|methoxybenzenesulfonamide|dimethylpyrazol|pyrrole|dithiophosphate|behenyl|diazatetracyclo|trimethylcyclohexyl|pyrrolo|trimethyl|dioxol|acetamidophenyl|hydroxymethylene|ylethynyl|bisulfate|benzhydryloxycarbonyl|deoxy|fluorobenzamide|dioxocyclobut|isobutoxycarbonyl|diacrylate|cyclohexanecarbonitrile|carbothioic|methylethenyl|dodec|propionyl|tetrahydropyran|oxopyrrolidine|fluorobenzamido|tetrafluoroethyl|triethyl|phenylcarbamate|pyridazinone|diaminomethylene|polymers|phenoxyethoxy|mercaptopropyl|dimethoxypyridin|pentanoate|estr|quinazolinamine|morpholinium|pyrrolidinyl|octene|heptadecenyl|disiloxane|cyclopropan|aminocrotonate|chlorobenzamide|benzodiazocine|benzyloxy|dimethylsilanediylbis|isobutyric|dichlorobenzenesulfonyl|tetraazabicyclo|chlorobenzamido|dihydropyrido|furyl|carboxyphenyl|benzaldehyd|trifluoromethylphenyl|aminomethylene|sulphur|bromobenzyl|methoxypyridin|ethylsulfanylmethyl|phenylphenyl|glucose|dibenzoyl|azelate|propandiol|perfluorobiphenyl|urea|capped|malonamic|octadecenoic|ethylheptyl|diphosphaspiro|methyldimethoxysilane|cephem|diethylacetal|tyrosyl|difluorobenzyl|arginine|ethoxypropionic|trifluoromethylpyrazole|copolymers|benzothiazolium|bitartrate|cyanophenyl|indolin|dibutylphenyl|methyltetrazol|cyanobiphenyl|piperidinylidene|fluorobenzaldehyde|tetraazacyclotetradecane|tetraglycidyl|octylphenol|dimethylpropane|iodo|oxolan|phthalimidoethyl|dibrom|methanesulfonyloxymethyl|hydroxyethylthio|methoxybenzylamino|methoxylphenyl|diethoxy|imine|dimethylpropylcarbamate|carboxamid|propylamine|hydroxybenzamide|tetrahydrothiopyran|dimethoxycarbonyl|methylcyclopentyl|dihydroxyphenyl|ylsulphonyl|butenedioate|myo|pivaloylamino|pyrazolin|thioacetic|cyclopropylmethyl|imino|pentadienyl|pyrimid|propylamino|trifluoromethylaniline|dimethylpyrazole|ylethoxy|indan|hindole|octyloxyphenyl|ylpiperidin|tertiary|trifluoromethylanilino|dimethylpyrazolo|hexahydrofuro|propionamide|methylpropionyl|methoxycinnamate|methylcarbonyl|benzothiazolinonyl|cyclohexanediamine|propionamido|triphenylmethoxy|ethylurea|tetrafluoroethoxy|methoxysilane|oxoacetamide|tetraazol|isocyanate|fluoroethyl|methoximino|isocyanato|acetone|trimethylsilylcyclopentadienyl|tetraaza|diethylaminomethyl|pyridyl|gallate|isothiazoline|phenylhexyl|methoxyethoxymethyl|decenyl|methylpyrazole|tetrahydropyrid|ylsulfamoyl|aminoethylthio|phenylaminocarbonyl|methanone|aminopropan|tetrahydropyrimidin|methylpyrazolo|methylbenzyloxy|aminobenzamide|piperidylmethyl|butylphenol|tetraazaindene|furancarboxylate|nitrophenoxy|biphenyltetracarboxylic|glycidoxypropyl|morpholinoethoxy|glycidyl|pyridinedicarboxylic|cyclopentylmethoxy|morpholinoethyl|chlorobenzene|tetrahydropyrido|indacen|dimethylbenzamide|propylpiperazin|methylbutanamide|methylbenzoate|pteridin|cyclopentanol|bromoanilino|methoxymethylene|silanyloxymethyl|methylthiobenzoyl|thiazolone|chlorphenyl|hydroxyphenylthio|hepta|methylbenzophenone|trifluoroacetamide|dimethylpiperidine|thiopropionate|oxazolidinone|inden|trihydrate|dimethylpiperidino|trifluoroacetamido|propylcarbamoyl|benzohydrazide|hydroxyprop|ribofuranose|diisopropylbenzene|methoxypropyl|trione|end|ene|naphthaldehyde|nonylphenol|trichlormethyl|dimethoxymethyl|thiazolecarboxylic|butyldimethylsilyloxy|methanesulfonic|caproyl|triethoxy|pentafluoro|hydroxybutyric|isocyanatophenyl|ureylenebis|dihydrobenzimidazol|methylamin|phenanthrolin|ethylsulfonyl|ylphenoxy|glutarate|pyrimidinylamino|fluorobenzenesulfonyl|aluminum|dicyclopropyl|carbazole|acetonyl|dichlorovinyl|diisopropylphosphoramidite|methacryloyloxyethyl|oxadiazine|hydrocinnamate|phosphoric|quinolinylmethyloxy|disulphonic|toluene|phosphono|methylbutanoyloxy|triazolium|phosphorothioate|chlorocarbonyl|bismuth|palladium|benzylsulfonyl|chloroimidazo|tetrachloride|tetrazolinone|valyloxy|nitrobenzoyl|chloropyrimidine|oxazolidin|aminopropylamino|pyrrolin|triazaspiro|propylthio|quinuclidine|azidopropyl|glucopyranosyl|phenylimino|oxazolylmethoxy|methylbenzylamine|undecanoic|phosphoramidate|geranyl|phenoxyacetic|tetrahydroquinoline|morpholinyl|methylbenzylamino|pentyloxycarbonyl|methoxyphenol|tridecafluoro|mercaptophenyl|octylphenyl|phenylmethylene|methylpyrido|aminopropionate|methoxybenzamide|mercaptide|cyclopentylethyl|tetramethylcyclohexyl|cyanoacetate|cyclohexanol|methoxybenzamido|oxobutanamide|cyclopropylaminomethyl|amidinophenyl|chlorobenzoate|dipentaerythritol|nitrophenylimino|butadienyl|hexyloxy|isonicotinic|chlorophenoxymethyl|propionamid|carbothioyl|sulphate|ethylpyrrolidin|thienylmethoxy|aminopropane|neo|nitrophenylsulfonyl|eth|cyclohexanamine|chloride|methyliden|pentanoylamino|trifluoromethylbenzene|dichloroethenyl|compound|benzindenyl|hydroxymethylpyrrolidine|phenoxycarbonyl|chromene|distannoxane|tetrahydropyrimidine|chromeno|monophosphate|dioxaborolan|acetoxy|hexadecene|pregnadiene|hydantoin|molybdate|glycerin|cyclopropanecarboxylate|isobutyryl|butylphenyl|dimethylcarbamoyloxy|pentylcyclohexyl|dihydroxy|dihydroimidazo|dimethylisoxazol|diethylaminoethoxy|carbinol|dimethylbenzofuran|heptadecyl|cyclopentapyrimidin|morphine|phenanthren|octanediol|oxazinyl|ceph|xylyl|ruthenium|pentynyl|aminoethanol|cyclopropylcarbonyl|iminoethyl|methylcarbamoylmethyl|methanoyl|diazole|tetrabutylphosphonium|thenyl|pyrimidinecarboxylate|diacetic|borane|bromobutyl|oxopentyl|stannane|thiazolidine|indol|ylacetic|nitrobenzenesulfonate|androstane|butoxycarbonylaminophenyl|ethylpyrimidine|dodecan|carbonate|octynyl|methylisoxazol|butylaminomethyl|pyrimidinol|heptanol|bromobenzene|methylpropionate|trioxa|dopamine|bromopentyl|indolyl|nonylphenyl|quinoxalinyl|lysyl|mercaptoethyl|phenylbenzamide|trioxo|exo|esters|dioxopiperidin|sorbitan|tricyclo|pyrrolidin|oxathiolan|pentenoate|dilauryl|benzisoxazole|mesyl|propanamide|phosphonium|trifluoromethylimidazo|ring|ethanolamine|hydroxycarbonyl|pyrazolyl|methylpentanoyloxy|diiodo|benzen|propanamido|dioxole|thioacetyl|xylylene|valerolactone|fluoran|trimethylbicyclo|oxobutanoic|furanylcarbonyl|acidethylester|dioxolo|hexadeca|bisphenol|tetrasodium|carbaldehyde|aminomethane|bistrifluoromethylphenyl|tetrahydrophthalazin|dibutoxy|maleinimide|hydroxypyrazole|trifluoropropan|threonine|diphosphonate|tryptophyl|dimethanol|dihydroisoquinoline|dimethoxybenzo|borohydride|methylpiperazine|dihydropyrimidine|benzenesulfonylamino|tetrabutylammonium|dipyridin|piperidinecarboxamide|hydroxypentane|methylpiperazino|methylpropoxy|samarium|malononitrile|methoxyethylamino|propylpyrimidin|nitrobenzyloxy|glycerol|aminohexanoic|benzylidene|pyrrolidinone|naphthylsulfonyl|methylhydantoin|hydroxybenzoate|fluoropiperidin|benzoxepin|benzonitrile|pyrido|cyclopentane|polymer|propanoate|hindol|ynoic|ethenyl|phosphorus|triacetic|pyrimidine|methoxyphenyl|oxopyridazin|carbonylaminomethyl|oxamate|octyloxy|oxyethylene|ylsulfonyl|oxobicyclo|dihydroisoquinolin|cyclopropyl|benzyliden|trifluoroethanone|epoxypropoxy|ylbenzenesulfonamide|bromothiophene|nitrobenzylidene|chlorovinyl|butylaminocarbonyl|borate|nicotinoyl|diiodide|benzil|benzothiazine|dihydrophenanthrene|methoxyphenylmethyl|fluoren|methylbenzenesulfonyl|methoxypyrid|benzhydryl|phenylchroman|hydroxymethylphenyl|methylpentan|carboxyl|methylthiazol|non|diamine|propenenitrile|hydroxyethane|cesium|nor|oxopropionate|pyridinyloxy|terphenyl|chlorine|benzoxazole|chlorobenzylamino|diamino|ethoxybenzyl|methanesulfonyl|phenoxyl|isopropylbenzyl|hydrogenphosphate|heptamethyl|melamine|pyrrolidon|difluorphenyl|isoquinolinesulfonyl|dimethylpropylamino|cyclohexylpiperazin|benzenedimethanamine|binaphthalene|phenylsulfanylmethyl|ethylene|indanyloxy|dihydropyrrol|chlorostyryl|phenylcyclohexyl|tetramethylpiperidin|bipyridinyl|thiomorpholine|acryloyloxyethyl|pyridine|amide|dihydropurine|phenylpyrido|thiomorpholino|tetrabutyl|methylendioxy|carbonylimino|nitroethylene|phenylindol|amido|pyranyl|phosphoryl|cyclohexylcarbamoyl|triphenylphosphoranylidene|azidophenyl|nitropyridine|tetrahydrofuran|hydroxypyrid|bipyrazinyl|pyrrolidinylmethyl|diaminomethyleneamino|androsten|methylidene|chloroacetanilide|cyanobutyl|chloroform|oxopiperazine|hydroxypiperidin|dimethylbutanamide|ethanesulfonic|anilin|phthalazin|phenylbenzene|pyrrolyl|cyclopentanecarboxamide|azetidine|thiocarbamate|pyrimidinyl|benzylpiperazin|aminosulfonylphenyl|chloromethyl|trimellitic|triisopropylsilanyloxy|methylenedioxybenzylamino|ethylenedioxyphenyl|phenoxyacetyl|aluminium|selenium|methoxybenzylthio|octadeca|propanamine|chlorphenoxy|thioureido|benzylcarbamoyl|fluoromethoxy|dichlorosilane|benzol|butylimino|aminopropyl|aminosulphonyl|phenylsulfonylamino|butenyl|tetrahydroindenyl|ylmethyloxy|cyclobutan|diethyleneglycol|methylbenzyl|silan|methylbenzimidazol|aminopiperidin|acetonitrile|camphorsulfonate|diaminophenyl|butyronitrile|sulphamoyl|pentyne|cyclohexylbutyl|acetylamino|dimethylbutane|diphenylamine|butylphenoxy|anthranilic|cholesta|cyclohexylene|choline|dimethylpiperidin|diphenylamino|methylpyrid|dodeca|oxalamide|phenylthiomethyl|protected|glyceryl|cyclopentanecarboxylic|methoxybenzaldehyde|molybdenum|nitropyridin|dimethylsilandiylbis|gadolinium|terephthalate|dimercapto|dibromomethyl|methylhexane|ethyleneglycol|dioxoisoindolin|triphenylmethyl|cyclohexylcarbonyl|methylaminocarbonyl|cyclohexylaminocarbonyl|dimethylol|carbomethoxymethyl|dichloropyrimidine|phenylimidazo|cycloheptatrien|phenylamin|undecenyl|isonicotinate|oxirane|trifluor|pentanedioic|ethylsulfanyl|hydroxydiphenylmethyl|tridecyl|ethylammonium|dimethoxyethyl|benzenediol|carbohydrazide|methylglycine|sorbitol|dimethylsulfamoyl|methansulfonylamino|tetrahydropyrano|oxadiazolin|chloronaphthalen|iodobenzene|hydrazinecarboxamide|nonadecyl|ylamin|pentan|diacetyl|tricarbonyl|xanthone|dimethylphenylimino|dien|dimethoxybenzamide|tellurium|perhydro|diethylphosphono|ylacetyl|thiazole|cyclobuten|methanesulfonylbenzyl|aminophenol|dimethylsilanyloxy|trimethylsilylmethyl|benzylsulfanyl|phosphonate|tetrakis|thiazolo|carbethoxy|phosphat|sulfopropyl|phenylamide|acetoxyethyl|deoxyguanosine|iodobenzyl|pyrrolidyl|ylpyridin|pentyloxyphenyl|ethoxyphenoxy|azoniabicyclo|thioxothiazolidin|ethylenebis|thiophenecarboxamide|tetrahydroxy|dihydroxyethyl|catecholate|pyrimidinedione|tetrahydropyridin|anthranilate|amine|chloroethylsulfonyl|titanium|iminodiacetic|methylmorpholine|thiaprolyl|amino|butylbenzoyl|lactic|tertiarybutyl|phenanthryl|tricyclohexylphosphine|methylmorpholino|dichlorbenzyl|methacrylamide|ethanediamine|monobutyl|methacrylamido|tetrahydronaphthalen|methylvalerohydrazide|nitric|pyridinediamine|ynoate|methylaminomethyl|toluenesulfonate|methoxypiperidin|diphosphite|bipyridin|hydroperoxy|pentanedione|penten|choro|dichlorophenol|dimethylaniline|imidazolidine|benzopyran|dimethylanilino|propenal|oxepin|iodophenylamino|oxazolidinedione|vanadium|perhydroazepin|carboxylat|glyphosate|ylmethylamine|benzotrifluoride|oxoisoindoline|methylpyrrol|fluorphenyl|pyroglutamyl|dimethylfuran|ylmethylamino|ylmethylthio|diphenylsulfonium|benzodiazepin|tetrahydronaphthyl|hydrochloride|cyclopentenone|ethylaminocarbonyl|isopropylphenoxy|yliden|carbamate|methylacrylamide|chloroethoxy|tetrahydro|methoxybiphenyl|benzyl|copolymer|ethene|phosphorochloridate|trimethylphenoxy|trifluormethyl|methylsulfinylphenyl|cyanide|pyridylmethylamino|benzochinon|propylenediamine|propylphenyl|cyclooctene|triazabicyclo|etheno|tetramethyldisiloxane|tartarate|ester|ethylacetate|isophthalate|borat|leucine|ethoxybenzoyl|oxopiperazin|terbium|hexyloxycarbonyl|tritylamino|iodobenzoate|dichlorphenylamino|dimethylpropanamide|aminosulfonyl|thienylmethyl|aminium|phthalonitrile|bromomethylphenyl|methylimidazolin|hexane|thiochroman|methylnicotinate|tetramethylpiperidyl|trimethylcyclopentadienyl|difluoro|arsenic|phenylmethanesulfonyl|ethylbiphenyl|trifluorophenoxy|dimethylsilylbis|dimethylpiperazine|hydroxybutane|ynyl|dodecyl|trifluoroprop|hydride|methoxynaphthalene|propoxysilane|methoxytetrahydropyran|hydrido|methylbutyric|uranium|farnesyl|phosphin|indacene|vinylsulfonyl|phenoxazine|dihydronaphthalen|phenylsulphonyl|benzimidazole|maleimidomethyl|hexanedione|methylenbis|carboxybenzyl|biimidazole|trifluoromethylbenzyl|trimethoxysilan|fluorene|tolyloxy|thiocarboxamide|propenoate|phenylpiperidin|propynoic|cellosolve|thiazin|ylimino|phenylbenzyl|sydnonimin|aminoiminomethylphenyl|methylpyrrolidinium|methylmethanamine|stearate|trimethylsilyl|methylthien|carboxyaldehyde|oct|diol|xylofuranosyl|bithiophene|diazabuta|peroxide|zinc|dion|trifluoromethylethyl|benzophenone|carbapen|aluminate|tetrahydropyrrolo|ethanesulfonyl|isodecyl|butyldimethylsilyl|aminophenyl|methoxyphenylthio|naphthalen|propionanilide|propanoicacid|benzeneacetonitrile|isononyl|oxoimidazolidin|methoxyquinazoline|trifluoropropyl|leucinyl|phenylamine|phenylsulfamoyl|trimethylbenzoyl|phenylamino|cyclobutanecarboxylic|benzenemethanamine|cyanobenzyl|benzothieno|leucinamide|dimethylchroman|iodopropargyl|keto|diphenylimidazole|methylcarbamate|thiochromen|difluorobiphenyl|propylbutyl|pyridinecarboxylic|bromomethyl|carboxylic|phenothiazin|furoic|pyrazolidine|fluorobenzothiazol|biphen|cinnolin|crosslinking|hydroxyurea|propoxy|dihydroxybenzyl|ylsulfanyl|tolylureido|sulfinate|prednisolone|peroxy|nitrophenylamino|trimethylsilanylethynyl|methylcyclopentadienyl|morpholinothieno|solution|dodecyloxyphenyl|dimethyloctyloxy|difluoromethyl|butoxycarbonyloxy|chrysanthemate|fur|phosphonomethoxy|trifluoro|quinolin|decalin|chlorobenzoic|phenylphosphine|propargylamino|fluorobiphenyl|diacetoxy|decene|dichlorophenyl|bromobenzo|boric|nitroquinolin|trifluoromethylpyridine|naphthalic|pyrrol|benzothiazepine|linking|naphthylene|dichloro|methylpentyl|toluenesulfonic|vinylene|methoxypyrido|pyrane|isoquinolinium|purine|quinuclidin|methylallyl|ethylureido|pyrano|phenylsulfonamido|oxoheptyl|ethylthieno|phenylester|oxadiazolyl|sulfophenyl|benezenesulfonamide|oic|dimethylbenzene|sulfonylcarbamate|thiazol|phthalimide|diphenylsilyl|aminoaniline|olhydrochloride|pteridine|phthalimido|scandium|aminoanilino|butyloxy|dimethylpyridine|benzamidehydrochloride|piperidinone|napthalen|dihydrogen|iminomethyl|methylfuro|chlorbenzyl|naphthyridine|dihydroindeno|ethylidenebis|tetrahydrofuryl|benzenesulfonamidehydrochloride|ethylenediaminetetraacetic|cinnamate|methylprop|phorbol|titanate|piperidinecarboxylate|himidazole|glyoxylic|dioxolanyl|difluorobenzene|imidazolinyl|dimethylaminopropyl|acryloyloxy|heptylcyclohexyl|oxiranylmethoxy|methylnaphthalene|trisphosphate|methylumbelliferyl|tetrahydropyridyl|perfluoromethyl|perfluorobutyl|methylpyridine|benzothiopyran|tetrahydrocarbazole|methoxy|adamantan|pentylamine|glycinyl|acetophenone|dioxaborolane|butylamide|dichlorophenylsulfonyl|sulfonylmethyl|butyrophenone|pentylamino|amidinophenylcarbamoyl|tetramethyl|methylfuran|methylphenylmethyl|butylamido|estra|sodium|thiophen|hexahydropyrrolo|crotonate|nitrotoluene|fluorobenzylidene|propylsulfonyl|isoamyl|hexamethyl|benzotriazin|succinate|azulene|aminophenylamino|ethylpiperidine|dicarbonitrile|carbonothioyl|bipyridyl|trichloro|amylphenoxy|propyltrimethoxysilane|dimethylimidazo|inner|piperidinylcarbonyl|diyl|crosslinked|diphosphate|methoxypyrrolidine|glucuronide|methylene|indolecarboxamide|difluorobut|hexadecanoic|cyclohexadiene|methylquinazoline|pyridinylamino|boron|cyclobutyl|propargyl|ylcarbamic|bipiperidine|ethylideneamino|silyl|butan|erythropentofuranosyl|fluorovinyl|hexaene|hexamethylenediamine|hydroxyphosphinyl|methylpentane|triphenylphosphorane|oxopyrimidin|dihydroisobenzofuran|cycloheptene|thia|cyclopentanone|cumyl|octoxy|tetramethylammonium|trifluoromethylbenzoic|trifluoromethanesulfanylphenyl|chlorobenzofuran|benzoimidazol|pentanoyloxy|ethoxycarbonyloxy|thio|oxoethoxy|piperazinedione|imidazolylmethylthio|nitrophenylthio|amidinobenzyl|dichloropyridine|rubidium|benzyloxycarbonylmethyl|acridinium|methylsulfonylamino|dimethylpropan|diphenyloxazol|valinamide|bromoethoxy|butene|oxadiazin|",
        "benzoyloxy|myristoyl|dimethylmorpholine|ethylsulfinyl|dicyclohexyl|glycerine|isobutoxyphenyl|dimethylmorpholino|methoxypropoxy|dihydropyran|butylpiperazin|pentyl|pentyn|glycine|difluoroethoxy|cyclopentanecarboxylate|dimethoxybenzenesulfonyl|phenylisocyanate|benzothien|pyrazolidin|dimethylcarbamyloxy|carbonylchloride|piperidine|chloropyridine|piperidinol|aminoiminomethyl|quinolizine|adamantylmethyl|chlorophenoxy|buten|ylaminomethyl|piperidino|methylbutyryl|fluorophenethyl|propanesulfonamide|nonene|butylcarbamoyl|chrysene|ethylidene|formamidine|butylbenzyl|bromobenzoic|dichloropyrid|trifluorophenyl|methylphenylsulfonylamino|carbonylamino|benzotriazol|trifluorethyl|diazabicyclo|ethylthio|diethoxyphosphinyl|hexylthio|substituted|dimethylhexyl|bromoethyl|trimethylsilanyl|chloroindole|benzamide|mercury|butoxycarbonylpiperidin|benzamido|methylthiazole|titaniumdichloride|homocysteine|aminocarbonylmethyl|pyrazine|napthalene|propoxyphenyl|chlorophenethyl|methanesulphonyl|pyrazino|cystine|propanesulfonic|acetyloxymethyl|methylbenzaldehyde|aminobenzene|carboxypentyl|pyridylcarbamoyl|ethoxypyridin|dicyclohexylphosphino|nona|trifluoromethylpentyl|hexanoyloxy|acetylene|methacryloxy|methan|chromone|pyridinone|decahydronaphthalene|phenylpentyl|hexahydroindolizin|terpyridin|methacryloyl|ylacetoxy|edta|carboxyphenoxy|oxoquinoline|benzoylphenylamino|diketo|pentadecane|dinitrobenzene|oxybis|propenyl|furoyl|butylamine|phenylazo|ylazo|methoxycarbonyl|prostadienoic|butylamino|benzolsulfonamid|oxadiazol|bisphosphonate|tertbutylphenyl|quinolyl|dihydrochlorid|phenylbutan|octahydrophenanthrene|bistrifluoromethylbenzyl|isothiazole|hydrazinecarboxylate|methoxymethoxy|methylpyrrolidine|zirconiumdichloride|naphtalene|oxohexyl|ethoxylphenyl|isothiazolo|methoxymethyloxy|methylimidazolium|diisopropoxy|chloronaphthalene|chlorobenzoyl|barbituric|isopentyl|trien|thiadiazine|oxa|hydroxyphenethyl|guanidin|diformyl|benzylmethylamino|strontium|nitrobenzonitrile|trimethylspiro|cyclobutylcarbonyl|toluenesulfonyl|oxo|ethylcarbonyl|ethylcarbamate|heptan|oxamic|oxy|phenyloxazolidin|diylbis|formylmethyl|methoxybenzoate|chloromethylphenyl|dihydrobenzofuran|methoxyphenylacetyl|thiophenecarboxylate|tetralin|hept|oyl|diphenyltetrazolium|dimethylaminomethylene|oate|tetrahydropyrimido|methoxybenzyl|butylperoxy|cinnolinecarboxamide|ylamide|thiazolidin|diphenylmethoxy|phosphoethanolamine|carbonylbis|crotonic|phenylsulphanyl|oxylbenzamidine|dimethylaminophenyl|methylbenzamide|oxiranyl|azol|naphthalenecarbonitrile|methylbenzamido|butylester|hexahydrocyclopenta|methylpropionitrile|carbomethoxy|behenate|benzimidazol|oxomorpholin|dimethylphenylamino|trifluoromethylphenoxy|hydroxypyridine|dibutyl|nitroimino|trimethoxysilyl|benzofuran|carboxybenzoyl|propenyloxy|butyric|sulphonic|allyloxycarbonyl|ethylbenzoate|toluoyl|hydroxypropan|methyleneamino|trimethylcyclohexane|anthryl|oxoprop|asparaginyl|phenylethynyl|acrylate|acridin|aminoethylamino|hydroperoxide|pentanamide|piperidinyl|furyloxy|phenylpropionylamino|tetraethyl|octadecene|hydroxyhexyl|tetrahydroindol|dihydroimidazol|oxopyridin|salicylate|hepten|benzylalcohol|methylthiopropyl|decyloxyphenyl|thiobis|piperazinyl|dimethylisoxazole|siloxane|dimethylbenzenesulfonamide|phenylmethyloxy|arsenide|allylamine|cyanoethyl|tetrahydropyridazine|isopropylidene|dicyano|ethanoate|chloroacetyl|phenylpropylamine|allylamino|trichloride|hexanoate|ylsulfanylmethyl|bromophenoxy|oxoacetic|nonafluoro|butyldimethylsilanyloxy|prosta|naphthalenone|dimethylaminopropoxy|adenine|phenylpropylamino|olean|triisocyanate|ynylamino|ethoxymethyl|hydroxybutanoate|mesitylene|hexanol|oxoindan|pyridinedicarboxylate|glutaminyl|dimethylaminoethylthio|dimethylquinolin|pentaen|cyclohexanediol|methylthiothiophene|porphine|piperazinylcarbonyl|tetracarboxylic|ammonia|sulphonamide|diethoxysilane|succinimide|mandelate|hydroxypropane|thenoyl|ammonio|formamidothiazol|succinimido|dimethoxyquinolin|trifluoromethylbenzoyl|tetradecyloxy|ethoxycarbonylmethoxy|cyanate|sulfat|methylthiopyrimidine|dithia|styrol|phenylhydrazone|phenylpyrrolidin|oleoyl|cyclopropylethynyl|gluco|indene|acetoxymethyl|piperid|dithio|chloropyrid|pentaerythritol|methanesulfinyl|isophthalamic|methoxyimino|indolizinyl|cyclopropane|hexa|cyclooctadiene|methylethylamine|indeno|methoxyindan|fluorobenzoylamino|cyanopyridine|methylquinolin|tetrahydropyranyl|methylstyryl|cyanoanilino|cyclohexylethyl|methylhexyl|fluorobenzoate|phenanthridin|imidazolone|pentadiene|methylethylamino|cyclohept|piperidinemethanol|aminobiphenyl|prolinamide|oxazine|octanesulfonate|phenylfuro|didodecyl|ethoxymethylene|dihydride|oxazolone|methoxyphenethyl|oxazino|triol|iodobenzoic|trideca|ethenediamine|dimethylbutanoate|ornithine|bromobutoxy|catechol|hydrate|phenylprop|cyclopentan|oxypyridin|benzoindenyl|heptafluoro|benzothiadiazole|pentylphenoxy|indanylidene|uracil|salicylic|ylmethylphenyl|isobutyryloxy|spiro|tetrahydrofuro|dihydropyridazine|purin|bromobenzoyl|dioxobutyl|trifluoromethylphenylthio|hydroxyimino|ylprop|diaminohexane|methylpyridinium|perfluorooctyl|hydroxyindan|methylbutyloxy|decyloxy|quinolinone|quinolizin|tetramethylfuro|methylacrylic|thienylsulfonyl|ylamine|benzenediamine|propynyloxy|nitromethylene|phenylpropanoate|ylamino|adamantyl|phenylpyrimidine|fluorophenylamino|mercaptoacetate|isoquinolin|nonyloxy|carbamicacid|propylsulfanyl|oxindole|propylammonium|hexadecenyl|copper|morphinan|quinolinecarboxylic|valine|pyridazine|phosphocholine|naphthalenylmethyl|dimethylsilane|methoxyphenylsulfonyl|valeric|carbamimidoyl|mercaptotetrazole|benzoylthio|pyridazino|pyrrolidineacetamide|hydroxycyclohexyl|allyloxyphenyl|oxylphenyl|isopropylacetamide|diphenylacetamide|ethanediylbis|tribromo|hexahydrobenzo|thiosemicarbazone|benzyloxyimino|hydroxymethane|dichlorophenylmethyl|tetraacetic|cyclooctyl|benzoylphenyl|dipropoxy|octan|octylthio|butanamide|imidazolidinedione|oxadiazolidine|ylcarbonyl|acetohydrazide|cyclopenten|butyl|isoquinoline|phosphide|butyn|butanamido|methylthiophen|naphthoylamino|alpha|dichlorophenylamino|yloxytris|diaminophosphinyl|oxypropyl|monoethyl|azaphthalide|methylphosphonic|chloroquinolin|dihydroisoxazole|methylheptyl|chlorothieno|hpyrazol|oxoethylamino|formyloxy|methanesulfonylmethyl|propyloxycarbonyl|phenylphenoxy|bromobiphenyl|diethoxycarbonyl|pentane|ylpyrazolo|monochloride|benzotriazole|piperazinone|pyrrolidone|decahydroisoquinoline|methylimino|carboximidamide|dihydro|bismaleimide|methylindan|tetrahydrophthalic|iodomethyl|tetra|butylsilyl|hydroxybenzophenone|cyclohexa|dimethylindol|propanediamine|dimethylpropyl|triethylene|benzthiazol|carbamoylethyl|perchlorate|dimethylbicyclo|phenylisoxazole|cyano|phenanthrene|propyne|ethoxyethoxy|tribromide|tetraphenylborate|fluorbenzyl|trimethylcyclohex|ethoxycarbonyl|hydroxypropylamino|nitrobenzamide|oxoindolin|hydroxybutylidene|hydroximino|glucopyranos|indolinon|methylenedioxyphenyl|nitrobenzamido|ethyloxy|dioate|octen|nonenyl|styryl|cyclohexane|allylsulfanyl|thienylacetyl|pentaerythrityl|propylbenzyl|thiophenesulfonamide|methylmorphinan|diazol|cyclobutanol|thiosulfate|thiadiazolidine|ethylenediamine|carboxy|cyclopropylcarbamoyl|diethylbenzamide|hydroxyformamide|dimethylphenoxymethyl|methylthiophenyl|butyryl|aminohexyl|methoxypentyl|valyl|sulphonyl|phenylspiro|acetoxyethoxy|cadmium|diazenyl|benzylaminomethyl|himidazol|triyl|indolecarboxylate|trichlor|dimethoxy|buta|phenylsulfonyl|piperidin|chloropyridin|benzooxazole|pyrazinecarboxamide|methylinden|nitrophenylazo|hydroxypentanoic|octahydrobenzo|aminoisoquinolin|trichlorosilyl|dicyclopentyl|chlorothien|silanyloxy|iodophenoxy|methyl|biphenylcarboxylic|phenylbenzoyl|chlorothiophen|toluidine|dimethylphenol|ethylbenzamide|azahexane|hydroxypropanoic|benzenesulphonamide|toluidino|propylbiphenyl|benzenedicarboxamide|thiazoline|acetamidomethyl|diene|propanediol|sulfamoyl|phenylpropanamide|sulfon|pentamethylcyclopentadienyl|phenylbutanoate|methylpyrazine|aceto|pyridinecarbonitrile|dihydrofuran|chlorothiophene|oxoacetyl|hydroxyphenylacetamido|hydrazinocarbonyl|diethylphosphonomethyl|acridine|dimethylformamide|methylethoxy|dithiole|methoxyindol|phenylcarbamoyloxy|carboxybiphenyl|difluoride|benzyloxyl|aminobenzyl|phenylbutyl|bromoacetyl|thioxanthen|butanedione|diazide|ethylpiperazine|tetrahydrophthalimide|cyclohexyloxy|diazocin|pentenyl|pent|hydroxyquinolin|boronic|carboxymethyl|dimethylcarbamoylmethyl|butanamine|quinolinylmethoxy|piperidinylamino|glutamic|biphenylcarboxamide|phosphine|triphenylmethane|phenylbut|tetrafluoroethane|containing|octanoate|naphthylmethyl|phenylmethyl|enal|heptyl|dimethylpropanoic|phosphino|benzylphenyl|methylurea|indanone|dimethylamide|octenyl|tetracyclo|dihydropyridin|tetrahydroquinoxalin|butyramid|trans|hydroxyethylsulfonyl|bromophenylamino|methoxyphenoxymethyl|propionate|chloroallyl|diisocyanate|diethoxyphenyl|phenylcyclopropyl|aminooxy|methylsulphonyl|dimethylbenzoic|difluorobenzenesulfonamide|diisocyanato|dioxide|acrolein|europium|thiophenecarboximidamide|iodobenzoyl|methylpiperidin|picolinate|dioxido|hexenoate|chloroformate|sulphonylamino|cyanomethyl|carboxyhexyl|aminophenylthio|dichlorobenzyl|cyclopentanecarbonyl|dioctyl|aminoindan|aminocyclohexane|tartrate|octyne|hcl|ethanethiol|propionamidine|benzofuryl|methanol|sulfenyl|disiloxanyl|monosodium|pyrazinone|benzoxazolyl|methylsulfonyloxy|isopropenyl|cyclobutanecarbonyl|hydroxypropyl|polydimethylsiloxane|dihydroxymethyl|hydro|indolinyl|endo|difluorobenzoic|butyrate|sulfur|ylpropoxy|phosphorodithioate|azapurine|methylsulfamoyl|cycloheptanol|acetylpiperazin|isoquinolyl|dienylidene|trifluoromethylpyridin|chloropyrazine|piperazine|benzenemethanol|ethyloxycarbonyl|methylen|propylcyclopentadienyl|piperazino|silver|isothiocyanate|benzenesulfonyloxy|dimethylaminocarbonylphenoxy|acrylat|thiazolium|isothiocyanato|hex|dienoic|dimethylsiloxane|chinolinyl|dimethoxybenzylidene|valeryl|dihydroisoindol|bromphenyl|trimethylsiloxy|oxyphenyl|dihydronaphthalene|methylpyrazin|indanol|indanon|methylacrylate|diphenylpropyl|ethanesulfonylamino|cysteine|perfluoroethyl|dihydroindole|dimethylamin|dimethylethylamino|dibenzylamino|binaphthyl|oleyl|carbamoylphenyl|methylindol|enecarboxylic|ethanoylamino|trimethacrylate|phosphite|triethylsilyloxy|pyridinamine|dimethoxyphenethyl|dihydroquinoxalin|piperidinehydrochloride|isopropyloxy|dimethylphenyl|ethylhexanoate|fluorophenylsulfonyl|methylsulfonylphenyl|dinitrophenyl|ylmethanesulfonyl|bromopyridin|chlormethyl|epsilon|cyclopent|lauric|propanediyl|quinolinecarboxamide|diphenylether|methylphenylthio|thienyl|azidoethyl|seryl|dihydroquinazoline|ethoxy|oxothieno|aminocarbonyl|cytosine|quinoxalin|ethan|benzaldehyde|butoxysilane|galactopyranosyloxy|disulfonate|isocyanatomethyl|dihydropyrrole|acetylphenyl|ylacetamide|dibenzo|benzylamide|dihydropyrrolo|propylcarbamate|aminopyrazole|morpholide|phenylacetylamino|hexadienyl|gamma|cyclobutene|thiazolecarboxamide|hydroxyamide|aminopyrazolo|lauroyl|diethoxyethyl|ylmethoxy|phenylsulfonium|based|cyclohexylpropyl|carboxaldehyde|piperonyl|beryllium|benzamidin|dimethoxytrityl|phenethylcarbamoyl|acetylthio|cyclopentyl|lambda|fluorescein|cyclopropanecarboxylic|pentakis|dimethylamine|aminobenzoate|azide|sulphonate|tetramethylpiperidine|dioxime|dihydrocarbostyril|nitrophenol|dimethylamino|azido|pentacene|dipalmitoyl|methylpyrazol|carbazol|ethylaniline|thiophosphate|phenylindenyl|onehydrochloride|phenylethoxy|ethoxycarbonylphenyl|fluorophenylthio|cinnamic|phenylpentanoic|ethylanilino|chlorobenzonitrile|ethylhexyloxy|hydroxybenzylidene|yne|butanenitrile|dioxine|piperidyl|dioxoperhydro|isobutoxybenzyl|dioxino|acrylic|manganese|cyanoguanidine|lactone|tridecane|ethen|phenylpropanoic|ether|trifluoromethylphenoxymethyl|trichloroethoxycarbonyl|methylbenzenesulfonate|porphyrin|dimethylphenoxy|dithiobis|tetrahydropyrazolo|phenylbutanoic|mercaptomethyl|fluoropropyl|glycin|benzamid|disulfonic|testosterone|yloxymethyl|aminobenzoic|cyclobutylpiperidin|phenoxide|hydroxypropanoyl|oxide|carboxylicacid|cyanoethoxy|thioxoimidazolidin|tris|heptynyl|phosphinoyl|trifluorobenzyl|oxido|cyclohex|methoxybenzoylamino|tetrafluoro|thiazolin|mercapto|octyl|furfuryl|pyridoxal|piperidinyloxy|methylcyclohexylmethyl|acetal|pyridinecarbonyl|methylol|methylamide|benzoxazepin|hexafluoro|tetradecyl|benzoxazepine|acetat|dihydroimidazole|chinazolin|enol|benzylpyrrolidin|methylalanine|pyrrolidinylmethoxy|naphthylamide|hbenzimidazol|difluorophenoxy|yloxyl|oxoisobenzofuran|tetramethoxy|butyldiphenylsilyloxymethyl|toluenesulfonylamino|dihydrothiophene|indanyl|acetamidehydrochloride|fluoropyrid|flouro|dimethylcyclohexane|methylbenzothiazol|difluorobenzonitrile|methylbutoxy|acidbenzylester|galactosyl|succinamide|pyridinylmethyl|phenylimidazole|diphenyliodonium|naphthyloxy|acetylsulfanyl|enoic|ethylhexyl|vinyloxy|glutamyl|phosphane|methylcarbamoyloxy|benzothiadiazepine|quinolylmethoxy|thiodipropionate|bromopyrimidin|aminopentyl|dimethylpropanoyl|acetaldehyde|benzoxazine|butandiol|alaninyl|phenylethan|bromobenzoate|isochroman|phenylpyridin|phenylthiophene|methylpropane|butyraldehyde|methoxyamino|lactose|fluorophenol|trimethoxysilane|butanoyloxy|dihydrodibenz|octamethyl|sulfobutyl|acetate|ribofuranosyl|doped|hydroxyphenyl|guanidinophenyl|ethylendiamin|dihydrate|butoxycarbonylaminomethyl|propanedioic|dimethylbenzoyl|dinitro|dienoate|perfluorooctane|phenyliodonium|magnesium|benzenecarboximidamide|oxyethyl|hydroxyaniline|chlorobenzotriazole|benzisoselenazol|prolin|dimethylpropoxycarbonyloxy|methylindole|iron|hydroxyanilino|thiophenecarbonyl|glycol|phenylsulfanyl|epoxypropane|benzylamine|morpholine|pyrrolidinium|benzylamino|aminehydrochloride|hydroiodide|methylglycyl|pyridazinon|diaminomethylen|cyclopentylidene|morpholino|triazolin|ethoxycarbonylamino|cytidine|hydroxyamino|indolylmethyl|octylcyclohexyl|methoxystyryl|difluorobenzoyl|dimethylsilylenebis|piperidinylmethyl|benzidine|tetrachloro|dimethylaminobenzyl|monostearate|benzo|norbornene|dichloropyrimidin|dodecylbenzene|nitrobenzylamino|phenazine|azine|diethyl|ethylsulphonyl|nitrophenyl|hexachloro|bromovinyl|ethylacetoacetate|isochromen|azino|quinolinyloxy|phenyloxazole|hexahydropyrido|trimethoxybenzamide|methacryl|acidmethylester|azobenzene|ethylpiperidin|sulfenamide|cyclohepten|epoxypropyl|enone|methoxypyridine|phenoxyphenoxy|carboxycarbonyl|acryloxypropyl|cyclopentyloxyphenyl|dioxaphosphorinane|acetanilide|methanesulfonamidoethyl|acetic|piperidylidene|triisopropyl|methyloxy|cinnamoylglycyl|trifluoromethanesulfonate|pivaloyloxy|oxime|benzodioxole|acrylamide|phosphate|hydroxyhydrocinnamate|ethylbenzene|epoxycyclohexylmethyl|trifluoromethylphenylamino|methoxymethyl|dodecanoic|acrylamido|acenaphthylene|pentadienamide|ethoxypropanoate|propanedione|ethylsulfamoyl|benz|arabinofuranosyl|tricarboxylic|benzylester|butyldimethylsiloxy|diaminocyclohexane|phenylpiperidine|ethoxyethyl|ethylimino|thymidine|toluic|heptenoic|phenylpiperidino|lauryl|xanthine|ethylpyridine|hexafluoroarsenate|carbazate|phenylurea|benzenesulphonic|aminophenoxy|cyclohexylphenyl|methylamine|cyanocyclopropyl|imidazolin|heptene|enyl|methylamino|nicotinate|naphthylamine|methylsulphanyl|isopropylpiperazin|sulfone|oxazolidone|difluormethyl|naphthylamino|ylurea|hydrazone|butoxy|methylenebis|semicarbazone|inositol|cyanurate|oxybutyramide|phenylpyrrolo|lithio|hydrazono|hbenzo|hbenzimidazole|picoline|valinyl|dihydrothieno|isocyanuric|phenylacetic|methoxycyclohexyl|pyrrolidinecarboxylic|trifluoromethylpyrimidin|triallyl|thioether|isopropylcarbamoyl|propoxycarbonyl|pyridinecarboxaldehyde|thiazine|tetrahydroisoquinolin|phenanthroline|methylpregna|palmitic|hydrazonomethyl|dimethylaminobutyl|azidomethyl|cyanamide|cyanoacrylate|thiazino|triaza|dicarboxy|butoxyphenyl|phenoxybenzoyl|methylsulfonylbenzoyl|valeronitrile|ethylimidazo|decanol|methoxycarbonyloxy|carbobenzyloxy|fluorophenyl|benzothiopyrano|diphenylethyl|taurine|pentenyloxy|cyclohexylcarbamate|geranyloxy|propylphenylimino|sarcosine|dihydroxybenzene|cinnamyl|cysteinyl|dioxopyrrolidin|methallyl|indole|dioxane|nitrosourea|dimethoxyphenylamino|iodide|perylene|aminonicotinic|butylthio|beta|amidinopropane|indolo|methylester|meso|butylthiazol|benzimidazolyl|phenylpropanoyl|glycyl|phenylbutoxy|morpholinopyridin|uronium|meta|dimethylsilyl|phenylbutanoyl|chloroacetamide|indazolyl|dimethylbutan|tetraene|dioxoethyl|phenylbenzo|acetyloxy|meth|phospha|chloroacetamido|pyridazinyl|aminobenzoyl|ribose|nitro|methylbutyrate|methanesulfonylaminophenyl|propylbenzamide|fluoropyridine|phospho|hydroxybenzene|pyridylamine|derived|octadecenyl|thiazolyl|ylbutyl|toluol|pyridinol|pyrazin|chlorophenylsulfonyl|pyridylamino|heptylamino|biphenylcarboxylate|diazobuta|methylpropanenitrile|pentadien|benzylcarbamate|ethenesulfonic|picolyl|malate|phosphonomethyl|iridium|benzodioxepin|oxalate|benzooxazol|cyclohexylmethylamino|cyanovinyl|dinitrobenzoyl|toluenesulfonyloxy|dibutyltin|sydnonimine|nitrone|ylmethylsulfanyl|isobutoxy|oxoazetidine|dimethylcyclopentyl|phenylindole|tetrafluoropropoxy|acryloylamino|hydrogen|phenylbenzofuran|biadamantane|pyruvate|phenethyl|hexafluoroantimonate|ethyl|piperazinehydrochloride|dionehydrochloride|tetrahydrothieno|undecane|difluorophenylthio|sulfonylamino|oxabicyclo|cerium|chlorophenylsulfonylamino|dimethylindenyl|isocyanatoethyl|enoyl|hydroxyacetamide|isobutyrate|propanediylbis|group|oxetane|aminocyclohexylamino|retinoic|thiomorpholinyl|dodecylthio|oxobutan|ethanaminium|methionyl|dichloroindol|undecyloxy|iodoacetyl|diazepan|methoxyethoxy|isoxazole|diaminopropane|propoxide|cycloheptylmethyl|isoxazolo|octacos|chromanone|citrate|isoindole|butoxycarbonylmethyl|acidtert|prolyl|hydroxyethyloxy|dihydroquinoxaline|undeca|hydroxyphenylamino|triacrylate|isopropoxyphenyl|azepine|carbonitril|monomethyl|chloroethane|hafniumdichloride|azepino|methoxyl|benzenesulfonyloxyphenyl|methanesulphonamide|ylcarbonylamino|cyclobutyloxy|trimethoxyphenyl|ethoxymethoxy|menthane|furanon|triazolyl|dibenzothiophen|hydroxypyrrolidin|pregna|propionicacid|pyrazol|adamantane|dodecane|neodymium|trisiloxane|pentenoic|benzoquinon|dimethylacetamide|nitrobenzenesulfonamide|dihydrazide|sulpho|cyanobenzoic|methylenedioxybenzyl|nitroso|ylcarboxamide|thallium|methoxymethoxyphenyl|propoxyimino|tartaric|methansulfonamid|propenoic|ylcarboxamido|tetrahydrate|decanyl|isostearyl|histidine|dioxaborinan|hydroxyphenylmethyl|piperidinecarboxylic|oxopropoxy|tetrahydrospiro|chinolin|alcohol|fluorenyl|quinolinecarboxylate|pentalen|dimethylcyclohex|sucrose|butenyloxy|naphthalenecarboxamide|butanal|butyloxycarbonyl|hydroxybutan|nonylcyclohexyl|dichlorobis|iodine|dioic|acetyl|phenylenebis|dimethylpyridin|pyrazolone|methylureido|methylphenethyl|methylthieno|oxoquinolin|butanoate|diphenylsulfone|propenamide|diethanolamine|dimethylbutanoyloxy|triisopropylsilyl|estrone|octadecyloxy|triethoxysilylpropyl|dodecanoyl|octa|hydroxypropanoate|pyridinylmethoxy|alanine|hydroxypyrimidine|methylcyclohex|nickel|piperidineacetic|thiazolecarboxylate|dihydroindol|methylmercapto|hydroxyiminomethyl|dibenz|biphenyloxy|heptadecane|tetrone|dihydrofuro|toluyl|heptanoic|furamide|pyridinyl|methylisoxazole|galactopyranosyl|dichloromethane|benzenesulphonyl|difluoropyridin|imidazolyl|pentacyclo|pentanenitrile|isoquinolinecarboxamide|acetamidoethyl|phenylethyl|pyrazinyl|tetraethylammonium|propargyloxyphenyl|propanoyloxy|thiazepin|aminocarbonylamino|dienyl|methoxypyrazin|cycloheptyl|methacrylat|dichloroisonicotinoyl|diazepin|acetylaminophenyl|trichlorosilane|propane|phenylpyridazine|phenylacetyl|propenone|trifluoromethoxy|propionitrile|glyoxylate|ylpropan|trifluoropropanamide|methoxyacetamide|dicarboxylic|methylsilyl|methylbenzoylamino|methanesulfonate|dimethylcyclopropanecarboxylic|bearing|methoxyquinolin|decan|methylbutan|palmityl|thienylcarbonyl|dihydropyrimido|androst|nitrobenzyloxycarbonyl|sulphinyl|nonan|carbonylpent|butyldimethylsilyloxymethyl|galactopyranoside|furaldehyde|dione|ethylcyclopentadienyl|phenylbutanamide|dodecanoate|phenylsulfinyl|hexafluoropropoxy|poly|cyanoacetyl|ylcyclohexyl|dioxaspiro|pyrimidinone|aminomethyl|ine|benzyloxyphenoxy|ethanol|hydroxybutyrate|phenoxycarbonylamino|formal|ylpiperazin|trimethylquinoline|ylmethoxycarbonyl|monoacrylate|decanoate|methylbutanoate|heptanone|iod|hydroxybenzaldehyde|resorcinol|triethylammonium|furanyl|ion|ylboronic|aminopropyltrimethoxysilane|heptanamide|thioethyl|ethylamide|benzothiazin|rac|ylcarbamoyl|ethylen|cyclopropylamide|bisphosphonic|acetonitril|dimethylpyrrole|fluoro|cyanophenylamino|thiazolidinyl|pentanediol|silicate|oxoisoindolin|dimethylpyrrolo|malonamide|butynyloxy|methoxyquinazolin|isobutyramide|tribromophenyl|methylenephosphonic|decen|asparagine|mannose|cycloocta|nonen|cyclohexylmethylthio|chloroindol|phenoxypyridin|pivaloyl|dimethylcyclopentadienyl|trifluoromethylbenzenesulfonamide|chloropyridazin|piperidinium|tetrahydrochloride|cross|cyanophenoxy|borinic|valerate|dihydroxynaphthalene|diethoxyphosphoryl|dimethylprop|dichloromethyl|dimethylxanthine|methane|ureido|benzofuro|methylaminoethyl|nonadec|himidazo|thieno|methano|difluormethoxy|pentafluoropropyl|phenylacetamide|dioxotetrahydro|piperidinopropyl|alaninamide|phenylacetamido|guanine|dimethylaminosulfonyl|acrylamid|heptanoate|methacrylic|naphthalenecarboxylic|hexamethylene|isoquinolinecarboxylic|methylsulphinyl|diacetate|quinoxalinedione|phenoxybenzamide|carbamimidoylphenylamino|benzofur|dimethylspiro|methacryloyloxy|iso|methylxanthine|dimethylbenzyl|aminopyridin|ylthiomethyl|propanoylamino|malonic|phenylethanone|pinacol|isophthalonitrile|rel|benzenesulfonylmethyl|dithiolan|hydroxyl|methanamine|methylhex|mannitol|cyclohexylaminomethyl|fluorobenzylamino|butanesulfonate|sulphophenyl|phenylethenyl|nitrobenzaldehyde|sulfonium|piperazinecarboxylic|thiazepine|fluoroquinolin|glucosyl|propylcatecholate|homopiperazine|glucofuranose|benzeneboronic|thiazepino|methoxybutoxy|enyloxy|phenylcarbonyl|azepane|dimethoxypropane|crown|dimethylsilylene|quaterphenyl|octane|formylphenoxy|formamide|thiadiazolyl|dimethylindolin|acid|tetrahydronaphth|butylate|glucopyranosyloxy|phenylureido|cumene|dimethylhexane|ium|trideoxy|formamido|phenylthieno|trihydrochloride|benzothiazol|butanol|trifluorobutyl|hydrobromide|pentahydrate|thiopropyl|methylaniline|methylthioethyl|estradiol|phenylpyrazole|methylanilino|sebacate|chlorobenzylthio|pentanoic|quinazolinone|phenylpyrazolo|chloro|diammonium|phenylenediamine|cyanobenzoyl|rhodanine|carbamoyloxy|chloropropoxy|aminocaproic|bromobenzyloxy|dimethylbutyl|cyclo|pyridylmethylthio|dichlorobenzylamino|azocine|dioxa|propenoyl|dihydrogenphosphate|anthrachinon|propanoic|tricarboxylate|dioxo|nitrobenzol|benzylpiperidine|oxospiro|ethylpyridinium|thiepin|thienylacetamido|androstene|imidazolinium|propylamin|fluoroborate|flavone|dioxy|methoxyindole|propylpiperidin|pentadecyl|trifluoroacetylamino|fluoroaniline|propanamid|aminothiocarbonyl|fluoroanilino|benzylideneamino|bipyridinium|octadec|phenylpropionate|sulfamide|tetraisopropyl|piperidinocarbonyl|carbonylethyl|difluorobenzyloxy|difluor|didecyl|hydroxyamidino|butyldiphenylsilyloxy|aminoethoxy|diethylaminoethyl|phthalimidomethyl|azulen|methylthio|aminopyridine|dinonyl|diphenylpropane|butylaminoethyl|carboxanilide|methylbutyramide|methoxyspiro|chlorothiazol|indolehydrochloride|ethylamine|cyclopropylamine|atom|hexadiene|ethylamino|heptanoyl|benzenedicarboxylic|cyclopentadienyl|cyclopropylamino|thiophenol|butanediol|tbutyl|vinylphenyl|isopropylamide|epoxycyclohexyl|dichlorobenzyloxy|tosyloxy|methyluridine|vinylpyridine|oxobutyl|imidazole|methyloxycarbonyl|isobutoxymethyl|tetrahydrocyclopenta|phenylpiperazine|ethylphosphonic|imidazolo|nitrogen|tetrahydrophthalamic|thiosemicarbazide|quinolinylmethyl|ylcarbonyloxy|pentanone|pyridinylacetyl|butylazo|methylpentanoylamino|hexylamine|epoxycyclohexane|thiophene|dimethylpyrrol|oxobutyric|myristate|hexylamino|cyclopentene|naphthalene|dodecenyl|thiourea|hexadien|imidazol|chromium|propanone|pyridinium|tetrahydropyridine|dimethylethoxycarbonyl|tetrazolium|all|methylpentanoic|methanesulfonylamino|mesylate|trifluoromethyl|nitroimidazol|fluorouridine|butenoate|dibutylamino|tetrabromo|naphtho|methoxybutyl|methylchroman|sulfoethyl|ylpyrimidin|methylpropanoic|bromide|trimethylammonio|thiazolylmethyl|dioxopiperazin|allyloxycarbonylamino|tetramethylbutyl|trichlorophenyl|tungsten|butylcarbamate|ethylester|adipate|ynyloxy|ketone|propoxymethyl|butoxycarbonylamino|butyne|pregnene|hydroxybenzyl|pyrimidinylthio|nitrooxy|thiocarbonyl|penicillanic|dihydrothiazol|quinazoline|hydroxynaphthalen|phenoxypropyl|nitrobenzenesulfonyl|dimethylphenylsulfonyl|hydroxysuccinimide|diphenylmethane|azobis|benzofurancarboxamide|carbazolyl|pyrimidinecarboxylic|bromopyridine|benzothiazyl|ammoniumchloride|octadienyl|carboxymethoxy|hydroxybutyl|dihydroxydiphenyl|trifluoromethylbenzyloxy|thiazolidinedione|glyoxal|isohexyl|isopropylsulfonyl|hexahydro|nitroquinazoline|cyclohexanecarbonyl|phosphonic|hydroxyhexane|hydroxyphenylacetic|trifluoromethoxyphenyl|ethylphosphonate|phenylhydrazine|pentafluorophenyl|dioxalate|benzylhydroxyphosphinyl|methacrylate|trichloroethyl|terpyridine|trifluoromethanesulfonyloxy|piperidinophenyl|diazaspiro|dimethylaminostyryl|azetidin|methylspiro|imidazoline|propyloxyphenyl|sulfonylchloride|methylbenzene|methylsulfonamido|ylacetate|ethylbenzthiazoline|trimethylsilyloxy|methylimidazole|cyclopropanesulfonyl|vinylidene|methoxyacrylate|dibenzofuran|aminothiazole|nitrobenzyl|benzylthio|propiophenone|carbostyril|oxazoline|hexenoic|methoxyacetyl|dicarboximide|methylthiophene|bromopropoxy|naphthalenol|dipotassium|trifluorobenzothiazol|methylthiopyrimidin|diphenylhexane|cyclopenta|benzenesulfonamide|dihydropyridazin|thymine|hexahydrobenz|trifluoromethanesulfonic|phenylisoxazol|desoxy|methylhept|methylstyrene|diisopropylamino|tosyl|hexylcyclohexyl|fluorobenzene|chalcone|sulfonate|biscarbonate|dimethylaminomethyl|ethylacrylate|dipropyl|diazomethane|nonanoate|stilbene|cyclobutoxy|propanesulfonate|hexynyl|oxetan|ylpropyl|monohydrochloride|benzazepine|hydroquinone|phenylpyridazin|thiophenyl|diallylamino|isochinolin|cephalosporanic|fluor|aminonaphthalene|ylcarboxylate|methylbutyl|decyl|benzothiadiazine|fluorenylmethoxycarbonyl|diazepine|isopropylamine|anilide|enylmethyl|octafluoro|naphth|nonyl|diazepino|tetrahydrothiazolo|caproate|isopropylamino|carbothioamide|butyryloxy|oxazolin|tetradecane|azaprop|oxotetrahydrofuran|chromane|octadien|aminopropyltriethoxysilane|propylpyridine|dimethylpentyl|tolylsulfonyl|chlorophenylthio|sulfamoylbiphenyl|dichlorobenzoylamino|naphthalenecarboxylate|methylimidazo|isobutylcarbamoyl|trifluoromethylbenzimidazole|pentylcarbamoyl|hexadecane|quinolone|palmitoyl|acetamidine|naphthoic|methylhexahydro|dihydroxybenzoic|toluate|trimethylolpropane|tetraacetate|dimethylaminoethyl|benzeneacetate|alphas|formyl|triphenylphosphin|fluoride|hydroxycarbamimidoyl|pentanoyl|benzodioxan|morpholinopropyl|diethylenetriamine|mono|dichloroaniline|trimethylol|aminoacetic|fluro|heptenoate|adenosine|dichloroanilino|bromine|propanoyl|hydroxycarbamoyl|fluoroimidazo|propanal|thioxanthene|cyclopentylcarbonyl|pyrrolidine|fluorobenzyl|trifluoroethoxy|tertbutoxycarbonyl|sulfonamid|bromopyrid|diphenol|benzyloxypropyl|penam|heptylphenyl|hydroxybenzoic|pyrrolidino|divinyl|imidazoyl|dithiolane|imidazolium|phthalazinone|acetylaminoethyl|isoquinolinecarboxylate|pyridinehydrochloride|pentadienoic|leucyl|siloxy|oxathiolane|trichlorophenoxy|tripropyl|ethylpropyl|tetrahydrobenzo|tetrahydropyranyloxy|phthalocyanine|pyrrolizine|norvaline|azaindole|cyclopentyloxy|ethanamide|ylethyl|diethylphenyl|benzotriazine|aminocyclohexyl|dimethylpiperazin|benzophenon|phenoxybutyl|aza|yttrium|glucuronic|cyclopropyloxy|ylaminocarbonyl|methylenedioxyanilino|hexanediamine|azo|quinolinium|methoxycarbonylamino|carbamoylmethoxy|cyanopyridin|menthyl|dioxopyrrole|octenoic|ethoxypropanoic|dimethylcarbamoyl|oxohexanoate|dichlorobenzylthio|chromate|thiadiazolidin|cyclopentenyl|oleate|dicarboxyphenyl|isonicotinamide|prostenoic|diisobutyl|butoxycarbonylmethoxy|hexanamide|phenylpropoxy|quinone|penem|ethylphenoxy|naphthalenyl|imidazolylmethyl|hydroxypiperidine|tetraone|octadiene|methylpentanoyl|hydroxypiperidino|sulphoxide|hexadecyloxy|benzylpiperidin|sulfamic|phenoxyphenyl|hydroxyethylidene|ethylphenol|benzothiadiazin|methylethoxyimino|natrium|perfluorobutane|methylpropanoyl|phthalide|azaindol|delta|octadecadienoic|methoxypyrimidine|naphtyl|diethanol|naphthamide|diazatricyclo|aminoquinolin|tetrahydropyrazin|isovalerate|acetylphenoxy|ethylcarbodiimide|naphthoquinone|dimethyloctyl|butoxycarbonyl|phthalazine|aniline|carbolin|dimethylaminoethoxy|benzodioxin|ethoxyl|aminobutyl|phenylthiazol|methoxycarbonylphenyl|anilino|allyloxymethyl|secocholesta|methylcyclohexyl|isoleucyl|hexene|butylsulfonyl|furanylmethyl|isopropoxybenzyl|triphenylphosphonium|fluoropyrimidin|betaine|thioacetamide|chlorophenylmethyl|cyclohexanecarboxamide|cephalosporanate|dihydropyrazolo|trimethoxybenzyl|carnitine|yloxycarbonyl|oxidopyridin|fluoroindol|hydroxypentyl|tetrazole|aminopyrrolidine|dimethoxysilane|benzenethiol|pyranyloxy|carboxamidehydrochloride|tetrazolo|benzoylamino|diethylaminopropyl|fluorine|phenylthio|ethinyl|pteridinone|nitroimidazole|isoquinolinyl|isobutylphenyl|cyclohexylidene|ethylbenzoyl|ethanedioate|cyclohexenyl|chloropyrimidin|tetrahydroquinolin|dioxothiazolidin|ylthio|phenylmethoxy|dimethano|dicarboethoxy|triamide|nitrothiophene|diaminobenzene|phosphinomethyl|aziridinyl|diphenyl|hexanoic|dichlorobenzene|ylphenyl|tributylstannyl|oxooctyl|octahydro|sec|trifluoromethanesulfonyl|isoxazoline|oxetanyl|cyclobutylamino|fluorocyclohexyl|hexafluorophosphate|triiodo|hydroxyphenoxy|tetrafluorophenyl|indoline|oxothiazolidin|benzothiadiazol|uridine|tetrahydroazepino|nicotinic|ethanamine|vinyl|pyrimidinyloxy|oxiranylmethyl|alanylamino|amylphenyl|cobalt|cyclopropanamine|diisocyanat|chlorophenyloxy|dimethylimidazol|hydroxylamine|bromothien|dimethylsilanediyl|triazole|pyrenyl|azetidinecarboxamide|benzodioxol|nitrosothio|triazolo|dienyloxy|diazepane|oxazolyl|hydroxyphenylpropionyl|chromanol|sulfonic|isoindol|methylthiomethyl|benzeneacetic|bipyrimidinyl|morpholinophenyl|bromobenzenesulfonyl|hydroxypyrimidin|dimethylbenzo|oxobutyrate|deca|hydroxybenzofuran|phen|pyromellitic|triethoxysilane|oxocyclohexyl|silyloxy|nitrobenzo|propanol|phenylpiperazin|piperidinamine|endblocked|naphthoxy|methylthiourea|carboxybutyl|trifluoroacetic|pyrene|methylimidazol|chroman|pyrrolizin|naphthol|fluoroquinoline|heptenyl|cyclopropylmethoxy|phosphorane|tertbutyl|trifluoromethylthio|benzenesulfonic|benzyloxyphenyl|naphthoyl|benzazocine|dicarbonyl|tetraoxa|dihydroxybenzoyl|salicylaldehyde|methylpropylamino|methylenedioxy|naphthalenesulfonamide|butylcyclohexyl|heptafluoropropyl|phenylcarbamoyl|isopropylsulfanyl|cyclotetrasiloxane|phenylglycine|dimethylurea|dodecylphenyl|hexanone|propylen|triphenyl|cyclobutanecarboxamide|ethylphenyl|isobornyl|hydroxyoctyl|aminoacetyl|tetramine|phenylene|morpholinopropoxy|ylene|isoindolinone|aminopyrimidin|dipropionate|hpyrido|iodosyl|chloroquinoline|difluoromethoxyphenyl|oxoazepan|dioxolan|oxazepan|trityl|thiabicyclo|benzothiepine|hydroxybenzoyl|piperazinecarboxamide|methylisothiazol|carbamoyloxymethyl|biguanide|dimethylpyrimidin|sulfamate|biscarbamate|bis|tyrosine|dioctadecyl|methyloctahydro|ylmethoxymethyl|hydroxyproline|trimethylene|trifluorobutoxy|dibenzoate|isothiourea|dimethylaminophthalide|methylpent|oxobenzo|dimethylbut|methanesulphonate|oxopiperidin|quinoxaline|benzylpiperazine|isopropoxyphenoxy|methoxyaniline|methylnicotinic|benzene|disulphide|hydroxycyclopentyl|isoquinolinesulfonamide|nitropyrimidine|docosyl|chromen|hexamethylenebis|methoxyanilino|benzenesulphonylamino|benzisoxazol|dichlorophenylthio|carbaldehyd|dianhydride|bromothiophen|aldehyde|phenanthro|methylbutoxycarbonyloxy|cholest|indolium|penta|methylether|methylazetidin|diyne|mandelic|trimethylsilane|acetamide|dichloroacetyl|antimony|anthra|dibromophenoxy|acetamido|diethoxymethyl|octanoic|methoxybenzo|styrene|sulphone|phenethyloxy|maleate|triamine|hydroxyquinoline|methoxybenzyloxycarbonyl|htetrazol|azatricyclo|carbon|prostanoic|ylethanol|triamino|methyloctyl|dioxolen|dihydroisoxazol|deoxycytidine|ylpyrrolidin|isosorbide|methylphenylsulfonamido|phenylvinyl|germanium|hexahydroazepino|dimethoxybenzylamino|monochloro|quinolinamine|methanesulfonyloxyphenyl|phenylmethylamino|toluenesulfonamide|dibromide|cyclopropylphenyl|lysine|sulfamyl|bicarbonate|ethane|hafniumdichlorid|hafnium|cyclopentadiene|tetrahydrofuranyl|dimethylimidazolium|ethano|piperazineacetamide|hydroxybenzo|phenylendiamin|boc|imidazo|keton|aminoquinoline|trimethylpropyl|methoxynaphth|diazonium|galactoside|methoxypyrimidin|oxaborol|diethylaminophenyl|brommethyl|sulfide|isoxazolin|oxymethyl|abeta|thione|hydrazide|semicarbazide|dimethylquinoline|racemic|benzoicacid|carboethoxy|hpyrazolo|mesityl|methoxybenzylidene|glucamine|benzoxadiazole|trifluoroethyl|trihydroxy|carbamoylmethyl|tetrafluoroborat|ylpyrimidine|propylidene|butadien|naphthyl|oxazepin|cyclohexanedicarboxylic|dicyanomethylene|oxopropan|pyrophosphate|benzolsulfonyl|malonate|glycinate|phenylbutyric|gluconate|butanediamide|propionaldehyde|fluorane|dimethylethyl|dithiane|dimethylpropoxy|methylbicyclo|methylnicotinamide|methylsulfonylmethyl|azulenyl|nicotinonitrile|methylacetamide|aminothieno|diaminodiphenyl|indolizin|pentanal|phenylethane|methoxychroman|cyclohexanedione|nitroethyl|methylacetamido|trifluoroacetate|benzylaminocarbonyl|hydroxide|isopropoxyethyl|camphor|methoxypropenoate|hydroxyphenylsulfonyl|dimethoxyphenyl|tetrahydronaphthalene|hexanoyl|oxoacetate|methylhydroxyphosphinyl|dihydrospiro|butoxide|succinic|methylbenzo|dihydropyrimidin|dicarboxamide|trifluoromethanesulfonylphenyl|amid|dihydropurin|decylphenyl|azabenzotriazol|hydroxybenzylamino|sila|sulfosuccinimidyl|ylidenemethyl|dimethoxybenzene|silicon|amin|butenamide|propylcyclohexane|methoxynaphthalen|trioxabicyclo|carboxypiperidin|sulfonyloxy|dimethylcyclohexyl|methylquinazolin|picolinic|dimethylmorpholin|chlorosilane|dimethylthiazol|ethylbutyl|methylsulphonylphenyl|naphthalenyloxy|benzanilide|benzenepropanoic|lithium|phenylpyridine|benzothienyl|methoxymethylphenyl|dimethylheptyl|butylnitrone|imidazolidinylidene|phenylpyrimidin|methyloxime|piperidyloxy|laurate|but|galactose|quinolinecarbonitrile|monocarbonate|tetrahydroquinazoline|oxoethyl|methoxybenzene|dihydrooxazol|diisopropylphenyl|acetamino|sulfonyl|cholesterol|methylphenoxymethyl|vinylbenzyl|nitride|labeled|secopregna|carboline|methanesulfonyloxy|ethoxycarbonylmethyl|benzoxazin|decane|methoxyquinoline|methylbutane|dibromo|ethylpyridin|butenoic|ethanesulfonamide|indoloylguanidine|tetrahydrothiophene|trifluoroacetyl|benzothiazolecarboxamide|deoxythymidine|methylpropanamide|dimethoxide|butylsulfanyl|propylcyclohexyl|tetramethyluronium|butylammonium|isopropoxycarbonylamino|propylbenzene|methylsulfate|benzenesulfonyl|ethylamin|fluoropyrrolidine|butyltin|diethylaminofluoran|syn|methoxyethyl|biphenylyl|hydroxypropionic|methylindolizin|phenacyl|dithiocarbamate|ylmethanone|chloropropyl|difluorocyclohexyl|benzenepropanamide|carboxamidine|diaminopropionic|toluenesulphonate|anthracene|ylmethanol|trimethylammonium|oxyacetate|benzodioxine|phosphinate|aspartic|isothiazolone|methylbenzylidene|difluoromethoxy|isopropylidenebis|oxazolidine|dimethylanilinium|hydrazine|methylpentanoate|tetralone|benzyloxycarbonyl|dihydroquinoline|thioxo|hydrazino|ethoxycarbonylethyl|nonanol|hydroxyazetidin|coumarin|dimethylaminocarbonyl|methylbenzoic|hydroxyethyl|benzoyloxymethyl|diphenylsilacyclopentadiene|tetrahydrofuranyloxyphenyl|aminopropionic|epoxy|fluorobenzonitrile|methylcyclopropyl|difluorophenylamino|stearyl|oxoquinazolin|naphthalenedisulfonic|disulfo|butylpiperidin|hexahydropyrazino|tetrahydroisochinolin|cyclopentylamine|chlorobenzenesulfonyl|butanediamine|dimethylpropionic|azetidinyl|phenylsulphonylamino|trifluoromethoxybenzyl|fluorouracil|cyclopentylamino|guanidinium|phenylquinoline|androsta|pentylene|prolinate|benzoxazol|hydroxymethyl|dipyrido|dibromopropyl|dibromovinyl|oxadiazole|guanidinomethyl|octanoyl|piperidylpyrrolo|dimethylethoxy|pentylsulfonyl|oxadiazolo|lead|cyclopentylmethyl|fluorobenzoic|acetylthiomethyl|benzyloxyethyl|hydroxypropionate|methylhexanoic|chlorophenol|dichlor|butylsulfamoyl|propylene|hydroxypropyloxy|pyridinecarboxamide|phenylthiazole|aminopropoxy|tantalum|isobutyrylamino|hydroxypyridin|perfluoro|trimethylphenyl|trifluoromethylpyrimidine|serine|octamethyloctahydrodibenzofluorenyl|aminobenzo|pyridazin|methylindoline|cycloheptane|methoxyprop|pyrrolidinecarboxamide|nitrile|propargyloxy|dimethylsilyloxy|chlorobutyl|histidyl|cyclobut|nitrilo|isopropylthio|diisopropyl|diphenylmethyl|trifluormethylphenyl|cyclohexanecarboxylate|phthalate|tetrafluoroborate|phenothiazine|secbutyl|homopiperazinyl|olide|methylethyl|dihydrodibenzo|nitromethyl|butane|butylphosphine|ethanesulfonate|proprionate|isoxazolyl|phenylethylamine|sulfite|caprylate|phenylalaninate|butylphosphino|nitroanilide|cholesteryl|pentanol|phenylethylamino|formylamino|ergoline|bromid|peroxydicarbonate|aminothiazol|barium|butylbiphenyl|hydroxylphenyl|oxopentanoate|methylhydrazino|phosphinic|cas|ditertiary|pentamethylene|pyridyloxy|trimethoxybenzoyl|octylamine|tetraoxaspiro|dioxidotetrahydro|methionine|octahydrocyclopenta|cyclohexylmethoxy|carboxamide|phenylaminomethyl|isoindoline|benzenesulfonate|octanol|nonane|octylamino|enoate|azaisobenzofuran|hexadecylamino|phenylpent|carboxamido|dichloromethylene|glucoside|amidinoaminooxy|methylaminopropyl|enamide|adamant|propyllithium|cbz|isoindolin|cinnoline|bornyl|sulfonyl|silver|palladium)"};
    // String pattern = StringUtils.join(patternPieces, "");
    StringBuffer patternBuff = new StringBuffer();
    patternBuff.append(patternPieces[0]);
    patternBuff.append(patternPieces[1]);
    String pattern = patternBuff.toString();
    int flags = Pattern.CASE_INSENSITIVE;

    // Nine spaces...
    String target = "Propanaminium";

    int[] expectedStarts = {0};
    int[] expectedEnds = {13};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * Test to verify behavior corrected via RTC 64107. Ensure that the lookahead specification on an
   * optional atom quantification works as desired
   * 
   * Testing absolute quantification - for example: \d{n} where n > 10
   * 
   * @throws Exception
   */
  @Test
  public void lookaheadAbsoluteQuantificationTest() throws Exception {
    try {

      String target = "PT121234567890";
      String pattern = "(PT)\\d{2}(\\d{10}| \\d{4} \\d{4} \\d{4} \\d{4} \\d{4} \\d)";

      genericRegexTest(pattern, target, new int[] {0}, new int[] {14});

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(
          "Didn't expect to encounter an exception when parsing regular expression. Test fails.");
    }
  }

  /**
   * Test to verify behavior corrected via RTC 64107. Ensure that the lookahead specification on an
   * optional atom quantification works as desired
   * 
   * Testing range quantification - for example: \d{m,n} where m > 10 n > 10
   * 
   * @throws Exception
   */
  @Test
  public void lookaheadRangeQuantificationTest() throws Exception {
    try {

      String target = "PT121234567890123";
      String pattern = "(PT)\\d{2}(\\d{10,15}| \\d{4} \\d{4} \\d{4} \\d{4} \\d{4} \\d)";

      genericRegexTest(pattern, target, new int[] {0}, new int[] {17});

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(
          "Didn't expect to encounter an exception when parsing regular expression. Test fails.");
    }
  }

  /**
   * A generic test of running SimpleRegex in multi-regex mode
   * 
   * @param patterns regular expressions to evaluate
   * @param flags flags to pass for each of the patterns
   * @param target target string to perform matching over
   * @param expectedStarts expected offsets of matches for each regular expression, or null to skip
   *        checking these offsets
   * @param expectedEnds expected end offsets of matches for each regular expression
   */
  private void genericMultiRegexTest(String[] patterns, int[] flags, String target,
      int[][] expectedStarts, int[][] expectedEnds) throws Exception {

    SimpleRegex mre = new SimpleRegex(patterns, flags);

    JavaRegex[] jre = new JavaRegex[patterns.length];
    for (int i = 0; i < patterns.length; i++) {
      jre[i] = new JavaRegex(patterns[i], flags[i]);
    }

    // System.err.printf("Parse tree is:\n");
    // re.dump(System.err, 1);

    SimpleRegexMatcher m = (SimpleRegexMatcher) mre.matcher(target);

    RegexMatcher[] jm = new RegexMatcher[patterns.length];
    for (int i = 0; i < patterns.length; i++) {
      jm[i] = jre[i].matcher(target);
    }

    System.err.printf("Matching over: '%s'\n", escapeNonPrinting(target));

    // Get all the matches from the multi-regex matcher in one pass, pulling
    // out the corresponding matches from the single-regex matchers.
    System.err.printf("Matches are:\n");

    // The multi-regex matcher gives us the match lengths at the current
    // position for all regexes that match there. It's up to us to remember
    // the last position that we matched each regex at.
    int[] curOff = new int[patterns.length];
    Arrays.fill(curOff, 0);

    int[] nextMatchNum = new int[patterns.length];
    Arrays.fill(nextMatchNum, 0);

    for (int pos = 0; pos < target.length(); pos++) {
      // Find the lengths of all matches at the current position.
      m.region(pos, target.length());
      int[] matchLens = m.getAllMatches();

      for (int i = 0; i < patterns.length; i++) {
        if (matchLens[i] >= 0 && curOff[i] <= pos) {
          // Found a match at this position, and it doesn't overlap
          // with the last match we found for this sub-expression.
          int begin = pos;
          int end = pos + matchLens[i];
          System.err.printf("Regex %d: [%2d, %2d]: '%s'\n", i, begin, end,
              target.substring(begin, end));

          if (null != expectedStarts) {
            // Verify that this match is where we expected it to be.
            int matchIx = nextMatchNum[i];
            if (matchIx > expectedStarts[i].length) {
              throw new Exception(
                  String.format("Got %d matches for regex %d," + " but only expected %d",
                      matchIx + 1, i, expectedStarts[i].length));
            }

            int expectedBegin = expectedStarts[i][matchIx];
            int expectedEnd = expectedEnds[i][matchIx];

            if (expectedBegin != begin || expectedEnd != end) {
              throw new Exception(String.format(
                  "Expected match %d for regex %d " + "to be on [%d, %d], " + "but got [%d, %d]",
                  matchIx, i, expectedBegin, expectedEnd, begin, end));
            }
          }

          nextMatchNum[i]++;
        }
      }
    }

    // Check for missing matches.
    if (null != expectedStarts) {
      for (int i = 0; i < patterns.length; i++) {
        int matchIx = nextMatchNum[i];
        if (matchIx < expectedStarts[i].length) {
          int start = expectedStarts[i][matchIx];
          int end = expectedEnds[i][matchIx];
          throw new Exception(String.format("Missed a match of regex %d from %d to %d ('%s')", i,
              start, end, target.substring(start, end)));

        }
      }
    }

    //
    //
    // int count = 0;
    // while (m.find()) {
    // System.err.printf(" [%2d, %2d]: '%s'\n", m.start(), m.end(),
    // escapeNonPrinting(target.subSequence(m.start(), m.end())));
    //
    // // Make sure the Java engine returns the same matches.
    // assertTrue(jm.find());
    // assertEquals(jm.start(), m.start());
    // assertEquals(jm.end(), m.end());
    //
    // if (null != expectedStarts) {
    // assertEquals(expectedStarts[count], m.start());
    // assertEquals(expectedEnds[count], m.end());
    // }
    // count++;
    // }
    //
    // // Make sure that there aren't any additional matches that we missed.
    // if (jm.find()) {
    // char[] str = target.subSequence(jm.start(), jm.end()).toString()
    // .toCharArray();
    // System.err.printf("JavaRegex returned: %s\n", Arrays.toString(str));
    // throw new Exception(String
    // .format("JavaRegex returns additional"
    // + " match: [%d, %d]: '%s'", jm.start(), jm.end(),
    // escapeNonPrinting(target.subSequence(jm.start(), jm
    // .end()))));
    //
    // }
    //
    // if (null != expectedStarts) {
    // assertEquals(expectedStarts.length, count);
    // }
  }

  /**
   * Convenience version of {@link #genericRegexTest(String, int, String, int[], int[])} with
   * default flags.
   */
  private void genericRegexTest(String pattern, String target, int[] expectedStarts,
      int[] expectedEnds) throws Exception {
    genericRegexTest(pattern, 0x0, target, expectedStarts, expectedEnds);
  }

  /**
   * A generic test of a simple regex.
   * 
   * @throws ParseException
   */
  private void genericRegexTest(String pattern, int flags, String target, int[] expectedStarts,
      int[] expectedEnds) throws Exception {

    SimpleRegex re = new SimpleRegex(pattern, flags, true);
    JavaRegex jre = new JavaRegex(pattern, flags);

    System.err.printf("Orig pattern is: %s\n", pattern);
    System.err.printf("Parsed regex is: %s\n", re.getExpr());

    // The pattern should be able to print itself out verbatim.
    assertEquals(pattern, re.getExpr());

    // System.err.printf("Parse tree is:\n");
    // re.dump(System.err, 1);

    RegexMatcher m = re.matcher(target);
    RegexMatcher jm = jre.matcher(target);

    System.err.printf("Matching over: '%s'\n", escapeNonPrinting(target));
    System.err.printf("Matches with SimpleRegex are:\n");

    int count = 0;
    while (m.find()) {
      System.err.printf("   [%2d, %2d]: '%s'\n", m.start(), m.end(),
          escapeNonPrinting(target.subSequence(m.start(), m.end())));

      // Make sure the Java engine returns the same matches.
      assertTrue(jm.find());
      assertEquals(jm.start(), m.start());
      assertEquals(jm.end(), m.end());

      if (null != expectedStarts) {
        assertEquals(expectedStarts[count], m.start());
        assertEquals(expectedEnds[count], m.end());
      }
      count++;
    }

    // Make sure that there aren't any additional matches that we missed.
    if (jm.find()) {
      char[] str = target.subSequence(jm.start(), jm.end()).toString().toCharArray();
      System.err.printf("JavaRegex returned: %s\n", Arrays.toString(str));
      throw new Exception(String.format("JavaRegex returns additional" + " match: [%d, %d]: '%s'",
          jm.start(), jm.end(), escapeNonPrinting(target.subSequence(jm.start(), jm.end()))));

    }

    if (null != expectedStarts) {
      assertEquals(expectedStarts.length, count);
    }
  }

  // Escape non-printing characters in a string.
  private static String escapeNonPrinting(CharSequence in) {
    String s = in.toString();
    s = s.replace("\n", "\\n");
    s = s.replace("\r", "\\r");
    s = s.replace("\t", "\\t");
    return s;
  }

}
