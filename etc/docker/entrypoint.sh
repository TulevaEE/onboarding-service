#!/bin/sh

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


JVM_OPTS="-Duser.timezone=${TIMEZONE:-"GMT"} $JVM_OPTS $MEMORY_GC_OPTS"

exec java $JVM_OPTS -cp app:app/lib/* ee.tuleva.onboarding.OnboardingServiceApplication

