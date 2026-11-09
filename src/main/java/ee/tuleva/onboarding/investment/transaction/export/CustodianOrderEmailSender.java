package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.UNKNOWN;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.email.AtMostOnceEmailSender;
import ee.tuleva.onboarding.notification.email.EmailSendResult;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Instant;
import java.util.List;
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

  public Runnable prepareSend(
      long batchId, TulevaFund fund, Instant timestamp, Map<String, byte[]> exports) {
    if (!properties.isSendable()) {
      log.info(
          "Custodian order email not sent (inert): enabled={}, recipients={}, fund={}",
          properties.enabled(),
          properties.to().size(),
          fund);
      return () -> {};
    }
    if (!hasContent(exports)) {
      log.warn("Custodian order email not sent: no export content, fund={}", fund);
      return () -> {};
    }
    MandrillMessage message;
    try {
      message = messageFactory.create(fund, timestamp, exports);
    } catch (RuntimeException e) {
      log.error(
          "Custodian order email could not be built, recording it as not sent with the batch:"
              + " batchId={}, fund={}",
          batchId,
          fund,
          e);
      var subject = messageFactory.subject(fund, timestamp);
      var problem = e.toString();
      notifier.recordNotBuilt(batchId, subject, problem);
      return () -> notifier.postNotBuilt(batchId, subject, problem);
    }
    notifier.reportSendAttempt(batchId, message);
    return () -> send(batchId, fund, message);
  }

  private void send(long batchId, TulevaFund fund, MandrillMessage message) {
    var result = sent(message);
    notifier.reportSendResult(batchId, message, result);
    if (result.outcome() == SENT) {
      log.info(
          "Custodian order email sent: fund={}, attachments={}",
          fund,
          message.getAttachments().size());
    } else {
      log.error(
          "Custodian order email not confirmed as sent: fund={}, outcome={}, problem={}",
          fund,
          result.outcome(),
          result.problem());
    }
  }

  private EmailSendResult sent(MandrillMessage message) {
    try {
      return custodianOrderEmailTransport.send(message);
    } catch (RuntimeException e) {
      return new EmailSendResult(UNKNOWN, List.of(), "The send failed unexpectedly: " + e);
    }
  }

  private static boolean hasContent(Map<String, byte[]> exports) {
    return exports.values().stream().anyMatch(bytes -> bytes != null && bytes.length > 0);
  }
}
