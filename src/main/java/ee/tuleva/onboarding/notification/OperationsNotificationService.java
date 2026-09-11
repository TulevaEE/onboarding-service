package ee.tuleva.onboarding.notification;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface OperationsNotificationService {

  void sendMessage(String message, Channel channel);

  void sendMessage(String message, Channel channel, Severity severity);

  enum Channel {
    AML,
    WITHDRAWALS,
    CAPITAL_TRANSFER,
    INVESTMENT,
    SAVINGS
  }

  enum Severity {
    INFO,
    ERROR
  }
}
