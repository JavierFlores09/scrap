#!/usr/bin/env bash
set -euo pipefail

tag=branch-builds
repo="$GITHUB_REPOSITORY"
branch_path=$(jq -rn --arg branch "$BUILD_BRANCH" '$branch | @uri')
tip=$(gh api "repos/$repo/branches/$branch_path" --jq .commit.sha)
if [[ "$BUILD_SHA" != "$tip" ]]; then
  echo "Skipping superseded build of $BUILD_BRANCH at $BUILD_SHA"
  exit 0
fi

# Keep names readable while distinguishing branches such as fix/a and fix-a.
slug=$(printf '%s' "$BUILD_BRANCH" | LC_ALL=C tr -cs 'A-Za-z0-9_-' '-' | cut -c1-80)
digest=$(printf '%s' "$BUILD_BRANCH" | sha256sum | cut -c1-16)
asset="scrap-${slug}-${digest}.jar"
mv dist/server.jar "dist/$asset"

if ! gh release view "$tag" --repo "$repo" >/dev/null; then
  gh release create "$tag" --repo "$repo" --target "$BUILD_SHA" \
    --title "Branch builds" --notes "Preparing branch downloads."
fi
gh release upload "$tag" "dist/$asset#$BUILD_BRANCH" --clobber --repo "$repo"

# Asset labels retain the exact branch names across independent workflow runs.
release_id=$(gh api "repos/$repo/releases/tags/$tag" --jq .id)
gh api --paginate --slurp "repos/$repo/releases/$release_id/assets?per_page=100" |
  jq -r '
    def escape: @html | gsub("\\|"; "&#124;") | gsub("`"; "&#96;");
    "Latest server JAR for each published branch. Download links stay the same across builds.",
    "",
    "| Branch | Download |",
    "| --- | --- |",
    (add | map(select(.state == "uploaded" and (.name | startswith("scrap-"))))
     | sort_by(.label)[] | "| <code>\(.label | escape)</code> | [Server JAR](\(.browser_download_url)) |")
  ' > release-notes.md
gh release edit "$tag" --repo "$repo" --notes-file release-notes.md
