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
package com.ibm.avatar.provenance;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Scanner;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Quad;
import com.ibm.avatar.algebra.datamodel.ScalarList;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Triple;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.function.scalar.AutoID;
import com.ibm.avatar.algebra.util.document.HtmlViz;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.aql.ColNameNode;
import com.ibm.avatar.aql.ConsolidateClauseNode;
import com.ibm.avatar.aql.DictExNode;
import com.ibm.avatar.aql.ExtractListNode;
import com.ibm.avatar.aql.ExtractNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.FromListItemSubqueryNode;
import com.ibm.avatar.aql.FromListNode;
import com.ibm.avatar.aql.MinusNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.SelectNode;
import com.ibm.avatar.aql.UnionAllNode;
import com.ibm.avatar.aql.ViewBodyNode;
import com.ibm.avatar.aql.WhereClauseNode;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.compiler.Compiler;
import com.ibm.avatar.aql.planner.Planner;
import com.ibm.avatar.logging.Log;

// import com.ibm.avatar.api.AOGRunner;

/**
 * Refines AQL queries based on labeled results. This class enumerates high level chagnes.
 */
public class AQLRefine {

  // TODO: Laura: configure Log4j properly

  /**
   * Entry point for refining AQL query.
   * 
   * @param inputAqlFile
   * @param outputAqlFile
   * @throws Exception
   */
  public static boolean DEBUG = false;

  public static long startTimeMS = 0;

  // This is used to counter the shift in positions in input text and label due to conversion.
  private static int POSITION_SHIFT = 0;

  // constants corresponding to different relationship between the positions of expected and actual
  // annotation spans
  private final static int BEFORE = 3;
  private final static int AFTER = 2;
  private final static int OVERLAP_BUT_NOT_EQUAL = 1;
  private final static int EQUAL = 0;

  private final static String BEGIN = "start";
  private final static String END = "end";
  private final static String ANNOTATION = "annotation";

  public static String[] REWRITE_MARKS = {"__Union", "__Base", "__Minus__", "__Subquery__",
      "__Consolidate__", "__Minus__", "__union_op"};

  public static final String AUTO_ID = "__auto__id";
  // the ending of source tuples of "select"
  private static final String SELECT_ID_END = "____id";
  private static final String UNION_ID_END = "__union_op__id";
  private static final String CONSOLIDATE_TARGET = "__consolidate__target";
  private static final String STMT_TYPE = "__stmt__type";
  @SuppressWarnings("unused")
  private static final String SUBQUERY_START = "__Subquery__";
  private static final String CONSOLIDATE_START = "__Consolidate__";
  // private static final String CONSOLIDATE_TARGET = "__consolidate__target";
  public static final String TUPLE_ID = "ID";
  public static final String TUPLE_TYPE = "Type";
  public static final String TUPLE_VALUE = "Tuple";
  public static final String TUPLE_OPERATION = "Operation";
  public static final String TUPLE_RULE = "Rule";

  // list of possible high-level changes
  public static final String CHANGE_REGEX = "changing regex";
  public static final String CHANGE_DICT = "changing dictionary";
  public static final String CHANGE_MINUS = "Minus";
  public static final String CHANGE_CONSOLIDATE = "changing consolidate policy";
  public static final String CHANGE_ADD_CONSOLIDATE = "adding a consolidate clause";
  public static final String CHANGE_WHERE = "changing where clause";
  public static final String CHANGE_SUBTRACT = "subtracting tuple";
  public static final String CHANGE_ADD_HAVING = "adding a having clause";
  public static final String CHANGE_HAVING = "changing the having clause";
  public static final String CHANGE_ADD_WHERE = "adding a where clause";
  public static final String CHANGE_RECURSIVE_REMOVE_TUPLE = "recursively remove tuple from view ";
  public static final String CHANGE_ADD_TUPLE = "adding tuple to view ";

  public static final String ADDTUPLE = "Add tuple";
  public static final String REMOVETUPLE = "Remove tuple";

  // labels: use short form for now to save length in URL
  public static final String LABEL_CORRECT = "C";
  public static final String LABEL_INCORRECT = "I";
  public static final String LABEL_NEUTRAL = "N";

  // Cache dependency of each view. For each view, first/second list contains views that it
  // does/does not depend on
  public static HashMap<String, Pair<ArrayList<Integer>, ArrayList<Integer>>> DEPENDENCIES =
      new HashMap<String, Pair<ArrayList<Integer>, ArrayList<Integer>>>();

  public static HashMap<String, Integer> VIEW_INDEX = new HashMap<String, Integer>();

  // Mapping from rewritten view name to copy of corresponding original view
  // body statement
  private HashMap<String, ViewBodyNode> rewritten2OriginalCopyMap;

  /**
   * mapping from original view body statement to rewritten view name.
   */
  private HashMap<ViewBodyNode, String> originalNode2RewrittenViewMap;

  // the catalog used to parse the rwritten query
  // For now it is needed in the ChangeViewFilter llc to determine
  // is a view should be filtered by another view
  // (we don't want to generate a HLC to filter V1 by V2 if V2 depends on V1 or is created after V1.
  private Catalog catalog;

  /**
   * The list of views that we should not refine, nor compute any provenance for and which are
   * regarded as base views for all practical purposes. This allows us to work with high-quality
   * annotators which might contain features that are not supported by the provenance rewrite code
   * yet (e.g., extract block, consolidate LeftToRight, extract pattern)
   */
  private final ArrayList<String> baseViews = new ArrayList<String>();

  // Various properties for the refinement algorithm
  private Properties props;

  public void makeDefaultProperties() {
    props = new Properties();

    props.setProperty(RefinerConstants.REFINER_UNTOUCHABLE_VIEWS_PROP,
        "FirstDict, LastDict, DblNewLine, SingleNewLine, "
            + "Sal, InitialWrd, Last, First, FirstLast, LastCommaFirst, InitialLast, "
            + "InitialFirst, FirstInitialLast, CapsCommaFirst, FirstCaps, CapsLast, "
            + "SalCapsCaps, LastCommaCaps, "
            + "Location, Organization, Address, EmailAddress, PhoneNumber, DateTime, URL");

    String[] untouchableViews =
        props.getProperty(RefinerConstants.REFINER_UNTOUCHABLE_VIEWS_PROP, "").split("\\s*,\\s*");
    initBaseViews(untouchableViews);

    props.setProperty(RefinerConstants.REFINER_NUM_ITERATIONS_PROP, Integer.toString(10));

    props.setProperty(RefinerConstants.REFINER_CROSS_VALIDATION_PROP, Boolean.toString(false));

    props.setProperty(RefinerConstants.REFINER_BETA_PROP, Integer.toString(1));

    props.setProperty(RefinerConstants.REFINER_F_WEIGHT_PROP, Double.toString(0.8));

    props.setProperty(RefinerConstants.REFINER_MAX_NUM_RANGES_PROP, Integer.toString(3));

    props.setProperty(RefinerConstants.REFINER_RANGE_PENALTY_PROP, Double.toString(0));
    props.setProperty(RefinerConstants.REFINER_DICT_PENALTY_PROP, Double.toString(0));
    props.setProperty(RefinerConstants.REFINER_FILTER_PENALTY_PROP, Double.toString(0));

    props.setProperty(RefinerConstants.REFINER_DICTIONARY_GAIN_THRESHOLD_PROP, Double.toString(0));

    props.setProperty(RefinerConstants.REFINER_MERGE_DICTIONARY_LLCS_PROP, Boolean.toString(true));

    props.setProperty(RefinerConstants.REFINER_OVERLAP_FILTER_VIEWS_PROP,
        "PersonLastFirstCand, PersonFirstLastCand, PersonBeforeConsolidate, SingleNewLine, Organization");

    props.setProperty(RefinerConstants.REFINER_SUMMARY_FILE_PROP, "ChangeSummary.txt");
    props.setProperty(RefinerConstants.REFINER_LLC_FILE_PROP, "LLC.txt");
  }

  /**
   * Override the default set of properties.
   * 
   * @param p
   */
  public void setProperties(Properties p) {
    Iterator<Entry<Object, Object>> itr = p.entrySet().iterator();
    while (itr.hasNext()) {
      Entry<Object, Object> prop = itr.next();
      String name = (String) prop.getKey();
      String value = (String) prop.getValue();
      props.setProperty(name, value);

      System.err.printf("SET: '%s'='%s'\n", name, value);
    }

    String[] untouchableViews =
        props.getProperty(RefinerConstants.REFINER_UNTOUCHABLE_VIEWS_PROP, "").split("\\s*,\\s*");
    initBaseViews(untouchableViews);
  }

  // records all view names in the original AQL; this is used to restrict high level refinement.
  private final ArrayList<String> viewNames = new ArrayList<String>();

  // the list of ids of the tuple to remove from result view
  private final ArrayList<Integer> negativeIds;

  // the list of ids of the tuple to retain in result view
  private ArrayList<Integer> positiveIds;

  // list of docs that user has labeled results on
  private final ArrayList<String> docLabels;
  // input File
  private File inputAqlFile;
  // output File
  private File outputFile;
  // location of the document collection
  private String docLocation;

  // the view of the requested tuple
  private static String requestedView;

  // the view right before the final view; used for consolidation to produce requestedView
  private static String consolidateCandidateView = null;

  // counting the total number of HLCs - for time saving, we wont' generate them all, but produce
  // the union
  private int HLC_COUNT = 0;

  /**
   * stores the complete provenance info for each requested tuple (that is marked negative). result
   * format: ArrayList: each element stores a pair: <viewName, listOfTupleIds>, where viewName is
   * view name of the list of tuples.
   */
  private final HashMap<Integer, HashMap<String, ArrayList<Integer>>> negativeProvResult;

  // cache all results in the provenance tree (of labeled tuples), including intermediate results
  // private HashMap<Integer, Tuple> resultTuples;

  // stores provenance info for positive tuples in output view. Same format as
  // <code>negativeProvResult</code>.
  private final HashMap<Integer, HashMap<String, ArrayList<Integer>>> positiveProvResult;

  private final HashMap<String, TupleSchema> schemas = new HashMap<String, TupleSchema>();

  // tupleFilterCache to pass to LowLevelChangeModule, index is autoid, pair is tuple and docLabel
  // private HashMap<Span, Pair<Tuple, String>> tupleFilterCacheMap = new HashMap<Span, Pair<Tuple,
  // String>>();
  // private HashMap<String, HashMap<Span, Pair<Tuple,String>>> tupleFilterCacheMap = new
  // HashMap<String, HashMap<Span,
  // Pair<Tuple,String>>>();
  private final HashMap<String, HashMap<String, ArrayList<Span>>> tupleFilterCacheMap =
      new HashMap<String, HashMap<String, ArrayList<Span>>>();

  /**
   * Maps an id to all tuple ids who is a descendant in the provenance tree until the view of
   * consolidateCandidateView. The output view is not included. Format: key is tuple ID, entry is
   * ArrayList<Pair<ViewName, list of tuple ids in that view that is a descendant of the key tuple>>
   */
  private static HashMap<Integer, HashMap<String, ArrayList<Integer>>> reverseMap =
      new HashMap<Integer, HashMap<String, ArrayList<Integer>>>();

  /**
   * Maps each id in the consolidateCandidateView to the list of output tuples it affected.
   */
  public static HashMap<Integer, ArrayList<Integer>> consolidateSourceToTarget =
      new HashMap<Integer, ArrayList<Integer>>();

  // Used to cache computed results of a list of descendants in the provenance tree of an id
  public static HashMap<Integer, ArrayList<Integer>> descendants =
      new HashMap<Integer, ArrayList<Integer>>();

  // stores the overlap but not equal tuple ids.
  public static ArrayList<Integer> overlapNotEqual = new ArrayList<Integer>();
  // public static HashMap<Integer, HashMap<String, ArrayList<Integer>>> idProvMap

  // list of possible changes: ViewName, Type of change, Purpose of change
  // (add or remove a tuple), tuple ID to change
  private final ArrayList<Quad<String, String, String, ArrayList<Integer>>> changes;

  @SuppressWarnings("unused")
  private String dictPath;
  @SuppressWarnings("unused")
  private String udfJarPath;

  // public static HashMap<String, ArrayList<Integer>> labelToTupleMap = new HashMap<String,
  // ArrayList<Integer>>();

  public AQLRefine(ArrayList<Integer> ids, File inputAqlFile, File outputFile, String docLocation) {

    clearAll();

    this.negativeIds = new ArrayList<Integer>();
    this.negativeIds.addAll(ids);
    this.docLabels = new ArrayList<String>();
    this.inputAqlFile = inputAqlFile;
    this.outputFile = outputFile;
    this.docLocation = docLocation;
    negativeProvResult = new HashMap<Integer, HashMap<String, ArrayList<Integer>>>();
    positiveProvResult = new HashMap<Integer, HashMap<String, ArrayList<Integer>>>();
    // catalog = new DefaultCatalog();
    changes = new ArrayList<Quad<String, String, String, ArrayList<Integer>>>();

    this.makeDefaultProperties();
    String[] untouchableViews =
        props.getProperty(RefinerConstants.REFINER_UNTOUCHABLE_VIEWS_PROP, "").split("\\s*,\\s*");
    initBaseViews(untouchableViews);
  }

  /**
   * A constructor without the list of input ids. A separate function must be called to set the IDs.
   * 
   * @param inputAqlFile
   * @param outputFile
   * @param docLocation
   */
  public AQLRefine(File inputAqlFile, File outputFile, String docLocation, String dictPath,
      String udfJarPath) {

    clearAll();
    this.negativeIds = new ArrayList<Integer>();
    this.positiveIds = new ArrayList<Integer>();
    this.docLabels = new ArrayList<String>();
    this.inputAqlFile = inputAqlFile;
    this.outputFile = outputFile;
    this.docLocation = docLocation;
    this.dictPath = dictPath;
    this.udfJarPath = udfJarPath;
    negativeProvResult = new HashMap<Integer, HashMap<String, ArrayList<Integer>>>();
    positiveProvResult = new HashMap<Integer, HashMap<String, ArrayList<Integer>>>();
    // catalog = new DefaultCatalog();
    changes = new ArrayList<Quad<String, String, String, ArrayList<Integer>>>();

    this.makeDefaultProperties();
  }

  /**
   * @param dictPath dictionary path to use during internal compilation operations
   */
  public void setDictPath(String dictPath) {
    this.dictPath = dictPath;
  }

  /**
   * @param udfJarPath UDF jar path to use during internal compilation operations
   */
  public void setUDFJarPath(String udfJarPath) {
    this.udfJarPath = udfJarPath;
  }

  /*
   * Used in conjunction with refineFromWeb
   */
  public AQLRefine() {

    clearAll();

    this.negativeIds = new ArrayList<Integer>();
    this.positiveIds = new ArrayList<Integer>();
    this.docLabels = new ArrayList<String>();
    negativeProvResult = new HashMap<Integer, HashMap<String, ArrayList<Integer>>>();
    positiveProvResult = new HashMap<Integer, HashMap<String, ArrayList<Integer>>>();
    // catalog = new DefaultCatalog();
    changes = new ArrayList<Quad<String, String, String, ArrayList<Integer>>>();

    this.makeDefaultProperties();
    String[] untouchableViews =
        props.getProperty(RefinerConstants.REFINER_UNTOUCHABLE_VIEWS_PROP, "").split("\\s*,\\s*");
    initBaseViews(untouchableViews);
  }

  private void clearAll() {
    descendants.clear();
    consolidateSourceToTarget.clear();
    overlapNotEqual.clear();
    reverseMap.clear();
    consolidateCandidateView = null;
    VIEW_INDEX.clear();
    DEPENDENCIES.clear();
  }

  /**
   * This function is specially written to automatic evaluation of refinement quality. To be called
   * from AutoRefineTest. Update on Oct 29: now only operate on labeled documents.
   */
  public String autoRefine(String rewrittenAQL, AQLProvenanceRewriter rewriter, String currentView,
      String includePath, String dictPath, String udfJarPath, String IN_DIR, RefineEvaluator eval,
      boolean askForInput, OutputStream out) throws Exception {

    requestedView = currentView;

    long startMS = System.currentTimeMillis();

    rewritten2OriginalCopyMap = rewriter.getRewritten2OriginalCopyMap();

    makeOriginal2RewrittenMap();

    // collect related views that need to be consider when filtering by type

    boolean toKeep = true;
    for (String s : rewritten2OriginalCopyMap.keySet()) {
      toKeep = true;
      for (String mark : REWRITE_MARKS) {
        if (s.startsWith(mark)) {
          toKeep = false;
          break;
        }
      }
      if (toKeep)
        viewNames.add(s);
    }

    // File docsFile = FileUtils.validateAndCreateFile (docsDir, docCollectionName);
    File docsFile = FileUtils.createValidatedFile(IN_DIR);
    DocReader docs = new DocReader(docsFile);

    int numDocs = docs.size();

    /**
     * Hashmap storing the immediate provenance of all tuples. Format: tuple id: <view, List of
     * provenance tuple ids> (note that view is the viewName of corresponding tuple, not the view
     * name of provenance tuples. The list is null if there is no more prov info.
     */

    // prepare to generate low level changes, index is autoid, pair is tuple and docLabel
    HashMap<Integer, Pair<Tuple, String>> tupleCacheMap =
        new HashMap<Integer, Pair<Tuple, String>>();

    // cache filter list to prepare for low level changes
    // cacheTupleFilterList(inputAQL, docs3, includePath, dictPath, udfJarPath);

    /**
     * Add partial matches to negative IDs. March 6th, 2010
     */
    negativeIds.addAll(eval.getOverlapNotEqual());
    negativeIds.addAll(eval.getActualNeg());

    positiveIds.addAll(eval.getActualPos());

    // oneLevelProvMap contains immediate provenance information - only one level above in the
    // provenance tree

    out.write("\nAnalyzing provenance...".getBytes());

    HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap =
        getSelectedProv(rewrittenAQL, docs, includePath, dictPath, udfJarPath, currentView,
            tupleCacheMap, eval, out, numDocs);

    // Close the document reader
    docs.remove();

    for (int id : negativeIds) {
      HashMap<String, ArrayList<Integer>> result = new HashMap<String, ArrayList<Integer>>();
      getIdProvenance(id, oneLevelProvMap, result);
      negativeProvResult.put(id, result);
    }

    for (int id : positiveIds) {
      HashMap<String, ArrayList<Integer>> result = new HashMap<String, ArrayList<Integer>>();
      getIdProvenance(id, oneLevelProvMap, result);
      positiveProvResult.put(id, result);
    }

    if (negativeIds.isEmpty() || positiveIds.isEmpty()) {
      System.out.println("Can't refine when negative or positive input is empty.");
      System.exit(0);
    }

    // verify correctness of pos/negs
    for (int i : negativeIds) {
      if (!oneLevelProvMap.get(i).first.equals(currentView)) {
        System.out.println("Error in autoRefine: " + oneLevelProvMap.get(i).first);
        System.out.println("id: " + i + ", " + tupleCacheMap.get(i).first.toString());
        System.exit(1);
      }

    }

    for (int i : positiveIds) {
      if (!oneLevelProvMap.get(i).first.equals(currentView)) {
        System.out.println("Error in autoRefine: " + oneLevelProvMap.get(i).first);
        System.out.println("id: " + i + ", " + tupleCacheMap.get(i).first.toString());
        System.exit(1);
      }
    }

    // viewname of the requested tuple

    // gen high level changes
    out.write("\nGenerating High-level Changes...".getBytes());
    refineViews(requestedView);

    // union all high level changes
    HashMap<HighLevelChange, ArrayList<Integer>> changeUnion =
        unionChanges(true, "HLCAfterUnion.txt");

    long endMS = System.currentTimeMillis();

    double elapsedSec = (endMS - startMS) / 1000.0;

    System.err.printf("High Level Changes took %1.3f sec.\n", elapsedSec);

    // File docsFile2 = new File(docsDir, docCollectionName);
    // FIXME: this can be improved - since all results should be included anyway, i can just cache
    // them at once,
    // thus no need to read the data twice.
    // cacheProvTree(tupleCacheMap, rewrittenAQL, docs2, includePath, dictPath, udfJarPath);

    // Update: Jan 2010, changed reverse mapping info from tuple => consolidateCandidateView (the
    // view immediately above
    // the final view). The original
    // mapping was wrong as it didn't handle consolidation cases.

    buildReverseMap(oneLevelProvMap);

    HashMap<String, ArrayList<Integer>> positiveByView =
        getPositiveByView(positiveProvResult, requestedView, positiveIds);

    // dispatch HLC to generate LLCs
    // TODO: STOP HERE: need to add parameters to dispatcher such that we can do iterative
    // refinement.

    dispatchHLC(changeUnion, rewriter.getRewritten2OriginalCopyMap(), tupleCacheMap, positiveByView,
        oneLevelProvMap, askForInput, eval, out);

    // new a doc DocReader to go through the fils
    // File docsFile3 = new File(docsDir, docCollectionName);
    // DocReader docs3 = new DocReader(docsFile3);
    // return printUnionedChanges(changeUnion, includePath, dictPath, udfJarPath, genTest,
    // rewrittenAQL, docs3, true);
    return "Not yet supported";
  }

  private void buildReverseMap(HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap) {

    // gather all ids in the consilidateCandidateView
    ArrayList<Integer> cons = new ArrayList<Integer>();
    Pair<String, ArrayList<Integer>> parentPair = null;
    Pair<String, ArrayList<Integer>> grandparentPair = null;

    /**
     * 1. We need to trace-back the provenance tree two levels up from the output view to the
     * consolidateCandidate view because the rewriter added another level in between. 2. Build a
     * separate mapping from consolidateCandidateView to output view
     */

    // temp fix to remove all tuples without provenance info.
    ArrayList<Integer> toRemove = new ArrayList<Integer>();

    for (int i : positiveIds) {
      if (!oneLevelProvMap.containsKey(i)) {
        System.out.println("bug in buildReverseMap: tuple " + i + " doesn't have provenance info.");
        toRemove.add(i);
        continue;
      }
      parentPair = oneLevelProvMap.get(i);
      if (parentPair.second == null)
        continue;
      for (int j : parentPair.second) {
        grandparentPair = oneLevelProvMap.get(j);
        if (grandparentPair.second == null)
          continue;
        for (int k : grandparentPair.second) {
          if (oneLevelProvMap.get(k).first.equals(consolidateCandidateView)) {
            cons.add(k);
            if (consolidateSourceToTarget.containsKey(k)) {
              consolidateSourceToTarget.get(k).add(i);
            } else {
              ArrayList<Integer> list = new ArrayList<Integer>(2);
              list.add(i);
              consolidateSourceToTarget.put(k, list);
            }
          }
        }
      }
    }

    positiveIds.removeAll(toRemove);
    toRemove.clear();
    for (int i : negativeIds) {
      if (!oneLevelProvMap.containsKey(i)) {
        System.out.println("bug in buildReverseMap: tuple " + i + " doesn't have provenance info.");
        toRemove.add(i);
        continue;
      }
      parentPair = oneLevelProvMap.get(i);
      if (parentPair.second == null)
        continue;
      for (int j : parentPair.second) {
        grandparentPair = oneLevelProvMap.get(j);
        if (grandparentPair.second == null)
          continue;
        for (int k : grandparentPair.second) {
          if (oneLevelProvMap.get(k).first.equals(consolidateCandidateView)) {
            cons.add(k);
            if (consolidateSourceToTarget.containsKey(k)) {
              consolidateSourceToTarget.get(k).add(i);
            } else {
              ArrayList<Integer> list = new ArrayList<Integer>(2);
              list.add(i);
              consolidateSourceToTarget.put(k, list);
            }
          }
        }
      }
    }
    negativeIds.removeAll(toRemove);

    for (int i : cons) {
      buildReverseMapforID(oneLevelProvMap, i);
    }

    cons.clear();
    cons = null;
  }

  /**
   * temp function for debug
   * 
   * @param oneLevelProvMap
   */
  @SuppressWarnings("unused")
  private HashMap<Integer, HashMap<String, ArrayList<Integer>>> buildCompleteReverseMap(
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap) {

    HashMap<Integer, HashMap<String, ArrayList<Integer>>> result =
        new HashMap<Integer, HashMap<String, ArrayList<Integer>>>();
    // gather all ids in the consilidateCandidateView
    ArrayList<Integer> cons = new ArrayList<Integer>();
    cons.addAll(positiveIds);
    cons.addAll(negativeIds);

    for (int i : cons) {
      buildCompleteReverseMapforID(oneLevelProvMap, result, i);
    }

    cons.clear();
    cons = null;
    return result;
  }

  /**
   * Cache all intermediate results mentioned in the provenance tree, for both positive and negative
   * tuples. Also cache all tuples labeled by users. This function should be executed after
   * negativeProvResult and postiveProvResult are filled. Not finished.
   * 
   * @param tupleCacheMap index is autoid, pair is tuple and docLabel
   * @throws Exception
   */
  public void cacheProvTree(HashMap<Integer, Pair<Tuple, String>> tupleCacheMap,
      String rewrittenAQL, DocReader docs, String includePath, String dictPath, String udfJarPath)
      throws Exception {
    ArrayList<Integer> tupleIDs = new ArrayList<Integer>();

    Log.info("Caching provenance tree...");
    // for all numbers mentioned in prov results, add to the list of ids

    HashMap<String, ArrayList<Integer>> tupleProv;
    ArrayList<Integer> intList = new ArrayList<Integer>();

    for (int i : negativeProvResult.keySet()) {
      intList.add(i);
      tupleProv = negativeProvResult.get(i);
      for (ArrayList<Integer> list : tupleProv.values()) {
        intList.addAll(list);
      }
    }

    for (int i : positiveProvResult.keySet()) {
      intList.add(i);
      tupleProv = positiveProvResult.get(i);
      for (ArrayList<Integer> list : tupleProv.values()) {
        intList.addAll(list);
      }
    }

    // also add all positive and negative tuple ids
    for (int i : positiveIds) {
      if (!tupleIDs.contains(i))
        tupleIDs.add(i);
    }

    for (int i : negativeIds) {
      if (!tupleIDs.contains(i))
        tupleIDs.add(i);
    }

    for (int i : intList) {
      if (!tupleIDs.contains(i))
        tupleIDs.add(i);
    }

    /*
     * now re-run the extraction process to cache the corresponding tuples. Optionally, we could
     * cache all results in previous runs, which could take much memory.
     */

    cacheTupleList(rewrittenAQL, docs, includePath, dictPath, udfJarPath, tupleIDs, tupleCacheMap);
  }

  /**
   * Cache all tuples whose ID is in idList
   * 
   * @param idList
   * @param tupleCacheMap Pair<tuple, docLabel> identified by its ID
   * @return
   * @throws Exception
   */
  private void cacheTupleList(String rewrittenAQL, DocReader docs, String includePath,
      String dictPath, String udfJarPath, ArrayList<Integer> idList,
      HashMap<Integer, Pair<Tuple, String>> tupleCacheMap) throws Exception {

    Log.info("Caching tuple list...");
    AutoID.resetIDCounter();
    Tuple docTup = null;
    TupleSchema schema = null;
    int autoID;
    Tuple tuple;

    // Prepare compile parameter
    CompileAQLParams compileParam = new CompileAQLParams();
    compileParam.setDataPath(dictPath + ";" + includePath + ";" + udfJarPath);
    compileParam.setInputFile(FileUtils.createValidatedFile(rewrittenAQL));
    compileParam.setPerformSDM(false);
    compileParam.setPerformSRM(false);

    // Compile
    CompileAQL.compile(compileParam);

    // Instantiate an OperatorGraph object
    String modulePath = compileParam.getOutputURI();
    OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
        modulePath, null, null);
    Map<String, TupleSchema> outputSchema = og.getOutputTypeNamesAndSchema();

    String docLabel = null;

    while (docs.hasNext()) {
      docTup = docs.next();

      // docLabel = docs.getLabelAcc().getVal(docTup).getText();
      // if (docLabel.startsWith("\\"))
      // docLabel = docLabel.substring(1);
      //
      // if (!labeledFiles.contains(docLabel))
      // continue;

      Map<String, TupleList> annotations = og.execute(docTup, null, null);

      // FIXME: potential problem: if a view is not in the output, may not be able to get all
      // intermediate result this
      // way.
      // FIXME: Not sure if the above comment is still valid after changing from AOGRunner to
      // OperatorGraph
      for (Entry<String, TupleList> entry : annotations.entrySet()) {
        String outputName = entry.getKey();
        TupleList tups = entry.getValue();

        schema = outputSchema.get(outputName);

        // collect all tuple schemas
        if (!schemas.containsKey(outputName))
          schemas.put(outputName, schema);

        // find the correct autoID name
        String idString = "";
        for (int i = 0; i < schema.size(); i++) {
          idString = schema.getFieldNameByIx(i);
          if (idString.startsWith(AUTO_ID))
            break;
        }

        if (!idString.startsWith(AUTO_ID))
          continue;

        FieldGetter<Integer> getInt = schema.intAcc(idString);

        for (TLIter itr = tups.iterator(); itr.hasNext();) {
          tuple = itr.next();

          // if (outputName.equals("PersonPhoneFiltered"))
          // Log.debug("output");

          autoID = getInt.getVal(tuple);

          if (idList.contains(autoID)) {
            Pair<Tuple, String> pair = new Pair<Tuple, String>(tuple, docLabel);
            tupleCacheMap.put(autoID, pair);
          }
        }
      }
    }
  }

  /**
   * Cache all tuples whose name is in OVERLAP_FILTER_VIEW
   * 
   * @param filterList
   * @param tupleCacheMap Pair<tuple, docLabel> identified by its ID
   * @return
   * @throws Exception
   */
  /*
   * private void cacheTupleFilterList( String inputAQL, DocReader docs, String includePath, String
   * dictPath, String udfJarPath ) throws Exception { Log.info("Caching tuple filter list...");
   * AutoID.resetIDCounter(); Tuple docTup = null; TupleSchema schema = null; Planner p = new
   * Planner(); Tuple tuple; Catalog catalog = new DefaultCatalog(); AQLParser parser = new
   * AQLParser(inputAQL); parser.setIncludePath(includePath); catalog.setDictsPath(dictPath);
   * catalog.setUDFJarPath(udfJarPath); parser.setCatalog(catalog); parser.parse();
   * p.setInlineDicts(true); p.setPerformSDM(false); p.setPerformSRM(false); String aog =
   * p.compileToString(parser.getCatalog()); // System.err.print(aog + "\n"); AOGRunner runner =
   * AOGRunner.compileStr(aog, dictPath); runner.setBufferOutput(); runner.setPushInput(1, null);
   * String docLabel = null; String filterList = ""; for (String filter : OVERLAP_FILTER_VIEWS){
   * filterList = filterList.concat(filter); } while (docs.hasNext()) { docTup = docs.next();
   * runner.setAllOutputsEnabled(false); runner.setAllOutputsEnabled(true); runner.pushDoc(docTup);
   * for (String outputName : runner.getValidOutputNames()) { if (!filterList.contains(outputName))
   * continue; TupleList tups = runner.getResults(outputName); schema =
   * runner.getOutputSchema(outputName); // collect all tuple schemas if
   * (!schemas.containsKey(outputName)) schemas.put(outputName,schema); // find the correct idString
   * name String idString = ""; for (int i = 0; i < schema.size(); i++) { idString =
   * schema.getFieldNameByIx(i); } //FieldGetter<Integer> getInt = schema.intAcc(idString);
   * FieldGetter<Span> getSpan = schema.spanAcc(idString); HashMap<Span, Pair<Tuple, String>>
   * viewFilterMap; if (tupleFilterCacheMap.containsKey(outputName)){ viewFilterMap =
   * tupleFilterCacheMap.get(outputName); } else { viewFilterMap = new HashMap<Span, Pair<Tuple,
   * String>>(); tupleFilterCacheMap.put(outputName, viewFilterMap); } for (TLIter itr =
   * tups.iterator(); itr.hasNext();) { tuple = itr.next(); //String outputLine = outputName + "," +
   * begin + "," + end + ",\"" + text + "\"\n"; //System.out.println(outputLine); Span span =
   * getSpan.getVal(tuple); Pair<Tuple, String> pair = new Pair<Tuple, String>(tuple, docLabel);
   * viewFilterMap.put(span, pair); } } } }
   */

  /**
   * This function should be called in refineFromWeb or autoRefine. Generate the reverse map only
   * once per AQL. The result contains for each tuple that is an ancestor of some other tuple, the
   * list of descendants in all views.
   * 
   * @param oneLevelProvMap
   * @param tupleCacheMap
   * @param outputView output view name
   */
  /*
   * public static void genReverseMap(HashMap<Integer, Pair<String, ArrayList<Integer>>>
   * oneLevelProvMap, ArrayList<Integer> positiveIds, ArrayList<Integer> negativeIds, String
   * outputView) { // HashMap<Integer, ArrayList<Pair<String, ArrayList<Integer>>>>
   * Log.info("Genearting reverse provenance map ..."); ArrayList<Integer> allLabeledIds = new
   * ArrayList<Integer>(positiveIds); allLabeledIds.addAll(negativeIds); // String view ; //view
   * name of the tuple in consideration // ArrayList<Integer> list; ArrayList<Pair<String,
   * ArrayList<Integer>>> entry; // get the list of ids we want to create reverse map: only those
   * mentioned in the provenance tree of labeled results. HashMap<String, ArrayList<Integer>> result
   * = new HashMap<String, ArrayList<Integer>>(); ArrayList<Integer> provIds; for (int i:
   * allLabeledIds) { result.clear(); getIdProvenance(i, oneLevelProvMap, result); for (String view:
   * result.keySet()){ provIds = result.get(view); for (int j: provIds){ if
   * (reverseMap.containsKey(j)){ entry = reverseMap.get(j); for (Pair<String, ArrayList<Integer>>
   * innerPair: entry) { if (innerPair.first.equals(view)) { // if (!innerPair.second.contains(i)) {
   * innerPair.second.add(i); } } } reverseMap.put(j, entry); } // create new entry else {
   * ArrayList<Pair<String, ArrayList<Integer>>> newEntry = new ArrayList<Pair<String,
   * ArrayList<Integer>>>(); ArrayList<Integer> ids = new ArrayList<Integer>(); ids.add(i);
   * newEntry.add(new Pair<String, ArrayList<Integer>>(outputView, ids)); //FIXME: we are not
   * tracking the removal of intermediate tuples! We need something like recursive print.
   * reverseMap.put(j, newEntry); } } } } }
   */
  /**
   * Recursively build a one-level reverse provenance mapping. This function is called on all tuples
   * in the consolidate candidate view.
   * 
   * @param oneLevelProvMap
   * @param tupleCacheMap
   * @param id the tuple id of one output tuple in the *output* view
   */

  public void buildReverseMapforID(
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap, int id) {

    Pair<String, ArrayList<Integer>> pair = null;
    HashMap<String, ArrayList<Integer>> mapEntry = null;
    ArrayList<Integer> tmpList = null;

    if (id != AQLProvenanceRewriter.DEFAULT_DOC_ID && oneLevelProvMap.containsKey(id)
        && oneLevelProvMap.get(id).second != null)
      pair = oneLevelProvMap.get(id);
    else
      return;
    for (int j : pair.second) {
      // Log.debug("Working on id " + j);
      // each of j is a parent of i
      if (reverseMap.containsKey(j)) {
        mapEntry = reverseMap.get(j);
        if (mapEntry.containsKey(pair.first)) {
          tmpList = mapEntry.get(pair.first);
          if (!tmpList.contains(id))
            tmpList.add(id);
        } else { // mapEntry doesn't contain the view
          ArrayList<Integer> list = new ArrayList<Integer>();
          list.add(id);
          mapEntry.put(pair.first, list);
        }
      } else { // reverseMap doesn't contain id j
        HashMap<String, ArrayList<Integer>> newEntry = new HashMap<String, ArrayList<Integer>>();
        ArrayList<Integer> list = new ArrayList<Integer>();
        list.add(id);
        newEntry.put(pair.first, list);
        reverseMap.put(j, newEntry);
      }
      buildReverseMapforID(oneLevelProvMap, j);
    }
  }

  public void buildCompleteReverseMapforID(
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap,
      HashMap<Integer, HashMap<String, ArrayList<Integer>>> reverseMap, int id) {

    Pair<String, ArrayList<Integer>> pair = null;
    HashMap<String, ArrayList<Integer>> mapEntry = null;
    ArrayList<Integer> tmpList = null;

    if (id != AQLProvenanceRewriter.DEFAULT_DOC_ID && oneLevelProvMap.containsKey(id)
        && oneLevelProvMap.get(id).second != null)
      pair = oneLevelProvMap.get(id);
    else
      return;
    for (int j : pair.second) {
      // Log.debug("Working on id " + j);
      // each of j is a parent of i
      if (reverseMap.containsKey(j)) {
        mapEntry = reverseMap.get(j);
        if (mapEntry.containsKey(pair.first)) {
          tmpList = mapEntry.get(pair.first);
          if (!tmpList.contains(id))
            tmpList.add(id);
        } else { // mapEntry doesn't contain the view
          ArrayList<Integer> list = new ArrayList<Integer>();
          list.add(id);
          mapEntry.put(pair.first, list);
        }
      } else { // reverseMap doesn't contain id j
        HashMap<String, ArrayList<Integer>> newEntry = new HashMap<String, ArrayList<Integer>>();
        ArrayList<Integer> list = new ArrayList<Integer>();
        list.add(id);
        newEntry.put(pair.first, list);
        reverseMap.put(j, newEntry);
      }
      buildCompleteReverseMapforID(oneLevelProvMap, reverseMap, j);
    }
  }

  /**
   * For debugging: print out the complete provenance subtree of a given ID
   * 
   * @param id
   */
  public void printReverseMapforID(int id,
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap,
      HashMap<Integer, HashMap<String, ArrayList<Integer>>> map, int spaces) {
    String s = "";
    for (int i = 0; i < spaces; i++) {
      s += " ";
    }

    System.out.println(s + "tuple ID = " + id + "; view = " + oneLevelProvMap.get(id).first);

    if (map.containsKey(id)) {
      for (String view : map.get(id).keySet()) {
        for (int i : map.get(id).get(view)) {
          printReverseMapforID(i, oneLevelProvMap, map, spaces + 2);
        }
      }
    }
  }

  public void printProvenanceforID(StringBuilder s, int id,
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap, int spaces) {

    for (int i = 0; i < spaces; i++) {
      s.append(" ");
    }

    if (isBaseView(oneLevelProvMap.get(id).first))
      s.append("BaseView");
    else {
      s.append("tuple ID = " + id + "; view = " + oneLevelProvMap.get(id).first + "\n");

      if (id > AQLProvenanceRewriter.DEFAULT_DOC_ID && oneLevelProvMap.containsKey(id)
          && oneLevelProvMap.get(id).second != null) {
        for (int i : oneLevelProvMap.get(id).second) {
          if (i > AQLProvenanceRewriter.DEFAULT_DOC_ID)
            printProvenanceforID(s, i, oneLevelProvMap, spaces + 2);
        }
      }
    }

  }

  /**
   * Dispatch high level changes to low level change generators. For each HLC, pass information to
   * LowLevelChangeModule, which will manage the generation of low level changes. LLCs are stored in
   * memory locally. The ranking functions will also be in this file. Ben: iterative refinement
   * happens here. User can also add more labels after initial refinement. - Nov. 1, 2009.
   * 
   * @param positiveByView positive provenance indexed by view name, obtained by function
   *        <code>getPositiveByView</code> .
   * @param askForInput if set, ask users to choose which LLC to apply.
   * @throws FileNotFoundException
   */
  public void dispatchHLC(HashMap<HighLevelChange, ArrayList<Integer>> changeUnion,
      HashMap<String, ViewBodyNode> rewritten2OriginalCopyMap,
      HashMap<Integer, Pair<Tuple, String>> tupleCacheMap,
      HashMap<String, ArrayList<Integer>> positiveByView,
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap, boolean askForInput,
      RefineEvaluator eval, OutputStream out) throws IOException {
    Log.info("Dispatching high level changes...");

    // TODO print out posi and nega prov and for debugging the recursiveRemoval.

    /*
     * Stores all remaining tuples for each views that's being refined. After each refinement is
     * applied, this map is refreshed: for each view, for each id, if it's not in the provenance of
     * any existing pos/neg tuples, it's removed.
     */
    HashMap<String, ArrayList<Integer>> remainMap = constructRemainMap(positiveIds, negativeIds);

    if (DEBUG) {
      PrintWriter provWriter = new PrintWriter("ProvenanceTree.txt");
      StringBuilder s = new StringBuilder();
      for (int i : positiveIds) {
        // System.out.println("Prov of positive " + i);
        printProvenanceforID(s, i, oneLevelProvMap, 0);
      }

      for (int i : negativeIds) {
        // System.out.println("Prov of negative " + i);
        printProvenanceforID(s, i, oneLevelProvMap, 0);
      }
      provWriter.append(s);
      provWriter.close();
    }

    out.write("\nGenerating Low-level Changes...".getBytes());

    FileWriter writer = new FileWriter(props.getProperty(RefinerConstants.REFINER_LLC_FILE_PROP),
        Boolean.getBoolean(props.getProperty(RefinerConstants.REFINER_CROSS_VALIDATION_PROP)));

    askForInput = false;
    Scanner in = new Scanner(System.in);

    int PRINT_LIMIT = 5; // max number of LLCs for users to choose from.

    // make a local copy of positive and negative tuples in the output view
    ArrayList<Integer> positivesCopy = new ArrayList<Integer>(positiveIds);
    ArrayList<Integer> negativesCopy = new ArrayList<Integer>(negativeIds);

    // ids in the view being considered in the HLC
    ArrayList<Integer> localPos = null, localNeg = null;
    String view = null;

    ArrayList<LowLevelChange> llcs = new ArrayList<LowLevelChange>();
    // HighLevelChange bestHLC = null; //remembers the best HLC so far
    LowLevelChange bestLLC; // best LLC

    // final ordered list of LLC to be presented to the user
    ArrayList<LowLevelChange> finalList = new ArrayList<LowLevelChange>();

    ArrayList<LowLevelChange> tempList = null;
    ArrayList<Integer> removedHLC = new ArrayList<Integer>();

    ArrayList<Integer> removedNonResults = new ArrayList<Integer>();

    ArrayList<LowLevelChange> sortedLLC = null;
    int choice;
    int iterationCount = 0;
    ArrayList<Integer> toRemovePos = new ArrayList<Integer>();
    ArrayList<Integer> toRemoveNeg = new ArrayList<Integer>();
    ArrayList<LowLevelChange> oneIterDicts = new ArrayList<LowLevelChange>();

    long startMS;
    long endMS;

    int numIterations =
        Integer.parseInt(props.getProperty(RefinerConstants.REFINER_NUM_ITERATIONS_PROP));

    while (negativesCopy.size() > 0 && positivesCopy.size() > 0 && iterationCount < numIterations) {
      startMS = System.currentTimeMillis();

      toRemoveNeg.clear();
      toRemovePos.clear();
      oneIterDicts.clear();

      writer.append(
          "\n\n\nBeginning iteration " + iterationCount + " current time " + startMS + "\n\n");
      // Step 1: for each HLC, find list of LLCs
      llcs.clear();
      sortedLLC = null;

      for (HighLevelChange hlc : changeUnion.keySet()) {
        if (hlc.getViewName().equals("PersonsPhoneCandidates")
            && hlc.getChangeType().equals(CHANGE_WHERE))
          System.out.println("Debug");

        if (DEBUG)
          // System.out.println("Considering HLC: " + hlc.toString() + "\n Remain # of HLC: " +
          // (changeUnion.size()-removedHLC.size()));
          // check if this HLC is removed; we exclude all HLC that attempts to modify the final
          // view. All refinements
          // should be done before that.
          // if (removedHLC.contains(hlc.getId()) || hlc.getViewName().equals(requestedView)){
          if (hlc.getViewName().equals(requestedView)) {
            if (DEBUG)
              System.out.println(
                  "HLC " + hlc.getId() + " has been considered before or is on the output view.");
            if (!removedHLC.contains(hlc.getId()))
              removedHLC.add(hlc.getId());
            continue;
          }

        // only support CHANGE_WHERE now
        if (!(hlc.getChangeType().equals(CHANGE_WHERE) || hlc.getChangeType().equals(CHANGE_DICT)
        // )){
            || hlc.getChangeType().equals(CHANGE_SUBTRACT))) { // disable change subtract for the
                                                               // moment.
          if (!removedHLC.contains(hlc.getId()))
            removedHLC.add(hlc.getId());
          if (DEBUG)
            System.out.println("HLC " + hlc.getId() + " is not supported.");
          continue;
        }

        localNeg = null;
        localPos = null;
        view = hlc.getViewName();

        // 1.a. find the list of tuples to remove
        localNeg = new ArrayList<Integer>(changeUnion.get(hlc));

        // /**
        // * code to verify that localNeg is correct
        // */
        // ArrayList<Integer> tmp = new ArrayList<Integer>();
        // if (view.equals(consolidateCandidateView)) {
        // for (int i: negativesCopy) {
        // for (int j: oneLevelProvMap.get(i).second) {
        // if (!tmp.contains(j))
        // tmp.add(j);
        // }
        // }
        // }
        // // end verification

        if (localNeg.isEmpty()) {
          if (DEBUG)
            System.out.println("HLC " + hlc.getId() + " has no local negatives to remove");
          if (!removedHLC.contains(hlc.getId()))
            removedHLC.add(hlc.getId());
          continue;
        }
        localNeg.removeAll(removedNonResults);

        // 1.b. for the view (in the HLC), find the list of tuples to preserve
        if (positiveByView.containsKey(view))
          localPos = new ArrayList<Integer>(positiveByView.get(view));
        else
          localPos = new ArrayList<Integer>();

        // FIXME: removedNonResults doesn't deal with anything in the upper stream of the provenance
        // tree
        localPos.removeAll(removedNonResults);
        /*
         * // code to verify that localPos is correct ArrayList<Integer> tmp2 = new
         * ArrayList<Integer>(); if (view.equals(consolidateCandidateView)) { for (int i:
         * positivesCopy) { for (int j: oneLevelProvMap.get(i).second) { if (!tmp2.contains(j))
         * tmp2.add(j); } } if (tmp2.size() == localPos.size()) System.out.println("Correct"); } //
         * end verification
         */

        // remove those tuples that appear in BOTH localPos and localNeg
        localPos.removeAll(localNeg);
        localNeg.removeAll(localPos);

        // clean up localPos and localNeg further
        if (iterationCount > 0) {
          if (remainMap.containsKey(view)) {
            for (Integer i : localPos)
              if (!remainMap.get(view).contains(i)) {
                toRemovePos.add(i);
                // System.out.println("positive tuple " + i + " of view " + view + " is removed
                // because it's " +
                // "not in the provenance of any existing tuple");
              }
            for (Integer i : localNeg)
              if (!remainMap.get(view).contains(i)) {
                toRemoveNeg.add(i);
                // System.out.println("negative tuple " + i + " of view " + view + " is removed
                // because it's " +
                // "not in the provenance of any existing tuple");
              }
          }
          localPos.removeAll(toRemovePos);
          localNeg.removeAll(toRemoveNeg);
        }

        if (localPos.isEmpty() || localNeg.isEmpty()) {
          if (DEBUG)
            System.out.println(
                "HLC " + hlc.getId() + " positive/negative set has become empty in dispatchHLC.");
          if (!removedHLC.contains(hlc.getId()))
            removedHLC.add(hlc.getId());
          continue;
        }

        // 1.c. pass the information to LLCM, which generates list of LLCs
        tempList = LowLevelChangeModule.genLowLevelChanges(hlc, rewritten2OriginalCopyMap, localNeg,
            null, localPos, tupleCacheMap, schemas, oneLevelProvMap, positivesCopy, negativesCopy,
            tupleFilterCacheMap, props, catalog);
        for (LowLevelChange llc : tempList)
          llc.setHlc(hlc.getId());

        // 1.d. Store LLCs in a list to be ranked.
        llcs.addAll(tempList);
        localPos.clear();
        localNeg.clear();
      } // end looping through HLCs

      if (llcs.size() > 0) {
        sortedLLC = LowLevelChangeModule.sortLLC(llcs);
      } else { // no LLC is generated; terminate
        System.out.println("No LLC is generated; current # of positives " + positivesCopy.size()
            + "; current # " + "of negatives " + negativesCopy.size());
        break;
      }

      String dicts = "";
      if (getBooleanProperty(RefinerConstants.REFINER_MERGE_DICTIONARY_LLCS_PROP)) {
        for (int i = 0; i < sortedLLC.size() && i < 100; i++) {
          if (sortedLLC.get(i).getChangeString().contains("dictionary"))
            oneIterDicts.add(sortedLLC.get(i));
          else
            writer.append(i + ": " + sortedLLC.get(i).toString());
        }
      } else {
        for (int i = 0; i < sortedLLC.size() && i < 40; i++) {
          writer.append(i + ": " + sortedLLC.get(i).toString());
        }
      }

      dicts = summarizeLLC(oneIterDicts,
          getDoubleProperty(RefinerConstants.REFINER_DICTIONARY_GAIN_THRESHOLD_PROP));
      writer.append("Dictionary changes " + dicts);

      if (askForInput) {
        System.out.println("Please pick one out of the following changes: ");
        for (int i = 0; i < sortedLLC.size() && i < PRINT_LIMIT; i++) {
          System.out.println(i + ": " + sortedLLC.get(i).toString());
        }
        do {
          choice = in.nextInt();
          if (choice < 0 || choice >= sortedLLC.size())
            System.out.println("Please enter a number between 0 and " + (sortedLLC.size() - 1));
        } while (choice < 0 || choice >= sortedLLC.size());
        bestLLC = sortedLLC.get(choice);
      } else
        bestLLC = sortedLLC.get(0);

      removedHLC.add(bestLLC.getHlc());

      LowLevelChangeModule.applyLLC(positivesCopy, negativesCopy, bestLLC, removedNonResults,
          oneLevelProvMap);
      cleanUpRemains(remainMap, positivesCopy, negativesCopy);
      endMS = System.currentTimeMillis();
      double elapsedSec = (endMS - startMS) / 1000.0;

      double timeToFirst;
      if (iterationCount == 0) {
        timeToFirst = (endMS - startTimeMS) / 1000.0;
        writer.append("Time to first change is: " + timeToFirst + "\n");
      }

      writer.append(
          "After applying the best LLC: " + bestLLC.getChangeString() + ",\n" + negativesCopy.size()
              + " negative outputs, " + positivesCopy.size() + " positive tuples remain. "
              + "\n Total time for this iteration is " + elapsedSec + " seconds");

      finalList.add(bestLLC);
      iterationCount++;
    }

    writer.append("Final list of changes\n");

    if (finalList.isEmpty()) {
      out.write("\nNo suggested refinements.\n".getBytes());
    } else
      out.write("\nSuggested refinements:\n".getBytes());

    StringBuffer sb = new StringBuffer();

    String dictChanges = summarizeLLC(finalList, 0.0001);
    writer.append(dictChanges);
    sb.append(String.format("\nDictionary Refinements\n----------------------------------\n%s",
        dictChanges));

    int llcId = 0;
    for (LowLevelChange llc : finalList) {
      llcId++;
      writer.append(llc.toString());
      sb.append(String.format("\nView Refinement #%d\n----------------------------------\n%s\n",
          llcId, llc.toPrettyString()));
    }

    out.write(sb.toString().getBytes());

    writer.close();
  }

  private void cleanUpRemains(HashMap<String, ArrayList<Integer>> remainMap,
      ArrayList<Integer> remainPos, ArrayList<Integer> remainNeg) {

    // construct a map based on remaining tuples
    HashMap<String, ArrayList<Integer>> sources = constructRemainMap(remainPos, remainNeg);

    // now clean up
    ArrayList<Integer> toRemove = new ArrayList<Integer>();
    for (String s : remainMap.keySet()) {
      toRemove.clear();
      if (sources.containsKey(s)) {
        for (int i : remainMap.get(s)) {
          if (!sources.get(s).contains(i))
            toRemove.add(i);
        }
        remainMap.get(s).removeAll(toRemove);
      }
    }

    sources.clear();
  }

  private HashMap<String, ArrayList<Integer>> constructRemainMap(ArrayList<Integer> pos,
      ArrayList<Integer> neg) {
    HashMap<String, ArrayList<Integer>> sources = new HashMap<String, ArrayList<Integer>>();
    // construct hashmap, for each view, the tuples that should be there
    for (int i : pos) {
      HashMap<String, ArrayList<Integer>> posSource = positiveProvResult.get(i);
      for (String s : posSource.keySet()) {
        if (sources.containsKey(s)) {
          ArrayList<Integer> list = sources.get(s);
          for (int j : posSource.get(s)) {
            if (!list.contains(j))
              list.add(j);
          }
        } else {
          ArrayList<Integer> list = new ArrayList<Integer>();
          list.addAll(posSource.get(s));
          sources.put(s, list);
        }
      }
    }

    // same for neg prov
    for (int i : neg) {
      HashMap<String, ArrayList<Integer>> negSource = negativeProvResult.get(i);
      for (String s : negSource.keySet()) {
        if (sources.containsKey(s)) {
          ArrayList<Integer> list = sources.get(s);
          for (int j : negSource.get(s)) {
            if (!list.contains(j))
              list.add(j);
          }
        } else {
          ArrayList<Integer> list = new ArrayList<Integer>();
          list.addAll(negSource.get(s));
          sources.put(s, list);
        }
      }
    }

    return sources;
  }

  /**
   * Combine a list of LLCs such that changes to the same dictionaries are automatically generated.
   * 
   * @param input list of llcs
   * @param minimum gain of F-score in training for a LLC to be added.
   * @return
   */
  public String summarizeLLC(ArrayList<LowLevelChange> input, double gainThreshold) {
    String result = "";
    // key: dictionary name; value: words to add or remove
    HashMap<String, String> additions = new HashMap<String, String>();
    HashMap<String, String> removals = new HashMap<String, String>();
    DictionaryChange d;
    String s = null;
    // double totalGain = 0;
    ArrayList<LowLevelChange> llcList = new ArrayList<LowLevelChange>();
    String dictWithScore = "";

    for (LowLevelChange llc : input) {
      if (llc instanceof DictionaryChange && llc.getGain() > gainThreshold) {
        // totalGain += llc.getGain ();

        d = (DictionaryChange) llc;
        if (d.isAddWord()) {
          if (additions.containsKey(d.getDictionary())) {
            s = additions.get(d.getDictionary()) + ", " + d.getWord();
            dictWithScore += String.format(", %s (%.2f%%) ", d.getWord(), llc.getGain() * 100);
            additions.put(d.getDictionary(), s);
          } else {
            dictWithScore += String.format("%s (%.2f%%) ", d.getWord(), llc.getGain() * 100);
            additions.put(d.getDictionary(), d.getWord());
          }
        } else {
          if (removals.containsKey(d.getDictionary())) {
            s = removals.get(d.getDictionary()) + ", " + d.getWord();
            removals.put(d.getDictionary(), s);
          } else {
            removals.put(d.getDictionary(), d.getWord());
          }
        }
      } else
        llcList.add(llc);
    }

    for (String dict : additions.keySet()) {
      result += "Additions to dictionary " + dict + ": " + additions.get(dict) + "\n";
    }

    for (String dict : removals.keySet()) {
      result += "Removal from dictionary " + dict + ": " + removals.get(dict) + "\n";
    }
    result += "Improvement in F1-measure: " + dictWithScore + "\n";
    // result += String.format("Total F1-measure improvement: %.2f%%\n", totalGain * 100);

    // finally, remove all summarized LLCs from the input list
    input.clear();
    input.addAll(llcList);
    llcList.clear();
    llcList = null;

    return result;
  }

  /**
   * Traverse the positive provenance result, and find out: for each view in the whole work flow,
   * the list of ids to preserve Note that this include the final output view.
   * 
   * @return
   */
  public HashMap<String, ArrayList<Integer>> getPositiveByView(
      HashMap<Integer, HashMap<String, ArrayList<Integer>>> positiveProvResult, String currentView,
      ArrayList<Integer> positiveIds) {
    HashMap<String, ArrayList<Integer>> result = new HashMap<String, ArrayList<Integer>>();

    HashMap<String, ArrayList<Integer>> provForTuple = null;
    for (int i : positiveProvResult.keySet()) {
      provForTuple = positiveProvResult.get(i);
      for (String view : provForTuple.keySet()) {
        if (result.containsKey(view)) {
          result.get(view).addAll(provForTuple.get(view));
        } else {
          result.put(view, provForTuple.get(view));
        }
      }
    }

    // also add the positiveIds specified by the user in the output view
    for (int i : positiveIds) {
      if (result.containsKey(currentView)) {
        if (result.get(currentView).contains(i))
          Log.debug("Error! Duplicate id in getPositiveByView", i);
        result.get(currentView).add(i);
      } else {
        ArrayList<Integer> list = new ArrayList<Integer>();
        list.add(i);
        result.put(currentView, list);
      }
    }

    return result;
  }

  /**
   * Produce a duplicate-free union of all changes. Output format: ViewName, Type of change, Purpose
   * of change (add or remove a tuple), list of tuples to change. the first three attributes form a
   * key. Also print change statistics
   */
  public HashMap<HighLevelChange, ArrayList<Integer>> unionChanges(boolean printStat,
      String outFileName) {

    HashMap<HighLevelChange, ArrayList<Integer>> changeUnion =
        new HashMap<HighLevelChange, ArrayList<Integer>>();

    int afterCount = 0;
    ArrayList<Integer> tupleIds;
    // for (int id : changes.keySet()) {

    for (Quad<String, String, String, ArrayList<Integer>> entry : changes) {

      // ignore all changes that's not on the original views
      if (!viewNames.contains(entry.first))
        continue;

      // Log.debug(entry.first + ", " + entry.second + ", " + entry.third + ", " + entry.fourth);

      HighLevelChange c = new HighLevelChange(entry.first, entry.second, entry.third);
      if (changeUnion.containsKey(c)) {
        tupleIds = changeUnion.get(c);
        for (int j : entry.fourth)
          if (!tupleIds.contains(j))
            tupleIds.add(j);
        changeUnion.put(c, tupleIds);
      } else {
        ArrayList<Integer> idList = new ArrayList<Integer>();
        idList.addAll(entry.fourth);
        changeUnion.put(c, idList);
      }
    }
    // }

    if (printStat) {
      afterCount = changeUnion.size();
      Log.debug("Total number of HLC " + HLC_COUNT);
      Log.debug("Total number of unioned HLC: " + afterCount);
    }

    if (outFileName != null) {
      String out = "";
      for (HighLevelChange HLC : changeUnion.keySet()) {
        out += HLC.toString();
        for (int i : changeUnion.get(HLC)) {
          out += " " + i;
        }
        out += "\n\n";
      }
      try {
        PrintWriter writer = new PrintWriter(new File(outFileName));
        writer.write(out);
        writer.close();
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      }
    }

    return changeUnion;
  }

  /*
   * private void printChanges(String parentDir, boolean genTest, String rewrittenAQL) throws
   * FileNotFoundException, Exception { //output the rewritten views
   * Log.info("Total number of high level changes: " + changes.size());
   * Log.info("Writing suggested changes to " + outputFile); //outputViews(rewrittenViews,
   * outputAqlFile); PrintWriter out = new PrintWriter(outputFile); for (int id: ids) {
   * out.print("High level changes for removing tuple " + id + " of type " + requestedView +":\n");
   * int counter = 0; for (Quad<String, String, String, Integer> entry: changes.get(id)) { if
   * (entry.third.equals(ADDTUPLE)) out.print(counter + " " + entry.first + ": " + entry.third + " "
   * + entry.fourth + "\n"); else out.print(counter + " " + entry.first + ": " + entry.third + " " +
   * entry.fourth + " by " + entry.second + "\n"); counter++; } if (genTest) { ArrayList<Integer>
   * printList = new ArrayList<Integer>(); printList.add(0, id); printList.addAll(genProvIDs(id));
   * DocReader docs2 = new DocReader(new File(docLocation));
   * out.print("\nPrinting related tuples :\n"); HashMap<Integer, String> tupleOut =
   * printTupleList(rewrittenAQL, docs2, parentDir, printList); for (int tupleID: printList) {
   * out.print(tupleOut.get(tupleID)); } } } out.close(); }
   */

  // /**
  // * Print out the unioned high level changes, to text or html format.
  // *
  // * @param changeUnion
  // * unioned changes
  // * @param genTest
  // * if set to true, output the list of related tuples
  // * @param docs
  // * a DocReader pointing to the document collection
  // * @param isHTML
  // * whether output to html format
  // */
  // private String printUnionedChanges(
  // HashMap<HighLevelChange, ArrayList<Integer>> changeUnion,
  // String includePath, String dictPath, String udfJarPath, boolean genTest,
  // String rewrittenAQL, DocReader docs, boolean isHTML)
  // throws FileNotFoundException, Exception {
  //
  // Log.info("Total number of high level changes: " + changes.size());
  //
  // int counter = 0;
  //
  // // now sort the changes alphabetically
  // HighLevelChange[] changesArray = new HighLevelChange[changeUnion
  // .keySet().size()];
  // int cnt = 0;
  // for (HighLevelChange c : changeUnion.keySet()) {
  // changesArray[cnt] = new HighLevelChange(c);
  // cnt++;
  // }
  //
  // String htmlBegin = "<META http-equiv=\"Content-Type\""
  // + " content=\"text/html; charset=UTF-8\">\n" + "<head>\n"
  // + "<style type=\"text/css\">\n" + " <!-- \n"
  // + "@import url(\"resultpage.css\"); \n" + "--> \n"
  // + "</style>\n"
  // + "<script language=\"javascript\" type=\"text/javaScript\" "
  // + " src=\"displayscripts.js\"></script>" + "<title>"
  // + "List of Suggested Changes" + "</title>\n"
  // + "</head>\n<body>\n";
  //
  // Arrays.sort(changesArray);
  //
  // String listToRemove = "You have requested to remove the following tuples: ";
  // for (int i : negativeIds)
  // listToRemove += i + " ";
  //
  // String title = "Output changes in alphabetical order of view names\n"
  // + listToRemove + ".\n" + "Output format: \n"
  // + "ViewName, ChangeType, ChangePurpose, "
  // + "List of tuple IDs for this change\n\n";
  //
  // String htmlTitle = "<h2>Output changes in alphabetical order of view names</h2>\n"
  // + listToRemove
  // + ".<br>"
  // + "Output format: \n"
  // + "High Level Change: ViewName, ChangeType, ChangePurpose, "
  // + "List of tuple IDs for this change\n\n";
  //
  // ArrayList<Integer> printList = new ArrayList<Integer>();
  //
  // String body = "";
  //
  // if (isHTML)
  // body += "<dd><ul>\n";
  //
  // for (HighLevelChange c : changesArray) {
  // ArrayList<Integer> idList = changeUnion.get(c);
  //
  // if (isHTML)
  // body += "<li>\n";
  // body += counter + " " + c.getViewName() + ", " + c.getChangeType()
  // + ", " + c.getChangePurpose() + ", ";
  // for (int id : idList) {
  // body += (id + " ");
  // if (!printList.contains(id))
  // printList.add(id);
  // }
  // body += "\n";
  // if (isHTML)
  // body += "</li>\n";
  // counter++;
  // }
  //
  // if (isHTML)
  // body += "</ul></dd>\n";
  //
  // String tuples = "";
  // if (genTest) {
  // for (int id : negativeIds) {
  // if (!printList.contains(id))
  // printList.add(id);
  // }
  //
  // // sort printList in order
  // Integer[] printIds = new Integer[printList.size()];
  // printIds = printList.toArray(printIds);
  // Arrays.sort(printIds);
  //
  // if (isHTML)
  // tuples = "<h2>Printing related tuples in ascending order of ID </h2></n>"
  // + "<dd><ul>\n";
  // else
  // tuples += "\nPrinting related tuples in ascending order of ID :\n";
  //
  // HashMap<Integer, String> tupleOut = printTupleList(rewrittenAQL,
  // docs, includePath, dictPath, udfJarPath, printList, isHTML);
  //
  // for (int tupleID : printIds) {
  // if (isHTML)
  // tuples += "<li>" + tupleOut.get(tupleID) + "</li>";
  // else
  // tuples += tupleOut.get(tupleID);
  // }
  // }
  //
  // String htmlEnd = "</body>\n</html>";
  // if (isHTML)
  // return htmlBegin + htmlTitle + body + tuples + htmlEnd;
  // else
  // return title + body + tuples;
  // }

  /**
   * Create a mapping from nodes in original catalog to rewritten view names.
   * 
   * @throws ParseException
   */
  public void makeOriginal2RewrittenMap() throws ParseException {
    originalNode2RewrittenViewMap = new HashMap<ViewBodyNode, String>();
    for (String viewName : rewritten2OriginalCopyMap.keySet()) {
      originalNode2RewrittenViewMap.put(rewritten2OriginalCopyMap.get(viewName), viewName);
    }
  }

  /**
   * Cache all provenance information and tuple list. Note that for some tuples, there is no further
   * provenance information. We still keep them in the oneLevelProvMap in order to know their type,
   * but their provEntry is null.
   * 
   * @param rewrittenAQL
   * @param docs
   * @param includePath
   * @param dictPath
   * @param udfJarPath
   * @param currentView
   * @param tupleCacheMap
   * @return
   * @throws Exception
   */
  private HashMap<Integer, Pair<String, ArrayList<Integer>>> getSelectedProv(String rewrittenAQL,
      DocReader docs, String includePath, String dictPath, String udfJarPath, String currentView,
      HashMap<Integer, Pair<Tuple, String>> tupleCacheMap, RefineEvaluator eval, OutputStream out,
      int numDocs) throws Exception {
    Compiler compiler = new Compiler();

    try {
      Log.info("Caching provenance info and tuple list...");
      PrintWriter writer = new PrintWriter(new File("tuples.txt"));

      AutoID.resetIDCounter();

      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap =
          new HashMap<Integer, Pair<String, ArrayList<Integer>>>();
      Tuple docTup = null;
      TupleSchema schema = null;
      int autoID;
      Tuple tuple;

      Catalog catalog = new Catalog();

      // Prepare compile parameter
      CompileAQLParams compileParam = new CompileAQLParams();
      compileParam.setDataPath(dictPath + ";" + includePath + ";" + udfJarPath);
      compileParam.setInputFile(new File(rewrittenAQL));
      compileParam.setPerformSDM(false);
      compileParam.setPerformSRM(false);

      // Compile
      compiler.setCatalog(catalog);
      CompileAQL.compile(compileParam);

      // Instantiate an OperatorGraph object from compiled AQL
      String modulePath = compileParam.getOutputURI();
      OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
          modulePath, null, null);
      Map<String, TupleSchema> outputSchema = og.getOutputTypeNamesAndSchema();

      this.catalog = catalog;

      // variables for finding ids of negative labeled results
      String docLabel;
      ArrayList<Span> actualAnnots = new ArrayList<Span>();
      ArrayList<Integer> autoIDs = new ArrayList<Integer>();

      String[] overlapFilterViews =
          props.getProperty(RefinerConstants.REFINER_OVERLAP_FILTER_VIEWS_PROP).split("\\s*,\\s*");

      int numProcessedDocs = 0;

      while (docs.hasNext()) {

        numProcessedDocs++;

        int progress = (int) (((double) numProcessedDocs / numDocs) * 100);
        if (progress % 10 == 0)
          out.write((String.format("%d%%...\n", progress).getBytes()));

        docTup = docs.next();
        // runner.setAllOutputsEnabled (false);
        // runner.setAllOutputsEnabled (true);

        actualAnnots.clear();
        autoIDs.clear();

        docLabel = (docs.getDocSchema().spanAcc(Constants.LABEL_COL_NAME)).getVal(docTup).getText();
        if (docLabel.startsWith(System.getProperty("file.separator")))
          docLabel = docLabel.substring(1);
        if (DEBUG)
          Log.debug("getSelectedProv: Extracting " + docLabel);
        // FIXME: it turns out that all docs must be pushed in order to get the same autoIDs as the
        // ones from evaluator.
        // if (!labeledFiles.contains(docLabel))
        // continue;

        // runner.pushDoc (docTup);

        ArrayList<String> filterList = new ArrayList<String>();
        for (String filter : overlapFilterViews) {
          filterList.add(filter);
        }

        Map<String, TupleList> annotations = og.execute(docTup, null, null);
        for (Entry<String, TupleList> entry : annotations.entrySet()) {
          String outputName = entry.getKey();
          TupleList tups = entry.getValue();
          schema = outputSchema.get(outputName);

          // collect all tuple schemas
          if (!schemas.containsKey(outputName))
            schemas.put(outputName, schema);

          if (filterList.contains(outputName)) {

            // Find the span attribute that will be used for filtering
            // For now we assume that each view has a single attribute of type span
            String spanAttrName = "";
            spanAttrName = schema.getLastSpanCol();
            // for (int i = 0; i < schema.size(); i++) {
            // idString = schema.getFieldNameByIx(i);
            // }

            FieldGetter<Span> getSpan = schema.spanAcc(spanAttrName);

            // changed by Bin on March 8th
            // HashMap<Span, Pair<Tuple, String>> viewFilterMap;
            HashMap<String, ArrayList<Span>> viewFilterMap; // docLabel -> List of spans

            if (tupleFilterCacheMap.containsKey(outputName)) {
              viewFilterMap = tupleFilterCacheMap.get(outputName);
            } else {
              viewFilterMap = new HashMap<String, ArrayList<Span>>();
              tupleFilterCacheMap.put(outputName, viewFilterMap);
            }

            for (TLIter itr = tups.iterator(); itr.hasNext();) {
              tuple = itr.next();

              Span span = getSpan.getVal(tuple);
              // Pair<Tuple, String> pair = new Pair<Tuple, String>(tuple, docLabel);
              if (viewFilterMap.containsKey(docLabel))
                viewFilterMap.get(docLabel).add(span);
              else {
                ArrayList<Span> list = new ArrayList<Span>();
                list.add(span);
                viewFilterMap.put(docLabel, list);
              }
            }
          }

          // find the correct autoID name
          String idString = "";
          for (int i = 0; i < schema.size(); i++) {
            idString = schema.getFieldNameByIx(i);
            if (idString.startsWith(AUTO_ID))
              break;
          }

          if (!idString.startsWith(AUTO_ID)) {
            // System.out.println("Skipping view " + outputName + " because it has no auto_id");
            if (outputName.equals(currentView)) {
              System.out.println("Error! Skipped output view in getSelectedProv.");
              System.exit(1);
            }
            continue;
          }
          // FIXME: potential problem here: if an output view doesn't have auto_id, it will not be
          // processed.
          // For example, temp views for minus do not have auto_id.

          FieldGetter<Integer> getInt = schema.intAcc(idString);

          for (TLIter itr = tups.iterator(); itr.hasNext();) {
            tuple = itr.next();

            autoID = getInt.getVal(tuple);
            Pair<String, ArrayList<Integer>> provEntry = new Pair<String, ArrayList<Integer>>(
                outputName, extractProvIDs(schema, tuple, autoID));

            // System.out.println(autoID + ": " + tuple.toString());

            // if (negativeIds.contains(autoID))
            // System.out.println(autoID + ": " + tuple.toString());

            if (!oneLevelProvMap.containsKey(autoID)) {
              oneLevelProvMap.put(autoID, provEntry);
              /*
               * if (provEntry.second == null)
               * System.out.printf("One level provenance map: %d %s\n", autoID, provEntry.first);
               * else System.out.printf("One level provenance map: %d %s\n", autoID, provEntry);
               */
            }

            else
              Log.debug("Error in getAllProvenance: id already exists", autoID);

            Pair<Tuple, String> pair = new Pair<Tuple, String>(tuple, docLabel);

            if (DEBUG) {
              writer.append(autoID + ": " + tuple.toString() + "\n");

            }
            tupleCacheMap.put(autoID, pair);

            // this is for getting result labels
            if (!outputName.equals(requestedView))
              continue;

            Span annot = (Span) tups.getSchema().getCol(tuple, 0);

            // System.out.println(autoID + ": " + tuple.toString());
            actualAnnots.add(annot);
            autoIDs.add(autoID);
            // end getting labels
          }

        }
        compareAnnots(eval.getStandard().get(docLabel), actualAnnots, autoIDs, positiveIds,
            negativeIds, negativeIds);
      }
      writer.close();
      return oneLevelProvMap;
    } finally {
      if (compiler != null) {
        compiler.deleteTempDirectory();
      }
    }
  }

  /**
   * Re-writes AQL for provenance. Cache all provenance information in hashmap. This function gets
   * provenance for all tuples. If used with labeled results, it also finds all their (auto) ids.
   * 
   * @param rewrittenAQL AQL that's provenance-rewritten
   * @param docs points to the collection of documents; should be ready to call next().
   * @param labeledResult labeled results passed from the web interface
   * @param curentView current output view in the web interface
   * @return hashmap storing the immediate provenance (the next level in the provenance tree) of all
   *         tuples. format: tuple id: <view, List of provenance tuple ids> (note that view is the
   *         viewName of corresponding tuple, not the viewname of provenance tuples. The list is
   *         null if there is no more prov info.
   * @throws Exception
   */
  @SuppressWarnings({"all"})
  private HashMap<Integer, Pair<String, ArrayList<Integer>>> getAllProvenance(String rewrittenAQL,
      DocReader docs, String includePath, String dictPath, String udfJarPath,
      ArrayList<Triple<String, Integer, String>> labeledResult, String currentView// output view on
                                                                                  // web)
  ) throws Exception {

    HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap =
        new HashMap<Integer, Pair<String, ArrayList<Integer>>>();
    Tuple docTup = null;
    TupleSchema schema = null;
    int autoID;
    Planner p = new Planner();
    Tuple tuple;

    // Prepare compile parameter
    CompileAQLParams compileParam = new CompileAQLParams();
    compileParam.setDataPath(dictPath + ";" + includePath + ";" + udfJarPath);
    compileParam.setInputStr(rewrittenAQL);
    compileParam.setPerformSDM(false);
    compileParam.setPerformSRM(false);

    // Compile
    CompileAQL.compile(compileParam);

    // Instantiate an OperatorGraph object from compiled AQL

    String modulePath = compileParam.getOutputURI();
    OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
        modulePath, null, null);
    Map<String, TupleSchema> outputSchema = og.getOutputTypeNamesAndSchema();

    // variables for finding ids of negative labeled results
    String docLabel;
    HashMap<String, ArrayList<Integer>> negativeResultMap = null;
    HashMap<String, ArrayList<Integer>> positiveResultMap = null;

    boolean getIDfromLabels = false;
    if (labeledResult != null && currentView != null) {
      getIDfromLabels = true;
      negativeResultMap = genLabelResults(labeledResult, LABEL_INCORRECT, null);
      // FIXME: by Bin (Oct 28, 2009): treat all "neutral" as positive for experiments.
      positiveResultMap = genLabelResults(labeledResult, LABEL_CORRECT, LABEL_NEUTRAL);
    }

    int tupleCnt = 0;
    // boolean collectID = false;
    boolean collectPosID = false, collectNegID = false;

    ArrayList<Integer> posIdList = new ArrayList<Integer>();
    ArrayList<Integer> negIdList = new ArrayList<Integer>();
    while (docs.hasNext()) {
      docTup = docs.next();

      docLabel = docs.getDocSchema().spanAcc(Constants.LABEL_COL_NAME).getVal(docTup).getText();
      if (docLabel.startsWith("\\"))
        docLabel = docLabel.substring(1);
      if (!docLabels.contains(docLabel))
        continue;

      Map<String, TupleList> annotations = og.execute(docTup, null, null);

      for (Entry<String, TupleList> entry : annotations.entrySet()) {
        String outputName = entry.getKey();
        TupleList tups = entry.getValue();

        schema = outputSchema.get(outputName);

        // FIXME: problematic: both conditions can be true at the same time.
        if (getIDfromLabels) {
          if (outputName.equals(currentView)) {
            if (negativeResultMap.containsKey(docLabel)) {
              collectNegID = true;
              negIdList = negativeResultMap.get(docLabel);
            } else
              collectNegID = false;
            if (positiveResultMap.containsKey(docLabel)) {
              collectPosID = true;
              posIdList = positiveResultMap.get(docLabel);
            } else
              collectPosID = false;
            tupleCnt = 0;
          } else {
            collectNegID = false;
            collectPosID = false;
          }
        }

        // if (getIDfromLabels) {
        // if (outputName.equals(currentView)
        // && negativeResultMap.containsKey(docLabel)) {
        // collectID = true;
        // tupleCnt = 0;
        // idList = negativeResultMap.get(docLabel);
        // } else
        // collectID = false;
        // }

        // find the correct autoID name
        String idString = "";
        for (int i = 0; i < schema.size(); i++) {
          idString = schema.getFieldNameByIx(i);
          if (idString.startsWith(AUTO_ID))
            break;
        }

        if (!idString.startsWith(AUTO_ID))
          continue;

        FieldGetter<Integer> getInt = schema.intAcc(idString);

        for (TLIter itr = tups.iterator(); itr.hasNext();) {
          tuple = itr.next();

          // if (outputName.equals("PersonPhoneFiltered"))
          // Log.debug("output");

          autoID = getInt.getVal(tuple);

          if (collectNegID && negIdList.contains(tupleCnt) && !negativeIds.contains(autoID)) {
            negativeIds.add(autoID);
          }

          if (collectPosID && posIdList.contains(tupleCnt) && !positiveIds.contains(autoID)) {
            positiveIds.add(autoID);
          }

          tupleCnt++;

          Pair<String, ArrayList<Integer>> provEntry = new Pair<String, ArrayList<Integer>>(
              outputName, extractProvIDs(schema, tuple, autoID));

          if (!oneLevelProvMap.containsKey(autoID))
            oneLevelProvMap.put(autoID, provEntry);
          else
            Log.debug("Error in getAllProvenance: id already exists", autoID);
        }
      }
    }
    return oneLevelProvMap;
  }

  /**
   * Find for each documents users marked, for a given label (Correct, Incorrect, Neutral), id of
   * marked tuple.
   * 
   * @param labeledResults
   * @param label1
   * @param label2 if this is not null, we extract this type of label, in addtion to label1
   * @return a hashMap of results
   */
  public HashMap<String, ArrayList<Integer>> genLabelResults(
      ArrayList<Triple<String, Integer, String>> labeledResults, String label1, String label2) {

    HashMap<String, ArrayList<Integer>> resultMap = new HashMap<String, ArrayList<Integer>>();

    for (Triple<String, Integer, String> triple : labeledResults) {
      if (triple.third.equals(label1) || ((label2 != null) && (triple.third.equals(label2)))) {
        if (resultMap.containsKey(triple.first)) {
          resultMap.get(triple.first).add(triple.second);
        } else {
          ArrayList<Integer> list = new ArrayList<Integer>();
          list.add(triple.second);
          resultMap.put(triple.first, list);
        }
      }
    }

    return resultMap;
  }

  /**
   * Print a list of tuples given in <code>tupleIdList</code>.
   * 
   * @throws Exception
   */
  @SuppressWarnings("unused")
  private HashMap<Integer, String> printTupleList(String rewrittenAQL, DocReader docs,
      String includePath, String dictPath, String udfJarPath, ArrayList<Integer> tupleIdList,
      boolean isHTML) throws Exception {
    HashMap<Integer, String> output = new HashMap<Integer, String>();

    AutoID.resetIDCounter();

    Tuple docTup = null;
    TupleSchema schema = null;
    int autoID;
    Tuple tuple;
    Compiler compiler = new Compiler();

    try {
      // Prepare compile parameter
      CompileAQLParams compileParam = new CompileAQLParams();
      compileParam.setDataPath(dictPath + ";" + includePath + ";" + udfJarPath);
      compileParam.setInputStr(rewrittenAQL);
      compileParam.setPerformSDM(false);
      compileParam.setPerformSRM(false);

      // Compile
      CompileAQL.compile(compileParam);

      // Instantiate an OperatorGraph object from compiled AQL
      String modulePath = compileParam.getOutputURI();

      OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
          modulePath, null, null);

      Map<String, TupleSchema> outputSchema = og.getOutputTypeNamesAndSchema();

      while (docs.hasNext()) {
        docTup = docs.next();
        Map<String, TupleList> annotations = og.execute(docTup, null, null);

        for (Entry<String, TupleList> entry : annotations.entrySet()) {
          String outputName = entry.getKey();
          TupleList tups = entry.getValue();

          schema = outputSchema.get(outputName);

          // find the correct autoID name
          String idString = "";
          for (int i = 0; i < schema.size(); i++) {
            idString = schema.getFieldNameByIx(i);
            if (idString.startsWith(AUTO_ID))
              break;
          }

          if (!idString.startsWith(AUTO_ID))
            continue;

          FieldGetter<Integer> getInt = schema.intAcc(idString);

          for (TLIter itr = tups.iterator(); itr.hasNext();) {
            tuple = itr.next();

            // if (outputName.equals("PersonPhoneFiltered"))
            // Log.debug("output");

            autoID = getInt.getVal(tuple);

            if (tupleIdList.contains(autoID)) {
              String tupleOut = "Tuple " + autoID + ": attributes: ";
              tupleOut += printTupleDetail(tuple, schema, isHTML) + "\n";
              output.put(autoID, tupleOut);
            }
          }
        }
      }
      return output;
    } finally {
      if (compiler != null) {
        compiler.deleteTempDirectory();
      }
    }
  }

  private String printTupleDetail(Tuple tuple, TupleSchema schema, boolean isHTML) {

    HtmlViz viz = new HtmlViz(null);
    StringBuilder sb = new StringBuilder("");
    // columns not to show in the result
    ArrayList<Integer> skipCol = new ArrayList<Integer>();
    // sb.append("<table border=\"1\">\n<tr>\n");
    for (int col = 0; col < schema.size(); col++) {
      String name = schema.getFieldNameByIx(col);
      if (name.startsWith(STMT_TYPE) || name.endsWith(SELECT_ID_END) || name.endsWith(UNION_ID_END)
          || name.equals(CONSOLIDATE_TARGET) || name.startsWith(AUTO_ID)) {
        skipCol.add(col);
      } else {
        sb.append(String.format("%s ", name));
      }
    }
    sb.append("\n");
    if (isHTML)
      sb.append("<br>");

    for (int col = 0; col < schema.size(); col++) {
      if (skipCol.contains(col))
        continue;
      Object val = schema.getCol(tuple, col);

      String fieldStr;
      if (null == val) {
        fieldStr = "null";
      } else {
        fieldStr = val.toString();
      }

      // The string representation of the field may contain HTML;
      // escape any &, < or > symbols in the string.
      String fieldStrEscaped = viz.escapeHTMLSpecials(fieldStr);
      sb.append(String.format("%s ", fieldStrEscaped));
    }
    return sb.toString();
  }

  /**
   * From an output tuple of provenance re-written query, extract the IDs of provenance. Note that
   * the list may be empty if there is no further provenance.
   * 
   * @param schema
   * @param tuple
   * @param id
   * @return an integer list of IDs that correspond to the immediate provenance of the given tuple
   */
  @SuppressWarnings({"all"})
  private ArrayList<Integer> extractProvIDs(TupleSchema schema, Tuple tuple, int id) {
    String stmtType = "";
    String[] fieldNames = schema.getFieldNames();
    FieldGetter<Integer> getInt;
    FieldGetter<Span> getStr;
    FieldGetter<ScalarList> getList;
    ScalarList<Integer> sList = null;
    String[] stmtStrings;

    ArrayList<Integer> provIDs = new ArrayList<Integer>();

    for (int i = 0; i < fieldNames.length; i++) {
      // these are source IDs
      if (fieldNames[i].contains(STMT_TYPE)) {
        getStr = schema.spanAcc(fieldNames[i]);
        stmtType = getStr.getVal(tuple).toString();
        if (stmtType.contains("STMT_TYPE_")) {
          stmtStrings = stmtType.split("STMT_TYPE_");
          stmtType = stmtStrings[1];
          // remove the trailing "'"
          stmtType = stmtType.substring(0, stmtType.length() - 1);
        }
      }

      else if ((fieldNames[i].endsWith(SELECT_ID_END) || fieldNames[i].endsWith(UNION_ID_END))
          && (!fieldNames[i].startsWith("Document_"))) {

        // handle consolidate
        if (fieldNames[i].startsWith(CONSOLIDATE_START)) {
          getList = schema.scalarListAcc(fieldNames[i]);
          sList = getList.getVal(tuple);
          provIDs.addAll(sList);
        }
        // handle others
        else {
          getInt = schema.intAcc(fieldNames[i]);
          provIDs.add(getInt.getVal(tuple));
        }
      }
    }
    // if (provIDs.size() > 0) {
    // System.out.println("found prov info for ID: " + id);
    // }
    if (provIDs.size() > 0)
      return provIDs;
    else
      return null;
  }

  /**
   * Given the input id, output the complete provenance views and ids. Recursively traverse the
   * provenance info we obtained in <code>getAllProenance</code>. Results are stored in
   * <code>provResult</code>, by each input id.
   * 
   * @param id input tuple id that we want to obtain provenance information on
   * @param oneLevelProvMap provenance information for all tuples already obtained.
   * @param result stores the result
   */

  public static void getIdProvenance(int id,
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap,
      HashMap<String, ArrayList<Integer>> result) {

    Pair<String, ArrayList<Integer>> provEntry;
    String viewName;
    String childViewName;

    if ((provEntry = oneLevelProvMap.get(id)) != null) {
      viewName = provEntry.first;

      if (DEBUG)
        Log.debug("Found provenance info for ID " + id + " of type " + viewName);
      if (provEntry.second != null) {
        for (int child : provEntry.second) {

          if (DEBUG)
            Log.debug("Retrieving provenance from the map of %d", child);
          Pair<String, ArrayList<Integer>> childProv = oneLevelProvMap.get(child);

          if (childProv == null)
            continue;

          childViewName = childProv.first;
          if (DEBUG)
            Log.debug(viewName + " " + id + ": source: " + childViewName + " " + child);

          if (!result.containsKey(childViewName)) {
            ArrayList<Integer> children = new ArrayList<Integer>();
            children.add(child);
            result.put(childViewName, children);
          } else {
            // get the idList, add any ID that doesn't already exist
            ArrayList<Integer> children = result.get(childViewName);
            if (!children.contains(child)) {
              children.add(child);
              result.put(childViewName, children);
            }
          }
          // recursively find the whole prov tree.
          if (child != AQLProvenanceRewriter.DEFAULT_DOC_ID)
            getIdProvenance(child, oneLevelProvMap, result);
        }
      }
    } else {
      Log.info("In getIdProvenance: no provenance info for ID " + id);
    }
  }

  /**
   * Starting point of refinement process. Start from the view of the requested tuples. Note that
   * the views to the input are from the original AQL instead of provenance re-written one. Method:
   * Starting from the view of the requested tuple, get a handle on the viewNodes of the original
   * query (referring to the un-rewritten one), start recursively make suggestions.
   */
  private void refineViews(String viewName) throws Exception {

    // for(CreateViewNode view: views){
    // // 1: check if this view is in the provenance
    // viewName = view.getViewName();
    //
    // Log.debug("Suggesting changes for view " + viewName);
    // if (originalNode2RewrittenViewMap.get(view) != null)
    // refineStmt(viewName, view.getBody());
    // else
    // throw new RuntimeException("refineViews: no rewritten view for " +
    // viewName);
    // }
    ViewBodyNode node = rewritten2OriginalCopyMap.get(requestedView);
    refineStmt(viewName, node);
  }

  /**
   * Refine a statement.
   * 
   * @param viewName If the original view has a view name, use it; if it's a subquery, use the
   *        nickname. Never use the view names in the rewritten view.
   * @param stmt
   * @throws Exception
   */
  private void refineStmt(String viewName, ViewBodyNode stmt) throws Exception {

    if (isUntouchable(viewName))
      return;

    // contains the list of tuple IDs that is in the provenance
    HashMap<Integer, ArrayList<Integer>> allProvIDs = getAllProvIDs(stmt);

    if (viewName.equals("Doc") || viewName.equals("D") || isBaseView(viewName)) {
      Log.debug("Reached Doc/DocScan/BaseView. Return.");
      return;
    }
    if (DEBUG)
      Log.debug("Refining view " + viewName);

    // if (viewName.equals("PersonCand"))
    // System.out.println("found");

    if (allProvIDs.size() == 0) {
      Log.debug("No tuple in this view in the provenance tree. Omitting view " + viewName);
      return;
    }
    if (stmt instanceof SelectNode) {
      // SELECT statement
      refineSelect(viewName, (SelectNode) stmt, allProvIDs);

    } else if (stmt instanceof UnionAllNode) {
      refineUnion(viewName, (UnionAllNode) stmt);

    } else if (stmt instanceof MinusNode) {
      refineMinus(viewName, (MinusNode) stmt);

    } else if (stmt instanceof ExtractNode) {
      refineExtract(viewName, (ExtractNode) stmt, allProvIDs);

    } else {
      throw new RuntimeException("Don't know how to rewrite " + stmt);
    }

  }

  /**
   * Find changes about selection statement of the original AQL query. Input: list of views in the
   * rewritten aql that corresponds to this view
   * 
   * @param stmt
   * @param allProvIDs list of all IDs that are in the provenance tree of the given tuple
   * @throws Exception
   */
  private void refineSelect(String viewName, SelectNode stmt,
      HashMap<Integer, ArrayList<Integer>> allProvIDs) throws Exception {

    // 1. where clause
    // 2. consolidate
    // 3. subtraction
    // 4. change element in the from clause (recursive)

    WhereClauseNode wc = stmt.getWhereClause();

    // Temp code for observing various predicates
    // ArrayList<PredicateNode> preds = wc.getPreds();
    // for (PredicateNode pred: preds) {
    //
    // if (pred.getFunc().getFuncName().equals(Follows.FNAME))
    // System.out.println("Change follows");
    // }
    // end temp code

    // FIXME: temporarily commented out all unsupported changes.

    if (viewNames.contains(viewName)) {
      if (wc == null) {
        // add where clause to remove each tuple; each one is considered a
        // change
        // addChangeList(viewName, CHANGE_ADD_WHERE, REMOVETUPLE, allProvIDs);
      } else {
        addChangeList(viewName, CHANGE_WHERE, REMOVETUPLE, allProvIDs);
      }
      addChangeList(viewName, CHANGE_SUBTRACT, REMOVETUPLE, allProvIDs);
    }

    // if(viewName.equals("PersonLastFirstCand"))
    // System.out.println("BLAH");

    // subtraction

    // Handle the CONSOLIDATE clause
    if (stmt.getConsolidateClause() != null) {
      // handleConsolidation();
      // addChangeList(changes, viewName, CHANGE_CONSOLIDATE, allProvIDs);
      handleConsolidation(viewName, stmt, allProvIDs);
    }

    // else
    // addChangeList(viewName, CHANGE_ADD_CONSOLIDATE, REMOVETUPLE,
    // allProvIDs);

    // look at From clause for subqueries
    handleFromListNode(viewName, stmt.getFromList());
    //
    // for (int i = 0; i < stmt.getFromList().size(); i++) {
    // FromListItemNode fromItem = stmt.getFromList().get(i);
    // handleFromListItemNode(viewName, changes, provResult, fromItem);
    // }

  }

  /**
   * Handle an individual FromListItemNode (not the complete from clause)
   */
  @SuppressWarnings("unused")
  private void handleFromListItemNode(String viewName, FromListItemNode fromItem) throws Exception {

    // here we assume that the subquery always have a nickname
    if (fromItem instanceof FromListItemSubqueryNode) {
      viewName = fromItem.getAlias().getNickname();
      refineSubquery(viewName, (FromListItemSubqueryNode) fromItem);
    } else {
      // recursively remove tuples from views in the fromList
      String fromView = fromItem.getExternalName();

      if (fromView.equals("Doc") || isBaseView(fromView))
        return;

      boolean hasView = false;
      for (int id : negativeIds) {
        if (negativeProvResult.get(id).containsKey(fromView)) {
          hasView = true;
          ArrayList<Integer> idList = negativeProvResult.get(id).get(fromView);
          // if (idList != null)
          // addChangeList(id, viewName,
          // CHANGE_RECURSIVE_REMOVE_TUPLE + fromView,
          // REMOVETUPLE, idList);
        }
      }
      if (hasView)
        refineStmt(fromView, rewritten2OriginalCopyMap.get(fromView));
    }
  }

  private void handleFromListNode(String viewName, FromListNode fromList) throws Exception {

    // first, list all changes from this view

    // to mark up if a view is necessary for refining; 1 yes, 0 no.
    ArrayList<Integer> hasView = new ArrayList<Integer>();
    for (int i = 0; i < fromList.size(); i++)
      hasView.add(-1);

    for (int i = 0; i < fromList.size(); i++) {
      FromListItemNode fromItem = fromList.get(i);
      String fromView = fromItem.getExternalName();

      for (int id : negativeIds) {
        if (negativeProvResult.get(id).containsKey(fromView)) {
          @SuppressWarnings("unused")
          ArrayList<Integer> idList = negativeProvResult.get(id).get(fromView);

          if (hasView.get(i) < 0)
            hasView.add(i, 1);

          // if (idList != null)
          // addChangeList(id, viewName,
          // CHANGE_RECURSIVE_REMOVE_TUPLE + fromView,
          // REMOVETUPLE, idList);
        }
        // else {
        // if (hasView.get(i) < 0)
        // hasView.add(i, 0);
        // }
      }
    }

    for (int i = 0; i < fromList.size(); i++) {
      FromListItemNode fromItem = fromList.get(i);
      if (fromItem instanceof FromListItemSubqueryNode) {
        viewName = fromItem.getAlias().getNickname();
        refineSubquery(viewName, (FromListItemSubqueryNode) fromItem);
      } else {
        String fromView = fromItem.getExternalName();
        if (hasView.get(i) > 0) {
          refineStmt(fromView, rewritten2OriginalCopyMap.get(fromView));
        }
      }
    }
  }

  /**
   * Handle extract
   */
  private void refineExtract(String viewName, ExtractNode stmt,
      HashMap<Integer, ArrayList<Integer>> allProvIDs) throws Exception {

    // 1. regex
    // 2. dictionary
    // 3. from clause (recursive)
    // 4. having clause

    if (allProvIDs.size() == 0) {
      Log.info("No suggestions for view " + viewName);
      return;
    }
    ExtractListNode extractList = stmt.getExtractList();
    FromListItemNode fromItem = stmt.getTarget();

    // 1: dict
    if (viewNames.contains(viewName)) {
      if (extractList.getExtractSpec() instanceof DictExNode)
        addChangeList(viewName, CHANGE_DICT, REMOVETUPLE, allProvIDs);
    }

    // 2: regex
    // else if (extractList.getExtractSpec() instanceof RegexExNode)
    // addChangeList(viewName, CHANGE_REGEX, REMOVETUPLE, allProvIDs);
    // else
    // throw new Exception("Don't know how to rewrite extraction.");

    // 3: having clause
    // if no having clause, suggest one; otherwise, suggest changing the
    // having clause
    // HavingClauseNode havingNode = stmt.getHavingClause();
    // if (null != havingNode) {
    // addChangeList(viewName, CHANGE_HAVING, REMOVETUPLE, allProvIDs);
    // } else {
    // addChangeList(viewName, CHANGE_ADD_HAVING, REMOVETUPLE, allProvIDs);
    // }

    // 4: handles from list
    handleFromListItemNode(viewName, fromItem);
  }

  /**
   * Recursively refine the operands of a union
   * 
   * @param viewName
   * @param union
   * @throws Exception
   */
  private void refineUnion(String viewName, UnionAllNode union) throws Exception {

    for (int i = 0; i < union.getNumStmts(); i++) {

      ViewBodyNode unionOperand = union.getStmt(i);
      // frequently unionOperand doesn't have a nickname; name them
      // ViewName_UnionOP_i
      // String unionOpName = viewName + "_UnionOp_" + i;
      String unionOpName = originalNode2RewrittenViewMap.get(unionOperand);

      refineStmt(unionOpName, unionOperand);
    }
  }

  private void refineMinus(String viewName, MinusNode minus) throws Exception {

    /**
     * Algorithm: we remove source tuples from the first operand as usual; Also, we suggest to add
     * tuples to second operand.
     */

    ViewBodyNode firstOp = minus.getFirstStmt();
    @SuppressWarnings("unused")
    HashMap<Integer, ArrayList<Integer>> allProvIDs = getAllProvIDs(firstOp);

    String minusOpNames[] = {"First Minus Operand", "Second Minus Operand"};
    // addChangeList(minusOpNames[1], CHANGE_ADD_TUPLE, ADDTUPLE, allProvIDs);

    refineStmt(minusOpNames[0], firstOp);
  }

  /**
   * Get the list of tuples in the provenance view that corresponds to stmt.
   * 
   * @param stmt a ViewBodyNode in the original query parse tree
   * @return
   * @throws ParseException
   */
  private HashMap<Integer, ArrayList<Integer>> getAllProvIDs(ViewBodyNode stmt)
      throws ParseException {

    HashMap<Integer, ArrayList<Integer>> allProvIDs = new HashMap<Integer, ArrayList<Integer>>();

    // map the stmt back to provenance view names
    String viewName = originalNode2RewrittenViewMap.get(stmt);
    for (int id : negativeIds) {
      if (negativeProvResult.get(id).containsKey(viewName))
        allProvIDs.put(id, negativeProvResult.get(id).get(viewName));
      else {
        if (viewName.equals(requestedView)) {
          if (!allProvIDs.containsKey(id))
            allProvIDs.put(id, new ArrayList<Integer>());
          allProvIDs.get(id).add(id);
        }
        // else, there is no provenance info for that view
      }
    }
    return allProvIDs;
  }

  /**
   * Add a change into the result, corresponding to one tuple to remove from one view
   * 
   * @param changes
   * @param viewName
   * @param changeType
   * @param id
   */
  /*
   * private static void addChangeEntry(ArrayList<Triple<String, String, Integer>> changes, String
   * viewName, String changeType, int id) { Triple<String, String, Integer> changeEntry = new
   * Triple(viewName, changeType, id); changes.add(changeEntry); }
   */
  /**
   * Add a list of changes to the result; list being the list of tuples that can be dropped.
   * 
   * @param changes
   * @param viewName
   * @param changeType
   * @param idList
   */
  private void addChangeList(String viewName, String changeType, String changePurpose,
      HashMap<Integer, ArrayList<Integer>> idsToChange) {

    HLC_COUNT += idsToChange.size();
    ArrayList<Integer> ids = new ArrayList<Integer>();

    for (int i : idsToChange.keySet()) {
      for (int j : idsToChange.get(i))
        if (!ids.contains(j))
          ids.add(j);
    }

    Quad<String, String, String, ArrayList<Integer>> changeEntry =
        new Quad<String, String, String, ArrayList<Integer>>(viewName, changeType, changePurpose,
            ids);
    changes.add(changeEntry);

    // for (int id : idsToChange.keySet()) { // for each output tuple, add the tuples to remove from
    // view viewName
    //
    // for (int changeID : idsToChange.get(id)) {
    //
    // Quad<String, String, String, Integer> changeEntry = new Quad<String, String, String,
    // Integer>(
    // viewName, changeType, changePurpose, changeID);
    //
    // if (!changes.containsKey(id)) {
    // ArrayList<Quad<String, String, String, Integer>> list = new ArrayList<Quad<String, String,
    // String, Integer>>();
    // list.add(changeEntry);
    // changes.put(id, list);
    // }
    // else {
    // cList = changes.get(id);
    // cList.add(changeEntry);
    // // Log.debug("Adding change for id " + id + " : " + changeEntry.first + " " +
    // changeEntry.second + " " +
    // changeEntry.third + " " + changeEntry.fourth);
    // changes.put(id, cList);
    // }
    //
    //
    // }
    // }
  }

  // /**
  // * Add change for a particular requested tuple
  // */
  // private void addChangeList(int id, String viewName, String changeType,
  // String changePurpose, ArrayList<Integer> idsToChange) {
  //
  // ArrayList<Quad<String, String, String, Integer>> cList;
  // for (int changeID : idsToChange) {
  // Quad<String, String, String, Integer> changeEntry = new Quad<String, String, String, Integer>(
  // viewName, changeType, changePurpose, changeID);
  //
  // if (changes.containsKey(id)) {
  // cList = changes.get(id);
  // cList.add(changeEntry);
  // changes.put(id, cList);
  // System.out.println("added HLC " + viewName +" " + changeType);
  // }
  // else {
  // ArrayList<Quad<String, String, String, Integer>> changeList = new ArrayList<Quad<String,
  // String, String,
  // Integer>>();
  // changeList.add(changeEntry);
  // changes.put(id, changeList);
  // }
  // }
  // }

  /**
   * Handle subquery.
   * 
   * @param viewName nickname of the subquery in the original AQL
   * @param subquery node in the parse tree of the original AQL
   * @throws Exception
   */
  private void refineSubquery(String viewName, FromListItemSubqueryNode subquery) throws Exception {
    refineStmt(viewName, subquery.getBody());
    // provViewsToProcess.remove(originalNode2RewrittenViewMap.get(subquery));
  }

  /**
   * Suggest to change consolidation policy Limitation: only works if the consoliation is on
   * ViewType.Column
   * 
   * @param viewName
   * @param stmt
   * @throws Exception
   */
  private void handleConsolidation(String viewName, SelectNode stmt,
      HashMap<Integer, ArrayList<Integer>> allProvIDs) throws Exception {

    ConsolidateClauseNode cons = stmt.getConsolidateClause();
    ColNameNode target = (ColNameNode) cons.getTarget();
    FromListNode fromList = stmt.getFromList();

    for (int i = 0; i < fromList.size(); i++) {
      FromListItemNode fromItem = fromList.get(i);
      String fromView = fromItem.getExternalName();
      if (fromItem.getAlias().getNickname().equals(target.getTabname())) {
        if (consolidateCandidateView == null)
          consolidateCandidateView = fromView;
        else {
          if (!consolidateCandidateView.equals(fromView)) {
            System.out.println("Error: do not support two or more consolidations: " + viewName);
            System.exit(0);
          }
        }
      }
    }
    addChangeList(viewName, CHANGE_CONSOLIDATE, REMOVETUPLE, allProvIDs);
    // ConsolidateClauseNode consolidate = stmt.getConsolidateClause();
  }

  /**
   * generate the complete list of tuple IDs in the provenance
   * 
   * @return
   */
  @SuppressWarnings("unused")
  private ArrayList<Integer> genProvIDs(int id) {
    ArrayList<Integer> result = new ArrayList<Integer>();
    for (ArrayList<Integer> list : negativeProvResult.get(id).values())
      result.addAll(list);
    return result;
  }

  public File getInputAqlFile() {
    return inputAqlFile;
  }

  public void setInputAqlFile(File inputAqlFile) {
    this.inputAqlFile = inputAqlFile;
  }

  public File getOutputFile() {
    return outputFile;
  }

  public void setOutputFile(File outputFile) {
    this.outputFile = outputFile;
  }

  public String getDocLocation() {
    return docLocation;
  }

  public void setDocLocation(String docLocation) {
    this.docLocation = docLocation;
  }

  public static HashMap<Integer, HashMap<String, ArrayList<Integer>>> getReverseMap() {
    return reverseMap;
  }

  public static String getRequestedView() {
    return requestedView;
  }

  public static String getConsolidateCandidateView() {
    if (consolidateCandidateView == null) {
      System.out
          .println("Error! consolidateCandidateView is null. Add consolidation to the annotator.");
      System.exit(0);
    }
    return consolidateCandidateView;
  }

  public static HashMap<Integer, ArrayList<Integer>> getDescendants() {
    return descendants;
  }

  public static void setDescendants(HashMap<Integer, ArrayList<Integer>> descendants) {
    AQLRefine.descendants = descendants;
  }

  // return true if the view can't be refined
  private boolean isUntouchable(String view) {

    String[] untouchableViews =
        props.getProperty(RefinerConstants.REFINER_UNTOUCHABLE_VIEWS_PROP).split("\\s*,\\s*");
    boolean result = false;
    for (String s : untouchableViews) {
      if (s.equals(view)) {
        result = true;
        break;
      }
    }
    return result;
  }

  // return true if the view is a base view
  private boolean isBaseView(String view) {
    return baseViews.contains(view);
  }

  private void addBaseView(String view) {
    baseViews.add(view);
  }

  private void initBaseViews(String[] untouchableViews) {

    if (null != untouchableViews) {
      for (int i = 0; i < untouchableViews.length; i++) {
        addBaseView(untouchableViews[i]);
      }
    }
  }

  public ArrayList<String> getBaseViews() {
    return baseViews;
  }

  public static boolean isDEBUG() {
    return DEBUG;
  }

  public static void setDEBUG(boolean dEBUG) {
    DEBUG = dEBUG;
  }

  // temp functions

  private void compareAnnots(ArrayList<Node> expectedAnnots, ArrayList<Span> actual,
      ArrayList<Integer> autoIDs, ArrayList<Integer> actualPos, ArrayList<Integer> actualNeg,
      ArrayList<Integer> overlapNotEqual) {
    // Span span = new Span();

    int begin = 0;
    int end = 0;
    String text = "";
    String type = "";

    int lastIdx = 0;

    if (expectedAnnots == null) {
      System.out.println("expected is null");
      return;
    }

    for (int i = 0; i < expectedAnnots.size();) {
      Node annotation = expectedAnnots.get(i);

      type = annotation.getNodeName();

      NodeList children = annotation.getChildNodes();

      // read content of expected annotation
      for (int j = 0; j < children.getLength(); j++) {
        Node child = children.item(j);

        if (child.getNodeType() != Node.ELEMENT_NODE)
          continue;

        if (child.getNodeName().equals(BEGIN)) {
          begin = Integer.parseInt(child.getTextContent());
        } else if (child.getNodeName().equals(END)) {
          end = Integer.parseInt(child.getTextContent());
        } else if (child.getNodeName().equals(ANNOTATION)) {
          text = child.getTextContent();
        }
      }

      if (lastIdx >= actual.size()) {
        // if (PRINT_DETAILS){
        // System.out.println("Missing " + type + ": " + text);
        // System.out.println(String.format("Missing %s: %s[%d-%d]", type, text, begin, end));
        // missingAnnotations.add(new SimpleSpan(annotation));

        // counters[which][AFTER]++;
        i++;
      }

      boolean moveOn = true;

      for (int k = lastIdx; k < actual.size() && moveOn;) {
        Span annot = actual.get(k);

        int relationship = comparePosition(annot, begin, end);

        switch (relationship) {
          case EQUAL:
            // System.out.println("Finding " + type + ": " + text);
            if (DEBUG)
              System.out.println(String.format("Finding %s: %s[%d-%d]", type, text, begin, end));
            // actualPos.add(autoIDs.get(k));
            // System.out.println("added autoid " + autoIDs.get(k));
            lastIdx = ++k;
            i++;
            moveOn = false;
            // counters[which][EQUAL]++;

            int cur = k;

            // Yunyao: added on 09/22/09 to consider the case where multiple actual annotations of
            // the same time
            // overlapping with
            // with one expected annotation
            if (cur < actual.size()) {
              boolean stillOverlap = false;

              do {
                annot = actual.get(cur);

                if (comparePosition(annot, begin, end) == OVERLAP_BUT_NOT_EQUAL) {
                  lastIdx = ++k;

                  stillOverlap = true;

                  // System.out.println("Partially wrong annotation " + type + ": " +
                  // annot.getText() + " instead of " +
                  // text);
                  if (DEBUG)
                    System.out.println(String.format(
                        "Partially wrong annotation %s: %s[%d-%d] instead of %s[%d-%d]", type,
                        annot.getText(), annot.getBegin(), annot.getEnd(), text, begin, end));

                  // counters[which][OVERLAP_BUT_NOT_EQUAL]++;
                  overlapNotEqual.add(autoIDs.get(cur));
                  // System.out.println("added autoid " + autoIDs.get(cur));

                }
                /*
                 * Added by Ben to handle case where multiple actual annotations start at the same
                 * position: count the matching tuple, and only move on when no more tuples start at
                 * that position.
                 */
                else if (comparePosition(annot, begin, end) == EQUAL) {
                  // counters[which][EQUAL]++;
                  // actualPos.add(autoIDs.get(cur));
                  // stillOverlap = true;
                  // lastIdx = ++k;
                  // if (PRINT_DETAILS)
                  // System.out.println(String.format("Finding %s: %s[%d-%d]", type, text, begin,
                  // end));
                  System.out.println(
                      String.format("Finding duplicates %s: %s[%d-%d] \n System exiting...", type,
                          text, begin, end));
                  System.exit(1);
                }

                else {
                  stillOverlap = false;
                }
                cur++;

              } while (stillOverlap & cur < actual.size());
            }

            break;
          case BEFORE:
            // System.out.println("Wrong " + type + ": " + annot.getText());
            if (DEBUG)
              System.out.println(String.format("Wrong %s: %s[%d-%d]", type, annot.getText(),
                  annot.getBegin(), annot.getEnd()));
            actualNeg.add(autoIDs.get(k));
            // System.out.println("added autoid " + autoIDs.get(k));
            lastIdx = ++k;
            moveOn = true;
            // counters[which][BEFORE]++;

            break;
          case AFTER:
            // System.out.println("Missing " + type + ": " + text);
            if (DEBUG)
              System.out.println(String.format("Missing %s: %s[%d-%d]", type, text, begin, end));
            // missingAnnotations.add(new SimpleSpan(annotation));
            lastIdx = k;
            i++;
            moveOn = false;
            // counters[which][AFTER]++;
            // actualNeg.add(autoIDs.get(k));

            break;
          case OVERLAP_BUT_NOT_EQUAL:

            // TODO: find out a way to deal the special symbols.
            String annotText = annot.getText();

            // if(INPUT_DOC_TYPE == FILE_TYPE.XML){
            annotText = annotText.replaceAll("amp;", "&");
            // }

            if (annotText.equals(text)) {
              // System.out.println("Finding " + type + ": " + text);
              if (DEBUG)
                System.out.println(String.format("Finding %s: %s[%d-%d]", type, text, begin, end));
              // counters[which][EQUAL]++;
              actualPos.add(autoIDs.get(k));
              // System.out.println("added autoid " + autoIDs.get(k));

            } else {
              // System.out.println("Partially wrong annotation " + type + ": " + annot.getText() +
              // " instead of " +
              // text);
              if (DEBUG)
                System.out.println(
                    String.format("Partially wrong annotation %s: %s[%d-%d] instead of %s[%d-%d]",
                        type, annotText, annot.getBegin(), annot.getEnd(), text, begin, end));
              // counters[which][OVERLAP_BUT_NOT_EQUAL]++;
              overlapNotEqual.add(autoIDs.get(k));
              // System.out.println("added autoid " + autoIDs.get(k));
            }

            lastIdx = ++k;
            cur = k;

            // Yunyao: added on 09/22/09 to consider the case where multiple actual annotations of
            // the same time
            // overlapping with
            // with one expected annotation
            if (cur < actual.size()) {
              boolean stillOverlap = false;

              do {
                annot = actual.get(cur);

                if (comparePosition(annot, begin, end) == OVERLAP_BUT_NOT_EQUAL) {
                  lastIdx = ++k;

                  stillOverlap = true;

                  // System.out.println("Partially wrong annotation " + type + ": " +
                  // annot.getText() + " instead of " +
                  // text);
                  if (DEBUG)
                    System.out.println(String.format(
                        "Partially wrong annotation %s: %s[%d-%d] instead of %s[%d-%d]", type,
                        annot.getText(), annot.getBegin(), annot.getEnd(), text, begin, end));

                  // counters[which][OVERLAP_BUT_NOT_EQUAL]++;
                  overlapNotEqual.add(autoIDs.get(cur));

                }
                /*
                 * Added by Ben to handle case where multiple actual annotations start at the same
                 * position: count the matching tuple, and only move on when no more tuples start at
                 * that position.
                 */
                else if (comparePosition(annot, begin, end) == EQUAL) {
                  // counters[which][EQUAL]++;
                  actualPos.add(autoIDs.get(cur));
                  stillOverlap = true;
                  lastIdx = ++k;
                  if (DEBUG)
                    System.out
                        .println(String.format("Finding %s: %s[%d-%d]", type, text, begin, end));
                  // System.out.println("added autoid " + autoIDs.get(cur));
                }

                else {
                  stillOverlap = false;
                }
                cur++;

              } while (stillOverlap & cur < actual.size());
            }

            i++;

            moveOn = false;

            break;
          default:
            break;
        }
      }
    }

    if (actual.size() > lastIdx) {
      for (int k = lastIdx; k < actual.size(); k++) {
        Span annot = actual.get(k);
        // System.out.println("Wrong annotation" + annot.getType().toPureSpanType() + ": " +
        // annot.getText());
        if (DEBUG)
          System.out.println(String.format("Wrong annotation: %s[%d-%d]", annot.getText(),
              annot.getBegin(), annot.getEnd()));
        // counters[which][BEFORE]++;
        actualNeg.add(autoIDs.get(k));
        // System.out.println("added autoid " + autoIDs.get(k));
      }
    }
  }

  private int comparePosition(Span span, int begin2, int end2) {
    int begin1 = span.getBegin();
    int end1 = span.getEnd();
    int result = 0;

    // System.out.println("begin1 = " + begin1 + " ; begin2" + begin2 + " ; end1 = " + end1 + " ;
    // end2" + end2);

    if ((begin1 + POSITION_SHIFT) == begin2 && (end1 + POSITION_SHIFT) == end2) {
      result = EQUAL;
    } else if (end1 + POSITION_SHIFT < begin2) {
      result = BEFORE;
    } else if (begin1 + POSITION_SHIFT > end2) {
      result = AFTER;
    } else
      result = OVERLAP_BUT_NOT_EQUAL;

    return result;
  }

  public static int getPOSITION_SHIFT() {
    return POSITION_SHIFT;
  }

  public static void setPOSITION_SHIFT(int pOSITIONSHIFT) {
    POSITION_SHIFT = pOSITIONSHIFT;
  }

  public static long getStartTimeMS() {
    return startTimeMS;
  }

  public static void setStartTimeMS(long startTimeMS) {
    AQLRefine.startTimeMS = startTimeMS;
  }

  public void setProperty(String name, String value) {
    props.setProperty(name, value);
  }

  public double getDoubleProperty(String name) {
    return Double.parseDouble(props.getProperty(name));
  }

  public boolean getBooleanProperty(String name) {
    return Boolean.getBoolean(props.getProperty(name));
  }

  public void addBaseViews(ArrayList<String> baseViews) {
    this.baseViews.addAll(baseViews);
  }
}
