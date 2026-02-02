#!/bin/bash
# Hook: Run Spotless apply and block if it fails
# Used as a Stop hook to ensure code is properly formatted before Claude finishes

set -o pipefail

cd "$CLAUDE_PROJECT_DIR" || exit 1

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
