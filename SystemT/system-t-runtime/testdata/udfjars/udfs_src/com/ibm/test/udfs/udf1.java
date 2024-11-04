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

import com.ibm.avatar.algebra.datamodel.Span;

public class udf1 {
	
	public String toLowerCase(String s)  {
		return s.toLowerCase();
	}

	public String toUpperCase(String s) {
		return s.toUpperCase();
	}
	
	public String f1(Integer p1, Integer p2, String p3, String p4) {
		return String.format("%s_%s_%s_%s", p1, p2, p3, p4);
	}
	
	public String f1() {
		return "F1";
	}
	
	public Span combineSpans(Span firstSpan, Span secondSpan) {
		int begin = firstSpan.getBegin();
		int end = secondSpan.getEnd();

		if (begin > end) {
			throw new RuntimeException(String.format(
					"Arguments to CombineSpans must be in left-to-right order"
							+ " (Input spans were %s and %s, respectively)",
					firstSpan, secondSpan));
		}

		Span ret = Span.makeBaseSpan(firstSpan, begin, end);

		// Propagate cached token offsets, if available.
		ret.setBeginTok(firstSpan.getBeginTok());
		ret.setEndTok(secondSpan.getEndTok());

		return ret; 
	}
	
	public Boolean spanGreaterThan(Span span, Integer size) {
		if ((span.getEnd() - span.getBegin()) > size)
			return true;
		return false;
	}
	
	public Span format(Span span, Boolean truncate) {
		int begin = span.getBegin();
		int end = span.getEnd();
		if (truncate) {
			if (end - begin > 5)
				end = begin + 5;
		}
		Span ret = Span.makeBaseSpan(span, begin, end);
		ret.setBeginTok(span.getBeginTok());
		ret.setEndTok(span.getEndTok());
		
		return ret;		
	}
}
