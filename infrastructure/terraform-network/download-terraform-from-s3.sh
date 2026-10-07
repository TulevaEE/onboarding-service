#!/bin/bash
set -e

# Network stack (egress NAT, egress IP protection). Backs up local *.tf files,
# then mirrors the configuration from S3. State stays in the S3 backend.

BUCKET_NAME="tuleva-infrastructure"
S3_PREFIX="onboarding-service/terraform-network"
REGION="eu-central-1"
AWS_PROFILE="${AWS_PROFILE:-default}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="${SCRIPT_DIR}/backup-$(date +%Y%m%d-%H%M%S)"

if ls "${SCRIPT_DIR}"/*.tf 2>/dev/null | grep -q .; then
    mkdir -p "${BACKUP_DIR}"
    cp "${SCRIPT_DIR}"/*.tf "${BACKUP_DIR}/"
    echo "Backed up local files to ${BACKUP_DIR}"
fi

aws s3 sync "s3://${BUCKET_NAME}/${S3_PREFIX}/" "${SCRIPT_DIR}/" \
    --exclude ".terraform/*" \
    --exclude "backup-*/*" \
    --delete \
    --region "${REGION}" \
    --profile "${AWS_PROFILE}"

echo "Run: terraform init && terraform plan"
