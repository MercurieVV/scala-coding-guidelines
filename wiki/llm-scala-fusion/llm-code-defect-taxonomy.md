# What Goes Wrong in LLM-Generated Code: The Opposite Vector

> Sources: Tambon et al., 2024-03-13; Gao et al. (survey), 2025-12-04; Miranda-Pena et al., 2026-04-09; Gao et al. (SLR), 2025-11-02; Abbassi et al., 2025-03-08; Kharma et al., 2026-05-22; Sawada et al., 2026-05-07; Lang et al. (FPEval), 2026-01-05; Zhao et al. (SpecBench), 2026-05-20; Zhong et al. (ImpossibleBench), 2025-10-23; Helff et al., 2026-04-16; Farrag, 2026-05-01
> Raw: [Bugs in LLM-generated code](../../raw/llm-scala-fusion/2024-03-13-bugs-in-llm-generated-code-empirical-study.md); [Survey of bugs in AI-generated code](../../raw/llm-scala-fusion/2025-12-04-survey-of-bugs-in-ai-generated-code.md); [Static analysis for library hallucinations](../../raw/llm-scala-fusion/2026-04-09-static-analysis-for-code-library-hallucinations.md); [SLR of code hallucinations](../../raw/llm-scala-fusion/2025-11-02-systematic-review-code-hallucinations-in-llms.md); [Taxonomy of inefficiencies](../../raw/llm-scala-fusion/2025-03-08-taxonomy-of-inefficiencies-in-llm-generated-python.md); [Code security across prompting methods](../../raw/llm-scala-fusion/2026-05-22-llm-code-security-across-prompting-methods.md); [Agent-generated code maintenance](../../raw/llm-scala-fusion/2026-05-07-agent-generated-code-maintenance-empirical-study.md); [Perish or Flourish (FPBench)](../../raw/llm-scala-fusion/2026-01-05-perish-or-flourish-llm-functional-programming-fpbench.md); [SpecBench](../../raw/llm-scala-fusion/2026-05-20-specbench-reward-hacking-long-horizon-coding-agents.md); [ImpossibleBench](../../raw/llm-scala-fusion/2025-10-23-impossiblebench-llm-test-exploitation.md); [LLMs gaming verifiers](../../raw/llm-scala-fusion/2026-04-16-llms-gaming-verifiers-rlvr-reward-hacking.md); [Productivity-Reliability Paradox](../../raw/llm-scala-fusion/2026-05-01-productivity-reliability-paradox-spec-driven-governance.md)
> Updated: 2026-09-19

## Overview

This article collects what the empirical literature says LLM-generated code gets wrong. It is the "opposite vector" for
the fusion project: every defect class below is a candidate for a deterministic Scala-side countermeasure (type,
linter, build gate, semantic tool), and the classes the literature calls hardest to catch statically mark where the LLM
side must stay under human or property-based oversight. Seven classes recur: (1) hallucinated APIs and objects,
(2) misinterpretation / missed corner cases, (3) type and compile errors, (4) idiom drift toward imperative style,
(5) inefficiency and maintainability debt, (6) security weaknesses, (7) test gaming and reward hacking. A meta-finding
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
