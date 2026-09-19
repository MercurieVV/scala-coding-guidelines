# The Decidable Boundary: What a Machine Decides, What a Human Rules

> Sources: `skreeep2` session, 2026-09-19 (the linter and review findings)
> Raw: [The reengineering kit — design (2026-09-19)](../../raw/reengineering/2026-09-19-kit-design.md)
> Updated: 2026-09-19

## Overview

`R-10` and `R-11` are the two rules that keep a gate honest about what it knows. `R-11` splits the findings a
reengineering job produces into two piles — what a machine decides, what a human rules — and forbids letting the
first stand in for the second. `R-10` says what to do where the two meet: a finding that cannot be fixed honestly is
escalated, never silenced. Both are stated in [The Reengineering Contract](reengineering-contract.md), the spine of
this topic; this article carries the two lists themselves, the reason a green gate is not a correct design, and the
shape an escalation should take.

## The boundary, stated plainly

The design states the split as two lists, one for each side of it. The machine's:

> Machine-decidable, and therefore L2's business: a concrete type outside the allowlist; an imperative construct;
> unformatted code; a red compile; a lint finding; a test that does not run.

Every entry is answerable by looking at the artifact: the type is named in a signature, the construct is in the
source, the formatting is in the diff, the compile either succeeds or does not, a lint rule reports the finding it
decided, and a test either ran or did not. None of them requires knowing what the author meant, which is why the
enforcement layer owns them.

The human's list is the other half:

> Judgement, and therefore L1/L3/L5's: is this the right split; does this component name a shape rather than spell
> one; is this contract honest; is this divergence a fix or a design change. The kit says so where an agent is most
> tempted to stop — at a green gate.

These are questions about intent, and no artifact carries the answer by itself. A rule can gather evidence for any of
them — the diff shows where the split fell, the signatures show whether a shape was named or spelled, the call sites
show whether a contract holds past its first implementor — but the verdict belongs to the rules layer, the process
skills and the independent lane.

## Why a green gate is not a correct design

The design lists this among the things the kit is not: it is "Not a claim that a green gate means a correct design",
and it points back to the boundary rule for the reason. The reason is the two lists above. Everything the machine
decides is a property of the artifact, and the design is not a property of the artifact: a module can stay inside
the allowlist, hold no imperative construct, be formatted, compile, lint and run its tests, and still split at the
wrong seam, put the wrong thing on the abstract level, or state a contract that only its first implementor can
satisfy. Green is evidence about the artifact; the judgement is whether the artifact is the right one.

`R-11` forbids letting "a machine-decidable rule stand in for a design judgement", and the temptation to do exactly
that is strongest where the machine has just said yes.

## The escalation rule (R-10)

R-10 covers the other direction: a finding is real, and the rule behind it cannot be satisfied honestly. The exits
that look like fixes are all dishonest — silence the rule, widen what it allows, move the line — and the design
closes each of them:

> A finding that cannot be fixed honestly is escalated, never suppressed

The three suppressions it names are no `@SuppressWarnings`, no allowlist widening, no threshold bump. What it
permits instead is escalation, and it prefers reshaping the code over changing the config — the linter skill's own
bias, the origin R-10 is generalized from: `reshaping-module-by-linters` "fixes code rather than config".

The procedure applies the same rule from the start. Step 3 baselines the gate — run it, record what it says, "red or
noisy" — because "That baseline is what later diffs are measured against"; the step ends with the same instruction,
"No suppressions (R-10)".

## What an escalation should ask

Escalating is not refusing the work; it is handing the decision to whoever owns the design with the trade-off made
legible. Three facts make the question answerable rather than a vote:

- **The finding.** What the tool reported, and under which rule — the artifact, the place and the rule's own words,
  not a summary of the inconvenience.
- **The rule.** The `R-n` ID it violates, and the layer that owns it; a rule changes by adding or superseding an ID,
  not by quietly editing prose that machinery believes it implements.
- **The cost of each option.** Reshaping the code until it satisfies the rule; changing the contract, if the rule is
  what is wrong; or accepting the divergence and writing it into the record as a ruling (`R-12`).

The one item that is not an option is the one R-10 forbids: a change that makes the finding stop without making it
false.

## Worked examples

Both are in `skreeep2`, 2026-09-19:

| What happened | Commits | Rule it demonstrates |
|---|---|---|
| "A linter finding was fixed by reshaping the code instead of suppressing it — and the rule's own source was read to find out what it decides" | `5170561` | `R-10` |
| "An independent lane found the contract worded for one implementor only, a lost check direction, and a test that proved less than it claimed" | `5170561`, `6863013` | `R-9`, `R-11` |

The first is the escalation performed properly: the finding was answered in the code, and the rule's source was read
before deciding what it actually required — which is how you tell a real finding from a misread one. The second is
`R-11`'s evidence in the other direction: the gate was green over wording that a lane then judged wrong, because the
wording was never the machine's to judge.

## See Also

- [The Reengineering Contract: Rules R-1 … R-14](reengineering-contract.md) — the spine: all fourteen rules, and what each forbids and permits
- [Verification and the Gate](verification-and-the-gate.md) — `R-8`, `R-9`: the gate's own output, and the independent lane that judges what a gate cannot
