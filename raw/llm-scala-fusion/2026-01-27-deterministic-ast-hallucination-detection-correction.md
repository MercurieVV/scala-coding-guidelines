# Detecting and Correcting Hallucinations in LLM-Generated Code via Deterministic AST Analysis

> Source: https://arxiv.org/abs/2601.19106
> Collected: 2026-09-19
> Published: 2026-01-27

Authors: Dipin Khati, Daniel Rodriguez-Cardenas, Paul Pantzer, Denys Poshyvanyk.

## Abstract

Large Language Models (LLMs) for code generation boost productivity but frequently introduce Knowledge Conflicting Hallucinations (KCHs), subtle, semantic errors, such as non-existent API parameters, that evade linters and cause runtime failures. Existing mitigations like constrained decoding or non-deterministic LLM-in-the-loop repair are often unreliable for these errors. This paper investigates whether a deterministic, static-analysis framework can reliably detect and auto-correct KCHs. We propose a post-processing framework that parses generated code into an Abstract Syntax Tree (AST) and validates it against a dynamically-generated Knowledge Base (KB) built via library introspection. This non-executing approach uses deterministic rules to find and fix both API and identifier-level conflicts. On a manually-curated dataset of 200 Python snippets, our framework detected KCHs with 100% precision and 87.6% recall (0.934 F1-score), and successfully auto-corrected 77.0% of all identified hallucinations. Our findings demonstrate that this deterministic post-processing approach is a viable and reliable alternative to probabilistic repair, offering a clear path toward trustworthy code generation.
