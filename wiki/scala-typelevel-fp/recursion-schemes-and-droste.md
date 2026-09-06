# Recursion Schemes and the Droste Library

> Sources: higherkindness/droste README (Unknown date); Ziyang Liu, "Curried Thoughts" blog, 2017-11-13; Claude Code synthesis, 2026-09-06; Wikipedia, F-algebra (Unknown date)
> Raw: [Droste Library Overview](../../raw/scala-typelevel-fp/droste-library-overview.md); [Recursion Schemes in Scala — Elementary Introduction](../../raw/scala-typelevel-fp/2017-11-13-recursion-schemes-scala-elementary-intro.md); [Applying Recursion Schemes When Structure Is Unclear](../../raw/scala-typelevel-fp/recursion-schemes-when-structure-unclear-notes.md); [F-Algebra (Wikipedia)](../../raw/scala-typelevel-fp/f-algebra-wikipedia.md)
> Updated: 2026-09-07

## Overview

Recursion schemes factor the *shape* of a recursive traversal (fold, unfold, fold-then-unfold) out from the per-node logic, by expressing a recursive type as the fixed point of a non-recursive "pattern functor." Droste is the Scala/cats library implementing this family of schemes (`Algebra`/`Coalgebra`, `Fix`, `Basis`, and a "zoo" of named schemes), descending from Haskell's `recursion-schemes` and Scala's earlier Matryoshka library.

## Core mechanics

- **Pattern functor `F[_]`**: one layer of a recursive structure with the recursive slot replaced by a type parameter `A`. E.g. a list's pattern functor has a no-`A` constructor (nil) and a one-`A` constructor (cons).
- **Fixpoint type**: `final case class Fix[F[_]](unfix: F[Fix[F]])` ties the pattern functor back into the actual recursive type — the same role the value-level fixpoint combinator (`fix(f) = f(fix(f))`) plays for recursive functions.
- **`Algebra[F, A]`** (`F[A] => A`): collapses one functor layer — the "how to fold" description.
- **`Coalgebra[F, A]`** (`A => F[A]`): expands one functor layer — the "how to unfold" description.
- **`Basis[F, T]`**: typeclass supplying `embed`/`project` between the pattern functor `F` and an existing recursive type `T`, so schemes can run over a type that isn't literally `Fix[F]`.

`Algebra[F, A]` and `Coalgebra[F, A]` are exactly an **F-algebra** (`F(A) -> A`) and its dual **F-coalgebra** (`A -> F(A)`) from category theory (Wikipedia, "F-algebra"). An F-algebra is a *constructor*: given one layer of `F`-structure, produce a value — the initial F-algebra is the datatype itself, built purely from these rules (natural numbers from zero + successor is the textbook case). An F-coalgebra is a *destructor/observer*: given a value, reveal its next layer of structure — the terminal F-coalgebra is what lets potentially-infinite structures be consumed one step at a time. Catamorphism = repeatedly apply the algebra (fold, provider side); anamorphism = repeatedly apply the coalgebra (unfold, consumer side). See [Provider/Consumer Design: Least Power, Algebras, and Modular Composition](provider-consumer-least-power.md) for the same duality applied to module boundaries generally.

## The zoo of schemes

| Scheme | Shape | When to reach for it |
|---|---|---|
| Catamorphism | fold, `F[A] => A` | Only need already-folded child results |
| Anamorphism | unfold, `A => F[A]` | Building a structure from a seed, no source structure yet |
| Hylomorphism | ana then cata, fused | Build-then-consume where materializing the intermediate structure is wasteful (stack/heap) |
| Paramorphism | fold + original substructure | Need the folded result *and* the un-folded child, not just one |
| Apomorphism | dual of paramorphism | Unfolding but need to terminate early with a final value |
| Histomorphism | fold over `Cofree`, sees any prior computed value | Later steps depend on more than just the immediate child (e.g. Fibonacci) |
| Dynamorphism | ana then histo | Same shape as hylo but the fold side needs histomorphism's lookback |
| Futumorphism | dual of histomorphism, via `Free` | Need flexible control over how many layers get unfolded per step |
| Zygomorphism | multiple simultaneous folds | Two+ recursive computations over one structure, fused into a single pass |

Droste additionally exposes a generalized combinator, `scheme.ghylo`, that composes an arbitrary "gather" (fold side, e.g. `Gather.histo`) with an arbitrary "scatter" (unfold side, e.g. `Scatter.ana`) — droste's own example is computing Fibonacci by unfolding natural numbers via `ana` and folding via `histo` in one fused pass. Algebras can also be zipped together (droste's `zoo`) to run multiple computations — e.g. Fibonacci and sum-of-squares — over the same traversal.

## Recognizing recursion-scheme shape when the structure isn't an obvious tree

The key move is to look for the pattern functor before looking for the recursion: ask "what does one step look like, with the recursive part blanked out as `A`?" If that's expressible as a single covariant `F[_]`, an `Algebra`/`Coalgebra` applies — regardless of whether the original type visually resembles a tree. This generalizes past ASTs/expression trees to:

- API pagination: `Coalgebra[Option, Cursor]`, `None` signaling done.
- Directory walks fused via hylomorphism (e.g. aggregate total size) without materializing the whole tree.
- A parser combinator's single grammar step, or a state machine's per-state transition set.

Choose the specific scheme by what information the step needs (see table above) — e.g. "only needs folded children" → catamorphism, "needs to see any earlier computed value" → histomorphism, "would otherwise materialize a throwaway intermediate structure" → hylomorphism.

**When not to bother:** a single fold direction, one or two recursion sites total, no plan to compose schemes later — plain recursion or `Functor`/`Traverse` is simpler than pulling in droste's `Fix`/`Basis`/macro machinery for one call site.

## Caveats

A from-scratch toy `Fix`/cata/ana implementation (as in introductory tutorials) is typically not stack-safe and can't handle infinite structures — droste (or historically Matryoshka) exists precisely to provide production-grade, stack-safe implementations of these schemes.

## See Also

- [Cats & Category-Theory Patterns](cats-category-theory-patterns.md)
- [Provider/Consumer Design: Least Power, Algebras, and Modular Composition](provider-consumer-least-power.md)
