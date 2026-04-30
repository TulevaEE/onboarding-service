package ee.tuleva.onboarding.investment.event;

public final class NavEventListenerOrder {

  private NavEventListenerOrder() {}

  // Lower values run first
  public static final int TRACKING_DIFFERENCE = 100;
  public static final int LIMIT_CHECK = 200;
}
