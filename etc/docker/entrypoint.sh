#!/bin/sh

# Onboarding-Service Container Entrypoint
# Works for BOTH Elastic Beanstalk and ECS deployments

echo "🚀 Onboarding-Service Container Startup"
echo "Environment: ${SPRING_PROFILES_ACTIVE:-unknown}"

# Detect deployment environment
# ECS sets ECS_CONTAINER_METADATA_URI, Beanstalk does not
if [ -n "$ECS_CONTAINER_METADATA_URI" ]; then
    echo "🐳 Detected: ECS Fargate deployment"
    DEPLOYMENT_ENV="ecs"
else
    echo "📦 Detected: Elastic Beanstalk deployment"
    DEPLOYMENT_ENV="beanstalk"
fi

if [ "$DEPLOYMENT_ENV" = "ecs" ]; then
    echo ""
    echo "🔐 Step 1: Downloading large files from S3"
    echo "=========================================="

    # Download truststore.jks from S3 (too large for Secrets Manager - 75-99KB)
    # Uses ECS Task Execution Role for authentication
    S3_BUCKET="tulevasecrets"
    ENVIRONMENT="${SPRING_PROFILES_ACTIVE:-staging}"

    echo "📥 Downloading SSL/TLS truststore from S3..."
    if aws s3 cp "s3://${S3_BUCKET}/${ENVIRONMENT}/truststore.jks" "/truststore.jks" --region eu-central-1; then
        chmod 444 "/truststore.jks"
        echo "✅ Downloaded: /truststore.jks"
    else
        echo "❌ Failed to download truststore.jks from S3"
        exit 1
    fi

    echo ""
    echo "🔓 Step 2: Decoding certificates from Secrets Manager"
    echo "=========================================="

    # Decode binary certificates from base64-encoded environment variables
    # These are injected by ECS from AWS Secrets Manager

    if [ -n "$JWT_KEYSTORE_P12" ]; then
        echo "📝 Writing JWT signing keystore..."
        echo "$JWT_KEYSTORE_P12" | base64 -d > "/jwt-keystore.p12"
        chmod 444 "/jwt-keystore.p12"
        echo "✅ Written: /jwt-keystore.p12"
    else
        echo "⚠️  Warning: JWT_KEYSTORE_P12 not set"
    fi

    if [ -n "$SWEDBANK_GATEWAY_P12" ]; then
        echo "📝 Writing Swedbank Gateway client certificate..."
        echo "$SWEDBANK_GATEWAY_P12" | base64 -d > "/swedbank-gateway.p12"
        chmod 444 "/swedbank-gateway.p12"
        echo "✅ Written: /swedbank-gateway.p12"
    else
        echo "⚠️  Warning: SWEDBANK_GATEWAY_P12 not set"
    fi

    if [ -n "$PARTNER_PUBLIC_KEY1_PEM" ]; then
        echo "📝 Writing Partner public key #1..."
        echo "$PARTNER_PUBLIC_KEY1_PEM" | base64 -d > "/partner-public-key1.pem"
        chmod 444 "/partner-public-key1.pem"
        echo "✅ Written: /partner-public-key1.pem"
    else
        echo "⚠️  Warning: PARTNER_PUBLIC_KEY1_PEM not set"
    fi

    if [ -n "$PARTNER_PUBLIC_KEY2_PEM" ]; then
        echo "📝 Writing Partner public key #2..."
        echo "$PARTNER_PUBLIC_KEY2_PEM" | base64 -d > "/partner-public-key2.pem"
        chmod 444 "/partner-public-key2.pem"
        echo "✅ Written: /partner-public-key2.pem"
    else
        echo "⚠️  Warning: PARTNER_PUBLIC_KEY2_PEM not set"
    fi

    echo ""
    echo "📋 Step 3: Verifying certificate files"
    echo "=========================================="

    # Verify all required files exist
    ALL_FILES_PRESENT=true
    for file in /truststore.jks /jwt-keystore.p12 /swedbank-gateway.p12 /partner-public-key1.pem /partner-public-key2.pem; do
        if [ -f "$file" ]; then
            file_size=$(stat -c%s "$file" 2>/dev/null || stat -f%z "$file" 2>/dev/null || echo "unknown")
            echo "✓ ${file} (${file_size} bytes)"
        else
            echo "✗ ${file} - MISSING"
            ALL_FILES_PRESENT=false
        fi
    done

    if [ "$ALL_FILES_PRESENT" = "false" ]; then
        echo ""
        echo "❌ ERROR: Some required certificate files are missing!"
        echo "Cannot start application without all certificates."
        exit 1
    fi

    echo ""
    echo "✅ All certificates ready!"
    echo ""
    echo "🎯 Starting onboarding-service (ECS)"
    echo "=========================================="
    echo "Spring Profile: ${SPRING_PROFILES_ACTIVE}"
    echo "Server Port: ${SERVER_PORT:-5000}"
    echo "JVM Options: ${JVM_OPTS}"
    echo ""

    # Execute the Java application (ECS uses absolute classpath)
    # All database credentials and secrets are available as environment variables
    # injected by ECS from AWS Secrets Manager
    exec java ${JVM_OPTS} -cp "/app:/app/lib/*" ee.tuleva.onboarding.OnboardingServiceApplication
else
    # Elastic Beanstalk: Use original JVM memory configuration
    echo ""
    echo "🎯 Starting onboarding-service (Beanstalk)"
    echo "=========================================="

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

    echo "Spring Profile: ${SPRING_PROFILES_ACTIVE}"
    echo "JVM Options: ${JVM_OPTS}"
    echo ""

    # Execute the Java application (Beanstalk uses relative classpath)
    exec java $JVM_OPTS -cp app:app/lib/* ee.tuleva.onboarding.OnboardingServiceApplication
fi
