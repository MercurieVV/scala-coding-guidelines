# Data Types à la Carte

> Source: https://www.extrema.is/blog/2022/04/04/data-types-a-la-carte (reviewing Wouter Swierstra's paper); paper: Journal of Functional Programming, Vol 18(4):423-436, published 2008-07-01
> Collected: 2026-09-07
> Published: 2008-07-01

Original paper by Wouter Swierstra, "Data types à la carte," Journal of Functional Programming, Volume 18, Issue 4, pages 423-436, published 2008-07-01. Raw notes below summarize a 2022-04-04 review/explainer by Travis Cardwell (extrema.is).

## The problem solved

This work addresses the "expression problem" — the difficulty of extending both data types and operations without modifying existing code. Traditional sum types require recompilation when adding new constructors, creating tight coupling between components.

## Core concepts

**The Coproduct type (`:+:`)**

The coproduct operator combines multiple functors into a single signature: `data (f :+: g) e = Inl (f e) | Inr (g e)`. Think of it as cons-list-like composition where type constructors chain rightward, enabling modular expression building without monolithic sum types.

**The Inject typeclass (`:<:`)**

This typeclass defines membership within a signature composition:

```haskell
class sub :<: sup where
  inj :: sub a -> sup a
  prj :: sup a -> Maybe (sub a)
```

It enables automatic injection of terms into larger expression types through three instances: reflexivity (a type contains itself), left-association (direct containment), and recursive containment. This eliminates manual constructor wrapping.

## Composing independent functors

Each operation (addition, multiplication, value) is defined as a separate functor with its own `Functor` instance. Rather than creating one monolithic expression type, they compose via `:+:`. A calculator expression combining values, addition, and multiplication becomes `Expr (Val :+: Add :+: Mul)` without modifying prior definitions.

Smart constructors using `inject` and `:<:` constraints automatically route terms to appropriate positions: `val 30000 %+% val 1330` elegantly constructs expressions while the type system handles nesting logistics.

## Extensibility in action

New operations like multiplication integrate seamlessly — define `Mul`'s `Functor` and `Eval` instances, then use it alongside existing terms. Evaluation, pretty-printing, and pattern-matching via `prj` all work without touching previous code, demonstrating true modularity.
