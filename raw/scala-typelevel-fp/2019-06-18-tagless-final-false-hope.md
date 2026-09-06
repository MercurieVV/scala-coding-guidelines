# The False Hope of Managing Effects with Tagless-Final in Scala

> Source: https://degoes.net/articles/tagless-horror
> Collected: 2026-09-06
> Published: 2019-06-18

By John A De Goes.

## Main arguments against tagless-final

1. **Premature Indirection** — Adding abstraction layers for hypothetical future changes imposes ongoing maintenance costs that rarely justify themselves. Applications typically don't abstract over technologies like Akka or Slick; doing so for effect types creates opportunity costs by restricting access to powerful, type-safe operations.

2. **Untestable Effects** — While testability appears possible, it depends entirely on whether type classes themselves are testable. Popular classes like `Sync` and `Async` are "explicitly designed to capture side-effecting code," making them inherently untestable. Testability stems from coding to interfaces, achievable without tagless-final.

3. **No Effect Parametric Polymorphism** — Scala lacks effect tracking; implicit parameters cannot constrain side-effects. De Goes argues: "effect parametric reasoning is a lie." Any reasoning benefits depend solely on social contracts enforced through code review discipline, not language features.

4. **Sync Bloat** — Real-world tagless-final code liberally uses unconstrained side-effect type classes, creating "opaque blobs of side-effecting, untestable procedural code."

5. **Fake Abstraction** — Tagless-final type classes lack algebraic laws. De Goes emphasizes: "generic reasoning requires abstractions...The moment we create fake abstractions...we aren't doing principled functional programming anymore." Without laws, operations like `putStrLn` remain semantically unspecified.

## Key examples and code

Console type class (basic example):
```scala
trait Console[F[_]] {
  def putStrLn(line: String): F[Unit] 
  val getStrLn: F[String]
}
```

Demonstrating untracked side-effects:
```scala
def consoleProgram[F[_]: Applicative]: F[String] = {
  println("What is your name?")
  val name = scala.io.StdIn.readLine()
  Applicative[F].pure(name)
}
```
This shows how side-effects execute without declaring them in the type signature.

## Key quotes

- "Premature indirection rarely pays for itself."
- "The testability of your application is completely orthogonal to its use of tagless-final."
- "Implicit parameters don't actually constrain; they unconstrain, giving more ways to construct values."
- "These 'guarantees' come from discipline, not from the Scala compiler."

## Conclusion

De Goes concludes that tagless-final's promised benefits — future-proofing, testability, and parametric reasoning — are largely illusory. The technique imposes pedagogical and institutional costs without delivering meaningful guarantees. He recommends:

- **Established teams** with working social contracts may continue using it
- **Library authors** should use indirection to support multiple effect types
- **Most applications** should pick concrete effect types and rely on interface-based design with traditional dependency injection

The overarching recommendation: pursue real abstractions governed by algebraic laws rather than "fake abstractions" masquerading as principled functional programming.
