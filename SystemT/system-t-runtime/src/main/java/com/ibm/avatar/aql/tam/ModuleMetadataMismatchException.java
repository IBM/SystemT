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
package com.ibm.avatar.aql.tam;

/**
 * Exception class to indicate that two metadata are not equal. <br/>
 * <br/>
 * Note: This class is for internal use only (by Junit test cases) and not to be made public. Also,
 * it must be an unchecked exception only, since it is being thrown by ModuleMetadataImpl.equals()
 * method.
 * 
 */
public class ModuleMetadataMismatchException extends RuntimeException {
  private static final long serialVersionUID = 5847593425714156546L;

  /**
   * Name of the module whose metadata does not match between expected and actual results
   */
  protected String moduleName;

  /**
   * Name of the element (or) attribute whose value does not match between expected and actual
   * results
   */
  protected String differingItem;

  /**
   * Value of a specific metadata, as expected by the test case
   */
  protected String expectedValue;

  /**
   * Value of a specific metadata, as returned by the test case
   */
  protected String actualValue;

  /**
   * Initializes a ModuleMetadataMismatchException object with given values
   * 
   * @param moduleName Name of the module whose metadata does not match between expected and actual
   *        results
   * @param differingItem Name of the element (or) attribute whose value does not match between
   *        expected and actual results
   * @param expectedValue Value of a specific metadata, as expected by the test case
   * @param actualValue Value of a specific metadata, as returned by the test case
   */
  public ModuleMetadataMismatchException(String moduleName, String differingItem,
      String expectedValue, String actualValue) {
    super();
    this.moduleName = moduleName;
    this.differingItem = differingItem;
    this.expectedValue = expectedValue;
    this.actualValue = actualValue;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Throwable#getMessage()
   */
  @Override
  public String getMessage() {
    return String.format(
        "The following detail differs between 'expected' and 'actual' values of metadata for module '%s'.\n"
            + "Differing item: %s, Expected value: %s, Actual value: %s",
        moduleName, differingItem, expectedValue, actualValue);
  }

}
