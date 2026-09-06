# Provider/Consumer Design: Least Power, Algebras, and Modular Composition

> Sources: Rúnar Bjarnason, "Constraints Liberate, Liberties Constrain" (talk, via Edd Steel's write-up, 2016-03-21); Wikipedia, F-algebra (Unknown date); Wouter Swierstra, "Data types à la carte", 2008-07-01
> Raw: [Applying Least Power in Scala](../../raw/scala-typelevel-fp/2016-03-21-applying-least-power-in-scala.md); [F-Algebra (Wikipedia)](../../raw/scala-typelevel-fp/f-algebra-wikipedia.md); [Data Types à la Carte](../../raw/scala-typelevel-fp/2008-data-types-a-la-carte.md)
> Updated: 2026-09-07

## Overview

"Provider" and "consumer" of a module boundary is really two sides of the same algebraic coin: a **provider** builds values of a type from smaller pieces (a constructor, an algebra); a **consumer** observes or interprets values of a type (a destructor, a coalgebra, an interpreter). Three independent threads converge on the same idea — pick the least powerful interface that does the job, and you get a system whose pieces compose without knowing about each other.

## Least power: fewer capabilities, more freedom

Rúnar Bjarnason's talk "Constraints Liberate, Liberties Constrain" (https://www.youtube.com/watch?v=GqmsQeSzMdw) reframes "power" as *unconstrained capability*, not usefulness — "the more a system *can* do, the less we can predict what it *will* do." A function that only needs `Applicative` is, by this definition, *less powerful* and therefore *more* desirable than one that demands `Monad`, because its signature rules out entire classes of behavior (no effect can depend on a previous result) that the caller would otherwise have to check for by reading the implementation.

Concretely (Edd Steel, "Applying Least Power in Scala"): passing a plain function or a plain value into a component, instead of an object/interface bundling many capabilities, shrinks what that component's caller must supply and what its tests must fake. A dependency expressed as `String => F[User]` cannot secretly also delete the user; a full `UserRepo` trait can. The provider (the caller wiring dependencies) and the consumer (the function receiving them) are both easier to reason about the smaller the passed-in capability is — this is the same principle already applied to `F[_]` constraints in [Scala 3 Type-Level Features](scala3-type-level-features.md) and to algebra design in [Tagless-Final Architecture & Hand-Wired DI](tagless-final-and-di-architecture.md).

## The formal shape: F-algebras and F-coalgebras

Category theory has a name for "provider" and "consumer" as dual constructions. For an endofunctor `F`:

- An **F-algebra** is a pair `(A, α)` with `α : F(A) -> A` — a way to *collapse* one layer of `F`-structure into `A`. This is a **constructor/provider**: given the pieces (`F(A)`), it produces a value (`A`). The initial F-algebra is the datatype itself, built purely from these construction rules (natural numbers from zero + successor is the classic example).
- An **F-coalgebra** is the dual: a pair `(A, α)` with `α : A -> F(A)` — a way to *expand* a value into one layer of `F`-structure. This is a **destructor/observer/consumer**: given a value, it reveals its next layer of structure. The terminal F-coalgebra allows potentially-infinite structures, observed one step at a time.

This is exactly the `Algebra[F, A]` / `Coalgebra[F, A]` pair already covered in [Recursion Schemes and the Droste Library](recursion-schemes-and-droste.md) — a catamorphism folds by repeatedly applying a provider (algebra); an anamorphism unfolds by repeatedly applying a consumer (coalgebra). "Provider vs. consumer" and "algebra vs. coalgebra" are the same duality at two levels of formality.

## Composing independently-built providers: Data Types à la Carte

Swierstra's "Data types à la carte" (2008) solves a very concrete version of the provider-composition problem: how do independently-written pieces of syntax (each its own functor — `Val`, `Add`, `Mul`, ...) combine into one expression type without editing each other, and how does a consumer (an evaluator) work over the combination without knowing which pieces are present?

The mechanism is a **Coproduct** of functors, `(f :+: g) e = Inl (f e) | Inr (g e)`, plus an **Inject** typeclass `sub :<: sup` (`inj`/`prj`) that lets a term from one small functor be injected into — and later projected back out of — the combined signature, automatically, via typeclass resolution rather than manual wrapping. A new operation (e.g. `Mul`) is added by defining its own functor and its own evaluator instance; nothing about the existing `Val`/`Add` code changes, and the combined evaluator dispatches correctly the moment `Mul` is in the coproduct.

This is precisely what cats' `EitherK` (the Coproduct) and `InjectK` (the `:<:`) give Free-monad-based algebra composition in Scala — see [Cats & Category-Theory Patterns](cats-category-theory-patterns.md) and [Tagless-Final Architecture & Hand-Wired DI](tagless-final-and-di-architecture.md): each module's algebra is its own functor/ADT (a small, independent *provider*), `EitherK` combines any number of them without their knowing about each other, and one interpreter (a natural transformation, the *consumer*) is built to handle the combination — extended by adding a new algebra and a new case to the interpreter, never by editing the existing ones.

## The unifying picture

| Framing | Provider side | Consumer side |
|---|---|---|
| Bjarnason / least power | the capability a dependency exposes | the caller that only asks for what it needs |
| Category theory | F-algebra (`F(A) -> A`), constructor | F-coalgebra (`A -> F(A)`), destructor/observer |
| Droste / recursion schemes | `Algebra[F, A]`, folds via catamorphism | `Coalgebra[F, A]`, unfolds via anamorphism |
| Data types à la carte / `EitherK`+`InjectK` | one functor per independently-built algebra | one interpreter (natural transformation) over their coproduct |

In each row, minimizing what the provider commits to (fewer required capabilities, a smaller functor, a narrower algebra) is what lets consumers compose freely — the throughline that connects "pass a function instead of an interface" all the way up to "combine independently-developed algebras via Coproduct."

## See Also

- [Tagless-Final Architecture & Hand-Wired DI](tagless-final-and-di-architecture.md)
- [Cats & Category-Theory Patterns](cats-category-theory-patterns.md)
- [Recursion Schemes and the Droste Library](recursion-schemes-and-droste.md)
- [Scala 3 Type-Level Features](scala3-type-level-features.md)
