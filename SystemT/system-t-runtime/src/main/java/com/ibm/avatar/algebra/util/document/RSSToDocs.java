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
package com.ibm.avatar.algebra.util.document;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Utility to dump the contents of an RSS feed into a directory. Understands the format of
 * Craigslist postings; may need to be modified for other RSS data sources.
 * 
 */
@SuppressWarnings("all")
public class RSSToDocs {

  public static final String USAGE =
      String.format("Usage: java %s [url] [dir]", RSSToDocs.class.getName());

  private static final void usage() {
    System.err.printf(USAGE + "\n");
  }

  public static void main(String[] args) throws Exception {

    if (args.length != 2) {
      usage();
      return;
    }

    String urlStr = args[0];
    String outDirName = args[1];

    URL url = new URL(urlStr);
    File outDir = new File(outDirName);

    // Create the output directory if it doesn't already exist.
    outDir.mkdirs();

    // Fetch the RSS feed.
    URLConnection conn = url.openConnection();

    // Parse the feed into a DOM tree.
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder parser = factory.newDocumentBuilder();
    Document doc = parser.parse(conn.getInputStream());

    // Now iterate through the <item> tags at the top level, pulling each
    // out as a separate file.
    NodeList itemNodes = doc.getElementsByTagName("item");

    for (int i = 0; i < itemNodes.getLength(); i++) {
      Node item = itemNodes.item(i);

      // Fetch the URL of the "about" link for this item.
      String aboutURLStr = item.getAttributes().getNamedItem("rdf:about").getNodeValue();

      // Pull out the document name from the URL, assuming it's the last
      // item.
      URL aboutURL = new URL(aboutURLStr);
      String aboutFileName = new File(aboutURL.getFile()).getName();

      // Change the file's extension to XML, to generate the target
      // document name.
      String targetName = aboutFileName.replaceAll(".html?$", ".xml");

      // Check whether the file has already been pulled from the feed.
      File target = new File(outDir, targetName);

      if (target.exists()) {
        // System.err.printf(" --> File already exists.\n");
      } else {
        System.err.printf("Writing file %s\n", targetName);

        FileOutputStream out = new FileOutputStream(target);

        // Create a new Document out of our item.
        DocumentBuilder db = factory.newDocumentBuilder();
        Document newDoc = db.newDocument();
        Node itemCopy = newDoc.importNode(item, true);
        newDoc.appendChild(itemCopy);

        // Serialize the document as XML.
        OutputFormat of = new OutputFormat("XML", "UTF-8", true);
        of.setIndent(1);
        of.setIndenting(true);
        of.setDoctype(null, "users.dtd");
        XMLSerializer serializer = new XMLSerializer(out, of);

        // As a DOM Serializer
        serializer.asDOMSerializer();
        serializer.serialize(newDoc);

        out.close();
      }

    }

    System.err.print("Done.\n");
  }
}
