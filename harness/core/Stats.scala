package harness.core

/** Ports GA/report.py `wilson`, `sign_test` and `statistics.median`. */
object Stats:
  def wilson(k: Int, n: Int, z: Double = 1.96): (Double, Double) =
    if n == 0 then (0.0, 1.0)
    else
      val p = k.toDouble / n
      val d = 1 + z * z / n
      val c = (p + z * z / (2 * n)) / d
      val h = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / d
      (math.max(0.0, c - h), math.min(1.0, c + h))

  /** Two-sided exact binomial sign test, ties (0) dropped. */
  def signTest(diffs: Seq[Int]): Double =
    val nz = diffs.filter(_ != 0)
    val n = nz.length
    if n == 0 then 1.0
    else
      val k = math.min(nz.count(_ > 0), nz.count(_ < 0))
      val num = (0 to k).map(i => comb(n, i)).sum
      // Python int / int: the correctly rounded quotient (exact here, the divisor is a power of two)
      val tail = (BigDecimal(num) / BigDecimal(BigInt(2).pow(n))).toDouble
      math.min(1.0, 2 * tail)

  private def comb(n: Int, k: Int): BigInt =
    (1 to k).foldLeft(BigInt(1))((acc, i) => acc * (n - k + i) / i)

  /** `statistics.median`: the middle element of the sorted values, or the mean of the two middle ones. */
  def median(xs: Seq[Double]): Option[Double] =
    if xs.isEmpty then None
    else
      val s = xs.sorted(using Ordering.Double.TotalOrdering)
      val n = s.length
      Some(if n % 2 == 1 then s(n / 2) else (s(n / 2 - 1) + s(n / 2)) / 2)
