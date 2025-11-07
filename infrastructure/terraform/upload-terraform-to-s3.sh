#!/bin/bash
set -e

# Upload Terraform Files to S3
# Uploads all terraform configuration files to s3://tuleva-infrastructure/onboarding-service/terraform/

BUCKET_NAME="tuleva-infrastructure"
S3_PREFIX="onboarding-service/terraform"
REGION="eu-central-1"
AWS_PROFILE="${AWS_PROFILE:-default}"  # Use AWS_PROFILE from environment, fallback to 'default'
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "📤 Uploading Terraform files to S3"
echo "   Bucket: s3://${BUCKET_NAME}/${S3_PREFIX}/"
echo "   Local: ${SCRIPT_DIR}"
echo ""

# Function to upload a file with metadata
upload_file() {
    local file=$1
    local s3_key="${S3_PREFIX}/${file}"

    echo "  📄 Uploading: ${file}"
    aws s3 cp "${SCRIPT_DIR}/${file}" "s3://${BUCKET_NAME}/${s3_key}" \
        --sse AES256 \
        --region ${REGION} \
        --profile ${AWS_PROFILE} \
        --metadata "uploaded-at=$(date -u +%Y-%m-%dT%H:%M:%SZ),uploaded-by=${USER}"
}

# Upload main terraform files
echo "📋 Core Terraform files:"
upload_file "main.tf"
upload_file "variables.tf"
upload_file "outputs.tf"

# Upload module files
echo ""
echo "🔧 Module files:"
upload_file "alb-trust-store.tf"
upload_file "log-streaming.tf"

# Upload configuration files
echo ""
echo "⚙️  Configuration files:"
upload_file "staging.tfvars"
upload_file "production.tfvars"

# Upload all scripts (including upload/download scripts for bootstrapping new machines)
echo ""
echo "🔨 Helper scripts:"
for script in *.sh; do
    upload_file "$script"
done

echo ""
echo "✅ Upload complete!"
echo ""
echo "📊 Verify upload:"
echo "   aws s3 ls s3://${BUCKET_NAME}/${S3_PREFIX}/ --recursive --profile ${AWS_PROFILE}"
echo ""
echo "💾 List versions (if you need to rollback):"
echo "   aws s3api list-object-versions --bucket ${BUCKET_NAME} --prefix ${S3_PREFIX}/ --profile ${AWS_PROFILE}"
echo ""
echo "🔐 Files are encrypted with AES256 and versioned for safety"
