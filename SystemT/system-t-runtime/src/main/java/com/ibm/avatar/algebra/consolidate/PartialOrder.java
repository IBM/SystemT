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
package com.ibm.avatar.algebra.consolidate;

import com.ibm.avatar.algebra.datamodel.Span;

/**
 * Class that encapsulates a partial order over objects.
 * 
 */
public abstract class PartialOrder<type> {

  /** Returns true if lhs is "greater than" rhs */
  public abstract boolean gt(type lhs, type rhs);

  /** Returns true if lhs is "equal to" rhs */
  public abstract boolean eq(type lhs, type rhs);

  // Current set of orderings.
  public static final containedWithin CONTAINED_WITHIN = new containedWithin();
  public static final notContainedWithin NOT_CONTAINED_WITHIN = new notContainedWithin();

  // Yunyao: added 07/26/2007
  // begin
  public static final containButNotEq CONTAINS_BUT_NOT_EQUAL = new containButNotEq();
  // end

  // Yunyao: added 10/31/2007 to remove overlapping annotations from left to
  // right
  // begin
  public static final overlap OVERLAP_ORDERED = new overlap();
  // end

  public static final exactMatch EXACT_MATCH = new exactMatch();

  public static final String CONTAINEDWITHIN_ORDERNAME = "ContainedWithin";
  public static final String NOTCONTAINEDWITHIN_ORDERNAME = "NotContainedWithin";
  public static final String CONTAINSBUTNOTEQUAL_ORDERNAME = "ContainsButNotEqual";
  public static final String OVERLAPORDERED_ORDERNAME = "OverlapOrdered";
  public static final String EXACTMATCH_ORDERNAME = "ExactMatch";
  // New name constants go here.

  /** List of names of all partial orders we know about. */
  public static final String[] ORDER_NAMES =
      {CONTAINEDWITHIN_ORDERNAME, NOTCONTAINEDWITHIN_ORDERNAME, CONTAINSBUTNOTEQUAL_ORDERNAME,
          OVERLAPORDERED_ORDERNAME, EXACTMATCH_ORDERNAME};

  /**
   * Map one of the partial order names in the parser to an object instance. Add new entries to this
   * function as you add new partial order types.
   * 
   * @param name name used to identify the partial order in AOG
   */
  public static final PartialOrder<Span> strToOrderObj(String name) {

    if (CONTAINEDWITHIN_ORDERNAME.equals(name)) {
      return CONTAINED_WITHIN;
    } else if (NOTCONTAINEDWITHIN_ORDERNAME.equals(name)) {
      return NOT_CONTAINED_WITHIN;
    } else if (CONTAINSBUTNOTEQUAL_ORDERNAME.equals(name)) {
      return CONTAINS_BUT_NOT_EQUAL;
    } else if (OVERLAPORDERED_ORDERNAME.equals(name)) {
      return OVERLAP_ORDERED;
    } else if (EXACTMATCH_ORDERNAME.equals(name)) {
      return EXACT_MATCH;
    }
    // New string comparisons go here
    else {
      throw new IllegalArgumentException("Don't understand partial order name '" + name + "'");
    }
  }

  /**
   * Partial order over Spans; span1 > span2 if span2 is entirely contained within span1.
   */
  private static class containedWithin extends PartialOrder<Span> {

    @Override
    public boolean gt(Span lhSpan, Span rhSpan) {
      boolean ret;

      // null spans are never removed by gt
      if ((lhSpan == null) || (rhSpan == null))
        return false;

      if (lhSpan.getBegin() == rhSpan.getBegin() && lhSpan.getEnd() > rhSpan.getEnd()) {
        ret = true;
      } else if (lhSpan.getEnd() == rhSpan.getEnd() && lhSpan.getBegin() < rhSpan.getBegin()) {
        ret = true;
      } else if (lhSpan.getBegin() < rhSpan.getBegin() && lhSpan.getEnd() > rhSpan.getEnd()) {
        ret = true;
      } else {
        ret = false;
      }

      // System.err.printf(" <%d,%d> %s <%d,%d>\n", lhSpan.getBegin(),
      // lhSpan.getEnd(), ret ? ">" : "?", rhSpan.getBegin(),
      // rhSpan.getEnd());

      return ret;
    }

    @Override
    public boolean eq(Span lhSpan, Span rhSpan) {
      if ((lhSpan == null) && (rhSpan == null))
        return true;
      if ((lhSpan == null) || (rhSpan == null))
        return false;

      if (lhSpan.getBegin() == rhSpan.getBegin() && lhSpan.getEnd() == rhSpan.getEnd()) {
        return true;
      } else {
        return false;
      }
    }

  }

  /**
   * Inverse of {@link containedWithin}; span1 > span2 if *span1* is entirely contained within
   * *span2*.
   */
  private static class notContainedWithin extends PartialOrder<Span> {

    @Override
    public boolean gt(Span lhSpan, Span rhSpan) {
      boolean ret;

      // null spans are never removed by gt
      if ((lhSpan == null) || (rhSpan == null))
        return false;

      if (rhSpan.getBegin() == lhSpan.getBegin() && rhSpan.getEnd() > lhSpan.getEnd()) {
        ret = true;
      } else if (rhSpan.getEnd() == lhSpan.getEnd() && rhSpan.getBegin() < lhSpan.getBegin()) {
        ret = true;
      } else if (rhSpan.getBegin() < lhSpan.getBegin() && rhSpan.getEnd() > lhSpan.getEnd()) {
        ret = true;
      } else {
        ret = false;
      }

      // System.err.printf(" <%d,%d> %s <%d,%d>\n", lhSpan.getBegin(),
      // lhSpan.getEnd(), ret ? ">" : "?", rhSpan.getBegin(),
      // rhSpan.getEnd());

      return ret;
    }

    @Override
    public boolean eq(Span lhSpan, Span rhSpan) {

      if ((lhSpan == null) && (rhSpan == null))
        return true;
      if ((lhSpan == null) || (rhSpan == null))
        return false;

      if (lhSpan.getBegin() == rhSpan.getBegin() && lhSpan.getEnd() == rhSpan.getEnd()) {
        return true;
      } else {
        return false;
      }
    }

  }

  // Yunyao: added 07/26/2007
  // begin
  /**
   * Partial order over Spans; span1 > span2 if span2 contains but not equals to span1
   */
  private static class containButNotEq extends PartialOrder<Span> {
    @Override
    public boolean gt(Span lhSpan, Span rhSpan) {

      boolean ret;

      // null spans are never removed by gt
      if ((lhSpan == null) || (rhSpan == null))
        return false;

      if (lhSpan.getBegin() == rhSpan.getBegin() && lhSpan.getEnd() > rhSpan.getEnd()) {
        ret = true;
      } else if (lhSpan.getEnd() == rhSpan.getEnd() && lhSpan.getBegin() < rhSpan.getBegin()) {
        ret = true;
      } else if (lhSpan.getBegin() < rhSpan.getBegin() && lhSpan.getEnd() > rhSpan.getEnd()) {
        ret = true;
      } else {
        ret = false;
      }

      // System.err.printf(" <%d,%d> %s <%d,%d>\n", lhSpan.getBegin(),
      // lhSpan.getEnd(), ret ? ">" : "?", rhSpan.getBegin(),
      // rhSpan.getEnd());

      return ret;
    }

    @Override
    public boolean eq(Span lhSpan, Span rhSpan) {

      // We always return false, since we want to keep spans that are the
      // same.
      return false;
    }

  }

  // end

  // Yunyao: added 10/31/2007
  // begin
  /**
   * Partial order over Spans; span1 > span2 if span2 overlaps with span1 but span1.begin <
   * span2.begin to span1
   */
  private static class overlap extends PartialOrder<Span> {
    @Override
    public boolean gt(Span lhSpan, Span rhSpan) {
      boolean ret;

      // null spans are never removed by gt
      if ((lhSpan == null) || (rhSpan == null))
        return false;

      if (lhSpan.getBegin() <= rhSpan.getBegin() && lhSpan.getEnd() >= rhSpan.getBegin()) {
        ret = true;
      } else {
        ret = false;
      }

      // System.err.printf(" <%d,%d> %s <%d,%d>\n", lhSpan.getBegin(),
      // lhSpan.getEnd(), ret ? ">" : "?", rhSpan.getBegin(),
      // rhSpan.getEnd());

      return ret;
    }

    @Override
    public boolean eq(Span lhSpan, Span rhSpan) {

      if ((lhSpan == null) && (rhSpan == null))
        return true;
      if ((lhSpan == null) || (rhSpan == null))
        return false;

      if (lhSpan.getBegin() == rhSpan.getBegin() && lhSpan.getEnd() == rhSpan.getEnd()) {
        return true;
      } else {
        return false;
      }
    }

  }

  // end

  /**
   * Partial order for "ExactMatch" consolidation; span a dominates span b if they mark exactly the
   * same region of the text.
   */
  private static class exactMatch extends PartialOrder<Span> {
    @Override
    public boolean gt(Span lhSpan, Span rhSpan) {
      // Nothing dominates anything else in this ordering.
      return false;
    }

    @Override
    public boolean eq(Span lhSpan, Span rhSpan) {
      // Detect equality so that the PartialOrderConsImpl class will
      // remove one of the equal spans according to order.

      if ((lhSpan == null) && (rhSpan == null))
        return true;
      if ((lhSpan == null) || (rhSpan == null))
        return false;

      if (lhSpan.getBegin() == rhSpan.getBegin() && lhSpan.getEnd() == rhSpan.getEnd()) {
        return true;
      } else {
        return false;
      }
    }

  }
}
