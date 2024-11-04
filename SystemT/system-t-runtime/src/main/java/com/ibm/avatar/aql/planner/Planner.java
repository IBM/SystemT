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
package com.ibm.avatar.aql.planner;

import java.io.PrintWriter;
import java.util.ArrayList;

import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.AQLParseTree;
import com.ibm.avatar.aql.CreateDictNode;
import com.ibm.avatar.aql.CreateExternalViewNode;
import com.ibm.avatar.aql.CreateFunctionNode;
import com.ibm.avatar.aql.CreateTableNode;
import com.ibm.avatar.aql.CreateViewNode;
import com.ibm.avatar.aql.DetagDocNode;
import com.ibm.avatar.aql.DetagDocSpecNode;
import com.ibm.avatar.aql.ExtractNode;
import com.ibm.avatar.aql.MinusNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.SelectNode;
import com.ibm.avatar.aql.UnionAllNode;
import com.ibm.avatar.aql.ViewBodyNode;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.logging.Log;

/**
 * Top-level entry point to the AQL planner/query optimizer. Takes in a parse tree and compiles it
 * down to AOG.
 * 
 */
public class Planner {

  /*
   * CONSTANTS
   */

  // debug for require doc cols
  private static boolean debugReq = false;

  /** Should regex strength reduction be enabled by default? */
  public static boolean DEFAULT_RSR = true;

  /*
   * MAJOR COMPONENTS OF THE OPTIMIZER
   */

  /** Implementation of the preprocessing/rewrite phase of the optimizer. */
  private final Preprocessor preproc = new Preprocessor();

  /** Implementation of the plan postprocessing phase of the optimizer. */
  private final Postprocessor postproc = new Postprocessor();

  /** Internal implementation of the Planner that does all the heavy lifting. */
  private PlannerImpl impl;

  /*
   * PUBLIC METHODS
   */

  /**
   * Use the "default" internal implementation.
   */
  public Planner() {
    this(DEFAULT_IMPL_TYPE);
  }

  /** The currently implemented types of annotator planner. */
  public enum ImplType {
    NAIVE, NAIVE_MERGE, MERGE, RANDOM_MERGE, RSE, HASH
  }

  /** Default implementation used if the user doesn't specify one. */
  public static final ImplType DEFAULT_IMPL_TYPE =
      // ImplType.RSE
      ImplType.HASH;

  /**
   * @param type what type of internal implementation to use for the planner.
   */
  public Planner(ImplType type) {
    if (ImplType.NAIVE == type) {
      impl = new NaivePlanner();
    } else if (ImplType.NAIVE_MERGE == type) {
      impl = new NaiveMergePlanner();
    } else if (ImplType.MERGE == type) {
      impl = new MergePlanner();
    } else if (ImplType.RANDOM_MERGE == type) {
      impl = new RandomMergePlanner();
    } else if (ImplType.RSE == type) {
      impl = new RSEPlanner();
    } else if (ImplType.HASH == type) {
      impl = new HashPlanner();
    } else {
      throw new IllegalArgumentException("Invalid implementation type");
    }
  }

  /**
   * @param performRSR TRUE to enable Regex Strength Reduction query rewrite
   */
  public void setPerformRSR(boolean performRSR) {
    preproc.setPerformRSR(performRSR);
  }

  /**
   * @param performSDM TRUE to apply the Shared Dictionary Matching optimization during the
   *        postprocessing phase
   */
  public void setPerformSDM(boolean performSDM) {
    postproc.setPerformSDM(performSDM);
  }

  /**
   * @param performSRM TRUE to apply the Shared Regex Matching optimization during the
   *        postprocessing phase
   */
  public void setPerformSRM(boolean performSRM) {
    postproc.setPerformSRM(performSRM);
  }

  /**
   * Convenience function for compiling directly to a string.
   * 
   * @throws ParseException
   * @throws CompilerException
   */
  public String compileToString(Catalog catalog) throws CompilerException, ParseException {
    AnnotPlan topLevelPlan = compileToPlan(catalog);
    return topLevelPlan.toAOGString(catalog);
  }

  /**
   * Compile the indicated AQL expression to AOG, using the internal compiler specified in the
   * constructor.
   * 
   * @param input expression to compile (pre-parsed)
   * @param output stream where the generated AOG spec goes
   * @throws ParseException
   * @throws CompilerException
   */
  public void compileToStream(Catalog catalog, PrintWriter output)
      throws CompilerException, ParseException {
    AnnotPlan topLevelPlan = compileToPlan(catalog);
    if (debugReq) {
      System.out.printf("top level dump\n");
      topLevelPlan.dump(new PrintWriter(System.out, true), 0);
    }
    topLevelPlan.toAOG(output, catalog);
  }

  public void compileToTAM(Catalog catalog, TAM tam) throws Exception {
    tam.setAog(compileToString(catalog));
  }

  /**
   * Compile down to a query plan; don't generate AOG.
   * 
   * @param catalog AQL catalog, containing information about all views, as well as inputs and
   *        outputs.
   * @return the AQL query plan for generating all required outputs
   * @throws ParseException
   * @throws CompilerException
   */
  public AnnotPlan compileToPlan(Catalog catalog) throws ParseException, CompilerException {

    boolean debug = false;

    impl.setCatalog(catalog);

    // We will walk through the parse tree, generating top-level plans.
    ArrayList<PlanNode> plans = new ArrayList<PlanNode>();

    // Names of external types that we have scans over
    // HashSet<String> externalTypes = new HashSet<String>();

    if (debug) {
      Log.debug("Pass 1:");
    }

    // Pass 1: Prepare the CREATE VIEW nodes for compilation.
    // Imp: This collection will be grown and updated in place by the methods called between Passes
    // 1 and 3
    ArrayList<CreateViewNode> sortedCVNodes = catalog.getRequiredViews();
    // input.getCreateViewNodes(true);

    // Do the same for lookup tables.
    for (CreateTableNode node : catalog.getCreateTableNodes()) {
      // Plan for a lookup table is always the same -- scan the table...
      LookupTablePlanNode scanPlan = new LookupTablePlanNode(node);
      plans.add(scanPlan);

      // Add the plan to the catalog so that views that reference it will
      // compile.
      catalog.cachePlan(node.getTableName(), scanPlan);
    }

    // Yunyao Li: added 02/26/2008 to support HTML detagger
    // add specifications for detagger to the output
    // Begin
    ArrayList<DetagDocNode> detagDocNodes = catalog.getDetagDocNodes();
    for (DetagDocNode node : detagDocNodes) {
      DetagDocPlanNode detagPlan = new DetagDocPlanNode(node);
      plans.add(detagPlan);

      // We need to compute the cost record for the detag plan now because
      // it won't be computed later in compileViews() -- that method only
      // computes cost records for view nodes. Fixes defect .
      impl.addCostRecordToNode(detagPlan);

      // Save the plans so that the cost model can get at them.
      catalog.cachePlan(node.getDetaggedDocName(), detagPlan);
      for (DetagDocSpecNode spec : node.getEntries()) {
        catalog.cachePlan(spec.getTagType().getNickname(), detagPlan);
      }

      // If the detag statement refers to an external type, make sure that
      // a scan over that type will be generated.
      // String targetView = node.getTarget().getTabname();
      // CatalogEntry targetEntry = catalog.lookupView(targetView);
      // if (targetEntry.getIsView() && targetEntry.getIsExternal()) {
      // externalTypes.add(targetView);
      // }
    }
    // End

    // Prepare the external views for compilation.
    for (CreateExternalViewNode node : catalog.getCreateExternalViewNodes()) {
      // Plan for an external view is always the same -- scan the view...
      ExternalViewPlanNode scanPlan = new ExternalViewPlanNode(node);

      impl.addCostRecordToNode(scanPlan);

      plans.add(scanPlan);

      // Add the plan to the catalog so that views that reference it will
      // compile.
      catalog.cachePlan(node.getExternalViewName(), scanPlan);
      // externalTypes.add(node.getExternalViewName());
    }

    if (debug) {
      Log.debug("Pass 2:");
    }

    // make a special node (RequireDocumentPlanNode) for the document
    // by unifying all the require statements, then
    // add the unified document schema to the plans
    RequireDocumentPlanNode docPlan = catalog.getDocumentPlan();

    // Even though this cost record is not useful because one cannot export
    // Document view, add one so that we don't run into problems down the line.
    impl.addCostRecordToNode(docPlan);
    plans.add(docPlan);

    // Pass 2: Run the preprocessor over the views.
    preproc.preProcess(sortedCVNodes, catalog);

    ArrayList<CreateDictNode> createDictNodes = catalog.getCreateDictNodes();

    // Add all dictionaries to the head of the plans.
    // It's important to do this step *after* the preprocessor runs, since
    // the preprocessor may convert some file-based dictionaries to inline
    // ones.
    for (CreateDictNode node : createDictNodes) {
      if (debug) {
        Log.debug("Adding CreateDictPlanNode for '%s' (%s)", node.getDictname(), node);
      }
      plans.add(new CreateDictPlanNode(node));
    }

    ArrayList<CreateFunctionNode> createFunctionNodes = catalog.getCreateFunctionNodes();

    for (CreateFunctionNode node : createFunctionNodes) {
      plans.add(new CreateFunctionPlanNode(node));
    }

    if (debug) {
      Log.debug("Pass 3:");
    }

    // Pass 3: Compile the CREATE VIEW nodes in topologically sorted order.
    // compileViews(catalog, plans, externalTypes, sortedCVNodes);
    // Imp: This collection sortedCVNodes is grown and updated in place by the methods called
    // between Passes 1 and 3
    compileViews(catalog, plans, sortedCVNodes);

    if (debug) {
      Log.debug("Pass 4:");
    }

    // Pass 4: Apply dictionary sharing and some other optimizations in a
    // postprocessing step.
    postproc.postProcess(plans, catalog);

    if (debug) {
      Log.debug("Done compiling.");
    }

    // We used to add jar files to the plan at this point, but now the jars are included in the TAM
    // file itself.

    AnnotPlan ret = new AnnotPlan(plans, catalog.getOutputNamePairs());

    return ret;
  }


  /**
   * The main pass in {@link #compileToPlan(AQLParseTree)}; compiles all the indicated views into
   * plans.
   */
  private void compileViews(Catalog catalog, ArrayList<PlanNode> plans,
      // HashSet<String> externalTypes,
      ArrayList<CreateViewNode> sortedCVNodes) throws ParseException {

    boolean debug = false;

    for (int i = 0; i < sortedCVNodes.size(); i++) {

      CreateViewNode cvn = sortedCVNodes.get(i);

      // There are several kinds of CREATE VIEW statement. Figure out
      // which one we're dealing with.
      ViewBodyNode body = cvn.getBody();

      if (debug) {
        Log.debug("Compiling plan for view '%s'", cvn.getViewName());
      }

      ViewNode viewplan = new ViewNode(cvn.getViewName());

      PlanNode plan;
      try {
        plan = planStmt(body);
      } catch (Exception e) {
        // Mark any exceptions that arise during compilation with the best-guess view name.
        throw new FatalInternalError(e, "Error compiling view %s: %s", cvn.getViewName(),
            TextAnalyticsException.findNonNullMsg(e));
      }

      viewplan.setBodyPlan(plan);

      if (viewplan.getCostRecord() == null) {
        SimpleCostModel costModel = new SimpleCostModel();
        costModel.setCatalog(catalog);

        viewplan.setCostRecord(costModel.computeCostRecord(viewplan));
      }
      catalog.cachePlan(cvn.getViewName(), viewplan);

      plans.add(viewplan);

    }
  }

  /** Recursively traverse a statement, compiling subparts. */
  private PlanNode planStmt(ViewBodyNode stmt) throws ParseException {

    if (stmt instanceof SelectNode) {
      // SELECT statement
      return impl.planSelect((SelectNode) stmt);

    } else if (stmt instanceof UnionAllNode) {

      // UNION ALL of multiple statements. Compile each in turn.
      UnionAllNode union = (UnionAllNode) stmt;

      ArrayList<PlanNode> stuffToUnion = new ArrayList<PlanNode>();

      for (int s = 0; s < union.getNumStmts(); s++) {
        stuffToUnion.add(planStmt(union.getStmt(s)));
      }

      return new UnionAllPlanNode(stuffToUnion);

    } else if (stmt instanceof MinusNode) {

      // Set difference over select statements
      MinusNode minus = (MinusNode) stmt;

      // Compile the two statements that produce the operands.
      ViewBodyNode firstStmt = minus.getFirstStmt();
      ViewBodyNode secondStmt = minus.getSecondStmt();

      return new MinusPlanNode(planStmt(firstStmt), planStmt(secondStmt));
    } else if (stmt instanceof ExtractNode) {

      return impl.planExtract((ExtractNode) stmt);

    } else {
      throw new RuntimeException("Don't know how to compile " + stmt);
    }
  }

}
