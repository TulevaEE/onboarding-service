package ee.tuleva.onboarding.nudge;

public interface RecurringContributionStatus {

  boolean thirdPillar(String personalCode);

  boolean savingsFund(NudgeAccount account);
}
