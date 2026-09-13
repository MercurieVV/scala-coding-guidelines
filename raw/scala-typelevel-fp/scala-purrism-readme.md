# scala-purrism: README (installation, rule list, artifact coordinates)

> Source: https://github.com/MercurieVV/scala-purrism (master branch, `README.md`, https://raw.githubusercontent.com/MercurieVV/scala-purrism/master/README.md); rule catalogue per the published rule reference site https://mercurievv.github.io/scala-purrism/
> Collected: 2026-09-13
> Published: Unknown

<img src="docs/purrism.svg" alt="scala-purrism logo" width="160">

# scala-purrism

Scalafix semantic rules for refactoring Typelevel Scala code toward pure,
polymorphic Cats and Cats Effect style.

Full rule reference: <https://mercurievv.github.io/scala-purrism/>
([source](docs/index.md)). Published from `docs/` by the Pages workflow on every
push to `master`; `mill docs.run` renders the same site into `website/docs`
locally.

## Quickstart

Latest release:

```text
io.github.mercurievv:scala-purrism-scalafix_3:0.9.0
```

The published artifact targets Scala 3 and Scalafix `0.14.7`.

### sbt

Add sbt-scalafix to `project/plugins.sbt`:

```scala
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.7")
```

Configure SemanticDB and the rule dependency in `build.sbt`:

```scala
ThisBuild / scalacOptions += "-Ysemanticdb"

ThisBuild / scalafixDependencies +=
  "io.github.mercurievv" %% "scala-purrism-scalafix" % "0.9.0"
```

Create `.scalafix.conf`:

```hocon
rules = [
  TypelevelPurrism
]
```

Run:

```bash
sbt scalafix
```

### Mill

Add Mill Scalafix support and the rule dependency to `build.mill`:

```scala
//| mvnDeps:
//| - com.goyeau::mill-scalafix::0.6.0

import com.goyeau.mill.scalafix.ScalafixModule
import mill.*, scalalib.*

object app extends ScalaModule, ScalafixModule {
  def scalaVersion = "3.8.4"
  def scalacOptions = Seq("-Ysemanticdb")
  def scalafixIvyDeps = Seq(
    mvn"io.github.mercurievv::scala-purrism-scalafix:0.9.0"
  )
}
```

Create `.scalafix.conf`:

```hocon
rules = [
  TypelevelPurrism
]
```

Run:

```bash
./mill app.fix
```

### Scala CLI

Add directives:

```scala
//> using options "-Ysemanticdb"
//> using scalafix.dep io.github.mercurievv::scala-purrism-scalafix:0.9.0
```

Create `.scalafix.conf`:

```hocon
rules = [
  TypelevelPurrism
]
```

Run:

```bash
scala-cli fix . --power
```

### Scalafix CLI

Use the CLI when a rule needs a whole-project view or when your build tool runs
Scalafix module by module.

```bash
cs install scalafix

scalafix \
  --rules TypelevelPurrism \
  --tool-classpath "$(cs fetch -p io.github.mercurievv::scala-purrism-scalafix:0.9.0)" \
  --semanticdb-targetroots target/scala-3.8.4/classes \
  --sourceroot . \
  --files src/main/scala/example/App.scala
```

`--semanticdb-targetroots` must point at the directory containing
`META-INF/semanticdb`, usually the compiler output directory.

## Full rule catalogue (from the published rule reference site)

**Expression & Data Rules**
- `PreferCatsFunctions` — matches whole method bodies against an index of known Cats source functions
- `PreferCatsSyntax` — rewrites summoner calls like `Typeclass[F].method(fa)`
- `SimplifyCatsExpressions` — simplifies dot-syntax sub-expressions already in Cats form
- `PropagateOpaqueType` — introduces an opaque type and follows selected SemanticDB seed symbols through value flow
- `PreferOptionIdioms` — rewrites nullable lookups and `Option.map(...).getOrElse(...)` patterns
- `PreferIndexedMap` — converts index loops to direct collection operations

**Effect & Control Flow Rules**
- `TypeclassWeakening` — weakens over-strong effect bounds when the body only needs weaker Cats capabilities
- `SuspendSideEffects` — rewrites eager `pure(effect)` into suspended `delay(effect)`
- `PreferEffectIdioms` — rewrites decidable effect idioms and reports shapes needing wider decisions

**Arrow & Kleisli Rules**
- `PreferKleisli` — transforms effectful data-in/data-out methods into `Kleisli` values
- `PreferArrow` — rewrites hand-threaded `Kleisli` bodies into Arrow composition such as `>>>`, `.map`, and `&&&`

**Polymorphic Signature Rules**
- `PreferPolymorphicTypeclasses` — generalizes unary Cats type constructors to type parameters
- `PreferPolymorphicCollections` — widens `List`/`Seq`/`Vector` to polymorphic collection APIs
- `PreferPolymorphicCollectionOps` — widens collections and renames operations (e.g., `mkString` → `mkString_`)

**Architecture & Restrictions**
- `RestrictVocabulary` — diagnostic-only gate ensuring named types conform to an allow-list and banning specified constructs
