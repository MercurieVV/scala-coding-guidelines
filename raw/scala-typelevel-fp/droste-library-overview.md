# Droste: A Recursion Library for Scala

> Source: https://github.com/higherkindness/droste (README.md, https://raw.githubusercontent.com/higherkindness/droste/main/README.md)
> Collected: 2026-09-06
> Published: Unknown

## Description

Droste is a recursion library for Scala that enables developers to assemble morphisms — recursive operation patterns — using functional programming concepts. It provides generalized folds, unfolds, and traversals for fixed point data structures. Tagline: "recursion schemes for cats; to iterate is human, to recurse, divine."

## Core Recursion Schemes ("the zoo")

- **Catamorphism** (fold): recursively reduces a structure
- **Anamorphism** (unfold): recursively builds a structure
- **Hylomorphism**: combines unfold then fold operations; can be implemented directly without materializing the intermediate fixed-point structure
- **Histomorphism**: fold with access to previously computed results at earlier recursion levels
- **Dynamorphism**: anamorphism followed by histomorphism
- **Zygomorphism**: multiple simultaneous recursive computations

## Key Types

- `Algebra[F, A]`: describes how to collapse one functor step (`F[A] => A`)
- `Coalgebra[F, A]`: describes how to expand into one functor step (`A => F[A]`)
- `CVAlgebra` and `RAlgebra`: variants used for histomorphism and zygomorphism respectively
- `Basis`: typeclass providing `embed` (construct the fixed point from one functor layer) and `project` (deconstruct it back to one functor layer)

## Installation

```scala
libraryDependencies += "io.higherkindness" %% "droste-core" % "x.y.z"
```

Replace `x.y.z` with a tagged release version (README does not pin a specific version string).

## Example: Fibonacci via ghylo (generalized hylomorphism)

Computing Fibonacci numbers by unfolding natural numbers via anamorphism, then folding results via histomorphism (a "dynamorphism" pattern), expressed with the generalized hylomorphism combinator:

```scala
val fib: BigDecimal => BigDecimal = scheme.ghylo(
  fibAlgebra.gather(Gather.histo),
  natCoalgebra.scatter(Scatter.ana))
```

## Additional Features

- Macro support for deriving pattern functors
- `athema` module: an expression parser/processor used as an extended example
- Composable algebras enabling multiple computations (e.g. Fibonacci and sum-of-squares) fused into a single recursive pass by zipping algebras together

## Attribution

The project acknowledges derivations from established recursion-scheme libraries including `recursion-schemes` (Haskell) and Matryoshka (Scala), plus contributions from the broader functional-programming community.

## License

Apache License 2.0 (2018-present).
