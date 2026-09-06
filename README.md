# scala-coding-guidelines

## Quick start: wire this into another project

Run this in the target project's directory. It adds a link to this project's knowledge base
into both `CLAUDE.md` and `AGENTS.md`, so an LLM working in that repo consults it before
making Scala architecture decisions. Safe to re-run — idempotent, no duplicates.

```bash
curl -fsSL https://raw.githubusercontent.com/MercurieVV/scala-coding-guidelines/master/scripts/add-guidelines-link.sh | bash
```

If your shell doesn't show the prompts under the pipe form, use process substitution instead:

```bash
bash <(curl -fsSL https://raw.githubusercontent.com/MercurieVV/scala-coding-guidelines/master/scripts/add-guidelines-link.sh)
```

On a real terminal it's an arrow-key menu (↑/↓ or j/k, Enter to choose, Esc/q to cancel):

1. **GitHub** or **a local checkout you already have**?
2. If GitHub: **link to it over the internet**, or **clone it locally** (you pick the
   parent folder, arrow-key filesystem browser)?
3. If local: browse the filesystem for the existing checkout.

## What this repo is

Two unrelated parts:

1. A [scala-cli](https://scala-cli.virtuslab.org/) single-file project (`main.scala`,
   `project.scala`) — currently a placeholder.
2. A `karpathy-llm-wiki`-managed knowledge base (`raw/`, `wiki/`) — a compounding,
   LLM-maintained wiki of Scala 3 / Typelevel / cats-effect architecture research. This is
   the actual content of the repo.

The wiki currently covers tagless-final module boundaries, hand-wired `Resource`-based DI,
opaque types and other Scala 3 type-level features, recursion schemes and the droste
library, cats category-theory patterns (Functor/Monad laws, Free/Cofree, optics), and
project/testing conventions. Start at [`wiki/index.md`](wiki/index.md).

- `raw/` — immutable source material, one topic subdirectory deep. Never edited after
  being written.
- `wiki/` — compiled knowledge articles, one topic subdirectory deep. `wiki/index.md` is
  the global index; `wiki/log.md` is an append-only operation log.

Every number, date, and direct quote in `wiki/` is grounded in the `raw/` files it links to
— verified with `python3 ~/.claude/skills/karpathy-llm-wiki/scripts/check_evidence.py .`

## Scala project commands

```bash
scala-cli run .        # run main.scala
scala-cli compile .    # compile only
scala-cli test .       # run tests (none exist yet)
```
