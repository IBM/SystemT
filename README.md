# SystemT

SystemT Runtime Engine


This repository contains the SystemT runtime engine and prebuilt extractor libraries as maintained and enhanced in Watson NLP, starting 2018.

SystemT is a language and runtime engine for developing Natural Language Processing algorithms. The primary language used by the engine is AQL (Annotation Query Language).

# Development

## Main Modules

1. system-t-runtime: the SystemT Runtime engine
2. simple-regex: the accelerated regex engine of SystemT
3. rbr-annotation-service-core


## Build Locally

```bash
$ mvn clean install -f SystemT/pom.xml -s build/maven-settings.xml -Dmaven.test.skip=true
```

To build Sentiment on Linux (to obtain the correct output for regression tests):
```bash
./build/build_in_docker.sh
```

## Eclipse Settings
Eclipse -> Preference -> Java -> Formatter -> Import [eclipse-java-google-style.xml]

