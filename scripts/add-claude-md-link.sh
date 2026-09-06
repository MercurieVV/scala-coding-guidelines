#!/usr/bin/env bash
# Idempotently adds a link to the scala-coding-guidelines knowledge base into
# ./CLAUDE.md in the current working directory. Interactive: pick GitHub link
# or browse the local filesystem for a checkout of the project.
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/MercurieVV/scala-coding-guidelines/master/scripts/add-claude-md-link.sh | bash
#
# Safe to re-run: it replaces its own marked block instead of duplicating it.
set -uo pipefail

REPO_URL="https://github.com/MercurieVV/scala-coding-guidelines"
WIKI_INDEX_REL="wiki/index.md"
TARGET_FILE="CLAUDE.md"
MARKER_START="<!-- scala-coding-guidelines:start -->"
MARKER_END="<!-- scala-coding-guidelines:end -->"

prompt_read() {
  local msg="$1"; shift
  if exec 3<>/dev/tty 2>/dev/null; then
    printf '%s' "$msg" >&3
    IFS= read -r "$@" <&3
    exec 3<&-
  else
    printf '%s' "$msg"
    IFS= read -r "$@"
  fi
}

echo "scala-coding-guidelines link setup -> $TARGET_FILE (in $(pwd))"
echo "Where does the project live?"
echo "  1) GitHub (link to the hosted repo)"
echo "  2) Local checkout (browse the filesystem for it)"
prompt_read "Choose [1/2]: " choice

link=""
case "$choice" in
  1)
    link="$REPO_URL/blob/master/$WIKI_INDEX_REL"
    ;;
  2)
    dir="$HOME"
    while true; do
      echo
      echo "Current: $dir"
      subdirs=()
      while IFS= read -r d; do
        [ -n "$d" ] && subdirs+=("$d")
      done < <(cd "$dir" 2>/dev/null && ls -d */ 2>/dev/null | sed 's#/$##' | sort)
      n=0
      for d in "${subdirs[@]}"; do
        n=$((n+1))
        echo "  $n) $d"
      done
      echo "  s) select this directory as the project"
      echo "  u) go up one level"
      echo "  p) type a path directly"
      prompt_read "> " sel
      case "$sel" in
        s|S) break ;;
        u|U) dir=$(dirname "$dir") ;;
        p|P)
          prompt_read "path: " typed
          [ -d "$typed" ] && dir="$typed" || echo "not a directory: $typed"
          ;;
        ''|*[!0-9]*) echo "invalid choice" ;;
        *)
          if [ "$sel" -ge 1 ] 2>/dev/null && [ "$sel" -le "${#subdirs[@]}" ]; then
            dir="$dir/${subdirs[$((sel-1))]}"
          else
            echo "invalid choice"
          fi
          ;;
      esac
    done
    dir=$(cd "$dir" && pwd)
    link="$dir/$WIKI_INDEX_REL"
    echo "Note: this script cannot change your shell's directory (it runs in a subprocess)."
    echo "Selected project path: $dir"
    ;;
  *)
    echo "invalid choice, aborting" >&2
    exit 1
    ;;
esac

block=$(cat <<EOF
$MARKER_START
## Scala Coding Guidelines Knowledge Base

Compounding knowledge base of Scala 3 / Typelevel / cats-effect architecture research
(tagless-final module boundaries, hand-wired Resource-based DI, opaque types, recursion
schemes / droste, cats category-theory patterns, project/testing conventions): $link

**Consult this knowledge base before making Scala architecture decisions or reviewing
Scala code in this repo.** Start at the linked index and follow its article links; treat
its patterns and anti-patterns as the default unless the code here has an explicit reason
to diverge.
$MARKER_END
EOF
)

if [ -f "$TARGET_FILE" ] && grep -qF "$MARKER_START" "$TARGET_FILE"; then
  tmp=$(mktemp)
  blockfile=$(mktemp)
  printf '%s\n' "$block" > "$blockfile"
  awk -v start="$MARKER_START" -v end="$MARKER_END" -v bf="$blockfile" '
    $0==start {
      while ((getline line < bf) > 0) print line
      skip=1; next
    }
    $0==end {skip=0; next}
    skip {next}
    {print}
  ' "$TARGET_FILE" > "$tmp" && mv "$tmp" "$TARGET_FILE"
  rm -f "$blockfile"
  echo "Updated existing block in $TARGET_FILE"
else
  { [ -s "$TARGET_FILE" ] 2>/dev/null && echo; printf '%s\n' "$block"; } >> "$TARGET_FILE"
  echo "Added block to $TARGET_FILE"
fi
