package ee.tuleva.onboarding.auth.browser;

public enum PushLogin {
  SMART_ID_NOTIFICATION("smart_id_notification_login_started_at");

  final String startedAtColumn;

  PushLogin(String startedAtColumn) {
    this.startedAtColumn = startedAtColumn;
  }
}
