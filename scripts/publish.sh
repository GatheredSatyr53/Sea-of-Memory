#!/usr/bin/env bash
# Cuts a release of Sea of Memory.
#
#   scripts/publish.sh [version] [--skip-build] [--dry-run]
#
# With a version, bumps mod_version in gradle.properties and commits it;
# without one, releases the version already there. Then builds the mod,
# tags the commit as v<version> and pushes both. The tag push triggers
# .github/workflows/release.yml, which builds the jar again and attaches it
# to a GitHub Release.
set -euo pipefail

cd "$(dirname "$0")/.."

release_branch=main
props=gradle.properties

version=
skip_build=false
dry_run=false
for arg in "$@"; do
    case "$arg" in
        --skip-build) skip_build=true ;;
        --dry-run) dry_run=true ;;
        -h|--help) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        -*) echo "Unknown option: $arg" >&2; exit 2 ;;
        *)
            [[ -z "$version" ]] || { echo "Only one version may be given" >&2; exit 2; }
            version="$arg" ;;
    esac
done

die() { echo "error: $*" >&2; exit 1; }
run() {
    echo "+ $*"
    if ! $dry_run; then "$@"; fi
}

current=$(sed -n 's/^mod_version=//p' "$props")
[[ -n "$current" ]] || die "mod_version not found in $props"
version=${version:-$current}
version=${version#v}

[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([-+][0-9A-Za-z.-]+)?$ ]] \
    || die "'$version' is not a semver version (e.g. 0.2.0 or 0.2.0-beta.1)"

tag="v$version"

branch=$(git rev-parse --abbrev-ref HEAD)
[[ "$branch" == "$release_branch" ]] || die "releases are cut from $release_branch, not $branch"
[[ -z "$(git status --porcelain)" ]] || die "working tree is not clean"

git fetch --quiet --tags origin "$release_branch"
[[ "$(git rev-parse HEAD)" == "$(git rev-parse "origin/$release_branch")" ]] \
    || die "$release_branch is not in sync with origin/$release_branch"
if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    die "tag $tag already exists"
fi

echo "Releasing Sea of Memory $version ($tag)"
$dry_run && echo "(dry run: nothing will be changed)"

if [[ "$version" != "$current" ]]; then
    echo "Bumping mod_version $current -> $version"
    if ! $dry_run; then
        sed -i.bak "s/^mod_version=.*/mod_version=$version/" "$props" && rm -f "$props.bak"
    fi
    run git commit -m "Release $version" -- "$props"
fi

if ! $skip_build; then
    run ./gradlew --no-configuration-cache clean build
fi

run git tag -a "$tag" -m "Sea of Memory $version"
run git push --atomic origin "$release_branch" "$tag"

echo "Done. GitHub Actions will publish the release for $tag."
