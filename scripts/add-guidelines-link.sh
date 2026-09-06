#!/usr/bin/env bash
# Idempotently links the scala-coding-guidelines knowledge base into
# ./CLAUDE.md and ./AGENTS.md in the current working directory.
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/MercurieVV/scala-coding-guidelines/master/scripts/add-guidelines-link.sh | bash
#   (or, if your shell doesn't pass prompts through the pipe:)
#   bash <(curl -fsSL https://raw.githubusercontent.com/MercurieVV/scala-coding-guidelines/master/scripts/add-guidelines-link.sh)
#
# Menu tree:
#   GitHub
#     link to it over the internet
#     clone it locally -> point to a folder to clone into
#   Local checkout I already have -> point to it
#
# On a real terminal: arrow keys (or j/k) + Enter, Esc/q cancels. Falls back
# to a plain numbered menu when there's no controlling terminal (e.g. certain
# non-interactive pipes/CI).
#
# Safe to re-run: replaces its own marked block instead of duplicating it.
set -uo pipefail

REPO_URL="https://github.com/MercurieVV/scala-coding-guidelines"
REPO_GIT="$REPO_URL.git"
REPO_DIR_NAME="scala-coding-guidelines"
WIKI_INDEX_REL="wiki/index.md"
TARGET_FILES=("CLAUDE.md" "AGENTS.md")
MARKER_START="<!-- scala-coding-guidelines:start -->"
MARKER_END="<!-- scala-coding-guidelines:end -->"

TTY_OK=0
ORIG_STTY=""
if exec 3<>/dev/tty 2>/dev/null; then
  TTY_OK=1
  ORIG_STTY=$(stty -g <&3 2>/dev/null || true)
fi

cleanup() {
  if [ "$TTY_OK" -eq 1 ]; then
    [ -n "$ORIG_STTY" ] && stty "$ORIG_STTY" <&3 2>/dev/null
    exec 3<&- 2>/dev/null
  fi
}
trap cleanup EXIT

restore_tty() {
  [ "$TTY_OK" -eq 1 ] && [ -n "$ORIG_STTY" ] && stty "$ORIG_STTY" <&3 2>/dev/null
}

# menu TITLE OUTVAR ITEM...
# Arrow-key menu on a real terminal; plain numbered menu otherwise.
# On confirm: sets OUTVAR to the 0-based selected index, returns 0.
# On cancel (Esc/q/EOF): returns 1.
menu() {
  local title="$1" outvar="$2"; shift 2
  local items=("$@")
  local n=${#items[@]}

  if [ "$TTY_OK" -ne 1 ]; then
    echo "$title" >&2
    local i
    for ((i = 0; i < n; i++)); do
      echo "  $((i + 1))) ${items[$i]}" >&2
    done
    local sel
    printf '> ' >&2
    IFS= read -r sel || return 1
    case "$sel" in
      ''|*[!0-9]*) return 1 ;;
    esac
    if [ "$sel" -ge 1 ] && [ "$sel" -le "$n" ]; then
      printf -v "$outvar" '%s' "$((sel - 1))"
      return 0
    fi
    return 1
  fi

  local sel=0 drawn=0 key rest i
  stty -echo -icanon min 1 time 0 <&3 2>/dev/null
  while true; do
    if [ "$drawn" -eq 1 ]; then
      printf '\033[%dA\033[J' "$((n + 1))" >&2
    fi
    drawn=1
    echo "$title" >&2
    for ((i = 0; i < n; i++)); do
      if [ "$i" -eq "$sel" ]; then
        printf '  \033[7m %s \033[0m\n' "${items[$i]}" >&2
      else
        printf '    %s\n' "${items[$i]}" >&2
      fi
    done

    IFS= read -rsn1 key <&3 || { restore_tty; return 1; }
    case "$key" in
      $'\x1b')
        # bash 3.2 (macOS default) has no fractional -t timeout, so read both
        # escape-sequence bytes in one shot: a genuine arrow key already has
        # them buffered (instant); a lone Esc times out after 1s and cancels.
        rest=""
        if IFS= read -rsn2 -t 1 rest <&3; then
          case "$rest" in
            '[A') sel=$(( (sel - 1 + n) % n )) ;;
            '[B') sel=$(( (sel + 1) % n )) ;;
            *) : ;; # unrecognized escape sequence (left/right/etc.) - ignore
          esac
        else
          restore_tty
          printf '\n' >&2
          return 1
        fi
        ;;
      k) sel=$(( (sel - 1 + n) % n )) ;;
      j) sel=$(( (sel + 1) % n )) ;;
      q)
        restore_tty
        printf '\n' >&2
        return 1
        ;;
      '')
        restore_tty
        printf '\n' >&2
        printf -v "$outvar" '%s' "$sel"
        return 0
        ;;
    esac
  done
}

# browse_dir -- interactive filesystem browser starting at $HOME.
# Prints the chosen absolute directory path on stdout; exits the whole
# script if the user cancels.
browse_dir() {
  local dir="$HOME"
  while true; do
    local subdirs=()
    while IFS= read -r d; do
      [ -n "$d" ] && subdirs+=("$d")
    done < <(cd "$dir" 2>/dev/null && ls -d */ 2>/dev/null | sed 's#/$##' | sort)

    local items=("[ select this directory ]" "..")
    [ "${#subdirs[@]}" -gt 0 ] && items+=("${subdirs[@]}")

    local idx
    if ! menu "Current: $dir" idx "${items[@]}"; then
      return 1
    fi

    case "$idx" in
      0) break ;;
      1) dir=$(dirname "$dir") ;;
      *) dir="$dir/${subdirs[$((idx - 2))]}" ;;
    esac
  done
  cd "$dir" && pwd
}

# upsert_block FILE LINK -- idempotently insert/replace the marked block in FILE.
upsert_block() {
  local target="$1" link="$2"
  local block
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

  if [ -f "$target" ] && grep -qF "$MARKER_START" "$target"; then
    local tmp blockfile
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
    ' "$target" > "$tmp" && mv "$tmp" "$target"
    rm -f "$blockfile"
    echo "Updated existing block in $target"
  else
    { [ -s "$target" ] 2>/dev/null && echo; printf '%s\n' "$block"; } >> "$target"
    echo "Added block to $target"
  fi
}

echo "scala-coding-guidelines link setup (in $(pwd))" >&2

top=0
if ! menu "Where does the project live?" top \
    "GitHub" \
    "A local checkout I already have"; then
  echo "Cancelled." >&2
  exit 1
fi

link=""
if [ "$top" -eq 0 ]; then
  sub=0
  if ! menu "Use it over the internet, or clone it locally?" sub \
      "Link to it over the internet (GitHub)" \
      "Clone it locally"; then
    echo "Cancelled." >&2
    exit 1
  fi
  if [ "$sub" -eq 0 ]; then
    link="$REPO_URL/blob/master/$WIKI_INDEX_REL"
  else
    echo "Pick a folder to clone into:" >&2
    parent_dir=$(browse_dir) || { echo "Cancelled." >&2; exit 1; }
    dest="$parent_dir/$REPO_DIR_NAME"
    if [ -d "$dest/.git" ]; then
      echo "Already cloned at $dest, pulling latest..." >&2
      git -C "$dest" pull --ff-only
    else
      echo "Cloning into $dest..." >&2
      git clone --depth 1 "$REPO_GIT" "$dest"
    fi
    link="$dest/$WIKI_INDEX_REL"
  fi
else
  echo "Browse to your existing checkout:" >&2
  dir=$(browse_dir) || { echo "Cancelled." >&2; exit 1; }
  link="$dir/$WIKI_INDEX_REL"
fi

echo "Link: $link" >&2

for f in "${TARGET_FILES[@]}"; do
  upsert_block "$f" "$link"
done
