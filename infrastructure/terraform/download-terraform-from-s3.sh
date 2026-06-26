#!/bin/bash
set -e

# Download Terraform Files from S3
# Downloads all terraform configuration files from s3://tuleva-infrastructure/onboarding-service/terraform/

BUCKET_NAME="tuleva-infrastructure"
S3_PREFIX="onboarding-service/terraform"
REGION="eu-central-1"
AWS_PROFILE="${AWS_PROFILE:-default}"  # Use AWS_PROFILE from environment, fallback to 'default'
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="${SCRIPT_DIR}/backup-$(date +%Y%m%d-%H%M%S)"

echo "📥 Downloading Terraform files from S3"
echo "   Bucket: s3://${BUCKET_NAME}/${S3_PREFIX}/"
echo "   Local: ${SCRIPT_DIR}"
echo ""

# Create backup of existing files
if ls "${SCRIPT_DIR}"/*.tf &>/dev/null || ls "${SCRIPT_DIR}"/*.tfvars &>/dev/null; then
    echo "💾 Creating backup of existing files..."
    mkdir -p "${BACKUP_DIR}"
    cp "${SCRIPT_DIR}"/*.tf "${BACKUP_DIR}/" 2>/dev/null || true
    cp "${SCRIPT_DIR}"/*.tfvars "${BACKUP_DIR}/" 2>/dev/null || true
    cp "${SCRIPT_DIR}"/*.md "${BACKUP_DIR}/" 2>/dev/null || true
    # Back up state too (defense in depth): a newer local apply not yet uploaded
    # would otherwise be silently overwritten by the pull below — recover from here.
    cp "${SCRIPT_DIR}"/*.tfstate* "${BACKUP_DIR}/" 2>/dev/null || true
    cp -r "${SCRIPT_DIR}"/terraform.tfstate.d "${BACKUP_DIR}/" 2>/dev/null || true
    echo "   Backup saved to: ${BACKUP_DIR}"
    echo ""
fi

# Download all files from S3 (including state files and lock file for team collaboration)
# --delete flag ensures files deleted from S3 are also deleted locally
echo "📥 Downloading files..."
# --exclude "backup-*/*" so --delete never wipes the local backup created above
# (the upload script also excludes it, so it is never in S3 to sync back). State
# itself round-trips via S3 (terraform.tfstate.d/ workspaces), so --delete here
# restores state rather than losing it — unlike a state-excluded stack.
aws s3 sync "s3://${BUCKET_NAME}/${S3_PREFIX}/" "${SCRIPT_DIR}/" \
    --exclude ".terraform/*" \
    --exclude "backup-*/*" \
    --delete \
    --region ${REGION} \
    --profile ${AWS_PROFILE}

echo ""
echo "✅ Download complete!"
echo ""
echo "📁 Downloaded files:"
ls -lh "${SCRIPT_DIR}"/*.tf "${SCRIPT_DIR}"/*.tfvars 2>/dev/null | awk '{print "   " $9, "(" $5 ")"}'
echo ""

if [ -d "${BACKUP_DIR}" ]; then
    echo "💾 Previous files backed up to:"
    echo "   ${BACKUP_DIR}"
    echo ""
fi

echo "🔍 Compare with backup (if needed):"
echo "   diff -r ${BACKUP_DIR} ${SCRIPT_DIR}"
echo ""
echo "🚀 Ready to use! Run terraform commands:"
echo "   terraform init"
echo "   terraform plan -var-file=staging.tfvars"
