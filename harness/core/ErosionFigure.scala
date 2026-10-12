package harness.core

import cats.effect.IO

/** GA erosion.py `aggregate`: method count, total mass (CC * sqrt(SLOC), rounded to 3) and the eroded share (rounded to 4). */
final case class ErosionFigure(erosion: Double, methods: Int, mass: Double):
  /** erosion.py's `json.dumps(aggregate(...))`, the object the chain reference line embeds. */
  def pyJson: String = s"""{"methods": $methods, "total_mass": ${Json.pyFloat(mass)}, "erosion": ${Json.pyFloat(erosion)}}"""

/** The erosion figure of the main source files of `tree` that differ from the tree at `base` (GA `erosion.py <tree> <head>`). */
trait ErosionMeasure:
  def measure(tree: os.Path, base: String): IO[Either[String, ErosionFigure]]
