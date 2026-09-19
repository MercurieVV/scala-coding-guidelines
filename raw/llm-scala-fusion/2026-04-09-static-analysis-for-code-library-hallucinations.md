# An Empirical Analysis of Static Analysis Methods for Detection and Mitigation of Code Library Hallucinations

> Source: https://arxiv.org/abs/2604.07755
> Collected: 2026-09-19
> Published: 2026-04-09

Authors: Clarissa Miranda-Pena, Andrew Reeson, Cécile Paris, Josiah Poon, Jonathan K. Kummerfeld. Submitted 9 Apr 2026 (v1), revised 10 Apr 2026 (v2).

## Abstract

Despite extensive research, Large Language Models continue to hallucinate when generating code, particularly when using libraries. On NL-to-code benchmarks that require library use, we find that LLMs generate code that uses non-existent library features in 8.1-40% of responses. One intuitive approach for detection and mitigation of hallucinations is static analysis. In this paper, we analyse the potential of static analysis tools, both in terms of what they can solve and what they cannot. We find that static analysis tools can detect 16-70% of all errors, and 14-85% of library hallucinations, with performance varying by LLM and dataset. Through manual analysis, we identify cases a static method could not plausibly catch, which gives an upper bound on their potential from 48.5% to 77%. Overall, we show that static analysis methods are cheap method for addressing some forms of hallucination, and we quantify how far short of solving the problem they will always be.
