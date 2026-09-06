# F-Algebra (Wikipedia)

> Source: https://en.wikipedia.org/wiki/F-algebra
> Collected: 2026-09-07
> Published: Unknown

## Definition

An **F-algebra** is a pair (A, α) where A is an object in category C and α is a morphism F(A) → A, with F being an endofunctor. The morphism α represents the "structure" on A. A homomorphism between F-algebras must satisfy the commutative property: "f ∘ α = β ∘ F(f)".

The dual concept, an **F-coalgebra**, reverses this: it pairs an object A* with a morphism α*: A* → F(A*).

## Initial F-algebra

An initial algebra is "an initial object" in the category of F-algebras. The classic example is natural numbers paired with functions for zero and successor. Initial algebras encapsulate induction and serve as constructors — they build up recursive structures from primitive operations.

## Terminal F-coalgebra

Terminal coalgebras represent the dual: they work with "greatest fixed point" constructs. These enable potentially infinite objects while maintaining strong normalization, functioning as destructors or observers that decompose structures.

## Key examples

- **Groups**: F(G) = 1 + G + G×G combines identity, inverse, and multiplication operations
- **Monoids**: F(M) = 1 + M×M
- **Rings**: multiple operations glued via coproduct
- **Natural numbers**: the classic recursive datatype example

Both initial algebras and terminal coalgebras are fundamental to functional programming and formal semantics of recursive data types.
