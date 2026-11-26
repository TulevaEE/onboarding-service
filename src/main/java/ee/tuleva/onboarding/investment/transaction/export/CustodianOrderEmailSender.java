package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.export.OrderEmail.FT;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.UNKNOWN;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.email.AtMostOnceEmailSender;
import ee.tuleva.onboarding.notification.email.EmailSendResult;
import java.util.List;
import java.util.stream.Stream;
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

  public Runnable prepareSend(OrderDay day) {
    if (!properties.enabled()) {
      log.info("Order emails not sent (inert): batchIds={}", day.batchIds());
      return () -> {};
    }
    List<OrderEmail> emails = OrderEmail.carrying(day.brokerFiles());
    if (emails.isEmpty()) {
      log.warn("Order emails not sent: no order files, batchIds={}", day.batchIds());
      return () -> {};
    }
    emails.forEach(properties::requireConfigured);
    if (emails.contains(FT)) {
      CustodianOrderMessageFactory.requireOneFtOrderType(day);
    }
    List<PreparedEmail> prepared = emails.stream().map(email -> prepare(day, email)).toList();
    List<Runnable> notBuiltPostsFirstThenSends =
        Stream.concat(
                prepared.stream().filter(PreparedEmail::notBuilt),
                prepared.stream().filter(email -> !email.notBuilt()))
            .map(PreparedEmail::afterCommit)
            .toList();
    return () -> notBuiltPostsFirstThenSends.forEach(CustodianOrderEmailSender::runOnItsOwn);
  }

  private record PreparedEmail(Runnable afterCommit, Built built) {

    boolean notBuilt() {
      return built == Built.NO;
    }
  }

  private enum Built {
    YES,
    NO
  }

  private PreparedEmail prepare(OrderDay day, OrderEmail email) {
    String emailKey = day.emailKey(email);
    MandrillMessage message;
    try {
      message = messageFactory.create(day, email);
    } catch (RuntimeException e) {
      log.error(
          "Order email could not be built, recording it as not sent with the batches:"
              + " emailKey={}",
          emailKey,
          e);
      var subject = messageFactory.describe(day, email);
      var problem = e.toString();
      notifier.recordNotBuilt(emailKey, subject, problem);
      return new PreparedEmail(() -> notifier.postNotBuilt(emailKey, subject, problem), Built.NO);
    }
    notifier.reportSendAttempt(emailKey, message);
    return new PreparedEmail(() -> send(emailKey, message), Built.YES);
  }

  private static void runOnItsOwn(Runnable send) {
    try {
      send.run();
    } catch (RuntimeException e) {
      log.error("Order email step failed, going on with the next email", e);
    }
  }

  private void send(String emailKey, MandrillMessage message) {
    var result = sent(message);
    notifier.reportSendResult(emailKey, message, result);
    if (result.outcome() == SENT) {
      log.info(
          "Order email sent: emailKey={}, attachments={}",
          emailKey,
          message.getAttachments().size());
    } else {
      log.error(
          "Order email not confirmed as sent: emailKey={}, outcome={}, problem={}",
          emailKey,
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
}
