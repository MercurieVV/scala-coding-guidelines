# The Bitter Lesson of Tool Calling

> Source: https://arxiv.org/abs/2608.06370
> Collected: 2026-10-01
> Published: 2026-08-06

Authors: Ishan Patel, Sahil Sen, Elias Lumer, Vamse Kumar Subbiah.

## Abstract

Tool use transforms LLMs into agents that act beyond their training data, and for code-capable models, programmatic tool calling extends this further by replacing rigid JSON calls with scripts that chain and parallelize naturally. However, a systematic evaluation of tools as code on an established benchmark across current and prior model generations under real-world task conditions has not been conducted. In this work, we empirically compare programmatic tool calling (PTC) to native JSON tool calling across 14 language models on BFCL v4. In the programmatic tool calling paradigm, tools are exposed as typed Python stubs that the model invokes through code, with execution and results handled in a single agent turn. Programmatic tool calling matches or exceeds native JSON tool calling in 11 of 14 models on BFCL v4, with the GPT-5.6 family achieving a 10.6% improvement over the JSON tool calling baseline. Further, it matches or outperforms baseline in 13 of 14 models under parallel fan-out, and holds stable under context rot conditions where baseline degrades 2.3% on average. Our results demonstrate that programmatic tool calling is a viable and robust alternative to JSON tool calling, with performance tracking model capability across release generations.

## Excerpts (full text, https://arxiv.org/html/2608.06370)

"Programmatic tool calling underperforms JSON tool calling by an absolute 14.1% on average in the parallel and parallel_multiple categories, while gaining an absolute 10.0% in live_multiple."

"Under context flooding, programmatic tool calling holds stable while JSON tool calling degrades 2.3% on average and the filesystem-discovery approach degrades 32%, demonstrating robustness to adversarial context load."
