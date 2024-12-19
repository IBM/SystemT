---
providerName: IBM
layout: page
title: Guidelines for writing AQL
nav_order: 3
has_children: true
parent: Guides
grand_parent: In depth rule-based modeling techniques
description: AQL guidelines
---

# Guidelines for writing AQL


* Table of Contents
{:toc}

# Best practices in organizing AQL code

You can use these guidelines to develop efficient extractors by using good practices of AQL development.

An extractor is a set of compiled AQL modules that work together to accomplish an extraction task.

Prior to extractor development, it is important to conduct an analysis of your extraction task to establish your extraction requirements. 
For example, if you want to analyze the IBM® quarterly earnings reports, you might develop extractors to help answer the question, 
“How do quarterly revenues for different IBM divisions change over the years?” 
To answer the question, you first need to find the mentions of quarterly revenue for each IBM division in each quarterly earnings report. 
The primary extraction task in this case is to extract quarterly revenue for each IBM division. 
Extractor development is generally an iterative process and you can follow some basic steps:

1. Identify the basic building blocks of your extractor. Start with the most basic clues that appear in the text that you want to analyze with the extractor. In this step, you create the AQL statements for a set of basic entity extraction views.
2. Combine the basic entities by using simple patterns to form more concise views. As a result, you have a set of statements that create more complex entities that are candidates for finalization.
3. Review all of the candidates, and create or edit your AQL statements to filter the valid from the invalid results. You can apply AQL filtering and consolidation techniques to achieve this step.
4. In some cases, you might need to use advanced AQL syntax to further refine your results.

The ability to modularize source AQL code is a great benefit. You can ensure that multiple concepts are well-separated in terms of rule sets and patterns that are used. 
There are four basic questions to begin to guide you in the design process.

## Question 1: How complex is your extractor?

Simple extractors are a single AQL module. The more complex the extractor, the greater the need to modularize it.

## Question 2: Does your extractor have objects that are used in other extractors?

An object refers to individual views, tables, dictionaries, functions, or any combinations of them. Identify AQL views, tables, dictionaries, and functions that are common across multiple versions of your extractor to create modules that can be reused by other specialized modules. Use the `output view … as …` statement to ensure that different specialized modules output a consistent set of names. Examples of such objects are apparent in the following two scenarios:

  - You have one UDF JAR file that you use in each of your AQL projects.
  - You have an extractor that you use in other extractors. For example, you use an organization extractor in another extractor that identifies financial events, or in an extractor that identifies relationships between persons and organizations.

## Question 3: Would you like to expose objects inside your extractor towards extractor customization?

One useful way to customize the behavior of an extractor is to abstract information that is used by the extractor to arrive at a decision \(such as dictionaries and tables\). Then, the consumer of the compiled extractor can complete these values when the extractor is applied to a specific domain. In this way, the same extractor can be applied to different domains, each time with a different set of entries for the customizable dictionaries and tables. For example, assume that you have a person extractor that is used to highlight mentions in an email inbox of a customer. You would like to share this extractor with multiple customers, and allow each one to customize the extractor with names from the employee directory of their organization, without providing the source AQL code and recompiling the extractor. You can expose these customization points by using the AQL statements that create an external dictionary and create an external table.

## Question 4: Are there domain-specific components to your extractor? Are there any common components used by these domain-specific components?

Create specialized modules that provide different implementations for the same semantic concept, where each implementation is customized for a particular application, domain, or language. 
It can be difficult to create generic AQL extractors that perform well on every possible domain or language. 
In general, a generic extractor requires customization to improve the accuracy and coverage on a specific domain. 
Such customization generally falls into three categories:

### Application customization

You have a generic extractor that is used in multiple applications. 
Each application has specific requirements on the output schema, or the types of mentions to be extracted. 
For example, you use a person extractor in two different applications. 
In one application, the extractor does not output mentions that consist of a single token \(for example, mentions of the given name of a person, without a mention of their surname\). 
In the second application, such single-token mentions are acceptable. 
In addition, the output schema of the extractor differs across the two applications. 
In one application, a single attribute for the full name is sufficient. 
The second application requires extra attributes in addition to the full name: the given name, the surname, and the job position if present in the text.

### Data source customization

You have an extractor that is applied to several different types of data sources. 
Each data source has specifics that require specialized rules. 
For example, you have an extractor for location mentions that is applied on two types of text: formal news reports and emails. 
In formal news report, you know that there is a mention of the location at the beginning of the first line. 
You want to write a special rule to capture that location, but you do not want to apply that rule when the extractor is used on emails.

### Language customization

Formal language and grammar structure varies across languages. If your extractor applies to multiple languages, you might want to implement rules that are applicable to a single language. 
For example, you have a sentiment extractor that is applied to documents in English and French. You might have a few specialized rules that apply only to English text. 
Similarly, you might have a few specialized rules that apply only to French text. 
In cases of language customization, use the `set default dictionary language` statement to set the default dictionary languages for each module according to the language it is intended to work with.

# Examples of frequently used AQL statements

Included in the examples of how to use the AQL statements to accomplish various extraction tasks are optimization guidelines. 
These guidelines describe good choices to make when you write your AQL code so that the Optimizer has the most latitude in choosing efficient execution plans.

- **[Using basic feature AQL statements](#using-basic-feature-aql-statements)**  
You can use the basic feature AQL statements to develop the core building blocks of your extractor. Then, combine and filter your AQL statements to refine your results.
- **[Using candidate generation AQL statements](#using-candidate-generation-aql-statements)**  
You can refine the basic extractor that you started with by combining entity extraction views. The candidate generation AQL statements combine basic features by using simple patterns to form larger groups of features, which further refine the concepts.
- **[Using filter and consolidate AQL statements](#using-filter-and-consolidate-aql-statements)**  
You can use filter and consolidate statements to refine results, to remove invalid annotations, and to resolve overlap between annotations.
- **[Creating complex AQL statements](#creating-complex-aql-statements)**  
 In some cases, you might need to use advanced AQL syntax to further refine your results. To develop more complex extractors, you can build on the basic design patterns in AQL with these advanced patterns.
- **[Enhancing content of AQL views](#enhancing-content-of-aql-views)**  
To output something other than text matches from your existing results, you can use AQL statements to transform data, specify fixed output, and add additional metadata.

## Using basic feature AQL statements

You can use the basic feature AQL statements to develop the core building blocks of your extractor. Then, combine and filter your AQL statements to refine your results.

An AQL module generally consists of multiple `create view` statements and one or more output view statements. Each view can also include the use of dictionaries, tables, and functions, as well as references to other views.

The following examples explain how to extract basic features, such as financial indicators, from unstructured text. For example, you might want to begin by extracting numbers, units, and metrics.

### When you want to match text that is based on a character-based pattern

Generally, a character-based pattern is recognizable in the text. You might see that there is always a sequence of letters followed by numbers, or numbers with known separators in between. For example, if you were to see “\(123\) 456-7890” or “123-45-6789” in text, you can easily specify a pattern that matches either sequence.

When you analyze text, you can use a regular expression when you want to match text that is based on a character pattern. The following is an example of a `create view` statement that uses a regular expression to extract decimal numbers:

```bash
-- Identify mentions of numbers with optional decimals
-- Example: 7, 49, 11.2
create view Number as
extract regex /\d+(\.\d+)?/ 
on R.text as number
from Document R; 
```

As you analyze text with regular expressions, follow the optimization guidelines for regular expressions whenever possible.

### When you want to find matches for a fixed set of words or phrases

Use a dictionary when you want to find matches for a fixed set of words or phrases. 
You can list the terms to be included in the dictionary either inline \(in an AQL file\) or externally \(in a .dict file\). 
The following is an example of how to create an inline dictionary:

```bash
create dictionary MyDict as ('Finance');
```

This statement creates a dictionary with just one entry, `‘Finance’`. Use an external file when you have many dictionary entries. 
The external file makes it easier to add and change entries without having to edit the AQL program. 
By default, dictionaries are tokenized and internalized at compile time, but you can use the `create external dictionary` statement to provide dictionaries at run time. 
The following sample creates a dictionary from a file on your local system:

```bash
-- use a dictionary to contain a list of terms for matching purposes
create dictionary MyDict as ('Finance');

-- Define a dictionary of financial amount units
-- Example: million, billion
create dictionary UnitDict
from file 'dictionaries/unit.dict'
with language as 'en';
```

The file dictionaries/unit.dict contains:

```bash
million 
billion
```

For more information about optimization by using dictionaries, follow the guidelines for regular expressions.

## Using candidate generation AQL statements

You can refine the basic extractor that you started with by combining entity extraction views. 
The candidate generation AQL statements combine basic features by using simple patterns to form larger groups of features, which further refine the concepts.

The following examples explain how to extract candidate features from unstructured text. 
For example, you might want to combine the number and the unit by using the previously built basic feature AQL statements together to produce an amount. Or you might combine a metric with an amount to produce a revenue indicator.

The following common tasks use these types of AQL statements:

### When you want to combine information from multiple views

Use the `select` clause inside of a `create view` statement to combine, or join, information from multiple views. 
In this example, the goal is to find a number, followed within 0 to 10 tokens, by a unit. 
Notice how the built-in scalar function `FollowsTok` is used as join predicate between the views `Number` and `Unit`.
Alternatively, you can use the `extract pattern` statement.

```bash
-- extract metrics like 46.1, $8.7 
create view Number as
extract regex /$?\p{Nd}+(\.\p{Nd}+)?/
on D.text as match
from Document D;

create dictionary UnitDict as ('percent', 'billion', 'million', 'trillion');

-- extract amount cues like 'percent', 'billion'
create view Unit as
extract dictionary 'UnitDict'
on D.text as match
from Document D;

-- Identify candidate indicators as a mention of metric followed within 
-- 0 to 10 tokens of a mention of amount
-- Example: Gross profit margin of 46.1 percent, cash flow of $8.7 billion
create view IndicatorCandidate as
select N.match as number, U.match as unit, 
     CombineSpans(N.match, U.match) as match 
from Number N, Unit U
where FollowsTok(N.match, U.match, 0, 10);

-- Alternatively, you can achieve the same with a much simpler statement
create view IndicatorCandidate as
extract N.match as number, U.match as unit, 
        pattern <N.match> <Token>{0,10} <U.match> as match 
from Number N, Unit U;
```

As you combine information from multiple views, follow the optimization guidelines and [Avoid Cartesian products](ana_txtan_extract-perform.html#avoid-cartesian-products), 
[Use the and keyword instead of the And\(\) built-in predicate](ana_txtan_extract-perform.html#use-the-and-keyword-instead-of-the-and-built-in-predicate), 
and [Be careful when using Or\(\) as a join predicate](ana_txtan_extract-perform.html#be-careful-when-using-or-as-a-join-predicate).

### When you want to combine results from different views into a single view

In the following example, a union allclause is used inside of a `create view` statement to capture a union of entities that were specified with different AQL statements:

```bash
create dictionary QuantityAbsoluteDict as ('billion', 'trillion', 'million');

create dictionary QuantityPercentDict as ('percent', 'percentile', 'percentage'); 

create view QuantityAbsolute as
select I.* from IndicatorCandidate I
where MatchesDict('QuantityAbsoluteDict', I.amount);

create view QuantityPercent as
select I.* from IndicatorCandidate I
where MatchesDict('QuantityPercentDict', I.amount);

-- Union all absolute and percentage amount candidates
-- Example: $7 billion, $11.52, 49 percent, 46.1 percent
create view AmountCandidate as
(select R.* from QuantityAbsolute R)
union all
(select R.* from QuantityPercent R); 
```

### When you want to extract chunks of data

Use the blocks extraction specification to identify blocks of contiguous spans across input text. 
This example shows how to identify blocks of exactly two capitalized words within zero to five tokens of each other:

```bash
-- A single capitalized word
create view CapitalizedWords as
 extract
    regex /[A-Z][a-z]+/
        on 1 token in D.text
        as word
from Document D;

-- extract blocks of exactly two capitalized words within 0-5 tokens of each other
create view TwoCapitalizedWords as
extract blocks
   with count 2
   and separation between 0 and 5 tokens
   on CW.word as capswords
from CapitalizedWords CW;
```

### When you want to identify patterns of simple text or fields from other views

In the following example, an amount entity is extracted with the help of a simple pattern involving other entities:

```bash
-- extract metrics like 46.1, 8.7 
create view Number as
extract regex /\p{Nd}+(\.\p{Nd}+)?/
on D.text as match
from Document D;

create dictionary UnitDict as ('percent', 'billion', 'million', 'trillion');

-- extract amount cues like 'percent', 'billion'
create view Unit as
extract dictionary 'UnitDict'
on D.text as match
from Document D;

-- Identify mentions of absolute amounts as a sequence of '$' character,
-- followed by a Number mention, optionally followed by a Unit mention
-- Example: $7 billion, $11.52
create view AmountAbsolute as
extract pattern /\$/ <N.match> <U.match>? 
return group 0 as match
from  Number N, Unit U
consolidate on match;
```

As you identify patterns of text or fields from other views, 
follow the optimization guideline and [Define common subpatterns explicitly](ana_txtan_extract-perform.html#define-common-subpatterns-explicitly).

## Using filter and consolidate AQL statements

You can use filter and consolidate statements to refine results, to remove invalid annotations, and to resolve overlap between annotations.

### When you want to remove data that overlaps

In the following example, the `consolidate on` clause is used to remove overlapping spans held by the column on which the consolidate clause is applied.

```bash
-- extract metrics like 46.1, $8.7 
create view Number as
extract regex /$?\p{Nd}+(\.\p{Nd}+)?/
on D.text as match
from Document D;

create dictionary UnitDict as ('percent', 'billion', 'million', 'trillion');

-- extract amount cues like 'percent', 'billion'
create view Unit as
extract dictionary 'UnitDict'
on D.text as match
from Document D;

-- Identify candidate indicators as a mention of metric followed within 
-- 0 to 10 tokens of a mention of amount
-- consolidate overlapping mentions
-- Example: Gross profit margin of 46.1 percent, cash flow of $8.7 billion

create view IndicatorCandidate as
select N.match as amount, U.match as metric, 
     CombineSpans(N.match, U.match) as match 
from Number N, Unit U
where FollowsTok(N.match, U.match, 0, 10);

create view IndicatorConsolidated as
select R.* from IndicatorCandidate R
consolidate on R.match using 'NotContainedWithin';
```

When you want to remove data that overlaps, follow the optimization guideline and [Use the consolidate clause wisely](ana_txtan_extract-perform.html#use-the-consolidate-clause-wisely).

### When you want to filter information based on a dictionary term or similar terms

You can use a predicate-based filter to remove similar data. The following example shows a simple filter condition that is based on information from the dictionary that you created:

```bash
-- Negative clues that signal a relative amount
-- Example: increased, decreased, down, up
create dictionary AmountNegativeClueDict
with language as 'en'
as ('points', 'debit', 'credit');


-- Narrow down the results to the candidate indicators which are absolute: 
-- the span between the metric and amount does not contain 
-- a "relative amount" term
create view IndicatorAbsoluteCandidate as
select I.metric as metric, I.amount as amount, I.match as match 
from IndicatorConsolidated I
where Not(MatchesDict('AmountNegativeClueDict', SpanBetween(I.metric, I.amount)));
```

### When you want to use a view to filter data from a larger subset of data

You can use a minus clause to express complex filter conditions for which predicate-based filters are not sufficient.

```bash
create dictionary MetricDict as ('revenue', 'Revenue');

create view Metric as
extract dictionary 'MetricDict'
on D.text as match
from Document D;

-- Identify one type of invalid Indicator candidates: mentions that contain  
-- another metric in between the Metric and Amount mentions 
-- Example:
-- [EPS growth]; Revenue of [$99.9 billion]
-- [revenue] up 19 percent; Free cash flow of [$8.7 billion]

create view IndicatorInvalid as
select R.* 
from IndicatorConsolidated R, Metric M
where Contains(SpanBetween(R.metric, R.amount), M.match);

-- Filter out invalid Indicator mentions from the set of all 
-- Indicator candidates

create view IndicatorAll as
(select R.* from IndicatorConsolidated R)
minus
(select R.* from IndicatorInvalid R);
```

## Creating complex AQL statements

In some cases, you might need to use advanced AQL syntax to further refine your results. 
To develop more complex extractors, you can build on the basic design patterns in AQL with these advanced patterns.

The following are examples of advanced AQL design patterns:

### When you want to remove HTML or XML tags and extract information based on the tags

The `detag` statement is used to remove the tags from input documents that are in HTML or XML format.

This example shows how to remember the locations and content of all `<title>` and `<a>` tags in the original input document. 
It also automatically creates a view that is called `DetaggedDoc` with a single column called `text`, 
and additional views that are called `Title` and `Anchor`, each with a single column called `match`.

```bash
detag Document.text as DetaggedDoc
detect content_type always
annotate element 'title' as Title,
         element 'a' as Anchor with attribute 'href' as href;
```

This example shows how to find specific words that are interesting from a website:

```bash
create dictionary InterestingSitesDict as
('ibm.com', 'slashdot.org' );
```

Create a view that contains all of the anchor tags whose targets contain a match of the "interesting sites" dictionary.

```bash
create view InterestingLinks as
select A.match as anchortext, A.href as href
from Anchor A
where ContainsDict('InterestingSitesDict', A.href);
```

Find all dictionary matches in the title text of links to "interesting" websites.

```bash
create view InterestingSites as
extract 
    dictionary 'InterestingSitesDict'
    on T.match as word
from Title T;
```

Finally, map spans in the `InterestingWords` document by using the Remap function.

```bash
create view InterestingWords as
select 
    I.href as href,
    IS.word as word
from InterestingLinks I, InterestingSites IS;

create view InterestingWordsHTML as
select I.href as href, Remap(I.word) as word
from InterestingWords I;
```

### When you need to manipulate a span of text or otherwise extend the language to obtain a scalar value

User-defined functions \(UDFs\) extend the capabilities of AQL. For example, you can develop a scalar function to check for 
normalization or to check whether a number is a valid credit card number. 
The input and output parameters must be one of the scalar types \(Integer, String, Text, Span, or ScalarList\). 
To make a scalar UDF available to your AQL code, you must first write the program that implements the function, 
and then declare it for usage so that the compiler recognizes the invocation from AQL and knows which program to call. 
For more information about how to implement and declare scalar UDFs, see User-defined functions in the *AQL reference*.

You can implement the UDF in a Java™ class as a public method. 
If the UDF involves a Span or Text type, the Java class must import com.ibm.avatar.datamodel.Span or com.ibm.avatar.datamodel.Text from the SystemT low-level Java API to compile. 

When the UDF is implemented, package the UDF as a \*.JAR file and place in the data path of the AQL project. 
The content of UDF JAR file is serialized inside the compiled plan file.

```bash
-- extract metrics like 46.1, $8.7 
create view Number as
extract regex /$?\p{Nd}+(\.\p{Nd}+)?/
on D.text as match
from Document D;

create dictionary UnitDict as ('percent', 'billion', 'million', 'trillion');

-- extract amount cues like 'percent', 'billion'
create view Unit as
extract dictionary 'UnitDict'
on D.text as match
from Document D;

-- Identify candidate indicators as a mention of metric followed within 
-- 0 to 10 tokens of a mention of amount
-- consolidate overlapping mentions
-- Example: Gross profit margin of 46.1 percent, cash flow of $8.7 billion

create view IndicatorCandidate as
select N.match as amount, U.match as metric, 
     CombineSpans(N.match, U.match) as match 
from Number N, Unit U
where FollowsTok(N.match, U.match, 0, 10);

create view IndicatorConsolidated as
select R.* from IndicatorCandidate R
consolidate on R.match using 'NotContainedWithin';
```

Implement the UDF as a public method in a Java class com.ibm.test.udfs.MiscScalarFunc.java:

```bash
-- Define the UDF
create function udfToUpperCase(p1 Text)
return String like p1
external_name 'udfjars/udfs.jar:com.ibm.test.udfs.MiscScalarFunc!toUpperCase'
language java 
deterministic
return null on null input;

-- Use the UDF function to normalize the metric value of Indicator tuples
create view IndicatorUDF as
select R.*, udfToUpperCase(GetText(R.metric)) as metric_normalized
from IndicatorConsolidated R;
```

When you need to obtain a scalar value, follow the optimization guideline and [Avoid UDFs as join predicates](ana_txtan_extract-perform.html#avoid-udfs-as-join-predicates).

### When you want to extract occurrences of different parts of speech

You can identify different parts of speech across the input text. The following is an example of code that captures all noun mentions from input text:

```bash
create view Noun as
extract part_of_speech 'NN, NNS, NNP, NNPS' 
with language 'en'
    on R.text as match
from Document R;
```

## Enhancing content of AQL views

To output something other than text matches from your existing results, you can use AQL statements to transform data, specify fixed output, and add additional metadata.

You can use AQL to enhance the content of views, for instance specifying what can be returned as the value of a column in a view, be it a fixed string, the result of a function call, or the result of a table lookup.

As you refine your outputs, consider the optimization guidelines. For more information about optimizing your extractor, 
see [Follow the optimization guidelines for writing AQL](ana_txtan_extract-perform-rules.md).

### When you want to return a fixed string for documents that contain a match for a view

This example illustrates the assignment of a fixed text value to a column to indicate positive polarity across an input document based on the presence of positive words inside the document. The positive words are defined by the dictionary `PositiveWordDict`.

```bash
create dictionary PositiveWordsDict as
('happy', 'like', 'amazing', 'beautiful', 'love');

create view PositivePolarity as
select
    D.text as text,
    'positive' as polarity
from Document D
where ContainsDict ('PositiveWordsDict', D.text);
```

### When you want to transform the value of an output column by using a function

This example shows how to use a built-in function to transform the contents of an existing view that is called `Division` to lowercase characters.

```bash
create function toLowerCase(p1 Text)
return String like p1
external_name 'udfjars/udfs.jar:com.ibm.test.udfs.MiscScalarFunc!toLowerCase'
language java 
deterministic
return null on null input;

create view DocumentLowerCase as 
select 
    toLowerCase(D.text) as lowerCase 
from Document D;
```

### When you want to return a fixed string for documents where a column of a view is null

The following example illustrates case-based value assignment in AQL. 
If an input document does not contain a person name as defined by the dictionary `PersonNamesDict`, 
then a fixed String value of NO-MATCH is assigned for the column `personName`.

```bash
create dictionary PersonNamesDict as
('John', 'George', 'Kevin', 'William', 'Stephen', 'Peter', 'Paul');

create view DocumentMatchingStatus as
select
    case when ContainsDict('PersonNamesDict', D.text)   
         then true
         else false
    as containsPersonName
from Document D;
```

### When you want to create extra metadata on spans extracted from the document

Use the `create table` statement to perform associative mapping on values of columns or on input text. 
Such a mapping can be used to enhance the content of an existing view towards adding useful metadata.

```bash
-- table mapping company names to their headquarters
create table NameToLocation 
 (name Text, location Text) as values 
('IBM',      'Endicott'), 
('Microsoft','Seattle'), 
('Initech',  'Dallas');
```

First, create a dictionary of company names from the table.

```bash
-- dictionary of company names from the table
create dictionary CompanyNamesDict
from table NameToLocation
with entries from name;
```

Then, create a view to find all matches of the company names dictionary.

```bash
-- find matches of [company-names] above dictionary terms in input text
create view Company as
extract dictionary 'CompanyNamesDict' on D.text as company
from Document D;
```

To finish, create a view that uses the table to augment the `Company` view with location information.

```bash
-- enhance company names information with their headquarters information [as metadata]
create view CompanyLoc as
select N2C.location as loc, C.company as company
from Company C, NameToLocation N2C
where Equals(GetText(C.company), GetText(N2C.name));
```

# AQL naming conventions

Naming conventions can improve the readability of AQL source code.

AQL uses identifiers to define the names of AQL objects, including names of modules, views, tables, dictionaries, functions, attributes, and function parameters.

In general, these identifier naming conventions apply:

- For a module name, you must use a simple identifier. All other AQL objects can be a simple or double-quoted identifier.
- Identifier names that are written in English can enhance the readability of the AQL source code.
- If the identifier consists of multiple words, use the underscore \(\_\) character or medial capitalization \(camel case\) to delimit different words. For example, three\_word\_identifier or threeWordIdentifier, or ThreeWordIdentifier.

The following are the naming conventions for the objects that are used in AQL syntax:

- **Module name**

    Begin module names with a lowercase letter. For example, myModule or "my\_module". In this code sample, the module name is `sample`.

    ```bash
    
    module sample;
    
    create dictionary GetTheDict as ('The', 'the');
    
    create view TheMentions as
      extract dictionary 'GetTheDict' on D.text as theMention
      from Document D;
    ```

- **View name**

    Begin view names with an uppercase letter. For example, MyView or "My\_view". An example from the previous code sample, is `TheMentions`.

- **Table name**

    Begin inline or external table names with an uppercase letter. For example, TableA or "Table\_1". In this example, the table name is `NameToLocation`.

    ```bash
    create table NameToLocation 
      (name Text, location Text) as
     values 
        ('IBM', 'USA'), 
        ('Enron', 'UK'), 
        ('Initech', 'Dallas'),
        ('Acme Fake Company Names', 'Somewhere');
    ```

- **Dictionary name**

    Begin inline or external dictionary names with an uppercase letter. For example, MyDictionary or "My\_dictionary". In this example, the dictionary name is `GreetingDict`.

    ```bash
    create dictionary GreetingDict as
    (
      'regards', 'regds', 'hello', 'hi', 'thanks', 'best', 'subj', 'to', 'from'
    );
    ```

- **User-defined function name**

    Begin user-defined function names with a lowercase letter. This convention distinguishes UDFs from built-in functions that always start with an uppercase letter. For example, myFunction or "my\_function". In this example, the function name is `udfCompareNames`.

    ```bash
    create function udfCompareNames(nameList ScalarList, myName String)
        return ScalarList like nameList
        external_name 'udfjars/udfs.jar:com.ibm.test.udfs.MiscScalarFunc!compareNames'
        language java 
        deterministic;
    ```

- **Attribute name**

    Begin attribute names with a lowercase letter. An attribute can be a column in a view, a column in a table, or a function parameter. For example, my\_attribute or "myAttribute".

    Examples from the previous code samples are `nameList` and `myName` \(function parameters\), `name`, and `location` \(table columns\), and `theMention` \(view column\).
