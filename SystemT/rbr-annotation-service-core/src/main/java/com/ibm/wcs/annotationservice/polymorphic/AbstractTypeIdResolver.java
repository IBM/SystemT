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

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.IOException;

public abstract class AbstractTypeIdResolver implements TypeIdResolver {

  private JavaType mBaseType;

  abstract Class<?> getTypeClazz(String id) throws Exception;

  abstract String getIdfromType(Object obj, Class<?> clazz) throws Exception;

  @Override
  public void init(JavaType baseType) {
    mBaseType = baseType;
  }

  @Override
  public Id getMechanism() {
    return Id.CUSTOM;
  }

  @Override
  public String idFromValue(Object obj) {
    return idFromValueAndType(obj, obj.getClass());
  }

  @Override
  public String idFromBaseType() {
    return idFromValueAndType(null, mBaseType.getRawClass());
  }

  @Override
  public String idFromValueAndType(Object obj, Class<?> clazz) {
    String id;
    try {
      id = getIdfromType(obj, clazz);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    if (id == null)
      throw new IllegalStateException("cannot find identifier for class '" + clazz + "'");
    return id;
  }

  @Override
  public JavaType typeFromId(DatabindContext databindContext, String type) throws IOException {
    Class<?> clazz;
    try {
      clazz = getTypeClazz(type);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    if (clazz == null)
      throw new IllegalStateException("cannot find class for type'" + type + "'");
    return TypeFactory.defaultInstance().constructSpecializedType(mBaseType, clazz);
  }

  /*
   * @Override public JavaType typeFromId(String type) { Class<?> clazz; try { clazz =
   * getTypeClazz(type); } catch (Exception e) { throw new IllegalStateException(e); } if (clazz ==
   * null) throw new IllegalStateException("cannot find class for type'" + type + "'"); return
   * TypeFactory.defaultInstance().constructSpecializedType(mBaseType, clazz); }
   */
}
