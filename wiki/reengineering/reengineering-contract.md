# The Reengineering Contract: Rules `R-1 … R-14`

> Sources: `skreeep2` contract and campaign documents; the reengineering-kit design, 2026-09-19
> Raw: [The reengineering kit — design (2026-09-19)](../../raw/reengineering/2026-09-19-kit-design.md)
> Updated: 2026-09-19

## Overview

The rules layer of the reengineering kit is a list of stable identifiers, `R-1` … `R-14`, each carrying a one-line
statement from the design's rules table. That list is the interface the other layers bind to: the scalafix rule and its config, the four process
skills, and the verification lane's verdicts all cite IDs, so "a rule changes by adding or superseding an ID" rather
than by editing prose that machinery believes it implements. This article is the canonical text of that list — the spine
the rest of the topic hangs off, and the `R-n` set a lane judges a diff against.

The design names five layers, each with an artifact and an interface. L1 *Rules* is this article: the `R-n`
identifiers, "one sentence each, citable". L2 *Enforcement* is the `AllowedShape` rule with its config keys. L3
*Process* is four skills. L4 *Harness* is the gate, "a runner over a build contract". L5 *Verification* is the lane,
which "consumes L1 and L4; produces receipts". This article defines L1 and names the other four; what each of them is
in full belongs to the design, and the rest of the topic cites them by number on that understanding.

## The rules

| ID | Rule |
|---|---|
| R-1 | Flow is composition: arrows, not ad-hoc plumbing. |
| R-2 | The abstract level names no concrete type; it reaches outside its package only through an explicit allowlist. |
| R-3 | Nothing imperative where the type system can state it. |
| R-4 | A component *names* a shape; it does not spell one. |
| R-5 | Absence is unrepresentable, not checked: a non-empty structure in the type beats a runtime guard. |
| R-6 | A contract is stated on the abstract member, and every implementation is total against it. |
| R-7 | Compiler settings are contract, not style — lowering `-Werror`, strict equality or the fatal warts to pass is a violation. |
| R-8 | No completion claim without the gate's own output, and the gate must *run* tests, not only compile them. |
| R-9 | Verification is independent: a fresh read-only lane judges the diff against these IDs, not against the author's summary. |
| R-10 | A finding that cannot be fixed honestly is escalated, never suppressed — no `@SuppressWarnings`, no allowlist widening, no threshold bump. |
| R-11 | State the decidable/judgement boundary, and never let a machine-decidable rule stand in for a design judgement. |
| R-12 | The record travels with the code: docs amended as the shape moves, stale claims corrected, open divergences written down as rulings. |
| R-13 | One module at a time, and a reshape is declared deliberate by an explicit target list rather than by whatever a diff happened to touch. |
| R-14 | Code is read and searched semantically (symbol-level tools), not textually; text search on sources is the exception, with a reason. |

## What each rule forbids, and what it permits

**R-1.** Forbids flow held together by hand — a step threaded around the composition instead of through it. Permits any
flow built by composing the project's arrows; the concrete refactoring patterns belong to *Refactoring Kleisli to
Arrow*. Generalized from `C-1`.

**R-2.** Forbids naming a concrete type at the abstract level, and reaching outside the package for anything the
allowlist does not grant. Permits the allowlisted types and any type parameter or abstract member that keeps the level
abstract. Generalized from `C-2`; the allowlist of types and the denylist of constructs are the machine-decidable half,
carried as the `AllowedShape` rule's two config keys — `allowedTypes` and `disallowedConstructs` — in each project's
build.

**R-3.** Forbids imperative constructs wherever the type system can state the same thing. Permits any encoding that
moves the decision into the types, so that what the code does is what its types say. Generalized from `C-3`.

**R-4.** Forbids spelling a shape at the use site — an inline refinement where a signature needs concrete type members.
Permits that same shape, named once and reused: a named child trait the consumer names instead of restating it. Origin:
the shape rule; *Named Shape Traits* is its article.

**R-5.** Forbids absence that is merely checked — a runtime guard over a structure that could have been empty. Permits,
indeed requires, a type in which the empty case does not exist. Origin: `F-2`, the `Tree` closure — two members built a
non-empty structure from a possibly-empty input, and the band and the roots' inputs became `Tree`s.

**R-6.** Forbids a contract stated on one implementation, and an implementation that is partial against the contract it
claims. Permits one contract, stated on the abstract member, with every implementation total against it. Origin: `F-2`.

**R-7.** Forbids lowering a compiler setting to make a build pass — `-Werror`, strict equality or the fatal warts.
Permits fixing the code; because the settings are contract rather than style, changing one is a contract change, not a
build knob. Generalized from `C-11`.

**R-8.** Forbids a completion claim without the gate's own output, and a gate that compiles tests without running them.
Permits a claim once the project's declared tasks — `preCommit`, `prePush`, `checkFormat` — have actually run, and
requires the gate to run tests, not only compile them. Generalized from `C-10` and the I4 finding.

**R-9.** Forbids verification by the author's own summary. Permits a fresh read-only lane that judges the diff against
these IDs and returns a verdict per rule — the lane that found the contract worded for one implementor only, a lost
check direction, and a test that proved less than it claimed.

**R-10.** Forbids suppression of a finding: no `@SuppressWarnings`, no allowlist widening, no threshold bump. Permits
escalating what cannot be fixed honestly, and prefers reshaping the code over changing the config. Origin: the linter
skill.

**R-11.** Forbids letting a machine-decidable rule stand in for a design judgement — the temptation is strongest at a
green gate. Permits both claims, kept apart: a concrete type outside the allowlist, an imperative construct, unformatted
code, a red compile, a lint finding and a test that does not run are decidable; the right split, a component that names
a shape rather than spells one, an honest contract and whether a divergence is a fix or a design change are judgement.

**R-12.** Forbids documentation that drifts from the code — a doc describing a shape that no longer exists, a stale
claim left standing, an open divergence kept out of the record. Permits the record to travel with the code: docs amended
as the shape moves, stale claims corrected, divergences written down as rulings.

**R-13.** Forbids reshaping more than one module at a time, and forbids letting the diff set the scope by what it
happened to touch. Permits a reshape declared deliberate by an explicit target list. Generalized from `C-4`/`C-5`, the
strictness dial.

**R-14.** Forbids reading and searching code textually as the default. Permits text search on sources as an exception,
with a reason; symbol-level tools are the rule. Generalized from `C-9`.

### Where each rule is carried further

`R-2`, `R-3` and `R-14` are elaborated nowhere else. The design's rules table gives each of them a one-line statement
and an origin — `C-2`, `C-3`, `C-9` — and nothing further: no worked example, no failure mode of its own, and for `R-2`
an enforcement half that is L2's config rather than more prose. This article is therefore their canonical text, and the
topic creates no sibling article for them.

Every other rule is carried further, and the `See Also` list below is the map: `R-1` by *Refactoring Kleisli to Arrow*,
`R-4` by *Named Shape Traits*, `R-7` by *Project Structure & Testing*, and `R-5`/`R-6`, `R-8`/`R-9`, `R-10`/`R-11` and
`R-12`/`R-13` by the four sibling articles, each annotated with the IDs it carries.

## See Also

- [Named Shape Traits: Do Not Spell a Type Member's Shape at the Use Site](../scala-typelevel-fp/named-shape-traits.md) — R-4
- [Refactoring Kleisli to Arrow: When and How](../scala-typelevel-fp/kleisli-to-arrow-refactoring.md) — R-1
- [Project Structure & Testing](../scala-typelevel-fp/project-structure-and-testing.md) — R-7, module layout
- [Absence Unrepresentable](absence-unrepresentable.md) — R-5, R-6
- [The Decidable Boundary](decidable-vs-judgement.md) — R-10, R-11
- [Verification and the Gate](verification-and-the-gate.md) — R-8, R-9
- [Recording a Migration](recording-a-migration.md) — R-12, R-13
- [What Goes Wrong in LLM-Generated Code](../llm-scala-fusion/llm-code-defect-taxonomy.md) — the empirical case for R-8, R-9, R-10, R-14 (test gaming, hallucinated symbols, idiom drift)
