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
package com.ibm.avatar.algebra.util.file;

import java.io.File;

/**
 */
public class FSPathResolver {
  private static String CONFIG_DIR = "configs";
  private static String DATA_DIR = "data";
  private static String LOG_DIR = "logs";

  private static String DEFAULT_ROOT = "C:/emailSearchResources";
  private static boolean AUTOSET_ROOT = false;

  @Deprecated
  public static void initResourceRoot(String rsrcRoot) throws Exception {
    File rsrcDir = new File(rsrcRoot);
    FileUtils.ensureReadWritableDir(rsrcDir);

    File configDir = new File(rsrcDir, CONFIG_DIR);
    FileUtils.ensureReadWritableDir(configDir);
    System.setProperty("avatar.config.root", configDir.getAbsolutePath());

    File dataDir = new File(rsrcDir, DATA_DIR);
    FileUtils.ensureReadWritableDir(dataDir);
    System.setProperty("avatar.data.root", dataDir.getAbsolutePath());

    File logDir = new File(rsrcDir, LOG_DIR);
    FileUtils.ensureReadWritableDir(logDir);
    System.setProperty("avatar.log.root", logDir.getAbsolutePath());
  }

  private static String dotOrProperty(String propName) {
    String r = System.getProperty(propName);

    if (r == null) {
      String root = System.getProperty("avatar.resource.root");
      if (root != null) {
        try {
          initResourceRoot(root);
          r = System.getProperty(propName);
        } catch (Exception e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }

    if (r == null && AUTOSET_ROOT) {
      try {
        initResourceRoot(DEFAULT_ROOT);
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      r = System.getProperty(propName);
    }

    return (r == null) ? "." : r;
  }

  public static String configRoot() {
    return dotOrProperty("avatar.config.root");
  }

  public static String dataRoot() {
    return dotOrProperty("avatar.data.root");
  }

  public static String logRoot() {
    return dotOrProperty("avatar.log.root");
  }

  public static String resolveCfgPath(String relPath) {
    return FileUtils.createValidatedFile(configRoot(), relPath).getAbsolutePath();
  }

  public static String resolveDataPath(String relPath) {
    return FileUtils.createValidatedFile(dataRoot(), relPath).getAbsolutePath();
  }

  public static String resolveLogPath(String relPath) {
    return FileUtils.createValidatedFile(logRoot(), relPath).getAbsolutePath();
  }
}
