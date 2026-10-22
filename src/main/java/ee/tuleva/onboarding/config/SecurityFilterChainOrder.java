package ee.tuleva.onboarding.config;

public final class SecurityFilterChainOrder {

  public static final int AUTHORIZATION_SERVER = 1;
  public static final int CONNECTED_APPS = 2;
  public static final int WEB_APP = 3;

  private SecurityFilterChainOrder() {}
}
