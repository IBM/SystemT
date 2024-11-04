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
package com.ibm.avatar.algebra.oldscan;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextGetter;
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.algebra.util.document.JsonFileReader;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.DocReaderException;
import com.ibm.avatar.logging.Log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

/**
 * Operator that reads documents from files in JSON format. These can contain external view
 * information.
 * 
 */
public class JsonFileScan extends DocScanInternal {

  private final boolean debug = false;

  /** Expected titles of records in JSON file */
  public static final String EXT_VIEW_RECORD_NAME = "ExternalViews";

  /** Expected field name indicating the begin offset of a span in the JSON file */
  public static final String BEGIN_OFFSET_FIELD_NAME = "begin";

  /** Expected field name indicating the end offset of a span in the JSON file */
  public static final String END_OFFSET_FIELD_NAME = "end";

  /**
   * Expected field name indicating the source text field of the view Document that the span is over
   * in the JSON file
   */
  public static final String DOCREF_FIELD_NAME = "docref";

  /** The file to scan */
  private final File JSONfile;

  /** Record counter for clearer error messages */
  private int recordCount = 1;

  /**
   * Map of external view's AQL name (name defined thru 'create external view') vs Schema of the
   * view. For modular AQL's, the AQL name will be qualified with module name.
   */
  private Map<String, TupleSchema> extViewSchemas;

  /**
   * Map of an external view's AQL name to the external name (defined through external_name clause).
   * For modular AQL's, the AQL name is qualified with module name.
   */
  private Map<String, String> aqlNameToExternalName;

  /** Map of all external view tuples associated with document */
  private Map<String, TupleList> extViewTups;

  /** Map of all setters associated with the doc tuple schema */
  protected Map<String, FieldSetter<?>> docSetters;

  /** Map of all getters associated with the doc tuple schema */
  protected Map<String, FieldGetter<?>> docGetters;

  /** Map of all setters associated with the external view schemas */
  protected Map<String, Map<String, FieldSetter<?>>> allExtViewSetters;

  /** Map of all getters associated with the external view schemas */
  protected Map<String, Map<String, FieldGetter<?>>> allExtViewGetters;

  /** Reader that does most of the work in parsing the JSON file */
  private final JsonFileReader in;

  /** example of proper JSON format, displayed on reading bad JSON input */
  private static final String exampleExtView = "Example:\n"
      + "... \"ExternalViews\": { \"viewName\": [{ \"fieldName1\":\"value1\", \"fieldName2\":\"value2\" }] } ...";

  /**
   * Convenience constructor for reading from just one file or directory.
   * 
   * @throws FileNotFoundException
   */
  public JsonFileScan(String JSONfileName, TupleSchema docSchema,
      Map<Pair<String, String>, TupleSchema> extViewNameVsSchemas) throws Exception {
    this(FileUtils.createValidatedFile(JSONfileName), docSchema, extViewNameVsSchemas);
  }

  /**
   * Constructor for scanning a file in JSON format
   * 
   * @param JSONfile name of file in JSON format
   * @param docSchema expected doc schema
   * @param extViews expected schemas for external views
   * @throws Exception
   */
  public JsonFileScan(File JSONfile, TupleSchema docSchema,
      Map<Pair<String, String>, TupleSchema> extViewNameVsSchemas) throws Exception {
    this.JSONfile = JSONfile;
    this.docSchema = docSchema;

    // External views are optional - we can have json doc without external view records
    if (null != extViewNameVsSchemas)
      initInternalMaps(extViewNameVsSchemas);

    if (!JSONfile.exists()) {
      throw new FileNotFoundException("JSON file " + this.JSONfile.getPath() + " not found");
    }

    in = new JsonFileReader(JSONfile);

  }

  @Override
  /**
   * Gets the next document tuple to process. Also, sets the {@link externalViews} variable to the
   * external views of this tuple
   *
   * @return next document tuple
   */
  protected Tuple getNextDoc(MemoizationTable mt) throws Exception {

    // Get the next document record
    JsonNode docRecord = in.getNextTup();

    if (docRecord != null) {

      // Populate the fields of the document tuple corresponding to this doc record
      Tuple docTup = parseJsonObject(docRecord);

      // If external view schemas are defined, populate the external views tuples
      // corresponding to this doc record
      if (extViewSchemas == null || extViewSchemas.isEmpty()) {
        extViewTups = null;
      } else {
        extViewTups = parseExternalViews(docRecord, docTup);
      }

      recordCount++;

      return docTup;
    } else {
      return null;
    }
  }

  @Override
  protected void startScan(MemoizationTable mt) throws Exception {
    // Scan was started in the constructor.
  }

  @Override
  protected void reallyCheckEndOfInput(MemoizationTable mt) throws Exception {
    if (in.endOfInput()) {
      mt.setEndOfInput();
    }

  }

  @SuppressWarnings("unchecked")
  @Override
  protected AbstractTupleSchema createOutputSchema() {
    docSchema.setName(Constants.DEFAULT_DOC_TYPE_NAME);

    docSetters = new HashMap<String, FieldSetter<?>>();
    docGetters = new HashMap<String, FieldGetter<?>>();

    cacheAccessors(docSetters, docGetters, docSchema);

    if (extViewSchemas != null) {
      allExtViewSetters = new HashMap<String, Map<String, FieldSetter<?>>>();
      allExtViewGetters = new HashMap<String, Map<String, FieldGetter<?>>>();

      for (Map.Entry<String, TupleSchema> extViewEntry : extViewSchemas.entrySet()) {
        Map<String, FieldSetter<?>> extViewAcc = new HashMap<String, FieldSetter<?>>();
        Map<String, FieldGetter<?>> extViewGetter = new HashMap<String, FieldGetter<?>>();

        cacheAccessors(extViewAcc, extViewGetter, extViewEntry.getValue());

        allExtViewSetters.put(extViewEntry.getKey(), extViewAcc);
        allExtViewGetters.put(extViewEntry.getKey(), extViewGetter);
      }
    }

    // Initialize text field getters and setters. Subclasses that override this
    // method should be sure to provide their own way of setting the
    // attributes of document tuples!

    // iterate through all getters and test for type Text.
    // If a TextGetter is found, add to the list of text getters
    String[] schemaFieldNames = docSchema.getFieldNames();
    for (String fieldName : schemaFieldNames) {
      FieldType fieldType = docSchema.getFieldTypeByName(fieldName);
      if (fieldType.getIsText()) {
        docTextGetters.add((FieldGetter<Text>) docGetters.get(fieldName));
      }
    }

    // initialize the default schema's setter
    if (docSchema.containsField(Constants.DOCTEXT_COL)) {
      docTextAcc = docSchema.textSetter(Constants.DOCTEXT_COL);
    } else {
      docTextAcc = null;
    }

    return docSchema;
  }

  /**
   * Helper function to iterate through each field of the passed-in schema, create an accessor for
   * it, and then cache it for later retrieval
   * 
   * @param setters The corresponding setter cache (document or external view)
   * @param getters The corresponding getter cache
   * @param schema The schema to generate accessors for
   */
  private void cacheAccessors(Map<String, FieldSetter<?>> setters,
      Map<String, FieldGetter<?>> getters, AbstractTupleSchema schema) {

    // get the name of the schema and identify whether we are working on a doc or external view
    // schema
    String schemaName = schema.getName();
    boolean isDocSchema = schemaName.equals(Constants.DEFAULT_DOC_TYPE_NAME);

    String[] schemaFieldNames = schema.getFieldNames();

    // iterate through each field of the schema and cache an accessor for it
    for (String fieldName : schemaFieldNames) {
      FieldType fieldType = schema.getFieldTypeByName(fieldName);
      if (fieldType.getIsIntegerType()) {
        setters.put(fieldName, schema.intSetter(fieldName));
        getters.put(fieldName, schema.intAcc(fieldName));
      } else if (fieldType.getIsFloatType()) {
        setters.put(fieldName, schema.floatSetter(fieldName));
        getters.put(fieldName, schema.floatAcc(fieldName));
      } else if (fieldType.getIsText()) {
        setters.put(fieldName, schema.textSetter(fieldName));
        getters.put(fieldName, schema.textAcc(fieldName));
      }
      // Boolean is only valid for document schemas, not external view schemas
      else if (fieldType.getIsBooleanType() && isDocSchema) {
        setters.put(fieldName, schema.genericSetter(fieldName, FieldType.BOOL_TYPE));
        getters.put(fieldName, schema.genericGetter(fieldName, FieldType.BOOL_TYPE));
      }
      // Span is only valid for external view schemas, not document schemas
      else if (fieldType.getIsSpan() && !isDocSchema) {
        setters.put(fieldName, schema.spanSetter(fieldName));
        getters.put(fieldName, schema.spanAcc(fieldName));
      } else {
        String schemaType = isDocSchema ? "document" : "external view";
        throw new RuntimeException(
            makeErrorHeader() + String.format("Invalid type '%s' in field '%s' of %s schema %s",
                fieldType.toString(), fieldName, schemaType, schemaName));
      }
    }
  }

  /**
   * Reads doc tuple information from the JsonNode.
   * 
   * @param obj The JsonNode containing a doc tuple and its corresponding external views
   * @return the document tuple
   * @throws IOException
   */
  private Tuple parseJsonObject(JsonNode obj) throws DocReaderException {

    Tuple tup = docSchema.createTup();

    // populate the document tuple according to the input schema
    // if the document record does not contain a field corresponding to
    // the input schema, throw an exception
    for (int i = 0; i < docSchema.size(); i++) {
      String fieldName = docSchema.getFieldNameByIx(i);
      FieldType ft = docSchema.getFieldTypeByName(fieldName);
      JsonNode fieldObject = obj.get(fieldName);

      if (fieldObject == null) {
        throw new DocReaderException(makeErrorHeader(), String.format(
            "JSON record at this line does not contain an attribute for the required field named '%s'. Provide a non-null value of type '%s' for this required field.",
            fieldName, ft.getIsText() ? FieldType.TEXT_TYPE.getTypeName() : ft.getTypeName()));
      }

      populateTupleWithObject(tup, docSchema, fieldName, docSetters.get(fieldName), fieldObject,
          null);
    }

    if (debug) {
      Log.debug("Doc tuple created with contents:");
      Log.debug(tup.toString());
    }

    return tup;
  }

  /**
   * Reads external view information from the JsonNode. All of the external views associated with
   * this object are read and stored in a map for later retrieval.
   * 
   * @param docRcd the JSON object containing a doc tuple and its external views
   * @param docTup the Tuple object created for the Document view, to access the fields of the view
   *        Document required for creating external view Span objects
   * @return map linking external view name to a list of tuples corresponding to that view
   */
  private HashMap<String, TupleList> parseExternalViews(JsonNode docRcd, Tuple docTup)
      throws DocReaderException {

    JsonNode extViewRecord = null;
    String extViewAQLName, extViewExternalName = null;

    try {
      extViewRecord = docRcd.get(EXT_VIEW_RECORD_NAME);
    } catch (ClassCastException cce) {
      throw new DocReaderException(makeErrorHeader(), "The top-level \"" + EXT_VIEW_RECORD_NAME
          + "\" object must be declared using a JsonNode.\n " + exampleExtView);
    }

    // parse the special ExternalViews field for external view data
    if (extViewRecord != null) {
      HashMap<String, TupleList> extViewMap = new HashMap<String, TupleList>();

      // iterate through each of the expected external views, throwing exception if one is not found
      for (Map.Entry<String, TupleSchema> extViewEntry : extViewSchemas.entrySet()) {
        extViewAQLName = extViewEntry.getKey();
        TupleSchema extViewSchema = extViewEntry.getValue();

        extViewExternalName = aqlNameToExternalName.get(extViewAQLName);

        if (extViewRecord.has(extViewExternalName)) {
          JsonNode extViewData;

          try {
            extViewData = extViewRecord.get(extViewExternalName);
          } catch (ClassCastException cce) {
            throw new DocReaderException(makeErrorHeader(), "External view " + extViewExternalName
                + " must be declared using a JsonNode.\n " + exampleExtView);
          }
          TupleList viewTuples =
              jsonArrayToTuples(extViewData, extViewAQLName, extViewSchema, docTup);
          extViewMap.put(extViewAQLName, viewTuples);
        } else {
          throw new DocReaderException(makeErrorHeader(), "External view " + extViewExternalName
              + " was expected but not found in input JSON.\n");

        }
      }

      return extViewMap;
    }

    // no ExternalViews field, make sure there are no expected external views, else throw an
    // exception
    else {
      if (extViewSchemas != null) {
        for (Map.Entry<String, TupleSchema> extViewEntry : extViewSchemas.entrySet()) {
          extViewAQLName = extViewEntry.getKey();
          extViewExternalName = aqlNameToExternalName.get(extViewAQLName);

          throw new DocReaderException(makeErrorHeader(), "External view " + extViewExternalName
              + " was expected but not found in input JSON.\n");

        }
      }
      return null;
    }

  }

  /**
   * Turns a JsonNode object into a TupleList of external view tuples. Used for external views only.
   * 
   * @param input array containing external view data
   * @param extViewAQLName the AQL-defined name of the external view to populate
   * @param extViewSchema the schema of the external view to populate
   * @param docTup the Tuple object created for the Document view, to access the fields of the view
   *        Document required for creating external view Span objects
   * @return
   */
  @SuppressWarnings("unchecked")
  private TupleList jsonArrayToTuples(JsonNode input, String extViewAQLName,
      TupleSchema extViewSchema, Tuple docTup) throws DocReaderException {

    TupleList ret = null;

    Iterator<JsonNode> itr = input.elements();
    ret = new TupleList(extViewSchema);

    // create the tuple list
    while (itr.hasNext()) {
      JsonNode viewTupleRecord = itr.next();

      Tuple externalViewTup = extViewSchema.createTup();

      // iterate over all external view schema fields and throw
      // exception if an expected field is not found
      for (int i = 0; i < extViewSchema.size(); i++) {
        String fieldName = extViewSchema.getFieldNameByIx(i);
        FieldType ft = extViewSchema.getFieldTypeByName(fieldName);

        JsonNode fieldObject = viewTupleRecord.get(fieldName);

        if (null == fieldObject) {
          throw new DocReaderException(makeErrorHeader(), String.format(
              "JSON record at this line does not contain an attribute for the required field named '%s'. Provide a non-null value of type '%s' for this required field.",
              fieldName, ft.getIsText() ? FieldType.TEXT_TYPE.getTypeName() : ft.getTypeName()));
        }

        populateTupleWithObject(externalViewTup, extViewSchema, fieldName,
            allExtViewSetters.get(extViewAQLName).get(fieldName), fieldObject, docTup);

        if (debug) {
          Log.debug("External view tuple created with contents:");
          Log.debug(externalViewTup.toString());
        }
      }

      // add the populated external view tuple
      ret.add(externalViewTup);

    }

    return ret;
  }

  /**
   * Parses an object correctly according to the tuple schema, and stores it in the passed-in tuple
   * Used to populate both document and external view tuples.
   * 
   * @param tup The tuple to be modified
   * @param schema The tuple schema to use
   * @param key The schema field corresponding to this object
   * @param obj The value to be parsed according to the schema
   * @param docTup the Tuple object created for the Document view, to access the fields of the view
   *        Document required for creating external view Span objects, or null when we populate the
   *        Document tuple itself
   * @throws DocReaderException
   */
  @SuppressWarnings("unchecked")
  private void populateTupleWithObject(Tuple tup, AbstractTupleSchema schema, String key,
      FieldSetter<?> accessor, JsonNode obj, Tuple docTup) throws DocReaderException {

    // Figure out the expected type
    FieldType expectedFieldType = schema.getFieldTypeByName(key);

    if (null == obj) {
      throw new DocReaderException(makeErrorHeader(), String.format(
          "%s JSON record at this line does not contain an attribute for the required field named '%s'. Provide a non-null value of type '%s' for this required field.",
          makeErrorHeader(), key, expectedFieldType.getIsText() ? FieldType.TEXT_TYPE.getTypeName()
              : expectedFieldType.getTypeName()));
    }

    if (accessor == null) {
      throw new DocReaderException(makeErrorHeader(), String.format(
          "Unable to put JsonNode into field '%s' of schema '%s' because schema field accessor does not exist.  "
              + "Check to see if field is part of the expected schema.",
          key, schema.getName()));
    }

    // Parse the object appropriately with the expected field type
    try {

      if (expectedFieldType == FieldType.INT_TYPE) {
        ((FieldSetter<Integer>) accessor).setVal(tup, Integer.parseInt(obj.asText()));
      } else if (expectedFieldType == FieldType.BOOL_TYPE) {
        ((FieldSetter<Object>) accessor).setVal(tup, Boolean.parseBoolean(obj.asText()));
      } else if (expectedFieldType == FieldType.FLOAT_TYPE) {
        ((FieldSetter<Float>) accessor).setVal(tup, Float.parseFloat(obj.asText()));
      } else if (expectedFieldType == FieldType.SPAN_TYPE) {

        // Begin and end offsets
        int begin = getOffsetValue(schema, key, obj, BEGIN_OFFSET_FIELD_NAME);
        int end = getOffsetValue(schema, key, obj, END_OFFSET_FIELD_NAME);

        // Field of view Document that the span is over; if missing, assume it is Document.text
        String docFieldName = null;
        JsonNode value = obj.get(DOCREF_FIELD_NAME);

        // JsonNode.get() will return NullNode for values explicitly set to null
        if (!(value instanceof NullNode) && (value != null)) {
          docFieldName = value.textValue();
        }

        if (null == docFieldName)
          docFieldName = Constants.DOCTEXT_COL;

        // Check that the Document schema contains the requested field
        if (false == docSchema.containsField(docFieldName)) {

          throw new DocReaderException(makeErrorHeader(), String.format(
              "Unable to create span from JSON record %s into field '%s' of schema '%s' because schema of view '%s' does not contain field '%s' of type Text.",
              obj, key, schema.getName(), Constants.DEFAULT_DOC_TYPE_NAME, docFieldName));
        }

        // Check that the requested field has the type Text
        FieldType docFieldType = docSchema.getFieldTypeByName(docFieldName);
        if (FieldType.TEXT_TYPE != docFieldType) {
          throw new DocReaderException(makeErrorHeader(), String.format(
              "Unable to create span from JSON record %s into field '%s' of schema '%s' because field '%s' of view '%s' is of not of expected type Text (type is %s)",
              obj, key, schema.getName(), docFieldName, Constants.DEFAULT_DOC_TYPE_NAME,
              docFieldType.getTypeName()));
        }

        // Found the doc field with the required type Text, so create an accessor for it, and access
        // the value
        TextGetter getDocField = docSchema.textAcc(docFieldName);
        Text srcText = getDocField.getVal(docTup);

        // Additional checks performed when creating the actual span
        Span span = null;
        try {
          span = Span.makeBaseSpan(srcText, begin, end);
        } catch (Throwable t) {
          throw new DocReaderException(t, makeErrorHeader(),
              String.format(
                  "Unable to create span from JSON record %s into field '%s' of schema '%s'.", obj,
                  key, schema.getName()));
        }

        ((FieldSetter<Span>) accessor).setVal(tup, span);
      } else {
        ((TextSetter) accessor).setVal(tup, obj.textValue());
      }
    } catch (IllegalArgumentException iae) {
      throw new DocReaderException(makeErrorHeader(),
          String.format("Value '%s' of field '%s' of schema '%s' is not expected type '%s'.", obj,
              key, schema.getName(), expectedFieldType.toString()));
    }

  }

  /**
   * @return the external view tuples map related to the current document tuple
   */
  public Map<String, TupleList> getExtViewTups() {
    return extViewTups;
  }

  /**
   * @return the getter map for the fields of the document schema
   */
  public Map<String, FieldGetter<?>> getDocAcc() {
    return docGetters;
  }

  /**
   * @return the getter map for the fields of the external view schemas
   */
  public Map<String, Map<String, FieldGetter<?>>> getAllExtViewAcc() {
    return allExtViewGetters;
  }

  /**
   * @return the map of external view AQL name vs their schema.
   */
  public Map<String, TupleSchema> getExtViewSchemas() {
    return this.extViewSchemas;
  }

  /**
   * Utility method to print out file name and line number for clear error messages
   * 
   * @return
   */
  public String makeErrorHeader() {
    return String.format("file '%s', line %d:  ", JSONfile.getName(), recordCount);
  }

  /*
   * PRIVATE METHODS GO HERE
   */

  /**
   * Method to initialize maps for retrieving the external name and schema associated with the AQL
   * name (name defined through 'create external view' statement) of an external view.
   * 
   * @param extViewSchemas a map of an external view name-pair (AQL Name, external Name) to its
   *        tuple schema
   */
  private void initInternalMaps(Map<Pair<String, String>, TupleSchema> extViewSchemas) {
    this.aqlNameToExternalName = new HashMap<String, String>();
    this.extViewSchemas = new HashMap<String, TupleSchema>();

    for (Entry<Pair<String, String>, TupleSchema> entry : extViewSchemas.entrySet()) {
      this.aqlNameToExternalName.put(entry.getKey().first, entry.getKey().second);
      this.extViewSchemas.put(entry.getKey().first, entry.getValue());
    }
  }

  /**
   * Utility method to extract the begin or end offset value from a JSON record representing a span.
   * 
   * @param schema external view name, for error reporting purposes
   * @param attrName external view attribute name, for error reporting
   * @param spanRcd the span value we are converting
   * @param offsetFieldName name of the field (begin or end) we are reading
   * @return the offset's integer value
   * @throws DocReaderException if the offset field is missing or is of the wrong type
   */
  private int getOffsetValue(AbstractTupleSchema schema, String attrName, JsonNode spanRcd,
      String offsetFieldName) throws DocReaderException {

    JsonNode obj = spanRcd.get(offsetFieldName);

    if (obj == null) {
      throw new DocReaderException(makeErrorHeader(), String.format(
          "Unable to create span from JSON record %s into field '%s' of schema '%s' because the value of the field '%s' of the JSON record is null. "
              + "Provide a non-null value for the field '%s' of this JSON record.",
          spanRcd, attrName, schema.getName(), offsetFieldName, offsetFieldName));

    }

    if (!(obj.canConvertToLong())) {
      throw new DocReaderException(makeErrorHeader(), String.format(
          "Unable to create span from JSON record %s into field '%s' of schema '%s' because the value of the field '%s' has unexpected type %s. "
              + "Provide a value of type Integer for the field '%s' of this JSON record.",
          spanRcd, attrName, schema.getName(), offsetFieldName, obj.getClass().getSimpleName(),
          offsetFieldName));
    }

    int intVal = obj.intValue();
    return intVal;

  }
}
