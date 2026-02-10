package ee.tuleva.onboarding.notification;

public interface OperationsNotificationService {

  void sendMessage(String message, Channel channel);

  enum Channel {
    AML,
    WITHDRAWALS,
    CAPITAL_TRANSFER,
    INVESTMENT,
    SAVINGS
  }
}
