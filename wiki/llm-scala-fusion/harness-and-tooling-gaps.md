# Harness and Tooling Gaps: purrism, ScalaSemantic, the Template and the dsh Profile Against the Literature

> Sources: Felendler et al., 2026-02-17; Anthropic Engineering, 2025-11-04; oraios/serena README, Unknown; Blinn et al., 2024-09-02; Khati et al., 2026-01-27; Ge et al. (vibe-coding survey), 2025-10-14; Zhong et al. (ImpossibleBench), 2025-10-23; Lang et al. (FPEval), 2026-01-05
> Raw: [MCP design choices](../../raw/llm-scala-fusion/2026-02-17-mcp-design-choices-tool-orchestration-vs-code-execution.md); [Code execution with MCP](../../raw/llm-scala-fusion/2025-11-04-anthropic-code-execution-with-mcp.md); [Serena](../../raw/llm-scala-fusion/serena-mcp-semantic-coding-toolkit.md); [Typed holes (Hazel)](../../raw/llm-scala-fusion/2024-09-02-statically-contextualizing-llms-with-typed-holes.md); [Deterministic AST hallucination correction](../../raw/llm-scala-fusion/2026-01-27-deterministic-ast-hallucination-detection-correction.md); [Survey of vibe coding](../../raw/llm-scala-fusion/2025-10-14-survey-of-vibe-coding-with-llms.md); [ImpossibleBench](../../raw/llm-scala-fusion/2025-10-23-impossiblebench-llm-test-exploitation.md); [Perish or Flourish (FPBench)](../../raw/llm-scala-fusion/2026-01-05-perish-or-flourish-llm-functional-programming-fpbench.md)
> Updated: 2026-09-19

## Overview

A surface-level comparison of the four existing fusion tools — the `scala-purrism` scalafix rules, the ScalaSemantic
MCP server and its guard hook, the `scala-llm-template` (`Setup.scala`, stryker4s, shared rules file) and the dsh
`scala-arhitect` profile (metals LSP, ScalaSemantic MCP, hooks bridge, vendored skills) — against what the literature
says a harness should do. The vibe-coding survey's conclusion is the frame: success "depends not merely on agent
capabilities but on systematic context engineering, well-established development environments, and human-agent
collaborative development models". Every gap below is a context-engineering or environment gap, not a model gap.

## What the literature asks of a harness

1. **Semantic, not textual, access to code.** Serena's README states the agent-first position: symbol-level tools
   with "robust high-level abstractions rather than low-level concepts like line numbers or primitive search
   patterns", claiming cross-file operations "that would cost 8–12 careful, error-prone steps collapse into one atomic
   call". Hazel's ChatLSP proposal is the same idea from the PL side: the language server should expose typing context
   to the completion system.
2. **Code execution over tool-by-tool orchestration.** Anthropic's engineering post and Felendler et al. both find
   that letting the agent *write a program* against tool APIs, executed in a sandbox, beats one-call-per-tool: in the
   Anthropic example token usage fell "from 150,000 tokens to 2,000 tokens—a time and cost saving of 98.7%", because
   tool definitions no longer flood the context and intermediate results stay in the runtime. Felendler et al. confirm
   CE-MCP "significantly reduces token usage and execution latency" while warning that it "introduces a vastly expanded
   attack surface" (sixteen attack classes), answered by containerised sandboxing and semantic gating.
3. **Deterministic post-processing before the model sees an error.** Khati et al.'s AST-vs-knowledge-base check
   (100% precision) shows that a non-LLM validator on the output is more reliable than asking the model to repair.
4. **Feedback translated into instructions.** FPEval's repair gains came from pairing each static finding with
   hand-crafted instructions, not from raw diagnostics.
5. **Verifier that cannot be gamed.** ImpossibleBench shows prompt, test access and feedback-loop design change
   cheating rates — the harness decides whether the agent can touch tests at all.

## Gap analysis

### scala-purrism (scalafix rules)

*Covers:* idiom drift (Abstract*, Cats*, Idiom*, Suspend*, Widen*, Opaque*, Arrow* rule families rewrite toward pure,
polymorphic, arrow-shaped code); the machine-decidable half of R-1/R-3 and, via AllowedShape, R-2.

*Gaps against the literature:*
- **No diagnostic→instruction table.** FPEval's finding (point 4) suggests exporting, for every rule, a one-paragraph
  *repair instruction* in a machine-readable form the harness injects when the rule fires, instead of the rule's
  human-facing message alone.
- **Rules run as a batch, not as a loop stage.** The compiler-in-the-loop studies assume a per-attempt cycle; the
  harness should run the purrism rewrite *before* the LLM reviews its own diff, so the model reads normalised code.
- **No rule for the test-gaming class.** A scalafix rule cannot see history, but it *can* flag constant-return or
  input-memorising shapes (SpecBench's test-memorising hash-table "compiler") in production code — a decidable smell worth a rule.

### ScalaSemantic (MCP + guard hook)

*Covers:* point 1 — compiler-resolved symbols, types, usages, hierarchies, implicits, call paths; the guard hook denies
`grep`/`cat`/`Read` on `.scala` files (R-14 enforced, not advised).

*Gaps:*
- **No typed-hole tool.** Hazel's most impactful signal — the expected type of the hole plus type definitions in
  scope — has no single ScalaSemantic call. A `hole_context(uri, pos)` returning expected type, compatible in-scope
  bindings, applicable givens and the definitions of the types involved is the highest-value addition.
- **No diff-scoped symbol verification.** A `verify_diff` tool that resolves every symbol a patch introduces against
  SemanticDB/classpath (Khati et al.'s pattern) before compilation, and checks R-2's allowlist at the same time, turns a
  probabilistic repair loop into a deterministic reject.
- **Tool-by-tool only.** Point 2 argues for a *code-execution* surface: expose the same queries as a Scala API a
  script (scala-cli) can call, so the agent writes one program for "find all implementers of X, get their signatures,
  report the ones lacking a given" rather than many round trips. The compiler then also catches hallucinated *tool*
  use.
- **No generator/property helpers.** A tool that, given a type, lists its constructors and existing `Arbitrary`/`Gen`
  instances would scaffold the PBT loop (see [Types and Properties](where-types-and-properties-buy-most.md)).

### scala-llm-template

*Covers:* build commands, mutation testing (stryker4s), a shared rules file, idempotent setup.

*Gaps:*
- **No held-out test lane.** SpecBench's visible/held-out split is a project-layout decision: a `holdout` test module
  the agent cannot edit, run only by the gate.
- **No property-test scaffold by default.** ScalaCheck + a convention for `Arbitrary` derivation per opaque type.
- **No BDD seam.** See [Alternative Paradigms and the BDD Seam](alternative-paradigms-and-bdd-seam.md).

### dsh `scala-arhitect` profile

*Covers:* metals LSP, ScalaSemantic MCP, hooks bridge (`.dsh/hooks.json`), vendored skills, permission presets.

*Gaps:*
- **Hooks do not yet encode the anti-gaming posture.** A pre-edit hook denying writes under `*/test/*` during an
  implementation phase (and under a holdout module always) is the cheapest ImpossibleBench mitigation.
- **No sandboxed code-execution runtime.** If a CE-MCP surface is added (above), Felendler et al.'s warning applies:
  it needs the `workspace-write` sandbox, not `danger-full-access`.
- **Diagnostics are raw.** The compile/scalafix output reaches the model untranslated; the diagnostic→instruction
  table belongs in the hook that reports build results.

## Priority order

1. Typed-hole context tool (ScalaSemantic) — highest evidence, moderate effort.
2. Diff-scoped deterministic symbol/allowlist verification (ScalaSemantic + purrism AllowedShape).
3. Test-protection hooks + holdout module (dsh profile + template) — lowest effort, closes the reward-hacking hole.
4. Diagnostic→instruction table (purrism export + hook).
5. Code-execution surface for the semantic tools (scala-cli API) — high payoff, needs sandboxing first.

## See Also

- [What Goes Wrong in LLM-Generated Code](llm-code-defect-taxonomy.md)
- [Where Types and Properties Buy the Most](where-types-and-properties-buy-most.md)
- [Roadmap: More Scala, Less LLM](roadmap-more-scala-less-llm.md)
- [Verification and the Gate](../reengineering/verification-and-the-gate.md) — the build contract the hooks enforce
- [Refactoring Kleisli to Arrow](../scala-typelevel-fp/kleisli-to-arrow-refactoring.md) — the purrism rules in context
