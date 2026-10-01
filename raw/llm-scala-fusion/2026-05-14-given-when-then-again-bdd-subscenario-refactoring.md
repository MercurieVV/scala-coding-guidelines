# Given, When, Then, Again: Mining Subscenario Refactoring Candidates in Behaviour-Driven Test Suites with ML Classifiers and LLM-Judge Baselines

> Source: https://arxiv.org/abs/2605.14568
> Collected: 2026-10-01
> Published: 2026-05-14

Authors: Mughal, Ali Hassaan; Fatima, Noor; Bilal, Muhammad

## Abstract

Context. Behaviour-Driven Development (BDD) test suites accumulate duplicated step subsequences. Three published refactoring patterns are available (within-file Background, within-repo reusable-scenario invocation, cross-organisational shared higher-level step), but no prior work automates which recurring subsequences are worth extracting or which mechanism applies. Objective. Rank recurring step subsequences ("slices") by refactoring suitability (extraction-worthy), pre-map each to one of the three patterns, and quantify prevalence across the public BDD ecosystem. Method. Every contiguous L-step window (L in [2, 18]) in a 339-repository / 276-upstream-owner Gherkin corpus is keyed by paraphrase-robust cluster identifiers and counted under three scopes. SBERT / UMAP / HDBSCAN clustering recovers paraphrase-equivalent slices. Three authors label a stratified 200-slice pool against a written rubric. An XGBoost extraction-worthy classifier trained under 5-fold cross-validation is compared with a tuned rule baseline and two open-weight Large Language Model (LLM) judges. Results. The miner produces 5,382,249 slices collapsing to 692,020 recurring patterns. Three-author Fleiss' kappa = 0.56 (extraction-worthy) and 0.79 (mechanism). The classifier reaches out-of-fold F1 = 0.891 (95% CI [0.852, 0.927]), outperforming both the rule baseline (F1 = 0.836, p = 0.017) and the better LLM judge (F1 = 0.728, p = 1.5e-4). 75.0%, 59.5%, and 11.7% of scenarios carry a within-file Background, within-repo reusable-scenario, and cross-organisational shared-step candidate, respectively; the figures are stable under a sweep of the classifier decision threshold. Conclusion. Paraphrase-robust subscenario discovery yields a corpus-wide census of BDD refactoring candidates; pipeline, classifier predictions, labelled pool, and rubric are released under Apache-2.0.

## Excerpts (verbatim, from the HTML full text)


