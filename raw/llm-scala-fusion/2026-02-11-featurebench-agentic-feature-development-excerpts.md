# FeatureBench: Benchmarking Agentic Coding for Complex Feature Development — excerpts

> Source: https://arxiv.org/abs/2602.10975 (Zhou et al., ICLR 2026)
> Collected: 2026-10-10
> Published: 2026-02-11

Excerpts (abstract, Tables 7 and 8, failure-case analysis, interface ablation), text extracted from the v1 PDF.

## Abstract


Agents powered by large language models (LLMs) are increasingly adopted in
the software industry, contributing code as collaborators or even autonomous de-
velopers. As their presence grows, it becomes important to assess the current
boundaries of their coding abilities. Existing agentic coding benchmarks, how-
ever, cover a limited task scope, e.g., bug fixing within a single pull request (PR),
and often rely on non-executable evaluations or lack an automated approach for
continually updating the evaluation coverage. To address such issues, we propose
FeatureBench, a benchmark designed to evaluate agentic coding performance in
end-to-end, feature-oriented software development. FeatureBench incorporates
an execution-based evaluation protocol and a scalable test-driven method that
automatically derives tasks from code repositories with minimal human effort.
By tracing from unit tests along a dependency graph, our approach can identify
feature-level coding tasks spanning multiple commits and PRs scattered across
the development timeline, while ensuring the proper functioning of other features
after the separation. Using this framework, we curated 200 challenging evaluation
tasks and 3825 executable environments from 24 open-source repositories in the
first version of our benchmark. Empirical evaluation reveals that the state-of-the-
art agentic model, such as Claude 4.5 Opus, which achieves a 74.4% resolved rate
on SWE-bench, succeeds on only 11.0% of tasks, opening new opportunities for
advancing agentic coding. Moreover, benefiting from our automated task collec-
tion toolkit, FeatureBench can be easily scaled and updated over time to mitigate
data leakage. The inherent verifiability of constructed environments also makes
our method potentially valuable for agent training.


## Table 7 and SWE-bench comparison

Without Interface Visible Unit Tests
Model
% Resolved % Passed # Token I/O % Resolved % Passed # Token I/O
†
Gemini-3-Pro-Preview 3.3 (-6.7) 25.3 (-18.1) 7.0M / 10K 60.0 (+50.0) 80.6 (+37.2) 6.9M / 18K
GPT-5.1-Codex‡ 16.7 (-3.3) 42.0 (-18.2) 7.6M / 38K 63.3 (+43.3) 80.9 (+20.7) 8.2M / 46K

Table 7: Performance comparison of lite set with visible unit tests and without interface. Models


For a more grounded analysis, we further compare the performance of agents on the SWE-bench
and our FeatureBench . To draw a more aligned comparison, we construct a subset of our bench-
mark including only repositories shared with SWE-bench. The results in Table 4 reveals a stark
performance gap between the two benchmarks in terms of resolved rate. Specifically, the most ca-
pable Claude Opus 4.5 only resolves 5.2% of the tasks in our FeatureBench subset in contrast to the
74.40% on the SWE-bench. This indicates the highly challenging nature of our benchmark, which
provides considerable room for future improvement and establishes a rigorous testbed to measure
the upper bound of existing agents.

## 4.1.3 Failure cases analysis

4.1.3 FAILURE C ASES A NALYSIS

We conduct a failure case analysis based on the results in our full set from the Claude Opus 4.5
model, leading to the following findings.
Limitations in Code Reasoning. As shown in Figure 4, the dominance of NameError suggests
that current LLMs still struggle with cross-file dependency resolution. When a feature spans multiple
files, models often focus on local edits without consistently re-establishing all necessary references,
leading to unresolved symbols and frequent name-related failures. This highlights a key limitation
in maintaining coherent program context beyond a single file.
The “Idle Habits” of LLMs. We also find that current LLMs exhibit a tendency toward “laziness”.
For example, they often resort to guessing (even hallucinating) the interface or attributes of compo-
nents defined across files, rather than performing the actual file reading required to retrieve precise
prototypes and members. This behavior leads to a considerable number of both TypeError and
AttributeError occurrences.
Appropriate Information in FeatureBench. Among the remaining failures, AssertionError
becomes the most frequent category. This suggests that a substantial portion of LLM-generated so-
lutions can run to the assertion checkpoints without earlier runtime crashes. This result underscores
that FeatureBench can effectively provide the LLMs with appropriate information to generate com-
plete programs.

## 4.2.1 Without Interface (excerpt)

Without Interface. We performed an ablation study to assess the role of explicit interface speci-
fication in agent performance. For controlled comparison, we employed the lite set, systematically
removing function signatures and call path annotations from the prompts. As shown in Table 7, this

removal leads to a marked decline in task success rates. The results confirm that clearly defined
interfaces are critical for enabling effective reasoning and program synthesis by LLM-based agents.

## Table 8. Performance by difficulty level


Difficulty Scaffold Models % Resolved % Passed
OpenHands Qwen3-Coder-480B-A35B-Instruct 3.6 22.4
OpenHands DeepSeek-V3.2 4.8 20.8
OpenHands Claude Opus 4.5 11.4 46.2
OpenHands Gemini 3 Pro† 4.2 29.0
L1 Gemini-CLI Gemini 3 Pro† 4.8 32.1
Codex GPT-5.1-Codex‡ 13.9 43.0
Claude Code Claude Opus 4.5 11.4 43.6
OpenHands Qwen3-Coder-480B-A35B-Instruct 2.9 35.2
OpenHands DeepSeek-V3.2 5.9 32.6
OpenHands Claude Opus 4.5 5.9 42.2
OpenHands Gemini 3 Pro† 5.9 35.6
L2 Gemini-CLI Gemini 3 Pro† 5.9 34.0
Codex GPT-5.1-Codex‡ 5.9 35.2
Claude Code Claude Opus 4.5 8.8 41.9

