# SWE-Chain: Benchmarking Coding Agents on Chained Release-Level Package Upgrades — excerpts

> Source: https://arxiv.org/abs/2605.14415 (Lam et al., CUHK)
> Collected: 2026-10-10
> Published: 2026-05-14

Excerpts (abstract, §4.1, Table 4 and §4.3, §4.4), text extracted from the v1 PDF.

## Abstract

Coding agents powered by large language models are increasingly expected to
perform realistic software maintenance tasks beyond isolated issue resolution.
Existing benchmarks have shifted toward realistic software evolution, but they
rarely capture continuous maintenance at the granularity of package releases,
where changes are bundled, shipped, and inherited by subsequent versions. We
present SWE-C HAIN, a benchmark for evaluating agents on chained release-level
package upgrades, where each transition builds on the agent’s prior codebase. To
produce upgrade specifications, we design a divide-and-conquer synthesis pipeline
that aligns release notes with code diffs for each version transition, ensuring
the requirements are grounded in actual code changes, informative to agents,
and feasible to implement. SWE-C HAIN contains 12 upgrade chains across 9
real Python packages, with 155 version transitions and 1,660 grounded upgrade
requirements. Across nine frontier agent-model configurations, agents achieve an
average of 44.8% resolving, 65.4% precision, and 50.2% F1 under the Build+Fix
regime, with Claude-Opus-4.7 (Claude Code) leading at 60.8% resolving, 80.6%
precision, and 68.5% F1. These results show that SWE-C HAIN is both feasible
and discriminative, and reveal that current agents still struggle to make correct
upgrades across chained package releases without breaking existing functionality.

## 4.1 Overall performance (excerpt)


Table 3 presents the overall results across 9 agent-model configurations under two regimes. Across
all agents and chains, the average Build+Fix resolving rate is only 44.8%, with 65.4% precision
and 50.2% F1-score. The higher precision suggests that successful changes are relatively targeted
and non-regressive, while the resolving rate indicates that agents still leave more than half of the
upgrade-related test behaviors unresolved, highlighting the challenge of continuous version-upgrade
maintenance for current agents. Nevertheless, Claude-Opus-4.7 (Claude Code) achieves the best
performance, with 60.8% resolving, 80.6% precision, and 68.5% F1-score, followed by GPT-5.5
(Codex). We also report Final Passing as an auxiliary measure of final codebase health over the chain,
which broadly follows the F1-score trend. The substantially stronger performance of frontier agents
shows that SWE-C HAIN is a feasible and discriminative benchmark.
We also observe three clear patterns: (1) frontier closed-source models outperform the other models
in their respective families, and closed-source models generally outperform GLM-5.1 and MiniMax-
M2.7-HS; (2) GPT-5.3-Codex slightly outperforms GPT-5.4 under the Codex CLI, suggesting that
Codex-native agentic variants can remain competitive with newer general-purpose models in continu-
ous maintenance; and (3) GPT-5.4 performs better with Codex than OpenCode, suggesting better
alignment between the native agent CLI and its underlying model. These patterns suggest that
SWE-C HAIN can distinguish differences in model capability, coding specialization, and agent CLIs.
Across agents, Build+Fix improves average precision from 54.0% to 65.4%, while average resolving
increases more modestly from 39.7% to 44.8%, indicating that the additional fixing mainly addresses
brittle failures and regressions rather than directly solving upgrade requirements. Since reasonable

## Table 4. Effect of specification granularity on the pytest 8.0 chain (Build+Fix)

 Table 4: Effect of specification granularity on the pytest 8.0 chain under the Build+Fix regime.
Model (Agent) Metric L1 L2 L3 L4 L5
Resolving 44.5% 62.6% 66.1% 66.7% 73.9%
Claude-Opus-4.7 Precision 8.9% 7.8% 88.1% 89.2% 93.5%
F1 14.8% 13.9% 75.5% 76.3% 82.5%
Resolving 47.4% 44.5% 53.4% 68.1% 67.8%
GPT-5.5 Precision 9.9% 9.5% 92.1% 93.3% 89.7%

## 4.3 Effect of specification granularity


4.3 Effect of Specification Granularity

To study how the specification granularity affects agent performance, we construct five specification
variants on the pytest (ver. 8.0) chain, with examples shown in Appendix J.2:

• L1: Release notes with GitHub issues and PRs, simulating SWE-EVO setting [Thai et al., 2026].
• L2: Problem statements only.
• L3 (default setting in §4.1): Problem statements with conceptual expectations and constraints.
• L4: Problem statements with grounded (concrete API/behavior details) expectations and constraints.
• L5: Problem statements, grounded expectations and constraints, behaviors, and acceptance criteria.

As shown in Table 4, the largest gap appears from L2 to L3, where expectations and constraints
improve precision much more than resolving rate for both agents, as they clarify the intended behavior,
implementation boundary, and preservation constraints, making the specification more informative
and feasible for agents to conduct the version transition. While L1 uses raw changelog entries with
linked GitHub issues and PRs, which contain the issues-driven requirements and useful evidence, it
is noisy, unevenly structured, and may expose implementation details. Meanwhile, higher-granularity
specifications provide more grounded information and generally improve performance, but they
are closer to oracle-style guidance, making them better suited for assessing agents’ implementation
capability than realistic upgrade workflows. However, they remain useful beyond upper-bound probes,
especially for reliability evaluations where the objective is to sustain agent progress over many steps
without excessive early collapse.
Key Finding: Granularity Control
Raw issue/PR artifacts are poor specifications for release-level upgrades, while expectations
and constraints are essential to avoid regressions. Our default L3 setting balances guidance
and leakage control without relying on the more oracle-like information in L4 and L5.

## 4.4 Efficiency (excerpt)

4.4 Efficiency and Resource Usage

Table 5 reports the efficiency, cost, token usage, and tool-call activity of each agent averaged per
chain. The strongest configurations with xhigh reasoning effort are also among the most expensive:
Claude-Opus-4.7 and GPT-5.5 lead in performance but cost $150.39 and $131.34 per chain on
average. However, higher resource usage does not inevitably imply better results. For example,
Claude-Sonnet-4.6 has the longest runtime and high token usage, and MiniMax-M2.7-HS uses more
tokens and tool calls than OpenCode GPT-5.4, but both perform substantially worse than top models.
