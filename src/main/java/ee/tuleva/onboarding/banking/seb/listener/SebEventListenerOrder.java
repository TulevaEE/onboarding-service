package ee.tuleva.onboarding.banking.seb.listener;

public final class SebEventListenerOrder {

  private SebEventListenerOrder() {}

  // Lower values run first
  public static final int PROCESS_STATEMENT = 100;
  public static final int RECONCILE = 200;
}
