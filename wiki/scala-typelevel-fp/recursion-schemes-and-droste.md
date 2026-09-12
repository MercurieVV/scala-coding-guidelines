# Recursion Schemes and the Droste Library

> Sources: higherkindness/droste README and source (Unknown date); Ziyang Liu, "Curried Thoughts" blog, 2017-11-13; Claude Code synthesis, 2026-09-06, 2026-09-12, 2026-09-13; Wikipedia, F-algebra (Unknown date); ekmett/recursion-schemes README (Unknown date); precog/matryoshka README (Unknown date)
> Raw: [Droste Library Overview](../../raw/scala-typelevel-fp/droste-library-overview.md); [Recursion Schemes in Scala — Elementary Introduction](../../raw/scala-typelevel-fp/2017-11-13-recursion-schemes-scala-elementary-intro.md); [Applying Recursion Schemes When Structure Is Unclear](../../raw/scala-typelevel-fp/recursion-schemes-when-structure-unclear-notes.md); [F-Algebra (Wikipedia)](../../raw/scala-typelevel-fp/f-algebra-wikipedia.md); [Droste Source: Basis and Fixpoints](../../raw/scala-typelevel-fp/droste-source-basis-and-fixpoints.md); [recursion-schemes (Haskell) README](../../raw/scala-typelevel-fp/recursion-schemes-haskell-readme.md); [Matryoshka README](../../raw/scala-typelevel-fp/matryoshka-readme.md)
> Updated: 2026-09-13

## Overview

Recursion schemes factor the *shape* of a recursive traversal (fold, unfold, fold-then-unfold, or something stranger) out from the per-node logic, by expressing a recursive type as the fixed point of a non-recursive "pattern functor." Droste is the Scala/cats library implementing this family of schemes, descending from Haskell's `recursion-schemes` and Scala's earlier Matryoshka library. This is the entry-point overview article; the full usage reference is split across two companion articles:

- [Droste Algebra/Coalgebra Type Taxonomy](droste-algebra-coalgebra-taxonomy.md) — every algebra/coalgebra entity type, the `Fix`/`Mu`/`Nu`/`Basis` fixpoint machinery, and macro/`derives`-based pattern-functor derivation.
- [Droste Recursion Scheme Zoo and Worked Examples](droste-scheme-zoo-examples.md) — every named scheme with a working code example and links to droste's own source and smoke tests on GitHub, plus the `athema` real-world example application.

## Core mechanics

- **Pattern functor `F[_]`**: one layer of a recursive structure with the recursive slot replaced by a type parameter `A`. E.g. a list's pattern functor has a no-`A` constructor (nil) and a one-`A` constructor (cons).
- **Fixpoint type**: `final case class Fix[F[_]](unfix: F[Fix[F]])` ties the pattern functor back into the actual recursive type — the same role the value-level fixpoint combinator (`fix(f) = f(fix(f))`) plays for recursive functions. Droste also provides `Mu[F]` (least fixed point — inductive/finite data, Church-encoded as "a value together with the fold that consumes it") and `Nu[F]` (greatest fixed point — coinductive/potentially-infinite codata, encoded as "a seed together with the unfold that regenerates it"), mirroring the same distinction documented in Matryoshka. Full source and the Church/existential encodings are in [Droste Algebra/Coalgebra Type Taxonomy](droste-algebra-coalgebra-taxonomy.md).
- **`Basis[F, T]` / `Embed[F, T]` / `Project[F, T]`**: typeclasses supplying `embed` (`F[T] => T`) and `project` (`T => F[T]`) between the pattern functor `F` and an existing recursive type `T`, so schemes can run over a type that isn't literally `Fix[F]` — including a hand-written sealed-trait ADT you already have. Droste ships built-in `Basis` instances for `List`, `Attr`, `Coattr`, `Fix`, `Mu`, `Nu`, `cats.free.Cofree`, and `cats.free.Free`.

`Algebra[F, A]` and `Coalgebra[F, A]` are exactly an **F-algebra** (`F(A) -> A`) and its dual **F-coalgebra** (`A -> F(A)`) from category theory (Wikipedia, "F-algebra"). An F-algebra is a *constructor*: given one layer of `F`-structure, produce a value — the initial F-algebra is the datatype itself. An F-coalgebra is a *destructor/observer*: given a value, reveal its next layer of structure — the terminal F-coalgebra is what lets potentially-infinite structures be consumed one step at a time. Catamorphism = repeatedly apply the algebra (fold, provider side); anamorphism = repeatedly apply the coalgebra (unfold, consumer side). See [Provider/Consumer Design: Least Power, Algebras, and Modular Composition](provider-consumer-least-power.md) for the same duality applied to module boundaries generally.

## Recognizing recursion-scheme shape when the structure isn't an obvious tree

The key move is to look for the pattern functor before looking for the recursion: ask "what does one step look like, with the recursive part blanked out as `A`?" If that's expressible as a single covariant `F[_]`, an `Algebra`/`Coalgebra` applies — regardless of whether the original type visually resembles a tree. This generalizes past ASTs/expression trees to:

- API pagination: `Coalgebra[Option, Cursor]`, `None` signaling done.
- Directory walks fused via hylomorphism (e.g. aggregate total size) without materializing the whole tree.
- A parser combinator's single grammar step, or a state machine's per-state transition set.

Choose the specific scheme by what information the step needs (see the scheme table in [Droste Recursion Scheme Zoo and Worked Examples](droste-scheme-zoo-examples.md)) — e.g. "only needs folded children" → catamorphism, "needs to see any earlier computed value" → histomorphism, "would otherwise materialize a throwaway intermediate structure" → hylomorphism.

**When not to bother:** a single fold direction, one or two recursion sites total, no plan to compose schemes later — plain recursion or `Functor`/`Traverse` is simpler than pulling in droste's `Fix`/`Basis`/macro machinery for one call site.

## Caveats

A from-scratch toy `Fix`/cata/ana implementation (as in introductory tutorials) is typically not stack-safe and can't handle infinite structures — droste (or historically Matryoshka) exists precisely to provide production-grade, stack-safe implementations of these schemes. Even droste isn't immune to stack overflow when a genuinely-infinite/cyclic coinductive structure (`Nu`) is fully forced — droste's own `NuLookup` test demonstrates a deliberately cyclic lookup table stack-overflowing when unrolled to completion (see [Droste Recursion Scheme Zoo and Worked Examples](droste-scheme-zoo-examples.md)).

## See Also

- [Droste Algebra/Coalgebra Type Taxonomy](droste-algebra-coalgebra-taxonomy.md)
- [Droste Recursion Scheme Zoo and Worked Examples](droste-scheme-zoo-examples.md)
- [Cats & Category-Theory Patterns](cats-category-theory-patterns.md)
- [Provider/Consumer Design: Least Power, Algebras, and Modular Composition](provider-consumer-least-power.md)
