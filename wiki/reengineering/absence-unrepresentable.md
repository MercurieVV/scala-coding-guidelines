# Absence Unrepresentable: Guarantees in the Type, Contracts on the Member

> Sources: `skreeep2` session, 2026-09-19 (commits `4a3242c`, `6863013`, `5aad2f9`)
> Raw: [The reengineering kit — design (2026-09-19)](../../raw/reengineering/2026-09-19-kit-design.md)
> Updated: 2026-09-19

## Overview

`R-5` and `R-6` are two halves of one move: take a guarantee a runtime check was supposed to provide, and put it
where the compiler can see it. `R-5` moves it into the structure — the empty case stops existing. `R-6` moves it onto
the abstract member — one statement of the obligation, and every implementation total against it. Both are stated in
[The Reengineering Contract](reengineering-contract.md), the spine of this topic; this article carries their failure
mode, their fix, and the worked examples the design records for them.

## The rules

**R-5 — absence is unrepresentable, not checked.**

> Absence is unrepresentable, not checked: a non-empty structure in the type beats a runtime guard.

A member that must produce at least one element should not take a structure that may have none and then test it. The
empty value is removed from the type: no caller can pass one, and no guard is left for anyone to forget, to skip, or
to write for the wrong call site.

**R-6 — a contract is stated on the abstract member.**

> A contract is stated on the abstract member, and every implementation is total against it.

The obligation is written once, on the abstract member, where every implementor reads it; each implementation owes it
for every input its type admits, not only for the inputs its author had in mind.

## The failure mode

Neither rule is a style preference. Each answers something you can watch happen.

**Absence that was only checked.** A member that builds a non-empty structure from a possibly-empty input has to read
a first element that may not be there. Nothing in the types stops the caller, so the failure waits for the value that
happens to arrive, and it surfaces as a read at runtime rather than as a compile error. The design's F-2 case is the
observed form: "A control event before the first note killed the events stream; the fix stated the contract on the
abstract member, made the implementation total and shipped the regression test"

**A contract written for one implementor.** A contract drafted with one implementation in view is that
implementation's behaviour, promoted — the second implementor either cannot satisfy it or satisfies it partially,
and the type says nothing about which. The design records this session's finding in exactly those terms: "An
independent lane found the contract worded for one implementor only, a lost check direction, and a test that proved
less than it claimed" — carried in the worked-examples table under `R-9` and `R-11`, with the commits `5170561`,
`6863013`, because what caught it was an independent lane and what it exposed was wording that had passed for a
design judgement.

## The two moves

**Put non-emptiness in the type.** The `Tree` closure is the worked form. Two members built a non-empty structure
from a possibly-empty input; the fix made the band and the roots' inputs `Tree`s, so emptiness is unrepresentable.
`Tree` — a `NonEmptyList`-shaped guarantee in the general case — admits no empty value for a caller to pass and none
for an implementation to check, which is what a non-empty structure in the type beats a runtime guard means
concretely.

**State the contract on the abstract member, and make every implementation total.** One statement, on the member
every implementation already has to answer to. An implementation that cannot be total against it does not get to
weaken it privately: a second, weaker contract inside one implementation is the failure above, spelled differently.
If the obligation itself is wrong, that is a change to make deliberately — a design judgement, `R-11` — and where a
divergence cannot be reconciled, the record carries it as a ruling, `R-12`.

## Worked examples

Both examples are in `skreeep2`, 2026-09-19:

| What happened | Commits | Rules it demonstrates |
|---|---|---|
| "A control event before the first note killed the events stream; the fix stated the contract on the abstract member, made the implementation total and shipped the regression test" | `4a3242c`, `6863013` | `R-5`, `R-6`, `R-8` |
| "Two members built a non-empty structure from a possibly-empty input" — the band and the roots' inputs became `Tree`s, so "emptiness is unrepresentable" | `5aad2f9` | `R-5` |

The first is `R-6`'s evidence: the contract restated where it belongs, the implementation made total, and the
regression test shipped with the fix — the last part also answering `R-8`, whose rule is that no completion claim is
made without the gate's own output. The second is `R-5`'s: a guarantee that had been a property of the input becomes
a property of its type.

## See Also

- [The Reengineering Contract: Rules R-1 … R-14](reengineering-contract.md) — the spine: all fourteen rules, and what each forbids and permits
- [Named Shape Traits: Do Not Spell a Type Member's Shape at the Use Site](../scala-typelevel-fp/named-shape-traits.md) — `R-4`, the neighbouring rule about putting a shape in a name instead of at a use site
