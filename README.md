# SystemT

SystemT Runtime Engine

This repository contains the SystemT runtime engine.
SystemT is a system for specifying and executing rule-based NLP models. The NP models are specified in the Annotation Query Language (AQL).

# Development

## Main Modules

1. system-t-runtime: the SystemT Runtime engine
2. simple-regex: the accelerated regex engine of SystemT
3. rbr-annotation-service-core: a simple wrapper for executing AQL models configured using a simple JSON specification


## Build Locally

```bash
mvn clean install -f SystemT/pom.xml -s build/maven-settings.xml -Dmaven.test.skip=true
```

## Eclipse Settings
Eclipse -> Preference -> Java -> Formatter -> Import [eclipse-java-google-style.xml]

