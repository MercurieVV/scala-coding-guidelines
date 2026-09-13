# Cats Source: `Kleisli.scala` — ArrowChoice/Category/Compose/Strong Instances

> Source: https://github.com/typelevel/cats (main branch, `core/src/main/scala/cats/data/Kleisli.scala`)
> Collected: 2026-09-13
> Published: Unknown

```scala
implicit val catsDataCommutativeArrowForKleisliId: CommutativeArrow[Kleisli[Id, *, *]] =
    catsDataCommutativeArrowForKleisli[Id]

implicit def catsDataCommutativeArrowForKleisli[F[_]](implicit
    M: CommutativeMonad[F]
): CommutativeArrow[Kleisli[F, *, *]] & ArrowChoice[Kleisli[F, *, *]] =
    new KleisliCommutativeArrow[F] { def F: CommutativeMonad[F] = M }

implicit def catsDataArrowChoiceForKleisli[F[_]](implicit M: Monad[F]): ArrowChoice[Kleisli[F, *, *]] =
    new KleisliArrowChoice[F] {
      def F: Monad[F] = M
    }

private[data] trait KleisliCommutativeArrow[F[_]]
    extends CommutativeArrow[Kleisli[F, *, *]]
    with KleisliArrowChoice[F] {
  implicit def F: CommutativeMonad[F]
}

private[data] trait KleisliArrowChoice[F[_]]
    extends ArrowChoice[Kleisli[F, *, *]]
    with KleisliCategory[F]
    with KleisliStrong[F] {
  implicit def F: Monad[F]

  def lift[A, B](f: A => B): Kleisli[F, A, B] =
    Kleisli(a => F.pure(f(a)))

  override def split[A, B, C, D](f: Kleisli[F, A, B], g: Kleisli[F, C, D]): Kleisli[F, (A, C), (B, D)] =
    Kleisli { case (a, c) => F.flatMap(f.run(a))(b => F.map(g.run(c))(d => (b, d))) }

  def choose[A, B, C, D](f: Kleisli[F, A, C])(g: Kleisli[F, B, D]): Kleisli[F, Either[A, B], Either[C, D]] =
    Kleisli((fe: Either[A, B]) =>
      fe match {
        case Left(a)  => F.map(f(a))(Left.apply _)
        case Right(b) => F.map(g(b))(Right.apply _)
      }
    )
}

private[data] trait KleisliStrong[F[_]] extends Strong[Kleisli[F, *, *]] {
  implicit def F: Functor[F]

  override def lmap[A, B, C](fab: Kleisli[F, A, B])(f: C => A): Kleisli[F, C, B] =
    fab.local(f)

  override def rmap[A, B, C](fab: Kleisli[F, A, B])(f: B => C): Kleisli[F, A, C] =
    fab.map(f)

  override def dimap[A, B, C, D](fab: Kleisli[F, A, B])(f: C => A)(g: B => D): Kleisli[F, C, D] =
    fab.dimap(f)(g)

  def first[A, B, C](fa: Kleisli[F, A, B]): Kleisli[F, (A, C), (B, C)] =
    fa.first[C]

  override def second[A, B, C](fa: Kleisli[F, A, B]): Kleisli[F, (C, A), (C, B)] =
    fa.second[C]
}

private[data] trait KleisliChoice[F[_]] extends Choice[Kleisli[F, *, *]] with KleisliCategory[F] {
  def choice[A, B, C](f: Kleisli[F, A, C], g: Kleisli[F, B, C]): Kleisli[F, Either[A, B], C] =
    Kleisli(_.fold(f.run, g.run))
}

private[data] trait KleisliCategory[F[_]] extends Category[Kleisli[F, *, *]] with KleisliCompose[F] {
  implicit def F: Monad[F]

  override def id[A]: Kleisli[F, A, A] = Kleisli.ask[F, A]
}

private[data] trait KleisliCompose[F[_]] extends Compose[Kleisli[F, *, *]] {
  implicit def F: FlatMap[F]

  def compose[A, B, C](f: Kleisli[F, B, C], g: Kleisli[F, A, B]): Kleisli[F, A, C] =
    f.compose(g)
}
```

Also, from earlier in the same file — `Kleisli`'s own `compose`/`first` methods that the `Strong`/`Compose` instances above delegate to:

```scala
def compose[Z, AA <: A](f: Z => F[AA])(implicit F: FlatMap[F]): Kleisli[F, Z, B] = ...
def compose[Z, AA <: A](k: Kleisli[F, Z, AA])(implicit F: FlatMap[F]): Kleisli[F, Z, B] = ...
def first[C](implicit F: Functor[F]): Kleisli[F, (A, C), (B, C)] = ...
```

Confirms precisely which typeclass instances a `Kleisli[F, *, *]` gets and under what constraint on `F`:

- `Compose`/`Category` (`compose`, `id`) need only `FlatMap[F]`/`Monad[F]` respectively.
- `Strong` (`first`, `second`, `lmap`, `rmap`, `dimap`) needs only `Functor[F]`.
- `ArrowChoice` (full `Arrow` + `split`/`choose`) needs `Monad[F]` — `split`'s definition (`F.flatMap(f.run(a))(b => F.map(g.run(c))(d => (b, d)))`) is exactly the same effect-sequencing a hand-written `for`-comprehension over two Kleislis would produce, left-to-right.
- `CommutativeArrow` additionally needs `CommutativeMonad[F]` — it is a claim about the effect `F` commuting, not license for `Arrow`'s own operations to reorder anything.
