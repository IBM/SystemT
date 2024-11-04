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
package com.ibm.wcs.annotationservice;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.dict.DictFile;
import com.ibm.avatar.algebra.util.dict.DictionarySerializer;
import com.ibm.avatar.algebra.util.dict.TextSerializer;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.tam.TableMetadata;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.wcs.annotationservice.exceptions.AnnotationServiceException;
import com.ibm.wcs.annotationservice.models.json.SystemTAnnotatorBundleConfig;
import com.ibm.wcs.annotationservice.util.file.CsvFileWriter;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


/**
 * Class to export annotator model for recreating operatorGraph
 *
 */
public class AnnotatorResources {
  private static final ObjectMapper mapper = new ObjectMapper();

  private final SystemTAnnotatorBundleConfig systemTConfig;

  private final OperatorGraph operatorGraph;

  static private final String TAMS_DIRNAME = "tams";
  static private final String RESOURCES_DIRNAME = "resources";
  static private final String DICTIONARY_DIRNAME = "externalDictionary";
  static private final String TABLE_DIRNAME = "externalTable";
  static private final String CONFIG_JSON_FILENAME = "manifest.json";
  static private final String DEFAULT_MODEL_FILENAME = "model.zip";

  public AnnotatorResources(SystemTAnnotatorBundleConfig systemTConfig,
      final OperatorGraph operatorGraph) {
    this.systemTConfig = systemTConfig;
    this.operatorGraph = operatorGraph;
  }


  /**
   * Serialize TAMs in operatorGraph and store serialized TAMs into specified directory as .tam
   * files.
   *
   * @param destDirPath
   * @throws JAXBException
   * @throws IOException
   */
  private void storeTams(final Path destDirPath) throws JAXBException, IOException {
    final Map<String, TAM> moduleNameToTam = this.operatorGraph.getModuleNameToTam();

    for (final String moduleName : moduleNameToTam.keySet()) {
      TAMSerializer.serialize(moduleNameToTam.get(moduleName), destDirPath.toUri());
    }
  }


  /**
   * Serialize dictionaries and store the dictionaries into specified directory as .dict/.cd files .
   *
   * @param destDirPath
   * @param compileDictionaries
   * @throws IOException
   */
  private Map<String, Path> storeExternalDictionaries(final Path destDirPath,
      final boolean compileDictionaries) throws IOException {

    if (this.systemTConfig.getExternalDictionaries() == null
        || this.systemTConfig.getExternalDictionaries().size() == 0) {
      return Collections.emptyMap();
    }

    if (Files.notExists(destDirPath)) {
      Files.createDirectories(destDirPath);
    }

    final Map<String, Path> externalDictPaths = new HashMap<>();

    final Map<String, CompiledDictionary> origLoadedDicts = this.operatorGraph.getOrigLoadedDicts();
    final Map<String, DictFile> origDictFiles = this.operatorGraph.getOrigDictFiles();
    final DictionarySerializer dictSerializer = new TextSerializer();

    for (final String dictName : origLoadedDicts.keySet()) {
      if (!compileDictionaries && origDictFiles.containsKey(dictName)) {
        // store the dictionary in non-compiled form.
        final DictFile dictFile = origDictFiles.get(dictName);

        final Path destFilePath = destDirPath.resolve(dictName + ".dict");
        dictFile.dumpToText(destFilePath.toFile());

        externalDictPaths.put(dictName, destFilePath);
      } else {
        // store the dictionary in compiled form.
        final CompiledDictionary compiledDict = origLoadedDicts.get(dictName);

        final Path destFilePath = destDirPath.resolve(dictName + ".cd");

        try (OutputStream os = Files.newOutputStream(destFilePath)) {
          dictSerializer.serialize(compiledDict, os);

          externalDictPaths.put(dictName, destFilePath);
        }
      }
    }

    return externalDictPaths;
  }


  /**
   * Serialize external tables and store the tables into specified directory as .csv files.
   *
   * @param destDirPath
   * @throws IOException
   */
  private Map<String, Path> storeExternalTables(final Path destDirPath) throws IOException {

    if (this.systemTConfig.getExternalTables() == null
        || this.systemTConfig.getExternalTables().size() == 0) {
      return Collections.emptyMap();
    }

    if (Files.notExists(destDirPath)) {
      Files.createDirectories(destDirPath);
    }

    final Map<String, Path> externalTablePaths = new HashMap<>();

    final Map<String, ArrayList<ArrayList<String>>> origLoadedTables =
        this.operatorGraph.getOrigLoadedTables();
    Map<String, TableMetadata> allExternalTableMetadata =
        this.operatorGraph.getAllExternalTableMetadata();

    for (final String tableName : origLoadedTables.keySet()) {
      final ArrayList<ArrayList<String>> table = origLoadedTables.get(tableName);
      final TableMetadata tableMetadata = allExternalTableMetadata.get(tableName);

      final Path destFilePath = destDirPath.resolve(tableName + ".csv");

      try (Writer writer = Files.newBufferedWriter(destFilePath)) {
        CsvFileWriter.writeTable(table, writer, tableMetadata.getTableSchema());

        externalTablePaths.put(tableName, destFilePath);
      }
    }

    return externalTablePaths;
  }


  /**
   * Create manifest.json in the specified directory. The following properties could be different
   * between the generated manifest.json and the original annotator bundle config. - location -
   * modulePath - sourceModules - externalDictionaries - externalTables
   *
   * @throws AnnotationServiceException
   * @throws IOException
   */
  private void writeConfigJson(final Path destDirPath, final List<Path> modulePaths,
      final Map<String, Path> externalDictPaths, final Map<String, Path> externalTablePaths)
      throws AnnotationServiceException, IOException {

    final List<String> modulePath = modulePaths.stream()
        .map(path -> destDirPath.relativize(path).toString()).collect(Collectors.toList());

    final HashMap<String, String> externalDictionaries = new HashMap<>();
    for (final String dictName : externalDictPaths.keySet()) {
      final Path dictPath = externalDictPaths.get(dictName);
      externalDictionaries.put(dictName, destDirPath.relativize(dictPath).toString());
    }

    final HashMap<String, String> externalTables = new HashMap<>();
    for (final String tableName : externalTablePaths.keySet()) {
      final Path tablePath = externalTablePaths.get(tableName);
      externalTables.put(tableName, destDirPath.relativize(tablePath).toString());
    }

    SystemTAnnotatorBundleConfig newConfig = new SystemTAnnotatorBundleConfig(
        this.systemTConfig.getVersion(), this.systemTConfig.getAnnotator(),
        this.systemTConfig.getAnnotatorRuntime(), this.systemTConfig.getAcceptedContentTypes(),
        this.systemTConfig.getInputTypes(), this.systemTConfig.getOutputTypes(), ".", // location
        this.systemTConfig.isSerializeAnnotatorInfo(), this.systemTConfig.getSerializeSpan(),
        this.systemTConfig.getModuleNames(), modulePath, Collections.EMPTY_LIST, // sourceModules
        externalDictionaries, externalTables, this.systemTConfig.getTokenizer(),
        this.systemTConfig.getTokenizerPearFile());

    String configJsonStr = mapper.writeValueAsString(newConfig);

    Path configJsonPath = destDirPath.resolve(CONFIG_JSON_FILENAME);
    if (Files.notExists(configJsonPath)) {
      Files.createFile(configJsonPath);
    }
    Files.write(configJsonPath, Arrays.asList(configJsonStr), StandardOpenOption.TRUNCATE_EXISTING);
  }


  /**
   * Export the stored model as a zip file in the zipFilePath.
   *
   * @param zipFilePath
   * @param compileDictionaries
   * @throws Exception
   */
  public void save(String zipFilePath, boolean compileDictionaries) throws Exception {
    Path destZipPath = pathFromString(zipFilePath);

    // If the specified path is existing directory, save model.zip in the specified directory.
    if (Files.exists(destZipPath) && Files.isDirectory(destZipPath)) {
      destZipPath = destZipPath.resolve(DEFAULT_MODEL_FILENAME);
    }

    File tempDir = null;
    try {
      // 0. Prepare temporary directory
      tempDir = FileUtils.makeTempDir("annotation-service-core-");
      final Path tempDirPath = pathFromString(tempDir.toString());
      final Path resourcesDirPath = tempDirPath.resolve(RESOURCES_DIRNAME);

      // 1. Store TAM instances into <tempDir>/tams/ directory as .tam files.
      final Path tamsDirPath = tempDirPath.resolve(TAMS_DIRNAME);
      storeTams(tamsDirPath);

      // 2. Store external dictionaries into <tempDir>/resources/externalDictionaries/ directory as
      // .dict or .cd files.
      final Path externalDictionaryPath = resourcesDirPath.resolve(DICTIONARY_DIRNAME);
      final Map<String, Path> externalDictPaths =
          storeExternalDictionaries(externalDictionaryPath, compileDictionaries);

      // 3. Store external tables into <tempDir>/resources/externalTables/ directory as .csv files.
      final Path externalTablePath = resourcesDirPath.resolve(TABLE_DIRNAME);
      final Map<String, Path> externalTablePaths = storeExternalTables(externalTablePath);

      // 4. Create manifest.json in <tempDir>/ directory.
      writeConfigJson(tempDir.toPath(), Arrays.asList(tamsDirPath), externalDictPaths,
          externalTablePaths);

      // 5. Build model.zip file with related model files and store it into <tempDir>/ directory.
      Map<String, Path> srcEntryNameToPath =
          Files.walk(tempDirPath).filter(p -> !Files.isDirectory(p))
              .collect(Collectors.toMap(p -> tempDirPath.relativize(p).toString(), p -> p));
      final Path tempZipPath = tempDirPath.resolve(DEFAULT_MODEL_FILENAME);
      zip(tempZipPath, srcEntryNameToPath);

      // 6. Copy model.zip in <tempDir> to the specified path.
      Files.copy(tempZipPath, destZipPath);
    } finally {
      if (tempDir != null) {
        // Delete the temporary directory if exists.
        FileUtils.deleteDirectory(tempDir);
      }
    }

  }

  private void zip(final Path zipPath, final Map<String, Path> srcEntryNameToPath)
      throws IOException {
    try (OutputStream os = Files.newOutputStream(zipPath);
        ZipOutputStream zos = new ZipOutputStream(os)) {

      for (final String entryName : srcEntryNameToPath.keySet()) {
        final Path srcPath = srcEntryNameToPath.get(entryName);
        final ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        byte[] bytes = Files.readAllBytes(srcPath);
        zos.write(bytes);
        zos.closeEntry();
      }
    }
  }

  private static Path pathFromString(final String path) {
    URI uri = URI.create(path);
    if (Objects.isNull(uri.getScheme())) {
      return Paths.get(path);
    } else {
      return Paths.get(uri);
    }
  }
}
