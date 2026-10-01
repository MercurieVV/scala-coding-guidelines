# TDAD: Test-Driven Agentic Development — results and limitations excerpts

> Source: https://arxiv.org/html/2603.17973
> Collected: 2026-10-01
> Published: 2026-03-18

Full-text excerpts (§3.1, §5.3, §5.4, §6.4) of Alonso, Yovine, Braberman. The abstract and headline excerpts are in
`2026-03-18-tdad-test-driven-agentic-development.md`.

Mechanism (§3): "The agent receives this file plus a 20-line skill definition; no MCP server, API calls, or graph database is required at runtime." "The agent uses grep on the test map to find tests to verify."

§5.3 Resolution vs. Regression Trade-off: "The modest 2-percentage-point resolution drop (31% → 29%) represents an intentional conservative bias. GraphRAG agents produced more empty patches (26% vs. 14%) when the test map indicated high regression risk; the agent “knew what it didn’t know” and abstained."

§5.4 Phase 2 (Table 6, SWE-bench Verified, 25 instances, Qwen3.5-35B-A3B + OpenCode agent):

| Metric | Baseline | TDAD Skill | Delta |
|---|---|---|---|
| Resolved | 6/25 (24%) | 8/25 (32%) | +8pp |
| Generated | 10/25 (40%) | 17/25 (68%) | +28pp |
| Res. of generated | 6/10 (60%) | 8/13 (62%) | +2pp |
| Empty patches | 15 | 8 | |
| Regression Rate | 0% | 0% | 0pp |

§6.4 External validity: "SWE-bench Verified draws exclusively from well-maintained, heavily-tested Python repositories. Projects with sparse test suites, non-Python codebases, or monorepo structures may respond differently to graph-based impact analysis. The two models used (Qwen3-Coder 30B and Qwen3.5-35B-A3B) are both smaller, locally-run models; frontier-scale models with longer context windows may not exhibit the same TDD-prompting paradox, as verbose instructions would consume a smaller fraction of available context."
