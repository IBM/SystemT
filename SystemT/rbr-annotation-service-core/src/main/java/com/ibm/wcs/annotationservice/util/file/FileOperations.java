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

package com.ibm.wcs.annotationservice.util.file;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.api.exceptions.URIToFileException;
import com.ibm.avatar.logging.Log;
import com.ibm.avatar.logging.MsgType;

/**
 * Handles file operations for Local File System, HDFS, and GPFS.
 * 
 */
@SuppressWarnings({"all"})
public abstract class FileOperations {
  private static boolean debug = false;

  // supported filesystems
  public enum FileSystemType {
    LOCAL_FS("file"), // local filesystem
    HDFS("hdfs"), // Hadoop Distributed File System
    GPFS("gpfs"); // IBM General Parallel File System

    // URI scheme associated with this file system type
    private final String scheme;

    private FileSystemType(String scheme) {
      this.scheme = scheme;
    }

    public String getScheme() {
      return this.scheme;
    }
  }

  /*--------------------------------------- BEGIN: Abstract file operations methods*/

  /**
   * Opens up an InputStream for the given URI
   * 
   * @param uri Absolute URI of the resource that should be opened up for reading.
   * @return InputStream for the given URI.
   * @throws Exception
   */
  protected abstract InputStream getStreamImpl(String uri) throws Exception;

  /**
   * Checks whether a given resource is a file
   * 
   * @param uri Absolute URI of the resource
   * @return true, if the URI represents a file; false, otherwise.
   * @throws Exception
   */
  protected abstract boolean isFileImpl(String uri) throws Exception;

  /**
   * Checks whether a given resource is a directory
   * 
   * @param uri Absolute URI of the resource
   * @return true, if the URI represents a directory; false, otherwise.
   * @throws Exception
   */
  protected abstract boolean isDirectoryImpl(String uri) throws Exception;

  /**
   * Checks if the given URI exists on the file system
   * 
   * @param uri Absolute URI of the resource
   * @return true, if the URI exists; false, otherwise.
   * @throws Exception
   */
  protected abstract boolean existsImpl(String uri) throws Exception;

  /**
   * Checks whether the parent directory contains a child resource with the given name
   * 
   * @param parentDirURI Absolute URI of the parent directory
   * @param childName Name of the child resource
   * @return true, if the parent directory contains a child resource of given name; false,
   *         otherwise.
   * @throws Exception
   */
  protected abstract boolean containsImpl(String parentDirURI, String childName) throws Exception;

  /**
   * Returns the full path of a URI String that contains no invalid URI characters. <br/>
   * This function creates a full path URI from the directory URI and filename, then converts it to
   * a String object to eliminate invalid characters. Call this method instead of manually making a
   * full path string argument to a FileOperations method. <br/>
   * Fixes defect : Module names that contain illegal URI characters are not handled correctly.
   * 
   * @param directoryURI the directory containing the file to operate on
   * @param name the filename we are operating on
   * @return a valid URI String containing the full path of the filename we are operating on
   * @throws Exception
   */
  protected abstract String constructValidURIImpl(String directoryURI, String name)
      throws Exception;

  /**
   * Copies a file from the source fileSystem to the local fileSystem.
   * 
   * @param src Path on the source fileSystem.
   * @param localDest Destination path on the local fileSystem.
   * 
   * @throws Exception
   */
  protected abstract void copyFileToLocalImpl(String src, String localDest) throws Exception;

  /**
   * Copies a directory from the source fileSystem to the local fileSystem.
   * 
   * @param src Path on the source fileSystem.
   * @param localDest Destination path on the local fileSystem.
   * 
   * @throws Exception
   */
  protected abstract void copyDirToLocalImpl(String src, String localDest) throws Exception;

  /**
   * Computes the checksum of the directory. A sorted recursive list of files will be generated for
   * the given directory, and the md5 hash of that list will be returned by this function.
   * 
   * @param dirPath Path of the directory for which the checksum needs to be computed.
   * @return Checksum of the directory which is computed as described above.
   * @throws Exception
   */
  protected abstract String getDirChecksumImpl(String dirPath) throws Exception;



  /* END: Abstract file operations methods-------------------------- */

  /* Static methods to call the right implementation methods */

  /**
   * Opens up an InputStream for the given URI
   * 
   * @param uri Absolute URI of the resource that should be opened up for reading.
   * @return InputStream for the given URI.
   * @throws Exception
   */
  public synchronized static InputStream getStream(String uri) throws Exception {
    FileOperations fileOps = FileOperations.getInstance(FileOperations.getFileSystemType(uri));

    return fileOps.getStreamImpl(uri);
  }

  /**
   * Checks whether a given resource is a file
   * 
   * @param uri Absolute URI of the resource
   * @return true, if the URI represents a file; false, otherwise.
   * @throws Exception
   */
  public synchronized static boolean isFile(String uri) throws Exception {
    FileOperations fileOps = FileOperations.getInstance(FileOperations.getFileSystemType(uri));

    return fileOps.isFileImpl(uri);
  }

  /**
   * Checks whether a given resource is a directory
   * 
   * @param uri Absolute URI of the resource
   * @return true, if the URI represents a directory; false, otherwise.
   * @throws Exception
   */
  public synchronized static boolean isDirectory(String uri) throws Exception {
    FileOperations fileOps = FileOperations.getInstance(FileOperations.getFileSystemType(uri));

    return fileOps.isDirectoryImpl(uri);
  }

  /**
   * Checks if the given URI exists on the file system
   * 
   * @param uri Absolute URI of the resource
   * @return true, if the URI exists; false, otherwise.
   * @throws Exception
   */
  public synchronized static boolean exists(String uri) throws Exception {
    FileOperations fileOps = FileOperations.getInstance(FileOperations.getFileSystemType(uri));

    return fileOps.existsImpl(uri);
  }

  /**
   * Checks whether the parent directory contains a child resource with the given name
   * 
   * @param parentDirURI Absolute URI of the parent directory
   * @param childName Name of the child resource
   * @return true, if the parent directory contains a child resource of given name; false,
   *         otherwise.
   * @throws Exception
   */
  public synchronized static boolean contains(String parentDirURI, String childName)
      throws Exception {
    FileOperations fileOps =
        FileOperations.getInstance(FileOperations.getFileSystemType(parentDirURI));

    return fileOps.containsImpl(parentDirURI, childName);
  }

  /**
   * Copies a file from src FileSystem to the dest FileSystem.
   * 
   * @param srcPath Path of the file on the source FS.
   * @param localDestPath Destination path on the local FS.
   *
   * @throws Exception
   */
  public synchronized static void copyFileToLocal(String srcPath, String localDestPath)
      throws Exception {

    FileOperations fileOps = FileOperations.getInstance(FileOperations.getFileSystemType(srcPath));

    File localDestFile = new File(localDestPath);
    if (localDestFile.exists()) {
      if (localDestFile.isDirectory()) {
        org.apache.commons.io.FileUtils.deleteDirectory(localDestFile);
      } else {
        localDestFile.delete();
      }
    }

    fileOps.copyFileToLocalImpl(srcPath, localDestPath);
  }

  /**
   * Copies a directory from src FileSystem to the dest FileSystem.
   * 
   * @param srcPath Path of the directory on the source FS.
   * @param localDestPath Destination path on the local FS.
   *
   * @throws Exception
   */
  public synchronized static void copyDirToLocal(String srcPath, String localDestPath)
      throws Exception {

    FileOperations fileOps = FileOperations.getInstance(FileOperations.getFileSystemType(srcPath));

    File localDestFile = new File(localDestPath);
    if (localDestFile.exists()) {
      if (localDestFile.isDirectory()) {
        org.apache.commons.io.FileUtils.deleteDirectory(localDestFile);
      } else {
        localDestFile.delete();
      }
    }
    fileOps.copyDirToLocalImpl(srcPath, localDestPath);
  }

  /**
   * Returns the full path of a URI String that contains no invalid URI characters. <br/>
   * This function creates a full path URI from the directory URI and filename, then converts it to
   * a String object to eliminate invalid characters. Call this method instead of manually making a
   * full path string argument to a FileOperations method. <br/>
   * 
   * @param directoryURI the directory containing the file to operate on
   * @param name the filename we are operating on
   * @return a valid URI String containing the full path of the filename we are operating on
   * @throws Exception
   */
  public synchronized static String constructValidURI(String directoryURI, String name)
      throws Exception {
    FileOperations fileOps =
        FileOperations.getInstance(FileOperations.getFileSystemType(directoryURI));

    return fileOps.constructValidURIImpl(directoryURI, name);
  }

  /**
   * 
   * @param dirPath
   * @return
   * @throws Exception
   */
  public synchronized static String getDirChecksum(String dirPath) throws Exception {

    FileOperations fileOps = FileOperations.getInstance(FileOperations.getFileSystemType(dirPath));

    return fileOps.getDirChecksumImpl(dirPath);
  }

  /**
   * Returns a normalized URI string. The following normalizations are performed: <br/>
   * 1) For URIs pointing to a directory, a trailing '/' character is appended if it already does
   * not contains one. <br/>
   * 
   * @param origPath a raw URI passed into the params object
   * @return normalized URI
   */
  public synchronized static String normalizeURI(String origPath) {
    String normalizedPath = origPath;

    // Normalize the URIs pointing to the directory by appending '/' character to the URIs; only
    // URIs which do not
    // contain the trailing '/' character are normalized
    if (!(origPath.endsWith(Constants.JAR_EXTENSION) || origPath.endsWith(Constants.ZIP_EXTENSION))
        && !origPath.endsWith("/")) {
      normalizedPath = origPath + "/";
    }

    return normalizedPath;
  }

  /**
   * Takes in a URI string that may or may not have a scheme. <br>
   * Resolves all scheme-less URI strings to local filesystem. <br>
   * Use instead of resolvePath() in compile time.
   * 
   * @param origPath path to filename
   * @return a URI string with a file:/ scheme
   * @throws Exception
   */
  public synchronized static String resolvePathToLocal(String origPath) throws URIToFileException {
    try {
      // check to see if this path already has a scheme
      if (origPath.startsWith(Constants.LOCALFS_URI_SCHEME))
        return origPath;
      if (origPath.startsWith(Constants.HDFS_URI_SCHEME)
          || origPath.startsWith(Constants.GPFS_URI_SCHEME)) {
        throw new TextAnalyticsException(
            "Expected local URI string but got a distributed FS URI %s", origPath);
      }

      String resolvedPath = FileUtils.createValidatedFile(origPath).toURI().toString();

      if (debug) {
        Log.info("resolvePathToLocal: %s resolved to %s ", origPath, resolvedPath);
      }

      return resolvedPath;
    } catch (Throwable t) {
      // rewrap into a more specific exception
      throw new URIToFileException(t, origPath);
    }
  }

  /**
   * Takes a path string, and if it has no scheme, auto-discovers what scheme (HDFS, GPFS, File) to
   * use. <br/>
   * If it has a scheme, return it (and fix any illegal characters in the process).
   * 
   * @param path - String of a URI that can be a path (e.g., /path/to/file.tam), or a full URI
   * @return a legal well-formed URI String that can be used in the URI constructor without throwing
   *         an exception
   */
  public synchronized static String resolvePath(String path) throws URIToFileException {
    if (debug) {
      Log.info("resolvePath: %s", path);
    }

    // Hadoop filesystem property for recent versions of Hadoop (2.0.4+)
    final String NEW_HADOOP_FS_PROPERTY = "fs.defaultFS";

    // Hadoop filesystem property for early versions of Hadoop
    final String OLD_HADOOP_FS_PROPERTY = "fs.default.name";

    // indicates which property was used, for error messages
    String usedHadoopFSProperty = NEW_HADOOP_FS_PROPERTY;

    try {
      String constructedUriString = null;

      // check to see if this path needs resolving
      if (path.startsWith(Constants.HDFS_URI_SCHEME) || path.startsWith(Constants.GPFS_URI_SCHEME)
          || path.startsWith(Constants.LOCALFS_URI_SCHEME)) {
        if (debug) {
          Log.info("Path already has scheme.");
        }

        // it is possible to have a schemed URI with illegal characters (Jaql). This ensures we
        // return a legal URI
        // string.
        return encodeToValidURIFormat(path);
      }

      // scheme does not exist, so check to see if a DFS is installed
      final String HADOOP_CONF_DIR = System.getenv("HADOOP_CONF_DIR");

      if (HADOOP_CONF_DIR == null || HADOOP_CONF_DIR.isEmpty()) {
        // no distributed file system installed, assume the path is local
        return resolvePathToLocal(path);
      }
      // distributed file system installed, parse the config file for fs.default.name
      else {
        Class hdfsConfigClazz = Class.forName("org.apache.hadoop.conf.Configuration");

        // get default constructor for Configuration class- org.apache.hadoop.conf.Configuration()
        Constructor constructor = hdfsConfigClazz.getConstructor(new Class[0]);

        // create an instance of Configuration class, using default constructor created above
        // This loads two resources, core-default.xml and core-site.xml
        Object hdfsConfig = constructor.newInstance(new Object[0]);

        // parse the loaded Configuration for the filesystem property and construct the URI
        // correctly
        Method getPropertiesMethod = hdfsConfigClazz.getMethod("get", String.class);
        String fsName = (String) getPropertiesMethod.invoke(hdfsConfig, NEW_HADOOP_FS_PROPERTY);

        // backwards compatibility for old versions of Hadoop -- if we don't find the new property,
        // look for it under the name of the old property
        if (fsName == null) {
          fsName = (String) getPropertiesMethod.invoke(hdfsConfig, OLD_HADOOP_FS_PROPERTY);

          if (fsName == null) {
            throw new TextAnalyticsException(
                "Neither filesystem property '%s' nor deprecated filesystem property '%s' was found in HDFS configuration file located in %s.",
                NEW_HADOOP_FS_PROPERTY, OLD_HADOOP_FS_PROPERTY, HADOOP_CONF_DIR);
          } else {
            // we found the old property, so set usedHadoopFSProperty to it to report a meaningful
            // error message
            // later
            usedHadoopFSProperty = OLD_HADOOP_FS_PROPERTY;
          }

        }

        if (fsName.startsWith(Constants.HDFS_URI_SCHEME)
            || fsName.startsWith(Constants.GPFS_URI_SCHEME)) {
          URI fsURI = new URI(fsName);
          String authority = fsURI.getAuthority();

          // an undefined authority is expected in GPFS -- convert to String
          if (authority == null) {
            authority = new String("");
          }

          // verification that the file exists and is accessible is NOT performed here
          if (path.startsWith("/")) {

            // path is absolute, it starts with a "/" and so it can be appended directly to the
            // authority
            constructedUriString = fsURI.getScheme() + "://" + authority + path;
          } else {
            // path is relative, assume it's relative to DFS root and construct a URI by prepending
            // a "/"
            Log.log(MsgType.Info, "Input path '%s' is relative; assuming relative to DFS root.",
                path);
            constructedUriString = fsURI.getScheme() + "://" + authority + "/" + path;
          }
        } else if (fsName.startsWith(Constants.LOCALFS_URI_SCHEME)) {
          return resolvePathToLocal(path);
        } else {
          // indicate which property we couldn't parse
          throw new TextAnalyticsException(
              "Hadoop configuration property '%s' specifies unsupported scheme: %s.",
              usedHadoopFSProperty, fsName);
        }
      }

      return constructedUriString;
    }

    catch (Throwable t) {
      // rewrap into a more specific exception
      throw new URIToFileException(t, path);
    }
  }


  /**
   * Parses the URI String and returns the corresponding filesystem type. If the URI has no scheme,
   * auto-discover the correct filesystem type. Current filesystem types supported : local, HDFS,
   * GPFS
   * 
   * @param uriString a URI String
   * @return a String containing the type of filesystem this URI string refers to
   * @throws TextAnalyticsException
   */
  public synchronized static FileSystemType getFileSystemType(String uriString)
      throws TextAnalyticsException {
    if (uriString == null) {
      throw new TextAnalyticsException("Passed a null URI");
    }

    URI uri = createURI(uriString);
    String scheme = uri.getScheme();

    if (scheme.equals(FileSystemType.LOCAL_FS.getScheme())) {
      return FileSystemType.LOCAL_FS;
    } else if (scheme.equals(FileSystemType.HDFS.getScheme())) {
      return FileSystemType.HDFS;
    } else if (scheme.equals(FileSystemType.GPFS.getScheme())) {
      return FileSystemType.GPFS;
    } else {
      throw new TextAnalyticsException(
          String.format("Filesystem scheme not supported: %s", scheme));
    }
  }

  /**
   * Encodes URI Strings to a valid URI format to avoid throwing URISyntaxException when illegal
   * characters such as space are part of the URI String. This happens most often with the Jaql
   * interface.
   * 
   * @param uriStr
   * @return a valid URI String
   * @throws TextAnalyticsException
   */
  protected synchronized static String encodeToValidURIFormat(String uriStr)
      throws TextAnalyticsException {
    try {
      return uriStr.replaceAll(" ", "%20");
    } catch (NullPointerException npe) {
      throw new TextAnalyticsException(npe, "Attempted to use a null URI.");
    }

  }

  /**
   * Creates a URI from a URI String, which may not have a scheme or legal syntax.
   * 
   * @param uriStr URI String to turn into a URI object
   * @returns the URI made from the URI String
   * @throws TextAnalyticsException
   */
  public synchronized static URI createURI(String uriStr) throws TextAnalyticsException {
    try {
      // if the URI has no scheme, auto-discover the true scheme and fix any illegal characters
      String resolvedString = resolvePath(uriStr);
      return new URI(resolvedString);
    } catch (URISyntaxException e) {
      throw new TextAnalyticsException(e, "URI %s has illegal syntax.", uriStr);
    }
  }

  /*-----------------------------------BEGIN: Factory methods */

  /**
   * Returns the appropriate singleton instance of FileOperations based on the filesystem type.
   * Lazily creates the instance if it has not been instantiated yet. Currently supports local,
   * HDFS, and GPFS.
   * 
   * @param fileSystemType type of FileOperations singleton to return
   * @return the appropriate FileOperations singleton
   * @throws Exception
   */
  public static FileOperations getInstance(FileSystemType fileSystemType)
      throws TextAnalyticsException {
    if (fileSystemType == null)
      return null;

    switch (fileSystemType) {
      case LOCAL_FS:
        return LocalFileOperations.getInstance();

      // HDFS and GPFS aren't in use within CnC, so comment this out to avoid having to endure a
      // bloated classpath courtesy Hadoop dependencies
      /*
       * case HDFS: return HDFSFileOperations.getInstance ();
       * 
       * case GPFS: return GPFSFileOperations.getInstance ();
       */

      default:
        throw new TextAnalyticsException(
            String.format("Unable to get filesystem instance because FS type %s not supported.",
                fileSystemType.getScheme()));

    }

  }

  /* END: Factory methods ------------------------------------ */

  /* BEGIN: utility methods ----------------------------------- */

  protected String getMd5(String string) throws NoSuchAlgorithmException {
    MessageDigest m = MessageDigest.getInstance("MD5");
    byte[] data = string.getBytes();
    m.update(data, 0, data.length);
    BigInteger i = new BigInteger(1, m.digest());
    return i.toString(16);
  }

  /* END: utility methods ----------------------------------- */
}
