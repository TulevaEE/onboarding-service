package ee.tuleva.onboarding.investment.transaction.portfolio;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.ArrayList;
import java.util.List;

class RecordingNotificationService implements OperationsNotificationService {

  record Sent(String message, Channel channel) {}

  private final List<Sent> sent = new ArrayList<>();
  private boolean failNext;

  void failNext() {
    failNext = true;
  }

  List<Sent> sent() {
    return sent;
  }

  @Override
  public void sendMessage(String message, Channel channel) {
    if (failNext) {
      failNext = false;
      throw new IllegalStateException("Slack unavailable");
    }
    sent.add(new Sent(message, channel));
  }

  @Override
  public void sendMessage(String message, Channel channel, Severity severity) {
    sendMessage(message, channel);
  }
}
