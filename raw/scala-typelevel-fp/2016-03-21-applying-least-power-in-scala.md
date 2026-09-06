# Applying Least Power in Scala

> Source: https://eddsteel.com/posts/least-power
> Collected: 2026-09-07
> Published: 2016-03-21

By Edd Steel.

## Core principle

The "Principle of Least Power" (also called the "Uji Principle") suggests selecting the least powerful tool sufficient for solving a problem. Steel illustrates this with five approaches to incrementing list values, from `map` to shared mutable state, arguing that more constrained solutions are superior.

## Key insight from Rúnar Bjarnason

Bjarnason's talk "Constraints liberate, liberties constrain" (https://www.youtube.com/watch?v=GqmsQeSzMdw) clarifies that "power" here means unconstrained capability. Steel notes: "The more a system *can* do, the less we can predict what it *will* do." More abstract, generic functions are actually *less powerful* by this definition — and therefore more desirable.

## Practical application in Scala

Steel demonstrates three iterations of a gift-ordering endpoint:

- **V1:** Traditional approach with mocks, framework coupling, and complex test setup.
- **V2:** Extracts logic into pure functions, eliminates unnecessary mocks.
- **V3:** Passes dependencies as functions and plain values rather than objects.

By V3, the test shrinks to three lines with no mocking framework required. Functions receive only the data they actually need, making code more testable and maintainable.

## Recommendations

Steel endorses Li Haoyi's guidance to pass functions instead of objects when methods use only single operations, and to inject plain values directly rather than complex dependency containers. This approach reduces coupling, improves testability, and clarifies intent.
