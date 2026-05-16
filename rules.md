# Agent Safety Rules

These rules apply to every AI coding agent working on this project.

## Destructive Command Rules

Agents must **not** run destructive delete commands.

Forbidden commands include, but are not limited to:

```bash
rm -rf
rm -fr
sudo rm
find . -delete
git clean -fd
git clean -fdx
git reset --hard
```

## File Deletion Policy

Agents may delete individual files only when all of the following are true:

1. The file is clearly generated, temporary, or obsolete.
2. The agent explains why the file should be deleted.
3. The deletion targets a specific file path, not a broad directory.
4. The command does not use recursive force deletion.

Preferred safer options:

```bash
mv path/to/file path/to/file.bak
mkdir -p .agent-trash
mv path/to/file .agent-trash/
```

## Directory Deletion Policy

Agents must not delete directories unless the user explicitly approves the exact directory path first.

Never delete these directories:

```text
.
..
.git
.gradle
.idea
app
src
build.gradle
settings.gradle
gradle
```

## Before Any Risky Change

Before running commands that modify many files, agents must:

1. Show the exact command.
2. Explain what it will change.
3. Prefer a dry run when available.
4. Ask for user approval if files may be deleted or overwritten.

## Safe Cleanup Alternatives

Use safe, targeted cleanup commands instead of destructive deletion.

Allowed examples:

```bash
./gradlew clean
git status
git diff
find . -name "*.tmp" -print
```

If cleanup is needed, prefer moving files into `.agent-trash/` instead of deleting them.

## Git Safety

Agents must not run any git commands:

without explicit user approval.

Before making large changes, agents should check:

```bash
git status
```

## Final Rule

When in doubt, do not delete. Ask first.
