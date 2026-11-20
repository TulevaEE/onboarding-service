package ee.tuleva.onboarding.investment.transaction;

import org.jspecify.annotations.NullMarked;

@NullMarked
record Netted<T>(T before, T after) {}
