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
package com.ibm.avatar.algebra.util.pmml;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.IOUtil;
import org.dmg.pmml.PMML;
import org.jpmml.evaluator.Computable;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.ModelEvaluatorFactory;
import org.jpmml.manager.PMMLManager;
import org.xml.sax.SAXException;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldCopier;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.api.exceptions.TableUDFException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.api.udf.TableUDFBase;
import com.ibm.avatar.logging.Log;

/**
 * Base class for PMML scoring functions. Uses the AQL table function API to obtain the learned
 * model in PMML format, as well as the names of input and output parameters. Converts inputs and
 * outputs from AQL tuples to the internal format used by the JPMML scoring library.
 * <p>
 * <b>NOTE:</b> Do not reference this class directly from runtime code! Direct calls will introduce
 * a dependency on the JPMML library when SystemT is not using that library. Instead, always call
 * this function via reflection. In particular, the current implementation of PMML scoring generates
 * subclasses of this class and packages those UDFs in jar files inside the tam file.
 * 
 */
public abstract class ScoringTableFuncBase extends TableUDFBase {
  /** In-memory representation of the PMML file */
  protected PMML pmml;

  /**
   * Scoring object that does most of the work of scoring and data transformation. Actual runtime
   * type of this object depends on what kind of model the PMML file specifies.
   */
  protected Evaluator evaluator;

  /**
   * Names of input fields of {@link #evaluator}.
   */
  protected ArrayList<FieldName> activeFields;

  /**
   * Accessors for retrieving inputs to the model from input. Entries correspond to entries in
   * {@link #activeFields}
   */
  protected ArrayList<FieldGetter<?>> fieldGetters;

  /**
   * Names of output fields of {@link #evaluator}.
   */
  protected ArrayList<FieldName> outputFields;

  /**
   * Accessors for setting the values of fields in returned tuples. Each entry corresponds to a name
   * in {@link #outputFields}.
   */
  protected ArrayList<FieldSetter<? extends Object>> outputFieldSetters;

  /**
   * Names of predicted fields of {@link #evaluator}.
   */
  protected ArrayList<FieldName> predictedFields;

  /**
   * Accessors for setting the values of fields in returned tuples. Each entry corresponds to a name
   * in {@link #predictedFields}.
   */
  protected ArrayList<FieldSetter<? extends Object>> predictedFieldSetters;

  /**
   * Accessor for copying any values that are passed through from input to output.
   */
  protected FieldCopier fieldCopier = null;

  /**
   * Subclasses must implement this method.
   * 
   * @return the XML contents of the PMML file that describes the scoring function.
   */
  protected abstract InputStream getPMMLStream() throws TableUDFException;

  @Override
  public void validateSchema(TupleSchema declaredInputSchema, TupleSchema runtimeInputSchema,
      TupleSchema returnSchema, Method methodInfo, boolean compileTime) throws TableUDFException {

    // We always load the model, even at compile time. Models are assumed to be reasonably small.
    initModel(runtimeInputSchema, returnSchema);

  }

  /**
   * Initialize objects associated with the JPMML library. Called both at compile time and during
   * operator graph initialization.
   * 
   * @throws JAXBException if there is an error parsing the PMML file
   * @throws SAXException if there is an error parsing the PMML file
   */
  private void initModel(TupleSchema runtimeInputSchema, TupleSchema returnSchema)
      throws TableUDFException {

    // Retrieve the schema of the model parameter records.
    FieldType paramsType = runtimeInputSchema.getFieldTypeByName(PMMLUtil.MODEL_PARAMS_ARG_NAME);
    if (false == paramsType.getIsLocator()) {
      throw new TableUDFException("Params argument of scoring function is not a record locator.  "
          + "Ensure that the function is declarated properly in the 'create function' statement.");
    }
    AbstractTupleSchema paramSchema = paramsType.getRecordSchema();

    InputStream pmmlStream = getPMMLStream();
    try {
      pmml = IOUtil.unmarshal(pmmlStream);
    } catch (Exception e) {
      throw new TableUDFException(e,
          "Error parsing PMML representation of model.  Ensure that the model file is in valid PMML format (PMML 4.1 or earlier).");
    }

    PMMLManager pmmlManager = new PMMLManager(pmml);
    evaluator = (Evaluator) pmmlManager.getModelManager(null, ModelEvaluatorFactory.getInstance());

    Log.debug("%s: Active fields are %s", this, evaluator.getActiveFields());
    Log.debug("%s: Output fields are %s", this, evaluator.getOutputFields());
    Log.debug("%s: Predicted fields are %s", this, evaluator.getPredictedFields());
    Log.debug("%s: Group fields are %s", this, evaluator.getGroupFields());

    // Grab input field information and set up accessors.
    // We store a list of PMML field names and a second list of the corresponding tuple field
    // accessors
    activeFields = new ArrayList<FieldName>();
    fieldGetters = new ArrayList<FieldGetter<?>>();

    for (FieldName fn : evaluator.getActiveFields()) {
      DataType pmmlType = evaluator.getDataField(fn).getDataType();

      if (paramSchema.containsField(fn.getValue())) {
        FieldType aqlType = paramSchema.getFieldTypeByName(fn.getValue());
        validateInputConversion(fn.getValue(), aqlType, pmmlType);
        activeFields.add(fn);
        fieldGetters.add(paramSchema.genericGetter(fn.getValue(), aqlType));
      } else {
        // Field not present in input schema; don't bother setting it
      }
    }

    // Generate setters for model outputs and predicted fields
    outputFields = new ArrayList<FieldName>();
    outputFieldSetters = new ArrayList<FieldSetter<? extends Object>>();

    for (FieldName fn : evaluator.getOutputFields()) {
      String nameStr = fn.getValue();
      if (returnSchema.containsField(nameStr)) {
        outputFields.add(fn);
        outputFieldSetters.add(makeFieldSetter(fn, false, returnSchema));
      } else {
        // Field not present in function's declared return schema; just drop the value
      }
    }

    predictedFields = new ArrayList<FieldName>();
    predictedFieldSetters = new ArrayList<FieldSetter<? extends Object>>();
    for (FieldName fn : evaluator.getPredictedFields()) {
      if (returnSchema.containsField(fn.getValue())) {
        predictedFields.add(fn);
        predictedFieldSetters.add(makeFieldSetter(fn, true, returnSchema));
      } else {
        // Field not present in function's declared return schema; just drop the value
      }
    }

    // Generate information on fields copied from input to output
    ArrayList<String> fieldsToCopy = new ArrayList<String>();
    for (String fieldName : paramSchema.getFieldNames()) {
      if (returnSchema.containsField(fieldName)) {
        fieldsToCopy.add(fieldName);
      }
    }

    if (fieldsToCopy.size() > 0) {
      String[] namesArray = new String[fieldsToCopy.size()];
      namesArray = fieldsToCopy.toArray(namesArray);
      fieldCopier = returnSchema.fieldCopier(paramSchema, namesArray, namesArray);
    }

  }

  /**
   * Create an accessor to set an field of output tuples based on an output of the model
   * 
   * @param fn name of the field (PMML name format)
   * @param isDataField true if this field is a PMML data field, as opposed to an output field
   * @param returnSchema schema of returned tuples from the table function
   * @return appropriate accessor to use when converting the specified field from the outputs of the
   *         model to a field in a returned tuple
   * @throws TableUDFException
   */
  private FieldSetter<? extends Object> makeFieldSetter(FieldName fn, boolean isDataField,
      TupleSchema returnSchema) throws TableUDFException {
    String fieldNameStr = fn.getValue();
    DataType pmmlType;
    if (isDataField) {
      pmmlType = evaluator.getDataField(fn).getDataType();
    } else {
      pmmlType = evaluator.getOutputField(fn).getDataType();
    }
    FieldType aqlType = returnSchema.getFieldTypeByName(fieldNameStr);
    validateOutputConversion(fieldNameStr, pmmlType, aqlType);

    if (aqlType.getIsText()) {
      // Need special accessor for text
      return returnSchema.textSetter(fieldNameStr);
    } else {
      // Use generic accessors for everything else
      return returnSchema.genericSetter(fieldNameStr, aqlType);
    }
  }

  /**
   * Validate that two types are compatible; i.e. that data can be converted between them on the
   * input (AQL==>PMML) side.
   * 
   * @param fieldName name of field (for error messages)
   * @param aqlType AQL data type
   * @param pmmlType (hopefully) corresponding PMML data type
   * @throws TableUDFException if the types are not compatible
   */
  private void validateInputConversion(String fieldName, FieldType aqlType, DataType pmmlType)
      throws TableUDFException {
    // TODO: Write this function!!!
  }

  /**
   * Validate that data can be converted from PMML outputs to AQL field values.
   * 
   * @param fieldName name of field (for error messages)
   * @param pmmlType PMML data type
   * @param aqlType (hopefully) corresponding AQL data type
   * @throws TableUDFException if the types are not compatible
   */
  private void validateOutputConversion(String fieldName, DataType pmmlType, FieldType aqlType)
      throws TableUDFException {
    // TODO: Write this function!!!
  }

  /**
   * Main entry point. Subclasses should call this method as their implementation of the scoring
   * UDF. Polymorphic; reads schema information from PMML and from the AQL table functions API, then
   * performs the appropriate mapping between the two schemas.
   * 
   * @param params table of input records to score.
   * @return table of corresponding output records
   * @throws TextAnalyticsException if an error occurs evaluating the model or converting arguments
   */
  @SuppressWarnings("unchecked")
  protected TupleList evalImpl(TupleList params) throws TextAnalyticsException {
    TupleSchema retSchema = getReturnTupleSchema();
    TupleList ret = new TupleList(retSchema);

    TLIter itr = params.iterator();

    // One evaluation of the model for each input tuple
    while (itr.hasNext()) {
      Tuple tup = itr.next();

      // Translate inputs from AQL to PMML
      // Would prefer to use TreeMap here, but FieldName doesn't implement Comparable
      Map<FieldName, FieldValue> arguments = new LinkedHashMap<FieldName, FieldValue>();
      for (int i = 0; i < activeFields.size(); i++) {
        FieldName fieldName = activeFields.get(i);
        Object rawAQLArg = fieldGetters.get(i).getVal(tup);

        FieldValue pmmlArg;
        try {
          pmmlArg = evaluator.prepare(fieldName, rawAQLArg);
        } catch (Exception e) {
          // The prepare() method can give
          throw new TextAnalyticsException(e,
              "Invalid argument value %s for field %s. " + "PMML error type is %s.  "
                  + "Ensure that the value satisfies the constraints specified in the PMML file.",
              rawAQLArg, fieldName, e.toString());

        }
        arguments.put(fieldName, pmmlArg);
      }

      // Evaluate the model on the collected vector of field values
      Map<FieldName, Object> results = (Map<FieldName, Object>) evaluator.evaluate(arguments);

      // Translate outputs into an output tuple
      Tuple outTup = getReturnTupleSchema().createTup();

      // First do PMML output fields (transformed input fields)
      for (int i = 0; i < outputFields.size(); i++) {
        FieldName fn = outputFields.get(i);

        FieldSetter<Object> acc = (FieldSetter<Object>) outputFieldSetters.get(i);

        acc.setVal(outTup, resultToObject(results.get(fn)));

      }

      // Then do PMML predicted fields (direct model outputs)
      for (int i = 0; i < predictedFields.size(); i++) {
        FieldName fn = predictedFields.get(i);
        FieldSetter<Object> acc = (FieldSetter<Object>) predictedFieldSetters.get(i);

        acc.setVal(outTup, resultToObject(results.get(fn)));
      }

      // Then do fields that are directly copied from input to output
      if (null != fieldCopier) {
        fieldCopier.copyVals(tup, outTup);
      }

      ret.add(outTup);
    }

    return ret;
  }

  /**
   * JPMML likes to return complex objects cast down to {@link Object} for certain model outputs.
   * This method massages these values to something that SystemT can understand.
   * 
   * @param result output from JPMML model.
   * @return more consumable version of the output
   */
  private Object resultToObject(Object result) {
    Object convertedResult = result;
    if (result instanceof Computable) {
      // Result is a "complex" output type; ask the library to convert it to an Object
      Computable c = (Computable) result;
      convertedResult = c.getResult();
    }

    return convertedResult;
  }
}
