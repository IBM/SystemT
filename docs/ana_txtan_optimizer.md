---
providerName: IBM
layout: page
title: The SystemT Optimizer
nav_order: 5
parent: Reference
grand_parent: In depth rule-based modeling techniques
description: The SystemT Optimizer
---

# SystemT Optimizer


* Table of Contents
{:toc}

# Overview

One of the strengths of SystemT is the Optimizer.
There are many ways to run a set of AQL statements. 
The Optimizer calculates different execution plans, evaluates these execution plans, 
and chooses a good plan from the alternatives.

The final output of the Optimizer is a compiled [execution plan](#execution-plan). 
Execution plans in SystemT consist of graphs of [operators](#operators).
A SystemT execution plan can contain familiar relational operators, and specialized operators that find patterns and features in text, compact sets of spans, and perform unique extraction tasks.

The idea of the SystemT Optimizer is similar to a relational database optimizer, but with text-specific optimizations and a text-specific cost model to select these optimizations.
Unlike the optimizer in a relational database, the SystemT Optimizer is tuned to the needs of information extraction rules. 
The cost model focuses on the text-specific operations that tend to dominate run time.
Unlike a traditional database where I/O functions are usually the most important cost, 
the SystemT cost model focuses on minimizing the processor cost of these text operations.

The plan optimizations are also specific to information extraction. 
There are five types of optimization techniques:

- Shared Dictionary Matching \(SDM\)
- Regular Expression Strength Reduction \(RSR\)
- Shared Regular Expression Matching \(SRM\)
- Conditional Evaluation \(CE\)
- Restricted Span Evaluation \(RSE\)

# Types of optimizations

## Shared Dictionary Matching \(SDM\)

The SystemT run time has a `Dictionary` operator that is called that implements the core of the `extract dictionary` statement. This operator matches an exhaustive dictionary of terms against the tokens in the document. Internally, this operator performs three different operations:

1. The operator tokenizes the input text and essentially divides the text into individual words.
2. The operator takes each input token, computes a hash value, and uses that hash code to probe into a hash table of dictionary entries.
3. If it finds a match, the operator generates output tuples that contain the dictionary matches based on the data that is stored in the hash table entry.

As is often the case, if there are multiple `extract dictionary` statements in the original AQL that target the same source text, it is possible to share the first two steps \(tokenization and hashing\) among all of those dictionaries. SystemT has a special `Dictionaries` operator that implements this sharing, and the Optimizer tries to generate this operator when it considers possible execution plans. This transformation from multiple individual Dictionary operators to the single `Dictionaries` operator is called Shared Dictionary Matching \(SDM\). The processor cost savings from this transformation can be substantial.

## Regular Expression Strength Reduction \(RSR\)

The SystemT runtime component uses two engines to evaluate regular expressions:

- **Java’s built-in regular expression engine**

    This engine supports a large class of regular expressions, including advanced constructs such as backreferences, lookahead, and lookbehind. To support such advanced constructs, Java's implementation is based on recursion and backtracking, which can be slow for certain types of regular expressions that are common in information extraction.

- **The SimpleRegex engine**

    To address the performance problems of the common regular expression engines, the SystemT runtime has its own regular expression evaluation engine called SimpleRegex. SimpleRegex is based on a design that is known as a Thompson NFA/DFA hybrid. The result is an engine that can run complex regular expressions that are common to information extraction applications much faster than a backtracking engine. Another benefit of this approach is that the engine can evaluate multiple regular expressions with one pass, using a single state machine.

The main disadvantage of the Thompson NFA/DFA approach is that the advanced features of Java or POSIX regular expressions \(such as backreferences and lookahead\) are difficult to implement. Therefore, not every `extract regex` statement can be evaluated with SimpleRegex. Instead, the Optimizer performs a plan transformation called Regular Expression Strength Reduction \(RSR\).

The Optimizer examines every regular expression that appears in an `extract regex` statement or in a scalar function call and determines whether that expression can be evaluated with the SimpleRegex engine. If the expression is SimpleRegex compatible, the Optimizer generates a plan with operators \(`FastRegex` and `FastRegexTok`\) that use the SimpleRegex engine. Otherwise, the Optimizer falls back on Java’s built-in engine.

You can determine whether RSR is being used if the `FastRegex` and `FastRegexTok` operators are in the execution plan.

## Shared Regular Expression Matching \(SRM\)

The SimpleRegex engine that is used for Regular Expression Strength Reduction can evaluate many regular expressions in a single pass over the target text. This capability can lead to a major performance boost. The engine can take 10 regular expressions and combine them, and then evaluate all 10 in one pass in approximately 1/10th of the time. This transformation is called Shared Regular Expression Matching \(SRM\), and the Optimizer attempts to generate plans that take advantage of this capability. You can determine whether SRM is being used by looking for the `RegexesTok` operator in the execution plan.

**Note:** Currently, the SRM optimization is only applied over `extract regex` statements that contain the `on between X and Y tokens` clause. This is one of the reasons that it is a good idea to use token-based regular expression matching in your AQL rules.

## Conditional Evaluation \(CE\)

This optimization leverages the fact that the SystemT runtime processes one document at a time. If there is a join operator in the plan, the join produces no outputs on a document if one of the inputs is empty. The SystemT join operators take advantage of this fact by first evaluating the outer \(left\) input. The inner \(right\) argument is only evaluated if the outer produces at least one tuple on the current document. This approach is called Conditional Evaluation \(CE\), and the cost savings can be substantial.

The Optimizer does not explicitly add operators to enable Conditional Evaluation. It is built into most of the join operators and is on constantly. Specifically, the Nested Loops Join \(`NLJoin`\), the Sort Merge Join \(`SortMergeJoin`\), the Hash Join \(`HashJoin`\), and the Adjacent Join \(`AdjacentJoin`\) operators all use Conditional Evaluation. The cost model of the Optimizer does take into account the probability that the inner \(right\) argument of a join operator will not need to be evaluated. Therefore, there are many execution plans where the order of the inputs to the join were changed from the original order in the `select` statement to take advantage of Conditional Evaluation.

In addition, the `Minus` operator \(which implements the `minus` statement in AQL\) also uses Conditional Evaluation, although the Optimizer does not consider reversing the order of the inputs to that operator.

## Restricted Span Evaluation \(RSE\)

The Restricted Span Evaluation \(RSE\) optimization is a more sophisticated version of Conditional Evaluation. This optimization is implemented by a special type of join operator that is called Restricted Span Evaluation Join \(`RSEJoin`\). The `RSEJoin` operator takes each tuple on the outer \(left\) input and sends information about that tuple down to the inner \(or right\) input. This information allows the operators on the inner input to avoid wasted work. For example, consider the following AQL code that finds person names, followed closely by phone numbers:

```bash
create dictionary PersonNameDict from file 'person_names.dict'; 

create view PersonPhone as 
select Person.name as name, Phone.number as number
from
( extract dictionary PersonNameDict on D.text as name from Document D ) Person,
( extract regex /\d{3}\-\d{4}/ on 3 tokens in D.text as number from Document D ) Phone
where FollowsTok(Person.name, Phone.number, 0, 5);

```

The execution plan for the `PersonPhone` view would consist of a join between person names and phone numbers. A `Dictionary` operator is used to find person names, and a `Regex` operator is used to find phone numbers.

The following diagram displays a high-level picture of three possible execution plans for the `PersonPhone` view:

![The diagram displays a high-level picture of three possible execution plans for the PersonPhone view](TA_plan12_v3_72res.gif)

Plan A illustrates a conventional join plan that evaluates the dictionary and regular expression, then identifies pairs of spans from both sets of matches that satisfy the FollowsTok\(\) predicate in the original where clause of the AQL statement.

Plans B and C use Restricted Span Evaluation \(RSE\) to evaluate the `PersonPhone` view. The execution plan of Plan B only evaluates the regular expression on selected portions of the document. Plan C only evaluates the dictionary on selected portions of the document. This approach can greatly reduce the amount of time that is spent evaluating low-level primitives like regular expressions. This approach is particularly useful with expensive regular expressions that are not amenable to Regular Expression Strength Reduction \(RSR\).

However, the inner operand of the join needs to be able to use the information from the outer, so the kinds of expressions where RSE can be applied are fairly limited. Specifically, RSE can only be used with joins that take Follows or FollowsTok\(\) join predicates, and where one of the inputs to the join is an extract statement. Therefore, RSEJoin might not appear in execution plans often.

# <a name="ex-plan">Execution plan</a>

The final output of the Optimizer is a compiled execution plan. Execution plans in SystemT consist of graphs of operators.

An operator is a module that performs specific extraction subtask, such as identifying matches of regular expressions within a string. These operators are tied together in a graph by connecting the output of one operator to the input of one or more other operators. Therefore, a compiled execution plan is also known as an Annotation Operator Graph \(AOG\).

Consider the following AQL code sample that shows you how to read this text representation of an AOG. This example identifies patterns of numeric amounts followed by units of measurement, such as “10 pounds” or “100 shillings”. The `Number` view identifies the numeric amount, the `Unit` view uses a dictionary to find units of measurement, and the `AmountWithUnit` view combines these features into a single entity.

```bash
create view Number as
extract regex /\d+/
   on between 1 and 1 tokens 
   in D.text
  as match
from Document D;

create view Unit as 
extract dictionary UnitDict
   on D.text as match
from Document D;

create view AmountWithUnit as
select 
CombineSpans(N.match, U.match) 
   as match
from Number N, Unit U
where FollowsTok(N.match, U.match,  0, 0);

```

The following sample is a portion of the text representation of the AOG that corresponds to the previous AQL code:

```bash
$AmountWithUnit =
Project(("FunctionCall30" => "match"),
  ApplyFunc(
    CombineSpans(
      GetCol("N.match"),
      GetCol("U.match")
    ) => "FunctionCall30",
    AdjacentJoin(
      FollowsTok(
        GetCol("N.match"),
        GetCol("U.match"),
        IntConst(0),
        IntConst(0)
      ),
      Project(("match" => "N.match"),
        $Number
      ),
      Project(("match" => "U.match"),
        $Unit
      )
    )
  )

```

The text `$AmountWithUnit =` at the beginning of the AOG sample specifies that everything to the right of the equals sign is a text representation of the portion of the operator graph that implements the `AmountWithUnit` view. The information to the right of the equals sign contains the tree of operators that implements this view.

You might be familiar with the `Project` operator, as it is the same projection operator from relational databases. This operator takes in a set of tuples, then rearranges and renames the fields of each individual tuple. In this example, the `Project` operator takes the input column that is called `FunctionCall30`, renames that column to `match`, and drops all of the other columns in the input.

The `ApplyFunc` operator evaluates function calls that appear in the `select` or `extract` list of an AQL statement. In this case, the original AQL `select` statement had a call to the built-in `CombineSpans()` function. The `ApplyFunc` operator calls this function on each input tuple, and passes the values of the columns `N.match` and `U.match` to the function as the first and second arguments. Each output tuple contains all of the columns of the input tuple, plus an extra `FunctionCall30` column that contains the return value from the function. This `FunctionCall30` column is what the `Project` operator projects to.

The next operator in the sample is an `AdjacentJoin` operator. The SystemT runtime has several different implementations of the relational join operator that are specialized for different text-specific join predicates. In this case, the Optimizer determined that the best join implementation for the FollowsTok\(\) predicate in the `where` clause of the original view is the `AdjacentJoin` operator. The first argument in the AOG is the join predicate. The second and third arguments are the two inputs to the join. Remember, a join operator takes two sets of tuples and finds all pairs of tuples from the two sets that satisfy a predicate.

The inputs to the join come from two more `Project` operators, but the inputs to those operators are slightly different from the rest of this tree of operators. These inputs `$Number` and `$Unit` are actually references to other plan subtrees. If you were to examine the complete AOG representation of the operator graph, you would see a line that starts `$Number =`, with a subplan to the right of the equals sign. The reference to `$Number` in the current subplan tells the operator graph initializer that the input of the `Project` operator should be connected to that `$Number` subplan. In the initialization stage, the SystemT runtime reads the compiled plans in AOG representation of each compiled module of the extractor. Then, it stitches together plan subtrees within each module and across modules to produce a complete operator graph for the entire extractor.

# <a name="operators">Operators</a>

A SystemT execution plan can contain familiar relational operators, and specialized operators that find patterns and features in text, compact sets of spans, and perform unique extraction tasks.

There are four categories of operators in an execution plan, or Annotation Operator Graph \(AOG\).

## Relational operators

Relational operators implement the different operations over sets of tuples. These operators are similar to operators from relational databases.

- Join operators

  Join operators take as input two sets of tuples and output a set of pairs of tuples that satisfy the join predicate. They are used to implement parts of the `where` clause of an AQL `select` statement. There are several kinds of join operators, corresponding to different built-in join predicates in AQL:

  - **Nested Loops Join \(NLJoin\)**

    This operator uses the most naïve join algorithm that examines every pair of input tuples. It is used for join predicates that are defined by using user-defined functions \(UDFs\), and built-in predicates that are not supported by other join algorithms, such as the Or\(\) built-in predicate.

  - **Adjacent Join \(AdjacentJoin\)**

    This operator is a specialized join operator that finds spans that are adjacent to each other. It is used to implement the FollowsTok\(\) and FollowedByTok\(\) built-in predicates. The `AdjacentJoin` operator uses an algorithm that performs very fast when the inputs are on token boundaries.

  - **Sort Merge Join \(SortMergeJoin\)**

    This operator is a specialized join operator for comparing two spans. It is used to implement the Contains\(\), ContainedWithin\(\), Overlaps\(\), Follows\(\), FollowedBy\(\), FollowsTok\(\), and FollowedByTok\(\) predicates.

  - **Hash Join \(HashJoin\)**

    This operator is a specialized join operator for the Equals\(\) predicate.

  - **Restricted Span Evaluation Join \(RSEJoin\)**

    This operator is a specialized join operator that implements Restricted Span Evaluation \(RSE\). It evaluates the outer \(first\) argument, then passes information about the tuples that are produced by the first argument to the inner \(second\) argument.

- Other relational operators

  Other familiar relational operators include:

  - **Select**

    This operator is used to implement the `having` clause of an `extract` statement, as well as parts of the `where` clause of a `select` statement that are not used as join predicates.

  - **Project**

    This operator implements the final processing that is required for the `select` list of an AQL `select` or `extract` statement by dropping unnecessary columns in the input, and renaming the remaining columns as needed.

  - **Union**

    This operator is used to implement the `union all` statement.

  - **Minus**

    This operator is used to implement the `minus` statement.

  - **ApplyFunc**

    This operator is used to compute the result of scalar function calls that appear in various parts of an AQL statement, such as in the select list of a `select` or `extract` statement.

  - **GroupBy**

    This operator is used to implement the `group by` clause of an AQL statement.

  - **Sort**

    This operator sorts the set of input tuples. It is used to implement the `order by` clause of a `select` statement.

## Span aggregation operators

Span aggregation operators take sets of input spans and reduce them to more compact entities.

There are three types of span aggregation operators:

- **Block**

    This operator groups simpler entities that are in close proximity to each other into larger entities. This operator implements the `extract blocks` statement when the distance between input spans is in characters.

- **BlockTok**

    This operator groups simpler entities that are in close proximity to each other into larger entities. This operator implements the `extract blocks` statement when the distance between input spans is in tokens.

- **Consolidate**

    The consolidate operator evaluates a collection of spans and removes overlapping spans according to a specified consolidation policy. This operator implements the `consolidate` clause in the `select` and `extract` statements.

## Span extraction operators

Span extraction operators identify basic low-level patterns in text, usually for extracting features such as matches for regular expressions or dictionaries.

Since these extraction primitives are crucial to overall performance, the system implements several variations of each operator and allows the Optimizer to choose the best option.

- Regular expression operators

  These operators identify matches of one or more regular expressions across the input text. They implement the AQL `extract regex` statement. There are multiple types of this kind of operator:

  - **RegularExpression**

    This operator evaluates a single regular expression by using the Java regular expression engine with character-based matching semantics.

  - **RegexTok**

    This operator evaluates a single regular expression by using the Java regular expression engine with token-based matching semantics.

  - **FastRegex**

    This operator evaluates a single regular expression by using the SimpleRegex expression engine with character-based matching semantics. This operator is the outcome of applying the Regular Expression Strength Reduction \(RSR\) optimization.

  - **FastRegexTok**

    This operator evaluates a single regular expression by using the SimpleRegex regular expression engine with token-based matching semantics. This operator is the outcome of applying the Regular Expression Strength Reduction \(RSR\) optimization.

  - **RegexesTok**

    This operator evaluates multiple regular expressions at the same time. This operator is the outcome of applying the Shared Regular Expression Matching \(SRM\) optimization.

- Dictionary operators

  Dictionary operators are used to implement the AQL `extract dictionary` statement. There are two versions of this operator:

  - **Dictionary**

    This operator evaluates a single dictionary.

  - **Dictionaries**

    This operator evaluates multiple dictionaries at the same time, and it is the outcome of applying the Shared Dictionary Matching \(SDM\) optimization.

- PartsOfSpeech operator

  This operator identifies locations of common parts of speech across the input text.

## Specialized operators

Specialized operators perform special kinds of extraction that do not fit in other operator categories.

There are two types of specialized operators in SystemT:

- **Split**

    This operator splits a span in to multiple subparts based on a set of input spans that mark boundaries. This operator implements the AQL `extract split` statement.

- **Detag HTML/XML**

    This operator detags the input text, optionally retaining the position and content of specific tags. This operator implements the AQL `detag` statement.
