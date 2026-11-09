package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.UNKNOWN;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.email.AtMostOnceEmailSender;
import ee.tuleva.onboarding.notification.email.EmailSendResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustodianOrderEmailSenderTest {

  private static final long BATCH_ID = 42L;
  private static final Instant TIMESTAMP = Instant.parse("2026-01-15T10:00:00Z");
  private static final Map<String, byte[]> EXPORTS = Map.of("sebFundXlsx", new byte[] {1, 2});

  @Mock private AtMostOnceEmailSender orderEmailTransport;
  @Mock private CustodianOrderEmailNotifier notifier;

  private final CustodianOrderEmailProperties properties =
      new CustodianOrderEmailProperties(
          true, List.of("desk@example.com"), List.of("copy@example.com"));
  private final CustodianOrderMessageFactory factory = new CustodianOrderMessageFactory(properties);

  @Test
  void send_emailsTheOrdersOnceAndReportsTheResult() {
    var sender = new CustodianOrderEmailSender(properties, factory, orderEmailTransport, notifier);
    var accepted = new EmailSendResult(SENT, List.of(), null);
    given(orderEmailTransport.send(any())).willReturn(accepted);
    var expected = factory.create(TUV100, TIMESTAMP, EXPORTS);

    boolean sent = sender.send(BATCH_ID, TUV100, TIMESTAMP, EXPORTS);

    assertThat(sent).isTrue();
    verify(orderEmailTransport).send(messageMatching(expected));
    verify(notifier).reportSendResult(eq(BATCH_ID), messageMatching(expected), eq(accepted));
  }

  @Test
  void send_recordsTheAttemptBeforeCallingMandrill_soAStopDuringTheCallStillLeavesARow() {
    var sender = new CustodianOrderEmailSender(properties, factory, orderEmailTransport, notifier);
    var accepted = new EmailSendResult(SENT, List.of(), null);
    given(orderEmailTransport.send(any())).willReturn(accepted);
    var expected = factory.create(TUV100, TIMESTAMP, EXPORTS);

    sender.send(BATCH_ID, TUV100, TIMESTAMP, EXPORTS);

    var inOrder = inOrder(notifier, orderEmailTransport);
    inOrder.verify(notifier).reportSendAttempt(eq(BATCH_ID), messageMatching(expected));
    inOrder.verify(orderEmailTransport).send(messageMatching(expected));
    inOrder
        .verify(notifier)
        .reportSendResult(eq(BATCH_ID), messageMatching(expected), eq(accepted));
  }

  @Test
  void send_reportsTheResultAndCountsTheEmailAsNotSentWhenTheResultIsUnknown() {
    var sender = new CustodianOrderEmailSender(properties, factory, orderEmailTransport, notifier);
    var unknown = new EmailSendResult(UNKNOWN, List.of(), "SocketTimeoutException: Read timed out");
    given(orderEmailTransport.send(any())).willReturn(unknown);

    boolean sent = sender.send(BATCH_ID, TUV100, TIMESTAMP, EXPORTS);

    assertThat(sent).isFalse();
    verify(notifier).reportSendResult(eq(BATCH_ID), any(), eq(unknown));
  }

  @Test
  void send_doesNothingWhenDisabled() {
    var disabled =
        new CustodianOrderEmailProperties(
            false, List.of("desk@example.com"), List.of("copy@example.com"));
    var sender =
        new CustodianOrderEmailSender(
            disabled, new CustodianOrderMessageFactory(disabled), orderEmailTransport, notifier);

    boolean sent = sender.send(BATCH_ID, TUV100, TIMESTAMP, EXPORTS);

    assertThat(sent).isFalse();
    verifyNoInteractions(orderEmailTransport, notifier);
  }

  @Test
  void send_doesNothingWhenNoRecipientsConfigured() {
    var noRecipients = new CustodianOrderEmailProperties(true, List.of(), List.of());
    var sender =
        new CustodianOrderEmailSender(
            noRecipients,
            new CustodianOrderMessageFactory(noRecipients),
            orderEmailTransport,
            notifier);

    boolean sent = sender.send(BATCH_ID, TUV100, TIMESTAMP, EXPORTS);

    assertThat(sent).isFalse();
    verifyNoInteractions(orderEmailTransport, notifier);
  }

  @Test
  void send_doesNothingWhenAllExportsEmpty() {
    var sender = new CustodianOrderEmailSender(properties, factory, orderEmailTransport, notifier);

    boolean sent = sender.send(BATCH_ID, TUV100, TIMESTAMP, Map.of());

    assertThat(sent).isFalse();
    verifyNoInteractions(orderEmailTransport, notifier);
  }

  private static MandrillMessage messageMatching(MandrillMessage expected) {
    return argThat(
        actual ->
            actual.getSubject().equals(expected.getSubject())
                && actual.getTo().size() == expected.getTo().size()
                && actual.getAttachments().size() == expected.getAttachments().size());
  }
}
