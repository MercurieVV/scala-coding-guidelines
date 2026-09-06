# Flavors of Shared State in Cats Effect

> Source: https://blog.kubukoz.com/flavors-of-shared-state/
> Collected: 2026-09-06
> Published: 2024-03-02

By Jakub Kozłowski.

## Overview

Explores multiple approaches for managing shared state in Cats Effect applications, addressing the practical need to maintain state when interacting with the real world — from simple request counters to complex resource pools and rate limiters.

## State management options

### 1. Ref

- Atomic updates across multiple fibers
- Globally shared within the scope where visible
- Simple to implement
- Use case: "suitable for a counter of all requests an application has received while it's up"
- Limitation: changes propagated from child fibers don't reflect in parent fibers, causing lost updates in parallel scenarios.

### 2. IOLocal

- Provides "fiber" local isolation (analogous to ThreadLocal but for fiber-based concurrency)
- Updates don't propagate to parent/child fibers
- Clean isolation between requests
- Use case: isolating state between concurrent server requests
- Limitation: if child fibers execute in parallel, their state changes won't bubble up to the parent, resulting in lost updates.

### 3. IOLocal[Ref] (composite approach)

- Combines both constructs: wraps mutable `Ref` inside `IOLocal`
- Provides fiber-local isolation while enabling parent-child state propagation
- Offers granular control over when state "forks"
- How it works: `IOLocal` prevents scope leakage, while the inner `Ref` handles cross-fiber updates. Before each request, a fresh `Ref` is created and placed in the local — providing isolation without losing updates from parallel child fibers.

## Tradeoffs summary

| Approach | Isolation | Parallel Child Updates | Complexity |
|----------|-----------|------------------------|------------|
| Ref | None (global) | Yes | Low |
| IOLocal | Strong | No | Medium |
| IOLocal[Ref] | Strong | Yes | High |

## Code example: IOLocal[Ref]

```scala
val localRefCounter: IO[CounterWithReset] =
  Ref[IO].of(0).flatMap(IOLocal(_)).map { local =>
    val c = makeCounter(
      local.get.flatMap(_.update(_ + 1)),
      local.get.flatMap(_.get)
    )
    // ... reset logic
    CounterWithReset(c, withFreshK)
  }
```

## Conclusion & recommendation

"for most usecases in HTTP applications that desire 'reader monad' semantics of state, `IOLocal` + `Ref` should be preferred over just `IOLocal`."

Rationale: using `IOLocal` alone risks subtle bugs when libraries introduce concurrency downstream. The composite approach balances isolation with correctness, preventing lost updates while maintaining per-request scoping.

Selection guide: choose `Ref` for application-wide sharing; prefer `IOLocal[Ref]` when combining isolation with reliable parallel propagation; use `IOLocal` only when child fiber updates needn't influence parent state.
