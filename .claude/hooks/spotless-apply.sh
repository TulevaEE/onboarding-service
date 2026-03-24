#!/bin/bash
# Hook: Run Spotless apply and block if it fails
# Used as a Stop hook to ensure code is properly formatted before Claude finishes

set -o pipefail

cd "$CLAUDE_PROJECT_DIR" || exit 1

# Set JAVA_HOME if not already set
if [ -z "$JAVA_HOME" ] && [ -d "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Skip if no Java/Groovy/Kotlin files were modified
java_files=$(git diff --name-only --diff-filter=ACMR 2>/dev/null | grep -E '\.(java|groovy|kt|kts)$')
if [ -z "$java_files" ]; then
    exit 0
fi

# Run spotlessApply to fix formatting issues
apply_output=$(./gradlew spotlessApply 2>&1)
apply_result=$?

if [ $apply_result -ne 0 ]; then
    cat >&2 << EOF
Spotless apply failed. Please fix the formatting issues.

$apply_output
EOF
    exit 2
fi

exit 0
