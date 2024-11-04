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

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Queue;
import java.util.LinkedList;

import com.ibm.avatar.algebra.datamodel.ScalarList;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Text;

/** 
 * Container for Gumshue Local Analysis UDFs
 * 
 *
 */
public class W3LAPortedUDF {
	
	//private static VGFlow default_flow =null;
	private static final int DEFAULT_SNIPPET_SIZE = 300;
	private static  Queue Murl = new LinkedList();
	private static  Queue Page = new LinkedList();
	private static  Integer UID = null;
	private static  Integer Page1 =null;
	private static  Integer i=0; 
	private static  Integer i1=0;
	public static void main(String[] args) throws URISyntaxException {
		ScalarList<String> dummy = new ScalarList<String>();
		
/*		dummy.add("IBM PSL");
		dummy.add("Pune");
		dummy.add("Almaden");
		GenerateQuery(dummy);
*/		//GenerateURI("http://w3-1.ibm.com:80/hr/americas/pbc/");
		//ScalarList<String> dum = new ScalarList<String>();
		//dum.add("");
		//GetNSF("/software/sales/saletool.nsf/920cfec8f69b3bcf852567ba0002c915/aeb1f6369ed1146a87256ea7005ce3f9/body/0.328a",
		//		"aeb1f6369ed1146a87256ea7005ce3f9","/920cfec8f69b3bcf852567ba0002c915/");
		//GetNSF("i_dir/info/infoger.nsf/0592739be28ce299c1256544005f07b2/a4bf30d052eff4fdc12566dc0065c1e1","a4bf30d052eff4fdc12566dc0065c1e1","/0592739be28ce299c1256544005f07b2/");
		//GetDedup("","","",null, "/b_dir/blueprint.nsf/53dac788da6a088e85256c170075d763/871a636f9d3b7075c1257410003f5eab/body/1.2f0e");
		GetURL("http://www.hcf.com.au/media_center.asp?ID=20&member_id=Guest&agentID=RBA215|200805291628", 1);
		temp("You and IBM - /Belgium / Luxembourg | Your career - Belgium LuxembourgIndividual Development plan","You and IBM Belgium Luxembourg Your career LuxembourgIndividual Development plan","Belgium Luxembourg",2);
	}

	
	/**
	 * 
	 * @param str
	 * @return the all lower case version of the input span.
	 */
	public String toLowerCase(Span span){
		
		return span.getText().toLowerCase();
	}

public static String pushval(Integer Murl1,Integer Page2,String val){
	
	//System.out.printf("\nPushPage\n"+ Page2);
	String splitquery[]=val.split("&");
	
	//System.out.printf("kk"+splitquery.length );
	//for(int i=1;i<=splitquery.length;i++)
	//{
	System.out.printf("Pu|"+ Murl1);
	Murl.add(Murl1);
	Page.add(Page2);
	//}
	return null;
}

public static Integer popval(Integer no){
	
	Object return_value=null;
		switch (no) {
	case 1:
		return_value = Murl.poll();
		
		//return_value=1;
		 //System.out.printf("\nPOP MURL Count for doc"+ i);
		// i=i+1;
		//i1=i1-1;
		System.out.printf("Po|"+ (Integer)return_value);
		return (Integer)return_value;
		//return UID; 
	case 2:
		return_value = Page.poll();
		//return_value=2;
		//System.out.printf("\npoppage"+Murl.isEmpty());
		//i1=i1-1;
		//System.out.printf("\nPopID\n"+ (Integer)return_value);
		return (Integer)return_value;
		//return Page1; 
	}
	
	return null;
	
}
public static String getid(String x){
	
	i=i+1;
	return i.toString(); 
	}
		
	

	/*
	 * generate URI
	 */
	public static String GenerateURI(String string)
	{
		String url_string="";
		string = EscaspeHTML(string);
		URI url;
		 Pattern space_pattern = Pattern.compile(" ");
		 try {
	            url = new URI(string);
	        } catch (URISyntaxException e1) {
	        	string = space_pattern.matcher(string).replaceAll("%20");
	        	try {
					url = new URI(string);
				} catch (URISyntaxException e) {
		        	url = null;
				}
			}
	        if(url!=null)
	         url_string = url.toString(); 
	        	
	      return url_string;
	}
	
	/**
	 * Output Url
	 * @param string
	 * @return
	 */
	public static String GetOutputUrl(String string)
	{
		String url2 ="";
		URI url;
		try{
			 url =  new URI(string);
		}
		catch (URISyntaxException e) {
			url=null;
		}
		if(url!=null)
			url2 = url.toASCIIString();
		return url2;
	}
	
	/**
	 * concatenating the strings with . for getting host1
	 * @param s1
	 * @param s2
	 * @return
	 */
	public static String GetHost1(String s1,String s2)
	{
		return (s1+"."+s2);
	}
	/**
	 * creating the Normalized String
	 * @param scheme
	 * @param _host_1
	 * @param _path_1
	 * @param _query_1
	 * @return
	 */
	public static String GetNormalizedString(String scheme,String _host_1,String port,String _path_1,String  _query_1)
	{
		URI url1;
		String _string_1 = "";
		int portnumber = -1 ;
		if(port!=null && port!="")
			portnumber = Integer.parseInt(port);
		if(_query_1.equals(""))
			_query_1=null;
		try{
			url1 = new URI(scheme, null, _host_1, portnumber, _path_1, _query_1, null);
		}
		catch (URISyntaxException e) {
			System.err.printf("Could not form url_1: %s: host = %s, path =%s\n", e.getMessage(), _host_1, _path_1);
			url1 = null;
		}
		if(url1!=null){
			try{
				_string_1 = URLDecoder.decode(url1.toString(), "utf-8");
			}
			catch (Exception e) {
				System.err.printf("Could not decode string1: %s: string = %s\n", e.getMessage(), _string_1);
			}
		}
		return _string_1;
	}
	
	/**
	 * creating More Normalized String
	 * @param scheme
	 * @param _host_2
	 * @param _path_2
	 * @param _query_1
	 * @return
	 */
	
	public static String GetMoreNormalizedString(String scheme,String _host_2,String port,String _path_2,String _query_1)
	{
		URI url2;
		String _string_2 = "";
		int portnumber = -1 ;
		if(port!=null && port!="") 
			portnumber = Integer.parseInt(port);
		
		if(_query_1.equals(""))
			_query_1=null;
		try{
			url2 = new URI(scheme, null, _host_2, portnumber, _path_2, _query_1, null);
		}
		catch (URISyntaxException e) {
			System.err.printf("Could not form url_2: %s: host = %s, path =%s\n", e.getMessage(), _host_2, _path_2);
			url2 = null;
		}
		
		if(url2!=null){
			try{
				_string_2 = URLDecoder.decode(url2.toString(), "utf-8");
			}
			catch (Exception e) {
				System.err.printf("Could not decode string2: %s: string = %s\n", e.getMessage(), url2.toString());
			}
		}
		return _string_2;
	}
	
	/**
	 * Generating the Dedup String
	 * @param _scheme
	 * @param _host_2
	 * @param _path_3
	 * @param _query_3
	 * @return
	 */
	public static String GetDedup(String _scheme,String _host_2,String _path_3,Span _query_3,String ESP2)
	{
		Pattern _nsf_pattern = Pattern.compile("\\.nsf/(?:0|[\\dA-Fa-f]{32})/([\\dA-Fa-f]{32})");
		String _string_3 = _path_3;
		String query = _query_3.getText();
		
		Matcher nsf_matcher = _nsf_pattern.matcher(ESP2);
		boolean _is_nsf = nsf_matcher.find();
		if(_is_nsf)
			query = null ;
		
		if (query!=null && (!(query.equals("")))) {
			_string_3 += "?" + query;
		}
		if ("".equals(_string_3)) {
			_string_3 = _scheme + "://" + _host_2;
		}

		if (_string_3 == null)
			_string_3 = "";
			
		try {
			
			_string_3 = URLDecoder.decode(_string_3, "utf-8");
			
		} catch (Exception e) {
			System.err.printf("Could not decode string3: %s: string = %s\n", e.getMessage(), _string_3);
		}
		return _string_3;
	}
	
	/**
	 * Generating the NSF value 
	 * @param nsf
	 * @param match
	 * @param replace
	 * @return
	 */
	public static String GetNSF(String before,String after,String group1)
	{	
		String nsf = before + ".nsf" + after + "/" + group1 ;
		return nsf;
	}
	/**
	 * creating the query string from List of query segments
	 * @param queries
	 * @return
	 */
	public static String CreateQuery(ScalarList<Span> queries)
	{	
		StringBuffer sbuf = new StringBuffer();
		int count = 0;
		int max = queries.size();
		for (Iterator iterator = queries.iterator(); iterator.hasNext();) {
			Span span = (Span) iterator.next();
			String q = span.getText();
			if (count >= max)
        		break;
        	if (count != 0) {
        		sbuf.append("&");
        	}
        	count++;
			sbuf.append(q);
		}
		return sbuf.toString();
	}
	/**
	 * method for replacing the special characters from URL
	 * @param url
	 * @return
	 */
	public static String EscaspeHTML(String inputStr)
	{
		Pattern space_pattern = Pattern.compile(" ");
		Pattern tilde_pattern = Pattern.compile("\\~");
		Pattern pipe_pattern = Pattern.compile("\\|");
		Pattern lessthan_pattern = Pattern.compile("\\<");
		Pattern greaterthan_pattern = Pattern.compile("\\>");
		Pattern cap_pattern = Pattern.compile("\\^");
		Pattern startbrace_pattern = Pattern.compile("\\{");
		Pattern endbrace_pattern = Pattern.compile("\\}");
		Pattern apostrophe_pattern = Pattern.compile("\\`");
		
		String str = space_pattern.matcher(inputStr).replaceAll("%20");
		str = tilde_pattern.matcher(str).replaceAll("%7E");
		str = pipe_pattern.matcher(str).replaceAll("%7C");
		str = lessthan_pattern.matcher(str).replaceAll("%3C");
		str = greaterthan_pattern.matcher(str).replaceAll("%3E");
		str = cap_pattern.matcher(str).replaceAll("%5E");
		str = startbrace_pattern.matcher(str).replaceAll("%7B");
		str = endbrace_pattern.matcher(str).replaceAll("%7D");
		str = apostrophe_pattern.matcher(str).replaceAll("%60");
		 
		return str;
	}
	/**
	 * retrieving the scheme,host,path,query,fragment from the URL
	 * @param url
	 * @param no
	 * @return
	 */
	public static String GetURL(String url,Integer no) {
		URI uri = null;
		String url_str = EscaspeHTML(url);
		try{
			uri = new URI(url_str);
		}
		catch (URISyntaxException e) {
			System.err.printf("The URI could not be created \n", e.getMessage(), url_str);
		}
		catch (Exception e) {
			System.err.printf("The URI could not be created \n", e.getMessage(), url_str);
		}
		String return_value = "";
		if(uri != null) {
			switch (no) {
				case 1:
					return_value = uri.getScheme();
					break;
				case 2:
					return_value = uri.getHost();
					break;
				case 3:
					return_value = uri.getPath();
					break;
				case 4:
					return_value = uri.getQuery();
					break;
				case 5:
					return_value = uri.getFragment();
					break;
				default:
					break;
			}
		}
		
		if(return_value==null)
			return_value = "" ;
		
		return return_value;
	}
	/**
	 * retrieving the port from URL
	 * @param url
	 * @return
	
	public static String GetPort(String url)
	{
		String value = "";
		URI uri = null;
		Pattern space_pattern = Pattern.compile(" ");
		Integer return_value = null;
		try {
			uri = new URI(url);
			return_value = uri.getPort();
			
		}
		catch (URISyntaxException e) {
			String str = space_pattern.matcher(url).replaceAll("%20");
			try {
				uri = new URI(str);
				return_value = uri.getPort();
			} catch (URISyntaxException e1) {
				url = null;
			}
		}
		if(return_value!=null)
			 value = return_value.toString() ;
		return value ;
	}
	 */
	
	public static String GetPort(String url) 
    { 
            String value = ""; 
            URI uri = null; 
            String url_str = EscaspeHTML(url); 
            Integer return_value = null; 
            try { 
                    uri = new URI(url_str); 
                    return_value = uri.getPort(); 
            } 
            catch (URISyntaxException e) { 
                    url =null; 
            } 
            if(return_value!=null)
            {
             	value = return_value.toString() ; 
            }
            return value ; 
    }
	
	
	
	/**
	 * 
	 * @param Concatenates the contents of the list using the input separator.
	 * @return
	 */
	public static String ListJoin(ScalarList<Span> list, String sep)
	{	
		StringBuffer out = new StringBuffer();

		for (int i = 0; i < list.size(); i++) {
			
			if(i > 0)
				out.append(sep);
			
			out.append(list.get(i).getText());			
		}
		return out.toString();
	}
/**
	 * Convert URL into URI and then apply resolve(url).toString()
	 * 
	 * @param BaseURL
	 *            ,url
	 * @return
	 */
	public static String resolveUrl(String baseURL, String anchorURL) {

						
		URI page_url = null;
		String url_str = EscaspeHTML(baseURL);
		try {
			page_url = new URI(url_str);
			return (page_url.resolve(anchorURL).toString());
		} catch (URISyntaxException e) {
			System.err.printf("The URI could not be created \n",
					e.getMessage(), url_str);
		} catch (Exception e) {
			System.err.printf("The URI could not be created \n",
					e.getMessage(), url_str);
		}
		return null;
	}

/**
	 * Removing the skip tags from the detagged text
	 * @param text
	 * @param skiplist
	 * @return
	 */
	public static String SkipTags(String text,ScalarList<Span> skiplist)
	{
		for (Iterator iterator = skiplist.iterator(); iterator.hasNext();) {
			Span span = (Span) iterator.next();
			try {
				String str = span.getText();
				Integer begin = span.getBegin();
				Integer end = span.getEnd();
				text = text.substring(0, begin) + text.substring(end, text.length());
			} catch (Exception e) {
				System.err.printf("", e.getMessage(), "");
			}
		}
		return text;
	}
	

	
	/**
	 * generic method which will return nth element from a List
	 * @param results
	 * @param index
	 * @return
	 */
	public static String getElementfromList(ScalarList<String> results,Integer index)
	{	
		if(results!=null && results.size() > 0 && index < results.size())
			return results.get(index);
		else
			return "";
	}
	

	

	
	public static String GenerateURIAll(String linkurl,String scheme,String host,String port)
	{
		String url_string="";
		URI link_url=null;
		linkurl = EscaspeHTML(linkurl);
		 try {
			 link_url = new URI(linkurl);
			  //Integer port1 = new Integer(port) ;
			 Integer port1 = -1 ;
			 link_url = new URI(scheme, link_url.getUserInfo(), host, port1, link_url.getPath(), link_url.getQuery(), link_url.getFragment());
	        } catch (URISyntaxException e1) {
	     
				}
		
	        if(link_url!=null)
	         url_string = link_url.toString(); 
	        	
	      return url_string;
	}
	


	
	
	public static String CreateLinkUrl(String link_url_str)
	{
		int _syntax_error_count = 0;
		URI link_url = null;
		try {
			link_url = new URI(link_url_str);
		} catch (URISyntaxException e) {
			try {
				String link_url_escape = URLEncoder.encode(link_url_str, "utf-8");
				link_url = new URI(link_url_escape);
			} catch (UnsupportedEncodingException e1) {
				e1.printStackTrace();
			} catch (URISyntaxException e1) {
	            _syntax_error_count += 1;
	            if (_syntax_error_count < 100) {
	                System.err.printf("ResolveRelativeURL.resolve_relative_url: URISyntaxException: '%s' + '%s' -> %s\n", "", link_url_str, e);
	            } else if (_syntax_error_count % 100 == 0)
	                System.err.println("ResolveRelativeURL.resolve_relative_url URISyntaxException: " + _syntax_error_count);
	            return null;
			}
		}
		if (link_url != null)
			return link_url.toString();
		else
			return "";
	}
	
	
	public static String temp(String with,String without,String clu,Integer flg) {
		Integer present=with.indexOf(clu);
		Integer Single=clu.indexOf(" ");
		if(present!=-1 && Single==-1 )
		switch (flg) {
		case 1:
			System.out.println(with.substring(0, present));
			return with.substring(0, present); 
		case 2:
			System.out.println(with.substring(present+clu.length()));
			return with.substring(present+1); 
		}
		
		Integer left1=clu.indexOf(" ");
		Integer right1=clu.lastIndexOf(" ");
		
		String FirstToken=clu.substring(0, left1);
		String LastToken=clu.substring(right1+1);
		present=with.indexOf(FirstToken);
		if(with.indexOf(FirstToken)==with.lastIndexOf(FirstToken))
			switch (flg) {
			case 1:
				System.out.println(with.substring(0, with.indexOf(FirstToken)));
				return with.substring(0, with.indexOf(FirstToken)); 
			case 2:
				System.out.println(with.substring(with.indexOf(LastToken,present)+LastToken.length()));
				return with.substring(with.indexOf(LastToken,present)+LastToken.length());
			}	
		
	
		String left2= without.substring(0,without.indexOf(clu)+FirstToken.length());
		//System.out.println(FirstToken);
		//System.out.println(LastToken);
		String S[]=left2.split(" ");
				//String Clue
		//  System.out.println(S.length);
		int i1=0;
		Integer temp=0;
		for(int i=0;i<S.length;i++)
		{	
			
			temp=with.indexOf(S[i], i1);
		//	System.out.println(S[i]);
		//	System.out.println(temp);
			i1=S[i].length()+temp;
			
		//	System.out.println(with.substring(0, temp));
		}	
		
		
		switch (flg) {
		case 1:
			System.out.println(with.substring(0, temp));
			return with.substring(0, temp); 
		case 2:
			temp=with.indexOf(LastToken, temp)+LastToken.length();
			System.out.println(with.substring(temp));
			return with.substring(temp);
		}	
	
		
		
		return null;
		
		
	}
	
	/**
	 * Example user-defined unary predicate.
	 * @param str a string to test
	 * @return true if the string does not contain the letter 'E'
	 */
	public Boolean containsNoE(String str) {
		
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if ('e' == c || 'E' == c) {
				return false;
			}
		}
		
		return true;
	}
		
}
