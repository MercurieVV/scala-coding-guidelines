# Applying Recursion Schemes When the Traversable Structure Isn't Obvious

> Source: Self-authored synthesis note (Claude Code), based on droste/recursion-schemes fundamentals in this topic's other raw files
> Collected: 2026-09-06
> Published: 2026-09-06

Working notes on how to reach for catamorphism/anamorphism/hylomorphism style thinking even when the data being processed doesn't look like an obvious recursive tree (JSON AST, expression tree, filesystem tree). This is opinion/heuristic, not a quoted external source — treat as guidance to apply, not as a fact requiring grounding-invariant verification.

## Heuristic: find the pattern functor first, not the recursion

1. Ask "what is one layer of this thing, with the recursive parts blanked out as a type parameter `A`?" That blanked-out shape is the pattern functor `F[_]`. If you can write `sealed trait FooF[A]` with constructors that either hold no `A` (a leaf) or hold one/many `A` (a branch), a recursion scheme applies — even if the original type didn't look tree-shaped (e.g. a linked list, a `Free` monad, a parser's step type, a state machine's transition graph, a paginated API cursor).
2. If you can't write that `F[A]`, recursion schemes don't fit — reach for `Functor`/`Traverse`/`foldLeft` instead, not for droste.

## Heuristic: pick the scheme by what info the fold/unfold step needs

- Only need the already-folded child results, nothing else → **catamorphism** (`Algebra[F, A]`, `F[A] => A`).
- Need the folded result *and* the original unfolded sub-structure at that point → **paramorphism**.
- Need access to *any* earlier computed value in the structure, not just the immediate children → **histomorphism** (via `Cofree`).
- Only producing a structure from a seed, no source structure exists yet → **anamorphism** (`Coalgebra[F, A]`, `A => F[A]`).
- Producing then immediately consuming a structure, and materializing the intermediate structure would be wasteful or blow the stack/heap → **hylomorphism** (fuses ana + cata without building the intermediate `Fix[F]`).
- Unfolding but need to bail out early with a already-final value instead of continuing to unfold → **apomorphism**.
- Two or more recursive computations over the same structure that should run in one pass (e.g. droste's Fibonacci + sum-of-squares example) → zip/compose algebras (droste's `zoo`), or **zygomorphism**.

## Heuristic: when the "structure" is implicit rather than a data type

Recursion schemes aren't limited to explicit ADTs. The same `F[A] => A` / `A => F[A]` shape shows up whenever there's a step function with a "one level of expansion/contraction" flavor:

- A recursive descent over API pagination cursors: `Coalgebra[Option, Cursor]` where `None` means "done."
- Directory traversal without materializing a `Fix` tree: `Coalgebra[Vector, Path]` (list children) fused via hylomorphism with an `Algebra` that aggregates (e.g. total file size) — avoids holding the whole tree in memory.
- A parser combinator's single-step grammar rule: the pattern functor is the grammar's own step type; catamorphism becomes "interpret," anamorphism becomes "parse."
- A state machine: `Coalgebra[F, State]` where `F` encodes the possible transitions out of one state.

In each case the test from the first heuristic still applies: write down "one step, with recursion blanked out," and check whether that's expressible as a single covariant type constructor `F[_]`. If yes, droste's `Algebra`/`Coalgebra`/`scheme.cata`/`scheme.ana`/`scheme.hylo` machinery applies directly, using `Fix[F]` (or a `Basis[F, T]` instance for an existing recursive type `T`) as the fixed point — no need to invent bespoke recursive functions per case.

## When not to bother

If the structure only ever needs a single fold direction, has no more than one or two recursion sites in the whole codebase, and no plan to add histomorphism/zygomorphism-style variants later, plain recursive functions or `Functor`/`Traverse` combinators are simpler and avoid pulling in droste's `Fix`/`Basis`/macro machinery for one call site.
