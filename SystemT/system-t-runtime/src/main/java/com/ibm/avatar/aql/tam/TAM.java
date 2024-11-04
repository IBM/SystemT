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

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.tam.ModuleMetadata;

/**
 * In-memory representation of a TAM file. Contains all necessary information for instantiating the
 * operators and resources described in the TAM file.
 */
public class TAM {
  protected String moduleName;

  /** Execution plan for this module, serialized as a string. */
  protected String aog;

  /** Metadata about the externally-visible resources and views defined by this module. */
  protected ModuleMetadata metadata;

  /** Compiled dictionaries stored in the TAM file, indexed by AQL name of the dictionary. */
  protected Map<String, CompiledDictionary> dicts = new TreeMap<String, CompiledDictionary>();

  /** Contents of jar files stored inside the TAM, indexed by AQL name of the jar file. */
  protected Map<String, byte[]> jars = new TreeMap<String, byte[]>();

  /**
   * Main constructor. Does not initialize the individual fields of the object aside from the module
   * name field.
   * 
   * @param moduleName name of the module that this TAM file represents
   */
  public TAM(String moduleName) {
    setModuleName(moduleName);
  }

  /**
   * @return the name of the module that this TAM file represents
   */
  public String getModuleName() {
    return moduleName;
  }

  /**
   * @param moduleName name of the module that this TAM file represents
   */
  public void setModuleName(String moduleName) {
    this.moduleName = moduleName;
  }

  /**
   * @return the serialized compiled execution plan for this module
   */
  public String getAog() {
    return aog;
  }

  /**
   * @param aog the serialized compiled execution plan for this module
   */
  public void setAog(String aog) {
    this.aog = aog;
  }

  /**
   * @return Metadata about the externally-visible resources and views defined by this module.
   */
  public ModuleMetadata getMetadata() {
    return metadata;
  }

  /**
   * @param metadata Metadata about the externally-visible resources and views defined by this
   *        module.
   */
  public void setMetadata(ModuleMetadata metadata) {
    this.metadata = metadata;
  }

  /**
   * Adds the contents of a dictionary to the module's set of compiled dictionaries
   * 
   * @param dictName AQL name of the dictionary
   * @param dict compiled contents of the dictionary
   */
  public void addDict(String dictName, CompiledDictionary dict) {
    if (dicts.containsKey(dictName)) {
      throw new FatalInternalError(
          "Attempted to add dictionary '%s' twice to TAM file for module '%s'", dictName,
          moduleName);
    }
    dicts.put(dictName, dict);
  }

  /**
   * Removes a dictionary from the module's set of compiled dictionaries
   * 
   * @param dictName AQL name of the dictionary
   */
  public void removeDict(String dictName) {
    dicts.remove(dictName);
  }

  /**
   * @param dictName AQL name of a dictionary located inside this module
   * @return compiled contents of the dictionary
   */
  public CompiledDictionary getDict(String dictName) {
    return dicts.get(dictName);
  }

  /**
   * @return an indexed collection of all compiled dictionaries present in this TAM file, indexed by
   *         AQL name of the dictionary
   */
  public Map<String, CompiledDictionary> getAllDicts() {
    return Collections.unmodifiableMap(dicts);
  }

  /**
   * Adds the contents of a jar file to the module
   * 
   * @param jarName AQL name of the jar file
   * @param jarContents raw contents of the file
   */
  public void addJar(String jarName, byte[] jarContents) {
    if (jars.containsKey(jarName)) {
      throw new FatalInternalError(
          "Attempted to add jar file '%s' twice to TAM file for module '%s'", jarName, moduleName);
    }
    jars.put(jarName, jarContents);
  }

  /**
   * Removes a jar file from the module. Does not check whether the jar file is already present.
   * 
   * @param jarName AQL name of the jar file
   */
  public void removeJar(String jarName) {
    jars.remove(jarName);
  }

  /**
   * @param dictName AQL name of a jar file located inside this module
   * @return contents of the file
   */
  public byte[] getJar(String jarName) {
    return jars.get(jarName);
  }

  /**
   * @return an indexed collection of all compiled dictionaries present in this TAM file, indexed by
   *         AQL name of the dictionary
   */
  public Map<String, byte[]> getAllJars() {
    return Collections.unmodifiableMap(jars);
  }

  // TODO: This implementation of equals() is does a very shallow comparison and is probably causing
  // bugs if it is used
  // at all.
  @Override
  public boolean equals(Object obj) {
    if (obj == null || (false == obj instanceof TAM)) {
      return false;
    }

    TAM other = (TAM) obj;
    if (this.aog == null && other.aog != null) {
      return false;
    }
    //
    // if(this.dicts == null && other.dicts != null){
    // return false;
    // }

    if (this.metadata == null && other.metadata != null) {
      return false;
    }

    if (this.moduleName == null && other.moduleName != null) {
      return false;
    }

    if (this.moduleName != null && false == this.moduleName.equals(other.moduleName)) {
      return false;
    }

    if (false == this.aog.equals(other.aog)) {
      return false;
    }

    // if(false == this.dicts.equals (other.dicts)){
    // return false;
    // }
    //
    // if(false == this.metadata.equals (other.metadata)){
    // return false;
    // }

    // all checks passed. Return true.
    return true;

  }

  @Override
  public int hashCode() {
    throw new FatalInternalError("Hashcode not implemented for class %s.",
        this.getClass().getSimpleName());
  }

}
