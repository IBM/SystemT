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
package com.ibm.avatar.aog;

import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.output.ToBuffer;

/**
 * Factory object for AOG that produces output operators. In this case, the output operators write
 * to internal buffers.
 * 
 */
public class BufferOutputFactory extends OutputFactory {

  public BufferOutputFactory() {}

  @Override
  public Operator makeOpInternal(Operator child, String nameStr) throws Exception {
    return new ToBuffer(child);
  }

}
