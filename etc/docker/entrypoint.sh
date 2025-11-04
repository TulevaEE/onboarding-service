#!/bin/bash

# Onboarding-Service Container Entrypoint
# Works for BOTH Elastic Beanstalk and ECS deployments

set -e

echo "🚀 Onboarding-Service Container Startup"
echo "Environment: ${SPRING_PROFILES_ACTIVE:-unknown}"
echo "=========================================="

# Detect deployment environment
# ECS: /truststore.jks doesn't exist (needs download from S3)
# Beanstalk: /home/webapp/truststore.jks exists (downloaded by .ebextensions)
if [ -f "/home/webapp/truststore.jks" ]; then
    echo "📦 Detected: Elastic Beanstalk deployment"
    echo "   Files provided by .ebextensions"
    DEPLOYMENT_ENV="beanstalk"
else
    echo "🐳 Detected: ECS deployment"
    echo "   Files need to be downloaded from S3"
    DEPLOYMENT_ENV="ecs"
fi

# Function to download file from S3 (ECS only)
download_from_s3() {
    local s3_path=$1
    local local_path=$2
    local description=$3

    echo "📥 Downloading ${description} from S3..."
    if aws s3 cp "${s3_path}" "${local_path}" --region eu-central-1; then
        chmod 444 "${local_path}"
        echo "✅ Downloaded: ${local_path}"
    else
        echo "❌ Failed to download: ${s3_path}"
        return 1
    fi
}

# Function to decode base64 secret to file (ECS only)
decode_secret_to_file() {
    local env_var_name=$1
    local output_path=$2
    local description=$3

    # Get the value from environment variable
    local value=$(eval echo \$${env_var_name})

    if [ -n "$value" ]; then
        echo "📝 Writing ${description}..."
        echo "$value" | base64 -d > "${output_path}"
        chmod 444 "${output_path}"
        echo "✅ Written: ${output_path}"
    else
        echo "⚠️  Warning: ${env_var_name} not set - ${description} will not be available"
    fi
}

if [ "$DEPLOYMENT_ENV" = "ecs" ]; then
    echo ""
    echo "🔐 Step 1: Downloading large files from S3"
    echo "=========================================="

    # Download truststore.jks from S3 (too large for Secrets Manager - 75-99KB)
    # Uses ECS Task Execution Role for authentication
    S3_BUCKET="tulevasecrets"
    ENVIRONMENT="${SPRING_PROFILES_ACTIVE:-staging}"

    download_from_s3 \
        "s3://${S3_BUCKET}/${ENVIRONMENT}/truststore.jks" \
        "/truststore.jks" \
        "SSL/TLS truststore"

    echo ""
    echo "🔓 Step 2: Decoding certificates from Secrets Manager"
    echo "=========================================="

    # Decode binary certificates from base64-encoded environment variables
    # These are injected by ECS from AWS Secrets Manager

    decode_secret_to_file \
        "JWT_KEYSTORE_P12" \
        "/jwt-keystore.p12" \
        "JWT signing keystore"

    decode_secret_to_file \
        "SWEDBANK_GATEWAY_P12" \
        "/swedbank-gateway.p12" \
        "Swedbank Gateway client certificate"

    decode_secret_to_file \
        "PARTNER_PUBLIC_KEY1_PEM" \
        "/partner-public-key1.pem" \
        "Partner public key #1"

    decode_secret_to_file \
        "PARTNER_PUBLIC_KEY2_PEM" \
        "/partner-public-key2.pem" \
        "Partner public key #2"

    echo ""
    echo "📋 Step 3: Verifying certificate files"
    echo "=========================================="

    # Verify all required files exist
    REQUIRED_FILES=(
        "/truststore.jks"
        "/jwt-keystore.p12"
        "/swedbank-gateway.p12"
        "/partner-public-key1.pem"
        "/partner-public-key2.pem"
    )

    ALL_FILES_PRESENT=true
    for file in "${REQUIRED_FILES[@]}"; do
        if [ -f "$file" ]; then
            file_size=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null)
            echo "✓ ${file} (${file_size} bytes)"
        else
            echo "✗ ${file} - MISSING"
            ALL_FILES_PRESENT=false
        fi
    done

    if [ "$ALL_FILES_PRESENT" = false ]; then
        echo ""
        echo "❌ ERROR: Some required certificate files are missing!"
        echo "Cannot start application without all certificates."
        exit 1
    fi

    echo ""
    echo "✅ All certificates ready!"
fi

echo ""
echo "🎯 Starting onboarding-service"
echo "=========================================="
echo "Spring Profile: ${SPRING_PROFILES_ACTIVE}"
echo "Server Port: ${SERVER_PORT:-5000}"

if [ "$DEPLOYMENT_ENV" = "beanstalk" ]; then
    # Elastic Beanstalk: Use original JVM memory configuration
    echo "Deployment: Elastic Beanstalk"

    MEMORY_GC_OPTS="-XX:+PreserveFramePointer"
    MEMORY_GC_OPTS="${MEMORY_GC_OPTS} -XX:+UseContainerSupport"
    MEMORY_GC_OPTS="${MEMORY_GC_OPTS} -XX:MaxRAMPercentage=70.0"
    MEMORY_GC_OPTS="${MEMORY_GC_OPTS} -XX:+CrashOnOutOfMemoryError"
    MEMORY_GC_OPTS="${MEMORY_GC_OPTS} -XX:+HeapDumpOnOutOfMemoryError"
    MEMORY_GC_OPTS="${MEMORY_GC_OPTS} -XX:HeapDumpPath=/tmp/heapdump.hprof"
    MEMORY_GC_OPTS="${MEMORY_GC_OPTS} -XX:+UnlockExperimentalVMOptions"
    MEMORY_GC_OPTS="${MEMORY_GC_OPTS} -XX:+AlwaysActAsServerClassMachine"
    MEMORY_GC_OPTS="${MEMORY_GC_OPTS} -XX:-TieredCompilation"
    MEMORY_GC_OPTS="${MEMORY_GC_OPTS} -Djava.security.egd=file:/dev/urandom"

    JVM_OPTS="-Duser.timezone=${TIMEZONE:-"GMT"} --enable-preview $JVM_OPTS $MEMORY_GC_OPTS"

    echo "JVM Options: ${JVM_OPTS}"
    echo ""

    # Execute the Java application (Beanstalk uses relative classpath)
    exec java $JVM_OPTS -cp app:app/lib/* ee.tuleva.onboarding.OnboardingServiceApplication
else
    # ECS: Use JVM_OPTS from environment variables
    echo "Deployment: ECS Fargate"
    echo "JVM Options: ${JVM_OPTS}"
    echo ""

    # Execute the Java application (ECS uses absolute classpath)
    # All database credentials and secrets are available as environment variables
    # injected by ECS from AWS Secrets Manager
    # Note: Dockerfile unpacks the jar into layers for faster startup
    exec java ${JVM_OPTS} -cp "/app:/app/lib/*" ee.tuleva.onboarding.OnboardingServiceApplication
fi
