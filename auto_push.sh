#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REMOTE_NAME="${GIT_REMOTE:-origin}"
REMOTE_URL_OVERRIDE="${GIT_REMOTE_URL:-}"
DRY_RUN=false
KEEP_TEMP=false
COMMIT_MESSAGE=""

usage() {
  cat <<'EOF'
Usage:
  ./auto_push.sh [--dry-run] [--keep-temp] [--remote-url URL] [-m "commit message"]
  ./auto_push.sh [--dry-run] [--keep-temp] [--remote-url URL] "commit message"

Environment overrides:
  GIT_REMOTE   Remote to read the push URL from (default: origin)
  GIT_REMOTE_URL Explicit remote URL to push to
  GIT_BRANCH   Branch to push to (default: current branch)

What it does:
  - clones the repo into a temporary directory
  - copies your current working tree into that temporary clone
  - removes local secret files from the push copy
  - replaces detected secrets with placeholders in the push copy only
  - commits and pushes the sanitized snapshot

Your local files stay unchanged.
EOF
}

while (($# > 0)); do
  case "$1" in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --keep-temp)
      KEEP_TEMP=true
      shift
      ;;
    -m|--message)
      if (($# < 2)); then
        echo "Missing value for $1" >&2
        exit 1
      fi
      COMMIT_MESSAGE="$2"
      shift 2
      ;;
    --remote-url)
      if (($# < 2)); then
        echo "Missing value for $1" >&2
        exit 1
      fi
      REMOTE_URL_OVERRIDE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      if [[ -n "$COMMIT_MESSAGE" ]]; then
        echo "Only one commit message is supported." >&2
        usage >&2
        exit 1
      fi
      COMMIT_MESSAGE="$1"
      shift
      ;;
  esac
done

if ! git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "This script must be run from inside the git repository." >&2
  exit 1
fi

CURRENT_BRANCH="$(git -C "$ROOT_DIR" rev-parse --abbrev-ref HEAD)"
TARGET_BRANCH="${GIT_BRANCH:-$CURRENT_BRANCH}"

if [[ "$TARGET_BRANCH" == "HEAD" ]]; then
  echo "Detached HEAD detected. Set GIT_BRANCH to the branch you want to push." >&2
  exit 1
fi

if [[ -z "$COMMIT_MESSAGE" ]]; then
  COMMIT_MESSAGE="chore: push sanitized snapshot"
fi

if ! command -v rsync >/dev/null 2>&1; then
  echo "rsync is required but was not found." >&2
  exit 1
fi

REMOTE_URL="$REMOTE_URL_OVERRIDE"
if [[ -z "$REMOTE_URL" ]]; then
  REMOTE_URL="$(git -C "$ROOT_DIR" remote get-url "$REMOTE_NAME" 2>/dev/null || true)"
fi

if [[ "$DRY_RUN" != "true" && -z "$REMOTE_URL" ]]; then
  echo "No git remote was found. Configure '$REMOTE_NAME' or pass --remote-url." >&2
  exit 1
fi

TMP_PARENT="${TMPDIR:-/private/tmp}"
TMP_DIR="$(mktemp -d "${TMP_PARENT%/}/jenkins-push.XXXXXX")"

cleanup() {
  if [[ "$KEEP_TEMP" == "true" ]]; then
    echo "Kept temporary sanitized clone at: $TMP_DIR"
  else
    rm -rf "$TMP_DIR"
  fi
}
trap cleanup EXIT

echo "Preparing temporary push clone at $TMP_DIR"
git clone --quiet "$ROOT_DIR" "$TMP_DIR"
git -C "$TMP_DIR" checkout --quiet "$TARGET_BRANCH"

if [[ -n "$REMOTE_URL" ]]; then
  git -C "$TMP_DIR" remote set-url origin "$REMOTE_URL"
fi

echo "Copying current working tree into the temporary clone"
rsync -a --delete \
  --exclude '.git/' \
  --exclude 'config-secret.yml' \
  --exclude 'config.secrets.yml' \
  --exclude '.DS_Store' \
  "$ROOT_DIR"/ "$TMP_DIR"/

rm -f "$TMP_DIR/config-secret.yml" "$TMP_DIR/config.secrets.yml"

echo "Sanitizing the temporary clone"
ruby - "$ROOT_DIR" "$TMP_DIR" <<'RUBY'
require "yaml"

source_root = ARGV.fetch(0)
target_root = ARGV.fetch(1)

TEXT_EXTENSIONS = %w[
  .md
  .txt
  .yml
  .yaml
  .groovy
  .sh
  .j2
  .ini
].freeze

def text_file?(path)
  return true if File.basename(path) == "Justfile"
  return false unless TEXT_EXTENSIONS.include?(File.extname(path))

  bytes = File.binread(path)
  !bytes.include?("\x00")
rescue StandardError
  false
end

def tracked_text_files(root)
  Dir.glob(File.join(root, "**", "*"), File::FNM_DOTMATCH).select do |path|
    next false unless File.file?(path)
    next false if path.include?("/.git/")

    text_file?(path)
  end
end

secret_config_path = File.join(source_root, "config-secret.yml")
secret_config = if File.exist?(secret_config_path)
  YAML.safe_load(File.read(secret_config_path), permitted_classes: [], aliases: false) || {}
else
  {}
end

exact_replacements = []

Array(secret_config["jenkins_string_credentials"]).each do |credential|
  next unless credential.is_a?(Hash)

  secret_id = credential["id"].to_s
  secret_value = credential["secret"].to_s
  next if secret_value.empty? || secret_value == "replace-me"

  next unless secret_id.match?(/token|secret|defectdojo/i) || secret_value.include?("github_pat_") || secret_value.start_with?("sqa_")

  exact_replacements << [secret_value, "replace-me"]
end

Array(secret_config["jenkins_username_password_credentials"]).each do |credential|
  next unless credential.is_a?(Hash)

  password = credential["password"].to_s
  next if password.empty? || password == "replace-me"
  next if password.length < 8 && !password.include?("github_pat_")

  exact_replacements << [password, "replace-me"]
end

Array(secret_config["jenkins_ssh_private_key_credentials"]).each do |credential|
  next unless credential.is_a?(Hash)

  private_key = credential["private_key"].to_s
  next if private_key.empty? || private_key == "replace-me"

  placeholder = [
    "-----BEGIN OPENSSH PRIVATE KEY-----",
    "replace-me",
    "-----END OPENSSH PRIVATE KEY-----"
  ].join("\n")
  exact_replacements << [private_key, placeholder]
end

tracked_text_files(target_root).each do |path|
  content = File.read(path)
  original_content = content.dup

  content.gsub!(/^(\s*jenkins_admin_username:\s*).*/, "\\1\"replace-me\"")
  content.gsub!(/^(\s*jenkins_admin_password:\s*).*/, "\\1\"replace-me\"")
  content.gsub!(/(- Login username:\s*`)([^`]+)(`)/, "\\1replace-me\\3")
  content.gsub!(/(- Login password:\s*`)([^`]+)(`)/, "\\1replace-me\\3")

  exact_replacements.each do |secret_value, placeholder|
    content.gsub!(secret_value, placeholder)
  end

  next if content == original_content

  File.write(path, content)
end
RUBY

git -C "$TMP_DIR" add -A

if git -C "$TMP_DIR" diff --cached --quiet; then
  echo "No sanitized changes to push."
  exit 0
fi

if [[ "$DRY_RUN" == "true" ]]; then
  echo
  echo "Dry run complete. Sanitized diff summary:"
  git -C "$TMP_DIR" status --short
  echo
  git -C "$TMP_DIR" diff --cached --stat
  exit 0
fi

echo "Committing sanitized snapshot"
git -C "$TMP_DIR" commit -m "$COMMIT_MESSAGE"

echo "Pushing sanitized snapshot to origin/$TARGET_BRANCH"
git -C "$TMP_DIR" push origin "HEAD:$TARGET_BRANCH"

echo "Push completed. Local files were not changed."
