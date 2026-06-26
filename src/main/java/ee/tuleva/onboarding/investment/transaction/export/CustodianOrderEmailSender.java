package ee.tuleva.onboarding.investment.transaction.export;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.notification.email.EmailService;
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
  private final EmailService emailService;

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
    boolean sent = emailService.sendSystemEmail(message);
    if (sent) {
      log.info(
          "Custodian order email sent: fund={}, to={}, cc={}, attachments={}",
          fund,
          properties.to(),
          properties.cc(),
          message.getAttachments().size());
    } else {
      log.error("Custodian order email failed: fund={}, to={}", fund, properties.to());
    }
    return sent;
  }
}
