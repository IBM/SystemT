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

// import java.io.File;
// import java.io.FileNotFoundException;
// import java.io.IOException;
// import java.io.InputStream;
// import java.lang.reflect.Constructor;
// import java.lang.reflect.Method;
// import java.math.BigInteger;
// import java.security.MessageDigest;
// import java.util.TreeSet;
//
// import org.apache.commons.lang.StringUtils;
// import org.apache.hadoop.conf.Configuration;
// import org.apache.hadoop.fs.FileStatus;
// import org.apache.hadoop.fs.FileSystem;
// import org.apache.hadoop.fs.FileUtil;
// import org.apache.hadoop.fs.Path;
//
// @SuppressWarnings({ "all" })
// public final class HDFSFileOperations extends FileOperations {
//
// /**
// * Singleton instance of this class. Marked as volatile so that
// * multi-threaded calls will guarantee seeing a completely initialized
// * object.
// */
// static volatile HDFSFileOperations singleton;
//
// /**
// * Returns a handle to the singleton instance of this class. If this
// * instance has not yet been created, lazily initialize it. When creating an
// * instance of the filesystem, the JVM loads classes specific to the DFS
// * that will throw a ClassNotFoundException on systems without that DFS
// * installed.
// *
// * @return a pointer to the HDFS Filesystem handler
// */
// public static HDFSFileOperations getInstance() {
// if (singleton == null) {
// synchronized (HDFSFileOperations.class) {
// if (singleton == null) {
// try {
// singleton = new HDFSFileOperations();
// } catch (Exception e) {
// throw new RuntimeException("Error instantiating Hadoop Filesystem", null);
// }
// }
// }
// }
// return singleton;
// }
//
// /**
// * This class provides basic file operations for Hadoop distributed file
// * system. While running this class expects hadoop-core*.jar and its
// * dependent jars in the classpath; in addition to these jars, this class
// * also expects the Hadoop configurations: core-site.xml and hdfs-site.xml
// * in the classpath.
// */
// /** Caching the loaded classes */
// private static Class hdfsConfigClazz, hdfsPathClazz, fileSystemClazz;
//
// /**
// * Hadoop configuration instance, created based on the configuation files in
// * the classpath
// */
// private static Object hdfsConfig;
//
// /** Handle to HDFS file system instance */
// private static Object fileSystem;
//
// /**
// * Flag to signal that the class in initialized; that is, all the Hadoop
// * classes are loaded and handle to HDFS file system is initialized
// */
// private static boolean initialized = false;
//
// /**
// * Main constructor to create an instance of HDFS file operations.
// *
// * @throws Exception
// * if any of the required Hadoop classes missing in the
// * classpath.
// */
// private HDFSFileOperations() throws Exception {
// Constructor constructor = null;
//
// if (!initialized) {
// // load relevant Hadoop classes
// hdfsConfigClazz = Class.forName("org.apache.hadoop.conf.Configuration");
// fileSystemClazz = Class.forName("org.apache.hadoop.fs.FileSystem");
// hdfsPathClazz = Class.forName("org.apache.hadoop.fs.Path");
//
// // get default constructor for configuration class-
// // org.apache.hadoop.conf.Configuration()
// constructor = hdfsConfigClazz.getConstructor(new Class[0]);
//
// // create an instance of configuration class, using default
// // constructor created above
// hdfsConfig = constructor.newInstance(new Object[0]);
//
// // create HDFS filesystem instance from the configuration in
// // classpath - FileSystem.get(configuration)
// Method getStaticMethod = fileSystemClazz.getMethod("get", hdfsConfigClazz);
// fileSystem = getStaticMethod.invoke(null, hdfsConfig); // first arg
// // null,
// // because
// // 'get' is
// // a static
// // method
//
// initialized = true;
// }
//
// }
//
// @Override
// protected InputStream getStreamImpl(String uri) throws Exception {
// if (!existsImpl(uri)) {
// throw new Exception(String.format("File does not exist at the given uri: %s", uri));
// }
//
// if (isDirectoryImpl(uri)) {
// throw new Exception(String.format(
// "Given uri %s is pointing to a directory; this method is only capable of reading file", uri));
// }
//
// Object hdfsPath = createPath(uri);
//
// Method openMethod = fileSystemClazz.getMethod("open", hdfsPathClazz);
//
// return (InputStream) openMethod.invoke(getFileSystem(hdfsPath), hdfsPath);
// }
//
// @Override
// protected boolean isFileImpl(String uri) throws Exception {
// Object hdfsPath = createPath(uri);
// Method isFileMethod = fileSystemClazz.getMethod("isFile", hdfsPathClazz);
// return ((Boolean) isFileMethod.invoke(getFileSystem(hdfsPath), hdfsPath)).booleanValue();
// }
//
// @Override
// protected boolean isDirectoryImpl(String uri) throws Exception {
// Object hdfsPath = createPath(uri);
// Method isDirectoryMethod = fileSystemClazz.getMethod("isDirectory", hdfsPathClazz);
// return ((Boolean) isDirectoryMethod.invoke(getFileSystem(hdfsPath), hdfsPath)).booleanValue();
// }
//
// @Override
// protected boolean existsImpl(String uri) throws Exception {
// Object hdfsPath = createPath(uri);
// Method existsMethod = fileSystemClazz.getMethod("exists", hdfsPathClazz);
// return ((Boolean) existsMethod.invoke(getFileSystem(hdfsPath), hdfsPath)).booleanValue();
// }
//
// @Override
// protected boolean containsImpl(String parentDirURI, String childName) throws Exception {
// return existsImpl(constructValidURIImpl(parentDirURI, childName));
// }
//
// @Override
// protected void copyFileToLocalImpl(String src, String localDest) throws Exception {
// if (((FileSystem) this.fileSystem).exists(new Path(src)))
// ((FileSystem) this.fileSystem).copyToLocalFile(new Path(src), new Path(localDest));
// else
// throw new FileNotFoundException(src + " Filesystem: HDFS");
// }
//
// @Override
// protected void copyDirToLocalImpl(String src, String localDest) throws Exception {
// if (((FileSystem) this.fileSystem).exists(new Path(src)))
// ((FileSystem) this.fileSystem).copyToLocalFile(new Path(src), new Path(localDest));
// else
// throw new FileNotFoundException(src + " Filesystem: HDFS");
// }
//
// @Override
// protected String constructValidURIImpl(String directoryURI, String name) throws Exception {
// Constructor constructor = hdfsPathClazz.getConstructor(String.class, String.class);
// Object hdfsFilePath = constructor.newInstance(directoryURI, name);
//
// Method toStringMethod = hdfsPathClazz.getMethod("toString");
// return (String) toStringMethod.invoke(hdfsFilePath);
//
// }
//
// @Override
// protected String getDirChecksumImpl(String dirPath) throws Exception {
// Path path = new Path(dirPath);
// if (isDirectory(path.toString())){
// return getMd5(getFileList(path));
// }else{
// /*
// * if a file, just return md5 of that file;
// * {@link #getFileListRecurisve(Path)} does not treat a single file
// */
// return getMd5(getFileHash(((FileSystem) fileSystem).getFileStatus(path)));
// }
// }
//
// private String getFileList(Path path) throws Exception {
//
// TreeSet<String> fileList = getFileListRecursive(path);
//
// return StringUtils.join(fileList, "\n");
// }
//
// private TreeSet<String> getFileListRecursive(Path path) throws Exception {
// TreeSet<String> fileList = new TreeSet<String>();
//
// if (!isDirectory(path.toString()))
// return fileList;
//
// for (FileStatus file : ((FileSystem) fileSystem).listStatus(path)) {
// if (file.isDirectory()) {
// fileList.addAll(getFileListRecursive(file.getPath()));
// } else {
// fileList.add(getFileHash(file));
// }
// }
// return fileList;
// }
//
// /**
// * A custom hash function for a given file.
// * @param file
// * @return a string of hashed value
// */
// private String getFileHash(FileStatus file) {
// String permissionHash = file.getPermission().toString();
// String size = Long.toString(file.getBlockSize());
// String modTime = Long.toString(file.getModificationTime());
// String path = file.getPath().toString();
// return permissionHash + ";" + size + ";" + modTime + ";" + path;
// }
//
// /**
// * Method to create HDFS org.apache.hadoop.fs.Path instance for the given
// * URI.
// *
// * @param uri
// * URI for which path instance is required.
// * @return HDFS path instance of the given URI.
// * @throws Exception
// */
// private Object createPath(String uri) throws Exception {
// // Since all abstract methods in HDFSOperations call createPath(), we
// // encode the URI String to a valid URI format
// // here.
// String validURI = FileOperations.encodeToValidURIFormat(uri);
//
// // Java API equivalent of following reflection code - Path p = new
// // Path(uri);
// Constructor constructor = hdfsPathClazz.getConstructor(String.class);
// return constructor.newInstance(validURI);
// }
//
// /**
// * Method to yield the FieldSystem instance that this input {@code filePath}
// * instance belongs to
// *
// * @param filePath
// * a Path instance wrapping around the file's location
// * @return a FileSystem instance that represents the file system to which
// * the input file path belongs
// * @throws Exception
// */
// private Object getFileSystem(Object filePath) throws Exception {
// Method getFileSystemMethod = hdfsPathClazz.getMethod("getFileSystem", hdfsConfigClazz);
// return getFileSystemMethod.invoke(filePath, hdfsConfig);
// }
// }
