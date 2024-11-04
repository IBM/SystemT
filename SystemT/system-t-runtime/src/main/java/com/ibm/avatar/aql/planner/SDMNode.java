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
import java.util.Set;
import java.util.TreeSet;

import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.ColNameNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.RValueNode;
import com.ibm.avatar.aql.SelectListNode;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Plan node that encapsulates shared dictionary matching.
 * 
 */
public class SDMNode extends PlanNode {

  /**
   * Name used for the output column of each output of the SDM dictionary operator; views that
   * consume output tuples will rename this column.
   */
  public static final String OUTPUT_COL_NAME = "__sdm_match";

  /**
   * Dictionary files that we use for shared dict matching, paired with their match modes.
   */
  ArrayList<DictInvocation> dictNames;

  /**
   * The target of the dictionary extraction
   */
  ColNameNode target;

  /**
   * Set of pass through cols
   */
  TreeSet<String> passThroughCols = new TreeSet<String>();

  /**
   * Main constructor.
   * 
   * @param unique set of unique dictionary invocations (dictionary name plus target) to extract
   * @param target column in input tuples containing the target span for extraction
   */
  public SDMNode(Set<DictInvocation> unique, ColNameNode target) {
    super(new PlanNode[0]);

    // Convert the set into a list.
    this.dictNames = new ArrayList<DictInvocation>(unique);
    this.target = target;
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    TreeSet<DictInvocation> unique = new TreeSet<DictInvocation>();
    unique.addAll(dictNames);

    SDMNode ret = new SDMNode(unique, target);

    ret.passThroughCols.addAll(passThroughCols);

    return ret;
  }

  public void addPassThroughCols(SelectListNode selectList) {

    if (null != selectList) {

      for (int ix = 0; ix < selectList.size(); ix++) {
        String columnName = null;
        try {
          if (selectList.get(ix).getValue() instanceof ColNameNode) {
            columnName = ((ColNameNode) selectList.get(ix).getValue()).getColnameInTable();
          } else {
            // Here for StringNode/IntNode/FloatNode
            columnName = selectList.get(ix).getValue().getColName();
          }
        } catch (ParseException e) {
          RValueNode origValue = selectList.get(ix).getOrigValue();
          String lineNumber = (null == origValue.getOrigTok()) ? "Unknown"
              : String.valueOf(origValue.getOrigTok().beginLine);
          FatalInternalError fe = new FatalInternalError(e,
              "Error applying Shared Dictionary Matching (SDM): "
                  + "cannot obtain the value of select list item node '%s' at line %s (file name not available) in module %s",
              origValue, lineNumber, origValue.getModuleName());
          throw fe;
        }

        // If we get here, then we didn't encounter any exception.
        // Add to the list only if we didn't add it before
        if (!passThroughCols.contains(columnName)) {
          passThroughCols.add(columnName);
        }
      }
    }
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("SDMNode\n");
    printIndent(stream, indent + 1);
    stream.printf("Dictionary Names: %s\n", dictNames);
    printIndent(stream, indent + 1);
    stream.printf("Target: %s\n", target.toString());
  }

  /**
   * Deterministic method of creating a unique <b>external</b> temporary nickname for one of the
   * outputs of the SDM dictionary operator.
   * 
   * @param dict name of the dictionary file being applied, paired with match mode
   * @param target the input (table.col) over which the dictionary file is run
   */
  private String makeTempOutputName(DictInvocation dict, ColNameNode target) {

    // Start by stripping out dots and forward slashes from dictName
    String escapedDictName = escapeDictName(dict.dictName);

    return String.format("SDM_OUTPUT_%s_WITH_%s_OVER_%s_%s", escapedDictName, dict.matchMode,
        escapeTargetName(target.getTabname()), target.getColnameInTable());
  }

  /**
   * @param dictName name of a dictionary in the original AQL
   * @return a string that can be used as part of an AQL/AOG variable name for shared dictionary
   *         matching.
   */
  private String escapeDictName(String dictName) {
    String escapedDictName = dictName.replace('/', '_');
    escapedDictName = escapedDictName.replace('.', '_');
    escapedDictName = escapedDictName.replace('-', '_');
    return escapedDictName;
  }

  /**
   * Deterministic method of creating a unique <b>internal</b> temporary nickname for one of the
   * outputs of the SDM dictionary operator.
   * 
   * @param dict parameters for invoking the dictionary
   * @param target the input (table.col) over which the dictionary file is run
   */
  private String makeTempName(DictInvocation dict, ColNameNode target) {

    String escapedDictName = escapeDictName(dict.dictName);

    return String.format("SDM_TMP_%s_WITH_%s_OVER_%s_%s", escapedDictName, dict.matchMode,
        escapeTargetName(target.getTabname()), target.getColnameInTable());
  }

  /**
   * We override the toAOG method, since output renaming is meaningless for a multi-output operator.
   */
  @Override
  public void toAOG(PrintWriter stream, int indent, Catalog catalog) throws Exception {

    printIndent(stream, indent);
    stream.print("# Dicts() operator created through Shared Dictionary Matching\n");

    // Now we can generate the Dicts() operator that outputs to the temp
    // nicknames.

    // Start with the targets list.
    printIndent(stream, indent);
    stream.print("(\n");

    for (int i = 0; i < dictNames.size(); i++) {

      // Generate the name of the temporary nickname for this output.
      String tempNickname = makeTempName(dictNames.get(i), target);

      printIndent(stream, indent + 1);
      if (i == dictNames.size() - 1) {
        // Leave the comma off the last one.
        stream.printf("%s\n", StringUtils.toAOGNick(tempNickname));
      } else {
        stream.printf("%s,\n", StringUtils.toAOGNick(tempNickname));
      }
    }

    printIndent(stream, indent);
    stream.print(") = \n");

    // Then generate the Dicts() operator.
    printIndent(stream, indent);
    stream.print("Dicts(\n");

    // Dictionaries and cases
    printIndent(stream, indent + 1);
    stream.print("(\n");
    for (int i = 0; i < dictNames.size(); i++) {
      printIndent(stream, indent + 2);
      stream.printf("\"%s\" => \"%s\"", dictNames.get(i).dictName, dictNames.get(i).matchMode);

      // No comma after last entry.
      if (i < dictNames.size() - 1) {
        stream.print(",\n");
      } else {
        stream.print("\n");
      }
    }
    printIndent(stream, indent + 1);
    stream.print("),\n");

    // Generate the target column.
    String targetTable = decorateTargetNameWithQuotes(target.getTabname());
    String targetCol = target.getColnameInTable();
    printIndent(stream, indent + 1);
    stream.printf("%s, %s, $%s\n", StringUtils.quoteStr('"', targetCol),
        StringUtils.quoteStr('"', OUTPUT_COL_NAME), targetTable);

    // Close the parens of Dicts()
    printIndent(stream, indent);
    stream.print(");\n\n");

    // Now that we've generated the Dicts() operator, we need to generate
    // the AOG to perform postprocessing (e.g. relabeling columns) on the
    // output of Dicts().
    printIndent(stream, indent);
    stream.print("# Apply labels to outputs of generated Dicts() operator.\n");
    for (int i = 0; i < dictNames.size(); i++) {

      // Generate the name of the temporary nickname for this output,
      // using the same deterministic function we used above.
      String tempNickname = StringUtils.toAOGNick(makeTempName(dictNames.get(i), target));

      // Generate the name for the output.
      // NOTE: This name MUST be kept in sync with what getAOGOutputNick()
      // returns!!!
      String aogNickStr = StringUtils.toAOGNick(getAOGOutputNick(dictNames.get(i)));

      // Name we apply to the output column added by the Dictionaries
      // operator.
      printIndent(stream, indent);

      stream.printf("%s = Project((\"%s\" => \"%s\", " + "\"%s\" => \"%s\" %s), %s);\n", aogNickStr,
          OUTPUT_COL_NAME, OUTPUT_COL_NAME,
          // Pass through input column.
          target.getColnameInTable(), target.getColnameInTable(), createPassThroughColumnList(),
          tempNickname);
    }

    stream.print("\n\n");
  }

  private String createPassThroughColumnList() {
    StringBuilder sb = new StringBuilder();

    for (String colName : passThroughCols) {
      sb.append(", \"" + colName + "\" => \"" + colName + "\"");
    }

    return sb.toString();
  }

  /**
   * @param dict parameters for evaluating a particular dictionary
   * @return name of the artificial view created for the results of this dictionary
   */
  public String getAOGOutputNick(DictInvocation dict) {
    if (dictNames.contains(dict)) {
      return makeTempOutputName(dict, target);
    } else {
      throw new IllegalArgumentException(String.format("Don't know about dictionary '%s'", dict));
    }
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {
    throw new RuntimeException(
        "Not implemented, because we override the top-level toAOG() method.");

  }

  @Override
  public void getPreds(TreeSet<PredicateNode> preds) {
    throw new RuntimeException("Should never be called");
  }

  @Override
  public void getRels(TreeSet<FromListItemNode> rels) {
    throw new RuntimeException("Should never be called");
  }

}
