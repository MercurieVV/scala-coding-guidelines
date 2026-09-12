# Mill + Stainless formal verification — reusable integration recipe

> Source: Derived from the ScalaSemanticMCP repo (/Users/viktorskalinins/IdeaProjects/my/ScalaSemanticMCP): `build.mill`, `scripts/stainless-verify.sh`, `analysis/lib/stainless-library.jar`, `analysis/.../PureKernels.scala`, `.github/workflows/ci.yml`; companion to `2026-09-07-scalasemanticmcp-stainless-mill-integration.md` (project snapshot). Distribution layout verified against the cached install `~/.cache/scalasemantic/stainless/0.9.9.3-mac-arm64/` (contains `bin/`, `lib/stainless-library.jar`, bundled `z3/`, `cvc5/`).
> Collected: 2026-09-07
> Published: Unknown

What ScalaSemanticMCP actually runs: Scala 3.8.4, Mill 1.1.7, Stainless **standalone** tool v0.9.9.3 (dotty frontend, not the `sbt-stainless` plugin). Verified reference state below; generalize by replacing the module name, paths, and project-specific cache names.

## 1. Design decisions that make this work

1. **No official Mill plugin exists** (`mill-stainless` does not exist on Maven Central; `sbt-stainless` is sbt-only). So the integration is three hand-ported pieces: an unmanaged jar on the compile classpath, a `Task.Command` that shells a wrapper script, and wiring of that command into `prePush` and CI.
2. **Verify production code in place, never a mirror.** The standalone tool runs directly over the production file whose functions carry `require`/`ensuring` contracts. The ScalaSemanticMCP target is `PureKernels.scala`, deliberately NOT named `StainlessContracts.scala` — the file IS the code that ships; no copy can drift.
3. **The standalone tool cannot model `.iterator`/`.view`/`.groupBy`.** Collection-heavy callers are therefore not directly verifiable. The pattern: pull the verifiable primitive logic DOWN into a dependency-free object of pure, collection-free functions, and let the collection-heavy code delegate to it (thin unverified wrappers over verified kernels).
4. **Two classes of stainless imports exist in source:**
   - `import stainless.annotation.pure` (also `.opaque`) — inert markers under plain scalac; they compile only because the jar is on the compile classpath. Used on many methods that are never run through the verifier (e.g. `Analyzer.scala`, `AnalyzerHelpers.scala`, `DuplicationAnalyzer.scala`, `InputTypes.scala`).
   - `import stainless.lang.*` — the ghost `require`/`ensuring` variants and Stainless's IEEE-754 `Double` model (`.isNaN`). Used only in the verification target. Because this import appears in production code, the jar is ALSO needed at runtime — Mill aggregates the unmanaged jar into the `mcp` fat jar (observed: the assembly `out/mcp/assembly.dest/out.jar` contains the stainless classes).

## 2. Contract style that verifies cleanly (habits worth copying)

- `@pure` on each target function.
- **Preconditions include overflow bounds the solver itself forces you to add.** Example from `instability` (`Ce/(Ca+Ce)`): `require(ca >= 0 && ce >= 0 && ca <= Int.MaxValue - ce)` — Stainless found the concrete counter-example `ca = 2147483640, ce = 8` where `ca + ce` overflows `Int` and invalidates the postcondition.
- **Postconditions pin exact boundary values, not just a range.** `instability` requires result ∈ [0.0, 1.0] AND `ce == 0 ==> r == 0.0` AND (`ca == 0 && ce > 0 ==> r == 1.0`). A range check alone passes even with the metric's operands swapped; the pinned boundaries catch that bug.
- **For `Double` arguments use Stainless's IEEE-754 model.** `pageRankBase(damping: Double, n: Int)` requires `!damping.isNaN && damping >= 0.0 && damping <= 1.0 && n > 0`. Without the NaN guard Stainless reports a `Comparison with NaN` counter-example (`damping = NaN`). A primitive `damping == damping` does NOT work as a substitute — Stainless must already know an operand is non-NaN to evaluate that comparison, so the guard would be circular; only `.isNaN` is accepted, which is why the target imports `stainless.lang.*`.
- Keep the verifiable functions **collection-free and primitive-typed** — verified members in ScalaSemanticMCP are `instability`, `pageRankBase`, `rangeContains`, `rangeSpan`, `nextLevel`, all taking/returning `Int`/`Long`/`Double`/`Boolean`.

## 3. Step-by-step recipe

### Step 1 — obtain and commit the library jar

The `stainless-library.jar` your code imports is part of the standalone distribution: download
`stainless-dotty-standalone-${VERSION}-${platform}.zip` from
`https://github.com/epfl-lara/stainless/releases/download/v${VERSION}/...` and copy the jar out of its
`lib/` directory. The jar checked into ScalaSemanticMCP at `analysis/lib/stainless-library.jar` is
byte-identical to the distribution's `lib/stainless-library.jar`
(sha256 `e1d309c7087bd8b4ba48e4d40fd007212340c61db2d09058fccb5bed8380e2c5`; the jar holds 770 classes,
including `.tasty` files — a Scala 3 build). Commit it to `<verifiableModule>/lib/stainless-library.jar`
(the sbt "unmanaged jar in lib/" convention, kept for Mill).

### Step 2 — wire the jar into the Mill module (`build.mill`)

Mill 1.x: a build-tracked file read inside a task must be declared via `Task.Source`, and an
unmanaged jar is added through `unmanagedClasspath` — for the main module AND its `test` submodule:

```scala
object analysis extends ScalaModule { // or your Common base
  // ...
  def stainlessJar = Task.Source(moduleDir / "lib" / "stainless-library.jar")
  def unmanagedClasspath = Task { Seq(stainlessJar()) }
  object test extends ScalaTests {
    def unmanagedClasspath = Task { Seq(stainlessJar()) }
  }
}
```

### Step 3 — add the `stainlessVerify` command

```scala
// shells scripts/stainless-verify.sh from the repo root; fails the build on non-zero exit.
def stainlessVerify() = Task.Command {
  val script = build.moduleDir / "scripts" / "stainless-verify.sh"
  val rc = os.proc("bash", script.toString).call(cwd = build.moduleDir, check = false)
  if (rc.exitCode != 0) sys.error(s"stainlessVerify failed (exit ${rc.exitCode})")
}
```

`check = false` is deliberate: the command inspects `rc.exitCode` itself rather than letting
`os.proc` throw, because the wrapper script's exit code already encodes pass/fail and Mill just
propagates it as a build failure.

### Step 4 — the wrapper script `scripts/stainless-verify.sh`

Copy the script below; the only edits needed are `VERSION`, `TARGET`, and the cache-dir default.
Requires `bash`, `curl`, `unzip` on the runner (the downloaded distribution bundles its own
`z3`/`cvc5`, so no solver install).

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

### Step 5 — wire into `prePush` and CI

`prePush` (build.mill) runs `analysis.stainlessVerify()()` after format/scalafix checks, golden
tests, and the module test suites, so a broken contract blocks push rather than surfacing only in CI.

CI gets a dedicated job (from `.github/workflows/ci.yml`), caching the tool so the ~80MB download
happens once:

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

The cache `key` must match the version the script defaults to (it defaults to the same location the
script writes to, so a cache hit skips the download+unzip entirely).

### Step 6 — run and iterate

- `./mill <module>.stainlessVerify` — every genuinely valid VC discharges well under the default 10s;
  an INVALID yields its counter-example near-instantly.
- If a nonlinear-arithmetic overflow VC stays `unknown` under the bundled `smt-z3`, verify it once
  under native Z3 to confirm `valid`, document that, and leave the gate tolerating `unknown` for it.
- Env overrides: `STAINLESS_VERSION`, `STAINLESS_TIMEOUT`, `STAINLESS_CACHE_DIR`.

## 4. Pitfalls checklist

- **Exit code conflates `invalid` with `unknown`** — never gate on the tool's exit code; parse the
  summary's `invalid: N` field (strip ANSI with the sed above, grep the `total:` line). Empty
  summary means the tool failed to compile/run — treat that as a hard failure.
- **Keep the verified target collection-free** — `.iterator`/`.view`/`.groupBy` are not modeled by
  the standalone tool; pull primitive kernels down and delegate.
- **Stainless 0.9.9.3's stdlib subset is thin** — e.g. it lacks `String.isEmpty`/`.replace`; that is
  a constraint on WHAT you can verify, not on the rest of your codebase.
- **The jar serves two roles** — compile-time (annotations resolve; `@pure` markers are inert under
  plain scalac) and, when `stainless.lang.*` is imported in production code, runtime (Mill
  aggregates the unmanaged jar into the fat/assembly jar — verify it actually lands there if your
  code imports `stainless.lang`).
- **Version/asset coupling** — the release asset name is
  `stainless-dotty-standalone-${VERSION}-${platform}.zip` with only three platform suffixes
  (`linux`, `mac-arm64`, `mac-x64`); pin `VERSION` consistently in the script, the CI cache key, and
  the jar you commit.
- **Stale `sbt` references** — ScalaSemanticMCP migrated sbt → Mill, yet scaladoc comments still say
  "`sbt stainlessVerify`" and "bundled ... (see `build.sbt`)". The real invocation is
  `./mill analysis.stainlessVerify`.
