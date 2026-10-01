# Can Old Tests Do New Tricks for Resolving SWE Issues?

> Source: https://arxiv.org/abs/2510.18270
> Collected: 2026-10-01
> Published: 2025-10-21

Authors: Yang Chen, Toufique Ahmed, Reyhaneh Jabbarvand, Martin Hirzel. Accepted to the main technical track of FSE 2026 (v2, revised 8 May 2026).

## Abstract

Test suites in real-world projects are often large and achieve high code coverage, yet they remain insufficient for detecting all bugs. The abundance of unresolved issues in open-source project trackers highlights this gap. While regression tests are typically designed to ensure past functionality is preserved in the new version, they can also serve a complementary purpose: debugging the current version. Specifically, regression tests can (1) enhance the generation of reproduction tests for newly reported issues, and (2) validate that patches do not regress existing functionality. We present TestPrune, a fully automated technique that leverages issue tracker reports and strategically reuses regression tests for both bug reproduction and patch validation. A key contribution of TestPrune is its ability to automatically minimize the regression suite to a small, highly relevant subset of tests. Due to the predominance of LLM-based debugging techniques, this minimization is essential as large test suites exceed context limits, introduce noise, and inflate inference costs. TestPrune can be plugged into any agentic bug repair pipeline and orthogonally improve overall performance. As a proof of concept, we show that TestPrune leads to a 6.2%-9.0% relative increase in issue reproduction rate within the Otter framework and a 8.0%-12.9% relative increase in issue resolution rate within Agentless, SWE-Agent, and Trae agent on SWE-Bench Lite and SWE-Bench Verified benchmarks. Compared to the benefits, the model API cost overhead of TestPrune is minimal, at $0.02 and $0.05 per SWE-Bench instance using GPT-4o and Claude-3.7-Sonnet models, respectively.
