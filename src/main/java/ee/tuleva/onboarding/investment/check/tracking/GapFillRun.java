package ee.tuleva.onboarding.investment.check.tracking;

import java.util.List;

record GapFillRun(List<TrackingDifferenceResult> results, List<GapFailure> failures) {}
