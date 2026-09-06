# Monads Categorically

> Source: https://bartoszmilewski.com/2016/12/27/monads-categorically/
> Collected: 2026-09-07
> Published: 2016-12-27

By Bartosz Milewski. Part of the "Categories for Programmers" series (Part 22).

## Categorical definition of a monad

A monad is an endofunctor **T** equipped with two natural transformations:

- **μ (mu)** — "a natural transformation from the square of the functor T² back to T," with component μₐ :: T(T a) → T a (analogous to `join` in Haskell)
- **η (eta)** — "a natural transformation between the identity functor I and T," with component ηₐ :: a → T a (analogous to `return`)

These must satisfy associativity and unit laws expressible through commuting diagrams.

## Motivation: expression substitution

The article opens with an intuitive explanation: substituting variables in expressions exemplifies monadic structure. Replacing variable `x` in an expression with another expression is captured by the type signature: m a → (a → m b) → m b, which is monadic bind.

## "Monoid in the category of endofunctors"

The piece develops this famous insight through monoidal categories:

1. **Monoidal Category Framework:** a category with a tensor product (⊗), unit object, and natural isomorphisms (associator and unitors)
2. **Monoid in Monoidal Category:** an object with morphisms μ :: m ⊗ m → m and η :: i → m satisfying monoidal laws
3. **The Key Insight:** endofunctors form a strict monoidal category where function composition serves as the tensor product. A monoid in this category is exactly a monad.

Key quote: "All told, monad is just a monoid in the category of endofunctors" — attributed to Saunders Mac Lane.

## Monads from adjunctions

An adjunction L ⊣ R between categories induces a monad on R ∘ L. The natural transformation μ is constructed via: μ = R ∘ ε ∘ L (where ε is the counit).

The **State monad** exemplifies this: it arises from the exponential adjunction between product and reader functors, with `join` defined as `uncurry runState . runState`.

## Key diagrams referenced

- Expression tree (variable substitution)
- Associativity law for functors (two paths reducing T³ to T)
- Unit laws using unitors (λ and ρ)
- Monoidal category diagrams
- Kleisli composition

## Core insight

The article bridges programmer intuition (effects, sequential computations) with category-theoretic formalism, showing that monadic structure emerges naturally from either compositional abstraction (adjunctions) or algebraic composition laws (monoids in endofunctor categories).
