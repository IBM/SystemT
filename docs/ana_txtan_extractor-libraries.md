---
providerName: IBM
layout: page
title: Pre-built Extractors
nav_order: 6
has_children: true
parent: Reference
grand_parent: In depth rule-based modeling techniques
description: Pre-built extractor libraries
---

# Pre-built extractor libraries

AQL pre-built extractor libraries extract mentions of specific information from input text. 
You can also extend the views of these extractors to develop custom extractors.

**TODO**: Describe the prebuilt AQL libraries available in Watson NLP
and how to obtain them from Artifactory.

## Extractors for Generic Entities

The extractors export the following views:

- Person
- Organization
- Location
- Address
- City
- Continent
- Country
- County
- DateTime
- EmailAddress
- NotesEmailAddress
- PhoneNumber
- StateOrProvince
- Town
- URL
- ZipCode
- CompanyEarningsAnnouncement
- AnalystEarningsEstimate
- CompanyEarningsGuidance
- Alliance
- Acquisition
- JointVenture
- Merger

**Note:** When you import modules from the pre-built extractor library, 
or other extractor libraries, you need to resolve naming conflicts between the modules that you develop,
 and the modules that are being imported or pointed to. To resolve this issue, 
 you must alter the names of the conflicting modules that you develop.

The module names for the Generic Extractors are:

- `Address`
- `EntitiesExport`
- `EntitiesOutput`
- `City`
- `CommonFeatures`
- `Continent`
- `County`
- `Date`
- `DateTime`
- `Dictionaries`
- `Disambiguation`
- `EmailAddress`
- `Facility`
- `FinancialAnnouncements`
- `FinancialDictionaries`
- `FinancialEvents`
- `InputDocumentProcessor`
- `Linguistics`
- `Location`
- `LocationCandidates`
- `NotesEmailAddress`
- `Organization`
- `OrganizationCandidates`
- `Person`
- `PersonCandidates`
- `PhoneNumber`
- `Region`
- `SentenceBoundary`
- `StateOrProvince`
- `Time`
- `Town`
- `UDFs`
- `URL`
- `WaterBody`
- `ZipCode`

- **[Named entity extractors](#NamedEntities)**  
The pre-built named entity extractors can be used with general knowledge data such as news articles, news reports, news websites, and blogs. These extractors have high coverage for English, and limited coverage for German input documents.
- **[Financial extractors](#FinEnt)**  
The pre-built financial extractors can be used with data such as finance reports, earnings reports, and analyst estimates. These extractors have coverage only for English.
- **[Generic extractors](#genericext)**  
These pre-built extractors can be used to extract generic text and numeric information such as capital words or integers. These extractors have coverage for English input documents.
- **[Other extractors](#OtherEnt)**  
These pre-built extractors can be used to extract domain independent information such as dates, URLs, and emails. These extractors have coverage for English input documents.
- **[Sentiment extractors](#sentimentext)**  
These pre-built extractors can be used to extract sentiment information from surveys or domain-independent content. These extractors have coverage for English input documents.
- **[Base modules](#basemodules)**
Base modules provide the fundamental extractors that are used by all the above

## <a name="NamedEntities">Named entity extractors</a>

The pre-built named entity extractors can be used with general knowledge data such as news articles, news reports, news websites, and blogs. These extractors have high coverage for English, and limited coverage for German input documents.

The extractor libraries detag the data collection before extraction. To transform the extraction results, you can use the [REMAP scalar function](../../com.ibm.swg.im.infosphere.biginsights.aqlref.doc/doc/scalar-functions.md).

There are primary and secondary attributes for these extractors. Extractor results for attributes are determined by the available information within the input document. Primary attributes are always populated, and are the main purpose of the analysis. Secondary attributes are populated whenever possible, but the extractor might not always return results. In some of the following extractor examples, extraction results for the attributes are shown with their corresponding span offset values \(`[offset begin-offset end]`\).

### Person

The Person extractor identifies the mentions of person names. The primary attribute is the full mention of a name and secondary attributes are first name, last name, and middle initial of a person.

- The Person extractor can be used with documents in English and German.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|firstname|Span|The first name part of a Person extraction.|
|middlename|Span|The middle initial part of a Person extraction.|
|lastname|Span|The last name part of a Person extraction|
|person|Span|The person mentions as extracted.|

For example, consider the results of the extractor on the following text:

```bash
Samuel J. Palmisano, IBM chairman, president and chief executive officer, said: "IBM had a terrific quarter and a good year with record cash performance, profit and EPS, as well as record payouts to shareholders."
```

The extraction results are:

```bash
firstname: Samuel [0-6]
middlename: J. [7-9]
lastname: Palmisano [10-19]
person:Samuel J. Palmisano [0-19]
```

### Organization

The Organization extractor identifies the mentions of organization names, such as a company, government agency, military organization, school, or committee.

- The Organization extractor can be used with documents in English.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|organization|Span|The organization mentions as extracted|

For example, consider the results of the extractor on the following text:

```bash
IBM will help Citi explore how the Watson technology could help improve and simplify the banking experience.
```

The extraction result is:

```bash
organization: IBM [0-3], Citi [14-18]
```

### Location

The Location extractor identifies the mentions of locations. The extracted information contains the mentions of addresses, cities, counties, countries, continents, states or provinces, and zip codes. This extractor cannot combine adjacent location names, identify types of locations, or postal or zip codes. Use the Address extractor for these extraction tasks.

- The Location extractor can be used with documents in English.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|location|Span|The location mentions as extracted|

For example, consider the results of the extractor on the following text:

```bash
Revenues from Europe/Middle East/Africa were $9.3 billion, up 11 percent(3 percent, adjusting for currency). 
```

The extraction result is:

```bash
location: Europe [14-20], Middle East [21-32], Africa [33-39]
```

### Address

This extractor identifies the mentions of U.S. postal addresses. Primary attributes are the street name and number, and a post office box if that information is available. Secondary attributes are the associated zip code and city and state information. Matches for a country are not included. Use the Location extractor to match general names of geographic locations.

- The Address extractor can be used with documents in English and German.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|city|Span|The city part of the extraction.|
|stateorprovince|Span|The state or province part of the extraction.|
|zip|Span|The zip code part of the extraction.|
|address|Span|The address mentions as extracted|

For example, consider the results of the extractor on the following text:

```bash
For further information, please contact the corporate headquarters of IBM at:

IBM Corporation
1 New Orchard Road
Armonk, New York 10504-1722
United States
914-499-1900

```

The extraction results are:

```bash
city: Armonk [118-124]
stateorprovince: New York [126-134]
zip: 10504-1722 [135-145]
address: 1 New Orchard Road Armonk, New York 10504-1722 [98-145]

```

### City

This extractor identifies the mentions of city names. Secondary attributes are the associated state, country, and continent of the city. Zip codes are not recognized.

- The City extractor can be used with documents in English and German.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|city|Span|The city mentions as extracted.|
|stateorprovince|Text|The state or province part of the extraction.|
|country|Text|The country part of the extraction.|
|continent|Text|The continent part of the extraction.|

For example, consider the results of the extractor on the following data:

```bash
Their work is getting it's first real-world tryout with Fondazione IRCCS IstitutoNazionale dei Tumori , a public health institute in Milan, Italy, which specializes in the study and treatment of cancer.
```

The extraction results are:

```bash
city: Milan [133-138]
stateorprovince:
country: Italy [140-145]
continent:
```

### Town

This extractor identifies the mentions of town names when terms such as “town of” are detected. This extractor is designed to recognize geographic locations, not postal addresses and zip codes.

- The Town extractor can be used with documents in English.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|town|Span|The town mentions as extracted|

Consider this example:

```bash
IBM is also working closely with the town of Littleton to build partnerships and engage community members.  Last fall, IBM awarded Littleton Public Schools a $40,000 grant for IBM's Reading Companion program. 
```

The extraction result is:

```bash
town: Littleton [45-64]
```

### County

This extractor identifies the mentions of county names located in the United States. Secondary attributes are the associated state, country, and continent of the county if the information is available. County names that are part of an organization name such as Essex County Council are ignored.

- The County extractor can be used with documents in English.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|county|Span|The county mentions as extracted|
|stateorprovince|Span|The state or province part of the extraction.|
|country|Span|The country part of the extraction.|
|continent|Span|The continent part of the extraction.|

For example, consider the results of the extractor on the following text:

```bash
Initial production at IBM's 300mm fab in East Fishkill has begun, with volume production set to begin at GlobalFoundries' new 300mm manufacturing facility in Saratoga County, NY.
```

The extraction results are:

```bash
county: Saratoga County [158-173]
stateorprovince: 
country:
continent:
```

### Country

This extractor identifies the mentions of country names, such as Canada or Kuwait.

- The Country extractor can be used with documents in English.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|country|Span|The country mentions as extracted|
|continent|Span|The continent part of the extraction.|

For example, consider the results of the extractor on the following text:

```bash
Revenues in the BRIC countries — Brazil, Russia, India and China — increased 19 percent (17 percent, adjusting for currency), and a total of 50 growth market countries had double-digit revenue growth.
```

The extraction results return all four countries noted in the input data collection:

```bash
country: Brazil [33-39], Russia [41-47], India [49-54], China [59-64]
```

### Continent

This extractor identifies the mentions of the seven continents Asia, Africa, North America, South America, Antarctica, Europe, and Australia.

- The Continent extractor can be used with documents in English.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|continent|Span|The continent mentions as extracted.|

For example, consider the results of the extractor on the following text:

```bash
Strategic outsourcing signings up 20 percent worldwide, up 44 percent in North America. 
```

The extraction result is:

```bash
continent: North America [73-87]
```

### StateOrProvince

This extractor identifies the mentions of states or provinces. for many countries around the world. Abbreviations for state or province will be extracted when the country name immediately follows the abbreviation. Secondary attributes are the associated country and continent names. To include territories, add entries to the Additional State/Province Names list.

Extracts the names of states or provinces

- The StateOrProvince extractor can be used with documents in English.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|stateorprovince|Span|The state or province mentions as extracted.|
|country|Span|The country mentions as extracted|
|continent|Span|The continent part of the extraction.|

Consider this example:

```bash
Baker is speaking at the upcoming IBM Finance Forum (May 9) and IBM Performance
(May 10) events in Calgary, Alberta, Canada.
```

The extraction results are:

```bash
stateorprovince: Alberta [109-116]
country: Canada [118-124]
continent: 
```

### Zipcode

This extractor identifies the mentions of U.S. zip codes. Numeric post codes for other countries such as France might also be extracted.

- The Zipcode extractor can be used with documents in English.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|zipcode|Span|The zip code mentions as extracted.|

For example, consider the results of the extractor on the following text:

```bash
For further information, please contact the corporate headquarters of IBM at:

IBM Corporation
1 New Orchard Road
Armonk, New York 10504-1722
United States
914-499-1900

```

The extraction result is:

```bash
zipCode: 10504-1722 [135-145]

```

## <a name="FinEnt">Financial extractors</a>

The pre-built financial extractors can be used with data such as finance reports, earnings reports, and analyst estimates. These extractors have coverage only for English.

The extractor libraries detag the data collection before extraction.

There are primary and secondary attributes for these extractors. Extractor results for attributes are determined by the available information within the input document. Primary attributes are always populated, and are the main purpose of the analysis. Secondary attributes are populated whenever possible, but the extractor might not always return results. In some of the following extractor examples, extraction results for the attributes are shown with their corresponding span offset values \(`[offset begin-offset end]`\).

### Acquisition

The Acquisition extractor identifies the mentions of acquisition transactions. The primary attributes are the names of the companies that are involved in the acquisition. Secondary attributes are the respective stock symbols and the status of the acquisition.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|company1|Span|The first company part of the extractor.|
|company1\_detag|Span|The first company part in a detagged format.|
|company1\_text|Text|The first company part in a text format.|
|company1\_detag\_text|Text|The first company part in a detagged text format.|
|stockexchange1|Span|The stock exchange of the first company part.|
|stocksymbol1|Span|The stock symbol of the first company part.|
|company2|Span|The second company part of the extractor.|
|company2\_detag|Span|The second company part in a detagged format.|
|company2\_text|Text|The second company part in a text format.|
|company2\_detag\_text|Text|The second company part in a detagged text format.|
|stockexchange2|Span|The stock exchange of the second company part.|
|stocksymbol2|Span|The stock symbol of the second company part.|
|company3|Span|The third company part of the extractor.|
|stockexchange3|Span|The stock exchange of the third company part.|
|stocksymbol3|Span|The stock symbol of the third company part.|
|date|Text|The date part of the extractor.|
|datestring|Text|The date string part of the extractor.|
|status|Text|The status part of the extractor.|
|match|Span|The match part of the extractor.|
|match\_detag|Span|The match part in a detagged format.|
|match\_text|Text|The match part in a text format.|
|match\_detag\_text|Text|The match part in a detagged text format.|

For example, consider the results of the Acquisition extractor on the following text:

```bash
ARMONK, NY, - 22 Apr 2013: IBM (NYSE: IBM) today announced it has acquired UrbanCode Inc. Based in Cleveland, Ohio, UrbanCode automates the delivery of software, helping businesses quickly release and update mobile, social, big data, cloud applications. 
```

The extraction results are:

```bash
company1: IBM [27-30]
company1_detag: IBM [27-30]
company1_text: IBM
company1_detag_text: IBM
stockexchange1: NYSE [32-36]
stocksymbol1: IBM [38-41]
company2: Urban Code, Inc.[75-89]
company2_detag: Urban Code, Inc.[75-89]
company2_text: Urban Code, Inc.
company2_detag_text: Urban Code, Inc.
stockexchange2:
stocksymbol2:
company3:
stockexchange3:
stocksymbol3:
date:
datestring:
status: announced
match: IBM (NYSE: IBM) today announced it has acquired UrbanCode Inc. [27-89]
match_detag: IBM (NYSE: IBM) today announced it has acquired UrbanCode Inc. [27-89]
match_text: IBM (NYSE: IBM) today announced it has acquired UrbanCode Inc.
match_detag_text: IBM (NYSE: IBM) today announced it has acquired UrbanCode Inc.

```

### Alliance

The Alliance extractor identifies the mentions of alliance agreements. The primary attributes are the names of the companies that are involved in the alliance. Secondary attributes are the respective stock symbols and the status of the alliance.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|company1|Span|The first company part of the extractor.|
|company1\_detag|Span|The first company part in a detagged format.|
|company1\_text|Text|The first company part in a text format.|
|company1\_detag\_text|Text|The first company part in a detagged text format.|
|stockexchange1|Span|The stock exchange of the first company part.|
|stocksymbol1|Span|The stock symbol of the first company part.|
|company2|Span|The second company part of the extractor.|
|company2\_detag|Span|The second company part in a detagged format.|
|company2\_text|Text|The second company part in a text format.|
|company2\_detag\_text|Text|The second company part in a detagged text format.|
|stockexchange2|Span|The stock exchange of the second company part.|
|stocksymbol2|Span|The stock symbol of the second company part.|
|company3|Span|The third company part of the extractor.|
|stockexchange3|Span|The stock exchange of the third company part.|
|stocksymbol3|Span|The stock symbol of the third company part.|
|date|Text|The date part of the extractor.|
|datestring|Text|The date string part of the extractor.|
|status|Text|The status part of the extractor.|
|match|Span|The match part of the extractor.|
|match\_detag|Span|The match part in a detagged format.|
|match\_text|Text|The match part in a text format.|
|match\_detag\_text|Text|The match part in a detagged text format.|

For example, consider the results of the Alliance extractor on the following text:

```bash
In December, ABC Insurance (NYSE: ABC) announced an alliance with SecondEast (NYSE: SEC) that likely influenced investor activity.
```

The extraction results are:

```bash
company1: ABC Insurance [13-26]
company1_detag: ABC Insurance [13-26]
company1_text: ABC Insurance
company1_detag_text: ABC Insurance
stockexchange1: NYSE [28-32]
stocksymbol1: ABC [34-37]
company2: SecondEast [66-76]
company2_detag: SecondEast [66-76]
company2_text: SecondEast 
company2_detag_text: SecondEast 
stockexchange2: NYSE [78-82]
stocksymbol2: SEC [84-87]
company3:
stockexchange3:
stocksymbol3:
date:
datestring:
status: announced
match: ABC Insurance (NYSE: ABC) announced an alliance with SecondEast (NYSE: SEC) [13-87]
match_detag: ABC Insurance (NYSE: ABC) announced an alliance with SecondEast (NYSE: SEC) [13-87]
match_text: ABC Insurance (NYSE: ABC) announced an alliance with SecondEast (NYSE: SEC)
match_detag_text: ABC Insurance (NYSE: ABC) announced an alliance with SecondEast (NYSE: SEC)
```

### AnalystEarningsEstimate

The AnalystEarningsEstimate extractor identifies the mentions of earnings estimates of companies that are issued by an analyst. The primary attributes are the name of the analyst, the analyst's company, the company whose estimates are being drawn and its stock symbol, and the financial metric on which the estimate is based. Secondary attributes are the quarter and year of the statement.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|companysource|Span|The company source part of the extractor.|
|personsource|Text|The person source part.|
|companyrated|Span|The company rated part.|
|companyrated\_detag|Span|The company rated part in a detagged format.|
|companyrated\_text|Text|The company rated part in a text format.|
|companyrated\_detag\_text|Text|The company rated part in a detagged text format.|
|stockexchange|Span|The stock exchange of the company part.|
|stocksymbol|Span|The stock symbol of the company part.|
|year|Text|The year part of the extractor.|
|quarter|Text|The quarter part of the extractor.|
|financialmetricname|Span|The financial metric name part of the extractor.|
|financialmetricestimate|Span|The financial metric estimate part.|
|financialmetricpreviousestimate|Text|The financial metric previous estimate part.|
|date|Text|The date part of the extractor.|
|datestring|Text|The date string part of the extractor.|
|match|Span|The match part of the extractor.|
|match\_detag|Span|The match part in a detagged format.|
|match\_text|Text|The match part in a text format.|
|match\_detag\_text|Text|The match part in a detagged text format.|

### CompanyEarningsAnnouncement

The CompanyEarningsAnnouncement extractor identifies the mentions of earnings announcements that are released by a company. The primary attributes are the name of the company that makes the announcement, its stock symbol, the financial metric, and the financial metric value. Secondary attributes are the quarter and year for the announcement.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|company|Span|The company part of the extractor.|
|company\_detag|Span|The company part in a detagged format.|
|company\_text|Text|The company part in a text format.|
|company\_detag\_text|Text|The company part in a detagged text format.|
|stockexchange|Span|The stock exchange of the company part.|
|stocksymbol|Span|The stock symbol of the company part.|
|year|Span|The year part of the extractor.|
|quarter|Text|The quarter part of the extractor.|
|financialmetricname|Span|The financial metric name part of the extractor.|
|financialmetricname\_detag|Span|The financial metric name part in a detagged format.|
|financialmetricname\_text|Text|The financial metric name part in a text format.|
|financialmetricname\_detag\_text|Text|The financial metric name part in a detagged text format.|
|financialmetricvalue|Span|The financial metric value part.|
|financialmetricvalue\_detag|Span|The financial metric value part in a detagged format.|
|financialmetricvalue\_text|Text|The financial metric value part in a text format.|
|financialmetricvalue\_detag\_text|Text|The financial metric value part in a detagged text format.|
|financialmetricestimate|Span|The financial metric estimate part.|
|date|Text|The date part of the extractor.|
|datestring|Text|The date string part of the extractor.|
|match|Span|The match part of the extractor.|
|match\_detag|Span|The match part in a detagged format.|
|match\_text|Text|The match part in a text format.|
|match\_detag\_text|Text|The match part in a detagged text format.|

### CompanyEarningsGuidance

The CompanyEarningsGuidance extractor identifies the mentions of earnings guidance that is issued by a company. The primary attributes are the name of the company that issues the guidance, its stock ticker, the financial metric, and value. Secondary attributes are the quarter and year for the guidance.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|company|Span|The company part of the extractor.|
|company\_detag|Span|The company part in a detagged format.|
|company\_text|Text|The company part in a text format.|
|company\_detag\_text|Text|The company part in a detagged text format.|
|stockexchange|Span|The stock exchange of the company part.|
|stocksymbol|Span|The stock symbol of the company part.|
|year|Text|The year part of the extractor.|
|quarter|Text|The quarter part of the extractor.|
|financialmetricname|Span|The financial metric name part of the extractor.|
|financialmetricname\_detag|Span|The financial metric name part in a detagged format.|
|financialmetricname\_text|Text|The financial metric name part in a text format.|
|financialmetricname\_detag\_text|Text|The financial metric name part in a detagged text format.|
|financialmetricestimate|Text|The financial metric estimate part.|
|financialmetricpreviousestimate|Span|The financial metric previous estimate part.|
|financialmetricconsensusestimate|Span|The financial metric consensus estimate part.|
|date|Text|The date part of the extractor.|
|datestring|Text|The date string part of the extractor.|
|match|Span|The match part of the extractor.|
|match\_detag|Span|The match part in a detagged format.|
|match\_text|Text|The match part in a text format.|
|match\_detag\_text|Text|The match part in a detagged text format.|

### JointVenture

The JointVenture extractor identifies the mentions of joint venture transactions. The primary attributes are the names of companies that are involved in a joint venture. Secondary attributes are their respective stock symbols and the status of the joint venture.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|company1|Span|The first company part of the extractor.|
|company1\_detag|Span|The first company part in a detagged format.|
|company1\_text|Text|The first company part in a text format.|
|company1\_detag\_text|Text|The first company part in a detagged text format.|
|stockexchange1|Span|The stock exchange of the first company part.|
|stocksymbol1|Span|The stock symbol of the first company part.|
|company2|Span|The second company part of the extractor.|
|company2\_detag|Span|The second company part in a detagged format.|
|company2\_text|Text|The second company part in a text format.|
|company2\_detag\_text|Text|The second company part in a detagged text format.|
|stockexchange2|Span|The stock exchange of the second company part.|
|stocksymbol2|Span|The stock symbol of the second company part.|
|company3|Span|The third company part of the extractor.|
|stockexchange3|Span|The stock exchange of the third company part.|
|stocksymbol3|Span|The stock symbol of the third company part.|
|companycreated|Text|The company created part.|
|date|Text|The date part of the extractor.|
|datestring|Text|The date string part of the extractor.|
|status|Text|The status part of the extractor.|
|match|Span|The match part of the extractor.|
|match\_detag|Span|The match part in a detagged format.|
|match\_text|Text|The match part in a text format.|
|match\_detag\_text|Text|The match part in a detagged text format.|

For example, consider the results of the JointVenture extractor on the following text:

```bash
Our financing and leasing assets include amounts related to the ABC Insurance (NYSE: ABC), and the SecondEast (NYSE: SEC) and Sample Outdoor Company joint venture programs. These amounts include receivables originated directly by ABC Insurance, as well as receivables purchased from joint venture entities. 
```

The extraction results are:

```bash
company1: SecondEast [91-101]
company1_detag: SecondEast [91-101]
company1_text: SecondEast
company1_detag_text: SecondEast
stockexchange1: NYSE [103-107]
stocksymbol1: SEC [109-112]
company2: Sample Outdoor Company [119-141]
company2_detag: Sample Outdoor Company [119-141]
company2_text: Sample Outdoor Company 
company2_detag_text: Sample Outdoor Company 
stockexchange2: 
stocksymbol2: 
company3:
stockexchange3:
stocksymbol3:
companycreated:
date:
datestring:
status:
match: SecondEast (NYSE: SEC) and Sample Outdoor Company joint venture [91-155]
match_detag: SecondEast (NYSE: SEC) and Sample Outdoor Company joint venture [91-155]
match_text: SecondEast (NYSE: SEC) and Sample Outdoor Company joint venture
match_detag_text: SecondEast (NYSE: SEC) and Sample Outdoor Company joint venture
```

### Merger

The Merger extractor identifies the mentions of merger transactions. The extracted primary attributes are the names of companies that are involved in a merger. Secondary attributes are their respective stock tickers and the status of the merger.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|company1|Span|The first company part of the extractor.|
|company1\_detag|Span|The first company part in a detagged format.|
|company1\_text|Text|The first company part in a text format.|
|company1\_detag\_text|Text|The first company part in a detagged text format.|
|stockexchange1|Span|The stock exchange of the first company part.|
|stocksymbol1|Span|The stock symbol of the first company part.|
|company2|Span|The second company part of the extractor.|
|company2\_detag|Span|The second company part in a detagged format.|
|company2\_text|Text|The second company part in a text format.|
|company2\_detag\_text|Text|The second company part in a detagged text format.|
|stockexchange2|Span|The stock exchange of the second company part.|
|stocksymbol2|Text|The stock symbol of the second company part.|
|company3|Text|The third company part of the extractor.|
|stockexchange3|Text|The stock exchange of the third company part.|
|stocksymbol3|Span|The stock symbol of the third company part.|
|companycreated|Text|The company created part.|
|date|Text|The date part of the extractor.|
|datestring|Text|The date string part of the extractor.|
|status|Text|The status part of the extractor.|
|match|Span|The match part of the extractor.|
|match\_detag|Span|The match part in a detagged format.|
|match\_text|Text|The match part in a text format.|
|match\_detag\_text|Text|The match part in a detagged text format.|

For example, consider the results of the Merger extractor on the following text:

```bash
Noninterest expense was $25.2 million, an increase of 33.4% from $18.9 million in the third quarter of 2007, driven by the acquisition of ABC Insurance (NYSE: ABC)and charges related to the upcoming merger with XYZ Financial Bancorp (NYSE: XYZ)as well as branch expansion.
```

The extraction results are:

```bash
company1: ABC Insurance [138-151]
company1_detag: ABC Insurance [138-151]
company1_text: ABC Insurance
company1_detag_text: ABC Insurance
stockexchange1: NYSE [153-157]
stocksymbol1: ABC [159-162]
company2: XYZ Financial Bancorp [212-233]
company2_detag: XYZ Financial Bancorp [212-233]
company2_text: XYZ Financial Bancorp
company2_detag_text: XYZ Financial Bancorp
stockexchange2: NYSE [235-239]
stocksymbol2: XYZ [241-244]
company3:
stockexchange3:
stocksymbol3:
companycreated:
date:
datestring:
status:
match: ABC Insurance (NYSE: ABC) and charges related to the upcoming merger with XYZ Financial Bancorp (NYSE: XYZ) [138-244]
match_detag: ABC Insurance (NYSE: ABC) and charges related to the upcoming merger with XYZ Financial Bancorp (NYSE: XYZ) [138-244]
match_text: ABC Insurance (NYSE: ABC) and charges related to the upcoming merger with XYZ Financial Bancorp (NYSE: XYZ)
match_detag_text: ABC Insurance (NYSE: ABC) and charges related to the upcoming merger with XYZ Financial Bancorp (NYSE: XYZ)
```

## <a name="genericext">Generic extractors</a>

These pre-built extractors can be used to extract generic text and numeric information such as capital words or integers. These extractors have coverage for English input documents.

### CapsWord

Matches any capitalized word including the optional symbols \(.\), \(-\), \(&\), and \('\).

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|match|Span|The capitalized word including the optional symbols \(.\), \(-\), \(&\), and \('\).|

For example, consider the results of the extractor on the following text:

```bash
IBM and AT&T are participants in the "SmartAmerica Challenge."
```

The result is:

```bash
match: IBM
match: AT&T
match: SmartAmerica
match: Challenge
```

### CurrencyAmount

Extracts a numeric currency amount and its preceding currency symbol. \(Currency symbols are those defined in Unicode category `\p{Sc}`.\) Extracts a range of currency amounts when the numbers are separated by "to", for example, `"$8 to $45"`.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|amountrange|Span|String corresponding to currency amount or a range of currency amounts.|

For example, consider the results of the extractor on the following text:

```bash
Revenues from the Software segment were $6.5 billion, up 1 percent.
```

The result is:

```bash
amountrange: $6.5 billion
```

### Decimal

Matches any decimal number. Decimal numbers include the decimal point and are expressed as numerals \(not words\).

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|decimal|Span|String corresponding to an decimal number.|

For example, consider the results of the extractor on the following text:

```bash
33 Attempts Off-Target; Tournament average 49.9
```

The result is:

```bash
decimal: 49.9
```

### FileName

Matches file names with common extensions, except for those contain white space characters. Hundreds of common file extensions are supported, including those for text, data, audio, video, language, and compressed files.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|name|Span|String corresponding to a file name.|

For example, consider the results of the extractor on the following text:

```bash
hdfs:/path/output.json
```

The result is:

```bash
name: output.json
```

### FileNameExtension

Matches common file extensions that begin with a period \(for example, .exe\). Matches file names with common extensions, except for those contain white space characters. Hundreds of common file extensions are supported, including those for text, data, audio, video, language, and compressed files.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|extension|Span|String corresponding to a file name extension.|

For example, consider the results of the extractor on the following text:

```bash
hdfs:/path/output.json
```

The result is:

```bash
extension: .json
```

### Integer

Matches any integer number expressed in numerals \(not words\).

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|integerview|Span|String corresponding to an integer number.|

For example, consider the results of the extractor on the following text:

```bash
SECTION 13 OR 15(d)
```

The result is:

```bash
integerview: 13
integerview: 15
```

### Number

Matches any integer or decimal number expressed in numerals \(not words\).

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|number|Span|String corresponding to a number.|

For example, consider the results of the extractor on the following text:

```bash
SECTION 13 OR 15(d)
```

The result is:

```bash
number: 13
number: 15
```

### Percentage

Matches any percentage.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|percentage|Span|String corresponding to a percentage number.|

For example, consider the results of the extractor on the following text:

```bash
Revenues from the Software segment were $6.5 billion, up 1 percent.
```

The result is:

```bash
percentage: 1 percent
```

## <a name="OtherEnt">Other extractors</a>

These pre-built extractors can be used to extract domain independent information such as dates, URLs, and emails. These extractors have coverage for English input documents.

The extractor libraries detag the data collection before extraction.

There are primary and secondary attributes for these extractors. Extractor results for attributes are determined by the available information within the input document. Primary attributes are always populated, and are the main purpose of the analysis. Secondary attributes are populated whenever possible, but the extractor might not always return results. In some of the following extractor examples, extraction results for the attributes are shown with their corresponding span offset values \(`[offset begin-offset end]`\).

### DateTime

The DateTime extractor identifies the mentions of date and time. The day, month, year, hours, minutes, seconds, and time zone of the date and time are secondary attributes that might be included in the information.

- The DateTime uses patterns that are based on numeric and special symbols, English days of the week, and English names of the months.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|datetime|Span|The datetime mention as extracted.|

For example, consider the results of the extractor on the following text:

```bash
Income from continuing operations for the year ended December 31, 2007 was $10.4 billion compared with $9.4 billion in the year-ago period, an increase of 11 percent.
```

The result is:

```bash
datetime: December 31, 2007 [53-70]
```

This example shows another output for this extractor:

```bash
Tomorrow (all times Eastern): 9 AM: Get Bold! IBM VP and author Sandy Carter hosts a coffee talk and book signing at New York City's Birch Coffee.

```

The result is:

```bash
datetime: 9 AM [30-34]
```

### EmailAddress

The EmailAddress extractor identifies the mentions of email addresses, which includes the local and the domain parts of the email address.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|emailAddress|Span|The email address mentions as extracted.|

This example shows the EmailAddress extractor:

```bash
IBM Easy Access
For large enterprise, government and education customers.
888-839-9289
ews@us.ibm.com

IBM Internal Contact : John Doe/Almaden/IBM 
```

The result for this extractor is:

```bash
emailAddress : ews@us.ibm.com [90-104]
```

### NotesEmailAddress

The NotesEmailAddress extractor identifies the mentions of Lotus Notes specific email addresses. The extracted information includes the local and domain parts of the Lotus Notes email address.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|notesEmailAddress|Span|The Notes email address mentions as extracted.|

For example, consider the following text:

```bash
IBM Easy Access
For large enterprise, government and education customers.
888-839-9289
ews@us.ibm.com

IBM Internal Contact : John Doe/Almaden/IBM
```

This is the output for this extractor:

```bash
notesEmailAddress : John Doe/Almaden/IBM [131-151]
```

### PhoneNumber

The PhoneNumber extractor identifies the mentions of phone numbers. A secondary attribute is the type of phone number that is extracted, such as mobile, fax, or office.

- The PhoneNumber extractor can be used with documents in English and German.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|phoneNumber|Span|The phone number mentions as extracted.|

For example, consider the following text:

```bash
IBM Easy Access
For large enterprise, government and education customers.
888-839-9289
ews@us.ibm.com

IBM Internal Contact : John Doe/Almaden/IBM 
```

This is the output for this extractor:

```bash
phoneNumber: 888-839-9289 [76-88]
```

### URL

The URL extractor identifies mention of URL values. Secondary attributes are the host and protocol value.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|url|Span|The URL mentions as extracted.|

For example, consider the following text:

```bash
These materials are available on the IBM investor relations Web site at www.ibm.com/investor and are being included in Attachment II ("Non-GAAP Supplementary Materials")to the Form 8-K that includes this press release and is being submitted today to the SEC.

```

The result of the URL extractor is:

```bash
url: www.ibm.com/investor [76-92]
```

## <a name="sentimentext">Sentiment extractors</a>

These pre-built extractors can be used to extract sentiment information from surveys or domain-independent content. These extractors have coverage for English input documents.

Before using sentiment extractors, set either a system property or an environment variable named `TEXTANALYTICS_HOME` to point to where the Text Analytics runtime RPM is installed on your cluster. By default, the RPM is installed at /usr/ibmpacks/current/text-analytics-runtime.

**Note:** Sentiment extractors are only supported on Windows or Linux operating systems.

### Sentiment\_Survey

The Sentiment\_Survey extractor extracts the expressions of sentiment from survey comments.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|text|Span|Mention of the sentiment.|
|patternName|Textual|Pattern used in detecting sentiment:-   **Target does positive**

Captures cases where the target performs a positive action. Example: `The design improved`.

- **Target does negative**

Captures cases where the target performs a negative action. Example: `The design worsened`.

|
|clue|Span|Indicator of the presence of the sentiment.|
|target|Span|Target object associated with the sentiment.|
|polarity|Textual|Polarity of the sentiment: positive or negative.|

For example, consider the results of the extractor on the following text:

```bash
My manager is good at setting priorities.
```

The result is:

```bash
text: My manager is good at setting priorities.
target: My manager
polarity: positive
patternName: Target is positive
clue: good at setting priorities
```

This example shows another output for this extractor:

```bash
I am happy to be on this project.
```

The result is:

```bash
text: I am happy to be on this project.
target: I
polarity: positive
patternName: Target is positive
clue: happy to be on this project
```

This example shows extraction of a negative sentiment:

```bash
The email system should be improved.
```

The result is:

```bash
text: The email system should be improved.
target: The email system
polarity: negative
patternName: Target is subjunctively positive
clue: improved
```

### Sentiment\_General

The Sentiment\_General extractor extracts the expressions of sentiment from textual content that is not specific to any domain.

|Output schema attribute|Type|Description|
|-----------------------|----|-----------|
|text|Span|Mention of the sentiment.|
|patternName|Span|Pattern used in detecting sentiment:-   **Target does positive**

Captures cases where the target performs a positive action. Example: `The design improved`.

- **Target does negative**

Captures cases where the target performs a negative action. Example: `The design worsened`.

|
|clue|Span|Indicator of the presence of the sentiment.|
|target|Span|Target object associated with the sentiment.|
|polarity|Textual|Polarity of the sentiment: positive or negative.|

For example, consider the results of the extractor on the following text:

```bash
The food was great.
```

The result is:

```bash
text: The food was great.
target: The food
polarity: positive
patternName: Target is positive
clue: great
```

This example shows another output for this extractor:

```bash
I love my new phone.
```

The result is:

```bash
text: I love my new phone.
target: I
polarity: positive
patternName: Speaker does positive on Target
clue: love
```

This example shows extraction of a negative sentiment:

```bash
This is the worst thing that ever happened to me.
```

The result is:

```bash
text: This is the worst thing that ever happened to me.
target: the worst thing that ever happened to me.
polarity: negative
patternName: Target has negative adjective
clue: worst
```

## <a name="basemodules">Base modules</a>

Base modules provide the fundamental extractors that are used by all other libraries above.

The following are the base modules:

- **InputDocumentProcessor**

    This module performs the initial document pre-processing. This module shows the view that contains the input document content after it extracts relevant markup content from the document.

- **CommonFeatures**

    This module extracts features that multiple modules use to build their extraction semantics. Examples of such features include cities, states, countries, stock tickers, and more.

- **Dictionaries**

    This module contains all of the dictionaries that are used by other named entity modules. Examples of such dictionaries include names of cities, famous personalities, sports teams, organizations, and more.

- **FinancialDictionaries**

    This module contains all of the dictionaries that are used by other financial entity modules. Examples of such dictionaries include clues for identifying financial events, and clues for identifying relevant terms across financial reports.

- **Linguistics**

    This module contains syntactic features that some of the other modules use for their own rules.

- **Disambiguation**

    This module is used to avoid overlap of the same extraction that belongs to multiple unrelated entities. For example, this module is used to disambiguate extractions for the Person entity against extractions for the Organization entity.

- **SentenceBoundary**

    This module contains extraction logic to identify boundaries in sentences, such as boundaries that are in plain text content and boundaries that are within markup-like content.

- **UDFs**

    This module contains user-defined functions that are used by the rest of the modules, such as `getAbbreviation` and `toUpperCase`.

- **Region, WaterBody, Facility**

    These modules are used by some internal modules to identify regions such as national parks, oceans, seas, and facilities such as buildings and known constructions.

- **EntitiesExport**

    This module is the sink module for this library. All of the views that represent both named and financial entities are aggregated in this module from the rest of the modules for export purposes.
