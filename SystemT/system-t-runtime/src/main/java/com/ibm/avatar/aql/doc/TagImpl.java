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
 * Represents a simple AQL doc tag consisting of a tag name and text.
 * 
 */
public class TagImpl implements Tag {
  /** The name of the tag, that is, the token that immediately follows the '@' character. */
  protected final String name;

  /** The text of the tag, that is, the text that immediately follows the tag name. */
  protected final String text;

  /**
   * Constructor
   * 
   * @param name the name of the tag
   * @param text the text of the tag
   */
  TagImpl(String name, String text) {
    this.name = name;
    this.text = text;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.doc.Tag#getName()
   */
  @Override
  public String getName() {
    return name;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.doc.Tag#getText()
   */
  @Override
  public String getText() {
    return text;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.doc.Tag#toString()
   */
  @Override
  public String toString() {
    return name + " " + text;
  }

}
