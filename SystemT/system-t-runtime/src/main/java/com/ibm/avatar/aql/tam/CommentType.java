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

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CommentType", namespace = "http://www.ibm.com/aql", propOrder = {"text"})
public class CommentType implements Serializable {
  /**
   * 
   */
  private static final long serialVersionUID = 4663450378726255582L;
  @XmlAttribute
  protected String text;

  /**
   * @return the text of the comment
   */
  public String getText() {
    return text;
  }

  /**
   * @param text the text of the comment
   */
  public void setText(String text) {
    this.text = text;
  }

}
