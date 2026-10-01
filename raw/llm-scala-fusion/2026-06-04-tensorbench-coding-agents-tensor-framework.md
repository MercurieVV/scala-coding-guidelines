# TensorBench: Benchmarking Coding Agents on a Compiler-Based Tensor Framework

> Source: https://arxiv.org/abs/2606.05570
> Collected: 2026-10-01
> Published: 2026-06-04

Authors: Bobby Yan and 1 other author.

## Abstract

Repository-level coding benchmarks face a trade-off between task difficulty and evaluation reliability: tasks that challenge frontier models often involve large codebases with incomplete test coverage, while human review does not scale. We introduce TensorBench, a benchmark of 199 feature-addition and refactoring tasks on an open-source compiler-based tensor framework that extends PyTorch with first-class support for dense and sparse tensors. Tasks cover new sparse formats, dense optimization passes, IR transformations, scheduler changes, runtime components, and high-level numerical operators. TensorBench grades each run by applying the agent's patch and running the framework's test suite, which includes the pre-existing randomized regression tests and any tests the agent adds. For feature-addition tasks, a pass means that the patched repository preserves the tested pre-existing behavior and satisfies the agent-added checks for the requested feature. We evaluate seven coding agents spanning three frontier model families and one open-weight model. Pass rates under this criterion range from 64.8% for the strongest agent to 22.1% for the weakest. Agents pass different subsets of tasks: pairwise Cohen's κ ranges from −0.07 to 0.43, with κ = 0.05 for the two strongest agents.

## Excerpt (full text, PDF, Table 5 and "Test-Preservation Across Generations")

Table 5: Fraction of applied patches that break ≥ 1 pre-existing test. Lower is better.

| Agent | Break-rate |
|---|---|
| Claude 4.7 | 32/199 = 16.1% |
| Codex 5.5 | 47/199 = 23.6% |
| Claude 4.6 | 54/199 = 27.1% |
| Codex 5.3 | 56/199 = 28.1% |
| Codex 5.4 | 73/199 = 36.7% |
| Gemini 3.1 | 89/199 = 44.7% |
| Qwen3 | 90/199 = 45.2% |

"Table 5 shows the fraction of applied patches that introduce at least one regression in the pre-existing suite. The newest agent (Claude 4.7, 16.1%) breaks pre-existing tests less often than its predecessor (Claude 4.6, 27.1%); within the Codex family, Codex 5.5 (23.6%) improves on both Codex 5.3 (28.1%) and Codex 5.4 (36.7%). These improvements appear on a metric that bug-fix benchmarks do not usually isolate, since those benchmarks treat existing tests as the success criterion rather than as a robustness probe."
