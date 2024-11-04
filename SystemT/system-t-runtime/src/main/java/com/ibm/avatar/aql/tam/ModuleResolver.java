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

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.ibm.avatar.algebra.util.file.FileOperations;
import com.ibm.avatar.algebra.util.file.FileOperations.FileSystemType;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.AmbiguousModuleMatchException;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.ModuleMatchException;
import com.ibm.avatar.api.exceptions.ModuleNotFoundException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Attempts to locate a .tam file for a given module name in the specified module path. This class
 * supports: <br>
 * a) loading TAM files from HDFS, GPFS, and Local File System URIs <br>
 * b) loading TAM files found under a directory (or) within a JAR/ZIP file
 * 
 */
public class ModuleResolver {

  /**
   * A list of semicolon-separated URIs (either hdfs://, gpfs://, or file://) to directories or
   * .jar/.zip archives where the compiled representation (.tam) of modules are found.
   */
  private String modulePathURI;

  /**
   * Enumeration for various policies applied when loading modules
   */
  private enum ModuleLoadPolicy {
    /**
     * Used when modules are to be loaded from system classpath. This policy does always returns the
     * first match and does not throw an error if more than one match is found in system classpath.
     */
    RETURN_FIRST_MATCH,

    /**
     * Used when modules are to be loaded from a specified module path. This policy throws
     * AmbiguousModuleMatchException if more than one .tam file with same name is found in module
     * path.
     */
    DISALLOW_DUPLICATES
  };

  /**
   * Determines the policy to use when loading modules. Defaults to
   * 
   * <pre>
   * ModuleLoadPolicy.DISALLOW_DUPLICATES
   * </pre>
   */
  private ModuleLoadPolicy moduleLoadPolicy = ModuleLoadPolicy.DISALLOW_DUPLICATES;

  /**
   * Stores the individual *unique* entries in modulePathURI
   */
  private Set<URI> entries = new HashSet<URI>();

  /**
   * Default constructor. Attempts to locate .tam file from system classpath
   */
  public ModuleResolver() throws Exception {
    // Load from class path
    moduleLoadPolicy = ModuleLoadPolicy.RETURN_FIRST_MATCH;
  }

  /**
   * Main constructor for this class
   * 
   * @param modulePathURIParam A list of semicolon-separated URIs (either hdfs://, gpfs://, or
   *        file://) to directories or .jar/.zip archives where the compiled representation (.tam)
   *        of modules are found.
   * @throws Exception
   */
  public ModuleResolver(String modulePathURIParam) throws Exception {
    // The module path should already be normalized and resolved to a fully valid URI by this point,
    // but since this constructor is public, it can't hurt to re-normalize and resolve it.
    // Fixes defect by removing extra semicolons at end
    modulePathURI = FileOperations.resolveModulePath(modulePathURIParam);
    String[] normalizedPaths = StringUtils.split(modulePathURI, Constants.MODULEPATH_SEP_CHAR);

    // flags to identify if path contains multiple distributed filesystems
    boolean pathContainsHDFS = false;
    boolean pathContainsGPFS = false;

    for (String path : normalizedPaths) {

      // getting the file system type checks to see whether this file system is supported
      FileSystemType fileSystemType = FileOperations.getFileSystemType(path);

      switch (fileSystemType) {
        case HDFS:
          pathContainsHDFS = true;

          if (pathContainsGPFS) {
            throw new TextAnalyticsException("Cannot have both HDFS and GPFS URIs in module path");
          }
          break;

        case GPFS:
          pathContainsGPFS = true;

          if (pathContainsHDFS) {
            throw new TextAnalyticsException("Cannot have both HDFS and GPFS URIs in module path");
          }
          break;

        default:
      }

      entries.add(new URI(path));
    }

  }

  /**
   * Attempts to load the given module from either classpath or specified modulePath.
   * 
   * @param moduleName The name of the module to load
   * @return An InputStream to the module file.
   * @throws ModuleMatchException If the specified module is not found,
   *         {@link ModuleNotFoundException} is thrown. When attempting to load a module from system
   *         classpath, the first occurring match is returned. When loading from a specified
   *         modulePath, if more than one match is found for the given module name,
   *         {@link AmbiguousModuleMatchException} is thrown.
   */
  public InputStream resolve(String moduleName) throws Exception {
    String tamFileName = String.format("%s.tam", moduleName);

    /* BEGIN: Load TAM through class loader */
    // Load from class path if modulepath is null & policy is to return first match
    if (modulePathURI == null && ModuleLoadPolicy.RETURN_FIRST_MATCH.equals(moduleLoadPolicy)) {
      InputStream in = loadFromClasspath(tamFileName);
      if (null == in) {
        throw new ModuleNotFoundException(moduleName);
      } else {
        return in;
      }
    }
    /* END: Load TAM through class loader */

    /* BEGIN: Load TAM from modulePath */
    // List of all matches for a given module name
    ArrayList<String> matches = new ArrayList<String>();
    URI matchedURI = null;

    for (URI uri : entries) {

      String path = uri.toString();

      // if path is a directory, then look for tam file within it
      if (FileOperations.isDirectory(path) && FileOperations.contains(path, tamFileName)) {

        // return the match, if the policy is RETURN_FIRST_MATCH
        if (moduleLoadPolicy.equals(ModuleLoadPolicy.RETURN_FIRST_MATCH)) {

          // ensure valid URI String by creating URI then converting it to String to eliminate
          // illegal characters
          return FileOperations.getStream(FileOperations.constructValidURI(path, tamFileName));
        }
        // add the path to matches list if the policy is ModuleLoadPolicy.DISALLOW_DUPLICATES
        else if (moduleLoadPolicy.equals(ModuleLoadPolicy.DISALLOW_DUPLICATES)) {
          matches.add(path);
          matchedURI = uri;
        }
      }

      // else if path is a JAR/ZIP file, then look for a tam file within it.
      else if (isJarOrZip(path) && FileOperations.exists(path)) {
        // check if the archive contains the TAM
        if (ModuleUtils.archiveContainsFile(uri, tamFileName)) {
          // return the match, if the policy is RETURN_FIRST_MATCH
          if (moduleLoadPolicy.equals(ModuleLoadPolicy.RETURN_FIRST_MATCH)) {
            return ModuleUtils.readTAMFromArchive(uri, tamFileName);
          }
          // add the path to matches list if the policy is ModuleLoadPolicy.DISALLOW_DUPLICATES
          else if (moduleLoadPolicy.equals(ModuleLoadPolicy.DISALLOW_DUPLICATES)) {
            matches.add(path);
            matchedURI = uri;
          }
        }
      }
    }

    if (matches.size() > 1) {
      throw new AmbiguousModuleMatchException(moduleName, modulePathURI, matches);
    }

    if (matches.size() == 1) {
      // return an InputStream to the match

      String uri = matches.get(0);

      // return a stream to the resource
      if (FileOperations.isDirectory(uri)) {

        return FileOperations.getStream(FileOperations.constructValidURI(uri, tamFileName));
      } else if (isJarOrZip(uri)) {
        return ModuleUtils.readTAMFromArchive(matchedURI, tamFileName);
      }
    }

    // if the control reaches here, then module is not found.
    throw new ModuleNotFoundException(moduleName, modulePathURI);

    /* END: Load TAM from modulePath */
  }

  /**
   * Returns the path location of the first match within the module path. Used to identify where the
   * module is located after the module is loaded (for error messages), so this does not check
   * module load policies.
   * 
   * @param moduleName the name of the module to resolve into a path location
   * @return the location of the module whose name is the input parameter
   */
  public String resolveToPath(String moduleName) throws ModuleNotFoundException {

    String tamFileName = String.format("%s.tam", moduleName);

    for (URI uri : entries) {

      String path = uri.toString();

      try {
        // if path is a directory, then look for tam file within it
        if (FileOperations.isDirectory(path) && FileOperations.contains(path, tamFileName)) {

          return path + tamFileName;
        }

        // else if path is a JAR/ZIP file, then look for a tam file within it.
        else if (isJarOrZip(path) && FileOperations.exists(path)) {
          // check if the archive contains the TAM
          if (ModuleUtils.archiveContainsFile(uri, tamFileName)) {
            // return the match, if the policy is RETURN_FIRST_MATCH
            return path + "/" + tamFileName;
          }
        }
      } catch (Exception e) {
        // we should never see this, as this method should only be called on modules that have been
        // successfully loaded
        throw new FatalInternalError("Error finding module %s in module path entry %s: %s",
            moduleName, path, e.getMessage());
      }
    }

    // if the control reaches here, then module is not found.
    throw new ModuleNotFoundException(moduleName, modulePathURI);
  }

  /**
   * Loads from the classpath in the following order:<br/>
   * 1) Exercises ThreadContext class loader <br/>
   * 2) Exercises current class's class loader which in-turn delegates to system class loader
   * 
   * @param tamFileName Name of the tam file to load
   */
  private InputStream loadFromClasspath(String tamFileName) {
    // Pass 1: Try loading from current thread's ContextClassLoader
    InputStream in =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(tamFileName);

    // Pass 2: Try loading from current class's class loader. This would delegate to parent class
    // loader and hence
    // System class path is also covered
    if (null == in) {
      in = this.getClass().getClassLoader().getResourceAsStream(tamFileName);
    }

    return in;
  }

  /**
   * Determines whether the specified resource is a JAR or ZIP. This method does NOT check for
   * existence of the file.
   * 
   * @param resourceURI Absolute URI of the resource
   * @return true, if the resource is a Jar file or zip file; false otherwise.
   */
  private boolean isJarOrZip(String resourceURI) {
    return null != resourceURI && (resourceURI.endsWith(Constants.JAR_EXTENSION)
        || resourceURI.endsWith(Constants.ZIP_EXTENSION));
  }

}
