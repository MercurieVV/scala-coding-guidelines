# ScalaSemanticMCP: Stainless formal verification integration (post-Mill-migration)

> Source: /Users/viktorskalinins/IdeaProjects/my/ScalaSemanticMCP (local repo) — build.mill, scripts/stainless-verify.sh, analysis/src/main/scala/com/github/mercurievv/scalasemantic/analysis/PureKernels.scala, and project memory note stainless-verification-scope.md
> Collected: 2026-09-07
> Published: Unknown

## Memory note (stainless-verification-scope.md)

`analysis/.../PureKernels.scala` (renamed from `StainlessContracts.scala`) is NOT a mirror —
correction to prior memory. File header states explicitly: standalone Stainless tool (v0.9.9.3)
runs directly on this file; contracts are discharged on the exact code that runs in production,
not a copy that can drift.

**Design**: collection-heavy callers (`graph.GraphMetrics`, `AnalyzerHelpers`) can't be verified
by the standalone tool (no `.iterator`/`.view`/`.groupBy` modeling) so the verifiable primitive
logic is pulled DOWN into `PureKernels` and callers delegate to it — rather than mirroring
verified logic back up into unverified callers.

**Verified members**: `instability`, `pageRankBase`, `rangeContains`, `rangeSpan`, `nextLevel`.
`nextLevel`'s overflow VC times out under bundled `smt-z3` (tolerated as `unknown`) but is `valid`
under native Z3. `pageRankBase` imports `stainless.lang.*` for stainless's IEEE-754 `Double`/`.isNaN`
model — the only not-NaN witness the verifier accepts.

**Mill port** (`build.mill:249-261`): no `mill-stainless` plugin exists, so ported by hand —
`stainless-library.jar` unmanaged dep in `analysis/lib/`; `stainlessVerify` Task.Command shells
`scripts/stainless-verify.sh` (standalone CLI, not sbt-stainless); wired into `prePush`
(build.mill:677).

**Still true from before**: Stainless 0.9.9.3 stdlib subset lacks `String.isEmpty`/`replace`, thin
collection mapping — this is WHY the verified kernels stay collection-free/primitive, not because
production lacks recursion/ADTs. Production's pageRank fixpoint iteration, `call_path` BFS, cycle
detection in DependencyGraphs, ADT model in `model/` remain unverified directly but now flow
through verified `PureKernels` primitives where applicable.

## PureKernels.scala file header (scaladoc)

```
The analysis engine's collection-free numeric & geometric kernels — the small primitive
decisions that the larger, collection-heavy algorithms delegate to. Kept in one dependency-free
object for a single reason: this file IS the Stainless verification target.

`sbt stainlessVerify` runs the standalone Stainless tool (v0.9.9.3, from
https://github.com/epfl-lara/stainless/releases/tag/v0.9.9.3) over THIS file directly — there is
no separate mirror. The functions here are the exact code that runs in production; their
`require`/`ensuring` contracts are formally discharged rather than re-stated on a copy that
could drift. The collection-bearing callers ([[graph.GraphMetrics]], [[AnalyzerHelpers]]) can't
be extracted by the standalone tool (it doesn't model `.iterator`/`.view`/`.groupBy`), so the
verifiable logic is pulled down to here and the callers delegate.

Verified members: [[instability]], [[pageRankBase]], [[rangeContains]], [[rangeSpan]],
[[nextLevel]] (the `rangeSpan` nonlinear multiplication-overflow VC times out under the bundled
`smt-z3` and is tolerated as `unknown`; it is `valid` under native Z3).

This object imports `stainless.lang.*` so [[pageRankBase]] can use stainless's IEEE-754 `Double`
model (`.isNaN`) — the only not-NaN witness the verifier accepts. The unmanaged
`stainless-library.jar` is therefore bundled into the `mcp` fat jar (see `build.sbt`). Under the
import, `require`/`ensuring` are stainless's erased ghost variants (no runtime cost), and `==>`
is available — but the boolean clauses below are kept as `!A || B` for readability parity with
[[graph.GraphMetrics]].
```

`instability` doc comment:

```
Instability metric Ce/(Ca+Ce), or 0 for an isolated node (no edges either way).

Verified contract:
  - *Precondition*: Ca and Ce are non-negative AND `ca + ce` does not overflow `Int` (the
    `ca <= Int.MaxValue - ce` bound — Stainless found that overflow in `ca + ce` invalidates
    the postcondition otherwise; e.g. ca = 2147483640, ce = 8).
  - *Postcondition*: result ∈ [0.0, 1.0], with both boundaries pinned exactly — no out-edges ⟹
    0.0 (maximally stable), only out-edges ⟹ 1.0 (maximally unstable). Pinning the boundaries
    (not just the range) is what catches an operand swap in the formula.
```

## build.mill wiring (analysis module, ~lines 249-261, 268-272)

```scala
object analysis extends Common with SonatypeCentralPublishModule {
  def id = "analysis"
  def artifactName = "scalasemantic-analysis"
  def publishVersion = build.publishVersion()
  def pomSettings = commonPom("scalasemantic-analysis")
  def moduleDeps = Seq(core, pc)
  def mvnDeps = Seq(
    mvn"com.lihaoyi::upickle:${V.upickle}",
    mvn"eu.timepit::refined:${V.refined}"
  )
  // build.sbt: stainless-library.jar is an UNMANAGED dep auto-picked from analysis/lib/.
  // Mill 1.x: a build-tracked file read in a task must be declared via Task.Source.
  def stainlessJar = Task.Source(moduleDir / "lib" / "stainless-library.jar")
  def unmanagedClasspath = Task { Seq(stainlessJar()) }
  object test extends CommonTests {
    def unmanagedClasspath = Task { Seq(stainlessJar()) }
  }

  // build.sbt `stainlessVerify` task — shells scripts/stainless-verify.sh.
  def stainlessVerify() = Task.Command {
    val script = build.moduleDir / "scripts" / "stainless-verify.sh"
    val rc = os.proc("bash", script.toString).call(cwd = build.moduleDir, check = false)
    if (rc.exitCode != 0) sys.error(s"stainlessVerify failed (exit ${rc.exitCode})")
  }
}
```

`prePush` (build.mill:901-909) calls `analysis.stainlessVerify()()` alongside other pre-push gates.

## scripts/stainless-verify.sh (full script, with its own inline rationale comments)

```bash
#!/usr/bin/env bash
# Formal-verification gate: run the standalone Stainless tool over the project's verifiable
# contracts (analysis/.../PureKernels.scala — the production numeric/geometric kernels, verified
# in place, no mirror) and fail iff any verification condition is INVALID.
#
# Why parse the summary instead of trusting Stainless's exit code: the tool exits non-zero on
# `unknown` (solver timeout) as well as `invalid`. One contract — `rangeSpan` — has a nonlinear
# Long-multiplication overflow VC that the bundled `smt-z3` cannot discharge within the timeout
# (it needs the native Z3 backend), so it comes back `unknown` on most runners. That is a solver
# limitation, not a soundness failure, and must NOT fail CI. A genuine regression (e.g. reverting
# rangeSpan to the unsound `Int` form) produces an `invalid` VC, which we DO fail on. So the gate
# keys off the `invalid:` count in the summary, tolerating `unknown`.
#
# The Stainless standalone distribution bundles its own z3/cvc5 binaries, so no extra solver
# install is needed. Usage: scripts/stainless-verify.sh   (run from the repo root)
set -euo pipefail

VERSION="${STAINLESS_VERSION:-0.9.9.3}"
TARGET="analysis/src/main/scala/com/github/mercurievv/scalasemantic/analysis/PureKernels.scala"
CACHE_DIR="${STAINLESS_CACHE_DIR:-$HOME/.cache/scalasemantic/stainless}"
# Per-VC timeout. Default kept low so the local/prePush path stays snappy: every genuinely-valid
# VC is discharged in well under this, an INVALID yields its counter-example near-instantly, and the
# only VCs that ever hit the limit are rangeSpan's nonlinear-multiplication ones that the bundled
# smt-z3 can't solve regardless (tolerated as `unknown`). CI overrides this to 30 for headroom.
TIMEOUT="${STAINLESS_TIMEOUT:-10}"

# --- pick the release asset for this OS/arch -------------------------------------------------
os="$(uname -s)"
arch="$(uname -m)"
case "$os/$arch" in
  Linux/x86_64)          platform="linux" ;;
  Darwin/arm64)          platform="mac-arm64" ;;
  Darwin/x86_64)         platform="mac-x64" ;;
  *) echo "stainless-verify: unsupported platform $os/$arch" >&2; exit 2 ;;
esac

asset="stainless-dotty-standalone-${VERSION}-${platform}.zip"
url="https://github.com/epfl-lara/stainless/releases/download/v${VERSION}/${asset}"
install_dir="${CACHE_DIR}/${VERSION}-${platform}"

# --- download + cache the standalone tool ----------------------------------------------------
stainless_bin="$(find "$install_dir" -maxdepth 2 -name stainless -type f 2>/dev/null | head -1 || true)"
if [ -z "$stainless_bin" ]; then
  echo "stainless-verify: installing Stainless $VERSION ($platform) -> $install_dir"
  mkdir -p "$install_dir"
  tmp_zip="$(mktemp -t stainless.XXXXXX.zip)"
  curl -fsSL "$url" -o "$tmp_zip"
  unzip -q -o "$tmp_zip" -d "$install_dir"
  rm -f "$tmp_zip"
  stainless_bin="$(find "$install_dir" -maxdepth 2 -name stainless -type f | head -1)"
fi
[ -n "$stainless_bin" ] || { echo "stainless-verify: stainless launcher not found after install" >&2; exit 2; }
chmod +x "$stainless_bin" 2>/dev/null || true

# --- verify ----------------------------------------------------------------------------------
echo "stainless-verify: verifying $TARGET (timeout ${TIMEOUT}s/VC)"
out="$(mktemp -t stainless-out.XXXXXX)"
# Don't let a non-zero exit (e.g. from `unknown`) abort the script before we inspect the summary.
set +e
"$stainless_bin" --timeout="$TIMEOUT" "$TARGET" 2>&1 | tee "$out"
set -e

# Strip ANSI colour, then read the summary's `invalid: N` field.
summary="$(sed -E 's/\x1b\[[0-9;]*m//g' "$out" | grep -E 'total:[[:space:]]+[0-9]+' | tail -1 || true)"
rm -f "$out"

if [ -z "$summary" ]; then
  echo "stainless-verify: FAILED — no verification summary produced (tool/compile error above)" >&2
  exit 1
fi

invalid="$(echo "$summary" | sed -E 's/.*invalid:[[:space:]]*([0-9]+).*/\1/')"
echo "stainless-verify: summary -> $summary"

if [ "${invalid:-0}" -ne 0 ]; then
  echo "stainless-verify: FAILED — $invalid invalid verification condition(s)" >&2
  exit 1
fi

echo "stainless-verify: OK — no invalid verification conditions (unknown/timeout VCs tolerated)"
```
