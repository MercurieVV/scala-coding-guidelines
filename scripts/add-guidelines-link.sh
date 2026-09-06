#!/usr/bin/env bash
# Idempotently adds a link to the scala-coding-guidelines knowledge base into
# both ./CLAUDE.md and ./AGENTS.md in the current working directory.
#
# Interactive, arrow-key menu on a real terminal (falls back to a plain
# numbered menu otherwise, e.g. non-interactive pipes/CI):
#   1. GitHub, or a local checkout you already have?
#   2. If GitHub: link to it over the internet, or clone it locally first
#      (you pick the parent folder)?
#   3. If local: browse the filesystem for the checkout.
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/MercurieVV/scala-coding-guidelines/master/scripts/add-guidelines-link.sh | bash
#   (or, if your shell doesn't pass prompts through the pipe:)
#   bash <(curl -fsSL https://raw.githubusercontent.com/MercurieVV/scala-coding-guidelines/master/scripts/add-guidelines-link.sh)
#
# Safe to re-run: it replaces its own marked block instead of duplicating it.
set -uo pipefail

REPO_URL="https://github.com/MercurieVV/scala-coding-guidelines"
REPO_GIT="$REPO_URL.git"
REPO_DIR_NAME="scala-coding-guidelines"
WIKI_INDEX_REL="wiki/index.md"
TARGET_FILES=("CLAUDE.md" "AGENTS.md")
MARKER_START="<!-- scala-coding-guidelines:start -->"
MARKER_END="<!-- scala-coding-guidelines:end -->"

TTY_OK=0
if exec 3<>/dev/tty 2>/dev/null; then
  TTY_OK=1
fi
ORIG_STTY=""
[ "$TTY_OK" -eq 1 ] && ORIG_STTY=$(stty -g <&3 2>/dev/null || true)

cleanup() {
  if [ "$TTY_OK" -eq 1 ]; then
    [ -n "$ORIG_STTY" ] && stty "$ORIG_STTY" <&3 2>/dev/null
    exec 3<&- 2>/dev/null
  fi
}
trap cleanup EXIT

set_raw()    { [ "$TTY_OK" -eq 1 ] && stty -echo -icanon min 1 time 0 <&3 2>/dev/null; }
set_cooked() { [ "$TTY_OK" -eq 1 ] && [ -n "$ORIG_STTY" ] && stty "$ORIG_STTY" <&3 2>/dev/null; }

# select_menu TITLE OUTVAR ITEM...
# Real-terminal menu: up/down arrows (or j/k) to move, enter to confirm, esc/q
# to cancel. On confirm sets OUTVAR to the 0-based selected index and returns
# 0; on cancel returns 1.
select_menu() {
  local title="$1" outvar="$2"; shift 2
  local items=("$@")
  local n=${#items[@]}
  local sel=0 first=1 key rest

  render_menu() {
    if [ "$first" -eq 0 ]; then
      printf '\033[%dA\033[0J' "$((n + 2))" >&2
    fi
    first=0
    printf '%s\n' "$title" >&2
    local i
    for ((i = 0; i < n; i++)); do
      if [ "$i" -eq "$sel" ]; then
        printf '  \033[7m> %s\033[0m\n' "${items[$i]}" >&2
      else
        printf '    %s\n' "${items[$i]}" >&2
      fi
    done
    printf '  (up/down or j/k, enter to choose, esc/q to cancel)\n' >&2
  }

  set_raw
  render_menu
  while true; do
    IFS= read -rsn1 key <&3 || { set_cooked; return 1; }
    case "$key" in
      $'\x1b')
        # bash 3.2 (macOS default) has no fractional -t timeout, so read both
        # escape-sequence bytes in one shot; a genuine arrow key has them
        # already buffered (instant), a lone Esc times out after 1s and cancels.
        rest=""
        if IFS= read -rsn2 -t 1 rest <&3; then
          case "$rest" in
            '[A') sel=$(( (sel - 1 + n) % n )); render_menu ;;
            '[B') sel=$(( (sel + 1) % n )); render_menu ;;
          esac
        else
          printf '\n' >&2
          set_cooked
          return 1
        fi
        ;;
      k) sel=$(( (sel - 1 + n) % n )); render_menu ;;
      j) sel=$(( (sel + 1) % n )); render_menu ;;
      q)
        printf '\n' >&2
        set_cooked
        return 1
        ;;
      '')
        printf -v "$outvar" '%s' "$sel"
        printf '\n' >&2
        set_cooked
        return 0
        ;;
    esac
  done
}

# text_menu TITLE OUTVAR ITEM...  -- plain numbered fallback for non-tty use.
text_menu() {
  local title="$1" outvar="$2"; shift 2
  local items=("$@")
  local n=${#items[@]}
  echo "$title" >&2
  local i
  for ((i = 0; i < n; i++)); do
    echo "  $((i + 1))) ${items[$i]}" >&2
  done
  local sel
  printf '> ' >&2
  IFS= read -r sel
  case "$sel" in
    ''|*[!0-9]*) return 1 ;;
  esac
  if [ "$sel" -ge 1 ] 2>/dev/null && [ "$sel" -le "$n" ]; then
    printf -v "$outvar" '%s' "$((sel - 1))"
    return 0
  fi
  return 1
}

# pick_one TITLE OUTVAR ITEM... -- arrow menu on a real terminal, plain
# numbered menu otherwise.
pick_one() {
  if [ "$TTY_OK" -eq 1 ]; then
    select_menu "$@"
  else
    text_menu "$@"
  fi
}

# read_line PROMPT OUTVAR -- cooked-mode line read (for typing a path).
read_line() {
  local prompt="$1" outvar="$2"
  if [ "$TTY_OK" -eq 1 ]; then
    set_cooked
    printf '%s' "$prompt" >&2
    IFS= read -r "$outvar" <&3
  else
    printf '%s' "$prompt" >&2
    IFS= read -r "$outvar"
  fi
}

# browse_local_dir -- interactive filesystem browser starting at $HOME.
# Prints the chosen absolute directory path on stdout.
browse_local_dir() {
  local dir="$HOME"
  while true; do
    local subdirs=()
    while IFS= read -r d; do
      [ -n "$d" ] && subdirs+=("$d")
    done < <(cd "$dir" 2>/dev/null && ls -d */ 2>/dev/null | sed 's#/$##' | sort)

    local items=("[ select this directory ]" "[ type a path ]" ".. (up one level)")
    [ "${#subdirs[@]}" -gt 0 ] && items+=("${subdirs[@]}")

    local idx
    if ! pick_one "Current: $dir" idx "${items[@]}"; then
      echo "Cancelled." >&2
      exit 1
    fi

    case "$idx" in
      0) break ;;
      1)
        local typed
        read_line "path: " typed
        [ -d "$typed" ] && dir="$typed" || echo "not a directory: $typed" >&2
        ;;
      2) dir=$(dirname "$dir") ;;
      *)
        local pick=$((idx - 3))
        dir="$dir/${subdirs[$pick]}"
        ;;
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

echo "scala-coding-guidelines link setup (in $(pwd))"

top_idx=0
if ! pick_one "Where does the project live?" top_idx \
    "GitHub" \
    "A local checkout I already have"; then
  echo "Cancelled." >&2
  exit 1
fi

link=""
case "$top_idx" in
  0)
    sub_idx=0
    if ! pick_one "Use it over the internet, or clone it locally?" sub_idx \
        "Link to it over the internet (GitHub)" \
        "Clone it locally (pick a parent folder)"; then
      echo "Cancelled." >&2
      exit 1
    fi
    case "$sub_idx" in
      0)
        link="$REPO_URL/blob/master/$WIKI_INDEX_REL"
        ;;
      1)
        echo "Pick the folder to clone into:" >&2
        parent_dir=$(browse_local_dir)
        dest="$parent_dir/$REPO_DIR_NAME"
        if [ -d "$dest/.git" ]; then
          echo "Already cloned at $dest, pulling latest..."
          git -C "$dest" pull --ff-only
        else
          echo "Cloning into $dest..."
          git clone --depth 1 "$REPO_GIT" "$dest"
        fi
        link="$dest/$WIKI_INDEX_REL"
        ;;
    esac
    ;;
  1)
    echo "Browse to your existing checkout:" >&2
    dir=$(browse_local_dir)
    link="$dir/$WIKI_INDEX_REL"
    echo "Note: this script cannot change your shell's directory (it runs in a subprocess)."
    echo "Selected project path: $dir"
    ;;
esac

for f in "${TARGET_FILES[@]}"; do
  upsert_block "$f" "$link"
done
