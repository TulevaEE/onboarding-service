package ee.tuleva.onboarding.swedbank.processor;

import ee.tuleva.onboarding.banking.message.BankMessageType;

public interface SwedbankMessageProcessor {
  void processMessage(String rawResponse, BankMessageType messageType);

  boolean supports(BankMessageType messageType);
}
