# Structural holdout seal — design

Date: 2026-10-02. Repositories: scala-llm-template (`kit/harness`, `kit/recipes/mill`), skreeep2 (first adopter).
Roadmap: item 1 follow-up (test protection and holdout), wiki `wiki/llm-scala-fusion/roadmap-more-scala-less-llm.md`.

## 1. Problem

The sealed holdout suite is protected only by tool-policy's text rules. Two live gated runs on 2026-10-02 showed
routes no text rule can see: a selector built at run time (`H=hold; H="${H}out"; ./mill "$H.test"`) and a Python
script calling `subprocess.call(["./mill", "hold" + "out.test"])`. Both ran the suite. Any rule that reads command text
loses to code that computes the command.

## 2. Goal and success criteria

An agent working in a gated repository cannot run the holdout suite, read its sources, read its compiled output, or
read its log — by any route its shell commands can take — while the gate keeps running the suite at turn-end and
reporting names only. The human's own terminal is unaffected.

Success, measured in skreeep2 by a live gated session after the change:

1. `./mill holdout.test`, the run-time-built selector, the Python `subprocess` route, and `cat holdout/...` all fail at
   the operating-system level (`Operation not permitted`), and no holdout test name or source line appears in the
   agent's output.
2. A Mill server started outside the sandbox — by the human's terminal (`./mill` in `out/`) or by the gate's hooks —
   is not reachable by the agent's Mill client.
3. Normal agent work runs sandboxed: `./mill <m>.compile` and `./mill <m>.test` for skreeep2 modules, with no
   unsandboxed retry available.
4. The gate's turn-end holdout run still reports `holdout: N passed, M failed` with names.
5. The added cost per turn-end and per edit is measured and stated; the target is under 10 s per turn-end on skreeep2.

## 3. Facts the design rests on (spike, 2026-10-02)

Claude Code 2.1.287, Mill 1.1.8, macOS; scratch clone of skreeep2; headless gated `claude -p` runs and `sandbox-exec`.

| Measured | Result |
|---|---|
| Sandbox on, strict, no other allowance | Mill cannot start: `SocketException: Operation not permitted` on the launcher's connect to its server |
| `network.allowLocalBinding` + `allowWrite` on `~/.cache/mill` and `~/Library/Caches/Coursier` | `version` 0.49 s, `core.compile` 0.95 s, `signals.test` 3 s |
| Mill server started outside the sandbox, agent's client sandboxed | **bypass**: `"$H.test"` ran, `+ the holdout suite runs under the gate`. The gate's own Stop hook left such a server running |
| Server started inside the sandbox, `denyRead` on `holdout/` | run-time selector and Python route fail: `…/holdout: Operation not permitted`; no test runs |
| `MILL_OUTPUT_DIR=out-agent` | honored: separate output directory and server; the server's port is in `<dir>/mill-daemon/socketPort` |
| Gate `--no-daemon` in the agent's directory while the agent's sandboxed server is alive | works, no new server: `signals.compile` 2 s; holdout first run 14 s, passes |
| `--no-daemon` cost outside the sandbox | `core.compile` 2 s (server path: 1–2 s) |
| Env switch for no-daemon | none: the launcher knows `MILL_ENABLE_STATIC_CHECKS`, `MILL_EXECUTABLE_PATH`, `MILL_SERVER_TIMEOUT_MILLIS`, `MILL_TEST_EXIT_AFTER_BSP_CHECK`, `MILL_WORKSPACE_ROOT`; `MILL_NO_SERVER` / `MILL_NO_DAEMON` still start a server |

Claude Code documentation (code.claude.com/docs/en/sandboxing): `denyRead` is enforced by the OS on shell commands
"and the processes they start"; hooks, MCP servers and the file tools run outside the sandbox; with
`allowUnsandboxedCommands: false` "Claude Code ignores the `dangerouslyDisableSandbox` parameter";
`failIfUnavailable: true` makes Claude Code exit rather than run commands unsandboxed when the sandbox cannot start;
the sandbox needs Seatbelt (macOS) or bubblewrap + socat (Linux/WSL2) and does not exist on native Windows.

## 4. Design

Four parts. The invariant they jointly hold: **every Mill server the agent's client can find runs inside the agent's
sandbox, and the sandbox cannot read the holdout.**

### 4.1 The agent's sandbox (human-owned settings)

The seed `kit/harness/seed/cc-settings.json` (copied by the human to `.claude/settings.json`) gains:

```json
"env": { "MILL_OUTPUT_DIR": "out-agent" },
"sandbox": {
  "enabled": true,
  "allowUnsandboxedCommands": false,
  "failIfUnavailable": true,
  "network": { "allowLocalBinding": true },
  "filesystem": {
    "allowWrite": ["~/.cache/mill", "~/.cache/coursier", "~/Library/Caches/Coursier"],
    "denyRead": ["./out/mill-daemon", "./out-agent/<holdout target>", "./.dsh/.state/holdout.log", "./<sealed source rows>"]
  }
}
```

- `denyRead` lists every `sealed` row of `.dsh/frozen-paths` (in skreeep2: `holdout`, `.dsh/.state/holdout.log`), the
  holdout module's compiled output in the agent's directory (`out-agent/holdout`), and the human's server directory
  `out/mill-daemon` — the agent cannot learn the port of a server it may not use.
- `env.MILL_OUTPUT_DIR=out-agent` puts the agent's builds and server in their own directory. An agent that overrides it
  (`MILL_OUTPUT_DIR=out ./mill …`) reaches `out/mill-daemon`, which it cannot read: its client fails rather than
  connecting.
- The values are a template: `install.sh` seeds them, filling `denyRead` from the sealed rows and the contract's
  holdout target. The settings file is already frozen, so the agent cannot loosen it.
- `out-agent/` is added to the seed `.gitignore` lines `install.sh` maintains.

### 4.2 The gate never leaves a Mill server behind

A new engine script, `kit/harness/mill-gate.sh`, runs the project's Mill (`resolve_mill`) with `--no-daemon` and
`MILL_OUTPUT_DIR` set to the agent's directory: the contract key `agentOutputDir` (new, optional, default `out-agent`), which check-harness requires to equal the settings file's `env.MILL_OUTPUT_DIR`. Every Mill
process the engine starts goes through it:

- direct calls (`$mill …` in post-edit, turn-end, module-map, health, feedback probes): `resolve_mill` returns the
  wrapper instead of `./mill`;
- contract commands the engine executes (`turnEndTests`, `fastTests`, `holdout`, `checkFormat`, `fix`, `coverage`):
  a leading `./mill` or `mill` word is replaced by the wrapper before `sh -c` runs the command
  (`dsh_gate_command <cmd>`); a command whose first word is anything else is run as written, and check-harness notes it
  (the engine cannot vouch for a server that command may start).

Sharing the agent's directory keeps one compile cache: the gate checks exactly what the agent built. `--no-daemon`
measured at 2 s against 1–2 s for `core.compile`.

### 4.3 The holdout run

Unchanged in what it reports. It runs through 4.2 (no server, agent's directory), outside the sandbox (hooks are), so it
reads `holdout/` normally; its compiled output lands in `out-agent/holdout`, which the agent cannot read. The time cap
(`DSH_HOLDOUT_CAP`) is unchanged.

### 4.4 Checks and reporting

- `check-harness.sh` (new group): `.claude/settings.json` exists and has `sandbox.enabled: true`,
  `allowUnsandboxedCommands: false`, `failIfUnavailable: true`, `env.MILL_OUTPUT_DIR`, and a `denyRead` entry for every
  sealed row, `out/mill-daemon`, and `<dir>/<holdout target>`. Missing or weaker → FAIL with the exact line to add.
  A contract command that starts Mill other than through `./mill`/`mill` → note.
- `health` (SessionStart): a named degradation line when the settings file lacks the sandbox block, and on a platform
  with no sandbox (native Windows; Linux without `bwrap`) — "the holdout seal is text-only in this session".
- tool-policy's text rules stay: they refuse the plain forms earlier, with a message, before the OS does.

## 5. Out of scope, and the residuals stated

- The human's terminal server in `out/` stays reachable through `localhost` by an agent that guesses its port and
  speaks Mill's protocol; `socketPort` is unreadable to it. Documented, not solved.
- Claude Code's file tools are not sandboxed: semantic-guard's sealed-path refusal stays their layer (unchanged).
- MCP servers run outside the sandbox: an MCP tool that runs Mill (none in skreeep2's set) would reopen the bypass;
  check-harness notes MCP servers named in the settings.
- Adding a new library dependency inside the sandbox: the JVM ignores the proxy variables, so Coursier downloads may
  fail sandboxed. To be measured in the plan; if they fail, the documented remedy is the human running
  `MILL_OUTPUT_DIR=out-agent ./mill --no-daemon <m>.compile` once in a terminal.
- Native Windows: no sandbox; the seal stays text-only and health says so.
- Seen in the spike, separate: when Mill's launcher cannot start, the feedback wrapper renders it as `UNREADABLE` rather
  than an INFRA record naming the cause. Fixed alongside only if it is one line; otherwise ledgered.

## 6. Testing

- Fixture cases (sh and dash): `dsh_gate_command` rewriting (`./mill`, `mill`, other first words, leading
  assignments); `resolve_mill` returning the wrapper; the wrapper passing `--no-daemon` and `MILL_OUTPUT_DIR`; turn-end
  and post-edit Mill calls going through it (stub Mill records its arguments and environment); check-harness rows for
  each missing or weak settings key; health lines.
- Live, skreeep2 clone with the new engine and settings: success criteria 1–5, by a gated headless session and a
  `pgrep` for servers after a turn ends (expected: only the agent's sandboxed server).
- Cost: turn-end and post-edit wall time before/after on skreeep2.

## 7. Rollout

scala-llm-template first (engine, seed, recipes doc), then skreeep2: re-vendor, the human copies the new settings block
into `.claude/settings.json` (a human step — the file is frozen), first agent build populates `out-agent/`.

## 8. Amendments after the final review (2026-10-02)

The whole-branch review found the design incomplete in six places; each is now part of the design.

1. **Writes, not only reads.** The sandbox allows writes in the working directory by default, so a sandboxed interpreter
   could replace `.dsh/engine/mill-gate.sh`, the contract's `holdout =` line or `.dsh/frozen-paths`, and the next hook —
   unsandboxed — would run it. The settings add `sandbox.filesystem.denyWrite` on `./.dsh/engine`, `./.dsh/contract`,
   `./.dsh/frozen-paths`, `./.claude` and every sealed row; check-harness FAILs per missing entry.
2. **Settings that switch the seal off FAIL.** A non-empty `sandbox.excludedCommands` (those commands run unsandboxed),
   `enableWeakerNestedSandbox: true`, and a `.claude/settings.local.json` that overrides any required key with a weaker
   value. health reads the local override for `sandbox.enabled`.
3. **One Mill per contract command.** A command that runs Mill after a separator (`./mill a && ./mill holdout.test`) can be
   routed only at its first Mill; check-harness FAILs it (`dsh_gate_command` exit 2).
4. **The holdout's output path** is the selector's module segments without the task (`engine.holdout.test` →
   `out-agent/engine/holdout`), not its first segment.
5. **No holdout, no change.** Without a `holdout =` line and without `agentOutputDir`, the agent directory defaults to `out`,
   so projects that need no seal keep one cache with the human's builds.
6. **MCP servers** named in `.mcp.json` or the settings get a check-harness note (they run outside the sandbox).
   `install.sh` does NOT generate `denyRead`/`denyWrite` from the sealed rows (§4.1 said it would): the seed is static for
   the conventional `holdout` module, and check-harness names every exact entry a project with another layout must add —
   the same result without editing JSON in POSIX sh.

## 9. Addendum: the gate's own Mill runs confined (2026-10-02, approved)

### 9.1 Why

Live verification of §4 (scratch clone of skreeep2, one-line `core` edit) measured the cost of `--no-daemon` on every
gate Mill call: turn-end warm 43–45 s → 71–73 s, post-edit 4–5 s → 15 s — success criterion 5 (<10 s added) failed.
The final review also found the gate runs agent-writable code unsandboxed (`build.mill`, and the agent's production code
the tests execute), which can copy `holdout/` somewhere the agent reads. Both come from the same fact: the gate's Mill is
unconfined.

### 9.2 Measured (macOS, `sandbox-exec`)

| Setting | turn-end warm | post-edit | holdout run |
|---|---|---|---|
| old engine (unconfined server) | 43–45 s | 4–5 s | — |
| §4 (`--no-daemon` everywhere) | 71–73 s | 15 s | 7 s (out-agent, unconfined) |
| gate calls under a holdout-read-denying profile, server allowed | 48–51 s | 5 s | — |
| holdout run confined (own output dir, no network, writes only there + Coursier `*.lock`) | — | — | 28 s first, then 3 s |

An unsandboxed client joining the gate's confined server for `holdout.compile` got `…/holdout: Operation not
permitted`. A process under the holdout-run profile could not write `/tmp`, the repository or `~/.cache/mill`, and had no
network (`curl` rc 7); allowing only Coursier `*.lock` writes (Coursier needs its cache lock) kept `holdout.test` passing.

### 9.3 Design

- **Gate calls (everything except the holdout run).** On macOS `mill-gate.sh` runs Mill under
  `sandbox-exec -p <gate profile>` and WITHOUT `--no-daemon`: any server it starts or joins is confined, so a server it
  leaves behind is harmless to join. Gate profile: `(allow default)`; deny `file-read*` on every sealed row,
  `<agent dir>/<holdout out path>`, `out-holdout`, `.dsh/.state/holdout.log`, `out/mill-daemon`; deny `file-write*` on
  `.dsh/engine`, `.dsh/contract`, `.dsh/frozen-paths`, every sealed row, `out-holdout`. Paths are absolute and physical
  (`pwd -P`). Without `sandbox-exec` (Linux, others) the gate keeps §4's `--no-daemon` — unconfined, slow, still no
  server left behind; Linux `bwrap` is future work, unmeasured.
- **The holdout run.** Its own output directory `out-holdout` (contract key `holdoutOutputDir`, same validation as
  `agentOutputDir`), `--no-daemon`, the JVM temp directory inside it (`_JAVA_OPTIONS=-Djava.io.tmpdir=<dir>/tmp`), and
  on macOS the holdout profile: `(allow default)`, `(deny network*)` except local unix sockets, `(deny file-write*)`
  except `out-holdout`, Coursier `*.lock` files, `/dev/null`, `/dev/tty`, `/dev/fd/*`. It may read `holdout/`; nothing it
  runs can write where the agent reads, except the residual below. Without `sandbox-exec`: unconfined, as §4.
- **Agent settings.** `denyRead` and `denyWrite` gain `./out-holdout`; check-harness requires both.
- **health** notes when the gate's Mill cannot be confined on this platform ("gate Mill runs unconfined and without a
  server").

### 9.4 Residuals

- The holdout run can still write Coursier `*.lock` files the agent can read — a deliberate encoding channel for code the
  holdout tests execute. Narrow; documented.
- No confinement of the gate's Mill outside macOS until `bwrap` is measured.
- The human's terminal server and file tools/MCP, as §5.

## 10. Revision: the holdout leaves the repository (2026-10-02, approved — supersedes §9)

### 10.1 Why §9 is withdrawn

The phase-2 whole-branch review (real `sandbox-exec` probes) showed §9's `(allow default)` profiles escapable: code inside
either profile launched an unconfined process through LaunchServices (`open`) and copied `holdout/` to an
agent-readable file, and the gate profile let gate-run code write `.claude/settings.local.json`, `.git/config` (a planted
`core.fsmonitor` ran in the gate's unsandboxed `git status`) and `$HOME`. Its left-behind server was also a sandbox
downgrade for the agent that joined it. §9's code is kept under the git tag `phase2-confined-gate` in scala-llm-template;
the branch returns to §4/§8 (the gate runs `--no-daemon`, measured +28 s per turn-end). Building deny-default profiles in
the engine is not pursued.

The same review found a seal-wide bypass independent of §9: a git-tracked `holdout/` is readable from `.git/objects`
(`git cat-file -p <sha>` passes the text rules, and the sandbox's `denyRead ./holdout` does not cover `.git`). Measured in
skreeep2: `holdout/test/src/com/github/mercurievv/skreeep2/holdout/SmokeHoldout.scala` is tracked.

### 10.2 Design

1. **The holdout lives outside the repository.** New required contract key `holdoutDir` (when a `holdout =` line exists):
   an absolute path, or one starting with `../`, resolved physically (`pwd -P`); it must exist and must not be inside the
   repository root. The holdout module's sources come from it only when the gate's holdout run sets
   `DSH_HOLDOUT_DIR=<holdoutDir>`; otherwise the module has no sources (the agent's own builds and the gate's checks see
   an empty holdout). The Mill recipe provides this as a `HoldoutModule` trait in `Gate.mill`.
2. **The agent's sandbox denies it.** `sandbox.filesystem.denyRead` and `denyWrite` include the absolute `holdoutDir`;
   check-harness FAILs when either is missing.
3. **Nothing of it inside the repository.** check-harness FAILs while `holdoutDir` is inside the root, missing, or while
   any file under a `sealed` row of `.dsh/frozen-paths` is tracked by git (`git ls-files`). Content that was ever
   committed stays in history: the adopter writes NEW holdout tests in `holdoutDir` (the old ones are readable from
   `.git` and no longer sealed); the README says so.
4. **The holdout report carries only real test names.** `dsh_weaver_failures`' names are kept only when the test-name text
   occurs verbatim in a file under `holdoutDir` (the hook reads it unsandboxed); at most 20 names, each at most 120
   characters. Printed totals are not trusted: the report states the run's exit status and the count of verified failing
   names, never a passed count taken from the run's output.
5. **Seams are not configuration.** check-harness FAILs on any `env.DSH_*` key in `.claude/settings.json` or
   `.claude/settings.local.json` (settings `env` reaches hooks).
6. **Ruling 20.** A `holdout =` command `dsh_gate_command` cannot route (first word not `./mill`/`mill`) FAILs check-harness.
7. **Docs.** README states what holds and what does not (below); the phase-1 claims stay true as written.

### 10.3 Residuals

- Code the gate runs unsandboxed — `build.mill`, and the agent's production code executed by the holdout run — can read
  `holdoutDir` if it knows the path (the path is in the contract, which the agent can read); the holdout run's output
  reaches the agent only as verified names (§10.2.4). Closing this needs the gate's Mill confined by a deny-default OS
  profile, which this design does not build.
- Holdout content committed before the move remains in git history.
- §5's residuals (the human's terminal server by port, file tools and MCP outside the sandbox, native Windows).

### 10.4 Success criteria (replace §2's for the seal)

1. In skreeep2 after migration: `git ls-files` lists no holdout file; check-harness passes with `holdoutDir` set and the
   settings denying it.
2. A gated agent session cannot read a file under `holdoutDir` (`Operation not permitted`), cannot run the holdout suite,
   and `git log -p`/`git cat-file` on the repository reveal no new holdout test.
3. The gate's turn-end still reports the holdout: exit status and verified failing names only.
4. A holdout-run line `- <arbitrary text>` that is not a test name in `holdoutDir` does not reach the agent.
