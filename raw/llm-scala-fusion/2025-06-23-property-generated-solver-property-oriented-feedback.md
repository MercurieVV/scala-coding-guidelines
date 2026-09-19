# Effective LLM Code Refinement via Property-Oriented and Structurally Minimal Feedback (Property-Generated Solver)

> Source: https://arxiv.org/abs/2506.18315 (v1 title: "Use Property-Based Testing to Bridge LLM Code Generation and Validation")
> Collected: 2026-09-19
> Published: 2025-06-23

Authors: Lehan He, Zeren Chen, Zhe Zhang, Xiang Gao, Lu Sheng. v1 23 Jun 2025; v2 1 May 2026.

## Abstract

LLMs excel at code generation, yet ensuring the functional correctness of their outputs remains a persistent challenge. While recent studies have applied Test-Driven Development (TDD) to refine code, these methods are often undermined by poor feedback quality, stemming from the scarcity of high-quality test cases and noisy signals from auto-generated ones. In this work, we shift the focus from test quantity to feedback quality. We introduce the Property-Generated Solver (PGS), a novel paradigm designed to generate highly effective feedback via two principles: it must be property-oriented, to provide semantic guidance beyond simple I/O mismatches, and structurally minimal, to reduce cognitive load and isolate root causes. PGS operates by checking high-level program properties (e.g., a sorting function must produce a non-decreasing sequence) then providing the simplest failing counterexample to the LLM. By adhering to these principles, this targeted feedback mechanism leads to significant performance gains. Specifically, PGS achieves an improvement of up to 13.4% in pass@1 against other TDD-based methods and an over 64% fix rate on problems where the model initially failed. This property-driven, minimal feedback steers LLMs toward correct and generalizable solutions. Across diverse benchmarks, PGS demonstrates superior performance, achieving a bug fix rate 1.4x-1.6x higher than the strongest debugging-based approaches and establishing a new state-of-the-art in automated code refinement.

## From the v1 framing (search-result summary)

Properties are often simpler to define and verify than directly predicting exhaustive test oracles, breaking the "cycle of self-deception" where tests might share flaws with the code they are meant to validate.
