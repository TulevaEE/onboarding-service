package ee.tuleva.onboarding.investment.check.limit;

sealed interface OwnershipAssessment permits OwnershipBreach, UnverifiedHolding, LeftOutHolding {}
