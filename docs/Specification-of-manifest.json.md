---
layout: page
title: Specification of manifest.json
nav_order: 3
parent: Reference
grand_parent: In depth rule-based modeling techniques
description: Specifying a JSON formatted configuration file for SystemT
---

# Specification of manifest.json
{:.no_toc}

* Table of Contents
{:toc}

## Overview

`manifest.json` is a json-format configuration file for [SystemT High-level Java API](./10-minutes-to-systemt/10-Minutes-to-SystemT-(Java-API).html#using-the-high-level-java-api). This format specifies all the artifacts that make up a SystemT extractor including:

- Location of compiled TAM files to execute, or optionally, location of AQL source modules to compile (if any) and then execute
- Location of external dictionaries and tables required by the extractor
- Modules, Input and Output views expected by the extractor
- Other metadata including format for serializing output spans and user-specified metadata for the extractor

Example `manifest.json` can be found in [Create configuration](./10-minutes-to-systemt/10-Minutes-to-SystemT-(Java-API)#create-configuration-file).

## Format

The format of the `manifest.json` is as follows:

```json
{
  "annotator": {
    "version": JSON string,
    "key": JSON string
  },
  "annotatorRuntime": "SystemT",
  "version": "1.0",
  "acceptedContentTypes": JSON array of string,
  "serializeAnnotatorInfo": JSON boolean,
  "location": JSON string,
  "serializeSpan": JSON string, 
  "tokenizer": JSON string,
  "modulePath": JSON array of string,
  "moduleNames": JSON array of string,
  "sourceModules": JSON array of string,
  "inputTypes": JSON array of string,
  "outputTypes": JSON array of string,
  "externalDictionaries": JSON record of string key/value pairs,
  "externalTables": JSON record of string key/value pairs
}
```

The semantics of each field, whether it is mandatory, and the allowed values are explained below.

| Field Name                 | Type                                  | Mandatory | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| :------------------------- | :------------------------------------ | :-------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `version`                | JSON string                           | Y         | The version of the configuration format. Supported values: `1.0`.                                                                                                                                                                                                            |
| `annotator`              | JSON record                           | Y         | User-specified annotator key and version, for informational purposes only; the format is a record with two fields: `key` and `version`.                                                                                                                                                                                                                                                                                                                              |
| `annotator.key`          | JSON string                           | Y         | User-specified key for the annotator, for informational purposes only; recommend assignment using `name::guid` format, but you could use any human-readable description that describes your extractor. For example, _"Extractor for rule-based implementation of PII entity types SSN, BankAccountNumber and PhoneNumber"_.                                                                                                                                                                                                                                                                                                                                                                                                                |
| `annotator.version`      | JSON string                           | Y         | User-specified version for the annotator, for informational purposes only; recommend `CCYY-MM-DDThh:mm:ssZ` format, but you can use any other version that is suitable for your workflow. For example, you could use the version number of the project where you develop the extractor.                                                                                                                                                                                                                                                                                                                                                                                                                |
| `annotatorRuntime`       | JSON string                           | Y         | Name of the field in the Annotator Module Configuration JSON record that indicates the text analytics runtime for executing this annotator; supported values: `SystemT`.                                                                                                                                                                                                                                                                                                           |
| `acceptedContentTypes`   | JSON array of string                  | Y         | Name of the field in the Annotator Module Configuration JSON record that indicates the types of input text accepted by this annotator; possible values are: `text/plain` if the annotator supports only plain text, and `text/html` if the annotator supports only HTML text; specify both `text/plain` and `text/html` if the annotator supports both kinds of document text.                                                                                                         |
| `inputTypes`             | JSON array of string                  | N         | Name of the field in the Annotator Module Configuration JSON record that indicates the input types expected by this annotator; null if no input type is expected. These are used to populate `external view` constructs in AQL.                                                                                                                                                                                                                                                                                                                                 |
| `outputTypes`            | JSON Array of String                  | Y         | Name of the field in the Annotator Module Configuration JSON record that indicates the output types produced by this annotator. Names should coincide with the names exposed by the `output view` AQL statements. If the extractor outputs view `X` from module `M` using AQL statement `output view X;` then output type name `M.X` should be used. If `output view X as 'Y';`  is used, output type name `Y` should be used.                                                                                                                                                                                                                                                                                                                                                      |
| `serializeAnnotatorInfo` | JSON boolean                          | N         | Whether to serialize the annotator info (including the key and the version) inside each output annotation; if true, the value of the `annotator` parameter is copied to each output annotation; if not specified, the default is false (no serialization).                                                                                                                                                                                                                          |
| `location`               | JSON string                           | Y         | Absolute path (on local file system, or a distributed file system) where all artifacts required by the annotator (including compiled modules, external dictionary files, and external table files) are located; relative paths are also supported, and they are resolved with respect to the root directory where the SystemT Java API or Python binding is executed from.                                                                                                                                                                                                                                                                                                            |
| `serializeSpan`          | JSON string                           | N         | How to serialize the AQL `Span` type. Possible values: `simple` and `locationAndText`.  If unspecified, the default is `simple`. In `simple` mode, spans are serialized in the format `{"begin": Integer, "end": Integer}`. In `locationAndText` mode, spans are serialized in the format `{"text": String, "location": {"begin": Integer, "end": Integer}}`. This latter mode coincides with the way spans are represented in Watson NLU and Discovery APIs. Here, `begin` and `end` are character offsets of the span in the input document (if the input was `text/html` the spans are over the input `html` text, and not the plain text that may have been computed internally by the extractor). Whereas `text` is the text covered by the span. If the input text is `text/plain`, the covered text is akin to `inputText.substring(begin, end)`, whereas if the input text is `text/html`, the computation of the covered text depends on how the developer computed the span in AQL: if the span was over the detagged document (after using the AQL `detag` statement) and the developer used the AQL `Remap()` function, the covered text is a substring of the input `html` text. If the developer did not use the `Remap()` function, the covered text is over the detagged text, so not a substring of the input `html` text, although the `begin` and `end` character offsets are always over the input text, in this case, the `html` text.                                                                                                                                                                                                                                                                                                     |
| `moduleNames`            | JSON array of string                  | Y         | The names of modules to instantiate for this annotator as a JSON Array of Strings, where each string is a module name. You can specify only the top-level modules you want to run; any other dependent modules are automatically searched in the `modulePath`.                                                                                                                                                                                                                                                                                                                                                                 |
| `modulePath`             | JSON array of string                  | N(\*1)    | The list of the module path for this annotator as a JSON array of String values, where each String indicates a path relative to the value of **location** of a directory or jar/zip archive containing compiled SystemT modules (.tam files).  This field enables you to execute pre-compiled models (`.tam` files).  If you want to run AQL models, please configure the `sourceModules` field. |
| `sourceModules`          | JSON array of string                  | N(\*1)    | The list of AQL source modules for this annotator as a JSON array of String values, where each String points to a source AQL module directory, the path being relative to the value of `location` of a directory containing aql files.  This field enables you to execute AQL models (`.aql` files).  If you'd like to run pre-compiled models, please configure the `modulePath` field. |
| `externalDictionaries`   | JSON record of string key/value pairs | N         | The external dictionaries (`create external dictionary` AQL statement) used by this annotator, as a JSON array of key value pairs, where the key is the AQL external dictionary name and the value is the path to the dictionary file, relative to the value of `location`.                                                                                                                                                                                                                                                       |
| `externalTables`         | JSON record of string key/value pairs | N         | The external tables (`create external dictionary` AQL statement) used by this annotator, as a JSON array of key value pairs, where the key is the AQL external table name and the value is the path to the CSV file with the table entries, relative to the value of `location`.                                                                                                                                                                                                                                                  |
| `tokenizer`              | JSON string                           | Y         | The type of tokenizer used by the SystemT annotator; possible values: `standard` to use the whitespace and punctuation-based tokenizer    | 

*1) Either `modulePath` or `sourceModules` field is required.  
