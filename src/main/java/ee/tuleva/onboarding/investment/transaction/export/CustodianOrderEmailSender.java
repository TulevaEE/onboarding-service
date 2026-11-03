package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT;

import ee.tuleva.onboarding.notification.email.AtMostOnceEmailSender;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustodianOrderEmailSender {

  private final CustodianOrderEmailProperties properties;
  private final CustodianOrderMessageFactory messageFactory;
  private final AtMostOnceEmailSender custodianOrderEmailTransport;
  private final CustodianOrderEmailNotifier notifier;

  public boolean send(TulevaFund fund, Instant timestamp, Map<String, byte[]> exports) {
    if (!properties.isSendable()) {
      log.info(
          "Custodian order email not sent (inert): enabled={}, recipients={}, fund={}",
          properties.enabled(),
          properties.to().size(),
          fund);
      return false;
    }
    boolean hasContent =
        exports.values().stream().anyMatch(bytes -> bytes != null && bytes.length > 0);
    if (!hasContent) {
      log.warn("Custodian order email not sent: no export content, fund={}", fund);
      return false;
    }

    var message = messageFactory.create(fund, timestamp, exports);
    var result = custodianOrderEmailTransport.send(message);
    notifier.reportSendResult(message, result);
    if (result.outcome() != SENT) {
      log.error(
          "Custodian order email not confirmed as sent: fund={}, outcome={}, problem={}",
          fund,
          result.outcome(),
          result.problem());
      return false;
    }
    log.info(
        "Custodian order email sent: fund={}, attachments={}",
        fund,
        message.getAttachments().size());
    return true;
  }
}
