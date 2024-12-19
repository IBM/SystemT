---
layout: home
title: In depth rule-based modeling techniques
nav_order: 8
has_children: true
description: SystemT and AQL (Annotation Query Language) home page
---

# SystemT and AQL model development

SystemT provides:
- AQL (Annotation Query Language): a declarative rule language for expressing transparent, explainable NLP algorithms on top of NLP Primitives and Operators. 
AQL enables visual tooling for rule-based NLP pipeline, and provides feature extraction for Machine Learning models.
- A compiler, optimizer and runtime engine that executes AQL models.

SystemT was originally developed in IBM Research - Almaden and is now owned and maintained by a team with 15+ years of experience in NLP.

## Features

### AQL (Annotation Query Language)

AQL is a declarative rule language, on top of NLP Primitives (e.g., tokens, parts of speech, shallow semantic parsing) and NLP Operators (e.g., dictionaries, regular expressions, span operators, etc.).

AQL is a pure declarative language: it separates semantics (the “what”) from execution strategy (the “how”). This enables an entire array of automatic optimizations.

### SystemT Optimizer

The Optimizer compiles AQL code to an optimized algebraic execution plan. The design of the SystemT optimizer is similar to SQL optimizers in Relational Database Systems, but with text-centric optimizations. 

Example optimizations include Shared Dictionary Matching, Shared Regex Matching, Regex Strength Reduction, Conditional Evaluation, and many others.

These optimizations achieve orders of magnitude speed-up compared to hand-tuned execution plans. They enable NLP engineers focus on the semantics of the NLP model, without worrying about hand-tuning the execution.

### SystemT Runtime

The SystemT runtime engine is a light-weight, high speed, low memory footprint runtime engine for executing compiled AQL code.

## Getting Started

### 1. Add SystemT to Your Project

- [Learning AQL](Learning-AQL)
- [10 Minutes to SystemT (Java API)](10-minutes-to-systemt/10-Minutes-to-SystemT-(Java-API))

