package ee.tuleva.onboarding.investment.transaction;

import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
record PositionCheck(
    List<InputGateFailure> failures, List<AcceptedHoldingDifference> acceptedDifferences) {}
