# A Systematic Literature Review of Code Hallucinations in LLMs: Characterization, Mitigation Methods, Challenges, and Future Directions for Reliable AI

> Source: https://arxiv.org/abs/2511.00776
> Collected: 2026-09-19
> Published: 2025-11-02

Authors: Cuiyun Gao, Guodong Fan, Chun Yong Chong, Shizhan Chen, Chao Liu, David Lo, Zibin Zheng, Qing Liao.

## Abstract

Model hallucination is one of the most critical challenges faced by Large Language Models (LLMs), especially in high-stakes code intelligence tasks. As LLMs become increasingly integrated into software engineering tasks, understanding and mitigating hallucination in code becomes essential. In this survey, we provide a systematic review of hallucination phenomena in code-oriented LLMs from four key perspectives. First, we begin by surveying 60 papers to define hallucination in the context of code and summarize its primary causes, such as data noise, exposure bias, and insufficient semantic grounding, while also tracing recent trends in literature across natural language processing (NLP) and software engineering communities. Second, we review model hallucination surveys in a broader span and summarize representative hallucination mitigation strategies, such as knowledge-enhanced generation, constrained decoding, and post-editing. Third, we review approaches targeted for code intelligence and highlight code-specific challenges that aggravate hallucination, including syntax sensitivity, strict type systems, and dependence on external libraries. Meanwhile, we analyze how emerging code intelligence tasks, e.g., program analysis, symbolic execution, and unit testing, are utilized to detect and mitigate hallucinations. Fourth, we summarize current evaluation benchmarks, ranging from static metrics to dynamic checks, e.g., compilation and execution correctness, and emphasize the need for hallucination-oriented benchmarks.
