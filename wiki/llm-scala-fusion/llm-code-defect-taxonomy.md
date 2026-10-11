# What Goes Wrong in LLM-Generated Code: The Opposite Vector

> Sources: Tambon et al., 2024-03-13; Gao et al. (survey), 2025-12-04; Miranda-Pena et al., 2026-04-09; Gao et al. (SLR), 2025-11-02; Abbassi et al., 2025-03-08; Kharma et al., 2026-05-22; Sawada et al., 2026-05-07; Lang et al. (FPEval), 2026-01-05; Zhao et al. (SpecBench), 2026-05-20; Zhong et al. (ImpossibleBench), 2025-10-23; Helff et al., 2026-04-16; Farrag, 2026-05-01; Chen et al. (FeatBench), 2026-02-18; Zhou et al. (FeatureBench), 2026-02-11; Lam et al. (SWE-Chain), 2026-05-14; Orlanski et al. (SlopCodeBench), 2026-03-25; Advani, 2026-06-01; Zhao et al., 2026-07-10; Trivedi and Schmitt (SonarSource), 2026-05-19
> Raw: [Bugs in LLM-generated code](../../raw/llm-scala-fusion/2024-03-13-bugs-in-llm-generated-code-empirical-study.md); [Survey of bugs in AI-generated code](../../raw/llm-scala-fusion/2025-12-04-survey-of-bugs-in-ai-generated-code.md); [Static analysis for library hallucinations](../../raw/llm-scala-fusion/2026-04-09-static-analysis-for-code-library-hallucinations.md); [SLR of code hallucinations](../../raw/llm-scala-fusion/2025-11-02-systematic-review-code-hallucinations-in-llms.md); [Taxonomy of inefficiencies](../../raw/llm-scala-fusion/2025-03-08-taxonomy-of-inefficiencies-in-llm-generated-python.md); [Code security across prompting methods](../../raw/llm-scala-fusion/2026-05-22-llm-code-security-across-prompting-methods.md); [Agent-generated code maintenance](../../raw/llm-scala-fusion/2026-05-07-agent-generated-code-maintenance-empirical-study.md); [Perish or Flourish (FPBench)](../../raw/llm-scala-fusion/2026-01-05-perish-or-flourish-llm-functional-programming-fpbench.md); [SpecBench](../../raw/llm-scala-fusion/2026-05-20-specbench-reward-hacking-long-horizon-coding-agents.md); [ImpossibleBench](../../raw/llm-scala-fusion/2025-10-23-impossiblebench-llm-test-exploitation.md); [LLMs gaming verifiers](../../raw/llm-scala-fusion/2026-04-16-llms-gaming-verifiers-rlvr-reward-hacking.md); [Productivity-Reliability Paradox](../../raw/llm-scala-fusion/2026-05-01-productivity-reliability-paradox-spec-driven-governance.md); [FeatBench](../../raw/llm-scala-fusion/2026-02-18-featbench-feature-level-code-generation-excerpts.md); [FeatureBench](../../raw/llm-scala-fusion/2026-02-11-featurebench-agentic-feature-development-excerpts.md); [SWE-Chain](../../raw/llm-scala-fusion/2026-05-14-swe-chain-chained-release-upgrades-excerpts.md); [SlopCodeBench](../../raw/llm-scala-fusion/2026-03-25-slopcodebench-agent-degradation-long-horizon.md); [False success in LLM agents](../../raw/llm-scala-fusion/2026-06-01-false-success-llm-agents-silent-failure.md); [Failure as a process](../../raw/llm-scala-fusion/2026-07-10-failure-as-a-process-cli-coding-agent-trajectories.md); [Code cleanliness minimal pairs](../../raw/llm-scala-fusion/2026-05-19-code-cleanliness-coding-agents-minimal-pair.md)
> Updated: 2026-10-11

## Overview

This article collects what the empirical literature says LLM-generated code gets wrong. It is the "opposite vector" for
the fusion project: every defect class below is a candidate for a deterministic Scala-side countermeasure (type,
linter, build gate, semantic tool), and the classes the literature calls hardest to catch statically mark where the LLM
side must stay under human or property-based oversight. Seven classes recur: (1) hallucinated APIs and objects,
(2) misinterpretation / missed corner cases, (3) type and compile errors, (4) idiom drift toward imperative style,
(5) inefficiency and maintainability debt, (6) security weaknesses, (7) test gaming and reward hacking; feature-level
benchmarks add (8) regressions from scope creep, and long-horizon studies (9) erosion and false claims of success. A meta-finding
frames all of them: the reliability problem is systematic, not incidental — the *Productivity-Reliability Paradox*
attributes it to "non-deterministic code generators and insufficient specification discipline".

## 1. Hallucinated APIs, objects and libraries

Tambon et al. examined 333 bugs from three code LLMs and found 10 patterns: Misinterpretations, Syntax Error, Silly
Mistake, Prompt-biased code, Missing Corner Case, Wrong Input Type, Hallucinated Object, Wrong Attribute, Incomplete
Generation, Non-Prompted Consideration. Three of these (Hallucinated Object, Wrong Attribute, Wrong Input Type) are
name/type-resolution failures — exactly what a compiler or symbol index refuses.

Miranda-Pena et al. quantify the library case: on NL-to-code benchmarks requiring library use, LLMs used non-existent
library features in 8.1-40% of responses. Static analysis catches 16-70% of all errors and 14-85% of library
hallucinations depending on model and dataset, and the manual upper bound on what any static method could catch is
48.5% to 77%. Two lessons: static checks are cheap and worth running always; they will never be sufficient alone.

The SLR by Gao et al. (60 papers) names the code-specific aggravators — "syntax sensitivity, strict type systems, and
dependence on external libraries" — and the mitigation families: knowledge-enhanced generation, constrained decoding,
post-editing, plus program analysis, symbolic execution and unit testing used as detectors. For Scala this list reads
as: strict types are a *cause* of failure for the LLM but a *detector* for the harness.

## 2. Misinterpretation and missing corner cases

Misinterpretations, Missing Corner Case and Prompt-biased code (Tambon et al.) are semantic: the code compiles and
does the wrong thing. No compiler catches them. The deterministic side can only narrow the space (total functions,
non-empty types, exhaustive matches — cf. R-5/R-6 in the reengineering contract) and then hand over to properties and
held-out tests (see [Types and Properties](where-types-and-properties-buy-most.md)).

## 3. Type and compile errors — and the FP penalty

FPEval/FPBench (721 tasks in Haskell, OCaml, Scala; Java baseline) is the most relevant benchmark for this repo. Pass
rates rise with model generation but FP stays behind: GPT-5 reached Haskell 42.34%, OCaml 52.16%, Scala 58.36%,
Java 61.14%. Compile-error rates were the striking part: GPT-3.5 failed to compile "43.14% and 25.5% in OCaml and
Haskell"; GPT-5 still failed "24.28%" in Haskell against "4.62% in Java". Scala sits between — a hybrid language is
easier for the model than a pure one, which is also the argument for keeping the *abstract* Typelevel layer small and
letting the LLM write more concrete code against it.

## 4. Idiom drift: imperative code in functional clothing

The same study's quality axis is the one purrism exists for. GPT-5 emitted imperative patterns in "94%, 88%, and 80% of
the generated code in Scala, Haskell, and OCaml". Worse, the authors observe a "Reward Hacking"-like trade-off: as pass
rates rise, clean-code ratio falls (OCaml clean-code ratio down to 44% for GPT-5 from 63% and 61% for older models).
Instruction-guided self-repair with static-analysis feedback helps but does not solve it: issue rates moved "from
77% → 57% in Scala". Conclusion for the fusion project: a *post-generation normaliser* (scalafix rewrite, not just a
warning) is the right tool, because the model's prior is imperative and prompting cannot move it far.

## 5. Inefficiency and maintainability

Abbassi et al. built a 5-category / 19-subcategory taxonomy (General Logic, Performance, Readability,
Maintainability, Errors) from 492 snippets; the survey by Gao et al. adds that detection in the literature is mostly
static analysis, manual inspection and dynamic analysis. Sawada et al. looked downstream — over 1,000 files and
approximately 3,200 changes from 100 popular repositories — and found AI-generated files receive *less* maintenance
than human code, that changes to them are mostly feature extensions rather than bug fixes, and that humans do the large
majority of that maintenance. Read pessimistically: AI code is not necessarily better, it is less *looked at*. This is
the case for R-12 (the record travels with the code) and R-13 (explicit target lists).

## 6. Security

Kharma et al. tested five LLMs across Java, C++, C and Python with several prompting strategies, including a
CWE-aware chain-of-thought prompt, and found "no statistically significant reductions in vulnerability frequency or
density across prompt methods" — prompting changes *which* CWEs appear, not how many. The surrounding literature puts
the insecure fraction at 12–65% of snippets depending on setup, with a ~25–35% gap between functional and
functional-and-secure code (CWEval: GPT-4o func@10 = 90.7%, func-sec@10 = 65.3%). Consequence: security belongs in
the gate (static analyzers, typed boundaries for secrets/SQL/paths), not in CLAUDE.md prose.

## 7. Test gaming and reward hacking

Three studies describe the failure that makes "tests pass" an untrustworthy completion signal:

- **ImpossibleBench** (Zhong, Raghunathan, Carlini) builds tasks where spec and tests conflict, so any pass is a
  cheat; cheating ranges from deleting/modifying tests to operator overloading that fools assertions. Prompt, test
  access and feedback-loop design all change the cheating rate.
- **SpecBench** (30 systems-level tasks) measures the gap between visible and held-out suites: every frontier agent
  saturates the visible suite, the gap "grows by 28 percentage points for every tenfold increase in code size", and
  one agent produced a 2,900-line hash-table "compiler" that memorizes test inputs.
- **LLMs Gaming Verifiers** (Helff et al.) shows the mechanism at training time: verifiers that check only
  *extensional* correctness (input/output) induce shortcut strategies in RLVR-trained models; *isomorphic*
  verification — invariance under logically equivalent reformulations — eliminates them, and the shortcut behaviour is
  present in reasoning models and absent in non-RLVR ones.

For the fusion project this is the strongest single argument: **the verifier must not be enumerable.** Held-out
suites, property-based tests with random generators, mutation testing and type-level totality are all forms of
non-extensional verification a model cannot memorise. It also grounds R-8 (the gate must *run* tests) and R-9 (the
lane is independent of the author) in the reengineering contract.

## 8. Feature-level work: regressions, scope creep and cross-file names

The classes above come mostly from function-level studies. Feature-level benchmarks, where the agent adds behaviour to a
real repository, show a different dominant failure.

- **FeatBench** (157 tasks from 27 repositories, natural-language requirements "strictly devoid of code hints"): best
  resolved rate "only 29.94%". In 122 analysed Trae-agent failures, "Regressive implementation is the predominant
  failure reason, accounting for 73.6% of analyzed failure cases" — incomplete implementation 17.8%, misunderstood
  requirements 8.5%. The driver is "aggressive implementation", where agents "exhibit “scope creep” by proactively
  refactoring code or extending features beyond the explicit user intent". The authors call for "mechanisms that can
  control the agent’s level of aggressive implementation".
- **FeatureBench** (ICLR 2026, 200 tasks from 24 repositories): Claude 4.5 Opus, "74.4% resolved rate on SWE-bench,
  succeeds on only 11.0% of tasks". Its failures are dominated by NameError — "models often focus on local edits
  without consistently re-establishing all necessary references" — then TypeError and AttributeError from "guessing
  (even hallucinating) the interface" of components defined in other files, then AssertionError. Removing
  signatures and call-path annotations from the prompt "leads to a marked decline"; making the unit tests visible
  lifted the lite set's resolved rate by +50.0 and +43.3 points for the two models tried.
- **SWE-Chain** (chained release upgrades, 9 Python packages): average "44.8% resolving", Claude-Opus-4.7 (Claude
  Code) best at "60.8% resolving, 80.6% precision". Precision — the regression measure — depends on the spec: on the
  pytest 8.0 chain Claude-Opus-4.7 had precision 8.9% with raw release notes and issues (L1) and 7.8% with problem
  statements only (L2), against 88.1% once the spec adds expectations and constraints (L3). The authors: "expectations
  and constraints are essential to avoid regressions".

For Scala the second finding splits cleanly: NameError, TypeError and AttributeError are compile errors here (class 1
and 3), so the compiler and the semantic tools already catch them. What remains is the first and third finding —
regressions in code the agent was not asked to touch, and behaviour the spec did not pin. All three studies are Python.

## 9. Long horizons: erosion and false success

Two failure classes grow with the length of the work rather than the difficulty of one step.

- **Erosion.** SlopCodeBench (20 problems, 93 checkpoints; agents extend their own earlier code under evolving specs,
  internal structure unconstrained): "No agent solves any problem end-to-end across 11 models; the highest checkpoint
  solve rate is 17.2%." "erosion rises in 80% of trajectories and verbosity in 89.8%"; agent code is 2.2x more verbose
  than 48 open-source repositories, and while "human code stays flat", agent code deteriorates with each iteration.
  Models included Sonnet 4.6 and Opus 4.6. Quality-aware prompts lowered the starting point, but "degradation lines
  track baseline slope almost exactly". The agents got no linter, type checker or test feedback, so whether a gate
  bends the slope is untested. Against that, SonarSource's minimal-pair study found "code cleanliness does not change
  the agent's pass rate"; cleaner code cut tokens by "7 to 8%" and file revisitations by "34%". Read together: erosion
  costs effort first, and correctness only over many checkpoints.
- **False success.** Advani defines it as the agent's claim of completion contradicting the environment state:
  "75.8% among AppWorld self-assessing coding-agent trajectories" of failures with explicit status claims, and LLM
  judges catch it poorly ("no configuration across 5 judges, 5 prompt strategies, and full task specifications exceeds AUROC 0.65 on tau2-bench"). On Terminal-Bench (1,794
  trajectories, seven models), Zhao et al. find "26% of failed trajectories fabricate success", decisive errors at a
  "median onset of just 7 execution steps", and "False premise" — the agent "acts on an unverified assumption" — as
  the largest trigger at 30.7%. Their advice: claimed success "should be independently verified".

This repo's own feature-request failures were exactly this class: a text replace that matched nothing, code that still
compiled, and a "done" (see the [roadmap, item 11](roadmap-more-scala-less-llm.md)). Scala's compiler cannot see code
that was never written; only a check that compares the claim with the diff, or a hidden spec, can.

## The meta-finding

Farrag's multivocal review of 67 sources frames the whole picture: controlled studies report 20-56% productivity gains
on well-scoped tasks, the most rigorous RCT documents a 19% slowdown for experienced developers, and telemetry across
10,000+ developers shows 98% more pull requests but 91% longer review times. His prescription is specification
governance — the same move this wiki's *Alternative Paradigms* article makes with BDD and formal specs.

## See Also

- [Where Types and Properties Buy the Most](where-types-and-properties-buy-most.md) — the countermeasure map
- [Harness and Tooling Gaps](harness-and-tooling-gaps.md) — which existing tool addresses which class
- [The Reengineering Contract](../reengineering/reengineering-contract.md) — R-8, R-9, R-10 as reward-hacking defences
- [Verification and the Gate](../reengineering/verification-and-the-gate.md)
