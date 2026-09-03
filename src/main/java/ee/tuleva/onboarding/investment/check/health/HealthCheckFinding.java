package ee.tuleva.onboarding.investment.check.health;

import ee.tuleva.onboarding.tulevafund.TulevaFund;

public record HealthCheckFinding(
    TulevaFund fund, HealthCheckType checkType, HealthCheckSeverity severity, String message) {}
