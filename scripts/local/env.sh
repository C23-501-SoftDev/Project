#!/bin/bash
# Source this file from bash or zsh: source scripts/local/env.sh
if [ -n "${BASH_VERSION:-}" ]; then
  _kb_env_file="${BASH_SOURCE[0]}"
elif [ -n "${ZSH_VERSION:-}" ]; then
  eval '_kb_env_file=${(%):-%x}'
else
  printf '%s\n' 'Use bash or zsh to source scripts/local/env.sh.' >&2
  return 1 2>/dev/null || exit 1
fi

export KB_PROJECT_ROOT="$(cd "$(dirname "$_kb_env_file")/../.." && pwd -P)"
export KB_LOCAL_ROOT="$KB_PROJECT_ROOT/.local"
if [ -x /opt/homebrew/bin/brew ]; then
  _kb_brew=/opt/homebrew
elif [ -x /usr/local/bin/brew ]; then
  _kb_brew=/usr/local
else
  printf '%s\n' 'Homebrew is required: https://brew.sh (then brew install maven node@24 ollama postgresql@18 pgvector).' >&2
  return 1 2>/dev/null || exit 1
fi
if ! JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null); then
  printf '%s\n' 'Java 21 is required; install a JDK 21 and verify /usr/libexec/java_home -v 21.' >&2
  return 1 2>/dev/null || exit 1
fi
export JAVA_HOME
export KB_PG_BIN="$_kb_brew/opt/postgresql@18/bin"
export LOCAL_MAVEN_REPO="$KB_LOCAL_ROOT/m2"
export PLAYWRIGHT_BROWSERS_PATH="$KB_LOCAL_ROOT/browsers"
export OPENSPEC_TELEMETRY=0
export PATH="$KB_LOCAL_ROOT/bin:$JAVA_HOME/bin:$KB_PG_BIN:$_kb_brew/opt/node@24/bin:$_kb_brew/bin:$_kb_brew/sbin:$PATH"
unset _kb_env_file _kb_brew
