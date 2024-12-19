---
providerName: IBM
layout: home
title: Improving AQL performance
nav_order: 6
parent: Guides
grand_parent: In depth rule-based modeling techniques
has_children: true
description: Improve extractor performance
---

# Improving the performance of an extractor


* Table of Contents
{:toc}

# Overview of AQL runtime performance best practices

Since you can usually rely on the SystemT Optimizer to choose an efficient execution plan, 
writing high-performance extraction rules with SystemT is relatively simple. 
But there are a few best practices that are important to follow to maximize the effectiveness of your extractor.

- **[Step 1: Know your performance requirements](#step-1-know-your-performance-requirements)**  
To begin, you must understand what level of throughput is acceptable for your application and what kinds of documents the extractor will examine.
- **[Step 2: Follow the optimization guidelines for writing AQL](#step-2-follow-the-optimization-guidelines-for-writing-aql)**  
These guidelines describe the ways that you can write your AQL code so that the Optimizer has the most latitude in choosing efficient execution plans.
- **[Step 3: Focus on writing AQL instead of tuning performance](#step-3-focus-on-writing-aql-instead-of-tuning-performance)**  
You can usually rely on the Optimizer to choose an efficient plan, so do not tune performance while you are writing AQL.
- **[Step 4: Use the AQL Profiler to find problems](#step-4-use-the-aql-profiler-to-find-problems)**  
Sometimes there is not an efficient plan for the particular semantics of the AQL expressions. The AQL Profiler that is included in the public API is designed to help you quickly zero in on these problem cases.
- **[Step 5: Tune the most expensive view first](#step-5-tune-the-most-expensive-view-first)**  
Focus your performance tuning on views that are heavily represented in the AQL Profiler output.
- **[Examples: Solving common performance problems](#extract-perform-ex)**  
The main goal of tuning an AQL view is to identify the reason that the view is running slowly, and then fix that problem.

# Step 1: Know your performance requirements

To begin, you must understand what level of throughput is acceptable for your application and what kinds of documents the extractor will examine.

These requirements determine how precisely you need to follow the other best practices.

- **Different text applications have vastly different performance requirements.**

    For example, if you are conducting a sentiment analysis on a small, fixed set of call-center records, you will probably require a throughput of only kilobytes per second. However, if you are analyzing over 10% of a Twitter feed, a throughput of megabytes per second might be needed.

- **Performance tuning takes time and makes the code more difficult to maintain.**

    Performance tuning is expensive and takes much time and effort. Changing the structure of the rules for performance adds extra effort later because it makes the AQL rules harder to maintain.

- **If possible, try to design the solution so that high throughput is not needed.**

    The best way to tune for performance is to make tuning unnecessary. In many cases, you can achieve this goal just by looking closely at the true requirements of the application. Evaluate the goal of your extractor, and try to determine the most efficient method by answering a few important questions:

  - At what rate is the new data coming in?
  - Is it possible to increase the level of parallelism \(for example, by increasing the number of mappers per node, or the size of the cluster\) instead of tuning the single-threaded performance?
  - Can the results of an extraction be saved for later?

# Step 2: Follow the optimization guidelines for writing AQL

These guidelines describe the ways that you can write your AQL code so that the Optimizer has the most latitude in choosing efficient execution plans.

These optimization guidelines can help you to reduce AQL code errors, and improve the performance of your extractor.

## Follow the guidelines for regular expressions

Write SimpleRegex compatible expressions and use token constraints in your regular expression extraction specifications whenever possible. Write regular expressions that are simple and easy to understand, and do not use regular expressions as a substitute for dictionaries.

### Write SimpleRegex compatible expressions

There are two regular expression engines that are used by the SystemT runtime component, JavaRegex and SimpleRegex. The JavaRegex engine is slower but allows for more advanced processing. The SimpleRegex engine is a fast regular expression evaluation engine, but it supports a slightly limited class of expressions. Since the SimpleRegex engine is more scalable than the Java™ engine, a good practice is to try to write SimpleRegex compatible expressions whenever possible. 
The SystemT engine automatically chooses the suitable Regex engine for running a given regular expression.

Most expressions are SimpleRegex compatible. Supported SimpleRegex constructs are:

- Alternation
- Sequence
- Parentheses
- Basic quantifiers such as +, \*, ?, \{m,n\}
- Unicode character classes
- Standard built-ins such as \\d, \\s

A regular expression is not SimpleRegex compatible if at least one of the following is true:

- Uses one of these flags: CANON\_EQ, COMMENTS, LITERAL, UNICODE, UNIX\_LINES
- Contains look-ahead or look-behind constructs such as \\A, \\Z, \\b, ^, $, \(?=X\), \(?!X\), \(?<=X\), or \(?<!X\)
- Contains capturing groups \(such as, `return group 1` as …\)

The AQL compiler flags a warning for all of the regular expressions that are not SimpleRegex compatible. If you encounter such a warning, try to determine if you can simplify your regular expression to make it SimpleRegex compatible while you preserve the semantics of your extractor.

As an example, consider this statement for extracting numbers with optional decimals. The statement uses the `\b` regex character class to indicate that the regular expression matching must be done on tokens considering word boundaries.

```bash
create view NumberUsingJavaRegex as
extract regex /\b\d+(\.\d+)?\b/
on D.text as match
from Document D;
```

This statement matches stand-alone numbers such as 10.99 in the phrase “I purchased something for 10.99 USD”, but it does not match numbers that are part of a larger word such as ‘20’ in the phrase “I just bought something for USD20”.

Alternatively, you can rewrite the regular expression to use SimpleRegex:

```bash
create view NumberUsingSimpleRegex as
extract regex /\d+(\.\d+)?/
on D.text as match
from Document D;
```

In this example, the word boundary `\b` characters were removed. As a result, the regular expression is compatible with the SimpleRegex engine, but the semantics are slightly different from the original. The new expression `\d+(\.\d+)?` matches both the numbers '10.99’ and ‘20’ from the phrases “I purchased something for 10.99 USD”, and "I just bought something for USD20”. However, the transformation might be acceptable if the number ‘20’ is not needed in the final result \(perhaps due to other constructs used downstream in parts of your AQL code\). If it is important to preserve the semantics of the original regular expression while using a regular expression that conforms to SimpleRegex, consider specifying token constraints in the `extract regex` statement.

### Use token constraints in your regular expression extraction specifications

The Optimizer performs Shared Regular Expression Matching \(SRM\), an optimization in which regular expressions that are both on token boundaries and are SimpleRegex compatible are grouped together and evaluated in one pass over the input text. Generally, this means that all regular expressions in the group are evaluated in approximately the same amount of time that it takes to individually evaluate the slowest expression in the group.

When possible, write your regular expression to take advantage of the SRM optimization. The regular expression should be SimpleRegex compatible and have token constraints. Consider the same AQL example that uses a regular expression with word boundary character class `\b`, and without token specifications:

```bash
create view Number as 
extract regex /\b\d+(\.\d+)?\b/
on D.text as match
from Document D;
```

Here is how you would rewrite it to both make it SimpleRegex compatible \(by removing the `\b`\) and amenable to SRM \(by introducing the token constraints\):

```bash
create view NumberSRM as
extract regex /\d+(\.\d+)?/ on between 1 and 3 tokens
in D.text as match
from Document D;
```

### Use dictionaries instead of regular expressions

An AQL regular expression that matches a list of terms is usually more expensive to evaluate than the equivalent `extract dictionary` statement. 
The dictionary evaluator in SystemT is specialized for the particular case of matching large dictionaries of strings on token boundaries, 
and the SystemT Optimizer generates plans that evaluate multiple `extract dictionary` statements in a single pass. Therefore, when you are looking for mentions of a set of words or phrases, avoid writing your extractor as a regular expression consisting of an alternation of terms and use a dictionary instead.

For example, if you are looking for mentions of currencies, do not write your statement using a regular expression:

```bash
-- Not recommended; long alternation of terms should be a dictionary
create view Currency as
extract regex /dollar|sterling pound|cent|euro|rupee|yen|peso|rouble/ 
        with flags 'CASE_INSENSITIVE'
    on D.text as match
from Document D;
```

In addition to the performance-related reasons for using a dictionary, this example also illustrates some additional common mistakes associated with misusing regular expressions:

- The `extract regex` statement will output matches that start or end in the middle of a word. For example, the statement will match the first four letters of the word “**cent**er”.
- The expression will match the phrase `sterling pound` only if there is exactly one space character between the two words. If the words `sterling` and `pound` appear in a document separated by a carriage return or by two spaces, the regular expression will not produce a match.

Instead, this rewrite uses a dictionary of terms. Using a dictionary increases performance, makes the AQL code easier to maintain and understand, and the code will be less prone to bugs:

```bash
-- Use a dictionary to match against same list of terms
create dictionary CurrencyDict as('dollar', 'sterling pound', 'cent', 'euro', 'rupee', 'yen', 'peso', 'rouble'); 

create view CurrencyAlternative as
extract dictionary 'CurrencyDict' 
    on D.text as match
from Document D; 
```

### Write short and simple regular expressions

Avoid writing long and complex regular expressions. In general, long and complex regular expressions make AQL code harder to understand and maintain. This type of regular expressions can be expensive to evaluate. Consider breaking a complex regular expression into smaller expressions, and combining the results of these expressions by using other AQL constructs.

In the following example, a regular expression is used to identify one to three capitalized words. The words are separated by white space.

```bash
-- Not recommended; long, complex regular expressions are difficult to maintain
create view OneToThreeCapitalizedWords as
extract regex /[A-Z][a-z]+(\s+[A-Z][a-z]+){0,2}/ 
    on D.text as match
from Document D;
```

The regular expression becomes harder to maintain as the subexpression for the capitalized word becomes more complex; for example, if it was to support Unicode characters. For ease of maintenance and understanding, you can break down the expression into smaller components. For example, you can use one regular expression with token constraints to find a single capitalized word, and the `extract blocks` statement to find blocks of one to three capitalized words:

```bash
-- Same extraction in an efficient, simple manner

-- A single capitalized word
create view CapitalizedWords as
 extract
    regex /[A-Z][a-z]+/
        on 1 token in D.text
        as word
from Document D;

-- One to three capitalized words - rewritten avoiding use of long, complex regular expression
create view OneToThreeCapitalizedWordsBetter as
 extract blocks
    with count between 1 and 3
    and separation 0 tokens
    on CW.word as capswords
from CapitalizedWords CW
consolidate on capswords;
```

Alternatively, you can also use `extract pattern` to find one to three capitalized words to make it even simpler and more efficient:

```bash
-- Same extraction using the 'extract pattern' statement 

-- One to three capitalized words
create view OneToThreeCapitalizedWordsBest as
 extract pattern <CW.word>{1,3}
    as capswords
from CapitalizedWords CW
consolidate on capswords;
```

In both cases, since the `extract blocks` and `extract pattern` statements return all matches, including the matches that overlap, the `consolidate` clause was used to remove the matches that overlap.

### Watch for regular expressions that match an empty string

A match on an empty string can create a serious performance problem. These matches can lead to unexpected results from your downstream AQL code, as well as performance bottlenecks \(as many spans and tuples need to be created to hold the result\).

Here is an example of a regular expression that involves an alternation of two optional subexpressions, one of which is an empty string:

```bash
-- Not recommended; the regular expression matches the empty string
create view OptionalCurrency as
extract 
    regex /(\$)?|(US dollar)?/ 
    on D.text as match 
from Document D;
```

In general, regular expressions that match the empty string are programming errors. The longer the regular expression, the easier it is to make such a mistake. Try to keep your regular expressions as simple as permitted by the semantics of your extractor by following the other regular expression guidelines.

## Use the `and` keyword instead of the And\(\) built-in predicate

The Optimizer does not attempt to optimize the order of evaluation of the arguments to the And\(\) predicate.

A rule that uses the And\(\) built-in predicate such as:

```bash
select ...
from ...
where And(predicate1, predicate2);
```

often runs considerably slower than the same rule in the form of:

```bash
select ...
from ...
where predicate1 and predicate2;
```

When possible, use the SQL-style `and` keyword instead of the And\(\) predicate.

## Avoid Cartesian products

Most often, the result of a Cartesian product is very large and takes a long time to compute.

Cartesian products occur in statements where the `from` clause contains two or more views or tables, but some of them are not connected through a join predicate \(in the `where` clause\). The presence of a Cartesian product usually indicates that the join predicate was omitted by mistake. This is a problem that can be easily corrected.

In the most simple case, a Cartesian product occurs when there are two views in the `from` clause, but there is no join predicate relating these two views in the `where` clause, as in this example:

```bash
create dictionary PersonDict
as ('Bill Clinton', 'Barack Obama', 'George Bush');

create view Person as
extract dictionary 'PersonDict'
on D.text as name
from Document D;

-- Extract U.S. phone numbers of the form '123-456-7890'
create view Phone as
extract regex /\p{Nd}{3}\-\p{Nd}{3}\-\p{Nd}{4}/
on between 1 and 10 tokens 
in D.text as number 
from Document D;

--Cartesian product between Person and Phone
create view PersonPhone as 
select P.name, Ph.number 
from Person P, Phone Ph;
```

The semantics of this AQL rule is to compute the set of all pairs of `Person.name` and `Phone.number` in the input document. If the input contains 1000 persons and 1000 phone numbers, the result consists of 1,000,000 pairs. However, it is unlikely that the intent was to compute all of these pairs. The intent likely was to find pairs of person and phone in close proximity of each other. This goal can be easily accomplished by adding a `Follows` or `FollowsTok` join predicate to the `where` clause:

```bash
--With a join predicate
create view PersonPhoneJoinWithPredicate as 
select P.name, Ph.number 
from Person P, Phone Ph
where FollowsTok(P.name, Ph.number, 0, 5);
```

Cartesian products can also occur in more subtle cases. In this example, the `from` clause consists of four views. However, only A and B, and respectively C and D, are connected by a join predicate. Hence, a Cartesian product occurs between the result of joining A and B and the result of joining C and D.

```bash
create dictionary FirstNamesUSA
as ('Bill', 'George', 'Barack');

create dictionary FirstNamesUK
as ('William', 'George', 'Stewart');

create dictionary LastNamesUSA
as ('Clinton', 'Bush', 'Obama');

create dictionary LastNamesUK
as ('Clinton', 'Johnson', 'Vaughan');

create view A as
extract dictionary 'FirstNamesUSA'
on D.text as match
from Document D;

create view B as
extract dictionary 'FirstNamesUK'
on D.text as match
from Document D;

create view C as
extract dictionary 'LastNamesUSA'
on D.text as match
from Document D;

create view D as
extract dictionary 'LastNamesUK'
on D.text as match
from Document D;

--Cartesian product between A joined with B and C joined with D
create view ABCD as 
select A.* 
from A, B, C, D 
where Equals(A.match ,B.match) 
   and Equals(C.match ,D.match);
```

To avoid Cartesian products, every view in the `from` clause must be connected to each of the other views by a single join predicate, or a chain of join predicates. These are some cases when Cartesian products between two views do not introduce a performance bottleneck. There are cases when the result of the Cartesian product is small, regardless of the documents on which you are using your extractor. An example is when both views contain at most a few tuples, or when one of the views has exactly one tuple. For example, the view `Document` always has a single tuple; therefore, AQL rules that involve a Cartesian product with the view `Document` \(for example, to add additional metadata to an output view\) do not introduce performance bottlenecks.

```bash
create dictionary MergerDict
as ('merged', 'Merged', 'merger', 'Merger', 'merging', 'Merging', 'M&A');

create view Merger as
extract dictionary 'MergerDict'
on D.text as mergeMention
from Document D;

--Cartesian product with the view Document is OK assuming the view Document has a single tuple or small number of tuples of dates
create view DocumentMergerDate as 
select M.*, D.text as mergerDate 
from Document D, Merger M;
```

## Avoid UDFs as join predicates

When the Optimizer is not aware of the semantics of the UDF implementation, it must generate a Nested Loops Join plan that examines every input span. If possible, express the semantics of the UDF in a way that allows the Optimizer to suggest performance improvements.

A user-defined function \(UDF\) can appear in the `where` clause of an AQL `select` statement as a join predicate. A join predicate is a predicate that determines whether a given pair of tuples from the `from` clause appears in the output of the view. Consider the following example, which identifies matching pairs of capitalized and lowercase names:

```bash
create dictionary CapsNameDict as
('GEORGE', 'SAMUEL', 'PETER', 'HENRY');

create dictionary LowNameDict as
('samuel', 'johnson', 'david', 'george');

create function equalsNormalized (p1 Span, p2 Span)
return Boolean
external_name 'udfjars/udfs.jar:com.ibm.test.udfs.MiscScalarFunc!equalsNormalized'
language java
deterministic
return null on null input;

create function normalize (p1 String)
return String
external_name 'udfjars/udfs.jar:com.ibm.test.udfs.MiscScalarFunc!normalize'
language java
deterministic
return null on null input;

create view CapsName as
extract 
    dictionary 'CapsNameDict' 
    on D.text as match
from Document D;

create view LowName as
extract 
    dictionary 'LowNameDict' 
    on D.text as match
from Document D;
```

In this case, equalsNormalized\(\) is a UDF function that takes as input two spans, and checks if their normalized values are equal.

```bash
-- 'equalsNormalized' is a UDF that checks normalized values
create view EqualNormalizedName as 
select C.match as caps, L.match as low 
from CapsName C, LowName L 
where equalsNormalized(L.match, C.match); 
```

When a `select` statement uses a UDF in this way, the Optimizer has limited options for compiling the statement. Since the Optimizer is not aware of the semantics of the UDF implementation, it must generate a Nested Loops Join plan that examines every pair of `CapsName` and `LowName`. As both of these views grow larger, this execution plan can become very expensive.

In some cases, it might be possible to express the same semantics in a different way. In the case of the current example, the semantics of this rule can be obtained by using a slightly different UDF, called normalize\(\), that takes as input a span and outputs the normalized value of the span, together with the built-in predicate Equals\(\) to compare the two normalized values:

```bash
-- Avoid UDF as the join predicate and use built-in function to establish equality
-- 'normalize', however is a UDF itself, although not used as the join predicate 
create view EqualNormalizedNameNoUDF as 
select C.match as caps, L.match as low 
from CapsName C, LowName L 
where Equals(normalize(GetString(L.match)), normalize(GetString(C.match)));
```

In this case, the Optimizer can choose the `Hash Join` algorithm to implement the semantics of the Equals\(\) predicate, which is usually much faster than using a Nested Loops Join operator.

## Be careful when using Or\(\) as a join predicate

The Or\(\) built-in predicate is run with the Nested Loops Join operator. In most cases, if the inputs are supported by faster join operators, it is worthwhile to split the statement into a union of multiple smaller statements.

For example, consider this sample where Or\(\) is run with the Nested Loops Join operator:

```bash
-- not recommended
create view CandidatesAll as
select C.*
from A, C
where Or( FollowsTok(A.match, C.match, 0, 5), 
          FollowsTok(C.match, A.match, 0, 5) );
```

The view can be rewritten into the union of two statements, which gives the Optimizer the opportunity to use faster join algorithms such as Adjacent Join or Sort Merge Join to implement the FollowsTok\(\) predicates in the two `select` statements to allow the run time to employ faster join algorithms internally::

```bash
-- above view, rewritten to use union all statement
create view CandidatesAllBetter as
(
    select C.*
    from A, C
    where FollowsTok(A.match, C.match, 0, 5)
)
union all
(
    select C.*
    from A, C
    where FollowsTok(C.match, A.match, 0, 5)
);
```

## Use the consolidate clause wisely

The `consolidate` clause is designed to be used at specific points in the extractor where overlapping matches are no longer useful downstream and can be removed. Do not use the `consolidate` clause more often than necessary, but take advantage of its functionality to filter your data.

If you use the `consolidate` too often your AQL code can be harder to maintain since each and every statement has an extra `consolidate` clause. You can also introduce redundant sorting operations, although this is not usually a performance bottleneck.

More importantly, if you do not use the `consolidate` clause enough, a large number of intermediate results can be carried downstream from one view to another, past the point where they are technically required by the semantics of the extractor. A large amount of unnecessary intermediate results can increase the memory footprint and slow extractor throughput.

In this example that illustrates this point, the extractor is looking for mentions of department names, followed within a single token by a phone number.

```bash
-- A single capitalized word
create view UppercaseWord as
 extract
    regex /[A-Z]+/
        on 1 token in D.text
        as word
from Document D;

-- Find organization names as one to two capitalized words
create view Department as
 extract blocks
    with count between 1 and 2
    and separation 0 tokens
    on UW.word as name
from UppercaseWord UW;

-- Find phone numbers of the form xxx-xxxx
create view Phone as
    extract regex /d{3}-\d{4}/ on 3 tokens in D.text as number
    from Document D;

-- Find candidate pairs of department and phone, where the department is followed within one token by the phone
create view DepartmentPhoneAll as 
    select D.name, P.number, CombineSpans(D.name, P.number) as deptWithPhone
    from Department D, Phone P
    where FollowsTok(D.name, P.number, 1, 1);

-- Consolidate to remove overlapping mentions followed within one token by the phone
create view DepartmentPhone as 
    select DP.*
    from DepartmentPhoneAll DP
    consolidate on DP.deptWithPhone;
```

An extremely simplistic Department extractor of a single uppercase word or two uppercase words can help illustrate the issues. Assume the following sample is the input text:

```bash
CS DEPARTMENT 555-1234 
REGISTRAR 555-5678
```

The semantics of the `extract block` statement are to return all mentions, including those mentions that overlap. Therefore, the view `Department` contains the following spans:

```bash
CS 
DEPARTMENT 
CS DEPARTMENT 
REGISTRAR 
```

The result of `DepartmentPhoneAll` is:

```bash
CS, 555-1234 
DEPARTMENT, 555-1234 
CS DEPARTMENT, 555-1234 
REGISTRAR, 555-5678

```

Notice that `DepartmentPhoneAll` contains overlapping matches that are unnecessary at this point in the extractor. Those overlapping matches are removed by the `consolidate` clause in the final view `DepartmentPhone`, to return this desired result:

```bash
CS DEPARTMENT, 555-1234 
REGISTRAR, 555-5678
```

However, the overlapping `Department` spans are probably not needed when you are looking for corresponding phones. The spans result in extra tuples that are created in `DepartmentPhoneAll` and carried downstream to `DepartmentPhone`, only to be removed later on by the `consolidate` clause. Instead, the unnecessary Department partial spans can be removed earlier, in the Department view:

```bash
-- Find organization names as one to two capitalized words
-- Use the consolidate clause to remove unnecessary overlapping mentions
create view DepartmentBetter as
 extract blocks
    with count between 1 and 2
    and separation 0 tokens
    on UW.word as name
from UppercaseWord UW
consolidate on name;
```

## Define common subpatterns explicitly

Define a view that computes common subpatterns so that the Optimizer does not need to evaluate each pattern.

The Optimizer does not currently perform common subexpression elimination for sequence pattern expressions. Therefore, subpatterns that appear in multiple pattern expressions are evaluated multiple times. In the following code snippet, the common subexpression `<A.match> <B.match>` is computed twice; once for `Pattern1`, and again for `Pattern2`:

```bash
-- subexpression <A.match> and <B.match> computed twice;  once each for Pattern1 and Pattern2
create view Pattern1 as
extract pattern <A.match> <B.match> <C.match> as match
from A A, B B, C C;

create view Pattern2 as
extract pattern <A.match> <B.match> <D.match> as match
from A A, B B, D D;
```

If you find that multiples of your pattern expressions use a common subpattern, it is a good idea to explicitly define a view that computes the subpattern. You can reuse that view when you define the larger patterns. In this example, the view `SubPattern` explicitly defines `<A.match> <B.match>`. This is then reused in the views `Pattern1` and `Pattern2`:

```bash
-- Define the common subpattern in its own view so not computed unnecessarily
create view SubPattern as
extract pattern <A.match> <B.match> as match
from A A, B B;

create view Pattern3 as
extract pattern <AB.match> <C.match> as match
from SubPattern AB, C C;

create view Pattern4 as
extract pattern <AB.match> <D.match> as match
from SubPattern AB, D D;
```

# Step 3: Focus on writing AQL instead of tuning performance

You can usually rely on the Optimizer to choose an efficient plan, so do not tune performance while you are writing AQL.

The SystemT engine has a sophisticated cost-based Optimizer, so you can concentrate on writing AQL rules that are easy to understand and easy to maintain.

- **Tuning your AQL to force a particular plan can be counter-productive**

    The best execution plan is often not obvious, especially when you are considering an extractor that might consist of multiple different modules written by different developers. Tuning AQL rules to produce a particular execution plan that seems optimal can be counter-productive. This tuning always makes the extractor harder to maintain, and it often makes the extractor slower.

- **Write AQL for ease of maintenance and understanding**

    Aside from following the basic AQL guidelines, in most cases you will have better performance, as well as improved accuracy and maintainability, if you write AQL rules for ease of maintenance and understanding. Let the Optimizer do its job, then check to make sure that the Optimizer chose the most efficient plan by using the AQL Profiler.

# Step 4: Use the AQL Profiler to find problems

Sometimes there is not an efficient plan for the particular semantics of the AQL expressions. 
The AQL Profiler is designed to help you quickly zero in on these problem cases.
The AQL Profiler is included in the SystemT low-level Java API (class com.ibm.avatar.api.AQLProfiler)
and is accessible in the AQL Developer Tooling (Eclipse-based) by right clicking on your AQL project > `Profile as` > `Text Analytics Profiler Configuration`.

The AQL Profiler is a sampling profiler for SystemT operator graphs.
While one thread runs the extractor over a collection of documents, a second thread periodically samples the state of the first thread, or what the operator is running at that moment. After the extraction thread is done running, the AQL Profiler sums up all of these samples to produce a breakdown of what views and operators account for what percentage of overall run time. This approach gives an accurate picture of the run time of various views and operators, as long as the AQL Profiler is allowed to run for a few tens of seconds.

The SystemT low-level Java APIs describe how to start the AQL Profiler: 
You provide a collection of example documents to use as a target of extraction during profiling. 
The AQL Profiler runs on the set of documents that you specified.

The documents should represent the kinds of documents that will be used in the end-to-end application, but you do not need a large document set. 
The AQL Profiler loops over the document collection until it has run for the specified minimum number of seconds. It is recommended to run the AQL Profiler for at least 30 seconds, to ensure the JVM has sufficient time to optimize byte code. This ensures the profiler results are realistic and as expected in production mode when usually an extractor is loaded once, and subsequently used to process many documents over a long period of time.

- **Hot Views**

    Hot views are views within the compiled plans that are responsible for the largest fraction of execution time. The views are sorted from least to most expensive. The view at the lower portion of the table is the most expensive and it is the best target for any hand-tuning efforts.

  - **Sequence patterns and subqueries**

        Sequence patterns are implemented via an AQL to AQL rewrite. Each sequence pattern turns into one or more AQL views. Each extract pattern statement is turned into a collection of internal views that are based on select and extract statements before they are fed to the Optimizer. You will see these view names in the operator graph plan and the AQL Profiler output. The naming convention for sequence patterns is:

        ```bash
        _[original view name]_TmpView_[pattern]__[number]
        ```

        For example:

        ```
        _AmountWithUnit_TmpView_<N.match> <U.match>__3
        ```

        A similar approach applies to subqueries in the from clause of a `select` statement.

- **Hot Documents**

    Hot documents are documents that took unusually long amounts of time to process during the profiling run. The Optimizer attempts to produce plans that are not sensitive to the input documents, but sometimes such plans do not exist. For example, there might be a regular expression that happens to run slowly on particular input strings, or an AQL view can require the extractor to output a view whose size is very large for certain documents.

    If you find a problem document, you can create a document collection with just that document and run the Profiler again.

    The first document in the collection often comes to the top of this list, because the “just in time” Java™ compiler might be generating object code at the same time that it is processing this document.

# Step 5: Tune the most expensive view first

Focus your performance tuning on views that are heavily represented in the AQL Profiler output.

A standard performance tuning practice is that if a view is taking up 10% of the execution time, even if you drop its running time to zero, the overall extractor will only get 10% faster. When you tune for performance it impacts readability and ease of maintenance, so be selective.

- **Use Amdahl's law**

    Amdahl’s Law states that the maximum possible speed enhancement from tuning a view is inversely proportional to what fraction of the execution time that the view represents. If a view takes 5% of the execution time and you make it 100 times faster, the extractor becomes about 1.05 times faster. If a view takes 90% of the execution time and you make it 100 times faster, the extractor becomes about 9.2 times faster.

- **Concentrate efforts on the most expensive view**

    Concentrate any tuning efforts on the most expensive view. Each time you make a change to improve performance, it is important to rerun the AQL Profiler and to check whether the view that you are working on is still the most expensive. If another view moves to the top of the list, switch to working on that view.

- **After each round of tuning, rerun the AQL Profiler and repeat**

    This process should continue until either the throughput goals of the application are met or all views take approximately the same amount of time to process.

# Examples: Solving common performance problems

The main goal of tuning an AQL view is to identify the reason that the view is running slowly, and then fix that problem.

Once you determine which view to tune for performance, you can focus on tuning that view. These scenarios illustrate regular causes of performance problems, from most common to least common, and explain ways to fix these performance problems.

## Did not follow an AQL guideline: Correct the AQL

The most common performance tuning scenario is the need to correct a case where the basic AQL writing guidelines were not followed. In this example, word boundary checks \(`\b`\) in the regular expression prevent Regular Expression Strength Reduction and Shared Regular Expression Matching \(RSR/SRM\) optimization. These are the most powerful regular expression optimizations.

```bash
create view Number as
extract regex /\b\d+\b/
      on D.text
  as match
from Document D;
```

This problem is easily corrected by modifying the view so that it conforms to the guideline. In this case, you can rewrite the `extract regex` statement to use the `on 1 token` clause in place of the word boundary checks. This change enables both RSR/SRM to be applied, without changing the results of the view. You can convert the regular expression to an RSR/SRM compatible view that produces the same result.

```bash
create view Number as
extract regex /\d+/
      on 1 token 
      in D.text
  as match
from Document D;
```

## Optimizer could not apply an optimization: Refine the view definition so that optimization applies

Another common scenario occurs when the Optimizer is unable to apply an optimization due to unnecessary constraints built into the structure of the AQL rules. In this example, the extractor identifies expressions of sentiment that apply to particular IBM® products. The rules are composed of two parts: a very complex and expensive sentiment extractor, and a simple dictionary evaluation that looks for product names:

```bash
-- Table containing <name, sentiment> pairs of well-known software products

create table SentimentTable 
  (name Text, sentiment Text) as
values
  ('Cognos', 'positive'),
  ('QuickBooks', 'neutral'),
  ('Office 365', 'positive'),
  ('Lotus Notes', 'neutral'),
  ('DB2', 'positive'),
  ('SQL Server', 'neutral'),
  ('BigInsights', 'positive'),
  ('Photoshop', 'positive'),
  ('Watson', 'positive');

create dictionary IBMProductNameDict
as ( 'DB2', 'BigInsights', 'Watson');

-- Classify mentions of products as positive or negative
create view ProductPairs as
select S.name as name, 
       S.sentiment as sentiment
from SentimentTable S
where MatchesDict('IBMProductNameDict', S.name);
```

The Optimizer cannot apply the most productive optimizations because of the way the AQL was written. This issue often happens because there are optimizations that occur behind the scenes that conflict with an attempt to hand-tune the AQL for performance while the rules are being written. In this case, the constraint is expressed that only sentiments about IBM products are relevant as an explicit filtering condition over the output tuples of the `Sentiment` view. There is only one plan that the Optimizer can generate that completely implements the semantics of the view; the Optimizer must evaluate the entire sentiment extractor all of the time, then run the results through a `Select` operator that filters the sentiments that do not apply to the target products. This plan leads to wasted work, particularly if most of the documents do not contain a product name.

You can rewrite this view to give the Optimizer more leeway to choose an efficient plan. Take that dictionary match that was expressed as a filtering condition \(a MatchesDict\(\) predicate\), and rewrite it into a join operator with the results of an `extract dictionary` statement. The result is a set of rules that has almost the same low-level semantics, and from the perspective of the application the semantics are exactly the same. But the Optimizer can now apply Conditional Evaluation to skip sentiment analysis entirely on documents that do not contain a product name.

```bash
-- Table containing <name, sentiment> pairs of well-known software products

create table SentimentTable 
  (name Text, sentiment Text) as
values
  ('Cognos', 'positive'),
  ('QuickBooks', 'neutral'),
  ('Office 365', 'positive'),
  ('Lotus Notes', 'neutral'),
  ('DB2', 'positive'),
  ('SQL Server', 'neutral'),
  ('BigInsights', 'positive'),
  ('Photoshop', 'positive'),
  ('Watson', 'positive');

create dictionary IBMProductNameDict
as ( 'DB2', 'BigInsights', 'Watson');

create view IBMProductName as
extract dictionary 'IBMProductNameDict'
on D.text as name
from Document D;

-- Classify mentions of products as positive or negative
create view ProductPairs as
select S.name as name, 
       S.sentiment as sentiment
from SentimentTable S, IBMProductName I
where Equals(S.name, I.name);
```

## Optimizer made a mistake: Rework the view definition to force the correct plan

Occasionally, the Optimizer makes a mistake. The Optimizer chooses its plan that is based on a cost model, and this model is not perfect. For example, the cost model currently assumes that all dictionary evaluations are faster than all regular expressions. But in some cases, such as the following example, this assumption is not true.

```bash
create dictionary TheDict as ('the');

create view The as 
extract dictionary 'TheDict'
   on D.text as match
from Document D;

create view Nth as
extract regex /\d+th/
   on 1 token 
   in D.text as match
from Document D;

-- Find phrases like "the 10th"
create view TheNth as
select 
CombineSpans(T.match, N.match) as match
from The T, Nth N
where 
   FollowsTok(T.match, N.match,0,0);
```

In this case, evaluating a dictionary that contains the common word `the` is fairly expensive because that word produces a large number of matches, and it takes time to construct a tuple for each match. The regular expression in this example is small and straightforward. It produces very few matches on most documents, so evaluating this regular expression is likely to be less expensive than running the `TheDict` dictionary.

For the overall extractor, which looks for phrases like `the 10th`, the Optimizer always incorrectly chooses to evaluate the dictionary and to use conditional evaluation or Restricted Span Evaluation \(RSE\) to reduce the time that is spent on the regular expression. The alternate plan, to evaluate the regular expression first, is actually the one that the Optimizer should choose. This mistake can be corrected by rewriting the AQL so that the Optimizer has no choice but to choose the “correct” plan. Basically, the modification is the inverse of the previous example. Take the join between the views `The` and `NTh` and replace that join with a filtering condition on the contents of the `Nth` view. This rewrite forces the compiler to generate an execution plan that uses RSE to run the `TheDict` dictionary only on selected portions of each document.

```bash
create dictionary TheDict as ('the');

create view Nth as
extract regex /\d+th/
   on between 1 and 1 tokens 
   in D.text as match
from Document D;

-- Find phrases like "the 10th"
create view TheNth as
select 
CombineSpans
  (
     LeftContextTok(N.match, 1), N.match
  ) 
  as match
from Nth N
where MatchesDict('TheDict', LeftContextTok(N.match, 1));
```

**Important:** These rewrites harm maintainability and can make the extractor slower if you are not careful. Rework views in this way only if the Profiler indicates that there is a serious performance problem.

## No efficient plan exists: Reevaluate the semantics of the extractor

A relatively uncommon performance tuning scenario is when there is no possible efficient plan for a given set of AQL rules. Here is an example of how this kind of scenario can occur:

```bash
create view SentenceBoundary as
extract 
  D.text as text,
  regex /(([\.\?!]+\s)|(\n\s*\n))/ on D.text as boundary 
from Document D;

create view Sentence as
extract 
    split using S.boundary 
    retain right split point
    on S.text as sentence
from SentenceBoundary S;

create dictionary ProductNameDict
as ( 'DB2', 'BigInsights', 'Watson');

create view ProductName as
extract dictionary 'ProductNameDict'
on D.text as match
from Document D;

-- Find pairs of product names that occurred in the same sentence
create view ProductPairs as
select N1.match as name1, 
       N2.match as name2
from ProductName N1, 
     ProductName N2, 
     Sentence S
where 
   FollowsTok(N1.match, N2.match, 0, 10) and Contains(S.sentence, N1.match) and Contains(S.sentence, N2.match) and Not(Equals(N1.match, N2.match));
```

In this example, the view `Sentence` splits the contents of the document into individual sentences. Imagine that the `SentenceBoundary` view is expensive because it contains an expensive regular expression. Another view, `ProductPairs`, looks for sentences that contain mentions of two different product names. Since the `Sentence` view uses the `extract split` statement, the join in the `ProductPairs` view must take the entire collection of sentences as input and identifying sentence boundaries can be expensive. The Optimizer can improve throughput by leveraging Conditional Evaluation to ensure that the `Sentence` view is only evaluated when the document contains at least one pair of product names. However, that entire view must still run even if the document contains only one product name pair. Basically, even the best possible plan is not very good.

This problem can be corrected by rethinking the semantics of the extractor. Instead of looking for two product mentions within the same sentence, you can find two product names that do not have a sentence boundary between them. Convert the expensive regular expression for sentence boundaries into a filtering condition \(the Not\(MatchesRegex\(\)\)\), and the Optimizer can access an execution plan that avoids evaluating the expensive regular expression over most of the document.

```bash
create dictionary ProductNameDict
as ( 'DB2', 'BigInsights', 'Watson');

create view ProductName as
extract dictionary 'ProductNameDict'
on D.text as match
from Document D;

-- Find pairs of product names that occurred in the same sentence without splitting sentences
create view ProductPairs as
select N1.match as name1, 
       N2.match as name2
from ProductName N1, 
     ProductName N2
where
   FollowsTok(N1.match, N2.match,0,10) 
   and Not(MatchesRegex(/(([\.\?!]+\s)|(\n\s*\n))/, SpanBetween(N1.match, N2.match)));s
```
