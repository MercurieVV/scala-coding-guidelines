# TDAD: Test-Driven Agentic Development - Reducing Code Regressions in AI Coding Agents via Graph-Based Impact Analysis

> Source: https://arxiv.org/abs/2603.17973
> Collected: 2026-10-01
> Published: 2026-03-18

Authors: Alonso, Pepe; Yovine, Sergio; Braberman, Victor A.

## Abstract

AI coding agents can resolve real-world software issues, yet they frequently introduce regressions -- breaking tests that previously passed. Current benchmarks focus almost exclusively on resolution rate, leaving regression behavior under-studied. This paper presents TDAD (Test-Driven Agentic Development), an open-source tool that performs pre-change impact analysis for AI coding agents. TDAD builds a dependency map between source code and tests so that before committing a patch, the agent knows which tests to verify and can self-correct. The map is delivered as a lightweight agent skill -- a static text file the agent queries at runtime. Evaluated on SWE-bench Verified with two open-weight models running on consumer hardware (Qwen3-Coder 30B, 100 instances; Qwen3.5-35B-A3B, 25 instances), TDAD reduced regressions by 70% (6.08% to 1.82%) compared to a vanilla baseline. In contrast, adding TDD procedural instructions without targeted test context increased regressions to 9.94% -- worse than no intervention at all. When deployed as an agent skill with a different model and framework, TDAD improved issue-resolution rate from 24% to 32%, confirming that surfacing contextual information outperforms prescribing procedural workflows. All code, data, and logs are publicly available at this https URL.

## Excerpts (verbatim, from the HTML full text)

> This work develops a method to enhance agents with appropriate structural knowledge that leads to meaningful regression reduction while improving resolution rate, and presents TDAD (Test-Driven Agentic Development), an open-source tool that performs pre-change impact analysis for AI coding agents. TDAD builds a dependency map between source code and tests so that before committing a patch, the agent knows which tests to verify and can self-correct. The map is delivered as a lightweight agent skill—a static text file the agent queries at runtime. Evaluated on SWE-bench Verified with two open-weight models running on consumer hardware (Qwen3-Coder 30B, 100 instances; Qwen3.5-35B-A3B, 25 instances), TDAD reduced regressions by 70% (6.08% 1.82%) compared to a vanilla baseline. In contrast, adding TDD procedural instructions without targeted test context increased regressions to 9.94%—worse than no intervention at all. When deployed as an agent skill with a different model and framework, TDAD improved issue-resolution rate from 24% to 32% , confirming that surfacing contextual information outperforms prescribing procedural workflows. All code, data, and logs are publicly available.

> 70% regression reduction. In Phase 1 (100 instances, Qwen3-Coder 30B, single-agent setup), TDAD reduced test-level regression rate from 6.08% to 1.82% (562 155 P2P failures).

> TDD Prompting Paradox. In the same Phase 1 setup, an ablation adding TDD procedural instructions (write tests first, then implement) without telling the agent which specific tests to check actually increased regressions to 9.94%—worse than vanilla. Procedural instructions without targeted context are counterproductive.

> GraphRAG+TDD achieved a 72% reduction in P2P failures (562 155) and a 70% reduction in regression rate (6.08% 1.82%). Compared to TDD-only, the reduction is even larger: 81% fewer failures (799 155). Catastrophic regressions (all P2P tests failing) dropped from 3 in vanilla and 5 in TDD-only to just 1 in GraphRAG+TDD.

> 799

> Test-driven development (TDD) ( 10 ) prescribes writing tests before implementation code, creating a tight feedback loop that catches regressions early. TDAD borrows TDD’s core philosophy —verify before you commit—but replaces TDD’s procedure (write test red green refactor) with contextual guidance : rather than instructing the agent to follow TDD steps, TDAD tells the agent which specific tests are at risk. This distinction directly motivates the TDD Prompting Paradox (§ 5.2 ), where procedural TDD instructions without contextual information actually increased regressions. Cui ( 18 ) introduces a TDD benchmark where test cases serve as both prompt and verification, finding that instruction following and in-context learning matter more than general coding proficiency for TDD success, and that performance degrades when instructions are long—a result consistent with the present work’s finding that smaller models benefit more from concise, targeted context than from lengthy procedural instructions.
