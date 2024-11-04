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
package com.ibm.avatar.algebra.util.lang;

import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.Constants;

/**
 * Codes for different languages that AQL supports.
 */
public enum LangCode {
  // NOTE: Keep these in alphabetical order!!!
  aa, ab, ae, af, ak, am, an, ar, as, av, ay, az, ba, be, bg, bh, bi, bm, bn, bo, br, bs, ca, ce, ch, co, cr, cs, cu, cv, cy, da, de, dv, dz, ee, el, en, eo, es, et, eu, fa, ff, fi, fj, fo, fr, fy, ga, gd, gl, gn, gu, gv, ha, he, hi, ho, hr, ht, hu, hy, hz, ia, id, ie, ig, ii, ik, io, is, it, iu, ja, jv, ka, kg, ki, kj, kk, kl, km, kn, ko, kr, ks, ku, kv, kw, ky, la, lb, lg, li, ln, lo, lt, lu, lv, mg, mh, mi, mk, ml, mn, mr, ms, mt, my, na, nb, nd, ne, ng, nl, nn, no, nr, nv, ny, oc, oj, om, or, os, pa, pi, pl, ps, pt, qu, rm, rn, ro, ru, rw, sa, sc, sd, se, sg, sh, si, sk, sl, sm, sn, so, sq, sr, ss, st, su, sv, sw, ta, te, tg, th, ti, tk, tl, tn, to, tr, ts, tt, tw, ty, ug, uk, ur, uz, ve, vi, vo, wa, wo, xh, yi, yo, za, zh, zu,

  // zz is technically NOT a valid language code (it's the *country* code for
  // "unspecified country"), but many people within IBM seem to use it in
  // place of "x-unspecified", so we support it as a language code.
  zz,

  x_unspecified;

  /** Language code to use when no code is specified. */
  public static final LangCode DEFAULT_LANG_CODE = en;

  /** Maximum number of language codes allowed to be specified */
  public static final int MAX_NUM_LANG_CODES = 31;

  /**
   * Language codes that are "supported" by SystemT
   */
  // public static final LangCode[] SUPPORTED_LANG_CODES = { de, es, en, fr,
  // it, ja,
  // ko, zh, x_unspecified };

  /**
   * Language codes that are enabled by default for dictionary evaluation, if no language set is
   * specified for a dictionary.
   */
  public static final LangCode[] DICT_DEFAULT_LANG_CODES = {de, es, en, fr, it, x_unspecified};
  // Laura 02/10/2011: Temporary patch for dictionary matching in Gumshoe.
  /*
   * { de, es, en, fr, it, x_unspecified, // Chinese zh, // Korean ko, // Vietnamese vi, // Japanese
   * ja};
   */
  // Laura 02/10/2011: END change

  /**
   * The maximum allowable ordinal value for a LangCode; if this number changes, the implementation
   * of the LanguageSet class may break.
   */
  private static final int MAX_ORDINAL = LangCode.values().length;

  /**
   * Read-only table used by {@link #ordinalToLangCode(int)} to convert numeric language codes to
   * enum constants.
   */
  private static final LangCode[] langCodesByOrdinal = makeLangCodesByOrdinal();

  private static final LangCode[] makeLangCodesByOrdinal() {
    LangCode[] ret = new LangCode[MAX_ORDINAL];
    for (LangCode lcode : values()) {
      ret[lcode.ordinal()] = lcode;
    }
    return ret;
  }

  /**
   * Inverts the built-in ordinal mapping of language codes.
   *
   * @param ordinal value that would be returned by {@link #ordinal()}.
   * @return the LangCode that would return the indicated value
   */
  public static LangCode ordinalToLangCode(int ordinal) {
    return langCodesByOrdinal[ordinal];
  }

  /**
   * Convenience method to convert the string representation of a language code to a
   * {@link com.ibm.avatar.algebra.util.lang.LangCode} value.
   *
   * @param langCodeStr string representation of a language code
   * @return the internal representation of this language
   */
  public static LangCode strToLangCode(String langCodeStr) {

    // Our enum constants are all lowercase...
    if (Character.isUpperCase(langCodeStr.charAt(0))) {
      langCodeStr = langCodeStr.toLowerCase();
    }

    try {
      // Try to just convert directly to the enum
      return valueOf(langCodeStr);
    } catch (IllegalArgumentException e) {
      // Hmm, that didn't work. Activate special-case logic.
      if ("x-unspecified".equals(langCodeStr)) {
        return x_unspecified;
      } else if (5 == langCodeStr.length()
          && ('-' == langCodeStr.charAt(2) || '_' == langCodeStr.charAt(2))) {
        // Long-form language code string, like "en-US" or "zh-CN"
        // Chop off the end and try again.
        return strToLangCode(langCodeStr.substring(0, 2));
      } else {
        throw new IllegalArgumentException(
            String.format("Don't understand language code '%s'", langCodeStr));
      }
    }
  }

  /**
   * Convenience method to convert {@link com.ibm.avatar.algebra.util.lang.LangCode} to string
   * representation.
   *
   * @param code an internal Text Analytics language code
   * @return standard string representation of the language code
   */
  public static String langCodeToStr(LangCode code) {

    if (x_unspecified.equals(code)) {
      // No dashes allowed in Java enum constant names.
      return "x-unspecified";
    }

    // Currently, all other language codes are the same as the enum
    // representation
    return code.toString();

  }

  /**
   * Method to validate comma delimited language code string. This method will throw exception:-
   * <br/>
   * a) If the number of language codes specified is greater than the maximum allowed <br/>
   * b) For invalid/unsupported language codes. <br/>
   *
   * @param languageCodeStr comma delimited string of language codes
   * @throws IllegalArgumentException This method will throw
   *         {@link java.lang.IllegalArgumentException} for language string containing unsupported
   *         languages or more than the number of languages allowed
   */
  public static void validateLangStr(String languageCodeStr) {
    String[] givenLanguageCodes = StringUtils.split(languageCodeStr, Constants.COMMA);
    // Buffer to maintain list of invalid language codes
    StringBuilder invalidLangCodes = new StringBuilder();

    if (givenLanguageCodes.length > MAX_NUM_LANG_CODES) {
      throw new IllegalArgumentException(String.format(
          "Incorrect number of language codes specified. Ensure that the number of language codes is in the range 1 to %d.",
          MAX_NUM_LANG_CODES));
    }

    for (String langCode : givenLanguageCodes) {
      try {
        strToLangCode(langCode);
      } catch (IllegalArgumentException e) {
        // Add to list of invalid language codes
        invalidLangCodes.append(langCode);
        invalidLangCodes.append(Constants.COMMA);
      }
    }

    // Throw exception; if any of the given language code is invalid
    if (invalidLangCodes.length() > 0) {
      throw new IllegalArgumentException(String.format("Invalid/Unsupported language codes: %s",
          invalidLangCodes.substring(0, invalidLangCodes.length() - 1)));
    }
  }
}
