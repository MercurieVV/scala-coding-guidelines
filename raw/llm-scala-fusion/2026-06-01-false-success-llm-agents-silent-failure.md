# From Confident Closing to Silent Failure: Characterizing False Success in LLM Agents (abstract)

> Source: https://arxiv.org/abs/2606.09863 (Laksh Advani; accepted to FAGEN@ICML2026)
> Collected: 2026-10-11
> Published: 2026-06-01

"LLM agents can fail silently by asserting task completion when the environment state shows otherwise." False success is
defined as a mismatch between the agent's natural-language claim of completion and the programmatic environment state.
The study covers 9,876 tau2-bench trajectories from 8 model families and 1,879 AppWorld trajectories from 4 model
families with text-independent ground truth.

False success is common but varies by setting: "45--48% of failures in single-control tau2-bench domains, 3% in
dual-control telecom, and 75.8% among AppWorld self-assessing coding-agent trajectories" with explicit status claims.
It is operationally dangerous because it propagates silently — unlike a crash, refusal, or explicit handoff, the agent
presents the interaction as resolved.

LLM judges detect it poorly: "no configuration across 5 judges, 5 prompt strategies, and full task specifications
exceeds AUROC 0.65 on tau2-bench." Lightweight TF-IDF detectors achieve task-disjoint "AUROC 0.83 on tau2-bench and
0.95 on AppWorld, recovering 4--8x more false successes than the best judge at the same flag rate with 3,300x lower
latency."
