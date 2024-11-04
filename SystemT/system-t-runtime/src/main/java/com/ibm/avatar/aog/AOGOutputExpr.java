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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;

/** Parse tree node for an AOG file's "Output" line. */
public class AOGOutputExpr {

  ArrayList<String> outputs = new ArrayList<String>();

  public void addOutput(String output) {
    // Add only unique output views; more then one module can output same view
    if (false == outputs.contains(output))
      outputs.add(output);
  }

  public ArrayList<String> getOutputs() {
    return outputs;
  }

  /** Regenerate the original "Output" line. */
  public void dump(PrintWriter stream) {

    if (outputs.size() > 0) {
      stream.print("Output: ");

      for (Iterator<String> iter = outputs.iterator(); iter.hasNext();) {
        String elem = iter.next();

        stream.print("$" + elem);

        if (iter.hasNext()) {
          stream.print(", ");
        }
      }

      stream.print(";\n");
    }

  }

}
