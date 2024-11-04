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
package com.ibm.wcs.annotationservice.polymorphic;

import com.ibm.wcs.annotationservice.exceptions.AnnotationServiceException;
import com.ibm.wcs.annotationservice.models.json.AnnotatorBundleConfig.AnnotatorRuntime;
import com.ibm.wcs.annotationservice.models.json.SystemTAnnotatorBundleConfig;

public class ABCTypeIdResolver extends AbstractTypeIdResolver {
  @Override
  Class<?> getTypeClazz(String id) throws AnnotationServiceException {
    switch (id) {
      case "SystemT":
        return SystemTAnnotatorBundleConfig.class;

      default:
        throw new AnnotationServiceException(
            "Unsupported type of annotator runtime '%s'; supported values are: %s", id,
            AnnotatorRuntime.getValues());

    }
  }

  @Override
  String getIdfromType(Object obj, Class<?> clazz) throws AnnotationServiceException {
    if (obj instanceof SystemTAnnotatorBundleConfig)
      return "SystemT";

    throw new AnnotationServiceException("Unsupported type of annotator type %s", obj.toString());
  }

  @Override
  public String getDescForKnownTypeIds() {
    return "SystemT or UIMA";
  }
}
