---
providerName: IBM
layout: page
title: AQL concepts
nav_order: 2
parent: Guides 
grand_parent: In depth rule-based modeling techniques
description: What are extractors?
---

# AQL concepts


* Table of Contents
{:toc}

## AQL extractors overview
Extractors are programs that extract structured information from unstructured or semistructured text by using AQL constructs. 

An extractor consists of compiled modules, or Text Analytics module (TAM) files, and content for external dictionary and table artifacts. 
At a high level, the extractor can be regarded as a collection of views, each of which defines a relationship. 
Some of these views are designated as output views, while others are non-output views. 
In addition, there is a special view called `Document`. 
This view represents the document that is being annotated. 
Furthermore, the extractor might also have `external views` whose content can be customized at run time with extra metadata about the document that is being annotated.

The following figure illustrates how SystemT compiles and runs extractors.

![Compiling and executing Text Analytics projects: an AQL file is compiled, an efficient execution plan is produced, and the plan is fed into the runtime component.](images/AQL_runtime_v2.gif "Compiling and executing
Text Analytics projects")

1. The SystemT Optimizer compiles the source modules into compiled TAM files.
2. An extractor is instantiated based on a set of one or more compiled modules, with the content for external dictionaries and tables that are required by these modules. You can reuse and combine modules to create more complex extractors. This process results in a complete executable plan for the extractor.
3. This executable plan is passed as input to the SystemT runtime component.
4. External views can be included as input to customize the extractor by your data needs.
5. SystemT has a document-at-a-time runtime model, which means that at run time, the view `Document` is populated with a single tuple that represents the content of the document to process.
6. The runtime component then runs the execution plan and populates the output views of the extractor with the extracted results.

## AQL modules

Text Analytics modules are self-contained packages. 
They contain a set of text extraction rules that are created by using AQL, 
and other resources that are required for text extraction.

- [Structure of a module](#structure-of-a-module)
- [Documenting a module with AQL Doc comments](#documenting-a-module-with-aql-doc-comments)
- [Example](#example)
- [Scenarios that illustrate modules](#scenarios-that-illustrate-modules)
- [Best practices for developing modules](#best-practices-for-developing-modules)

### Structure of a module

![Source modules are composed of AQL files, dictionary files, user-defined function (UDF) JAR files, and module information. The Optimizer creates a Text Analytics module (TAM) file from the module.](images/AQL_module.gif "The structure of a module")

Resources that are included in a source module can include AQL files, dictionary files, UDF JAR files, and a special file named `module.info` that contains the AQL Doc comment that details the specifics of the module. 
When you import a module, these resources in the form of AQL objects \(such as a `view`, `table`, `dictionary`, or `function`\) are available for reuse. 
This increases the efficiency of development.

The structure of a module consists of, and is defined by, a top-level folder. The name of the folder is also the name of the module. One or more AQL files are located directly in the top-level module folder. AQL files that are located within subdirectories of the top-level folder are ignored by the compiler. Dictionary files and UDF JAR files can be located in the top-level module folder, or within subdirectories of the module folder. References to these files inside a module must be done by using a path relative to the top-level module folder.

Compiling your source module results in a TAM file that includes:

- The compiled form of the AQL files, which is known as the execution plan
- Metadata of the elements that are defined in the module

Module metadata includes all of the details that are necessary to consume the module without instantiating the module. These details include the following information:

- The schema of the special view `Document`
- The views \(output, external, and exported\) with the schema and AQL Doc comments, and cost information that is associated with the view that is used to optimize modules that depend on this view
- The tables \(external and exported\) with the schema and AQL Doc comments
- The dictionaries \(external and exported\) with AQL Doc comments
- The exported functions with AQL Doc comments
- The type of tokenizer used to compile the dictionaries of the module \(This tokenizer must coincide with the tokenizer used at execution time\)
- Any other modules that this module depends on, such as modules needed at compile time or at initialization time, to ensure that all components of the extractor are completely loaded

After you have a compiled module, you can combine the modules or reuse modules that contain established AQL elements, such as tables and artifacts, to build new extractors. 
Your AQL project can be modified easily with modular AQL, and you can reuse your data and share your projects with others.

### Documenting a module with AQL Doc comments

Each module should have a comment that describes its high-level semantics. The comment for a module is placed in a file named module.info in the module folder. This file contains only the comment for the current module.

Capture the following information in a module-level comment:

- A high-level description that contains the application areas, types of data sources, and languages used
- The schema of the `Document` view
- Author information
- The customization points of the module, and what must be modified to customize the module for a different domain or language
- Any other assumptions necessary to effectively use the module.
  - Input format expectations
  - Expectations about the data source, for example, the domain and average size of the documents on which the module is expected to perform
  - Higher level types of analysis that the module completes
  - Patterns that are captured or not captured by the module
  - Specific tags to represent the information

The module comment format consists of a top-level comment followed by a tag. The following pre-defined special AQL Doc comment tags can be used to convey specific portions of the module information. Any other necessary information that does not fall into any of the specific tags should be included in the top-level comment.

- **@appType**

    Specifies application type and areas that are covered by the module. For example, one can specify whether the extractor works for a specific vertical, such as financial, or healthcare, or a cross vertical, such as Social Media Analytics.

- **@dataSource**

    Specifies data sources that the module expects to work with or was tested with. For example, you can indicate the type of input documents that the extractor works with, such as email, social media data, news reports. You can also indicate other characteristics of the data, such as:

  - The average document size
  - The type of content \(formal or informal/noisy\)
  - The structure \(completely unstructured, semi-structured such as HTML or XML, or intrinsic structured such as log records\)

- **@languages**

    A comma-separated list of language codes that are supported by the module.

- **@author**

    The name of the author of this module – one each for every author.

- **@docField**

    A tag each for every field in the required input document schema of the module.

### Example

Consider this common AQL module comment format:

```bash
/**
* This module contains extractors for social data analytics. Extractors include Buzz, Intent, Sentiment, and Person microsegmentation.
*
* @appType 	Social Data Analytics
* @dataSource	Supports social media data
* @languages	en,fr,de
* @docField     text the content of the social media message
* @docField     location the “location” field of a social media data record. Used for extracting attributes such as city and state.
* 
*/
```

### Scenarios that illustrate modules

You can use common design patterns to create Text Analytics modules. 
These examples encompass various AQL statements, and each example uses the AQL Doc comments to explain the code.

#### Importing and exporting modules

In the following example, the module `personEn` creates and makes a view available called `Person`. The module `signature` reuses the views from the `personEn` and `email` modules.

```bash
module personEn; 

/**
 * Extract mentions of person names, primarily from English text. 
 * Limited support for Italian and French.
 *
 * @field fullName person's complete name
 * @field firstName person's given name (optionally populated, when possible)
 * @field lastName person's surname (optionally populated, when possible)
 */
create view Person as
select P.name as fullName, P.first as firstName, P.last as lastName
from PersonFinal P;

export view Person;
```

```bash
module signature; 

import view Person from module personEn;
import view EmailAddress from module email as Email;  
/**
 * Find mentions of person full names followed 
 * within 0 to 5 tokens by an email address.
 *
 * @field match the span covering the full name and email address
 */
create view PersonWithEmail as
extract pattern <P.fullName> <Token>{0,5} <E.address> as  match 
  from personEn.Person P, Email E;

output view PersonWithEmail;
```

This example illustrates the two forms of the `import view` statement:

- Without a local name.

    The name, fully qualified by module name \(`personEn.Person`\), is placed in the namespace of the current module.

- With a local name.

    The local name `Email` is placed within the namespace of the current module `signature`, instead of the fully qualified name `email.EmailAddress`.

The name that is imported from the first module is used throughout the second module in the `from` clause of that statement, which uses the fully qualified name, or the local name for the two views.

#### Domain-customization of an extractor

In the following example, `common` is a library of basic extractors that are useful for Machine Data Analysis. The `datapower` and `syslog` modules are implementations of Machine Data Analysis extractors for two different types of logs. The `extractor_datapower` and `extractor_syslog` modules import relevant libraries and output the views.

```bash
module common; 
/**
 * Extract mentions of IP addresses of 
 * the form xxx.xxx.xxx.xxx
 * from the text field of view Document.
 *
 * @field address the IP address
 */
create view IP as … ;
export view IP;
```

```bash
module datapower; 
import module common;
/**
 * Extract mentions of DeviceIP addresses. In DataPower logs,
 * let’s imagine that the DeviceIP is the first IP mention 
 * after the timestamp.
 * @field address the IP address
 */
create view DeviceIP as … ;
export view DeviceIP;
```

```bash
module syslog; 
import module common;
/**
 * Extract mentions of DeviceIP addresses. In system logs,
 * let’s imagine the DeviceIP is at the end of the log record,
 * so we must provide an implementation different than for DataPower.
 * @field address the IP address
 */
create view DeviceIP as … ;
export view DeviceIP;
```

```bash
module extractor_datapower; 
import module datapower;

output view datapower.DeviceIP as 'DeviceIP';
```

```bash
module extractor_syslog; 
import module syslog;

output view syslog.DeviceIP as 'DeviceIP';
```

The `import module` statement makes names available that are fully qualified by the module name. The view names \(and dictionary, table, and function names\) are always fully qualified by the module name. The `output view DeviceIP` results in different output names \(such as `datapower.DeviceIP` and `syslog.DeviceIP`\). The application code must change depending on which modules you use for extraction. For example, Extractor1 \(which uses common, datapower, and extractor\_datapower\) and Extractor2 \(which uses common, syslog, and extractor\_syslog\) generate different output names for the DeviceIP type. The best practice is to use the `output view <view-name> as '<alias>'` statement. The `output view <moduleName>.DeviceIP as 'DeviceIP'` results in both Extractor1 and Extractor2 exporting identical names, DeviceIP.

#### Combining modules to create an extractor

You can combine modules to create an extractor. This example uses two modules, `phone` and `person`, to create an extractor that identifies all of the occurrences of a person with a phone number that immediately follows it \(for example, the view `PersonPhone`\). The `Person` and `PhoneNumber` views might be used in other extractors, so they are placed in separate modules for easy reuse.

```bash
module phone;

create view PhoneNumber as
select P.num as number
from
{
extract
   regexes /\+?\[1-9]\d{2}\)\d{3}-\d{4}/ and /\+?[Xx]\.?\d{4,5}/
   on D.text as num
from Document D
} P;

export view PhoneNumber;

```

```bash
module person;

-- first names, derived from a dictionary of common first names
create dictionary FirstNameDict from file ‘dictionaries/first.dict’;

create view FirstName as
extract dictionary FirstNameDict on D.text as name
from Document D;


-- last names, derived from a dictionary of common last names
create dictionary LastNameDict from file ‘dictionaries/last.dict’;

create view LastName as 
extract dictionary LastNameDict on D.text as name
from Document D;


-- a person is a first name followed immediately by a last name
create view Person as 
select F.name as firstName, L.name as lastName, CombineSpans(F.name, L.name) as person
from FirstName F, LastName L
where FollowsTok(F.name, L.name, 0, 0);

export view Person;

```

```bash
module personPhone;

import module person;
import module phone;

-- generate people followed immediately by a phone number
create view PersonPhone as 
select PE.person as name, PH.number as number 
from person.Person PE, phone.PhoneNumber as PH
where FollowsTok(PE.person, PH.number, 0, 0);

output view PersonPhone;

```

#### Building a library of UDFs

This example illustrates a common design pattern on how to build a module of user-defined functions \(UDFs\) that you can reuse in other modules.

The module `normalizationLib` defines and exports one UDF function. The module `signature` reuses all of the views, tables, dictionaries, and functions that are made available by the `personEn` module, and one function from the `normalizationLib` module. Although the `import function` statement is not illustrated in this example, it is similar to other `import` statements in that it has two forms; with or without local name. Notice in the example, the use of the local name of the function, `normalize`, in the `select` clause of the view `PersonNormalized`:

```bash
module normalizationLib; 

/**
 * UDF function for normalizing a span value.
 *
 * @param val the input span value
 * @return string content of the span, normalized as follows:
 *         all characters converted to lower case;
 *         multiple consecutive white spaces replaced by a single space
 */
create function normalizeSpan(val Span)
return String
external_name 'udfjars/exampleUDFs.jar:textanalytics.udf.ExampleUDFs!norm'
language java 
deterministic
return null on null input;

export function normalizeSpan;
```

```bash
module signature; 

import module personEn;
import function normalizeSpan from module normalizationLib as normalize;

/**
 * Generate a normalized version of a Person mention 
 * by replacing consecutive white spaces with a single space
 * and converting the result to lower case.
 *
 * @field normFullName normalized string representation of the full name
 */
create view PersonNormalized as
select normalize(P.fullName) as normFullName
from personEn.Person P;
```

#### Customizing dictionaries

In this example, the `commonPersonNames` module makes two dictionaries available, an internal dictionary and an external dictionary. The external dictionary is initially empty \(at compile time\). It is populated at initialization time. The format for the external dictionary file is .dict. There is one dictionary entry per line.

The scope of `set default dictionary language` is all external or internal dictionaries inside the entire module \(not just the current AQL file\) because the statement does not use the `with language as` clause. The `set default dictionary language` statement affects only the dictionary CustomFirstNames\_WesternEurope because this dictionary is declared without an explicit `with language as` clause. In contrast, the statement does not affect the dictionary FirstNames\_en\_fr because that dictionary is declared with an explicit `with language as` clause.

```bash
module commonPersonNames; 
set default dictionary language as 'en,fr,it,de,pt,es';
/**
 * Dictionary of top 1000 baby names in the U.S. and France. 
 * Evaluated on English and French text only.
 */
create dictionary FirstNames_en_fr 
from file 'dicts/firstNames.dict'
with language as 'en,fr';

/**
 * Customizable dictionary of first names.
 * Evaluated on English, French, Italian, German, Portuguese, Spanish text.  
 */
create external dictionary CustomFirstNames_WesternEurope
allow_empty true;

export dictionary FirstNames_en_fr;
export dictionary CustomFirstNames_WesternEurope;
```

```bash
module personWesternEurope;

import dictionary FirstNames_en_fr from module commonPersonNames;
import dictionary CustomFirstNames_WesternEurope from module commonPersonNames
               as CustomFirstNames ;


/**
 * Find mentions of common first names. Use a variety of 
 * person first names in various languages, 
 * as well as a customizable external dictionary 
 */
create view FirstName as
extract dictionaries commonPersonNames.FirstNames_en_fr 
                 and CustomFirstNames
                 -- and <... other dictionaries here ...>
        on D.text
        as name
from Document D; 
```

#### Customizing tables

In this example, the `sentiment` module makes available an external table of product names. As with external dictionaries, external tables are initially empty at compile time. They are populated at initialization time. The format of the external table is a .CSV file with a header. The first row is the header and the other rows are the table rows.

Internal tables and external tables can be imported in two ways, with or without local name. As with an internal table, you can create a dictionary from a column of an external table. However, you cannot create a dictionary from an external table that is imported. The work-around for this issue is to create the dictionary in the same module where the external table is defined.

```bash
nickName,formalName
“Canon 1D”,”Cannon EOS 1D”
“Canon 1”,”Cannon EOS 1D”
“Canon 7”,”Canon EOS 7”
```

```bash
module sentiment;
/**
 * A table of products around which we extract sentiment. 
 * 
 * @field nickName common name for the product as it may appear in text
 * @field formalName formal name of the product, used for normalization
 */
create external table Product (nickName Text, formalName Text)
allow_empty false;

/**
 * Dictionary of product nicknames, from the nickName field 
 * of the customizable external table Product.
 */
create dictionary ProductDict 
from table Product 
with entries from nickName;

create view ProductSentiment as 
extract pattern 'I like' (<P.match>)return group 1 as product
from (
extract dictionary 'ProductDict' on D.text as match
from Document D
) P;

/**
 * Products with positive sentiment.
 * @field nickName product mention extracted from the text
 * @field formalName normalized name of the product
 */
create view ProductLikeNormalized as
select S.product as nickName, P.formalName
from ProductSentiment S, Product P
where Equals(GetText(S.product), P.nickName);

export view ProductLikeNormalized;
```

#### Using custom documents and external views

In this example, the module `common` defines the schema of the special view Document, which contains two attributes: `twitterID` of type Integer and `twitterText` of type Text. The module also uses additional metadata about the document, in the form of a set of hash tags that are defined by using the `HashTags` external view.

You can write extractors that operate on documents with additional metadata. There are two types of metadata:

- Scalar-value metadata.

    Use fields in the view Document \(the `require document with columns` statement\). The scope of the statement is the entire module, not just the current AQL file.

- Set-value metadata

    Use external views.

The JSON data collection format \(Hadoop\) is used to represent custom documents and external view content. The existing data collection formats work only with the default Document schema \(text Text, label Text\). The JSON text format is defined by one record per line. Each record contains content for the Document and external views.

The AQL Doc is in the module.info file that documents the entire module.

```bash
module common;

require document with columns 
twitterId Integer and twitterText Text;

/**
 * Hash tags associated with a Twitter message.
 * @field tag string content of the hash tag, without #
 */
create external view HashTags (tag Text)
external_name 'Hashes';

/**
 * Input documents that contain at least one hashtag
 * are treated in a special way during extraction.
 * @field twitterText the Twitter message text
 * @field twitterId the Twitter ID, as integer
 * @field tag tags associated with the twitter message 
 */
create view DocumentWithHashTag as
select D.twitterId, D.twitterText, H.tag  
from Document D, HashTags H;

output view DocumentWithHashTag;
```

```bash
{“twitterId”:12345678, “twitterText”:”Gone fishing”, “ExternalViews”:{“Hashes”:[{“tag”:”halfmoonbay”}]}}
{“twitterId”:23456789, “twitterText”:”Gone surfing”, “ExternalViews”:{“Hashes”:[{“tag”:”usa”}, {“tag”:”santacruz”}]}}
```

### Best practices for developing modules

These best practices present practical advice to improve your modules.

- Document the source code by using AQL Doc comments.

    Document your exported artifacts, the output views, the `Document` view, and the module itself. By using informative AQL Doc comments, you can make sure that your module is consumable by others in the expected fashion.

- Place large dictionaries and tables in separate modules.

    When you place large dictionaries and tables that do not frequently change in separate modules, it results in a decrease in compilation time for the entire extractor.

- Place UDF JAR files in their own separate module and export the functions.
- Build a library of UDFs that you can reuse in other modules to decrease unnecessary redundancy of the files.

- Do not use the `output view` statement when you develop extractor libraries.

    The `output` directive in one module cannot be overridden in another module. If you do not use the output statement in your extractor library, the consumer of the library can choose what types to output.

- Use the `output view … as …` statement when you customize an extractor for different domains.

    The use of this statement ensures that output names are identical across different implementations of the customization.

## AQL files

AQL files are text files that contain text extraction rules that are written in the AQL programming language. A module contains one or more AQL files.

AQL files are a way of managing your AQL artifacts separately from larger dictionary files and reusable UDF files to increase performance and opportunities for modularization. AQL files include:

- **A module statement**

    A module statement indicates the name of the containing module. AQL files must be placed directly under the top-level module directory. The first statement in an AQL file must be `module <module name>` to indicate the name of the containing module. These files are not allowed to be in subdirectories, therefore there can be no submodules.

- **The AQL statements for defining artifacts \(view, dictionary, table, function\)**

    AQL files provide the definition of AQL artifacts by using views, dictionaries, tables, and user-defined functions. The scope of the statement is the entire module, not just the current AQL file.

- **The export statements**

    Export statements expose the artifacts created in the module to other modules. Unless exported, an artifact is private to that specific module. You can use the `import` statement to reuse artifacts from another module.

- **The create external dictionary statements**

    These statements allow you to customize dictionaries without recompiling AQL. The content is generated when the extractor is initialized, and the dictionary remains constant for each document that is annotated.

- **The create external table statements**

    These statements allow you to customize tables without recompiling AQL. The content is generated when the extractor is initialized, and the dictionary remains constant for each document that is annotated.

- **The require document with columns statement**

    This statement defines the necessary columns in the view `Document`.

- **The set default dictionary language statement**

    Defines the default set of languages to use when you compile a dictionary that is defined without the with language clause. When you compile a dictionary, you must tokenize each dictionary in each language.

### Views

A view is a logical statement that defines what to match in your document or how to refine your extraction. Views are the top-level components of an extractor.

A common way that views are used is to define what to match in a document. Views can also be used to select information from previously created views to refine or combine constraints, and to define what to return (as tuples) when the AQL script is run. Views define the tuples, but do not compute them. All of the tuples in a view have the same schema

There are three types of views in AQL:

- Internal views describe content that is computed, if necessary, by the extractor, which is based on the input that is supplied at run time in the special view `Document`, and the external views. To define an internal view, use one of the statements:
  - The `create view` statement
  - The `detag` statement
  - The `select into` statement
- The special view `Document` is the most basic view that captures the input document. The view `Document` is populated at run time with the necessary values for each of the attributes of its schema. By default, the schema of this view is `text: Text, label: Text`, but you can specify a custom schema by using the `require document with columns` statement.
- External views define content that is not explicitly specified at compile time. External views are useful if you want to inject metadata about the input document that cannot be captured by using the `Document` view. For example, if you want to use metadata that cannot be represented as scalar values. To define an external view, use the `create external view` statement.

The SystemT engine does not compute the content of a view unless you explicitly request it by using the `output view` or `select into` statements. For more information about the `Document` view and external views, see the AQL execution model.
    
Views can be used in the following AQL constructs:

- The `from` clause of a `select` or `extract` statement
- The target of an `extract` statement or `detag` statement
- The `export` or `import` statements to expose the view, and then use the view in another module

The following is a simple example that illustrates how you can define an internal view and specify that it should be computed:

```bash
require document with columns text Text;

create view Phone as
  extract regex /(\d{3})-\d{4}/ 
   on 3 tokens in D.text 
   return 
    group 0 as fullNumber
    and group 1 as areaCode
from Document D;

output view Phone;
```

The first statement specifies that the view `Document` has an attribute that is called `text` of type Text. Consider that the value of the `text` field in the input document is `My number is 555-1234, yours is 500-5678`. The extractor that is specified by this AQL code computes a view `Phone` with the schema `fullNumber: Span, areaCode: Span`. The content consists of the tuples `[beginOffset, endOffset]`. Notice that the span values are shown for clarity in the format `'matched text'`.

|fullNumber|areaCode|
|----------|--------|
|'320-555-1234' \[13-24\]|'320' \[13-16\]|
|'480-500-5678' \[32-43\]|'480' \[32-35\]|

### Dictionaries

A dictionary is a set of terms that is used to identify matching words or phrases in the input text.

There are two types of dictionaries in AQL:

- Internal dictionaries contain content that is fully specified in AQL. Internal dictionaries are compiled and serialized in the compiled representation of AQL code (TAM files). To define an internal dictionary, use the `create dictionary` statement.
- External dictionaries contain content that is not specified in AQL. Instead, the content is supplied when the compiled extractor is run. You can customize extractors for different scenarios with external dictionaries. You can supply different content for the external dictionary for each scenario without needing to recompile the source AQL of your extractor. The content of external views might change for each input document. However, the content of external dictionaries remains the same throughout a particular instantiation of the extractor. External dictionaries can be defined directly by using the `create external dictionary` statement, or indirectly by using the `create dictionary from table` statement if the table is an external table.

Dictionaries can be used in the following AQL constructs:

- The `extract dictionary` statement to identify all matches of dictionary entries in the input
- The MatchesDict() and ContainsDict() predicates to test if the input precisely matches or contains a match for one of the dictionary terms
- The `export` and `import` statements to expose the dictionary and then use the dictionary in another module

### Tables

A table is a static set of tuples in a file that contains the terms that you want to use in your extractor.

The content of views can differ for every input document that is processed by the extractor, but the content of a table is static and remains unchanged for the lifetime of an extractor. You can use internal and external tables when you develop your module. There are two types of tables in AQL:

- Internal tables have content that is fully specified in AQL, and the content is compiled and serialized in the compiled representation of AQL code (TAM files). To define an internal table, use the `create table` statement
- External tables have content that is not specified in AQL. Instead, the content is supplied when the compiled extractor is run. Extractors can be customized for different scenarios with external tables. You can supply different content for the external table for each scenario without recompiling the source AQL of your extractor. Unlike external views, whose content can change for each input document, the content of external tables remains the same throughout an instantiation of the extractor. External tables can be defined by using the `create external table` statement.

Tables can be used in the following AQL constructs:

- The `from` clause of a `select` or `extract` statement.
- The `create dictionary from table` statement.
- The `export` and `import` statements to expose the table and then run the table in another module.

### Functions

A user-defined function (UDF) specifies custom functions that you can use in your extraction rules.

AQL has a collection of built-in functions and predicates that you can use in extraction rules. If the built-in functions and predicates are not sufficient for your extraction task, you can define your own user-defined function (UDF) in AQL by using the `create function` statement. AQL supports scalar UDFs and table UDFs that are implemented in Java. A scalar UDF outputs a single scalar value, whereas a table UDF outputs a multiset of tuples.

UDFs can be used in the following AQL constructs:

- Scalar UDFs can be used in all constructs where built-in functions can be used, such as the `where` clause of a `select` statement and the `having` clause of the `extract` statement.
- Table UDFs can be used in the `from` clause of a `select` or `extract` statement.
- The `export` and `import` statements to expose the function, and then use the function in another module. For more information about how to build libraries of user-defined functions, see Scenarios that illustrate modules.

For more information about how to define, implement, and use UDFs, see the user-defined functions topics in the AQL reference.
