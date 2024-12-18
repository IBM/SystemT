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
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference
// Implementation, vJAXB 2.1.10 in
// JDK 6
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a>
// Any modifications to this file will be lost upon recompilation of the source schema.
// Generated on: 2012.06.19 at 04:26:09 PM IST
//

package com.ibm.avatar.aql.tam;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.biginsights.textanalytics.util.ObjectComparator;

/**
 * JAXB class to represent input to a function (UDF)
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InputType", namespace = "http://www.ibm.com/aql", propOrder = {"param"})
public class InputType implements Serializable {

  private static final long serialVersionUID = -3234596250661443425L;
  protected List<InputType.Param> param;

  /**
   * Gets the value of the param property.
   * <p>
   * This accessor method returns a reference to the live list, not a snapshot. Therefore any
   * modification you make to the returned list will be present inside the JAXB object. This is why
   * there is not a <CODE>set</CODE> method for the param property.
   * <p>
   * For example, to add a new item, do as follows:
   * 
   * <pre>
   * getParam().add(newItem);
   * </pre>
   * <p>
   * Objects of the following type(s) are allowed in the list {@link InputType.Param }
   */
  public List<InputType.Param> getParam() {
    if (param == null) {
      param = new ArrayList<InputType.Param>();
    }
    return this.param;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (false == obj instanceof InputType)
      return false;

    InputType other = (InputType) obj;

    // use null for <code>moduleName</code> in calls to ModuleMetadataMismatchException()
    // constructor below, as we do
    // not know the module name at this point. ModuleMetadataImpl.equals() would set the
    // <code>moduleName</code> before
    // re-throwing this exception to the consumers.

    // name
    if (false == ObjectComparator.equals(this.param, other.param))
      throw new ModuleMetadataMismatchException(null, "function.param", String.valueOf(this.param),
          String.valueOf(other.param));

    // return true, if all tests pass
    return true;
  }

  /**
   * <p>
   * Java class for anonymous complex type.
   * <p>
   * The following schema fragment specifies the expected content contained within this class.
   * 
   * <pre>
   * &lt;complexType>
   *   &lt;complexContent>
   *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
   *       &lt;attribute name="name" type="{http://www.w3.org/2001/XMLSchema}string" />
   *       &lt;attribute name="type">
   *         &lt;simpleType>
   *           &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
   *             &lt;enumeration value="int"/>
   *             &lt;enumeration value="float"/>
   *             &lt;enumeration value="boolean"/>
   *             &lt;enumeration value="string"/>
   *             &lt;enumeration value="span"/>
   *             &lt;enumeration value="text"/>
   *           &lt;/restriction>
   *         &lt;/simpleType>
   *       &lt;/attribute>
   *     &lt;/restriction>
   *   &lt;/complexContent>
   * &lt;/complexType>
   * </pre>
   */
  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlType(name = "")
  public static class Param implements Serializable {
    private static final long serialVersionUID = 325488514192111661L;

    @XmlAttribute
    protected String name;
    @XmlAttribute
    protected String type;

    /**
     * Gets the value of the name property.
     * 
     * @return possible object is {@link String }
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value allowed object is {@link String }
     */
    public void setName(String value) {
      this.name = value;
    }

    /**
     * Gets the value of the type property.
     * 
     * @return possible object is {@link String }
     */
    public String getType() {
      return type;
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value allowed object is {@link String }
     */
    public void setType(String value) {
      this.type = value;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (false == obj instanceof Param)
        return false;

      Param other = (Param) obj;

      // name
      if (false == ObjectComparator.equals(this.name, other.name))
        throw new ModuleMetadataMismatchException(null, "function.name", String.valueOf(this.name),
            String.valueOf(other.name));

      // type
      if (false == ObjectComparator.equals(this.type, other.type))
        throw new ModuleMetadataMismatchException(null, "function.type", String.valueOf(this.type),
            String.valueOf(other.type));

      // return true, if all tests pass
      return true;
    }

    @Override
    public int hashCode() {
      throw new FatalInternalError("Hashcode not implemented for class %s.",
          this.getClass().getSimpleName());
    }

  }

  @Override
  public int hashCode() {
    throw new FatalInternalError("Hashcode not implemented for class %s.",
        this.getClass().getSimpleName());
  }

}
