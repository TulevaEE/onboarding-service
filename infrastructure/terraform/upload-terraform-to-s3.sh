#!/bin/bash
set -e

# Upload Terraform Files to S3
# Uploads all terraform configuration files to s3://tuleva-infrastructure/onboarding-service/terraform/
# Uses sync with --delete to mirror local state (including deletions)

BUCKET_NAME="tuleva-infrastructure"
S3_PREFIX="onboarding-service/terraform"
REGION="eu-central-1"
AWS_PROFILE="${AWS_PROFILE:-default}"  # Use AWS_PROFILE from environment, fallback to 'default'
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "📤 Uploading Terraform files to S3"
echo "   Bucket: s3://${BUCKET_NAME}/${S3_PREFIX}/"
echo "   Local: ${SCRIPT_DIR}"
echo ""

# Sync all files to S3
# --delete flag ensures files deleted locally are also deleted from S3
# --exclude prevents uploading provider binaries
echo "🔄 Syncing files (including deletions)..."
aws s3 sync "${SCRIPT_DIR}/" "s3://${BUCKET_NAME}/${S3_PREFIX}/" \
    --exclude ".terraform/*" \
    --exclude "backup-*/*" \
    --delete \
    --sse AES256 \
    --region ${REGION} \
    --profile ${AWS_PROFILE}

echo ""
echo "✅ Upload complete!"
echo ""

# Show what was uploaded
echo "📁 Files in S3:"
aws s3 ls "s3://${BUCKET_NAME}/${S3_PREFIX}/" --recursive --region ${REGION} --profile ${AWS_PROFILE} | tail -20

echo ""
echo "📊 Full listing:"
echo "   aws s3 ls s3://${BUCKET_NAME}/${S3_PREFIX}/ --recursive --profile ${AWS_PROFILE}"
echo ""
echo "💾 List versions (if you need to rollback):"
echo "   aws s3api list-object-versions --bucket ${BUCKET_NAME} --prefix ${S3_PREFIX}/ --profile ${AWS_PROFILE}"
echo ""
echo "🔐 Files are encrypted with AES256 and versioned for safety"
