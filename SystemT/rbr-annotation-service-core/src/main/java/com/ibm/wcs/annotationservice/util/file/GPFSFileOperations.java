/// *******************************************************************************
// * Copyright IBM
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// *******************************************************************************/
//
// import java.io.FileNotFoundException;
// import java.io.InputStream;
// import java.lang.reflect.Constructor;
// import java.lang.reflect.Method;
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
// public class GPFSFileOperations extends FileOperations
// {
//
// /**
// * Singleton instance of this class. Marked as volatile so that multi-threaded calls will
/// guarantee seeing a
// * completely initialized object.
// */
// static volatile GPFSFileOperations singleton;
//
// /**
// * Returns a handle to the singleton instance of this class. If this instance has not yet been
/// created, lazily
// * initialize it. When creating an instance of the filesystem, the JVM loads classes specific to
/// the DFS that will
// * throw a ClassNotFoundException on systems without that DFS installed.
// *
// * @return a pointer to the GPFS Filesystem handler
// */
// public static GPFSFileOperations getInstance ()
// {
// if (singleton == null) {
// synchronized (GPFSFileOperations.class) {
// if (singleton == null) {
// try {
// singleton = new GPFSFileOperations ();
// }
// catch (Exception e) {
// throw new RuntimeException ("Error instantiating GPFS FileOperations object", e);
// }
// }
// }
// }
// return singleton;
// }
//
// /**
// * This class provides basic file operations for GPFS. While running this class expects gpfs
/// libraries in the
// * classpath; in addition to these jars, this class also expects the Hadoop configurations:
/// core-site.xml and
// * gpfs-site.xml in the classpath.
// */
// /** Caching the loaded classes */
// private static Class gpfsConfigClazz, gpfsPathClazz, fileSystemClazz;
//
// /** Hadoop configuration instance, created based on the configuration files in the classpath */
// private static Object gpfsConfig;
//
// /** Handle to GPFS file system instance */
// private static Object fileSystem;
//
// /**
// * Flag to signal that the class is initialized; that is, all the Hadoop classes are loaded and
/// handle to gpfs file
// * system is initialized
// */
// private static boolean initialized = false;
//
// /**
// * Main constructor to create an instance of GPFS file operations.
// *
// * @throws Exception if any of the required classes are missing in the classpath.
// */
// GPFSFileOperations () throws Exception
// {
// Constructor constructor = null;
//
// if (!initialized) {
// // load relevant classes
// gpfsConfigClazz = Class.forName ("org.apache.hadoop.conf.Configuration");
// fileSystemClazz = Class.forName ("org.apache.hadoop.fs.FileSystem");
// gpfsPathClazz = Class.forName ("org.apache.hadoop.fs.Path");
//
// // get default constructor for configuration class- org.apache.hadoop.conf.Configuration()
// constructor = gpfsConfigClazz.getConstructor (new Class[0]);
//
// // create an instance of configuration class, using default
// // constructor created above
// gpfsConfig = constructor.newInstance (new Object[0]);
//
// // create GPFS filesystem instance from the configuration in classpath -
/// FileSystem.get(configuration)
// Method getStaticMethod = fileSystemClazz.getMethod ("get", gpfsConfigClazz);
// fileSystem = getStaticMethod.invoke (null, gpfsConfig); // first arg null, because 'get' is a
/// static method
//
// initialized = true;
// }
// }
//
// @Override
// protected InputStream getStreamImpl (String uri) throws Exception
// {
// if (!existsImpl (uri)) { throw new Exception (String.format ("File does not exist at the given
/// uri %s", uri)); }
//
// if (isDirectoryImpl (uri)) { throw new Exception (String.format (
// "Given uri %s is pointing to a directory; this method is only capable of reading file", uri)); }
//
// Object gpfsPath = createPath (uri);
//
// Method openMethod = fileSystemClazz.getMethod ("open", gpfsPathClazz);
//
// return (InputStream) openMethod.invoke (fileSystem, gpfsPath);
// }
//
// @Override
// protected boolean isFileImpl (String uri) throws Exception
// {
// Object gpfsPath = createPath (uri);
// Method isFileMethod = fileSystemClazz.getMethod ("isFile", gpfsPathClazz);
// return ((Boolean) isFileMethod.invoke (fileSystem, gpfsPath)).booleanValue ();
// }
//
// @Override
// protected boolean isDirectoryImpl (String uri) throws Exception
// {
// Object gpfsPath = createPath (uri);
// Method isDirectoryMethod = fileSystemClazz.getMethod ("isDirectory", gpfsPathClazz);
// return ((Boolean) isDirectoryMethod.invoke (fileSystem, gpfsPath)).booleanValue ();
// }
//
// @Override
// protected boolean existsImpl (String uri) throws Exception
// {
// Object gpfsPath = createPath (uri);
// Method existsMethod = fileSystemClazz.getMethod ("exists", gpfsPathClazz);
// return ((Boolean) existsMethod.invoke (fileSystem, gpfsPath)).booleanValue ();
// }
//
// @Override
// protected boolean containsImpl (String parentDirURI, String childName) throws Exception
// {
// return existsImpl (constructValidURIImpl (parentDirURI, childName));
// }
//
// @Override
// protected void copyFileToLocalImpl(String src, String localDest) throws Exception {
// if(((FileSystem) this.fileSystem).exists(new Path(src)))
// ((FileSystem) this.fileSystem).copyToLocalFile(new Path(src), new Path(localDest));
// else
// throw new FileNotFoundException(src + " Filesystem: GPFS");
// }
//
// @Override
// protected void copyDirToLocalImpl(String src, String localDest) throws Exception {
// if(((FileSystem) this.fileSystem).exists(new Path(src)))
// ((FileSystem) this.fileSystem).copyToLocalFile(new Path(src), new Path(localDest));
// else
// throw new FileNotFoundException(src + " Filesystem: GPFS");
// }
//
// @Override
// protected String constructValidURIImpl (String directoryURI, String name) throws Exception
// {
// Constructor constructor = gpfsPathClazz.getConstructor (String.class, String.class);
// Object gpfsFilePath = constructor.newInstance (directoryURI, name);
//
// Method toStringMethod = gpfsPathClazz.getMethod ("toString");
// return (String) toStringMethod.invoke (gpfsFilePath);
//
// }
//
// @Override
// protected String getDirChecksumImpl(String dirPath) throws Exception {
// String fileList = getFileList(dirPath);
// return getMd5(fileList);
// }
//
// private String getFileList(String pFile) throws Exception {
//
// TreeSet<String> fileList = getFileListRecursive(new Path(pFile));
//
// return StringUtils.join(fileList, "\n");
// }
//
// private TreeSet<String> getFileListRecursive(Path pFile) throws Exception {
// TreeSet<String> fileList = new TreeSet<String>();
//
// if(!isDirectory(pFile.toString()))
// return fileList;
//
//
// for(FileStatus file : ((FileSystem)fileSystem).listStatus(pFile)) {
// if(file.isDirectory()) {
// fileList.addAll(getFileListRecursive(file.getPath()));
// }
// else {
// String permissionHash = file.getPermission().toString();
// String size = Long.toString(file.getBlockSize());
// String modTime = Long.toString(file.getModificationTime());
// String path = file.getPath().toString();
//
// fileList.add(permissionHash + ";" + size + ";" + modTime + ";" + path);
// }
// }
// return fileList;
// }
//
// /**
// * Method to create gpfs org.apache.hadoop.fs.Path instance for the given URI.
// *
// * @param uri URI for which path instance is required.
// * @return gpfs path instance of the given URI.
// * @throws Exception
// */
// private Object createPath (String uri) throws Exception
// {
// // Since all abstract methods in gpfsOperations call createPath(), we encode the URI String to a
/// valid URI format
// // here.
// String validURI = FileOperations.encodeToValidURIFormat (uri);
//
// // Java API equivalent of following reflection code - Path p = new Path(uri);
// Constructor constructor = gpfsPathClazz.getConstructor (String.class);
// return constructor.newInstance (validURI);
// }
//
// }
