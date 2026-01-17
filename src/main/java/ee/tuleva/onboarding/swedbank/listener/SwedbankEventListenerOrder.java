package ee.tuleva.onboarding.swedbank.listener;

public final class SwedbankEventListenerOrder {

  private SwedbankEventListenerOrder() {}

  // Lower values run first
  public static final int PROCESS_STATEMENT = 100;
  public static final int RECONCILE = 200;
}
