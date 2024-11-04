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
/**
 * 
 */
package com.ibm.avatar.api;

import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.output.Passthru;
import com.ibm.avatar.aog.OutputFactory;

/**
 * Stub factory that produces null output operators; use for disabling annotation output for an AOG
 * operator graph.
 * 
 */
class DisableOutputFactory extends OutputFactory {
  @Override
  public Operator makeOpInternal(Operator child, String nameStr) throws Exception {
    return new Passthru(child);
  }
}
