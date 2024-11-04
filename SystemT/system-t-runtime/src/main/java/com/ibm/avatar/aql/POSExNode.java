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
import java.util.List;

import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.CatalogEntry;
import com.ibm.avatar.aql.catalog.TableCatalogEntry;

/** Parse tree node for a part of speech extraction specification. */
public class POSExNode extends ExtractionNode {

  /** Name of the mapping table column that contains a low-level POS tag. */
  public static final String TAG_COL_NAME = "Tag";

  /**
   * Name of the mapping table column with a high-level "part of speech" name.
   */
  public static final String POS_COL_NAME = "BaseTag";

  /**
   * Name of the mapping table column that contains a comma-delimited list of flags associated with
   * a part of speech name.
   */
  public static final String FLAGS_COL_NAME = "FlagStr";

  /**
   * Strings identifying parts of speech to extract. These strings correspond to the actual string
   * tags that the POS tagger produces, not any shorthand the user has defined.
   */
  private ArrayList<String> lowLevelPOSStrs;

  private LangCode language;

  private final ArrayList<StringNode> posStrs;

  private final ArrayList<StringNode> flagStrs;

  private final StringNode langCodeStr;

  private final NickNode mappingTableName;

  private final ColNameNode targetName;

  // private NickNode outputCol;

  public POSExNode(ArrayList<StringNode> posStrs, ArrayList<StringNode> flagStrs,
      StringNode langCodeStr, NickNode mappingTableName, ColNameNode targetName, NickNode outputCol,
      String containingFileName, Token origTok) throws ParseException {
    super(targetName, outputCol, containingFileName, origTok);

    this.posStrs = posStrs;
    this.flagStrs = flagStrs;
    this.langCodeStr = langCodeStr;
    this.mappingTableName = mappingTableName;
    this.targetName = targetName;
    // this.outputCol = outputCol;

  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    try {
      lowLevelPOSStrs = decodePOS(posStrs, flagStrs, mappingTableName, catalog);
    } catch (ParseException e1) {
      errors.add(e1);
    }

    if (null == langCodeStr) {
      // No language code provided -- use the default
      language = LangCode.DEFAULT_LANG_CODE;
    } else {
      try {
        language = LangCode.strToLangCode(langCodeStr.getStr());
      } catch (IllegalArgumentException e) {
        errors.add(AQLParserBase.makeException(langCodeStr.getOrigTok(),
            "Don't understand language code '%s'", langCodeStr.getStr()));
      }
    }

    // TODO: Use the tokenizer information in the Catalog to determine
    // whether the part of speech tag information is valid, and throw an
    // exception if it is not.

    return errors;
  }

  /**
   * Turns a POS specification into a set of low-level POS tags to fetch.
   * 
   * @param posStrs strings identifying parts of speech
   * @param flagStrs flags associated with each string
   * @param mappingTableName name of the table used for mapping high-level tags to low-level ones,
   *        or null to use low-level tags directly
   * @param catalog pointer to the catalog, for fetching lookup tables
   * @return a list of low-level POS tags
   */
  private static ArrayList<String> decodePOS(ArrayList<StringNode> posStrs,
      ArrayList<StringNode> flagStrs, NickNode mappingTableName, Catalog catalog)
      throws ParseException {
    ArrayList<String> ret = new ArrayList<String>();

    if (null == mappingTableName) {
      // No mapping table; POS strings should map directly to low-level
      // tags.
      for (int i = 0; i < posStrs.size(); i++) {
        // Make sure that the user didn't try to pass in flags.
        StringNode flagStr = flagStrs.get(i);
        if (null != flagStr) {
          throw AQLParserBase.makeException(
              "POS flags specified, " + "but no POS mapping table provided" + " for decoding them.",
              flagStr.getOrigTok());
        }

        StringNode tagStrNode = posStrs.get(i);

        // The tags string may be a comma-delimited list.
        String tagsStr = tagStrNode.getStr();

        String[] tags = StringUtils.split(tagsStr, ',');
        for (String tag : tags) {
          ret.add(StringUtils.chomp(tag).toString());
        }
      }

    } else {
      // Mapping table provided; fetch the table, and map strings and
      // (optional) flags to low-level tags.
      CatalogEntry entry =
          catalog.lookupView(mappingTableName.getNickname(), mappingTableName.getOrigTok());

      if (false == (entry instanceof TableCatalogEntry)) {
        throw AQLParserBase.makeException(mappingTableName.getOrigTok(), "%s is not a static table",
            mappingTableName.getNickname());
      }

      TableCatalogEntry tabEntry = (TableCatalogEntry) entry;

      // throw exception if mapping table is imported or external
      if (tabEntry.isImported()) {
        throw AQLParserBase.makeException(mappingTableName.getOrigTok(),
            "Mapping table %s is imported.  Parts of speech extraction using an imported mapping table is not supported. Provide a mapping table which is defined within the current module.",
            mappingTableName.getNickname());
      } else if (tabEntry.getParseTreeNode().getIsExternal()) {
        throw AQLParserBase.makeException(mappingTableName.getOrigTok(),
            "Mapping table %s is external.  Parts of speech extraction using an external mapping table is not supported. Provide a mapping table which is defined within the current module.",
            mappingTableName.getNickname());
      }
      ArrayList<String> colNames = tabEntry.getColNames();

      // Decode the schema of the mapping table.
      int flagsColIx = -1, tagColIx = -1, posColIx = -1;

      for (int i = 0; i < colNames.size(); i++) {
        String name = colNames.get(i);

        // Don't be strict about case.
        if (FLAGS_COL_NAME.equalsIgnoreCase(name)) {
          flagsColIx = i;
        } else if (POS_COL_NAME.equalsIgnoreCase(name)) {
          posColIx = i;
        } else if (TAG_COL_NAME.equalsIgnoreCase(name)) {
          tagColIx = i;
        }
      }

      if (-1 == flagsColIx || -1 == tagColIx || -1 == posColIx) {
        throw AQLParserBase.makeException(mappingTableName.getOrigTok(),
            "Part of speech mapping table must have columns " + "called '%s', '%s', and '%s'",
            FLAGS_COL_NAME, POS_COL_NAME, TAG_COL_NAME);
      }

      // Now we can make a pass through the mapping table for each
      // combination of POS name and flags.
      for (int i = 0; i < posStrs.size(); i++) {
        // Decode flags, if any.
        StringNode flagStr = flagStrs.get(i);
        String[] flags;
        if (null == flagStr) {
          flags = new String[0];
        } else {
          flags = StringUtils.split(flagStr.getStr(), ',');

          // Strip off any stray whitespace.
          for (int j = 0; j < flags.length; j++) {
            flags[j] = StringUtils.chomp(flags[j]).toString();
          }
        }

        StringNode posStrNode = posStrs.get(i);

        String posStr = posStrNode.getStr();

        // POS string may contain multiple entries
        String[] poses = StringUtils.split(posStr, ',');

        for (String rawPos : poses) {
          String pos = StringUtils.chomp(rawPos).toString();

          // Iterate through the table, marking all the low-level tags
          // the user has implicitly specified.
          ArrayList<Object[]> tups = tabEntry.getParseTreeNode().tupVals;

          if (tups == null) {
            throw AQLParserBase.makeException(mappingTableName.getOrigTok(),
                "No declared tuples found for mapping table %s.", mappingTableName.getNickname());
          }

          for (Object[] tup : tups) {

            // Pull out the relevant columns of the tuple.
            String curTagStr = (String) tup[tagColIx];
            String curPosStr = (String) tup[posColIx];
            String curFlagStr = (String) tup[flagsColIx];

            if (curPosStr.equals(pos)) {
              // The part of speech field of this entry in the
              // table matches what the user specified.
              // Now we need to verify that all the flags required
              // are present.
              boolean flagsMatch = true;

              String[] curFlags = StringUtils.split(curFlagStr, ',');
              for (String flag : flags) {
                boolean thisFlagMatches = false;

                // Test whether the specified flag is one of the
                // flags that appears in this row of the table.
                for (String curFlag : curFlags) {
                  curFlag = StringUtils.chomp(curFlag).toString();
                  if (curFlag.equals(flag)) {
                    thisFlagMatches = true;
                  }
                }

                if (false == thisFlagMatches) {
                  flagsMatch = false;
                }
              }

              if (flagsMatch) {
                // We passed all the flag checks.
                ret.add(curTagStr);
              }
            }

          }

        }

      }
    }

    return ret;
  }

  /**
   * @return number of different part of speech specs attached to this statement.
   */
  public int getNumTags() {
    return lowLevelPOSStrs.size();
  }

  // public StringNode getPosStr(int ix) {
  // return lowLevelPOSStrs.get(ix);
  // }

  public String getPosStr(int ix) {
    return lowLevelPOSStrs.get(ix);
  }

  public LangCode getLanguage() {
    return language;
  }

  /**
   * Returns NickNode for mapping table name
   * 
   * @return mappingTableName nick node
   */
  public NickNode getMappingTableNameNode() {
    return mappingTableName;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    stream.print(1 == lowLevelPOSStrs.size() ? "part_of_speech " : "parts_of_speech ");
    for (int i = 0; i < lowLevelPOSStrs.size(); i++) {
      if (i > 0) {
        stream.print(" and ");
      }

      stream.printf("%s", StringUtils.quoteStr('\'', lowLevelPOSStrs.get(i), true, true));
    }

    stream.printf("\n");
    printIndent(stream, indent);
    stream.printf("with language '%s'", getLanguage());

    stream.printf(" on %s as ", super.getTargetName().getColName());
    getOutputCol().dump(stream, 0);
    stream.printf("\n");
  }

  public NickNode getOutputCol() {
    return super.getOutputCols().get(0);
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    POSExNode other = (POSExNode) o;
    int val = super.compareTarget(other);

    if (0 != val) {
      return val;
    }

    int firstLowLevelPOSStrsSize = 0, secondLowLevelPOSStrsSize = 0;

    if (lowLevelPOSStrs != null) {
      firstLowLevelPOSStrsSize = lowLevelPOSStrs.size();
    }

    if (other.lowLevelPOSStrs != null) {
      secondLowLevelPOSStrsSize = other.lowLevelPOSStrs.size();
    }

    val = firstLowLevelPOSStrsSize - secondLowLevelPOSStrsSize;

    if (0 != val) {
      return val;
    }
    for (int i = 0; i < firstLowLevelPOSStrsSize; i++) {
      val = lowLevelPOSStrs.get(i).compareTo(other.lowLevelPOSStrs.get(i));
    }
    return val;
  }

  @Override
  public int getNumInputCopies() {
    // We do everything with one pass now.
    return 1;
    // return getNumPoses();
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    targetName.qualifyReferences(catalog);
  }
}
