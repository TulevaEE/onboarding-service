package ee.tuleva.onboarding.mandate;

@FunctionalInterface
public interface SavingsFundSaverStatus {

  boolean isSaver(String personalCode);
}
