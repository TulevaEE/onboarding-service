#!/bin/bash
set -e

# Mirrors infrastructure/terraform/upload-terraform-to-s3.sh but for the X-Road stack.
# Source of truth lives in S3; the local infrastructure/terraform-xroad/ directory
# is gitignored.

BUCKET_NAME="tuleva-infrastructure"
S3_PREFIX="onboarding-service/terraform-xroad"
REGION="eu-central-1"
AWS_PROFILE="${AWS_PROFILE:-default}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Uploading X-Road Terraform files to S3"
echo "   Bucket: s3://${BUCKET_NAME}/${S3_PREFIX}/"
echo "   Local:  ${SCRIPT_DIR}"
echo

# State IS synced to S3 for this stack (interim model — removes the laptop-only
# SPOF and lets teammates apply). State holds secrets; the bucket is private,
# SSE-encrypted, and versioned. Proper long-term fix: an S3 backend with locking.
aws s3 sync "${SCRIPT_DIR}/" "s3://${BUCKET_NAME}/${S3_PREFIX}/" \
    --exclude ".terraform/*" \
    --exclude "backup-*/*" \
    --delete \
    --sse AES256 \
    --region "${REGION}" \
    --profile "${AWS_PROFILE}"

echo
echo "Upload complete."
echo
echo "Files in S3:"
aws s3 ls "s3://${BUCKET_NAME}/${S3_PREFIX}/" --recursive --region "${REGION}" --profile "${AWS_PROFILE}" | tail -20
