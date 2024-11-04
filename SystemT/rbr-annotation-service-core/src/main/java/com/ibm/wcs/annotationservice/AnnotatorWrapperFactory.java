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
package com.ibm.wcs.annotationservice;

import com.ibm.wcs.annotationservice.exceptions.AnnotationServiceException;
import com.ibm.wcs.annotationservice.models.json.AnnotatorBundleConfig;
import com.ibm.wcs.annotationservice.models.json.AnnotatorBundleConfig.AnnotatorRuntime;
import com.ibm.wcs.annotationservice.models.json.SystemTAnnotatorBundleConfig;

/**
 * Factory class to create an instance of {@link AnnotatorWrapper}.
 * 
 */
public class AnnotatorWrapperFactory {

  // Suppress object instantiation
  private AnnotatorWrapperFactory() {}

  /**
   * Create an instance of the annotator runtime of type SystemT, UIMA or SIRE, depending on the
   * annotator type specified in the input annotator bundle configuration.
   * 
   * @param annotBundleCfg configuration of the annotator bundle
   * @return instance of the actual annotator bundle
   * @throws AnnotationServiceException
   */
  public static AnnotatorWrapper createInstance(AnnotatorBundleConfig annotBundleCfg)
      throws AnnotationServiceException {
    AnnotatorRuntime annotatorRuntimeType =
        AnnotatorRuntime.getEnum(annotBundleCfg.getAnnotatorRuntime());
    AnnotatorWrapper aw = null;

    switch (annotatorRuntimeType) {
      case SYSTEMT: {
        aw = new AnnotatorWrapperSystemT((SystemTAnnotatorBundleConfig) annotBundleCfg);
        break;
      }
      default: {
        throw new AnnotationServiceException("Unsupported annotator runtime type: '%s'",
            annotatorRuntimeType);
      }
    }

    return aw;
  }

}
