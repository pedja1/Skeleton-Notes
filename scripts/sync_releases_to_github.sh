#!/usr/bin/env bash
# One-way sync of releases from Gitea (local) to GitHub (remote).
# Creates on GitHub any non-draft Gitea release whose tag is missing there,
# together with its assets. Never modifies Gitea; never deletes on GitHub.
#
# Required env:
#   GITEA_API_URL  e.g. https://gitea.host/api/v1   (= $GITHUB_SERVER_URL/api/v1 in Gitea Actions)
#   GITEA_REPO     owner/repo on Gitea              (= $GITHUB_REPOSITORY)
#   GITEA_TOKEN    Gitea token                      (= secrets.GITHUB_TOKEN)
#   GH_REPO        owner/repo on GitHub
#   GH_PAT         GitHub token with contents:write on $GH_REPO
set -euo pipefail
: "${GITEA_API_URL:?}"; : "${GITEA_REPO:?}"; : "${GITEA_TOKEN:?}"; : "${GH_REPO:?}"; : "${GH_PAT:?}"

workdir=$(mktemp -d)
CURRENT_RELEASE_ID=""
gh() { curl -sS -H "Authorization: Bearer ${GH_PAT}" -H "Accept: application/vnd.github+json" \
       -H "X-GitHub-Api-Version: 2022-11-28" "$@"; }
cleanup() {
  if [ -n "$CURRENT_RELEASE_ID" ]; then
    echo "Rolling back incomplete GitHub release ${CURRENT_RELEASE_ID}" >&2
    gh -X DELETE "https://api.github.com/repos/${GH_REPO}/releases/${CURRENT_RELEASE_ID}" >/dev/null 2>&1 || true
  fi
  rm -rf "$workdir"
}
trap cleanup EXIT

fetch_all() { # $1=describe  $2=url-prefix  $3=auth-header ; prints array elements, one JSON object per line
  local page=1 resp count
  while :; do
    resp=$(curl -sS -H "$3" "${2}?per_page=50&page=${page}")
    if ! echo "$resp" | jq -e 'type == "array"' >/dev/null 2>&1; then
      echo "Unexpected $1 response: $resp" >&2; exit 1
    fi
    count=$(echo "$resp" | jq 'length'); [ "$count" -eq 0 ] && break
    echo "$resp" | jq -c '.[]'
    page=$((page+1))
  done
}

# Gitea releases (drop drafts)
fetch_all "Gitea" "${GITEA_API_URL}/repos/${GITEA_REPO}/releases" "Authorization: token ${GITEA_TOKEN}" \
  | jq -c 'select(.draft == false)' > "$workdir/gitea.ndjson"
# Existing GitHub tags
fetch_all "GitHub" "https://api.github.com/repos/${GH_REPO}/releases" "Authorization: Bearer ${GH_PAT}" \
  | jq -r '.tag_name' > "$workdir/gh_tags.txt"

created=0
while IFS= read -r rel; do
  tag=$(echo "$rel" | jq -r '.tag_name')
  [ -z "$tag" ] && continue
  if grep -Fxq "$tag" "$workdir/gh_tags.txt"; then echo "= ${tag}: present"; continue; fi

  echo "+ ${tag}: creating on GitHub"
  payload=$(echo "$rel" | jq '{tag_name, name: (.name // .tag_name), body: (.body // ""),
      prerelease, draft: false} + (if (.target_commitish // "") == "" then {}
      else {target_commitish: .target_commitish} end)')
  resp=$(gh -X POST "https://api.github.com/repos/${GH_REPO}/releases" -d "$payload")
  rel_id=$(echo "$resp" | jq -r '.id')
  if [ -z "$rel_id" ] || [ "$rel_id" = "null" ]; then echo "  ! create failed: $resp" >&2; exit 1; fi
  CURRENT_RELEASE_ID="$rel_id"

  while IFS= read -r asset; do
    [ -z "$asset" ] && continue
    a_name=$(echo "$asset" | jq -r '.name')
    a_url=$(echo "$asset" | jq -r '.browser_download_url')
    echo "    asset ${a_name}"
    curl -fsSL -H "Authorization: token ${GITEA_TOKEN}" -o "$workdir/a.bin" "$a_url"
    up=$(gh -X POST -H "Content-Type: application/octet-stream" --data-binary "@$workdir/a.bin" \
        "https://uploads.github.com/repos/${GH_REPO}/releases/${rel_id}/assets?name=${a_name}")
    if [ "$(echo "$up" | jq -r '.state // empty')" != "uploaded" ]; then
      echo "  ! upload failed: $up" >&2; exit 1
    fi
    rm -f "$workdir/a.bin"
  done < <(echo "$rel" | jq -c '.assets[]?')

  CURRENT_RELEASE_ID=""   # fully synced; disarm rollback
  created=$((created+1))
done < "$workdir/gitea.ndjson"

echo "Sync complete. Created ${created} release(s) on GitHub."
