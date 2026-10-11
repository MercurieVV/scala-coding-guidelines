# Failure as a Process: An Anatomy of CLI Coding Agent Trajectories (excerpts)

> Source: https://arxiv.org/abs/2607.09510 (Zhao, Li, Li, Zhao, Barr, Sarro, Ye — University College London, Nanjing University); excerpts via the arXiv HTML v1; data https://github.com/xz-Sean/cli_trajectory_analysis
> Collected: 2026-10-11
> Published: 2026-07-10

The study analyzes 1,794 execution trajectories across three coding-agent scaffolds (MiniSWE, OpenHands, Terminus2)
and seven frontier models (Claude Sonnet 4, GPT-5, Gemini 2.5 Pro, Qwen3 Coder 480B, DeepSeek V3.2, Kimi K2 Instruct,
Devstral 2) on Terminal-Bench. It derives 14 findings: "coding-agent failures are predominantly driven by epistemic
errors, typically begin within the first few execution steps, and often remain hidden until recovery is no longer
possible."

## Taxonomy

| Category | Frequency | Error types |
|---|---|---|
| Epistemic | 57.9% | False premise (30.7%), Specification neglect (14.9%), Output misreading (4.4%), Ignored signal (4.1%), Premature action (3.7%) |
| Competence | 32.8% | Knowledge gap (24.0%), Capability limitation (8.8%) |
| Environment | 9.4% | Environment blocker (8.8%), Other (0.6%) |

False premise: the agent "acts on an unverified assumption".

## Findings

- "Decisive errors occur surprisingly early, with a median onset of just 7 execution steps." Observable failure signals
  typically emerge around 10 execution steps after the decisive error.
- Only 18% terminate immediately; 82% continue without progress. "Repairs the wrong problem" consumes 39% of wasted
  execution.
- 71% of successful trajectories encounter errors but recover.
- "26% of failed trajectories fabricate success"; fabrication "is a consequence of failure rather than its source".
- Successful trajectories respond to error signals 92% of the time vs. 37% for failed ones.
- "Real-time recall remains low (28.8% at best)" for automated failure detection.
- Implications: "Improving coding-agent reliability requires earlier validation and intervention rather than relying
  solely on final-outcome evaluation"; claimed success "cannot be trusted on its face, especially late in a trajectory,
  and should be independently verified".
