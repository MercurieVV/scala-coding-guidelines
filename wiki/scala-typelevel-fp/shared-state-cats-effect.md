# Shared State in Cats Effect: Ref, IOLocal, IOLocal[Ref]

> Sources: Jakub Kozłowski, 2024-03-02
> Raw: [Flavors of Shared State in Cats Effect](../../raw/scala-typelevel-fp/2024-03-02-flavors-of-shared-state-cats-effect.md)
> Updated: 2026-09-06

## Overview

Three constructs cover most shared-mutable-state needs in a Cats Effect application, from a simple request counter to per-request isolated state that must still see updates from concurrently forked children: `Ref`, `IOLocal`, and the composite `IOLocal[Ref]`.

## The three options

**`Ref`** — atomic, globally shared within whatever scope holds the reference; simple to use. Suitable for "a counter of all requests an application has received while it's up." Limitation: updates made in a child fiber and updates made in its parent don't automatically reconcile beyond the atomicity `Ref` itself provides — there's no per-request isolation, so unrelated logical requests share the same cell.

**`IOLocal`** — fiber-local storage, analogous to `ThreadLocal` but scoped to a fiber rather than a thread. Gives clean isolation between concurrent requests (each fiber's local doesn't leak into siblings). Limitation: if a request forks child fibers that run in parallel, their `IOLocal` writes don't propagate back to the parent — updates made by children are lost from the parent's point of view.

**`IOLocal[Ref]` (composite)** — an `IOLocal` whose held value is itself a `Ref`. `IOLocal` gives per-request isolation (a fresh `Ref` is placed in the local before each request starts); the inner `Ref` gives correct cross-fiber updates *within* that request, since children read the same `Ref` value out of the local rather than getting an isolated copy. This combines isolation-between-requests with correctness-within-a-request.

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

## Tradeoffs

| Approach | Isolation | Parallel Child Updates | Complexity |
|---|---|---|---|
| `Ref` | None (global) | Yes | Low |
| `IOLocal` | Strong | No | Medium |
| `IOLocal[Ref]` | Strong | Yes | High |

## Recommendation

"for most usecases in HTTP applications that desire 'reader monad' semantics of state, `IOLocal` + `Ref` should be preferred over just `IOLocal`" — plain `IOLocal` risks subtle lost-update bugs the moment a downstream library introduces its own concurrency (e.g. parallel calls fired from within one request handler). Reach for bare `Ref` when state is genuinely meant to be shared application-wide rather than scoped per request.

## See Also

- [Typelevel FP References](typelevel-fp-references.md)
