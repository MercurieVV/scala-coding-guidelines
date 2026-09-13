# DI and the Cake Pattern

> Source: https://www.mattheworford.dev/blog/di-in-scala
> Collected: 2026-09-13
> Published: 2024-04-08

By Matthew Orford (mattheworford.dev blog).

"The cake pattern uses traits and self-types to wire dependencies at compile time, no framework required. It's clever, type-safe, and weirdly good at making ordinary wiring feel like spelunking."

## Cake pattern example

```scala
trait UserRepositoryComponent {
  val userRepo: UserRepository
  class UserRepository {
    // Implementation
  }
}

trait UserServiceComponent {
  self: UserRepositoryComponent =>
  val userService = new UserService(userRepo)
  class UserService(repo: UserRepository) {
    // Implementation
  }
}

object UserModule extends UserRepositoryComponent with UserServiceComponent
```

"The pitch is reasonable: explicit dependencies, compile-time wiring, modular design. The compiler catches missing pieces before you ship."

## The cost as it scales

"The part people underprice is the shape of the codebase after it grows.

Traits start depending on other traits, self-types accumulate and the wiring graph becomes a second program next to the program you meant to write.

New engineers (like myself a few years ago) learn the dependency system before they learn the business logic.

The compiler may understand it, but the team still has to as well."

## Constructor injection as the default

```scala
class UserRepository(db: Database) {
  // Implementation
}
class UserService(repo: UserRepository) {
  // Implementation
}

val db = new Database(...)
val repo = new UserRepository(db)
val service = new UserService(repo)
```

"Notice there are no traits, no self-types and no component hierarchy.

You can read it, you can test it, and you can refactor it. If you forget a dependency, the compiler still complains. The cake pattern's real value is not compile-time safety in general (constructor injection already gives you that). The value is enforcing a particular structure.

Sometimes that structure helps, but usually, it's ceremony with a confident accent."

## When the author would reach for it

"I would consider the cake pattern for framework code, library extension points, or a very large Scala codebase where a component structure is already the organizing principle.

For ordinary services, just use constructor injection until it hurts. If the wiring gets annoying, look at MacWire or Airframe before reaching for full cake-pattern architecture.

The plain default is plain for a reason: the best dependency injection system is the one you stop noticing."
