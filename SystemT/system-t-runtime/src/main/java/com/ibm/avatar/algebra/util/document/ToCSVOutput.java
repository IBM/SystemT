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
package com.ibm.avatar.algebra.util.document;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.opencsv.CSVWriter;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;

/**
 * Utility class to send the output of an extractor on a collection of documents to CSV format. The
 * class takes as input a map of output view names and their schemas and dumps results to CSV files,
 * one per output view.
 * 
 */
public class ToCSVOutput {

  /**
   * Set of CSVWriter objects, indexed by the name of the output view.
   */
  HashMap<String, CSVWriter> outputNameToWriter = new HashMap<String, CSVWriter>();

  /**
   * Set of tuple schemas objects, indexed by the name of the output view.
   */
  HashMap<String, TupleSchema> outputNameToSchema = new HashMap<String, TupleSchema>();

  /**
   * Set of field accessor objects, indexed by the name of the output view.
   */
  @SuppressWarnings("all")
  HashMap<String, FieldGetter[]> outputNameToAccessors = new HashMap<String, FieldGetter[]>();

  /**
   * The output directory where output HTML files go.
   */
  private final File outputDir;

  /**
   * Main constructor.
   * 
   * @param outputViews map of output view name to schema
   * @param outputDir location of directory where the output CSV files go
   * @throws IOException
   */
  public ToCSVOutput(Map<String, TupleSchema> outputViews, File outputDir)
      throws IOException, Exception {

    // Sanity checks to make sure the output directory is in place.
    if (!outputDir.exists())
      throw new Exception(
          String.format("The output directory '%s' does not exist.", outputDir.getCanonicalPath()));

    if (!outputDir.isDirectory())
      throw new Exception(String.format("The output directory '%s' does not point to a directory.",
          outputDir.getCanonicalPath()));

    this.outputDir = outputDir;

    // Get the list of all output views
    Iterator<Entry<String, TupleSchema>> itr = outputViews.entrySet().iterator();

    // Now set up one output buffer object for each output view
    while (itr.hasNext()) {

      Entry<String, TupleSchema> outputView = itr.next();

      String viewName = outputView.getKey();
      TupleSchema viewSchema = outputView.getValue();

      // Remember the schema and memoize accessors
      outputNameToSchema.put(viewName, viewSchema);

      // Memoize accessors to get at values for this schema
      @SuppressWarnings("all")
      FieldGetter[] getters = new FieldGetter[viewSchema.size()];
      for (int ix = 0; ix < viewSchema.size(); ix++) {

        FieldType type = viewSchema.getFieldTypeByIx(ix);
        if (type.getIsFloatType()) {
          getters[ix] = viewSchema.floatAcc(viewSchema.getFieldNameByIx(ix));
        } else if (type.getIsIntegerType()) {
          getters[ix] = viewSchema.intAcc(viewSchema.getFieldNameByIx(ix));
        } else if (type.getIsScalarListType()) {
          getters[ix] = viewSchema.scalarListAcc(viewSchema.getFieldNameByIx(ix));
          // } else if (type.getIsStringType()) {
          // getters[ix] = viewSchema.genericGetter(
          // viewSchema.getFieldNameByIx(ix),
          // FieldType.STRING_TYPE);
        } else if (type.getIsBooleanType()) {
          getters[ix] =
              viewSchema.genericGetter(viewSchema.getFieldNameByIx(ix), FieldType.BOOL_TYPE);
        } else if (type.getIsText()) {
          getters[ix] = viewSchema.textAcc(viewSchema.getFieldNameByIx(ix));
        } else if (type.getIsSpan()) {
          getters[ix] = viewSchema.spanAcc(viewSchema.getFieldNameByIx(ix));
        } else {
          throw new RuntimeException(
              String.format("Unexpected field type %s for view %s and column %s",
                  viewSchema.getFieldTypeByIx(ix), viewName, viewSchema.getFieldNameByIx(ix)));
        }
      }
      outputNameToAccessors.put(viewName, getters);

      // Create the output CSV file to write the content of this view to.
      File outFile = new File(outputDir, String.format("%s.csv", viewName));

      // Create a CSV Writer object for this view and remember it
      CSVWriter writer = new CSVWriter(new FileWriter(outFile));
      outputNameToWriter.put(viewName, writer);

      // Write out the output view schema in the header
      ArrayList<String> fieldNames = new ArrayList<String>(viewSchema.size() + 1);
      // First element is the document label
      fieldNames.add("Document label");
      // The rest are the column names
      fieldNames.addAll(Arrays.asList(viewSchema.getFieldNames()));
      writer.writeNext(fieldNames.toArray(new String[viewSchema.size() + 1]));
    }
  }

  /**
   * Close the CSV Writer objects.
   */
  public void close() throws IOException {

    if (null != outputNameToWriter) {
      for (CSVWriter writer : outputNameToWriter.values()) {
        writer.close();
      }
    }
  }

  /**
   * Write the input annotations to CSV.
   * 
   * @param docLabel
   * @param annots Set of output tuples, indexed by view name
   * @throws Exception if the view name is unknown (i.e., it was not provided at initialization
   *         time)
   */
  public void write(String docLabel, Map<String, TupleList> annots) throws Exception {

    for (String outputTypeName : annots.keySet()) {

      // Get the writer
      CSVWriter writer = outputNameToWriter.get(outputTypeName);

      if (null == writer)
        throw new Exception(String.format(
            "Attempting to write results for unknown output view '%s'. Known output views are: %s",
            outputTypeName, outputNameToWriter.keySet().toArray()));

      // Get the view schema used to initialize the writer
      TupleSchema schema = outputNameToSchema.get(outputTypeName);

      if (null == schema)
        throw new Exception(
            String.format("Schema for output view '%s' is not known. Known schemas are: %s",
                outputTypeName, outputNameToSchema.keySet().toArray()));

      // Get the accessors used to initialize the writer
      @SuppressWarnings("all")
      FieldGetter[] accessors = outputNameToAccessors.get(outputTypeName);

      if (null == accessors)
        throw new Exception(String.format(
            "Accessors for output view '%s' have not been initialized. Known accessors are for views: %s",
            outputTypeName, outputNameToAccessors.keySet().toArray()));

      try {
        addDoc(outputTypeName, docLabel, annots.get(outputTypeName));
      } catch (Throwable e) {
        throw new RuntimeException(String.format("Exception when writing view %s for document %s",
            outputTypeName, docLabel), e);
      }
    }
  }

  /**
   * @return the outputDir
   */
  public File getOutputDir() {
    return outputDir;
  }

  /* PRIVATE METHODS GO HERE */

  /**
   * MEthod that does the heavy lifting. Writes each input tuple to CSV.
   * 
   * @param tuplist a list of output tuples
   * @return a string containing the tuples in CSV format.
   * @throws IOException
   */
  private void addDoc(String outputTypeName, String docLabel, TupleList tuplist)
      throws IOException {

    // Get the writer
    CSVWriter writer = outputNameToWriter.get(outputTypeName);

    // Get the view schema used to initialize the writer
    TupleSchema schema = outputNameToSchema.get(outputTypeName);

    // Get the view schema used to initialize the writer
    @SuppressWarnings("all")
    FieldGetter[] accessors = outputNameToAccessors.get(outputTypeName);

    // For each result tuple
    for (TLIter itr = tuplist.iterator(); itr.hasNext();) {
      Tuple tup = itr.next();

      // Make an array to hold the column values; first value is the
      // document label
      String[] outTuple = new String[schema.size() + 1];
      outTuple[0] = docLabel;

      // For each column value
      for (int col = 0; col < schema.size(); col++) {

        // Get the column value
        Object val = accessors[col].getVal(tup);

        String fieldStr;
        if (null == val) {
          fieldStr = "null";
        } else {
          // Special case Span and Text values so we grab the
          // actual content, not the long representation
          // "Span over..."
          // We do Span and Text separately so as to be compatible with previous versions of SystemT
          // TODO: do the same for ScalarList of Text or Span some
          // other time
          if (val instanceof Span)
            fieldStr = ((Span) val).getText();
          else if (val instanceof Text)
            fieldStr = ((Text) val).getText();
          else
            fieldStr = val.toString();
        }

        // Add the column value to the row
        outTuple[col + 1] = fieldStr;
      }
      // Write the row
      writer.writeNext(outTuple);
    }
    // Flush (all rows for this document)
    writer.flush();
  }

}
