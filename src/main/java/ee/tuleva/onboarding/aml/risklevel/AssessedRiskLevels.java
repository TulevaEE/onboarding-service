package ee.tuleva.onboarding.aml.risklevel;

import java.time.Instant;
import java.util.List;

record AssessedRiskLevels(Instant assessedAt, List<RiskLevelResult> results) {}
