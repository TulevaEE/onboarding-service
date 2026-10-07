#!/bin/bash
set -e

# Network stack (egress NAT, egress IP protection). Source of truth for the
# configuration lives in S3; the local infrastructure/terraform-network/
# directory is gitignored. State is NOT synced here: it lives in the S3 backend
# (onboarding-service/terraform-network-state/) with lockfile locking.

BUCKET_NAME="tuleva-infrastructure"
S3_PREFIX="onboarding-service/terraform-network"
REGION="eu-central-1"
AWS_PROFILE="${AWS_PROFILE:-default}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Uploading network Terraform files to s3://${BUCKET_NAME}/${S3_PREFIX}/"

aws s3 sync "${SCRIPT_DIR}/" "s3://${BUCKET_NAME}/${S3_PREFIX}/" \
    --exclude ".terraform/*" \
    --exclude "backup-*/*" \
    --exclude "*.tfstate*" \
    --exclude "*.tfplan" \
    --delete \
    --sse AES256 \
    --region "${REGION}" \
    --profile "${AWS_PROFILE}"

aws s3 ls "s3://${BUCKET_NAME}/${S3_PREFIX}/" --recursive --region "${REGION}" --profile "${AWS_PROFILE}"
