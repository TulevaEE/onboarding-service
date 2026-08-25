package ee.tuleva.onboarding.kyc;

public final class KycCheckPerformedEventOrder {

  private KycCheckPerformedEventOrder() {}

  // Lower values run first. Completing a waiting company reads the aml_check this
  // event produces, so it has to run after that check has been written.
  public static final int PERSIST_AML_CHECK = 100;
  public static final int COMPLETE_WAITING_COMPANIES = 200;
}
