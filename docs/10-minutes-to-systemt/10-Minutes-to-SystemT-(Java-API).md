---
layout: page
title: 10 Minutes to SystemT (Java-API)
parent: Guides
grand_parent: In depth rule-based modeling techniques
nav_order: 5
description: Add SystemT (Java API) to your project in 10 minutes
---

# 10 Minutes to SystemT (Java-API)


* Table of Contents
{:toc}

## Add SystemT to Your Project

### Prerequisites

- Java Development Kit 8, 11, 17

### Installation

#### Add Dependency to SystemT

Add dependency settings to `rbr-annotation-service-core` in your project's `pom.xml` file. 

## Analyze Text

### Create AQL Code

We show a very simple AQL code to extract mentions of programming languages and their versions, such as `Python version 3.7` and `Java 8`. 

This example is very simplistic. AQL can express much more than we can cover in a simple Hello World example. To learn AQL in depth, see [Learning AQL](../Learning-AQL).

This is the directory structure and files we will create:

```sh
demoAqlModule
\
---main.aql
```

AQL code is organized in modules. AQL modules are similar to Java packages, or Python libraries. An AQL module can import concepts from other AQL modules, and can export concepts to be consumed in other AQL modules. 

We first create an AQL module called `demoAqlModule`. An AQL module is a directory. The name of the directory is the name of the AQL module. The AQL code lives in `.aql` files inside the module directory. 

For more details, see [AQL modules reference](../aql-guidelines.md) and [AQL Reference](../aql-ref-guide.md).

Create a new file, called `main.aql` in the module directory. Declare the module's name (same as the module directory name).

```sh
module demoAqlModule;
```

Declare a dictionary containing common programming language names. Also, define the matching semantics for the dictionary (in this case, the matching is case insensitive). Dictionaries can be declared inline, of from files or tables, and can have various matching semantics. 

```sh
create dictionary ProgrammingLanguages_Dict 
   with language as 'en' and case insensitive
   as ('AQL', 'C++', 'Java', 'JavaScript', 'Objective C', 'Python', 'PhP');
```

Extract mentions of those names in the input text. The extraction happens on an object `Document.text`, which by default, represents the input text in AQL.

```sh
create view ProgrammingLanguageName as
  extract dictionary ProgrammingLanguage_Dict on D.text 
        as name
  from Document D;
```

Use a regular expression to find spans that look like version numbers.

```sh
create view VersionNumber as
  extract regex /[Vv]?\d{1,3}(\.\d{1,2}){0,3}/ on D.text
          as version
  from Document D;
```

We can now use an AQL pattern to combine our building blocks, `ProgrammingLanguageName` and `VersionNumber`, into a larger concept. We declare a pattern to extract mentions of programming languages, optionally followed within 0 to 1 tokens by a version number. Patterns are powerful AQL constructs to match regular expressions over tokens and annotations.

```sh
create view ProgrammingLanguageWithVersion as
    extract pattern (<P.name>) ( ('version'|'v'|'v.')? (<V.version>) )?
            return group 0 as fullMatch and
                   group 1 as name and 
                   group 4 as version
            with inline_match on Document.text
    from ProgrammingLanguageName P, VersionNumber V
    consolidate on fullMatch using 'LeftToRight';
```

Since the pattern has optional components, it can match overlapping portions of the text. For example, in the text `I use Python 3.7`, the pattern matches both `Python 3.7` and `Python` (with and without the version number). Therefore, we use the `consolidate` clause to resolve these overlapping matches. There are many consolidation policies available in AQL, see [AQL Reference](../aql-ref-guide.md) for details.

Finally, we must declare which of the AQL concepts to output. The SystemT runtime engine saves CPU time by not executing any AQL code that does not contribute to an output.

```sh
output view ProgrammingLanguageWithVersion as 'ProgrammingLanguageWithVersion';
```

You're done creating your first AQL code! But, this is just the beginning. If you want to harness the entire expressive power of AQL, check out [Learning AQL](../Learning-AQL).

### Java API: Low-level vs. High-level

SystemT has two Java APIs:

1. The low-level Java API allows compiling and executing AQL code. The input and output are low-level Java objects specific to SystemT. In addition, the API provides access to functionality such as querying compiled AQL modules for their input and output schema, and profiling AQL code at runtime. Use this API if you are building an application that requires access to all these functionalitites. For example, the Watson Knowledge Studio (WKS) uses the low-level Java API to generate AQL code, and compile it on the fly in the rule editor.
1. The high-level Java API is a simple interface around the most common functionality: compiling and executing compiled AQL code. The API also hides low-level SystemT data objects as Jackson JSON objects for ease of consumption. This API is further wrapped and exposed in SystemT's Python binding.

### Using the Low-level Java API

Use this API to compile AQL code programmatically, instantiate a set of compiled AQL modules, and execute them on input documents.
In addition, this API also provides access to other methods such as querying a set of compiled modules for their input and output schema, and a sample-based AQL Runtime Profiler. See [API Reference](../API-Reference.md) for details of each method.

#### Compile AQL Code into Text Analytics Modules (TAM) files

The following shows how to use the SystemT low-level Java API to compile AQL source code into Text Analytics Modules (.tam) files.

```java
// Set up compilation parameters
CompileAQLParams params = new CompileAQLParams ();

// The names of source AQL modules to compile
String[] sourceModuleNames = new String[] {"demoAqlModule"};
String sourceModuleUri = new File("./src/main/aql/demoAqlModule").toURI().toString();
String[] sourceModuleUris = new String[] { sourceModuleUri};
params.setInputModules (sourceModuleUris);

// The module path, only if your source AQL uses an AQL library (other compiled AQL modules)
params.setModulePath (null);
    
// Set up the output directory where source AQL modules get compiled into .tam files
String compiledTamUri = new File ("./target").toURI ().toString () ;
params.setOutputURI (compiledTamUri);

// Compile AQL modules to .tam files
CompileAQL.compile (params);
```

#### Instantiate Extractor from TAM Files

The following shows how to use the SystemT low-level Java API to instantiate an extractor, known as `OperatorGraph` object, from Text Analytics Modules (.tam) files.

```java
OperatorGraph og = OperatorGraph.createOG (sourceModuleNames, compiledTamUri, null, null);
```

#### Execute Extractor

The following code takes English text and writes analysis result to console. See [API Reference](../API-Reference.md) for details of each method.

```java
// Assume the document schema expected by all extractors is (label text, text Text);
TupleSchema docSchema = og.getDocumentSchema ();
TextSetter textSetter = docSchema.textSetter (Constants.DOCTEXT_COL);

// Make a document tuple out of the input text
Tuple docTup = docSchema.createTup ();
String inputStr = "I like implementing NLP models in AQL. I can execute AQL from Java 8 and Python 3.7.";
textSetter.setVal (docTup, new Text (inputStr, LangCode.en));

// Run the extractor
Map<String, TupleList> result = og.execute (docTup, null, null);
```

#### Get Result

The analysis result is stored in `TupleList` objects. The schema of the tuples in each tuple list, including the names and types of each attribute in the tuple list, along with all tuples, and attribute values can be accessed using the low-level Java API. For details see [API Reference](../API-Reference.md).

```java
// Print the extractor results
for (String viewName : result.keySet ()) {
	// All tuples of the output view
	TupleList tups = result.get (viewName);

	System.out.printf ("Output View %s:\n", viewName);

	// The schema of the output view
	AbstractTupleSchema schema = tups.getSchema ();

	// Iterate through the tuples of the output view
	TLIter itr = tups.iterator ();
	while (itr.hasNext ()) {
		Tuple tup = itr.next ();
		System.out.printf ("\n   %s\n", tup);

		// Create and use accessor objects -- do this ONCE, ahead of time.
		// The accessor objects should be created ONCE, ahead of time. The accessors can be
		// reused subsequently to access values of all tuples of this output view, across all input documents      
		for (int fieldIx = 0; fieldIx < schema.size (); fieldIx++) {

			// Obtain the field name from the view schema
			String fieldName = schema.getFieldNameByIx (fieldIx);

			// Use a Span accessor to access fields of type Span.
			if (schema.getFieldTypeByIx (fieldIx).getIsSpan ()) {
				FieldGetter<Span> accessor = schema.spanAcc (fieldName);

				// Using the accessor to get the field value
				Span span = accessor.getVal (tup);
				if (null == span)
					System.out.printf ("    %s: %s\n", fieldName, null);
				else
					System.out.printf ("    %s: %s, beginOffset: %d, endOffset: %d\n", fieldName, span.getText (), span.getBegin(), span.getEnd());
			}
			// Use an Integer accessor to access fields of type Integer
			else if (schema.getFieldTypeByIx (fieldIx).getIsIntegerType ()) {
				FieldGetter<Integer> accessor = schema.intAcc (fieldName);

				// Using the accessor to get the field value
				int intVal = accessor.getVal (tup);
				System.out.printf ("    %s: %d\n", fieldName, intVal);
			}
			// Similarly, we have accessors for other scalar data types in AQL, such as 
			// Text, Float, Boolean and ScalarList
		}
	}
}
```

For the following input text:

```curl
I like implementing NLP models in AQL. I can execute AQL from Java 8 and Python 3.7.
```

The above code will print to console:

```sh
Output View ProgrammingLanguageWithVersion:

   [[34-37]: 'AQL', [34-37]: 'AQL', NULL(3 fields)]
    fullMatch: AQL, beginOffset: 34, endOffset: 37
    name: AQL, beginOffset: 34, endOffset: 37
    version: null

   [[53-56]: 'AQL', [53-56]: 'AQL', NULL(3 fields)]
    fullMatch: AQL, beginOffset: 53, endOffset: 56
    name: AQL, beginOffset: 53, endOffset: 56
    version: null

   [[62-68]: 'Java 8', [62-66]: 'Java', [67-68]: '8'(3 fields)]
    fullMatch: Java 8, beginOffset: 62, endOffset: 68
    name: Java, beginOffset: 62, endOffset: 66
    version: 8, beginOffset: 67, endOffset: 68

   [[73-83]: 'Python 3.7', [73-79]: 'Python', [80-83]: '3.7'(3 fields)]
    fullMatch: Python 3.7, beginOffset: 73, endOffset: 83
    name: Python, beginOffset: 73, endOffset: 79
    version: 3.7, beginOffset: 80, endOffset: 83
```

#### Using external dictionaries and tables

When you are creating the operator graph, you can also pass on the content of the external dictionaries and the external tables that are required by the loaded compiled modules by using the com.ibm.avatar.api.ExternalTypeInfo API.

The following example shows how to load modules and pass the content of external dictionaries by using the OperatorGraph.createOG\(\) API:

```java
// URI to the location where the compiled modules should be stored
String COMPILED_MODULES_PATH = new File ("textAnalytics/bin").toURI ().toString ();
    
// Name of the AQL modules to be loaded
String[] TEXTANALYTICS_MODULES = new String[] { "main", "metricsIndicator_dictionaries",
      "metricsIndicator_externalTypes", "metricsIndicator_features", "metricsIndicator_udfs" };
    
// Create an instance of tokenizer
TokenizerConfig TOKENIZER = new TokenizerConfig.Standard ();
    
// Create an empty instance of the container used to pass in actual content of external dictionaries and
// external tables to the loader
ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance ();
    
// Qualified name of the external dictionary 'abbreviations' declared in the module 'metricsIndicator_dictionaries'
// through the 'create external dictionary...' statement
String EXTERNAL_DICT_NAME = "metricsIndicator_dictionaries.abbreviations";
    
// URI pointing to the file abbreviations.dict containing entries for 'abbreviations' external dictionary
String EXTERNAL_DICT_URI = new File ("resource/dictionaries", "abbreviations.dict").toURI ().toString ();
    
// Populate the empty ExternalTypeInfo object with entries for 'abbreviations' dictionary
externalTypeInfo.addDictionary (EXTERNAL_DICT_NAME, EXTERNAL_DICT_URI);
    
// Similarly, populate the content of external tables into ExternalTypeInfo object
// using the ExternalTypeInfo.addTable() API
    
// Instantiate the OperatorGraph object
OperatorGraph extractor = OperatorGraph.createOG (TEXTANALYTICS_MODULES, COMPILED_MODULES_PATH, externalTypeInfo, TOKENIZER);
```


#### Using external views

If your extractor contains external views, you must first prepare the content in a format as specified in file formats for external artifacts and the create external view statement. 
Then, pass the extractor as one of the input arguments to the execute API.
The execute\(\) method then annotates the document and returns the extraction results.

**Note:** The content for external views that are defined inside an extractor is optional. 
The execute\(\) method will not return an error if the content for one or more external views is not provided for annotating input documents.

The following example shows how to populate external views while annotating documents:

```java
// Load Operator Graph
OperatorGraph extractor = OperatorGraph.createOG (TEXTANALYTICS_MODULES, COMPILED_MODULES_PATH, externalTypeInfo,
      TOKENIZER);
    
// Directory containing the collection of documents to extract information from, in one of the supported input
// collection formats.
File INPUT_DATA_COLLECTION = new File ("data/ibmQuarterlyReports");
    
// Open a reader over input document set.
DocReader inputDataReader = new DocReader (INPUT_DATA_COLLECTION);
    
// Qualified name of the external view as it is defined in a "create external view" statement of your extractor.
// In this code snippet we assume the external view has been declared using the following AQL statement:
// create external view MyExternalView(stringField Text, integerField Integer)
// external_name MyExternalView_ExternalName;
String EXTERNAL_VIEW_NAME = "ModuleName.MyExternalView";
    
// Obtain the schema of the external view from the loaded OperatorGraph
TupleSchema externalViewSchema = extractor.getExternalViewSchema (EXTERNAL_VIEW_NAME);
    
// Prepare accessor objects to get and set values for different fields from and in a tuple.
// In this example, the first column of the view is of type text,
// and the second column is of type integer.
    
// Setter for the text field
TextSetter textSetter = externalViewSchema.textSetter (externalViewSchema.getFieldNameByIx (0));
    
// Setter for the integer field
FieldSetter<Integer> intSetter = externalViewSchema.intSetter (externalViewSchema.getFieldNameByIx (1));
    
// Similarly you can create setter for fields with other data types
// like float,span ..etc
    
// Prepare a Tuplelist with two tuples { {"text1",1}, {"test2",2} }
TupleList externalViewTups = new TupleList (externalViewSchema);
Tuple externalViewTup;
    
// Prepare two external view tuples
externalViewTup = externalViewSchema.createTup ();
textSetter.setVal (externalViewTup, "text1");
intSetter.setVal (externalViewTup, 1);
externalViewTups.add (externalViewTup);
externalViewTup = externalViewSchema.createTup ();
textSetter.setVal (externalViewTup, "text2");
intSetter.setVal (externalViewTup, 2);
externalViewTups.add (externalViewTup);
    
// Process the documents one at a time.
System.err.println ("Executing SystemT ...");
int ndoc = 0;
while (inputDataReader.hasNext ()) {
    Tuple doc = inputDataReader.next ();
    
    // Prepare the content of the external view in a map where the
    // key represents an external view name, and the value is the list of tuples of that external view
    Map<String, TupleList> extViewTupMap = new HashMap<String, TupleList> ();
    extViewTupMap.put (EXTERNAL_VIEW_NAME, externalViewTups);
    
    // Annotate the current document, generating every single output
    // type that the extractor produces.
    // The second argument is an optional list of what output types to generate; null means "return all types"
    // The third argument is the content of the external views
    Map<String, TupleList> results = extractor.execute (doc, null, extViewTupMap);
}
```

#### Query the extractor schema and other metadata

Class OperatorGraph provides APIs to query a loaded operator graph about its input document schema, and to list all the output types and their schema. The following example illustrates the use of these APIs:

```java
// Load Operator Graph
OperatorGraph extractor = OperatorGraph.createOG (TEXTANALYTICS_MODULES, COMPILED_MODULES_PATH, externalTypeInfo, TOKENIZER);
        
// Schema of the document expected by the loaded Operator Graph
TupleSchema inputDocumentSchema = extractor.getDocumentSchema ();
        
System.out.println ("\n Displaying input document schema of the constructed extractor : ");
System.out.println (inputDocumentSchema.toString ());
        
// Schema for every output view type in this extractor
Map<String, TupleSchema> outputTypesSchema = extractor.getOutputTypeNamesAndSchema ();
        
for (String outputType : outputTypesSchema.keySet ()) {
    System.out.println ("Output schema for output view type : " + outputType);
    System.out.println (outputTypesSchema.get (outputType));
}
```
        
        
- **ModuleMetadata API**
        
Class ModuleMetadata provides APIs to query the Text Analytics module \(TAM\) about the:

    - schema of the view `Document`
    - list of elements \(views/tables/functions/dictionaries\) exported by the module
    - list of views output by the module
    - schema of the exported or output views
    - external dictionaries, tables and views declared in the module
    - list of other modules that this module depends upon

The following example illustrates how to load the module metadata from a compiled Text Analytics module \(TAM\), and later query the loaded module about its metadata:

```java
// URI to the location where the compiled modules should be stored
String COMPILED_MODULES_PATH = new File ("textAnalytics/bin").toURI ().toString ();
        
// Name of the compiled AQL modules 
String[] TEXTANALYTICS_MODULES = new String[] { "main", "metricsIndicator_dictionaries",
          "metricsIndicator_externalTypes", "metricsIndicator_features", "metricsIndicator_udfs" };
        
// Read metadata for all the modules compiled in Step#1
ModuleMetadata[] modulesMetadata = ModuleMetadataFactory.readMetaData (TEXTANALYTICS_MODULES, COMPILED_MODULES_PATH);
        
// Query metadata of each module to obtain the following:
// 1) Exported views and their schemas
// 2) External dictionaries and external tables
        
for (int metadataIndex = 0; metadataIndex < modulesMetadata.length; ++metadataIndex) {
        
    ModuleMetadata metadata = modulesMetadata[metadataIndex];
    System.out.printf ("\n Displaying metadata for module named '%s': ", metadata.getModuleName ());
        
    // Obtain list of views exported by this module and their schema
    String[] exportedViews = metadata.getExportedViews ();
    for (int exportedviewIndex = 0; exportedviewIndex < exportedViews.length; exportedviewIndex++) {
        // Fetch ViewMetadata for exported view
        ViewMetadata exportedViewMetadata = metadata.getViewMetadata (exportedViews[exportedviewIndex]);
        System.out.printf ("\n Exported view name is '%s' and its schema is '%s'.", exportedViews[exportedviewIndex],
            exportedViewMetadata.getViewSchema ());
    }
        
    // Obtain list of external dictionaries and inquire if it is required to pass in entries for the dictionary while
    // loading
    String[] externalDictionaries = metadata.getExternalDictionaries ();
    for (int externalDictIndex = 0; externalDictIndex < externalDictionaries.length; externalDictIndex++) {
        // Fetch metadata for the external dictionary
        DictionaryMetadata dictionaryMetadata = metadata.getDictionaryMetadata (externalDictionaries[externalDictIndex]);
        System.out.printf ("\n External dictionary name is '%s' and it is an '%s' dictionary.",
            externalDictionaries[externalDictIndex], dictionaryMetadata.isAllowEmpty () ? "Optional" : "Required");
    }
        
    // Similarly, you can obtain list of external tables and inquire if it is required to pass in entries for the table
    // while loading
        
    // Obtain list of modules this module depends on 
    List<String> dependentModules = metadata.getDependentModules (); 
    System.out.printf ("\n Module named '%s' depends on following modules %s.", 
          metadata.getModuleName (), 
          dependentModules);
}
```

- **MultiModuleMetadata API**

While individual modules contain metadata to describe their own schema and artifacts, applications might need metadata about the attributes of combined modules, numerous times, without having to create and load the extractor in memory. The MultiModuleMetadata API provides metadata for extractors formed from a group of modules.

The following example illustrates the usage of the MultiModuleMetadata API:

```java
/**
 * Method to illustrate the ability to understand what an extractor represents before having to create it
 * 
 * Useful when an application consuming text-analytics needs to know extractor specifics ahead of creating extractor
 * This can also be used by say, a tool that displays extractor specifics for users before they choose within the tool 
 *  
 * @throws TextAnalyticsException
 * @throws Exception
 */
public void illustrateMultiModuleMetadata() throws TextAnalyticsException, Exception {
        		
    MultiModuleMetadata modulesMetadata = ModuleMetadataFactory.readAllMetaData(TEXTANALYTICS_MODULES, COMPILED_MODULES_PATH);
    
    System.out.println("Input document schema across these modules : "+ modulesMetadata.getDocSchema());
        		
    System.out.println("Tokenizer used in creating this extractor : "+modulesMetadata.getTokenizerType());
        
    String[] outputViews = modulesMetadata.getOutputViews();
        		
    // Schema of each output view across these modules
    for (String outputView : outputViews) {
        ViewMetadata viewMetadata = modulesMetadata.getViewMetadata(outputView);
        System.out.println("Schema for output view : "+ outputView + " is "+ viewMetadata.getViewSchema());
    }
        		
    // Schema of each exported view across these modules
    String[] exportedViews = modulesMetadata.getExportedViews();
    for(String exportedView : exportedViews) {
        ViewMetadata viewMetadata = modulesMetadata.getViewMetadata(exportedView);
        System.out.println("Schema for exported view : "+ exportedView + " is " + viewMetadata.getViewSchema());
    }
        		
    // Particulars of any function being exported from within any of the modules
    String[] exportedFunctions = modulesMetadata.getExportedFunctions();
    for (String exportedFunction : exportedFunctions) {
        			
        // Fetch function metadata
        FunctionMetadata functionMetadata = modulesMetadata.getFunctionMetadata(exportedFunction);
        			
        System.out.println("Particulars of exported function : "+exportedFunction);
        			
        System.out.println("Function external name : "+functionMetadata.getExternalName());
        
        // Function input parameters
        Param[] functionParams = functionMetadata.getParameters();
        int ix = 0;
        for (Param functionParam : functionParams)
            System.out.println("Function parameter #"+(++ix)+"--> Name : "+functionParam.getName()+" ; Type : "+functionParam.getType());
        
        // Function return type
        System.out.println("Function return type : "+functionMetadata.getReturnType());
    }
        		
    /**
     * Like above, one could use similar public methods exposed by the MultiModuleMetadata API to obtain information
     * about dictionaries, tables, comments relevant to the modules being considered.
    */
}
        
```

#### The DocReader API

The SystemT low-level Java API also includes the [DocReader API](#docreader-api), 
a convenient Java API for reading input document collections that are stored on the local file 
system in one of the supported input collection formats.
The DocReader API supports the input document collections that use the data collection formats.

##### Annotating a document collection \(non-JSON, non-CSV\)

The following example demonstrates how to annotate document collections in an input format that allows only the default document schema \(text Text, \[label Text\]\)\). 
Create a DocReader object, and pass in the location of the input document collection as its parameter. Then, use the DocReader.next\(\) function to obtain the next document in a collection.

```java
// Directory containing the collection of documents from which to extract information, in one of the supported input collection formats.
File INPUT_DOCS_FILE = new File ("/path/to/input/collection/on/disk");

// Create the operator graph
String modulePath = "/path/to/modules/on/disk";
  
TokenizerConfig tokenizer = new TokenizerConfig.Standard ();
OperatorGraph og = OperatorGraph.createOG (new String[] { "module1", "module2" }, modulePath, null, tokenizer);

// Open a reader over input document set 
DocReader docs = new DocReader (INPUT_DOCS_FILE);

while (docs.hasNext ()) {
    Tuple docTuple = docs.next ();

    // Execute the operator graph on the current document, generating every single output type that the extractor produces.
    Map<String, TupleList> results = og.execute (docTuple, null, null);

    // Process the results as required
}
```

##### Annotating a CSV document collection

Annotating a CSV document collection is similar to annotating a regular document collection, 
except that a CSV file can support a non-default document schema. 
When you create the DocReader object for a CSV document collection, use the constructor that specifies a custom document schema, as seen in the following example. 
Then, use DocReader.next\(\) to traverse the collection.

```java
// Directory containing the collection of documents from which to extract information in CSV format
File INPUT_DOCS_FILE = new File ("/path/to/csv/input/collection/on/disk/");

// Create the operator graph
String modulePath = "/path/to/modules/on/disk";

TokenizerConfig tokenizer = new TokenizerConfig.Standard ();
OperatorGraph og = OperatorGraph.createOG (new String[] { "module1", "module2" }, modulePath, null, tokenizer);

// Use the operator graph to determine the document schema.
TupleSchema docSchema = og.getDocumentSchema ();

// Open a reader over input document set
DocReader docs = new DocReader (INPUT_DOCS_FILE, docSchema, null);

while (docs.hasNext ()) {
    Tuple docTuple = docs.next ();

    // Execute the operator graph on the current document, generating every single output type that the extractor produces.
    Map<String, TupleList> results = og.execute (docTuple, null, null);

    // Process the results as required
}
```

##### Annotating a JSON document collection

For a JSON document collection, use the static method DocReader.makeDocAndExternalPairsItr\(\) 
to retrieve an Iterator that returns the document content and any corresponding external view tuples. 
To use this method, you must build an external view map object that represents all the external views, 
with corresponding external names and document schemas.

```java
// Directory containing the collection of documents from which to extract information in a JSON format.    
File INPUT_DOCS_FILE = new File ("/path/to/json/input/collection/on/disk");     

// Create the operator graph    
String modulePath = "/path/to/modules/on/disk";     

TokenizerConfig tokenizer = new TokenizerConfig.Standard ();    
OperatorGraph og = OperatorGraph.createOG (new String[] { "module1", "module2" }, modulePath, null, tokenizer);    
Map<Pair<String, String>, TupleSchema> extViewsMap = new HashMap<Pair<String, String>, TupleSchema> ();     

// Prepare external view map for (String extViewName : og.getExternalViewNames ()) {     
// Get external name of the external view

String extViewExternalName = og.getExternalViewExternalName (extViewName);
  
// Get schema of external view
Pair<String, String> extViewNamePair = new Pair<String, String> (extViewName, extViewExternalName);
TupleSchema schema = og.getExternalViewSchema (extViewName);

extViewsMap.put (extViewNamePair, schema);

// iterate over doc tuples with associated external views
Iterator<Pair<Tuple, Map<String, TupleList>>> itr = DocReader.makeDocandExternalPairsItr (  
INPUT_DOCS_FILE.toURI ().toString (), og.getDocumentSchema (), extViewsMap);
  
while (itr.hasNext ()) {
    Pair<Tuple, Map<String, TupleList>> docExtViewTup = itr.next ();

    Tuple docTuple = docExtViewTup.first;
    Map<String, TupleList> extViewData = docExtViewTup.second;

    Map<String, TupleList> results = og.execute (docTuple, null, extViewData);

    // Process the results as required

}
```

#### Text Analytics URI formats

You can specify the input and output location of your files with Uniform Resource Identifiers \(URIs\).

The following operations are examples of API calls that use URIs:

- Reading an AQL file for compilation
- Writing a TAM file
- Loading modules from a TAM file
- Loading module metadata
- Loading external artifact data

Compared to other applications, SystemT has a flexible definition of what constitutes a valid URI. 
You can use a URI that does not contain a file system scheme \(schemeless URI\),
as well as file system schemes of type HDFS.

1. The full HDFS URI \(hdfs://namenode.ibm.com:9080/directory/file.tam\) has an HDFS scheme and specified authority.
1. The HDFS URI \(hdfs:///directory/file.tam\) has an HDFS scheme and no specified authority.
1. The local URI \(file:///directory/file.tam\) has a local file system scheme and no specified authority.
1. The schemeless absolute URI \(/directory/file.tam\) has a POSIX-compliant absolute file path.
1. The schemeless relative URI \(directory/file.tam\) has a POSIX-compliant relative file path.

For more information about the specifics of the following APIs, see the Javadoc classes and APIs.

|Java API|Supported URI formats|
|-----------------------|---------------------|
|CompileAQLParams\(...\) \[constructor\]|- Local<br>- Schemeless absolute<br>- Schemeless relative|
|CompileAQLParams.setInputModules\(...\)|- Local<br>- Schemeless absolute<br>- Schemeless relative|
|CompileAQLParams.setModulePath\(...\)|- Local<br>- Schemeless absolute<br>- Schemeless relative|
|CompileAQLParams.setOutputURI\(...\)|- Local<br>- Schemeless absolute<br>- Schemeless relative|
|OperatorGraph.createOG\(...\)|- Full HDFS<br>- HDFS<br>- Local<br>- Schemeless absolute<br>- Schemeless relative|
|OperatorGraph.validateOG\(...\)|- Full HDFS<br>- HDFS<br>- Local<br>- Schemeless absolute<br>- Schemeless relative|
|ExternalTypeInfo.addDictionary\(...\)|- Full HDFS<br>- HDFS<br>- Local<br>- Schemeless absolute<br>- Schemeless relative|
|ExternalTypeInfo.addTable\(...\)|- Full HDFS<br>- HDFS<br>- Local<br>- Schemeless absolute<br>- Schemeless relative|
|ModuleMetadataFactory.readMetaData\(...\)|- Full HDFS<br>- HDFS<br>- Local<br>- Schemeless absolute<br>- Schemeless relative|
|ModuleMetadataFactory.readAllMetaData\(...\)|- Local<br>- Schemeless absolute<br>- Schemeless relative|

Schemeless URIs \(formats 5 and 6\) are resolved to a schemed URI in a process that is called scheme auto-discovery 
so that you can reuse a URI in applications without committing to a specific underlying file system scheme:

- If you are using APIs that do not support a distributed file system \(for example, compile APIs\), 
the auto-resolved scheme is always local file system \(file://\).
- If the APIs support a distributed file system \(for example OperatorGraph.createOG\(\)\), 
the auto-resolved scheme is one of HDFS if installed \( hdfs://\), otherwise it is the local file system \(file://\). 
To determine whether a DFS is installed, SystemT attempts to read the fs.default.name property 
from the file core-site.xml in the directory that is specified by the environment variable HADOOP\_CONF\_DIR. 
It then sets the scheme to be identical to the scheme of the URI contained in that property. 
If the read fails for any reason, the scheme is set to local file system.

When the scheme is auto-discovered in a relative file path URI \(format 6\), the URI is resolved to an absolute URI:

- If the scheme is a distributed file system \( hdfs://\), you can assume that the URI is relative to the root of the DFS that is specified.
- If the scheme is local file system \(file://\), then you can assume that the URI is relative to the current working directory \(usually the directory from which the application was started\).

##### Example 1

In this first example, you can create an operator graph with the module path of `application/modules` \(format 6\), using the OperatorGraph.createOG\(\) API. 
The system checks $HADOOP\_CONF\_DIR/core-site.xml for the property file fs.default.name, which is set to hdfs://bigserver.widgets.org. 
The module path is then set to hdfs://bigserver.widgets.org/application/modules.

##### Example 2

In the same environment, the HADOOP\_CONF\_DIR environment variable is not set and the directory where SystemT is started is your home directory /home/username. 
With no distributed file system found, the module path is set to file:///home/username/application/modules.


### Using the High-level Java API

#### Prepare Compiled Text Analytics Modules (TAM) files

SystemT's high-level API is designed to compile AQL code and to execute compiled AQL code (TAM modules). For reference, AQL compilation can be completed by using either the [high-level API](#configuration-for-aql-compilation) or the [low-level API](#compile-aql-code-into-text-analytics-modules-tam-files),

```sh
tams
\
---demoAqlModule.tam
```

#### Create Configuration File

SystemT high-level API is designed to integrate application easily, so the input object and output annotation result are defined as Jackson Json node. To construct annotation result, we need to setup annotation core module by using a configuration json file named `manifest.json`

Here is the `manifest.json` used in this tutorial:

```json
{
  "annotator": {
    "version": "1.0",
    "key": "Demo"
  },
  "annotatorRuntime": "SystemT",
  "version": "1.0",
  "acceptedContentTypes": [
    "text/html",
    "text/plain"
  ],
  "serializeAnnotatorInfo": false,
  "location": "./model",
  "serializeSpan": "locationAndText",
  "tokenizer": "standard",
  "modulePath": [
    "tams"
  ],
  "moduleNames": [
    "demoAqlModule"
  ],
  "inputTypes": null,
  "outputTypes": [
    "ProgrammingLanguageName", "ProgrammingLanguageWithVersion"
  ],
  "externalDictionaries": null,
  "externalTables": {}
}
```

This looks hard to understand at a glance, but not many fields are mandatory to modify for trial. For a complete reference, see [Specification of manifest.json](../Specification-of-manifest.json).

Here's some key fields to use your own custom annotator.

- Model file path:  
  Set `location` to the model folder.
  ```sh
    "location": "./model",
  ```
- Module path:  
  Path to `.tam` folder path relative to the value in `location`.
  ```sh
    "modulePath": [
      "tams"
    ]
  ```
- Module name:  
  Names of AQL modules that you want to execute (coincide with the names of TAM files without `.tam` extension).
  ```sh
    "moduleNames": [
      "demoAqlModule"
    ]
  ```
- Output types:  
  Output views that you want to execute (other output views may exist in your TAM files, but they will not be executed unless explicitly included here).
  ```sh
    "outputTypes": [
      "ProgrammingLanguageName", "ProgrammingLanguageWithVersion"
    ]
  ```

For further details, please refer to [Specification of manifest.json](../Specification-of-manifest.json)

Now we have

```sh
.
├── model
│   ├── manifest.json
│   ├── tams
│   │   └── demoAqlModule.tam
│   └── aql
│       └── demoAqlModule
│           └── main.aql
```

##### Configuration for AQL Compilation

Add the following field to specify the location of the AQL files, if you'd like to compile and execute a model including AQL files.

- Source modules:  
  Path to `.aql` folder path relative to the value in `location`.
  ```sh
    "sourceModules": [
      "aql/demoAqlModule"
    ]
  ```

#### Instantiate and Execute Extractor

The following code takes English text and writes analysis result to console. See [API Reference](../API-Reference.md) for details of each method.

```java
// Annotation service core object (an embeddable library, not a service)
AnnotationService as = new AnnotationService();

// Use jackson objectmapper to read manifest.json and dump result
ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

// STEP 1. Load configuration (manifest.json)
Path manifestPath = Paths.get("model/manifest.json");
AnnotatorBundleConfig cfg = mapper.readValue (Files.readAllBytes (manifestPath), 
AnnotatorBundleConfig.class);

// STEP 2. Prepare execution parameter
// LanguageCode for text
String language = "en";
ExecuteParams execParams = new ExecuteParams (-1L, language, null,
	AnnotatorBundleConfig.ContentType.PLAIN.getValue (), null);

// Text to process
String text = "I like implementing NLP models in AQL. I can execute AQL from Java 8 and Python 3.7.";

ObjectNode objNode = mapper.createObjectNode ();
objNode.put ("label", "docLabel");
objNode.put ("text", text);

// STEP 3. Invoke annotation service core and get result
JsonNode outputJson = as.invoke (cfg, objNode, execParams);
```

#### Get Result

The analysis result is stored in `Jackson JsonNode` objects.

For the following input text:

```curl
I like implementing NLP models in AQL. I can execute AQL from Java 8 and Python 3.7.
```

The above code will print to console:

```json
{
  "annotations" : {
    "ProgrammingLanguageName" : [ {
      "name" : {
        "location" : {
          "begin" : 34,
          "end" : 37
        },
        "text" : "AQL"
      }
    }, {
      "name" : {
        "location" : {
          "begin" : 53,
          "end" : 56
        },
        "text" : "AQL"
      }
    }, {
      "name" : {
        "location" : {
          "begin" : 62,
          "end" : 66
        },
        "text" : "Java"
      }
    }, {
      "name" : {
        "location" : {
          "begin" : 73,
          "end" : 79
        },
        "text" : "Python"
      }
    } ],
    "ProgrammingLanguageWithVersion" : [ {
      "fullMatch" : {
        "location" : {
          "begin" : 34,
          "end" : 37
        },
        "text" : "AQL"
      },
      "name" : {
        "location" : {
          "begin" : 34,
          "end" : 37
        },
        "text" : "AQL"
      },
      "version" : null
    }, {
      "fullMatch" : {
        "location" : {
          "begin" : 53,
          "end" : 56
        },
        "text" : "AQL"
      },
      "name" : {
        "location" : {
          "begin" : 53,
          "end" : 56
        },
        "text" : "AQL"
      },
      "version" : null
    }, {
      "fullMatch" : {
        "location" : {
          "begin" : 62,
          "end" : 68
        },
        "text" : "Java 8"
      },
      "name" : {
        "location" : {
          "begin" : 62,
          "end" : 66
        },
        "text" : "Java"
      },
      "version" : {
        "location" : {
          "begin" : 67,
          "end" : 68
        },
        "text" : "8"
      }
    }, {
      "fullMatch" : {
        "location" : {
          "begin" : 73,
          "end" : 83
        },
        "text" : "Python 3.7"
      },
      "name" : {
        "location" : {
          "begin" : 73,
          "end" : 79
        },
        "text" : "Python"
      },
      "version" : {
        "location" : {
          "begin" : 80,
          "end" : 83
        },
        "text" : "3.7"
      }
    } ]
  },
  "instrumentationInfo" : {
    "annotator" : {
      "version" : "1.0",
      "key" : "Demo"
    },
    "runningTimeMS" : 4,
    "documentSizeChars" : 92,
    "numAnnotationsTotal" : 8,
    "numAnnotationsPerType" : [ {
      "annotationType" : "ProgrammingLanguageName",
      "numAnnotations" : 4
    }, {
      "annotationType" : "ProgrammingLanguageWithVersion",
      "numAnnotations" : 4
    } ],
    "interrupted" : false,
    "success" : true
  }
}
```
