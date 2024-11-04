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
import java.util.TreeSet;

import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aog.AOGOpTree;
import com.ibm.avatar.aog.AOGParseTree;
import com.ibm.avatar.aog.AOGParserConstants;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Logical plan node for reading annotations out of an external store like the UIMA CAS or AOM.
 * 
 */
@Deprecated
public class ExternalScanNode extends PlanNode {

  // private String UIMATypeName;

  /** Name of the external type to scan, from AQL's perspective. */
  private String aqlTypeName;

  /**
   * Name of the internal type to scan, from AOG's perspective. This type is always the same, except
   * for the document type, which can be either "Document" or "DocScan" on the AQL side but must be
   * "Document" on the AOG side.
   */
  private String aogTypeName;

  /** Names of AQL columns in the fake AQL view this scan represents. */
  private ArrayList<String> colNames;

  public ExternalScanNode(String typeName, ArrayList<String> colNames) throws ParseException {
    super(new PlanNode[] {});
    this.aqlTypeName = typeName;
    this.colNames = colNames;

    // Convert old-fashioned references to DocScan to the new name of the
    // document type.
    if ("DocScan".equals(typeName)) {
      aogTypeName = AOGParseTree.DOC_TYPE_NAME;
    } else {
      // All other external type names are the same in AQL and AOG.
      aogTypeName = typeName;
    }
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    return new ExternalScanNode(aogTypeName, colNames);
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {

    // Write out the reference to the view.
    printIndent(stream, indent);

    // replaced the SCAN opname with DOCSCAN opname since we can always
    // specify the doc schema now -- eyhung
    stream.printf("%s = %s(\"%s\");\n", StringUtils.toAOGNick(aqlTypeName),
        AOGOpTree.getConst(AOGParserConstants.DOCSCAN_OPNAME), aogTypeName);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("ExternalScanNode\n");
    printIndent(stream, indent + 1);
    stream.printf("AQL Type: %s\n", aqlTypeName);
    stream.printf("AOG Type: %s\n", aogTypeName);
    stream.printf("Schema: %s\n", colNames);
  }

  @Override
  public void getPreds(TreeSet<PredicateNode> preds) {
    // No-op
  }

  @Override
  public void getRels(TreeSet<FromListItemNode> rels) {
    // No-op
  }

}
