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
package com.ibm.avatar.aql.doc;

/**
 * Simple representation for an AQL doc tag consisting of the tag's name and text. This interface
 * can be extended in the future to provide support for parsing more structured tags such as @field,
 * which has 3 parts: tag name, value and text, or @auxViewField which has 4 parts: tag name,
 * auxiliary detag view name, attribute name, and text. When doing so, create the following
 * artifacts:
 * <ul>
 * <li>a new interface StructTag that implements {@link Tag}</li>
 * <li>a class StructTagImpl that implements StructTag and extends {@link TagImpl}</li>
 * </ul>
 * 
 */
public interface Tag {

  /**
   * 
   * @return
   */
  String getName();

  /**
   * Return the text of this tag, which is the portion of text that appears after the tag name.
   * 
   * @return
   */
  String getText();

  /**
   * String representation of this tag.
   */
  @Override
  String toString();

}
