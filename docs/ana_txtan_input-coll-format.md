---
providerName: IBM
layout: page
title: Data formats for DocReader API 
nav_order: 4
parent: Reference
grand_parent: In depth rule-based modeling techniques
description: Data formats for DocReader API 
---

# Data collection formats




The DocReader API from SystemT low-level Java API supports specific formats to represent a data collection.
A data collection is a document or a set of documents from which you want to extract text or discover patterns.

**Restriction:** All input documents must be UTF-8 encoded files, or the analysis can produce unexpected results. Also, while the data collections must contain text files, the content of these text files is not restricted to plain text. Non-binary content such as HTML or XML is also accepted.

A data collection must be in one of the following formats:

* Table of Contents
{:toc}

## UTF-8 encoded text files

A data collection in this format can consist of a single UTF-8 encoded file, or a set of UTF-8 encoded text files, that are contained in a directory, archive, or a single comma-delimited file.

### A single text file

A UTF-8 encoded single text file must have a .txt, .htm, .html, .xhtml, or .xml file extension. For a text file, the DocReader.next\(\) API create one Document tuple with the schema as \(text Text\) or \(text Text, label Text\) based on the document schema of the loaded extractor. The value for the `text` field is the content of the file, and the value of the label field is the name of the file.

### Text files in a directory

A data collection format can be a directory that contains UTF-8 encoded text files. For each file in the directory, the DocReader.next\(\) API create one Document tuple with the schema as \(text Text\)or \(text Text, label Text\) based on the document schema of the loaded extractor. The value of the `text` field is the content of the file, and the value of the `label` field is the name of the file.

### Text files in an archive

A data collection can be an archive file in a .zip, .tar, .tar.gz, or .tgz archive format that contains UTF-8 encoded text files. For each file in the archive file, the DocReader.next\(\) API create one Document tuple with the schema as \(text Text\) or \(text Text, label Text\) based on the document schema of the loaded extractor. The value for the `text` field is the content of the file, and the value of the `label` field is the name of the file.

### A single comma-delimited file

A UTF-8 encoded comma-delimited file \(.del\) must be in the following format:

- Each row represents a single document. Rows are separated by new lines.
- Each row has three comma-separated columns that represent the identifier \(of type Integer\), label \(of type String\), and text \(of type String\).
- The label and text string values must be surrounded by the character string delimiter, which is a double quotation mark \("\). Any double quotation marks inside these values must be escaped \(""\).
- The document label can be any character string \(for example, a file name\) which must be unique.
- For each row in the .del file, the DocReader.next\(\) API create one Document tuple with the schema as \(text Text\) or \(text Text, label Text\) based on the document schema of the loaded extractor. The value for the `text` field is the value of the third column of the row, and the value of the `label` field is the value of the second column of the row.

The following is an example of a .del file in this format that contains two documents with labels, doc\_1.txt and doc\_2.txt.

```bash
1,"doc_1.txt","This is an example document."
2,"doc_2.txt","This is an example document with ""double quotation marks"" that need to be escaped"
```

## UTF-8 encoded CSV files

A data collection in this format can consist of a single UTF-8 encoded comma-delimited file with a header row.

### A single comma-delimited with header file

A UTF-8 encoded comma-delimited file with header \(.csv\) must be in the following format:

- The first line of the file must be a header row that has comma-separated columns that represent the header or column-name, which specify the column field names.
- Columns can be of the data type Integer, Float, Text, or Boolean.
- Data of type Text must be surrounded by a double quotation mark \("\). In the following example of a CSV file, the field `First_name` is of type Text, so all of its values are enclosed in double quotation marks \(such as "Rohan"\).
- For each row in the CSV file, the DocReader.next\(\) API create one Document tuple with the schema according to the document schema of the loaded extractor.
- You must specify the document schema in the AQL script by using the require document with columns statement. Headers that are not defined in the statement are ignored. The fields do not need to be specified in the order that they appear in the document.

The following is an example of a CSV file in this format:

```bash
First_name, Last_name, Id, Avg_score, Passed, Remarks
“Rajeshwar”, “Kalakuntla”, 101, 70.96, true, ”Pass – First class”
“Jayatheerthan”, “Krishna”, 102, 85.96, true, ”Pass - Distinction”
“Nisanth”, “Simon”, 103, 80.96, true, ” Pass - Distinction”
“Rahul”, “Kumar”, 104, 49.96, false, ”Failed”
“Rohan”, “Reddy”, 105, 59.96, true, ”Pass – Second class”
```

The first row of the CSV file is the header and it contains six columns: `First_name`, `Last_name`, `Id`, `Avg_score`, `Passed`, and `Remarks`.The remaining data is the CSV content.

The following require document with columns statement establishes the column header labels and data types:

```bash
require document with columns
        First_name Text and
        Last_name  Text and
        Id         Integer and
        Avg_score  Float and
        Passed     Boolean;
```

Notice that the `Remarks` column is not used in the following statement, so the AQL will not have access to the `Remarks` column.

## UTF-8 encoded JSON files in Hadoop text input format

A data collection in this format consists of a single UTF-8 encoded JSON \(.json\) file in Hadoop text input format.

In this format, there is one JSON record per line. Each JSON record encodes the fields of one input document, and optionally, the external view content that is associated with that document.

The document record is divided into multiple fields corresponding to a document schema \(the structured format of the input document\). 
Unlike the other data collection formats, which require a schema of \(text Text, label Text\), the JSON format allows the parsing of documents with a custom schema. 
The DocReader API aligns the schema that is read from the input JSON record with the expected input schema for the extractor. The expected input schema is the union of the schemas of its component modules \(defined 
by the `require document with columns` statement of an AQL file\) in that extractor. 
For example, if one module requires a document schema of \(text Text\) and another module requires a document schema of \(id Integer\), 
an extractor that is derived from both modules requires a document schema \(text Text, id Integer\).

It is expected that the field names read from the JSON file are equal to the names of the extractor schema, and that the field types read from the JSON file are compatible with the types of the extractor schema.
If the JSON input does not contain a required document field, an exception occurs. If the JSON input contains extraneous fields that are not specified by the extractor schema, the fields are ignored.

In addition to the document schema fields, the document record can include a special field named ExternalViews. This field is a record where the name of each element is the external name of the external view \(declared in AQL as external\_name\). The value of that element is an array of records. Each record corresponds to a tuple of an external view. As with the document schema, the field names and field types of each external view tuple must correspond with their defined schema in AQL. When a JSON input data file contains documents with `ExternalViews` fields, every document in that JSON data file must contain a field named `ExternalViews`, whether r not this field is populated with some external view content for that document. The following table shows the correspondence between JSON types and AQL types:

|AQL type|JSON type|
|--------|---------|
|Text|String|
|Integer|Long|
|Float|Double|
|Boolean|Boolean|
|String|String|
|Scalar list|Array|
|Span|Record of the form \{begin: Long, end: Long\} or \{begin: Long, end: Long, docref: String\}|

A value of AQL type Span is represented as a JSON record with the following fields:

- **begin** of type JSON Long represents the begin offset of the span value. This field is mandatory.
- **end** of type JSON Long represents the end offset of the span value. This field is mandatory.
- **docref** of type JSON String represents the field of type Text in the view **Document** that holds the Text value that the span’s begin and end offsets refer to. This attribute is optional. If missing, the span is assumed to be over the field **text** of the view **Document**.

# Examples

**Requirement:** The example data is formatted for readability, but each JSON record must be entered on a single line.

## Example 1: Extractor with a custom document schema

In this example, you see a code snippet of a single module with a required Document schema of four fields, one field for each of the four AQL data types that are supported by the `require document with columns` statement.

```bash
module baseball;

require document 
    with columns avg Float 
             and hr Integer 
             and mvp Boolean 
             and name Text;
```

The following example shows a JSON file that consists of three input documents that are suitable for use as input documents for the extractor. The example data is formatted for readability, but each JSON record must be on a single line. No external view contents are present in any of the three documents. The rbi field in the first document is not required by the document schema, and is therefore ignored.

```bash
{"avg":0.357,"hr":40,"mvp":true,"rbi":139,"name":"Cabrera, Miguel"}
{"avg":0.326,"hr":30,"mvp":false,"name":"Trout, Mike"}
{"avg":0.219,"hr":1,"mvp":false,"name":"Punto, Nick"}
```

## Example 2: Extractor that uses multiple modules with external views

This example shows code snippets from various modules.

1. Module email has Document schema \(text Text\) and an external view, EmailMetadata, with schema \(toAddress Text, title Text\) and external name, EmailMetadataSrc.

```bash
module email;

require document with columns text Text;

create external view EmailMetadata(toAddress Text, title Text)
external_name 'EmailMetadataSrc';
```

2. Module spam has Document schema \(label Text\) and one external view, SpamMetadata with schema \(spamChance Float\) and external name, SpamMetadataSrc.

```bash
module spam;

require document with columns label Text;

create external view SpamMetadata(spamChance Float)
external_name 'SpamMetadataSrc';
```

The following example shows a JSON file with a single document record that can be used as input to an extractor that combines both modules. This extractor has a unionized schema \(label Text, text Text\), and access to both external views. Notice the usage of external names of external views in the ExternalViews record.

```bash
{ "label": "email.txt", "text": "Send this to 20 other people!", "ExternalViews": { "EmailMetadataSrc": [ { "toAddress":"John Doe", "title":"Make Money Fast" }, { "toAddress":"Jane Smith", "title":"Make Money Fast" } ], "SpamMetadataSrc": [ { "spamChance":.999 } ] }}
```

## Example 3: Extractor that uses a module with external views but not all of the external views are populated

The JSON input in this example contains documents with ExternalViews defined in each record, but the second record is not populated with external view content.

```bash
module whitehouse;
    
require document with columns
      id Integer
      and label Text
      and text Text
      and url Text
      and timeStamp Text;
    
create external view EmailMetadata (fromAddress Text, toAddress Text, msgid Integer) 
external_name 'EmailMetadataSrc1';
    
output view EmailMetadata;
    
create view dumpDocument as
select D.id, D.label, D.text, D.url, D.timeStamp
from Document D;
    
output view dumpDocument;
```

```bash
{"id": 123, "label": "dem.whitehouse.txt", "text": "Yes we can!", "url": "www.whitehouse.gov", "timeStamp": "03:00", "ExternalViews": { "EmailMetadataSrc1": [{"fromAddress": "Barack Obama","toAddress": "Hillary Clinton","msgid": 2012}]}}
{"id": 125,"label": "wh2.txt","text": "DON'T PRESS IT!","url": "www.whitehouse.org","timeStamp": "22:01","ExternalViews": {"EmailMetadataSrc1": []}}
```

## Example 4: External views with an attribute of type Span

In this example, you see a code snippet of a single module with a required Document schema with two fields, and an external view with a single field of type Span.

```bash
module baseball;

require document 
        with columns text Text 
                 and url Text;
    
create external view ExternalSpans (annotation Span)
external_name 'ExternalSpansSrc';
```

The following example shows a JSON file that consists of one input document, with two tuples in the external view.
 The span in the first tuple has all three fields, including the optional field **docref**:`{"begin":4, "end":11, "docref":"url"}`
 and represents the span \[4-11\] on the text “www.ibm.com” which is the value of the **url** field of the view 
 **Document**, spanning the text “ibm.com”. Notice that the span value in the second tuple `{"begin":0, "end":4}` 
 does not have the optional docref attribute. This represents the span \[0-4\] on the text “Some news” 
 which is the value of the **text** field of the view **Document**, therefore spanning the text “Some”.

```bash
{"text":"Some news","url":"www.ibm.com","ExternalViews":{"ExternalSpansSrc":[{"annotation":{"begin":4, "end":11, "docref":"url"}},{"annotation":{"begin":0,"end":4}}]}
```
