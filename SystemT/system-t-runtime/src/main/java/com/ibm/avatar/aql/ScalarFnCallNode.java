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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.function.agg.Count;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.AggFunc;
import com.ibm.avatar.algebra.function.base.ScalarReturningFunc;
import com.ibm.avatar.algebra.function.predicate.ContainedWithin;
import com.ibm.avatar.algebra.function.predicate.Contains;
import com.ibm.avatar.algebra.function.predicate.ContainsDict;
import com.ibm.avatar.algebra.function.predicate.ContainsDicts;
import com.ibm.avatar.algebra.function.predicate.Equals;
import com.ibm.avatar.algebra.function.predicate.FollowedBy;
import com.ibm.avatar.algebra.function.predicate.FollowedByTok;
import com.ibm.avatar.algebra.function.predicate.Follows;
import com.ibm.avatar.algebra.function.predicate.FollowsTok;
import com.ibm.avatar.algebra.function.predicate.MatchesDict;
import com.ibm.avatar.algebra.function.predicate.Overlaps;
import com.ibm.avatar.algebra.function.predicate.True;
import com.ibm.avatar.algebra.function.scalar.AutoID;
import com.ibm.avatar.algebra.function.scalar.GetCol;
import com.ibm.avatar.algebra.function.scalar.NullConst;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.aog.AOGFuncNode;
import com.ibm.avatar.aog.AggFuncNode;
import com.ibm.avatar.aog.ScalarFuncNode;
import com.ibm.avatar.aog.TableLocatorNode;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.catalog.AbstractRelationCatalogEntry;
import com.ibm.avatar.aql.catalog.AggFuncCatalogEntry;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.ScalarFuncCatalogEntry;
import com.ibm.avatar.aql.catalog.ScalarUDFCatalogEntry;
import com.ibm.avatar.aql.planner.SchemaInferrer;

/** Parse tree for a scalar/aggregate function call (NOT a table function) */
@SuppressWarnings("deprecation")
public class ScalarFnCallNode extends RValueNode {

  public static final String NODENAME = "FunctionCall";

  private final NickNode func;

  private final ArrayList<RValueNode> args;

  /** Flag that is set to true if this item is the aggregate function COUNT(*) */
  protected boolean isCountStar = false;

  /**
   * Main constructor
   * 
   * @param func parse tree node for the function name
   * @param args arguments to the function call
   * @throws ParseException
   */
  public ScalarFnCallNode(NickNode func, ArrayList<RValueNode> args) throws ParseException {
    // set error location info
    super(NODENAME, func.getContainingFileName(), func.getOrigTok());

    this.func = func;
    this.args = args;
  }

  /**
   * Special constructor for when we have Count(*).
   * 
   * @param func
   * @param catalog
   * @throws ParseException
   */
  public ScalarFnCallNode(NickNode func, boolean isCountStar) throws ParseException {
    // set error location info
    super(NODENAME, func.getContainingFileName(), func.getOrigTok());

    this.func = func;
    this.args = new ArrayList<RValueNode>();

    if (false == isCountStar) {
      throw new RuntimeException(
          "This method should never be called with itCountStar set to FALSE");
    }

    // We have enough information to set the function type now; don't need
    // to wait for the preprocessor
    this.isCountStar = true;
  }

  /**
   * Internal constructor for wrapping constants as function calls in select lists.
   * 
   * @param fname name of the constant function (e.g. "GetCol")
   * @param arg single constant argument
   * @param catalog pointer to the AQL catalog
   */
  public ScalarFnCallNode(String fname, RValueNode arg) throws ParseException {
    // set error location info
    super(NODENAME, arg.getContainingFileName(), arg.getOrigTok());

    this.func = new NickNode(fname);
    this.args = new ArrayList<RValueNode>();
    args.add(arg);
  }

  /**
   * Validate this parse tree node against the catalog entry for this function.
   */
  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    // First, we just make sure that the function is actually in the
    // catalog.
    String lookupName = func.getNickname();
    if (getModuleName() != null) {
      lookupName =
          String.format("%s%c%s", getModuleName(), Constants.MODULE_ELEMENT_SEPARATOR, lookupName);
    }
    ScalarFuncCatalogEntry entryScalar = catalog.lookupScalarFunc(lookupName);

    AggFuncCatalogEntry entryAgg = catalog.lookupAggFunc(lookupName);

    // Validate that the function name reference is valid
    if (null == entryScalar && null == entryAgg) {
      errors.add(AQLParserBase.makeException(func.getOrigTok(),
          "Don't know about scalar or aggregate function '%s'", lookupName));
    }

    // Validate function inputs.
    for (RValueNode arg : getArgs()) {
      if (arg instanceof ScalarFnCallNode) {
        ScalarFnCallNode funcArg = (ScalarFnCallNode) arg;
        errors.addAll(funcArg.validate(catalog));
      }
    }

    // For now, just make sure an aggregate function has a single argument,
    // or it's COUNT(*)
    if (entryAgg != null) {
      if (getIsCountStar()) {
        if (!func.getNickname().equals(Count.FNAME))
          errors.add(AQLParserBase.makeException(func.getOrigTok(),
              "Encountered '%s', expected Count(*)", func.getNickname()));
      } else if (args.size() != 1)
        errors.add(AQLParserBase.makeException(func.getOrigTok(),
            "Aggregate function '%s' must have one argument", func.getNickname()));
    }

    // Make sure all scalar functions have more than 0 arguments
    // AutoID, True, and NullConst are the only exceptions.
    if (entryScalar != null) {
      if (args.size() == 0) {
        if ((false == AQLFunc.computeFuncName(True.class).equals(func.getNickname()))
            && (false == AQLFunc.computeFuncName(NullConst.class).equals(func.getNickname()))
            && (false == AQLFunc.computeFuncName(AutoID.class).equals(func.getNickname()))
            && (false == this instanceof ConstAOGFunctNode)) {
          errors.add(AQLParserBase.makeException(func.getOrigTok(),
              "Scalar function '%s' must have at least one argument.", func.getNickname()));
        }
      }
    }

    // Validate function arguments
    for (RValueNode arg : args) {
      List<ParseException> argErrors = arg.validate(catalog);
      if (null != argErrors && argErrors.size() > 0)
        errors.addAll(argErrors);
    }

    return errors;
  }

  public boolean getIsCountStar() {
    return this.isCountStar;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    // SPECIAL CASE: null as <colname>
    if (AQLFunc.computeFuncName(NullConst.class).equals(func.getNickname())) {
      printIndent(stream, indent);
      stream.print("null");
      return;
      // END SPECIAL CASE
    }
    func.dump(stream, indent);
    if (getIsCountStar()) {
      // SPECIAL CASE: Count(*) --> no newlines, and arguments aren't in args field
      stream.print("(*)");
      return;
      // END SPECIAL CASE
    } else if (0 == args.size()) {
      // SPECIAL CASE: No arguments --> no newlines
      stream.print("()");
      return;
      // END SPECIAL CASE
    }

    stream.print("(");
    for (int i = 0; i < args.size(); i++) {
      ((AQLParseTreeNode) args.get(i)).dump(stream, 0);
      if (i < args.size() - 1) {
        stream.print(", ");
      }
    }
    stream.print(")");
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(func.getNickname());
    sb.append("(");
    if (getIsCountStar())
      sb.append("*");
    else
      for (int i = 0; i < args.size(); i++) {
        sb.append(args.get(i).toString());
        if (i < args.size() - 1) {
          sb.append(", ");
        }
      }
    sb.append(")");
    return sb.toString();
  }

  /**
   * Dump the appropriate AOG code for calling this function as a scalar function or selection
   * predicate.
   * 
   * @param stream where to dump the AOG
   * @param indent how far to indent the lines we print out
   * @param catalog AQL catalog for looking up function metadata
   */
  public void toAOG(PrintWriter stream, int indent, Catalog catalog) {
    // toAOGNode() does all the heavy lifting.
    AOGFuncNode aogFuncNode;
    try {
      aogFuncNode = toAOGNode(catalog);
    } catch (ParseException e1) {
      throw new FatalInternalError(e1, "Couldn't generate AOG plan for %s", this);
    }
    try {
      // System.err.printf("Dumping AOG function: %s\n", aogFuncNode);
      aogFuncNode.dump(stream, indent);
    } catch (com.ibm.avatar.aog.ParseException e) {
      // This should never happen.
      throw new RuntimeException(e);
    }

  }

  public String getFuncName() {
    return func.getNickname();
  }

  /**
   * @return the nick node of function name
   */
  public NickNode getFuncNameNode() {
    return func;
  }

  public ArrayList<RValueNode> getArgs() {
    return args;
  }

  @Override
  public void getReferencedCols(TreeSet<String> accum, Catalog catalog) throws ParseException {
    // Make sure that any locator arguments are represented as such.
    convertLocators(catalog);

    // This class doesn't represent column references, but the arguments might, so recurse.
    for (RValueNode arg : args) {
      arg.getReferencedCols(accum, catalog);
    }
  }

  @Override
  public void getReferencedViews(TreeSet<String> accum, Catalog catalog) throws ParseException {
    // System.err.printf ("%s.getReferencedViews()\n", this);

    // Make sure that any locator arguments are represented as such.
    convertLocators(catalog);

    // Scalar functions can't directly reference views, but their children can
    for (RValueNode arg : args) {
      // System.err.printf (" ==> Calling getReferencedViews() on %s (type %s)\n", arg, arg.getClass
      // ().getName ());
      arg.getReferencedViews(accum, catalog);
    }
  }

  /**
   * @return true if this function can be the join predicate for merge join.
   */
  protected boolean hasMergeImpl() {
    return ScalarFuncNode.hasMergeJoinImpl(getFuncName());
  }

  /**
   * @return true if this function can be the join predicate for RSE join.
   */
  protected boolean hasRSEImpl() {
    return ScalarFuncNode.hasRSEJoinImpl(getFuncName());
  }

  /**
   * @return true if this function can be the join predicate for hash join
   */
  protected boolean hasHashImpl() {
    // Hash join only does equality.
    return (AQLFunc.computeFuncName(Equals.class).equals(getFuncName()));
  }

  /**
   * Table of predicates for which a merge join predicate with arguments in reverse order exists,
   * along with the corresponding reversed predicate. Note that we only include the documented
   * "forward" versions of the predicates here.
   * <p>
   * Technically we could also include the reversed predicates like FollowedBy, but we don't want to
   * encourage use of undocumented functions in AQL.
   */
  private static final Class<?>[][] REVERSE_MERGE_PREDS = {{FollowsTok.class, FollowedByTok.class},
      {Follows.class, FollowedBy.class}, {Contains.class, ContainedWithin.class},
      {Overlaps.class, Overlaps.class}, {Equals.class, Equals.class}};

  /**
   * Preprocessed version of {@link #REVERSE_MERGE_PREDS}; maps AQL function names to AQL function
   * names.
   */
  private static final HashMap<String, String> REVERSE_MERGE_PREDS_MAP;
  static {
    REVERSE_MERGE_PREDS_MAP = new HashMap<String, String>();
    for (int i = 0; i < REVERSE_MERGE_PREDS.length; i++) {
      @SuppressWarnings("unchecked")
      String fname = AQLFunc.computeFuncName((Class<? extends AQLFunc>) REVERSE_MERGE_PREDS[i][0]);
      @SuppressWarnings("unchecked")
      String reversedFname =
          AQLFunc.computeFuncName((Class<? extends AQLFunc>) REVERSE_MERGE_PREDS[i][1]);
      REVERSE_MERGE_PREDS_MAP.put(fname, reversedFname);
    }
  }

  /**
   * @return true if there is a version of this predicate that can operate as a merge join predicate
   *         with outer and inner operands reversed
   */
  public boolean hasReversedMergeImpl() {
    return (REVERSE_MERGE_PREDS_MAP.containsKey(getFuncName()));
  }

  /**
   * Note that our implementation of merge join is asymmetric -- the outer and inner operands are
   * treated differently. As a result, a given merge join predicate will only work with a particular
   * input as the outer and a particular input as the inner. Most (though not necessarily all)
   * predicates have a corresponding "reversed" version that swaps the outer and inner operands.
   * Users of this method should check both forward and reverse versions of a given predicate for
   * merge join applicability.
   * 
   * @param outerRels relations represented in the outer of a hypothetical merge join
   * @param innerRels inner relations
   * @param catalog catalog ptr for looking up metadata
   * @return true if this function can act directly as the join predicate between the indicated two
   *         sets of relations.
   * @throws ParseException
   */
  public boolean coversMergeJoin(TreeSet<FromListItemNode> outerRels,
      TreeSet<FromListItemNode> innerRels, Catalog catalog) throws ParseException {

    if (!hasMergeImpl()) {
      return false;
    }

    // Check for locator args anywhere underneath this function call.
    TreeSet<String> locatorNames = new TreeSet<String>();
    getReferencedViews(locatorNames, catalog);
    if (locatorNames.size() > 0) {
      // Disallow merge join if there is a record locator anywhere in the function call tree.
      return false;
    }

    // Currently, the first two arguments to any merge-join-compatible
    // predicate are the outer and inner columns to apply the function to.
    // The predicate can be used for merge join if the first column is from
    // an outer relation and the second is from an inner relation

    // Convert relation names in the outer and inner sides of the join into
    // fully-scoped relation names
    HashSet<String> outerRelNames = getRelNames(outerRels);
    HashSet<String> innerRelNames = getRelNames(innerRels);

    // Figure out what relations the first and second arguments come from.
    TreeSet<String> firstCols = new TreeSet<String>();
    TreeSet<String> secondCols = new TreeSet<String>();
    args.get(0).getReferencedCols(firstCols, catalog);
    args.get(1).getReferencedCols(secondCols, catalog);

    Set<String> firstRefs = getRelNames(firstCols);
    Set<String> secondRefs = getRelNames(secondCols);

    // Check for a "forward" version of the predicate.
    if (outerRelNames.containsAll(firstRefs) && innerRelNames.containsAll(secondRefs)) {
      return true;
    }

    return false;
  }

  /**
   * @param outerRels relations represented in the outer of a hypothetical hash join
   * @param innerRels inner relations
   * @param catalog catalog ptr for looking up metadata
   * @return true if this function can act directly as the join predicate between the indicated two
   *         sets of relations.
   * @throws ParseException
   */
  public boolean coversHashJoin(TreeSet<FromListItemNode> outerRels,
      TreeSet<FromListItemNode> innerRels, Catalog catalog) throws ParseException {
    if (!hasHashImpl()) {
      return false;
    }

    // Check for locator args anywhere underneath this function call.
    TreeSet<String> locatorNames = new TreeSet<String>();
    getReferencedViews(locatorNames, catalog);
    if (locatorNames.size() > 0) {
      // Disallow hash join if there is a record locator anywhere in the function call tree.
      return false;
    }

    // There is currently only one hash-compatible join predicate: Equals
    // The join is only possible if one side of the equality is derived
    // entirely from the outer and the other side is entirely from the
    // inner. We don't care which side is which, since equality is
    // symmetric.

    // Convert relation names in the outer and inner sides of the join into
    // fully-scoped relation names
    HashSet<String> outerRelNames = getRelNames(outerRels);
    HashSet<String> innerRelNames = getRelNames(innerRels);

    // Figure out which relations are referenced in the function calls.
    TreeSet<String> firstCols = new TreeSet<String>();
    TreeSet<String> secondCols = new TreeSet<String>();
    args.get(0).getReferencedCols(firstCols, catalog);
    args.get(1).getReferencedCols(secondCols, catalog);

    Set<String> firstRefs = getRelNames(firstCols);
    Set<String> secondRefs = getRelNames(secondCols);

    if (outerRelNames.containsAll(firstRefs) && innerRelNames.containsAll(secondRefs)) {
      return true;
    }

    return false;
  }

  private HashSet<String> getRelNames(TreeSet<FromListItemNode> outerRels) {
    HashSet<String> outerRelNames = new HashSet<String>();
    for (FromListItemNode node : outerRels) {
      outerRelNames.add(node.getScopedName());
    }
    return outerRelNames;
  }

  /**
   * This method return the list of relations from the given column name
   * 
   * @param referencedCols column names referenced in this function call
   * @return list of relations name
   */
  private static Set<String> getRelNames(Set<String> referencedCols) {
    Set<String> referencedRels = new TreeSet<String>();
    for (String col : referencedCols) {
      int index = col.indexOf(".");
      if (index > 0)
        referencedRels.add(col.substring(0, index));
    }
    return referencedRels;
  }

  /**
   * @param outerRels the relations represented on the outer of a join
   * @param innerRels relations represented on the inner of the join
   * @param catalog pointer to the AQL catalog, for looking up metadata
   * @return true if this function call can be implemented as a basic RSE join
   */
  public boolean coversRSEJoin(TreeSet<FromListItemNode> outerRels,
      TreeSet<FromListItemNode> innerRels, Catalog catalog) throws ParseException {

    // First, check whether this function can serve as an RSE join
    // predicate.
    if (!hasRSEImpl()) {
      return false;
    }

    // Check for locator args anywhere underneath this function call.
    TreeSet<String> locatorNames = new TreeSet<String>();
    getReferencedViews(locatorNames, catalog);
    if (locatorNames.size() > 0) {
      // Disallow RSE join if there is a record locator anywhere in the function call tree.
      return false;
    }

    // Convert relation names in the outer and inner sides of the join into
    // fully-scoped relation names
    HashSet<String> outerRelNames = getRelNames(outerRels);
    HashSet<String> innerRelNames = getRelNames(innerRels);

    // Check to ensure that the first (outer) argument of the join only
    // references base relations from the outer part of the join.
    TreeSet<String> firstCols = new TreeSet<String>();
    TreeSet<String> secondCols = new TreeSet<String>();
    args.get(0).getReferencedCols(firstCols, catalog);
    args.get(1).getReferencedCols(secondCols, catalog);

    Set<String> outerRefs = getRelNames(firstCols);
    Set<String> innerRefs = getRelNames(secondCols);

    if (false == outerRelNames.containsAll(outerRefs)) {
      return false;
    }

    // Currently, we only support RSE join when the inner argument is a
    // direct column reference (e.g. no scalar transformations)
    if (false == (args.get(1) instanceof ColNameNode)) {
      return false;
    }

    // Finally, make sure that the inner function argument is evaluated over
    // the inner relation.
    if (1 != innerRefs.size()) {
      // Sanity check.
      throw new RuntimeException("Inner should only reference one relation");
    }

    if (false == innerRelNames.containsAll(innerRefs)) {
      return false;
    }

    return true;
  }

  /**
   * Convert a (merge-join-compatible) predicate into a version with inner and outer arguments
   * reversed in order.
   */
  public ScalarFnCallNode getReversePred(Catalog c) {

    if (!hasReversedMergeImpl()) {
      return null;
    }

    String funcName = getFuncName();

    NickNode newname = new NickNode(REVERSE_MERGE_PREDS_MAP.get(funcName),
        func.getContainingFileName(), func.getOrigTok());

    // By convention, the constructor for the reversed version of the predicate takes the same
    // arguments as the forward
    // version, except that arguments 0 and 1 are swapped.
    ArrayList<RValueNode> newargs = new ArrayList<RValueNode>();
    newargs.add(args.get(1));
    newargs.add(args.get(0));

    // Remaining arguments (if any) go in the same order as before
    for (int i = 2; i < args.size(); i++) {
      newargs.add(args.get(i));
    }

    try {
      ScalarFnCallNode newFunctionNode = new ScalarFnCallNode(newname, newargs);

      return newFunctionNode;
    } catch (ParseException e) {
      // This should never happen, but just in case...
      throw new RuntimeException(
          String.format("Error reversing '%s' predicate: %s", funcName, e.getMessage()));
    }
  }

  @Override
  public int reallyCompareTo(AQLParseTreeNode o) {
    ScalarFnCallNode other = (ScalarFnCallNode) o;
    int val = func.compareTo(other.func);
    if (val != 0) {
      return val;
    }

    // If we get here, the two functions have the same function name.
    // Compare arguments to break the tie.
    val = args.size() - other.args.size();
    if (val != 0) {
      return val;
    }

    for (int i = 0; i < args.size(); i++) {
      RValueNode myArg = args.get(i);
      RValueNode otherArg = other.args.get(i);
      val = myArg.compareTo(otherArg);
      if (val != 0) {
        return val;
      }
    }

    return val;
  }

  /**
   * Converts from AQL's representation of a function call tree to AOG's representation; this allows
   * us to put all the type-checking and instantiation code on the AOG side.
   * 
   * @param catalog pointer to the AQL catalog, for retrieving function metadata
   * @return the AQL parse rooted at this node, converted to AOG parse tree nodes.
   * @throws ParseException if the function cannot be converted due to a syntax error that wasn't
   *         detected in earlier stages of parsing
   */
  @Override
  public AOGFuncNode toAOGNode(Catalog catalog) throws ParseException {
    String funcName = func.getNickname();

    // SPECIAL CASE: count(*)
    if (getIsCountStar()) {
      // No arguments for count(*) aggregate
      AggFuncNode countNode = new AggFuncNode(funcName, null);
      countNode.setIsCountStar(true);
      return countNode;
    }
    // END SPECIAL CASE

    ScalarFuncCatalogEntry scalarFuncEntry = catalog.lookupScalarFunc(funcName);

    if (scalarFuncEntry != null) {

      // Make sure that any locator arguments are labeled as such.
      convertLocators(catalog);

      // Arguments can be base types (i.e. Integer) or function calls
      ArrayList<Object> convertedArgs = new ArrayList<Object>();

      if (scalarFuncEntry.isConstFn() || (AQLFunc.computeFuncName(GetCol.class).equals(funcName))) {
        // SPECIAL CASE: Built-in constant function, or a column
        // reference; don't "functionize" the arguments any further.
        for (RValueNode arg : args) {
          convertedArgs.add(arg);
        }
        // END SPECIAL CASE
      } else {
        // Normal case: Recursively convert arguments.
        for (RValueNode arg : args) {
          convertedArgs.add(arg.toAOGNode(catalog));
        }
      }

      ScalarFuncNode ret = new ScalarFuncNode(funcName, convertedArgs.toArray());

      if (scalarFuncEntry instanceof ScalarUDFCatalogEntry) {
        // SPECIAL CASE: If this is a UDF, use the fully-qualified function name for AOG generation
        // purposes.
        ScalarUDFCatalogEntry udfEntry = (ScalarUDFCatalogEntry) scalarFuncEntry;
        ret.setCanonicalName(udfEntry.getFqFuncName());
        // END SPECIAL CASE
      }

      return ret;
    }

    // If we get here, no suitable scalar function was found in the catalog. Look for an aggregate
    // function.
    AggFuncCatalogEntry aggFuncEntry = catalog.lookupAggFunc(funcName);
    if (aggFuncEntry != null) {
      // The special case of count(*) is already handled at the top of the method.
      // All other types of aggregates are handled the same. Convert the single argument, then
      // create an AggFuncNode
      // object.
      Object convertedArg = args.get(0).toAOGNode(catalog);

      // Then convert this function to AOG's AggFuncNode object.
      return new AggFuncNode(funcName, convertedArg);
    }

    // If we get here, all of our catalog lookups have failed.
    throw new FatalInternalError("No entry in catalog for function '%s'", funcName);
  }

  /**
   * When a function call is parsed, not enough information is available to distinguish between
   * locators and column references. The parser deals with this lack of information by generating a
   * {@link ColNameNode} parse tree node for both types of reference. This method walks through the
   * arguments of the function and converts any locator arguments to the proper type of parse tree
   * node -- {@link TableLocatorNode}.
   * <p>
   * This method is idempotent. Call it as many times as needed.
   * 
   * @param Catalog pointer to the AQL catalog, for looking up function information
   * @throws ParseException if there is a problem looking up the function or interpreting arguments
   */
  private void convertLocators(Catalog catalog) throws ParseException {
    ScalarFuncCatalogEntry entry = catalog.lookupScalarFunc(func.getNickname());

    // Currently only UDFs can have locator arguments.
    if (entry instanceof ScalarUDFCatalogEntry) {
      ScalarUDFCatalogEntry udfEntry = (ScalarUDFCatalogEntry) entry;
      TupleSchema argSchema = udfEntry.toUDFParams(catalog).getArgsAsSchema();

      // Make sure that the function has the right number of arguments, so we don't need to worry
      // about different
      // numbers of arguments in the code below (or in the methods that call convertLocators())
      if (args.size() != argSchema.size()) {
        // Calling code may not wrap exceptions for us, so add line and file location info here.
        ParseException pe = AQLParserBase.makeException(origTok,
            String.format(
                "Function call '%s' has wrong number of arguments (expected %d, found %d).  "
                    + "Add or remove arguments to match the signature of the function as declared.",
                this.toString(), argSchema.size(), args.size()));

        ExtendedParseException epe =
            new ExtendedParseException(pe, FileUtils.createValidatedFile(getContainingFileName()));
        throw epe;
      }

      for (int i = 0; i < args.size(); i++) {
        FieldType argType = argSchema.getFieldTypeByIx(i);
        RValueNode arg = args.get(i);
        if (argType.getIsLocator()) {
          // Locator argument. Check whether it's still a ColNameNode
          if (arg instanceof ColNameNode) {
            // Argument is still a ColNameNode. Convert (in place) to a TableLocatorNode
            ColNameNode cnn = (ColNameNode) arg;
            NickNode nick =
                new NickNode(cnn.getColName(), cnn.getContainingFileName(), cnn.getOrigTok());
            args.set(i, new com.ibm.avatar.aql.TableLocatorNode(nick));
          } else if (arg instanceof com.ibm.avatar.aql.TableLocatorNode) {
            // Argument already converted; flow through.
          } else {
            throw AQLParserBase
                .makeException(arg.getOrigTok(),
                    String.format(
                        "Argument %d of function %s is not a reference to a table or view.  "
                            + "Please use a record locator for this argument.",
                        i, this.toString()));
          }
        }
      }
    }
  }

  @Override
  public ScalarFnCallNode asFunction() throws ParseException {
    return this;
  }

  /**
   * Verify that this function does not contain any aggregate function calls. This method is used
   * when validating a PatternNode, and should also be used when validating an ExtractNode.
   */
  protected List<ParseException> ensureNoAggFunc(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    // First, we just make sure that the function is actually in the
    // catalog.
    AggFuncCatalogEntry entryAgg = catalog.lookupAggFunc(func.getNickname());

    if (null != entryAgg) {
      errors.add(AQLParserBase.makeException(func.getOrigTok(),
          "Aggregate function '%s' not allowed in EXTRACT clause.", func.getNickname()));
    }

    // Validate function arguments
    for (RValueNode arg : args) {
      if (arg instanceof ScalarFnCallNode) {
        List<ParseException> argErrors = ((ScalarFnCallNode) arg).ensureNoAggFunc(catalog);
        if (null != argErrors && argErrors.size() > 0)
          errors.addAll(argErrors);
      }
    }

    return errors;
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    String functionName = getFuncName();

    AggFuncCatalogEntry aggFunc = catalog.lookupAggFunc(functionName);
    ScalarFuncCatalogEntry scalarFunc = catalog.lookupScalarFunc(functionName);
    // if aggregate function, then it is built-in function; no need to qualify
    if (null == aggFunc) {
      // Only UDFs needs qualification
      if (scalarFunc instanceof ScalarUDFCatalogEntry) {
        // Don't qualify; the qualified name may not be a valid function reference in the context of
        // the current module
        // this.func = new NickNode (func.getOrigTok (), catalog.getQualifiedFuncName
        // (functionName));
      } else if (scalarFunc instanceof ScalarFuncCatalogEntry) {
        // if here, then function reference in hand is built-in scalar function; no need to qualify
      } else {
        throw new RuntimeException(
            String.format("Don't know about scalar or aggregate function '%s'", functionName));
      }
    }

    // Special case: Arguments to these functions are dictionary references, lets qualify them also
    if (ContainsDict.FNAME.equals(functionName) || MatchesDict.FNAME.equals(functionName)) {
      // First argument of MatchesDict() and ContainsDict() function is dictionary name
      StringNode dictNameNode = (StringNode) args.get(0);
      String dictName = dictNameNode.getStr();
      String qualifiedName = catalog.getQualifiedDictName(dictName);
      if (qualifiedName != null) {
        args.set(0, new StringNode(qualifiedName, dictNameNode.getContainingFileName(),
            dictNameNode.getOrigTok()));
      }
    }

    if (ContainsDicts.FNAME.equals(functionName)) {
      // First (n-2) arguments of ContainsDicts are dictionary references
      for (int i = 0; i < args.size() - 2; ++i) {
        StringNode dictNameNode = (StringNode) args.get(i);
        String dictName = dictNameNode.getStr();
        String qualifiedName = catalog.getQualifiedDictName(dictName);
        if (qualifiedName != null) {
          args.set(i, new StringNode(qualifiedName, dictNameNode.getContainingFileName(),
              dictNameNode.getOrigTok()));
        }
      }

      // Second-to-last argument could be either a boolean flag or
      // a dictionary name.
      RValueNode secondToLast = args.get(args.size() - 2);
      if (secondToLast instanceof StringNode) {
        StringNode dictNameNode = (StringNode) secondToLast;
        String qualifiedName = catalog.getQualifiedDictName(dictNameNode.getStr());
        if (qualifiedName != null) {
          args.set(args.size() - 2, new StringNode(qualifiedName,
              dictNameNode.getContainingFileName(), dictNameNode.getOrigTok()));
        }
      }
    }
    // Special case ends

    // Recursively qualify all the arguments too ..
    for (RValueNode rval : args) {
      rval.qualifyReferences(catalog);
    }
  }

  @Override
  public FieldType getType(Catalog catalog, AbstractTupleSchema schema) throws ParseException {
    // Validate the function (and any child function calls) before attempting to generate type info.
    // If the validation
    // fails, stop trying to find the schema and return the first error identified.
    {
      List<ParseException> errors = validate(catalog);
      if (errors.size() > 0) {
        // Throw first error; remaining errors will be intercepted by other validation code that
        // also calls validate()
        throw errors.get(0);
      }
    }

    // We need to convert to a tree of actual function implementation objects in order to compute
    // the return type.
    // For now we don't cache the result of this conversion.
    AOGFuncNode fn = toAOGNode(catalog);

    // We also need the schemas of any views referenced via record locators.
    TreeSet<String> referencedViews = new TreeSet<String>();
    getReferencedViews(referencedViews, catalog);
    AbstractTupleSchema[] referencedSchemas = new AbstractTupleSchema[referencedViews.size()];
    String referencedSchemaNames[] = new String[referencedViews.size()];

    int i = 0;
    for (String viewName : referencedViews) {

      AbstractRelationCatalogEntry ce;
      if (catalog.isValidViewReference(viewName)) {
        ce = (AbstractRelationCatalogEntry) catalog.lookupView(viewName, null);
      } else if (catalog.isValidTableReference(viewName)) {
        ce = (AbstractRelationCatalogEntry) catalog.lookupTable(viewName);
      } else {
        throw AQLParserBase.makeException(getOrigTok(),
            "View %s, referenced in record locator argument to function call %s, does not exist.  "
                + "Ensure that all view references refer to views that are visible in the current module.",
            viewName, this);
      }

      TupleSchema viewSchema = ce.getSchema();

      if (null == viewSchema) {
        throw new FatalInternalError(
            "Catalog has null schema pointer for view or table '%s' (entry type %s).  "
                + "This usually means that type inference is not correctly topologically sorting input views.",
            viewName, ce.getClass().getName());
      } else if (SchemaInferrer.TYPE_INFERENCE_ERROR_SCHEMA == viewSchema) {
        // Type inference was attempted before and failed. Throw an exception so the caller doesn't
        // attempt to continue
        // type inference for a view that depends on something with no schema.
        throw AQLParserBase.makeException(getOrigTok(), "Error determining schema of '%s'.",
            viewName);
      }

      referencedSchemas[i] = viewSchema;
      referencedSchemaNames[i++] = viewName;
    }

    // The AOG parser's type hierarchy is very flat, so the code here needs to do some casts.
    try {
      if (fn instanceof ScalarFuncNode) {
        ScalarFuncNode sfn = (ScalarFuncNode) fn;

        ScalarReturningFunc func = sfn.toScalarFunc(null, catalog);

        func.bind(schema, referencedSchemas, referencedSchemaNames);
        return func.returnType();
      } else if (fn instanceof AggFuncNode) {
        AggFuncNode afn = (AggFuncNode) fn;

        AggFunc func = afn.toFunc(null, catalog);

        func.bind(schema, referencedSchemas, referencedSchemaNames);
        return func.returnType();
      } else {
        throw new FatalInternalError("Don't know how to get return type of %s", fn);
      }
    } catch (Exception e) {
      // Wrap other types of exception in the ParseException that the superclass expects here.
      throw AQLParserBase.makeException(e, getOrigTok(),
          "Error determining return type of expression '%s': %s", fn.toString(), e.getMessage());
    }
  }
}
