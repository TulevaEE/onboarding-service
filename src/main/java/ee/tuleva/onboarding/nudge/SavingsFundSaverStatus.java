package ee.tuleva.onboarding.nudge;

@FunctionalInterface
public interface SavingsFundSaverStatus {

  boolean savesFor(NudgeAccount account);
}
