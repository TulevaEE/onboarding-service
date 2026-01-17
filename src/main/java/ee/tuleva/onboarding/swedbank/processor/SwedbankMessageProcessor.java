package ee.tuleva.onboarding.swedbank.processor;

import ee.tuleva.onboarding.banking.message.BankMessageType;
import java.time.ZoneId;

public interface SwedbankMessageProcessor {
  void processMessage(String rawResponse, BankMessageType messageType, ZoneId timezone);

  boolean supports(BankMessageType messageType);
}
