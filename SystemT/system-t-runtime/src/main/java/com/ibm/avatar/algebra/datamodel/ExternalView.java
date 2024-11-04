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

import java.io.PrintWriter;

import com.ibm.avatar.algebra.util.data.StringPairList;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aog.AOGOpTree;
import com.ibm.avatar.aog.ParseException;

/**
 * Class that represents an external view.
 * 
 */
public class ExternalView {

  /** Name of the external view in AQL. */
  private String name;

  /** External name of the external view. */
  private String externalName;


  /**
   * The externally-visible schema of the external view. The column order is the same as the
   * original external view definition.
   */
  private TupleSchema schema;

  /**
   * Constructor for use by the AOG parser for creating an external view.
   * 
   * @param viewName the AOG name of the external view
   * @param schemaStrs string representation of the schema, as (name, type) pairs.
   * @param tupleStrs string representation of the tuples
   * @throws ParseException if the external view can't be decoded from the strings
   */
  public ExternalView(String viewName, String externalViewName, StringPairList schemaStrs)
      throws ParseException {

    name = viewName;
    externalName = externalViewName;

    // Convert the string representation of the schema into an actual
    // schema.
    String[] names = new String[schemaStrs.size()];
    FieldType[] types = new FieldType[schemaStrs.size()];

    for (int i = 0; i < names.length; i++) {
      Pair<String, String> elem = schemaStrs.get(i);

      String colName = elem.first;
      String typeName = elem.second;

      names[i] = colName;
      try {
        types[i] = FieldType.stringToFieldType(typeName);
      } catch (com.ibm.avatar.aql.ParseException e) {
        throw new ParseException(
            String.format("Error initializing type for column %s of view %s from type name '%s'",
                colName, viewName, typeName),
            e);
      }
    }


    schema = new TupleSchema(names, types);
    schema.setName(name);


  }


  public TupleSchema getSchema() {
    return schema;
  }

  public String getName() {
    return name;
  }

  public String getExternalName() {
    return externalName;
  }

  /**
   * Convert this external view to its AOG representation
   * 
   * @param writer stream to send output to
   * 
   *        The only allowed types are Text, Span, Integer, Float which is enforced by the AQL
   *        compiler.
   */
  public void dump(PrintWriter writer) {
    // First argument is name.
    writer.printf("CreateExternalView(%s,%s,\n", StringUtils.quoteStr('"', name),
        StringUtils.quoteStr('"', externalName));

    // Second argument is schema.
    AOGOpTree.printIndent(writer, 1);
    writer.print("(");
    for (int i = 0; i < schema.size(); i++) {
      String fname = schema.getFieldNameByIx(i);
      FieldType ftype = schema.getFieldTypeByIx(i);

      String ftypeStr;
      if (ftype.getIsText()) {
        ftypeStr = "Text";
      } else if (ftype.getIsSpan()) {
        ftypeStr = "Span";
      } else if (FieldType.INT_TYPE.equals(ftype)) {
        ftypeStr = "Integer";
      } else if (FieldType.FLOAT_TYPE.equals(ftype)) {
        ftypeStr = "Float";
      } else {
        throw new RuntimeException(
            String.format("Don't know how to print out field type '%s'", ftype));
      }

      writer.printf("%s => %s", StringUtils.quoteStr('"', fname),
          StringUtils.quoteStr('"', ftypeStr));

      if (i < schema.size() - 1) {
        writer.print(", ");
      }
    }

    // Close the schema paren.
    writer.print(")\n");

    // Close the top-level paren.
    writer.print(");\n");
  }
}
