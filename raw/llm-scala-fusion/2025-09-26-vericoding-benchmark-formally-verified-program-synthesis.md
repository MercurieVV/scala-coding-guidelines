# A benchmark for vericoding: formally verified program synthesis

> Source: https://arxiv.org/abs/2509.22908
> Collected: 2026-09-19
> Published: 2025-09-26

Authors: Sergiu Bursuc, Theodore Ehrenborg, Shaowei Lin, Lacramioara Astefanoaei, Ionel Emilian Chiosa, Jure Kukovec, Alok Singh, Oliver Butterley, Adem Bizid, Quinn Dougherty, Miranda Zhao, Max Tan, Max Tegmark.

## Abstract

We present and test the largest benchmark for vericoding, LLM-generation of formally verified code from formal specifications - in contrast to vibe coding, which generates potentially buggy code from a natural language description. Our benchmark contains 12,504 formal specifications, with 3,029 in Dafny, 2,334 in Verus/Rust and 7,141 in Lean. Of these, 6,174 are new unseen problems. We find vericoding success rates of 27% in Lean, 44% in Verus/Rust and 82% in Dafny using off-the-shelf LLMs. Adding natural-language descriptions does not significantly improve performance. We also find that LLM progress has improved progress on pure Dafny verification from 68% to 96% over the past year.
