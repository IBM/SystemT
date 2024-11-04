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
package com.ibm.avatar.provenance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.logging.Log;

/*
 * Various utilities for cross-validation. author: Bin Liu
 */

public class CrossValidation {

  // private static String SOURCE_EXTENSION = "";
  private static String LABEL_EXTENSION = ".xml";
  private static String DATA_FOLDER = "data";
  private static String LABEL_FOLDER = "label";

  public CrossValidation(String inFolder, String outFolder, int fold, double percentage) {
    super();
    this.inFolder = inFolder;
    this.outFolder = outFolder;
    this.fold = fold;
    this.percentage = percentage;
  }

  private final String inFolder;
  private final String outFolder;
  private final int fold; // e.g., 10-fold cross validation
  private final double percentage; // percentage of data use for training; the rest is for testing

  /**
   * @param args
   */
  public static void main(String[] args) {

    // split a dataset for cross-validation
    String DATA_DIR = "testdata/docs/aqlRefineTest";
    // String inFolder = DATA_DIR + "/PersonPhoneEnronClean"; //personphoneEnronGS
    // String outFolder = DATA_DIR + "/PersonPhoneEnronCrossValidation";
    // CrossValidation cv = new CrossValidation(inFolder, outFolder, 10, 0.667);
    // cv.splitData();

    // String inFolder = DATA_DIR + "/CoNLL2003-all"; //personphoneEnronGS
    // String outFolder = DATA_DIR + "/CoNLLCrossValidation";
    // CrossValidation cv = new CrossValidation(inFolder, outFolder, 10, 0.667);
    // cv.splitData();

    String inFolder = DATA_DIR + "/ace"; // personphoneEnronGS
    String outFolder = DATA_DIR + "/ACECrossValidation";
    CrossValidation cv = new CrossValidation(inFolder, outFolder, 10, 0.667);
    cv.splitData();

    // prepare for speed test.
    // String dataFolder = DATA_DIR + "/PersonSLCleanCV/train/data_5";
    // String labelFolder = DATA_DIR + "/PersonSLCleanCV/train/label_5";
    // randomDelete(dataFolder, labelFolder, 100);
    //
    // dataFolder = DATA_DIR + "/PersonSLCleanCV/train/data_6";
    // labelFolder = DATA_DIR + "/PersonSLCleanCV/train/label_6";
    // randomDelete(dataFolder, labelFolder, 200);
    //
    // dataFolder = DATA_DIR + "/PersonSLCleanCV/train/data_7";
    // labelFolder = DATA_DIR + "/PersonSLCleanCV/train/label_7";
    // randomDelete(dataFolder, labelFolder, 300);
    //
    // dataFolder = DATA_DIR + "/PersonSLCleanCV/train/data_8";
    // labelFolder = DATA_DIR + "/PersonSLCleanCV/train/label_8";
    // randomDelete(dataFolder, labelFolder, 400);

    // String inFolder = DATA_DIR + "/personSelfLabel"; //personphoneEnronGS
    // String outFolder = DATA_DIR + "/PersonSLCleanCV";
    // CrossValidation cv = new CrossValidation(inFolder, outFolder, 10, 0.667);
    // cv.splitData();

    /*********************************************************************/
    /*
     * // process ACE data to remove XML tags String[] filter = {"<annotations>", "<text>",
     * "</annotations>", "</text>", "<content>", "</content>"}; // String inRoot =
     * "/ace2005-original"; // String[] inFolders = {"/ace2005trainingDocOriginal",
     * "/ace2005testingDocOriginal"}; // String outRoot = "/acer2005-clean"; // String[] outFolders
     * = {"/train", "/test"};
     */
    /*
     * String[] filter = {"<annotations>", "<text>", "</annotations>", "</text>", "<content>",
     * "</content>"}; String inRoot = "/CoNLL2003-original"; String[] inFolders =
     * {"/CoNLL2003testingDocA", "/CoNLL2003testingDocB", "/CoNLL2003trainingDoc"}; String outRoot =
     * "/CoNLL2003-clean"; String[] outFolders = {"/testA/data", "/testB/data", "/train/data"}; for
     * (int i = 0; i < inFolders.length; i++){ try {
     * CrossValidation.removeTags(DATA_DIR+inRoot+inFolders[i], DATA_DIR+outRoot+outFolders[i],
     * filter); } catch (Exception e) { e.printStackTrace(); } }
     */
    /*********************************************************************/
    // String folder1 = DATA_DIR + "/ace2005-clean/train/data";
    // String folder2 = DATA_DIR + "/ace2005-clean/train/label";
    // CrossValidation.batchRename(folder1, folder2, null, "xml");

    // String folder1 = DATA_DIR + "/CoNLL2003-clean/testA/data";
    // String folder2 = DATA_DIR + "/CoNLL2003-clean/testA/label";
    // CrossValidation.batchRename(folder1, folder2, null, "xml");
    //
    // folder1 = DATA_DIR + "/CoNLL2003-clean/testB/data";
    // folder2 = DATA_DIR + "/CoNLL2003-clean/testB/label";
    // CrossValidation.batchRename(folder1, folder2, null, "xml");
    //
    // folder1 = DATA_DIR + "/CoNLL2003-clean/train/data";
    // folder2 = DATA_DIR + "/CoNLL2003-clean/train/label";
    // CrossValidation.batchRename(folder1, folder2, null, "xml");

    /***************** Cleaning Labels **********************************/
    // String correctFolder = DATA_DIR + "/EnronPersonCorrected";
    // String existingFolder = DATA_DIR + "/PersonSelfLabel/label";
    // mergeLabels(correctFolder, existingFolder, "Person");
  }

  public static void calculateTime(long start, long end) {
    double time = (start - end) / 1000.0;
    System.out.println(time);
  }

  public static void randomDelete(String dataFolder, String labelFolder, int fileToKeep) {
    File data = FileUtils.createValidatedFile(dataFolder);
    String dataFiles[] = data.list();
    int totalFiles = dataFiles.length;

    ArrayList<Integer> chosenFiles = new ArrayList<Integer>();

    Random generator = new Random();
    long seed = Long.MAX_VALUE - 32423;
    generator.setSeed(seed);
    int choice = 0;;

    for (int j = 0; j < fileToKeep; j++) {
      do {
        choice = generator.nextInt(totalFiles);
      } while (chosenFiles.contains(choice));
      chosenFiles.add(choice);
    }

    File dataF, labelF;
    int count = 0;
    for (int i = 0; i < dataFiles.length; i++) {
      if (!chosenFiles.contains(i)) {
        count++;
        dataF = FileUtils
            .createValidatedFile(dataFolder + System.getProperty("file.separator") + dataFiles[i]);
        dataF.delete();

        labelF = FileUtils.createValidatedFile(
            labelFolder + System.getProperty("file.separator") + dataFiles[i] + ".xml");
        labelF.delete();
      }
    }
    System.out
        .println("Totally deleted " + count + " files. " + (totalFiles - count) + " remaining");
  }

  public void splitData() {

    int totalFiles;

    File dataIn = FileUtils
        .createValidatedFile(inFolder + System.getProperty("file.separator") + DATA_FOLDER);
    String files[] = dataIn.list();
    File labelIn = FileUtils
        .createValidatedFile(inFolder + System.getProperty("file.separator") + LABEL_FOLDER);

    totalFiles = files.length;

    int t1 = (int) (totalFiles * percentage); // total number of training files
    int t2 = totalFiles - t1;

    String outDataFolder, outLabelFolder;
    File out = FileUtils.createValidatedFile(outFolder);
    out.mkdir();
    File trainOut = new File(out, "train");
    trainOut.mkdir();
    File testOut = new File(out, "test");
    testOut.mkdir();

    Random generator = new Random();
    long seed = Long.MAX_VALUE - 32423;
    generator.setSeed(seed);

    ArrayList<String> train = new ArrayList<String>();
    ArrayList<Integer> chosenFiles = new ArrayList<Integer>();

    int choice;
    int trainCount, testCount;
    for (int i = 0; i < fold; i++) {
      trainCount = 0;
      testCount = 0;
      train.clear();
      chosenFiles.clear();

      outDataFolder = DATA_FOLDER + "_" + i;
      outLabelFolder = LABEL_FOLDER + "_" + i;

      File trainDataOut = new File(trainOut, outDataFolder);
      trainDataOut.mkdir();
      File trainLabelOut = new File(trainOut, outLabelFolder);
      trainLabelOut.mkdir();

      File testDataOut = new File(testOut, outDataFolder);
      testDataOut.mkdir();
      File testLabelOut = new File(testOut, outLabelFolder);
      testLabelOut.mkdir();

      // randomly select training data
      for (int j = 0; j < t1; j++) {
        do {
          choice = generator.nextInt(totalFiles);
        } while (chosenFiles.contains(choice));
        chosenFiles.add(choice);
        train.add(files[choice]);
      }
      try {
        for (String s : files) {
          if (s.contains(".svn"))
            continue;
          if (train.contains(s)) {
            copyFile(dataIn, trainDataOut, s);
            copyFile(labelIn, trainLabelOut, s + LABEL_EXTENSION);
            trainCount++;
          } else {
            copyFile(dataIn, testDataOut, s);
            copyFile(labelIn, testLabelOut, s + LABEL_EXTENSION);
            testCount++;
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }

      if (trainCount != t1 || testCount != t2)
        System.out.println("Error in splitData! Total count doesn't match.");

      Log.debug("For fold " + i + ", copied " + trainCount + " files to training folder, and "
          + testCount + " to test folder");
    }

  }

  /**
   * Copy file from one folder to another.
   * 
   * @param in
   * @param out
   * @param fileName
   * @return the total number of bytes read into the buffer, or -1 is there is no more data because
   *         the end of the stream has been reached
   * @throws FileNotFoundException
   * @throws IOException
   */
  private static void copyFile(File in, File out, String fileName)
      throws FileNotFoundException, IOException {
    File f1 = new File(in, fileName);
    File f2 = new File(out, fileName);
    InputStream inFile = new FileInputStream(f1);

    // For Append the file.
    // OutputStream out = new FileOutputStream(f2,true);

    // For Overwrite the file.
    OutputStream outFile = new FileOutputStream(f2);

    byte[] buf = new byte[1024];
    int len;
    while ((len = inFile.read(buf)) > 0) {
      outFile.write(buf, 0, len);
    }

    inFile.close();
    outFile.close();
    System.out.println("File copied: " + fileName);
    // return len;
  }

  /**
   * Sort files by file name; for each file name in folder2, copy the name in folder1, add extension
   * This is used to make sure data and label has matching file names.
   * 
   * @param folder1
   * @param folder2
   * @param removeExt remove this extension from file names in folder1 (e.g., txt)
   * @param addExt add this extension to the end (e.g., xml)
   */
  public static void batchRename(String folder1, String folder2, String removeExt, String addExt) {
    File source = FileUtils.createValidatedFile(folder1);
    String[] sourceFiles = source.list();
    Arrays.sort(sourceFiles);

    File target = FileUtils.createValidatedFile(folder2);
    String[] targetFiles = target.list();
    Arrays.sort(targetFiles);

    if (sourceFiles.length != targetFiles.length) {
      System.out.println("Error in batchRename: source and target have different number of files!");
      System.exit(1);
    }
    File before, after;
    for (int i = 0; i < sourceFiles.length; i++) {
      if (sourceFiles[i].contains(".svn"))
        continue;
      before = FileUtils.createValidatedFile(folder2 + "/" + targetFiles[i]);
      if (removeExt != null)
        after = FileUtils
            .createValidatedFile(folder2 + "/" + sourceFiles[i].replaceAll(removeExt, addExt));
      else
        after = FileUtils.createValidatedFile(folder2 + "/" + sourceFiles[i] + "." + addExt);
      if (!before.renameTo(after)) {
        System.out.println("Error in batchRename: error renaming file " + targetFiles[i]);
        System.exit(1);
      } else
        System.out.println("Renamed " + targetFiles[i] + " to " + sourceFiles[i] + "." + addExt);
    }
  }

  public static void mergeLabels(String correctFolder, String existingFolder, String type) {
    File f1 = FileUtils.createValidatedFile(correctFolder);
    File f2 = FileUtils.createValidatedFile(existingFolder);

    List<String> correct = Arrays.asList(f1.list());
    List<String> existing = Arrays.asList(f2.list());

    // read each additional label files into buffer, indexed by start position

    HashMap<Integer, ArrayList<Pair<String, Integer>>> records =
        new HashMap<Integer, ArrayList<Pair<String, Integer>>>();
    try {
      for (String s : correct) {
        if (s.contains(".svn"))
          continue;
        if (!existing.contains(s + ".xml")) {
          System.out.println(
              "Error in mergeLabels: file " + s + ".xml" + " not exist in " + existingFolder);
          return;
        }
        records.clear();

        cacheRecords(existingFolder, type, records, s + ".xml");
        cacheRecords(correctFolder, type, records, s);

        ArrayList<Integer> keys = new ArrayList<Integer>();

        for (Integer i : records.keySet()) {
          if (!keys.contains(i))
            keys.add(i);
        }

        Object[] keyArray = keys.toArray();
        Arrays.sort(keyArray);

        PrintWriter pw = new PrintWriter(new FileWriter(correctFolder + "/" + s + ".xml"));

        pw.append("<annotations><text>\n");

        for (Object i : keyArray) {
          ArrayList<Pair<String, Integer>> list = records.get(i);
          for (Pair<String, Integer> pair : list) {
            pw.append(pair.first + "\n");
          }
        }

        pw.append("</text></annotations>\n");

        pw.close();

        System.out.println("Merged file " + s);
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void cacheRecords(String correctFolder, String type,
      HashMap<Integer, ArrayList<Pair<String, Integer>>> records, String s)
      throws FileNotFoundException, IOException {
    String line1;
    FileReader in = new FileReader(correctFolder + "/" + s);
    BufferedReader br = new BufferedReader(in);

    String buffer = "";
    int begin = 0, end = 0;
    ArrayList<Pair<String, Integer>> tmp = null;
    int startPos = 0, endPos = 0;
    while ((line1 = br.readLine()) != null) {
      if (line1.startsWith("<annotations>") || line1.endsWith("</annotations>")
          || line1.startsWith("<text>") || line1.endsWith("</text>"))
        continue;

      buffer += line1 + "\n";

      // if (line1.startsWith("<start>")){
      // begin = Integer.parseInt((line1.replaceAll("<start>", "")).replaceAll("</start>", ""));
      // }
      //
      // if (line1.startsWith("<end>")){
      // end = Integer.parseInt((line1.replaceAll("<end>", "")).replaceAll("</end>", ""));
      // }

      if (line1.contains("<start>")) {
        startPos = line1.indexOf("<start>");
        endPos = line1.indexOf("</start>");
        begin = Integer.parseInt(line1.substring(startPos + 7, endPos));
      }

      if (line1.contains("<end>")) {
        startPos = line1.indexOf("<end>");
        endPos = line1.indexOf("</end>");
        end = Integer.parseInt(line1.substring(startPos + 5, endPos));
      }

      if (line1.endsWith("</" + type + ">")) {
        Pair<String, Integer> pair = new Pair<String, Integer>(buffer, end);
        if (records.containsKey(begin)) {
          // records.get(begin).add(pair);
          tmp = records.get(begin);
          for (int i = 0; i < tmp.size(); i++) {
            if (end == tmp.get(i).second) {
              System.out.println("Skipped entry in file " + s + ": " + buffer);
              break;
            }
            if (end < tmp.get(i).second) {
              tmp.add(i, pair);
              break;
            }

            if ((i == tmp.size() - 1) && end > tmp.get(i).second) {
              tmp.add(i + 1, pair);
              break;
            }
          }
        } else {
          ArrayList<Pair<String, Integer>> list = new ArrayList<Pair<String, Integer>>();
          list.add(pair);
          records.put(begin, list);
        }
        buffer = "";
        begin = 0;
        end = 0;
      }

      if (line1.contains("<>") || line1.contains("</>")) {
        System.out.println("Error in mergeLabels: " + line1);
      }
    }
    br.close();
  }
}
