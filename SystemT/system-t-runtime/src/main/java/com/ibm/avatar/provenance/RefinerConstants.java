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

/**
 * Container for various properties used in the AQL Refeinement algorithm.
 * 
 */
public class RefinerConstants {

  // the list of views that we shouldn't refine
  public static final String REFINER_UNTOUCHABLE_VIEWS_PROP = "untouchable.views";

  // Number of iteration to refine: how many top changes should apply and see
  // improvement.
  public static final String REFINER_NUM_ITERATIONS_PROP = "iterations.number";

  // Do we perform cross-validation ?
  public static final String REFINER_CROSS_VALIDATION_PROP = "cross.validation";

  // beta value for computing F-measure
  public static final String REFINER_BETA_PROP = "beta";

  // weight of measurement: how much percentage is on F-score, and the rest is
  // on complexity of change
  public static final String REFINER_F_WEIGHT_PROP = "f.weight";

  // *maximum* number of ranges to split for Follows
  public static final String REFINER_MAX_NUM_RANGES_PROP = "max.num.ranges";

  // penalty values for changes
  // FIXME: needs discussion.
  // each added range in "Change Follows" adds some penalty
  public static final String REFINER_RANGE_PENALTY_PROP = "follows.range.penalty";
  // removal of each word in the dictionary
  public static final String REFINER_DICT_PENALTY_PROP = "dict.penalty";
  // removal of overlap filter
  public static final String REFINER_FILTER_PENALTY_PROP = "filter.penalty";

  // Added to calculate LLC for FilterView
  public static final String REFINER_OVERLAP_FILTER_VIEWS_PROP = "filter.views";

  // for cross-validation, out to all suggested changes to this file.
  public static final String REFINER_SUMMARY_FILE_PROP = "summary.file";

  // Where to write all LLCs that are generated
  public static final String REFINER_LLC_FILE_PROP = "llc.file";

  // Minimum threshold for F1-measure improvement from removing/adding a single phrase to a
  // dictionary in order to
  // output that phrase as part of a LLC
  public static final String REFINER_DICTIONARY_GAIN_THRESHOLD_PROP = "dict.improvement.threshold";

  // Minimum threshold for F1-measure improvement from removing/adding a single phrase to a
  // dictionary in order to
  // output that phrase as part of a LLC
  public static final String REFINER_MERGE_DICTIONARY_LLCS_PROP = "merge.dictionary.llcs";

}
