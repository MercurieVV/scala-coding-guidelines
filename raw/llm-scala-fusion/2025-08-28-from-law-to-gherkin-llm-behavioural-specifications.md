# From Law to Gherkin: A Human-Centred Quasi-Experiment on the Quality of LLM-Generated Behavioural Specifications from Food-Safety Regulations

> Source: https://arxiv.org/abs/2508.20744
> Collected: 2026-09-19
> Published: 2025-08-28

Authors: Shabnam Hassani, Mehrdad Sabetzadeh, Daniel Amyot. v1 28 Aug 2025; v2 11 Mar 2026.

## Abstract

Context: Laws and regulations increasingly shape software design, development, and quality assurance in regulated domains. Because legal provisions are written in technology-neutral language, deriving concrete specifications, requirements, and acceptance criteria to verify software compliance is difficult and error-prone. Recent advances in generative AI, especially large language models (LLMs), may help automate this process. Objective: We present the first systematic human-subject evaluation of LLMs' ability to derive Gherkin behavioural specifications from legal texts using a quasi-experimental design. Gherkin is a domain-specific language for scenario-based system behaviour descriptions in Given-When-Then form and is well suited to automation in software development. Methods: Ten participants evaluated 60 Gherkin specifications generated from food-safety regulations by Claude and Llama. Each participant assessed 12 specifications across five criteria: relevance, clarity, completeness, singularity, and time savings. Each specification was evaluated by two participants, yielding 120 assessments with quantitative ratings and qualitative feedback. Results: Ratings were uniformly high in the top two categories: relevance 95%, clarity 100%, completeness 94.2%, singularity 93.4%, and time savings 91.7%. No statistically reliable differences were found across participants or between LLMs. Qualitative feedback noted occasional omissions, hallucinations, and mixed intents, underscoring the need for human oversight, especially in safety-critical domains. Conclusion: In food safety, LLMs can assist in deriving Gherkin specifications from legal texts, but omissions and hallucinations require systematic human review.

## Related (search-result summary of other BDD+LLM work)

- "First Experiments on Automated Execution of Gherkin Test Specifications with Collaborating LLM Agents" (ACM A-TEST 2024, doi 10.1145/3678719.3685692): LLM agents (AutoGen) execute Gherkin scenarios by exploring the system under test and generating test code on the fly; high success rates on simple scenarios, but difficulties remain regarding failure scenarios and fault detection.
- Spec-driven development (SDD) frameworks — GitHub Spec Kit, Kiro, OpenSpec, BMAD — converge on the same four-phase loop: spec first (behavior, architecture, edge cases, constraints), then an agent produces code, tests, docs.
