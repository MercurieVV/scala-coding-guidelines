# SlopCodeBench: Benchmarking How Coding Agents Degrade Over Long-Horizon Iterative Tasks (excerpts)

> Source: https://arxiv.org/abs/2603.24755 (Orlanski, Roy, Yun, Shin, Gu, Ge, Adila, Sala, Albarghouthi — University of Wisconsin–Madison, Washington State University, MIT); excerpts via the arXiv HTML v1
> Collected: 2026-10-11
> Published: 2026-03-25

## Design

SlopCodeBench is a language-agnostic benchmark comprising 20 problems and 93 checkpoints, in which agents repeatedly
extend their own prior solutions under evolving specifications that force architectural decisions without prescribing
internal structure. Each problem has 3–8 sequential checkpoints. "Problems specify only observable behavior at a CLI or
API boundary, leaving internal structure unconstrained." Test suites are hidden; agents see only specification prose and
examples. Test categories: Core, Error, Functionality (hidden), and Regression (prior checkpoints). No test-suite
feedback, type checker or linter is given to the agent during execution. Python was evaluated.

## Metrics

- Structural erosion: the fraction of total code complexity concentrated in high-complexity functions (cyclomatic
  complexity > 10); mass(f) = CC(f) × √SLOC(f).
- Verbosity: "Fraction of code hitting AST-grep patterns or structural duplication, normalized by lines of code."

## Results

- "No agent solves any problem end-to-end across 11 models; the highest checkpoint solve rate is 17.2%."
- "Quality degrades steadily: erosion rises in 80% of trajectories and verbosity in 89.8%."
- Against 48 open-source Python repositories, agent code is 2.2x more verbose and markedly more eroded. Tracking 20 of
  those repositories over time shows that human code stays flat, while agent code deteriorates with each iteration.
- Models include Claude Sonnet 4.5, Sonnet 4.6, Opus 4.5, Opus 4.6, GPT 5.1–5.4 variants with Codex, GLM 4.7.
- Prompt intervention (anti_slop, plan_first): "Quality-aware prompts reduce initial verbosity and erosion" but
  "degradation lines track baseline slope almost exactly." Pass rates showed no consistent improvement despite halving
  erosion metrics.
- The authors conclude that "pass-rate benchmarks systematically undermeasure extension robustness."
