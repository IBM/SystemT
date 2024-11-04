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
package com.ibm.avatar.api.tam;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.ibm.avatar.algebra.util.file.FileOperations;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.CircularDependencyException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException.ExceptionType;
import com.ibm.avatar.aql.tam.ModuleMetadataImpl;
import com.ibm.avatar.aql.tam.MultiModuleMetadataImpl;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;

/**
 * This class provides APIs to load metadata of a module from the compiled AQL module file (i.e
 * <code>.tam</code> file). There are APIs to load metadata for single and multiple modules from the
 * given modulepath or classpath.
 *
 * @see ModuleMetadata
 */
public class ModuleMetadataFactory {
  /**
   * Loads the given module's metadata from the given module path, without instantiating the module.
   *
   * @param moduleName The name of the module whose metadata is to be loaded (without the .tam
   *        extension)
   * @param modulePath A list, separated by semi-colon (;), of URIs to directories or .jar/.zip
   *        archives where the compiled representation of module referenced in the
   *        <code>moduleName</code> parameter is to be found. Each entry in the list must be a fully
   *        qualified <code>file://</code> or <code>hdfs://</code> or <code>gpfs://</code> URI. See
   *        <a href="../../../../overview-summary.html#uri">Supported URI Formats</a> for details.
   * @return metadata of the given module
   * @throws TextAnalyticsException Exceptions are thrown under the following circumstances:
   *         <ul>
   *         <li>If moduleName is <code>null</code></li>
   *         <li>If module is not found</li>
   *         <li>If metadata cannot be deserialized properly</li>
   *         </ul>
   */
  public static ModuleMetadata readMetaData(String moduleName, String modulePath)
      throws TextAnalyticsException {
    try {
      modulePath = FileOperations.resolveModulePath(modulePath);
      return TAMSerializer.loadModuleMetadata(moduleName, modulePath);
    } catch (Throwable t) {
      throw TextAnalyticsException.convertToTextAnalyticsException(t, ExceptionType.RUNTIME_ERROR);
    }
  }

  /**
   * Loads the metadata for the given set of modules from the given module path, without
   * instantiating the module.
   *
   * @param moduleNames An array of names of modules whose metadata is to be loaded.
   * @param modulePath A list, separated by semicolons, of URIs to directories or .jar/.zip archives
   *        where the compiled representation of modules referenced in the <code>moduleNames</code>
   *        are to be found. Each entry in the list must be a fully qualified <code>file://</code>
   *        or <code>hdfs://</code> or <code>gpfs://</code> URI. See
   *        <a href="../../../../overview-summary.html#uri">Supported URI Formats</a> for details.
   * @return metadata of the given modules
   * @throws TextAnalyticsException Exceptions are thrown under the following circumstances:
   *         <ul>
   *         <li>If moduleNames is <code>null</code></li>
   *         <li>If module is not found</li>
   *         <li>If metadata cannot be deserialized properly</li>
   *         </ul>
   */
  public static ModuleMetadata[] readMetaData(String[] moduleNames, String modulePath)
      throws TextAnalyticsException {
    ModuleMetadata[] ret = new ModuleMetadataImpl[moduleNames.length];
    for (int i = 0; i < moduleNames.length; i++) {
      ret[i] = readMetaData(moduleNames[i], modulePath);
    }

    return ret;
  }

  /**
   * Returns the metadata of the given module, by locating the module file using the class loader as
   * explained below. First, locate the module file using the current thread's context class loader;
   * if the module is not found, locate using the current class loader. The metadata is loaded
   * without instantiating the module.
   *
   * @param moduleName The name of the module whose metadata is to be loaded (without the .tam
   *        extension)
   * @return metadata of the given module
   * @throws TextAnalyticsException Exceptions are thrown under the following circumstances:
   *         <ul>
   *         <li>If moduleName is <code>null</code></li>
   *         <li>If module is not found</li>
   *         <li>If metadata cannot be deserialized properly</li>
   *         </ul>
   */
  public static ModuleMetadata readMetaData(String moduleName) throws TextAnalyticsException {
    try {
      return TAMSerializer.loadModuleMetadata(moduleName);
    } catch (Throwable t) {
      throw TextAnalyticsException.convertToTextAnalyticsException(t, ExceptionType.RUNTIME_ERROR);
    }
  }

  /**
   * Returns the metadata of the given set of modules, by locating them using the class loader as
   * explained below. First, locate using the current thread's context class loader; if the module
   * is not found, locate using the current class loader. The metadata is loaded without
   * instantiating the module.
   *
   * @param moduleNames An array of names of modules whose metadata is to be loaded.
   * @return metadata of the given modules
   * @throws TextAnalyticsException Exceptions are thrown under the following circumstances:
   *         <ul>
   *         <li>If moduleNames is <code>null</code></li>
   *         <li>If module is not found</li>
   *         <li>If metadata cannot be deserialized properly</li>
   *         </ul>
   */
  public static ModuleMetadata[] readMetaData(String[] moduleNames) throws TextAnalyticsException {
    ModuleMetadata[] ret = new ModuleMetadataImpl[moduleNames.length];
    for (int i = 0; i < moduleNames.length; i++) {
      ret[i] = readMetaData(moduleNames[i]);
    }

    return ret;
  }

  /**
   * Returns the metadata of all modules found in the given module path, without instantiating
   * modules.
   *
   * @param modulePath A list, separated by semi-colons, of URIs to directories or .jar/.zip
   *        archives where the compiled representation of modules referenced in the input modules
   *        are to be found. Each entry in the list must be a fully qualified <code>file://</code>
   *        URI. Currently, this API does not support </code>hdfs://</code> URIs. See
   *        <a href="../../../../overview-summary.html#uri">Supported URI Formats</a> for details.
   * @return An array of ModuleMetadata objects
   */
  public static ModuleMetadata[] readAllMetaData(String modulePath) {
    // First, check for null or whitespace
    if (StringUtils.isNullOrWhiteSpace(modulePath)) {
      return new ModuleMetadata[0];
    }

    // Split modulePath into individual path entries
    String[] modulePaths = modulePath.split(String.valueOf(Constants.MODULEPATH_SEP_CHAR));

    ArrayList<ModuleMetadata> ret = new ArrayList<ModuleMetadata>();

    // Iterate over each modulePathEntry
    for (String modulePathEntry : modulePaths) {
      try {
        // Attempt to create a URI. Any illegal URI will be caught through exception
        URI uri = new URI(modulePathEntry);
        try {
          // As of v2.0, this API supports only file://. Attempt to create a File object out of the
          // URI
          File fileURI = new File(uri);
          if (fileURI.isDirectory()) {
            // Retrieve metadata from all .tam files under the directory
            readMetadataFromTAMFilesInDir(fileURI, ret);
          } else if (fileURI.getName().endsWith(".jar") || fileURI.getName().endsWith(".zip")) {
            // Retrieve metadata from all .tam files under the jar file
            readMetadataFromTAMFilesInJarOrZip(fileURI, ret);
          }
        } catch (Exception e) {
          Log.info(
              "Cannot locate metadata from modulePath %s because it is not a valid file:// URI",
              modulePathEntry);
        }
      } catch (URISyntaxException e) {
        Log.info("Cannot locate metadata from modulePath %s because it is not a valid URI",
            modulePathEntry);
      }
    }

    return ret.toArray(new ModuleMetadata[0]);
  }

  /**
   * Returns the {@link com.ibm.avatar.api.tam.MultiModuleMetadata} object formed from the modules
   * listed in moduleNames. This object is formed using not only the modules in the module set, but
   * all their dependent modules as well. The modules are looked for in the system classpath or the
   * current thread’s context classpath.
   *
   * @param moduleNames the modules for which to construct multi-module metadata around
   * @return the multi-module metadata for the modules in the module set and their dependent modules
   */
  public static MultiModuleMetadata readAllMetaData(String[] moduleNames) throws Exception {
    // Pass null as the module path to read from the system classpath / current thread's context
    // classpath
    return readAllMetaData(moduleNames, null, null);
  }

  /**
   * Returns the {@link com.ibm.avatar.api.tam.MultiModuleMetadata} object formed from the modules
   * listed in moduleNames. This object is formed using not only the modules in the module set, but
   * all their dependent modules as well. The modules are looked for in the module path specified in
   * the input parameter modulePath.
   *
   * @param moduleNames the modules for which to construct multi-module metadata around
   * @return the multi-module metadata for the modules in the module set and their dependent modules
   */
  public static MultiModuleMetadata readAllMetaData(String[] moduleNames, String modulePath)
      throws Exception {
    return readAllMetaData(moduleNames, modulePath, null);
  }

  /**
   * Returns the {@link com.ibm.avatar.api.tam.MultiModuleMetadata} object formed from the modules
   * listed in moduleNames. This object is formed using not only the modules in the module set, but
   * all their dependent modules as well. The modules are looked for in the module path specified in
   * the input parameter modulePath.
   *
   * @param moduleNames the modules for which to construct multi-module metadata around
   * @return the multi-module metadata for the modules in the module set and their dependent modules
   */
  public static MultiModuleMetadata readAllMetaData(String[] moduleNames, String modulePath,
      TreeMap<String, TAM> nameToTAM) throws Exception {
    // Stack to maintain the list of modules, whose dependents are yet to be visited
    Stack<String> stack = new Stack<String>();

    // List of visited modules, this is maintained to not re-visit. already visited modules
    List<String> visited = new ArrayList<String>();

    TreeSet<String> moduleSet = new TreeSet<String>();

    List<ModuleMetadataImpl> moduleMetadataList = new ArrayList<ModuleMetadataImpl>();

    // recursively compute the full module set and their metadata, including all dependent modules
    for (String startModule : moduleNames) {
      if (false == visited.contains(startModule)) {
        moduleSet.addAll(
            buildModuleSet(startModule, stack, visited, moduleMetadataList, modulePath, nameToTAM));
      }
    }

    MultiModuleMetadataImpl mmm = MultiModuleMetadataImpl.createEmptyMMDInstance();

    mmm.setModules(moduleMetadataList);
    mmm.setModuleNames(moduleSet);

    // validate the computed module set
    mmm.validateDocSchemaCompatibility();
    mmm.validateTokenizerCompatibility();
    mmm.validateConflictingOutputNames();
    mmm.validateConflictingElementNames();

    return mmm;
  }

  /**
   * Helper method to traverse the graph (modules and their dependents) in depth-first order; this
   * method identifies the cycle, reports them and later breaks the cycle.
   * 
   * @param currentModule name of the module to be traversed
   * @param stack Stack to maintain the list of modules, whose dependents are yet to be visited
   * @param visited list of visited modules
   * @param dependencies map of input modules and its dependent modules (with error location info)
   * @param TAM objects that have been already read to avoid unnecessary load TAM file
   */
  private static TreeSet<String> buildModuleSet(String currentModule, Stack<String> stack,
      List<String> visited, List<ModuleMetadataImpl> moduleMetadataList, String modulePath,
      TreeMap<String, TAM> nameToTAM) throws Exception {
    TreeSet<String> ret = new TreeSet<String>();
    List<String> dependencies;

    int foundAtIndex = stack.indexOf(currentModule);

    // We have cycle if the currentModule is in the module stack, terminate with exception
    // explaining the
    // dependency
    // this specific check is probably never reachable but leaving in just in case
    if (-1 != foundAtIndex) {
      throw new CircularDependencyException(currentModule, stack);
    }

    stack.push(currentModule);
    visited.add(currentModule);

    // load and add the current module's metadata to the list of metadata
    ModuleMetadataImpl moduleMetadata;
    if (nameToTAM != null && nameToTAM.containsKey(currentModule)) {
      moduleMetadata = (ModuleMetadataImpl) nameToTAM.get(currentModule).getMetadata();
    } else if (modulePath == null) {
      moduleMetadata = (ModuleMetadataImpl) TAMSerializer.load(currentModule).getMetadata();
    } else {
      moduleMetadata =
          (ModuleMetadataImpl) TAMSerializer.load(currentModule, modulePath).getMetadata();
    }

    moduleMetadataList.add(moduleMetadata);

    // traverse all non-visited dependencies
    dependencies = moduleMetadata.getDependentModules();

    if (false == dependencies.isEmpty()) {
      for (String dependentModule : dependencies) {
        if (false == visited.contains(dependentModule)) {
          ret.addAll(buildModuleSet(dependentModule, stack, visited, moduleMetadataList, modulePath,
              nameToTAM));
        } else {
          // we have a dependency cycle if the already-visited dependent module is in the stack,
          // throw an exception
          if (stack.indexOf(dependentModule) != -1) {
            throw new CircularDependencyException(dependentModule, stack);
          }
        }
      }
    }

    // we're done with this module and all of its dependencies
    // add it to the module set and remove it from the module stack
    ret.add(currentModule);

    stack.pop();

    return ret;

  }

  /**
   * Reads metadata from .tam files found in given directory
   * 
   * @param dir Directory where .tam files are to be located
   * @param ret Return object - list of ModuleMetadata of .tam files found in given directory
   * @throws TextAnalyticsException
   */
  private static void readMetadataFromTAMFilesInDir(File dir, ArrayList<ModuleMetadata> ret)
      throws TextAnalyticsException {
    File[] tamFiles = dir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.endsWith(".tam");
      }
    });

    // for each .tam file
    for (File file : tamFiles) {
      // process .tam file
      if (true == file.getName().endsWith(".tam")) {
        // compute moduleName
        String fileName = file.getName();
        String moduleName = getModuleName(fileName);

        // read metadata and add it to return list
        ret.add(readMetaData(moduleName, file.getParentFile().toURI().toString()));
      }
    }
  }

  /**
   * Reads metadata from .tam files found in given jar or zip file
   * 
   * @param jarOrZipFile Archive file where .tam files are to be searched for
   * @param ret Return object - list of ModuleMetadata of .tam files found in given directory
   * @throws TextAnalyticsException
   */
  private static void readMetadataFromTAMFilesInJarOrZip(File jarOrZipFile,
      ArrayList<ModuleMetadata> ret) throws TextAnalyticsException {
    try {
      String jarURI = jarOrZipFile.toURI().toString();

      // Iterate over entries of JAR file
      JarFile jarFile = new JarFile(jarOrZipFile);
      Enumeration<JarEntry> jarEntries = jarFile.entries();
      while (jarEntries.hasMoreElements()) {
        JarEntry jarEntry = jarEntries.nextElement();

        // read metadata if a .tam is found within the JAR
        if (true == jarEntry.getName().endsWith(".tam")) {
          String moduleName = getModuleName(jarEntry.getName());
          ret.add(readMetaData(moduleName, jarURI));
        }
      }
      jarFile.close();
    } catch (Throwable t) {
      throw TextAnalyticsException.convertToTextAnalyticsException(t, ExceptionType.RUNTIME_ERROR);
    }
  }

  /**
   * Truncates the ".tam" part of the file name and returns the first part as moduleName
   * 
   * @param tamFileName Name of the .tam file (with the .tam suffix)
   * @return moduleName part of the .tam file
   */
  private static String getModuleName(String tamFileName) {
    if (StringUtils.isNullOrWhiteSpace(tamFileName)) {
      return null;
    }
    int index = tamFileName.lastIndexOf(".tam");
    return tamFileName.substring(0, index);
  }
}
