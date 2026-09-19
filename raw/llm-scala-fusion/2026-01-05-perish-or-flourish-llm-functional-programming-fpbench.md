# Perish or Flourish? A Holistic Evaluation of Large Language Models for Code Generation in Functional Programming

> Source: https://arxiv.org/abs/2601.02060 (results from https://arxiv.org/html/2601.02060v1)
> Collected: 2026-09-19
> Published: 2026-01-05

Authors: Nguyet-Anh H. Lang, Eric Lang, Thanh Le-Cong, Bach Le, Quyet-Thang Huynh. Benchmark code: https://github.com/thanhlecongg/FPEval

## Abstract

Functional programming provides strong foundations for developing reliable and secure software systems, yet its adoption remains not widespread due to the steep learning curve. Recent advances in Large Language Models (LLMs) for code generation present new opportunities to lower these barriers. However, extensive evaluations of LLMs largely focus on imperative programming languages, and their capabilities in functional programming languages (FP) remain underexplored. To address this gap, we introduce FPEval, a holistic evaluation framework built on FPBench, a new benchmark of 721 programming tasks across three difficulty levels on three mainstream FP languages: Haskell, Ocaml and Scala. FPEval provides compehensive evaluation infrastructures with both test validations with comprehensive test suites and static analysis tools to assess both functional correctness and code style and maintainability. Using this framework, we evaluate state-of-the-art LLMs, including GPT-3.5, GPT-4o, and GPT-5, for code generation in functional programming languages and Java as an imperative baseline. Our results demonstrate that LLM performance in functional programming improves substantially with model advancement; however, error rates remain significantly higher in purely functional languages (Haskell and OCaml) than in hybrid (Scala) or imperative (Java) languages.

## Key results (extracted from the HTML full text)

- Task difficulty split: 184 easy, 346 medium, 191 hard.
- Pass rates by language:
  - GPT-3.5: Haskell 14.15%, OCaml 9.43%, Scala 19.28%, Java 22.19%
  - GPT-4o: Haskell 27.18%, OCaml 36.20%, Scala 38.83%, Java 43.69%
  - GPT-5: Haskell 42.34%, OCaml 52.16%, Scala 58.36%, Java 61.14%
- Compilation errors: GPT-3.5 error rates of "43.14% and 25.5% in OCaml and Haskell"; GPT-4o "reduce[d] these rates to approximately 21%"; GPT-5 achieved "4.62% in Java" but "24.28%" in Haskell.
- Code quality: "GPT-5 shows a marked decline to 44%" clean-code ratio in OCaml versus older models at 63% and 61% — a trade-off between correctness and maintainability the authors describe as a "Reward Hacking" phenomenon: as pass rates rise, code quality often declines.
- Imperative bias: GPT-5 produced "94%, 88%, and 80% of the generated code in Scala, Haskell, and OCaml" containing imperative patterns. LLMs frequently generate non-idiomatic functional code that follows imperative patterns.
- Self-repair: LLMs can partially self-repair both correctness and quality issues when provided with static analysis feedback and hand-crafted instructions for common types of issues. Reported issue-rate changes under instruction-guided repair: "from 77% → 57% in Scala, 63% → 51% in Java, 46% → 45% in Haskell".
