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

/**
 * An ordered quad
 * 
 * @param <firstT> type of the first element in the quad
 * @param <secondT> type of the second element in the quad
 * @param <thirdT> type of the third element in the quad
 * @param <fourthT> type of the fourth element in the quad
 */
public class Quad<firstT, secondT, thirdT, fourthT> {
  public firstT first;
  public secondT second;
  public thirdT third;
  public fourthT fourth;

  public Quad(firstT first, secondT second, thirdT third, fourthT fourth) {
    this.first = first;
    this.second = second;
    this.third = third;
    this.fourth = fourth;
  }
}
