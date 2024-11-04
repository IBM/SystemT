/*******************************************************************************
* Copyright IBM
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/
package com.ibm.test.udfs;

import java.util.ArrayList;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.ScalarList;

/**
 * User-defined functions used in test cases. Should be compiled into a jar
 * storead at testdata/udfjars/UDFTests.jar
 * 
 */
public class UDFTests {
	/***********************************************
	 * To compare two names use IsEquivalentName(name1, name2, direction,
	 * confidence): -- given two names and the direction of match (sub-names
	 * have to be in the same order within the two names if direction == 1, or
	 * in any order if direction == 0), return true if -- there is a
	 * non-conflicting match (conflict is defined as : there is at least one
	 * non-matching subname/initial in each name) -- there is at least one
	 * word-name in common ("J. Smith" cannot match "John") -- if direction
	 * parameters is satisfied -- confidence is not yet supported (still
	 * debatable if it is necessary)
	 * 
	 * Example: "John Smith" matches "Smith J" if direction is "any",
	 * "John Smith" can match "John" no matter what direction, but "J. Smith"
	 * cannot match "John"
	 ***********************************************/
	ArrayList<Integer> res_prev = new ArrayList<Integer>();
	ArrayList<ArrayList<Integer>> result = new ArrayList<ArrayList<Integer>>();
	ArrayList<Integer> elist = new ArrayList<Integer>(); // integers
	ArrayList<Integer> to_spare = new ArrayList<Integer>(); // integers
	ArrayList<ArrayList<Integer>> to_return = new ArrayList<ArrayList<Integer>>(); // will hold a list of lists
	String name1 = null;
	String name2 = null;
	String names1[] = null; // split name
	String names2[] = null;
	int direction;
	int strenght;
	
	@SuppressWarnings("unused")
	public static void main(String[] args) {

		String name2 = "STUART D. BILTON";

		String name1 = "Bilton";
		
		UDFTests nc = new UDFTests();

		// System.out.println("result: " + nc.IsEquivalentName(name1, name2, 1,
		// 0));
		ScalarList<String> dummyList = new ScalarList<String>();
		ScalarList<String> L = GenerateNames("John Smith", dummyList);
		

		/*
		 * String name1 = "GOOGLE INC"; String name2 = "GOOG"; NameCompare nc =
		 * new NameCompare(); System.out.println(name1 + " ? " + name2 +
		 * " result: " + nc.IsEquivalentOrg2Acronym(name1, name2));
		 */
	}

	public void NameCompare() {

	}

	public void PrintResult() {
		for (int i = 0; i < result.size(); i++) {
			// res_prev.clear();
			res_prev = result.get(i);
			System.out.print(i + "th solution:  ");
			for (int j = 0; j < res_prev.size(); j++)
				System.out.println(" " + res_prev.get(j) + " ");

		}
	}

	public Boolean CheckConflict(int index) {
		// two names have a conflict if there is at least one non-match word in
		// each. At least one name should be
		// fully matched. That is, while excluding the numbers
		// where mod 10 is zero, the solution has no less edges than the number
		// of names.

		// at least one non-initial name must match with a non-initial name
		Boolean toreturn = false;
		// res_prev.clear();
		Boolean word_match = false;
		res_prev = result.get(index);
		int count = 0;
		for (int j = 0; j < res_prev.size(); j++) {
			if (res_prev.get(j) % 10 != 0) {
				count++;
				if (names1[res_prev.get(j) / 10 - 1].length() > 1)
					if (names2[res_prev.get(j) % 10 - 1].length() > 1)
						word_match = true;
			}
		}
		if (!word_match)
			return true;
		if ((names1.length > count) && (names2.length > count))
			toreturn = true;
		return toreturn;
	}

	public Boolean CheckDirection(int index) {
		// no cross edges in name-name matches

		res_prev.clear();
		int currentnum = -1;
		int currentfirst = -1;
		int currentsecond = -1;

		res_prev = result.get(index);
		for (int j = 0; j < res_prev.size(); j++) {
			currentnum = res_prev.get(j);
			if (currentnum % 10 != 0)
				if ((currentnum / 10 <= currentfirst)
						|| (currentnum / 10 <= currentsecond))
					return false;
				else {
					currentfirst = currentnum % 10;
					currentsecond = currentnum / 10;

				}
		}
		return true;

	}

	/*************
	 * 
	 * @param name1
	 * @param name2
	 * @param direction
	 *            (0 = any, 1 = same)
	 * @param strength
	 *            (NOT USED FOR NOW; 0 = at least one full name and no
	 *            conflicts, 1 = at least one full name and one initial, no
	 *            conflicts)
	 * @return] will use the following for the select of
	 */

	public static ScalarList<String> GenerateNames(String name1, ScalarList L) {
		
	  // This constructor is deprecated. Replacing with the factory method.
	  // ScalarList<String> toreturn = new ScalarList<String>();
	  ScalarList<String> toreturn = ScalarList.makeScalarListFromType (FieldType.TEXT_TYPE);
	  
		// System.out.println("return type = scalarList");
		//Integer result = 1;
		String n1 = "firstname lastname " + name1;
		String n2 = name1;
		// CORRECTION: First of all, the constructor {@link FieldType(String)} should not have been made public.
		// FieldType field = new FieldType("String"); // note: had to make the constructor public
		// ScalarList.makeScalarListFromType(field);
		// toreturn.setScalarType(field);
		
		toreturn.add(n1);
		toreturn.add(n2);
		
		// System.out.println("type " + toreturn.getScalarType().toString());

		// System.out.println("first element " + toreturn.get(0).toString());
		return toreturn;
		// return result;

	}

	public Boolean IsEquivalentName(String name1, String name2,
			Integer direction, Integer strenght) {
		return IsEquivalent(name1, name2, 0, 1); // overwrite direction and
													// strenght to the most
													// relaxed version, may
													// allow more flexibility
													// later
	}

	public Boolean IsEquivalentCompany(String name1, String name2,
			Integer direction, Integer strenght) {
		return IsEquivalent(name1, name2, 0, 1); // overwrite direction and
													// strenght to the most
													// relaxed version, may
													// allow more flexibility
													// later
	}

	public Boolean IsEquivalent(String name1, String name2, Integer direction,
			Integer strenght) {

		// int direction = 1;
		this.name1 = name1;
		this.name2 = name2;
		this.direction = 1;
		this.strenght = 0;

		res_prev = new ArrayList<Integer>();
		result = new ArrayList<ArrayList<Integer>>();
		elist = new ArrayList<Integer>(); // integers
		to_spare = new ArrayList<Integer>(); // integers
		to_return = new ArrayList<ArrayList<Integer>>(); // will hold a list of lists
		names1 = null; // split name
		names2 = null;

		if ((name1 == "") || (name2 == ""))
			return false;
		String full_name[] = new String[2];
		full_name[0] = name1;
		full_name[1] = name2;

		// EXTRACT SALUTATIONS
		/*
		 * int len; String post_sal[] = new String[2]; for (int j = 0; j<2;
		 * j++){ post_sal[j] = "?"; post_sal[j] =
		 * post_sal[j].concat(full_name[j]); post_sal[j] =
		 * post_sal[j].replaceAll("\\?\\w*\\.", ""); len = 0; if
		 * (post_sal[j].compareTo("?") != 0) len = full_name[j].length()-
		 * post_sal[j].length()-1; //post_sal[j] = post_sal[j].substring(1,);
		 * full_name[j] = full_name[j].substring(len, full_name[j].length()); }
		 */
		/*************************************
		 * 1)normalize and clean up 2)tokenize the two names
		 ***************************************/
		// NORMALIZE AND CLEAN UP
		for (int i = 0; i < 2; i++) {
			full_name[i] = full_name[i].toUpperCase();
			full_name[i] = full_name[i].replaceAll("(\\r\\n)", " ");
			full_name[i] = full_name[i].replaceAll(",", " ");
			full_name[i] = full_name[i].replaceAll("\\.", " ");
			full_name[i] = full_name[i].replaceAll("\\s\\s+", " ");
		}

		// THE TWO NAMES WILL NOW BE SPLIT INTO INDIVIDUAL (SUB)NAMES
		names1 = full_name[0].split("\\s");
		if (names1 == null)
			return false;
		names2 = full_name[1].split("\\s");
		if (names2 == null)
			return false;

		// for ORGANIZATION exclude general ending such as Company, Inc, etc so
		// that matching cannot be based entirely on it

		/*************************************
		 * NOTE: a conflict is when there is an entry in first name AND an entry
		 * in second name that cannot be matched 0) stop early the match if
		 * there is a conflict 1)build the list of choice matches. an entry 23
		 * means second entry in first name and third entry in second name
		 * 2)recursively fill in the solutions 3) check solutions against
		 * parameters
		 ***************************************/
		// quit early
		Boolean match1 = false;
		Boolean match2 = false;
		for (int i = 0; i < names1.length; i++) {
			match1 = false;
			for (int j = 0; j < names2.length; j++) {
				if (names1[i].charAt(0) == names2[j].charAt(0))
					match1 = true;
			}
			if (!match1) {
				// System.out.println(" no match");
				break;
			}
		}

		for (int i = 0; i < names2.length; i++) {
			match2 = false;
			for (int j = 0; j < names1.length; j++) {
				if (names2[i].charAt(0) == names1[j].charAt(0))
					match2 = true;
			}
			if (!match2) {
				// System.out.println(" no match");
				break;
			}
		}
		if ((!match1) && (!match2))
			return false;
		// FILL IN ARRAY THAT WILL HOLD THE LEVELS OF SIMILARITY BETWEEN
		// (SUB)NAMES

		// build exhaustive list of matches. When exact full name matches
		// eliminate all the other matches for all relevant entries.
		// Add to a list to_spare to eliminate also entries that are calculated
		// later

		int val = 0;

		for (int i = 0; i < names1.length; i++) {
			elist.add((i + 1) * 10);
			for (int j = 0; j < names2.length; j++) {
				val = (i + 1) * 10 + (j + 1);
				if (names1[i].compareTo(names2[j]) == 0) {
					// full name match
					to_spare.add(val);
					elist.add(val);
				} else if ((names1[i].length() == 1)
						&& (names2[j].startsWith(names1[i])))
					elist.add(val); // match full name w. initial or initial w.
									// initial

				else if ((names2[j].length() == 1)
						&& (names1[i].startsWith(names2[j])))
					elist.add(val); // match initial w. full name

			}
		}
		// eliminate from elist according to to_spare
		int delval = 0;
		int allval = 0;
		int firstnum = 0;
		int secondnum = 0;
		for (int i = 0; i < to_spare.size(); i++) {
			delval = to_spare.get(i);
			firstnum = delval / 10;
			secondnum = delval % 10;
			for (int j = 0; j < elist.size(); j++) {
				allval = elist.get(j);
				if ((allval != delval)
						&& ((firstnum == allval / 10) || (secondnum == allval % 10)))
					elist.remove(j);
			}
		}

		// now match recursively

		if (elist.size() > 0)
			result = GetMatch(0);

		// detect conflicts and check against parameters
		// PrintResult();

		for (int i = 0; i < result.size(); i++) {
			// res_prev.clear();
			// res_prev = (ArrayList)result.get(i);
			/*
			 * System.out.print(i+"th solution:  "); for (int j = 0; j <
			 * res_prev.size(); j++)
			 * System.out.println(" "+res_prev.get(j)+" ");
			 */
			if (!CheckConflict(i)) {
				// System.out.println("CONFLICT");
				// if (direction == 0)
				return true;
				// else if (CheckDirection(i))
				// return true;
			}

		}

		return false;

	}

	/**************************************/
	public ArrayList<ArrayList<Integer>> GetMatch(int index) {

		int firstval = 0;
		int curval = 0;
		boolean skip = false;
		boolean lastlevel = false;
		int nextlevel = elist.size();
		curval = elist.get(index);
		if (elist.get(elist.size() - 1) / 10 == curval / 10)
			lastlevel = true;
		if (!lastlevel) {
			// find the next level
			for (int i = index; i < elist.size(); i++) {
				firstval = elist.get(i);
				if (firstval / 10 > curval / 10) {
					nextlevel = i;
					break;
				}
			}
		}
		for (int i = index; i < nextlevel; i++) {
			firstval = elist.get(i);

			if (curval % 10 != 0) {
				for (int k = 0; k < res_prev.size(); k++)
					if (res_prev.get(k) % 10 == curval % 10)
						skip = true;
			}
			if (!skip)
				res_prev.add(firstval);

			// assemble the array to return and return;
			if (!lastlevel) {
				GetMatch(nextlevel);

			} else if (lastlevel) {
				ArrayList<Integer> new_return = new ArrayList<Integer>();
				new_return.addAll(res_prev);
				to_return.add(new_return);
				res_prev.clear();
			}

		}
		return to_return;
	}

	public Boolean IsEquivalentOrg2Acronym(String org_name, String acronym_name) {
		/*
		 * Assumption: 1) organization name has at least two words 2) acronym
		 * has at least two characters The two inputs match iff * (at least two
		 * of the acronym characters match first characters in two words of the
		 * full name, in the same order) OR (for short org names GOOGLE INC ==
		 * GOOG may match at least three characters in first name) * first
		 * acronym character must match first character in first word of full
		 * name
		 */

		if ((org_name == null) || (acronym_name == null)
				|| (acronym_name.length() < 2))
			return false;

		String org = org_name.toUpperCase();
		String acronym = acronym_name.toUpperCase();

		if (acronym.charAt(0) != org.charAt(0))
			return false;

		org = org.replaceAll("(\\r\\n)|(\\.)|(\\s\\s+)|(,)", " ");
		String[] org_array = org.split("\\s");
		if ((org_array == null) || org_array.length < 2)
			return false;

		// System.out.println(org_name + " ? "+ acronym_name);

		int counter = 0;
		// CASE 1: (at least two of the acronym characters match first
		// characters in two words of the full name, in the same order)
		int last_j = -1;

		for (int i = 0; i < acronym.length(); i++) {
			for (int j = last_j + 1; j < org_array.length; j++) {
				if ((org_array[j] != null) && (org_array[j].length() > 0))
					if (acronym.charAt(i) == org_array[j].charAt(0)) {
						counter++;
						if (counter == 2)
							return true;
						last_j = j;
						break;
					}

			}
		}

		// System.out.println("case2: " + org_array[0] + " ? "+ acronym);

		// CASE 2: (for short org names GOOGLE INC == GOOG may match at least
		// three characters in first name)
		counter = 0;
		last_j = -1; // iterate now over the characters in first word of org
						// name
		// org_array[0] = org_array[0].toUpperCase();
		if ((org_array.length <= 3) && (org_array[0].length() > 0)) // example
																	// Vodafone
																	// Group Plc
		{
			for (int i = 0; i < acronym.length(); i++) {
				for (int j = last_j + 1; j < org_array[0].length(); j++) {
					// System.out.println("compare: "+
					// acronym.charAt(i)+" and "+ org_array[0].charAt(j));
					if (acronym.charAt(i) == org_array[0].charAt(j)) {
						// System.out.println("match: acr " + i + " org "+j);
						counter++;
						if (counter == 3)
							return true;
						last_j = j;
						break;
					}
				}

			}
		}
		// System.out.println("false");
		return false;
	}

}
