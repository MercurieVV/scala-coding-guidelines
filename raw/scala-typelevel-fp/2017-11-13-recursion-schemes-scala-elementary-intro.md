# Recursion Schemes in Scala — An Absolutely Elementary Introduction

> Source: https://free.cofree.io/2017/11/13/recursion/
> Collected: 2026-09-06
> Published: 2017-11-13

Published 11/13/2017 on Ziyang Liu's blog "Curried Thoughts." Elementary introduction to recursion schemes using Scala, with factorial calculations as the primary running example.

## Fixpoint combinator

`fix(f) = f(fix(f))` — the fixpoint combinator converts recursive values into non-recursive ones by delegating recursion to `fix`.

## Fixpoint type

```scala
final case class Fix[F[_]](unfix: F[Fix[F]])
```

Allows recursive structure to be factored out at the type level, the same way the fixpoint combinator factors it out at the value level. `F[_]` here is the "pattern functor" — one layer of the recursive structure with the recursive slot abstracted out.

## Recursion scheme types covered

1. **Anamorphism** — generalizes unfold; builds recursive structures from a coalgebra of type `A => F[A]`.
2. **Catamorphism** — generalizes fold; deconstructs recursive structures using an algebra of type `F[A] => A`.
3. **Hylomorphism** — composes anamorphism then catamorphism; can be implemented directly without building the large intermediate `Fix` structure in memory.
4. **Paramorphism** — "more powerful than catamorphism in the sense that in the algebra `f`, we not only have an `F[A]` to work with" (also access to the original sub-structure, not just its folded result).
5. **Apomorphism** — dual of paramorphism; extends anamorphism with more control over terminating recursion early.
6. **Histomorphism** — operates on `Cofree` structures, annotating each node with its computed value while folding, so later steps can look back at any previously computed value in the tree, not just the immediate child.
7. **Dynamorphism** — composes anamorphism and histomorphism.
8. **Futumorphism** — dual of histomorphism; uses the `Free` type to give more flexible control over how many recursion layers are unfolded in one step.

## Practical benefits (per article)

1. Factoring out the recursion pattern itself, separate from the per-node logic.
2. Eliminating runtime bugs caused by missing or misplaced recursive calls.
3. Reducing boilerplate code.

## Caveats

The toy implementation given in the article "is not stack-safe, not as general as it can be, and cannot handle infinite recursions." For production use, the article recommends the **Matryoshka** library (a Scala recursion-schemes library; droste is a later alternative in the same space, built for cats).
