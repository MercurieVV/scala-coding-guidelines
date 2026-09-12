# Integrating Stainless verification into a Mill build

> Sources: ScalaSemanticMCP repo (local), 2026-09-07
> Raw: [ScalaSemanticMCP Stainless/Mill integration](../../raw/stainless-verification/2026-09-07-scalasemanticmcp-stainless-mill-integration.md); [Mill + Stainless formal verification — reusable integration recipe](../../raw/stainless-verification/2026-09-07-mill-stainless-verification-recipe.md)
> Updated: 2026-09-13

## Overview

Stainless (the EPFL formal-verification tool for Scala) has no official Mill plugin, only
`sbt-stainless`. ScalaSemanticMCP ported its Stainless gate to Mill by hand: an unmanaged jar
dependency, a `Task.Command` that shells out to a standalone-CLI wrapper script, and that command
wired into `prePush`. The pattern generalizes to any Mill project adopting Stainless without
waiting for first-party plugin support.

## Architecture: verify production code directly, not a mirror

The verified file is `PureKernels.scala` (renamed from `StainlessContracts.scala` specifically to
signal it is not a contracts mirror). The standalone Stainless tool runs directly against this
file, so its `require`/`ensuring` contracts are discharged against the exact code that ships to
production — there is no separate "verified copy" that can drift from the real implementation.

The standalone tool cannot model `.iterator`/`.view`/`.groupBy`, so collection-heavy call sites
(`graph.GraphMetrics`, `AnalyzerHelpers`) can't be verified directly. Rather than maintaining a
verified mirror that collection-heavy code doesn't actually run, the verifiable primitive logic
(`instability`, `pageRankBase`, `rangeContains`, `rangeSpan`, `nextLevel`) is pulled *down* into
the dependency-free `PureKernels` object, and the collection-heavy callers delegate to it. This
keeps unverified code (pageRank fixpoint iteration, BFS, cycle detection, ADT models) thin
wrappers around verified primitives, rather than duplicating verified logic back upward.

`PureKernels` imports `stainless.lang.*` so `pageRankBase` can use Stainless's IEEE-754 `Double`
model (`.isNaN`) — described as "the only not-NaN witness the verifier accepts." Under that
import, `require`/`ensuring` become Stainless's erased ghost variants (zero runtime cost).

## Two classes of Stainless imports

- `import stainless.annotation.pure` (also `.opaque`) — inert markers under plain scalac; they compile only because the jar is on the compile classpath. Used on many methods that are never run through the verifier (e.g. `Analyzer.scala`, `AnalyzerHelpers.scala`, `DuplicationAnalyzer.scala`, `InputTypes.scala`).
- `import stainless.lang.*` — the ghost `require`/`ensuring` variants and Stainless's IEEE-754 `Double` model. Used only in the verification target. Because this import appears in production code, the jar is *also* needed at runtime — Mill aggregates the unmanaged jar into the `mcp` fat jar (observed: the assembly `out/mcp/assembly.dest/out.jar` contains the stainless classes).

For `Double` arguments, use the IEEE-754 model directly: `pageRankBase(damping: Double, n: Int)` requires `!damping.isNaN && damping >= 0.0 && damping <= 1.0 && n > 0`. Without the NaN guard Stainless reports a `Comparison with NaN` counter-example (`damping = NaN`). A primitive `damping == damping` does **not** work as a substitute — Stainless must already know an operand is non-NaN to evaluate that comparison, so the guard would be circular; only `.isNaN` is accepted, which is why the target imports `stainless.lang.*`.

## Contract style example

`instability` (the `Ce/(Ca+Ce)` metric) illustrates two verification habits worth copying:

- **Precondition strengthened by the solver, not the author**: `ca + ce` must not overflow `Int`
  (`ca <= Int.MaxValue - ce`). Stainless itself surfaced the counterexample that forced this bound
  (`ca = 2147483640, ce = 8`) — the precondition wasn't anticipated up front.
- **Postcondition pins exact boundary values, not just a range**: result ∈ `[0.0, 1.0]`, with "no
  out-edges ⟹ 0.0" and "only out-edges ⟹ 1.0" both required as *exact* values. A mere range check
  would pass even with the metric's two operands swapped; pinning the boundaries is what catches
  that bug.

## Mill wiring

No `mill-stainless` plugin exists, so three things are hand-ported from the sbt equivalent:

1. **Unmanaged jar dependency.** `stainless-library.jar` lives in `analysis/lib/`. sbt picks up
   unmanaged jars from a lib directory automatically; Mill 1.x requires an explicit
   `Task.Source(moduleDir / "lib" / "stainless-library.jar")` wired into `unmanagedClasspath` —
   for both the main module and its `test` submodule.
2. **A `Task.Command` that shells the verify script**, rather than reimplementing verification
   logic in Scala:
   ```scala
   def stainlessVerify() = Task.Command {
     val script = build.moduleDir / "scripts" / "stainless-verify.sh"
     val rc = os.proc("bash", script.toString).call(cwd = build.moduleDir, check = false)
     if (rc.exitCode != 0) sys.error(s"stainlessVerify failed (exit ${rc.exitCode})")
   }
   ```
   `check = false` is deliberate: the command inspects `rc.exitCode` itself rather than letting
   `os.proc` throw, because the wrapper script's own exit code already encodes the pass/fail
   decision (see below) and Mill just needs to propagate it as a build failure.
3. **Wiring into `prePush`**: the `prePush` task calls `analysis.stainlessVerify()()` alongside the
   project's other pre-push gates, so a broken contract blocks push rather than surfacing later in
   CI.

Because `stainless.lang.*` is imported in production code, `stainless-library.jar` must also be
bundled into the final fat/assembly jar (the `mcp` module here), not just the build-time classpath.

## Step-by-step recipe

What ScalaSemanticMCP actually runs: Scala 3.8.4, Mill 1.1.7, Stainless **standalone** tool v0.9.9.3
(dotty frontend, not the `sbt-stainless` plugin).

1. **Obtain and commit the library jar.** `stainless-library.jar` is part of the standalone
   distribution: download `stainless-dotty-standalone-${VERSION}-${platform}.zip` from
   `https://github.com/epfl-lara/stainless/releases/download/v${VERSION}/...` and copy the jar out
   of its `lib/` directory. The jar checked into ScalaSemanticMCP at
   `analysis/lib/stainless-library.jar` is byte-identical to the distribution's
   `lib/stainless-library.jar` (sha256 `e1d309c7087bd8b4ba48e4d40fd007212340c61db2d09058fccb5bed8380e2c5`;
   770 classes, including `.tasty` files — a Scala 3 build). Commit it to
   `<verifiableModule>/lib/stainless-library.jar` (the sbt "unmanaged jar in lib/" convention, kept
   for Mill).
2. **Wire the jar into the Mill module** (`build.mill`) — see the `unmanagedClasspath` snippet
   above, for both the main module and its `test` submodule.
3. **Add the `stainlessVerify` command** — see the `Task.Command` snippet above.
4. **Write the wrapper script** `scripts/stainless-verify.sh` (full text below); the only edits
   needed to reuse it elsewhere are `VERSION`, `TARGET`, and the cache-dir default. Requires
   `bash`, `curl`, `unzip` on the runner (the downloaded distribution bundles its own `z3`/`cvc5`,
   so no solver install).
5. **Wire into `prePush` and CI.** `prePush` (`build.mill`) runs `analysis.stainlessVerify()()`
   after format/scalafix checks, golden tests, and the module test suites, so a broken contract
   blocks push rather than surfacing only in CI. CI gets a dedicated job, caching the tool so the
   ~80MB download happens once:

   ```yaml
   verify:
     name: Stainless verification
     runs-on: ubuntu-latest
     steps:
       - uses: actions/checkout@v4
       - uses: actions/setup-java@v4
         with: { distribution: temurin, java-version: '21' }
       - name: Cache Stainless tool
         uses: actions/cache@v4
         with:
           path: ~/.cache/scalasemantic/stainless
           key: stainless-0.9.9.3
       - name: Verify contracts
         run: ./mill analysis.stainlessVerify
         env:
           STAINLESS_TIMEOUT: '30'
   ```

   The cache `key` must match the version the script defaults to (it defaults to the same location
   the script writes to, so a cache hit skips the download+unzip entirely).
6. **Run and iterate.** `./mill <module>.stainlessVerify` — every genuinely valid VC discharges
   well under the default 10s; an INVALID yields its counter-example near-instantly. If a
   nonlinear-arithmetic overflow VC stays `unknown` under the bundled `smt-z3`, verify it once
   under native Z3 to confirm `valid`, document that, and leave the gate tolerating `unknown` for
   it. Env overrides: `STAINLESS_VERSION`, `STAINLESS_TIMEOUT`, `STAINLESS_CACHE_DIR`.

The full wrapper script:

```bash
#!/usr/bin/env bash
# Formal-verification gate: run the standalone Stainless tool over the project's verifiable
# contracts (the production file, verified in place, no mirror) and fail iff any verification
# condition is INVALID.
#
# Why parse the summary instead of trusting Stainless's exit code: the tool exits non-zero on
# `unknown` (solver timeout) as well as `invalid`. A nonlinear-arithmetic overflow VC (e.g. Long
# multiplication) can time out under the bundled `smt-z3` on most runners (it needs the native Z3
# backend) and comes back `unknown` — a solver limitation, not a soundness failure, and must NOT
# fail CI. A genuine regression produces an `invalid` VC, which we DO fail on. So the gate keys
# off the `invalid:` count in the summary, tolerating `unknown`.
#
# The Stainless standalone distribution bundles its own z3/cvc5 binaries, so no extra solver
# install is needed. Usage: scripts/stainless-verify.sh   (run from the repo root)
set -euo pipefail

VERSION="${STAINLESS_VERSION:-0.9.9.3}"
TARGET="<module>/src/main/scala/<pkg-path>/<PureKernels>.scala"   # EDIT
CACHE_DIR="${STAINLESS_CACHE_DIR:-$HOME/.cache/<project-name>/stainless}"  # EDIT
# Per-VC timeout. Default kept low so the local/prePush path stays snappy: every genuinely-valid
# VC is discharged in well under this, an INVALID yields its counter-example near-instantly, and the
# only VCs that ever hit the limit are nonlinear-multiplication ones the bundled smt-z3 can't solve
# regardless (tolerated as `unknown`). CI overrides this to 30 for headroom.
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

## Known issues & fixes (standalone-tool wrapper script)

These are the specific pitfalls the wrapper script (`scripts/stainless-verify.sh`) exists to work
around — relevant to anyone driving the *standalone* Stainless CLI (rather than the sbt plugin)
from any build tool:

- **Issue: exit code conflates "invalid" with "unknown."** Stainless exits non-zero both when a
  verification condition (VC) is genuinely `invalid` and when the solver merely times out
  (`unknown`). Trusting the exit code directly fails builds on solver limitations that are not
  regressions.
  **Fix:** parse the tool's printed summary instead of trusting its exit code — strip ANSI colour
  (`sed -E 's/\x1b\[[0-9;]*m//g'`), find the `total: N` summary line, extract the `invalid: N`
  count from it, and fail the build only when that count is nonzero. `unknown` VCs are tolerated
  by design.
- **Issue: nonlinear-arithmetic VCs time out on the bundled solver.** `rangeSpan`'s Long-
  multiplication overflow VC and `nextLevel`'s overflow VC both time out under the bundled
  `smt-z3` on most runners — they need the native Z3 backend to discharge in reasonable time.
  **Fix:** don't try to force these to pass in CI; document them as verified `valid` separately
  under native Z3, and rely on the invalid-count gate (above) to tolerate their `unknown` result
  in the standard pipeline.
- **Issue: standalone tool's stdlib subset is thin.** Stainless 0.9.9.3 lacks modeling for
  `String.isEmpty`/`.replace` and has thin collection support in general. This is *why* verified
  kernels are kept collection-free and primitive-only — not evidence that the production code
  lacks recursion or ADTs elsewhere (it doesn't; those parts just aren't verified directly, and
  flow through the verified primitives where applicable).
- **Issue: picking the right release asset per platform.** The standalone distribution ships
  separate archives per OS/arch.
  **Fix:** `case "$os/$arch"` mapping — `Linux/x86_64 → linux`, `Darwin/arm64 → mac-arm64`,
  `Darwin/x86_64 → mac-x64` — feeding an asset name of the form
  `stainless-dotty-standalone-${VERSION}-${platform}.zip`, downloaded from the GitHub releases URL
  for that version tag.
- **Issue: repeated downloads of a multi-hundred-MB tool.** Every fresh CI runner or clean
  checkout would otherwise re-download the standalone distribution.
  **Fix:** cache the extracted tool under
  `${STAINLESS_CACHE_DIR:-$HOME/.cache/scalasemantic/stainless}/${VERSION}-${platform}` and skip
  the download when a `stainless` binary is already found there.
- **Issue: one global timeout doesn't fit both local dev and CI.** Local runs should stay snappy;
  CI needs headroom for slower/shared runners.
  **Fix:** `STAINLESS_TIMEOUT` env var, defaulting to 10s per VC locally, overridden to 30s in CI.
- **Issue: the jar serves two roles, easy to only wire one.** Compile-time (annotations resolve;
  `@pure` markers are inert under plain scalac) and, when `stainless.lang.*` is imported in
  production code, runtime too (Mill must aggregate the unmanaged jar into the fat/assembly jar).
  **Fix:** verify the jar actually lands in the assembly output if your code imports
  `stainless.lang` — ScalaSemanticMCP confirmed this by inspecting
  `out/mcp/assembly.dest/out.jar`.
- **Issue: version/asset coupling drifts silently.** The release asset name is
  `stainless-dotty-standalone-${VERSION}-${platform}.zip` with only three platform suffixes
  (`linux`, `mac-arm64`, `mac-x64`).
  **Fix:** pin `VERSION` consistently across the script, the CI cache key, and the committed jar
  (verify the jar's sha256 against the distribution's `lib/stainless-library.jar` when bumping).
- **Issue: stale references to a prior build tool linger in comments.** ScalaSemanticMCP migrated
  sbt → Mill, yet scaladoc comments still said "`sbt stainlessVerify`" and "bundled ... (see
  `build.sbt`)".
  **Fix:** the real invocation is `./mill analysis.stainlessVerify` — treat leftover sbt-era
  comments as stale, not as an alternate invocation path.

## See Also

- [Project Structure & Testing](../scala-typelevel-fp/project-structure-and-testing.md)
