#!/bin/sh
# Teste do hook commit-msg: mensagens válidas passam, inválidas falham.
set -u
hook="$(dirname "$0")/../commit-msg"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
failures=0

check() {
  expected="$1"
  printf '%b' "$2" > "$tmp"
  if sh "$hook" "$tmp" 2>/dev/null; then actual=pass; else actual=fail; fi
  if [ "$actual" = "$expected" ]; then
    echo "ok   ($expected) $(head -n1 "$tmp")"
  else
    echo "FAIL (esperado $expected, obtido $actual) $(head -n1 "$tmp")"
    failures=$((failures + 1))
  fi
}

check pass 'feat(orders): add order aggregate\n'
check pass 'fix: handle null customer\n\n# Please enter the commit message (Claude)\n'
check fail 'feat: x\n\nCo-Authored-By: Someone <a@b.c>\n'
check fail 'feat: x\n\nco-authored-by: someone\n'
check fail 'chore: tweak prompt for Claude\n'
check fail 'docs: mention anthropic\n'
check fail 'feat: x\n\nGenerated with some tool\n'

if [ "$failures" -gt 0 ]; then
  echo "$failures caso(s) falharam" >&2
  exit 1
fi
echo "todos os casos passaram"
