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

import java.io.OutputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import com.ibm.avatar.api.tam.ITAMComponent;

public abstract class AbstractTAMComponent implements ITAMComponent {
  private static final long serialVersionUID = -8163898896431676926L;

  private static JAXBContext jaxbContext;

  @Override
  public void serialize(OutputStream out) throws JAXBException {

    if (jaxbContext == null) {
      jaxbContext = JAXBContext.newInstance(getClass());
    }

    Marshaller marshaller = jaxbContext.createMarshaller();
    // Make the output formatted for human consumption
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    marshaller.marshal(this, out);

  }

}
