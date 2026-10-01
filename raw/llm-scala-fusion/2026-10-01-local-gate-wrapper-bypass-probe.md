# Local probe: gate rules bypassed through command wrappers

> Source: local measurement, this repository's owner — the harness engine's tool-policy hook (`kit/harness/hooks/tool-policy.sh`, vendored as `.dsh/engine/`) run against PreToolUse payloads in skreeep2, before and after scala-llm-template 3b63334 (skreeep2 4009fc0)
> Collected: 2026-10-01
> Published: 2026-10-01

Measurement record. Exit code 2 = denied, 0 = allowed. Outputs copied from the commands as run.

## Before the fix (skreeep2 master 477e2d7)

```
rc=2 :: ./mill holdout.test
rc=0 :: env FOO=1 ./mill holdout.test
rc=0 :: timeout 600 ./mill holdout.test
rc=0 :: nice -n 5 ./mill holdout.test
rc=0 :: time -p ./mill holdout.test
rc=0 :: eval "./mill holdout.test"
rc=0 :: sh -c "./mill holdout.test"
rc=2 :: command ./mill holdout.test
rc=2 :: xargs ./mill <<< holdout.test
rc=2 :: sbt test
rc=2 :: env sbt test
rc=0 :: timeout 5 sbt test
```

Cause: `command_position_words` skipped a wrapper (`sudo`, `env`, `time`, `nohup`, `command`, `exec`, `xargs`) only
as a bare word, so the wrapper's first option or assignment was read as the command; `timeout`, `nice`, `eval` and a
shell's `-c` text were not looked through at all. The engine README listed `sudo -u x …`, `env -i …`, `nice -n 5 …`,
`time -p …` and `xargs -I{} …` as ALLOWED for the interpreter text rule too.

## After the fix (skreeep2 master 4009fc0)

```
rc=2 :: ./mill holdout.test
rc=2 :: env FOO=1 ./mill holdout.test
rc=2 :: timeout 600 ./mill holdout.test
rc=2 :: nice -n 5 ./mill holdout.test
rc=2 :: time -p ./mill holdout.test
rc=2 :: eval "./mill holdout.test"
rc=2 :: sh -c "./mill holdout.test"
rc=2 :: timeout 5 sbt test
rc=0 :: timeout 5 ./mill resolve _
rc=0 :: env FOO=1 ls
```

Interpreter text rule against a frozen target (`engine/test/A.scala`), after the fix:

```
rc=2 :: sudo -u x python3 -c "open('engine/test/A.scala','w')"
rc=2 :: env -i python3 -c "open('engine/test/A.scala','w')"
rc=2 :: nice -n5 perl -e 'unlink q(engine/test/A.scala)'
rc=2 :: time -p sh -c 'rm engine/test/A.scala'
rc=2 :: xargs -I{} python3 -c "open('engine/test/A.scala','w')"
```

## The fix

The parser skips each wrapper with its own options (`env -i`/`-u NAME`/`NAME=value`, `timeout -s/-k DURATION`,
`nice -n`, `time -p`, `sudo -u`, `stdbuf`, `exec -a`, `ionice`, `xargs`, `busybox <applet>`, chains, a leading
`NAME=value`). The code given to `eval` / `<shell> -c` is appended to the line and judged by every rule, up to three
nested levels; a command word computed at run time inside it (`sh -c "$CMD"`, `eval "$(cat run.sh)"`, a backquote)
is refused. 29 new fixture cases (23 denied, 6 allowed controls), written failing first: 22 failed before the fix.

Suites after the fix: template `run.sh` 475 passed, 0 failed under both sh and dash; skreeep2 `run.sh` 475 passed,
0 failed; skreeep2 check-harness 80 passed, 0 failed.

Still outside the text layer: a shell alias or function, and a script file an interpreter runs.
