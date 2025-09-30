package ee.tuleva.onboarding.swedbank.processor;

public interface SwedbankMessageProcessor {
  void processMessage(String rawResponse, SwedbankMessageType messageType);

  boolean supports(SwedbankMessageType messageType);
}
