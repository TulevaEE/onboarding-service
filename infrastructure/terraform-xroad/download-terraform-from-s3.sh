#!/bin/bash
set -e

# Mirrors infrastructure/terraform/download-terraform-from-s3.sh but for the X-Road stack.
# Backs up the current local directory before overwriting from S3.

BUCKET_NAME="tuleva-infrastructure"
S3_PREFIX="onboarding-service/terraform-xroad"
REGION="eu-central-1"
AWS_PROFILE="${AWS_PROFILE:-default}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="${SCRIPT_DIR}/backup-$(date +%Y%m%d-%H%M%S)"

echo "Downloading X-Road Terraform files from S3"
echo "   Bucket: s3://${BUCKET_NAME}/${S3_PREFIX}/"
echo "   Local:  ${SCRIPT_DIR}"
echo

if ls "${SCRIPT_DIR}"/*.tf "${SCRIPT_DIR}"/*.tfvars 2>/dev/null | grep -q .; then
    echo "Backing up existing files to: ${BACKUP_DIR}"
    mkdir -p "${BACKUP_DIR}"
    cp "${SCRIPT_DIR}"/*.tf "${BACKUP_DIR}/" 2>/dev/null || true
    cp "${SCRIPT_DIR}"/*.tfvars "${BACKUP_DIR}/" 2>/dev/null || true
    cp "${SCRIPT_DIR}"/*.md "${BACKUP_DIR}/" 2>/dev/null || true
    # Snapshot state too (defense in depth): if a local apply that hasn't been
    # uploaded yet gets overwritten by the pull below, recover it from backup-*/.
    cp "${SCRIPT_DIR}"/*.tfstate* "${BACKUP_DIR}/" 2>/dev/null || true
    echo
fi

# State is the source of truth in S3 for this stack, so we DO pull it and let
# --delete mirror S3. The pre-sync backup above snapshots local *.tfstate* first,
# so a divergent local apply not yet uploaded can be recovered from backup-*/.
aws s3 sync "s3://${BUCKET_NAME}/${S3_PREFIX}/" "${SCRIPT_DIR}/" \
    --exclude ".terraform/*" \
    --exclude "backup-*/*" \
    --delete \
    --region "${REGION}" \
    --profile "${AWS_PROFILE}"

echo
echo "Download complete. Run \`terraform init\` if this is a fresh checkout."
