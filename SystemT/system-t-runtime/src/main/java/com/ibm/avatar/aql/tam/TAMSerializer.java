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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import javax.xml.bind.JAXBException;

import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.dict.DictionarySerializer;
import com.ibm.avatar.algebra.util.dict.TextSerializer;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.IncompatibleCompiledDictException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.api.exceptions.VerboseNullPointerException;
import com.ibm.avatar.api.tam.ModuleMetadata;
import com.ibm.avatar.logging.Log;

/**
 * Class that handles serialization and loading of TAMs.
 * 
 */
public class TAMSerializer {
  private final static boolean debug_flag = false;

  private static void debug(String fmt, Object... args) {
    if (debug_flag) {
      Log.debug(fmt, args);
    }
  }

  /**
   * Dictionary serializer/de-serializer to serialize/load compiled dictionaries from/to TAM.
   */
  private static DictionarySerializer dictSerializer = new TextSerializer();

  private static final String DICTS_DIR = "dicts/";
  private static final String JARS_DIR = "jars/";
  private static final String JARS_INDEX_FILE_NAME = JARS_DIR + "jar_index.csv";
  private static final String COMPILED_DICT_EXTN = ".cd";
  private static final String AOG_FILE_NAME = "plan.aog";
  private static final String METADATA_FILE_NAME = "metadata.xml";

  /**
   * Convenience method for serializing a TAM
   * 
   * @param tam The TAM object to be serialized
   * @param destURI destination URI where the TAM object should be serialized to. Should point to a
   *        JAR, ZIP or an existing directory
   * @throws IOException If there is any error in serializing the TAM
   * @throws JAXBException
   */
  public static void serialize(TAM tam, URI destURI) throws IOException, JAXBException {
    File dest;
    FileOutputStream ostream = null;
    JarOutputStream jarOut = null;

    if (null == tam.getAog()) {
      throw new FatalInternalError("Attempted to serialize a TAM file with no AOG.");
    }

    try {
      dest = new File(destURI);
    } catch (IllegalArgumentException e) {
      // Make the exception(s) thrown by File() more user-friendly
      throw new IOException(
          String.format("Error converting destination URI %s into a location on the filesystem: %s",
              destURI, e.getMessage()),
          e);
    }

    boolean finishedSerializing = false;
    try {

      if (false == dest.exists()) {
        dest.mkdirs();
      }

      File tamFile = new File(dest, String.format("%s.tam", tam.getModuleName()));

      ostream = new FileOutputStream(tamFile);

      jarOut = new JarOutputStream(ostream);
      serializeTAM(jarOut, tam);

      finishedSerializing = true;
    } finally {
      if (false == finishedSerializing) {
        // SPECIAL CASE: Caught an exception above; there may be zero entries in the TAM file, so
        // create a dummy entry.
        jarOut.putNextEntry(new ZipEntry("/"));
        jarOut.closeEntry();
        // END SPECIAL CASE
      }

      // close all streams
      if (jarOut != null)
        jarOut.close();
      if (ostream != null)
        ostream.close();
    }
  }

  /**
   * Serializes a complete TAM object into the JarOutputStream representing the .tam file
   * 
   * @param out JarOutputStream representing the .tam file.
   * @param tam TAM object whose contents are to be written out to the output stream.
   * @throws IOException
   * @throws JAXBException
   */
  private static void serializeTAM(JarOutputStream out, TAM tam) throws IOException, JAXBException {
    serializeAOG(out, tam);
    serializeMetadata(out, tam);
    serializeDictionaries(out, tam);
    serializeJars(out, tam);
  }

  /**
   * Serializes the AOG string from the TAM object into the JarOutputStream representing the .tam
   * file
   * 
   * @param out JarOutputStream representing the .tam file.
   * @param tam TAM object whose metadata is to be written out to the output stream.
   * @throws IOException
   */
  private static void serializeAOG(JarOutputStream out, TAM tam) throws IOException {
    String aogStr = tam.getAog();
    if (null == aogStr) {
      throw new FatalInternalError("No AOG to serialize for module %s", tam.getModuleName());
    }
    JarEntry entryAOG = new JarEntry(AOG_FILE_NAME);
    out.putNextEntry(entryAOG);
    out.write(aogStr.getBytes(Constants.ENCODING_UTF8));
    out.closeEntry();
  }

  /**
   * Serializes the metadata from the TAM object into the JarOutputStream representing the .tam file
   * 
   * @param out JarOutputStream representing the .tam file.
   * @param tam TAM object whose metadata is to be written out to the output stream.
   * @throws IOException
   * @throws JAXBException
   */
  private static void serializeMetadata(JarOutputStream out, TAM tam)
      throws IOException, JAXBException {

    if (tam != null) {
      ModuleMetadata metadata = tam.getMetadata();
      // There can be a tam without metadata
      if (null != metadata) {
        JarEntry entryMetadata = new JarEntry(metadata.getFileName());
        out.putNextEntry(entryMetadata);
        tam.getMetadata().serialize(out);
        out.closeEntry();
      }
    }
  }

  /**
   * Serializes dictionaries of a given TAM object into a JarOutputStream representing the .tam
   * file.
   * 
   * @param out JarOutputStream representing the .tam file.
   * @param tam TAM object whose dictionaries are to be written out to the output stream.
   * @throws IOException
   */
  private static void serializeDictionaries(JarOutputStream out, TAM tam) throws IOException {
    Map<String, CompiledDictionary> dicts = tam.getAllDicts();
    if (dicts != null && dicts.size() > 0) {
      JarEntry dictsFolder = new JarEntry(DICTS_DIR);
      out.putNextEntry(dictsFolder);
      out.closeEntry();

      for (CompiledDictionary dict : dicts.values()) {
        JarEntry dictEntry = new JarEntry(String.format("%s%s", DICTS_DIR, getEntryName(dict)));
        out.putNextEntry(dictEntry);
        // dict.serialize (out);
        dictSerializer.serialize(dict, out);
        out.closeEntry();
      }
    }
  }

  /**
   * Serializes jar files for a given TAM object into a JarOutputStream representing the .tam file.
   * 
   * @param out JarOutputStream representing the .tam file.
   * @param tam TAM object whose jars are to be written out to the output stream.
   * @throws IOException
   */
  private static void serializeJars(JarOutputStream out, TAM tam) throws IOException {
    Map<String, byte[]> jars = tam.getAllJars();
    if (jars != null && jars.size() > 0) {
      JarEntry jarsFolder = new JarEntry(JARS_DIR);
      out.putNextEntry(jarsFolder);
      out.closeEntry();

      TreeMap<String, String> jarNameToFileName = new TreeMap<String, String>();

      for (Entry<String, byte[]> mapEntry : jars.entrySet()) {
        // fix issue on GHE nlp/watson-nlp-tracker #386
        // author: a-oka
        // use UUID to define UDFs file name.
        String key = mapEntry.getKey();
        String basename = new File(key).getName();
        String uuid = UUID.randomUUID().toString();
        String fileName =
            String.format("%s-%s.jar", basename.substring(0, basename.lastIndexOf('.')), uuid);
        jarNameToFileName.put(key, fileName);

        JarEntry destEntry = new JarEntry(JARS_DIR + fileName);
        out.putNextEntry(destEntry);
        out.write(mapEntry.getValue());
        out.closeEntry();
      }

      // Generate the table that maps AQL names to file names.
      JarEntry tableEntry = new JarEntry(JARS_INDEX_FILE_NAME);
      out.putNextEntry(tableEntry);
      for (Entry<String, String> mapEntry : jarNameToFileName.entrySet()) {
        String quotedAQLName = StringUtils.quoteForCSV(mapEntry.getKey());
        String quotedFileName = StringUtils.quoteForCSV(mapEntry.getValue());
        String line = String.format("%s,%s\n", quotedAQLName, quotedFileName);
        out.write(line.getBytes("UTF-8"));
      }
      out.closeEntry();
    }
  }

  /**
   * Loads the specified moduleName from modulePath. Throws an exception if there are more than one
   * 
   * <pre>
   * &lt;moduleName&gt;.tam
   * 
   * <pre>
   * file in the given modulePath. The modulePath can be either a file:// or hdfs:// URI
   * 
   * @param moduleName The name of the module to be loaded (without the .tam extension)
   * @param modulePath A list, separated by semi-colon (;), of paths to directories or .jar/.zip
   *        archives where the compiled representation of modules referenced in the input modules
   *        are to be found.
   * 
   * @return TAM object representing the loaded module
   * @throws Exception
   */
  public static TAM load(String moduleName, String modulePath) throws Exception {
    if (null == moduleName) {
      throw new VerboseNullPointerException(TAMSerializer.class, "load", "module name");
    }
    if (null == modulePath) {
      throw new VerboseNullPointerException(TAMSerializer.class, "load", "module path");
    }
    ModuleResolver resolver = new ModuleResolver(modulePath);
    return loadInternal(moduleName, resolver);
  }

  /**
   * Loads the specified moduleName from system classpath. If multiple modules are found in system
   * classpath, then this method returns the module that occurs first in the classpath.
   * 
   * @param moduleName The name of the module to be loaded (without the .tam extension)
   * @return TAM object representing the loaded module
   * @throws Exception
   */
  public static TAM load(String moduleName) throws Exception {
    if (null == moduleName) {
      throw new VerboseNullPointerException(TAMSerializer.class, "load", "module name");
    }
    ModuleResolver resolver = new ModuleResolver(); // do not pass any modulePath. ModuleResolver
                                                    // will default it to
    // System classpath.
    return loadInternal(moduleName, resolver);
  }

  /**
   * Helper method that does the actual reading of TAM file.
   * 
   * @param moduleName name of the module stored in the TAM file
   * @param resolver object that knows how to find the TAM file for the specified module
   * @return in-memory representation of the TAM file
   * @throws Exception
   */
  private static TAM loadInternal(String moduleName, ModuleResolver resolver) throws Exception {
    TAM ret = new TAM(moduleName);

    InputStream in = null;
    JarInputStream jis = null;

    // Jar files are stored inside the TAM file with generated names, along with an index file.
    TreeMap<String, byte[]> rawJarFiles = new TreeMap<String, byte[]>();
    TreeMap<String, String> jarIndex = null;

    debug("Processing tam file for module %s", moduleName);

    try {
      in = resolver.resolve(moduleName);
      jis = new JarInputStream(in);
      JarEntry entry = null;
      while ((entry = jis.getNextJarEntry()) != null) {
        String entryName = entry.getName();
        byte[] content = ModuleUtils.readCurrJarEntry(jis);

        if (entryName.equals(AOG_FILE_NAME)) {
          ret.setAog(readAOG(new ByteArrayInputStream(content)));
        } else if (entryName.endsWith(COMPILED_DICT_EXTN)) {
          int idx = entryName.indexOf(DICTS_DIR);
          int begin = idx + DICTS_DIR.length();
          int end = entryName.lastIndexOf(COMPILED_DICT_EXTN);
          String dictName = entryName.substring(begin, end);


          try {
            ret.addDict(dictName, readDict(new ByteArrayInputStream(content)));
          } catch (IncompatibleCompiledDictException icde) {
            // this is the point where we can get the path to the TAM, so we
            // fill out that information before passing the exception up the stack.
            icde.setTamPath(resolver.resolveToPath(moduleName));
            throw new IncompatibleCompiledDictException(icde.getOutputMessage());
          }
        } else if (entryName.equals(METADATA_FILE_NAME)) {
          ret.setMetadata(readMetadata(new ByteArrayInputStream(content)));
        } else if (entryName.startsWith(JARS_DIR) && entryName.endsWith(".jar")) {
          // Jar file (stored under generated name)
          debug("Reading jar file %s", entryName);
          String fileName = entryName.substring(JARS_DIR.length());
          rawJarFiles.put(fileName, content);
        } else if (entryName.equals(JARS_INDEX_FILE_NAME)) {
          debug("Reading jar index");
          jarIndex = readJarIndex(content);
        } else {
          debug("Skipping entry %s", entryName);
        }

        jis.closeEntry();
      }

      // Organize the jar files we read. Note that, for older TAM files, we may not read any jar
      // files at all.
      if (null != jarIndex) {
        for (Entry<String, String> indexEntry : jarIndex.entrySet()) {
          String aqlName = indexEntry.getKey();
          String fileName = indexEntry.getValue();

          if (false == rawJarFiles.containsKey(fileName)) {
            throw new TextAnalyticsException(
                "Jar file for AQL jar name '%s' not found in TAM file for module %s", aqlName,
                moduleName);
          }

          ret.addJar(aqlName, rawJarFiles.get(fileName));
        }
      }

      // Check for orphan jar files
      for (String fileName : rawJarFiles.keySet()) {
        if (false == jarIndex.containsValue(fileName)) {
          throw new TextAnalyticsException("TAM file for module %s contains orphan jar file %s",
              moduleName, fileName);
        }
      }

      // Do some basic sanity checks
      if (null == ret.getAog()) {
        throw new Exception(String.format("No AOG found in TAM file for module %s", moduleName));
      }

      // TODO: If anything other than the AOG file is required, add additional
      // checks to make sure that everything was loaded.

      return ret;
    } finally {

      if (null != jis) {
        jis.close();
      }

      if (null != in) {
        in.close();
      }

    }
  }

  /**
   * Reads the metadata from the InputStream of metadata.xml file
   * 
   * @param inputStream InputStream to metadata.xml file
   * @return Metadata for a given module
   * @throws Exception if there are any issues in reading the metadata.
   */
  private static ModuleMetadata readMetadata(InputStream inputStream) throws Exception {
    return ModuleMetadataImpl.deserialize(inputStream);
  }

  /**
   * Reads the compiled dictionary from TAM file
   * 
   * @param inputStream InputStream to the compiled dictionary file stored in TAM.
   * @parma tamPath path to the TAM file containing this dictionary
   * @return Object representation of compiled dictionary
   * @throws IOException
   * @throws Exception
   */
  private static CompiledDictionary readDict(InputStream inputStream)
      throws IOException, Exception {
    return dictSerializer.deSerialize(inputStream);
  }

  /**
   * Reads the jar index file from a TAM file. The jar index file stores the mapping from AQL names
   * of jars to the generated names of jar files inside the TAM file.
   * 
   * @param fileContents raw contents of the file
   * @return map representation of the file's contents; key is AQL name
   */
  private static TreeMap<String, String> readJarIndex(byte[] fileContents) {
    TreeMap<String, String> ret = new TreeMap<String, String>();

    // This file is always generated by SystemT code, so don't need to worry about all the corner
    // cases that one can
    // encounter when parsing CSV.

    // Convert file's contents to string; assume UTF-8, since SystemT generated the file.
    String contentsStr;
    try {
      contentsStr = new String(fileContents, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new FatalInternalError("JVM can't read UTF-8 data.");
    }

    // Cut off last newline, split into lines, then split each line into 2 fields.
    CharSequence[] lines = StringUtils.split(StringUtils.chomp(contentsStr), '\n');
    for (CharSequence line : lines) {
      CharSequence[] fields = StringUtils.split(line, ',');
      String aqlName = StringUtils.unquoteFromCSV(fields[0]);
      String fileName = StringUtils.unquoteFromCSV(fields[1]);
      ret.put(aqlName, fileName);
    }

    return ret;
  }

  /**
   * Reads the AOG string from plan.aog
   * 
   * @param in InputStream to plan.aog of the tam file
   * @return String representation of AOG
   * @throws IOException
   */
  public static String readAOG(InputStream in) throws IOException {
    BufferedReader br = null;
    try {
      StringBuilder buf = new StringBuilder();
      br = new BufferedReader(new InputStreamReader(in, Constants.ENCODING_UTF8));
      String line = null;

      while ((line = br.readLine()) != null) {
        buf.append(line).append(Constants.NEW_LINE);
      }
      return buf.toString();
    } finally {
      if (br != null) {
        br.close();
      }
    }

  }

  /**
   * Return the file name to which compiled dictionary should get serialized.
   */
  public static String getEntryName(CompiledDictionary compiledDict) {
    // we use dictionary name, to come up with entry/file name where compiled dictionary is
    // serialized
    return compiledDict.getCompiledDictName() + Constants.COMPILED_DICT_FILE_EXT;
  }

  /**
   * Utility method to load meta-data for the specified moduleName from the modulePath.
   * 
   * @param moduleName name of the module (without the .tam extension), whose meta-data should
   *        should be loaded
   * @param modulePath a list, separated by semi-colon (;), of paths to directories or .jar/.zip
   *        archives where the compiled representation of modules referenced in the input modules
   *        are to be found
   * @return loaded module meta-data object
   * @throws Exception
   */
  public static ModuleMetadata loadModuleMetadata(String moduleName, String modulePath)
      throws Exception {
    if (null == moduleName) {
      throw new VerboseNullPointerException(TAMSerializer.class, "load", "module name");
    }
    if (null == modulePath) {
      throw new VerboseNullPointerException(TAMSerializer.class, "load", "module path");
    }

    ModuleResolver resolver = new ModuleResolver(modulePath);
    return loadInternalMetadata(moduleName, resolver);
  }

  /**
   * Utility method to load meta-data for the specified moduleName from system classpath. If
   * multiple modules are found in system classpath, then this method returns the module that occurs
   * first in the classpath.
   * 
   * @param moduleName name of the module (without the .tam extension), whose meta-data should
   *        should be loaded
   * @return loaded module meta-data object
   * @throws Exception
   */
  public static ModuleMetadata loadModuleMetadata(String moduleName) throws Exception {
    if (null == moduleName) {
      throw new VerboseNullPointerException(TAMSerializer.class, "load", "module name");
    }

    ModuleResolver resolver = new ModuleResolver();
    return loadInternalMetadata(moduleName, resolver);
  }

  /**
   * Helper method to read only meta-data from the compiled module(tam) file .
   * 
   * @param moduleName name of the module (without the .tam extension), whose meta-data should
   *        should be loaded
   * @param resolver module resolver used to arrive at the compiled form based on the module name
   * @return loaded module meta-data object
   * @throws Exception
   */
  private static ModuleMetadata loadInternalMetadata(String moduleName, ModuleResolver resolver)
      throws Exception {
    ModuleMetadata ret = null;

    InputStream in = null;
    JarInputStream jis = null;

    try {
      in = resolver.resolve(moduleName);
      jis = new JarInputStream(in);
      JarEntry entry = null;
      while ((entry = jis.getNextJarEntry()) != null) {
        if (entry.getName().equals(METADATA_FILE_NAME)) {
          byte[] content = ModuleUtils.readCurrJarEntry(jis);
          ret = readMetadata(new ByteArrayInputStream(content));
        }
        jis.closeEntry();
      }
    } finally {

      if (jis != null)
        jis.close();
      if (in != null)
        in.close();
    }
    return ret;
  }

}
