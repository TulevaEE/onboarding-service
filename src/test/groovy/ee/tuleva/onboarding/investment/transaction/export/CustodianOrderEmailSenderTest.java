package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.UNKNOWN;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

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
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class CustodianOrderEmailSenderTest {

  private static final long BATCH_ID = 42L;
  private static final Instant TIMESTAMP = Instant.parse("2026-01-15T10:00:00Z");
  private static final Map<String, byte[]> EXPORTS = Map.of("sebFundXlsx", new byte[] {1, 2});
  private static final String SUBJECT = "TUV100 ostu-/müügikorraldused 15.01.2026";

  @Mock private AtMostOnceEmailSender orderEmailTransport;
  @Mock private CustodianOrderEmailNotifier notifier;
  @Mock private CustodianOrderMessageFactory brokenFactory;

  private final CustodianOrderEmailProperties properties =
      new CustodianOrderEmailProperties(
          true, List.of("desk@example.com"), List.of("copy@example.com"));
  private final CustodianOrderMessageFactory factory =
      spy(new CustodianOrderMessageFactory(properties));

  @Test
  void prepareSend_recordsTheAttempt_andTheStepAfterTheCommitSendsThatSameMessageOnce() {
    var sender = new CustodianOrderEmailSender(properties, factory, orderEmailTransport, notifier);
    var accepted = new EmailSendResult(SENT, List.of(), null);
    given(orderEmailTransport.send(any())).willReturn(accepted);

    var sendTheOrders = sender.prepareSend(BATCH_ID, TUV100, TIMESTAMP, EXPORTS);

    then(notifier).should().reportSendAttempt(eq(BATCH_ID), messageWithSubject(SUBJECT));
    then(orderEmailTransport).shouldHaveNoInteractions();

    sendTheOrders.run();

    then(factory).should(times(1)).create(TUV100, TIMESTAMP, EXPORTS);
    then(orderEmailTransport).should().send(messageWithSubject(SUBJECT));
    then(notifier)
        .should()
        .reportSendResult(eq(BATCH_ID), messageWithSubject(SUBJECT), eq(accepted));
  }

  @Test
  void prepareSend_recordsNothingAndSendsNothing_whenTheOrdersWillNotBeEmailed() {
    var disabled =
        new CustodianOrderEmailProperties(
            false, List.of("desk@example.com"), List.of("copy@example.com"));
    var noRecipients = new CustodianOrderEmailProperties(true, List.of(), List.of());
    var inert =
        new CustodianOrderEmailSender(
            disabled, new CustodianOrderMessageFactory(disabled), orderEmailTransport, notifier);
    var withoutRecipients =
        new CustodianOrderEmailSender(
            noRecipients,
            new CustodianOrderMessageFactory(noRecipients),
            orderEmailTransport,
            notifier);
    var sender = new CustodianOrderEmailSender(properties, factory, orderEmailTransport, notifier);

    inert.prepareSend(BATCH_ID, TUV100, TIMESTAMP, EXPORTS).run();
    withoutRecipients.prepareSend(BATCH_ID, TUV100, TIMESTAMP, EXPORTS).run();
    sender.prepareSend(BATCH_ID, TUV100, TIMESTAMP, Map.of()).run();

    then(notifier).shouldHaveNoInteractions();
    then(orderEmailTransport).shouldHaveNoInteractions();
  }

  @Test
  void
      prepareSend_whenTheMessageCannotBeBuilt_recordsItAsNotSentWithTheBatch_andPostsItAfterTheCommit() {
    var sender =
        new CustodianOrderEmailSender(properties, brokenFactory, orderEmailTransport, notifier);
    given(brokenFactory.create(TUV100, TIMESTAMP, EXPORTS))
        .willThrow(new IllegalStateException("an export has no file name"));
    given(brokenFactory.subject(TUV100, TIMESTAMP)).willReturn(SUBJECT);

    var sendTheOrders = sender.prepareSend(BATCH_ID, TUV100, TIMESTAMP, EXPORTS);

    then(notifier)
        .should()
        .recordNotBuilt(
            BATCH_ID, SUBJECT, "java.lang.IllegalStateException: an export has no file name");
    then(notifier).shouldHaveNoMoreInteractions();

    sendTheOrders.run();

    then(notifier)
        .should()
        .postNotBuilt(
            BATCH_ID, SUBJECT, "java.lang.IllegalStateException: an export has no file name");
    then(notifier).should(never()).reportSendAttempt(anyLong(), any());
    then(orderEmailTransport).shouldHaveNoInteractions();
  }

  @Test
  void
      prepareSend_failingToRecordTheAttempt_failsTheBatchTransaction_sinceTheRowIsInTheSameDatabase() {
    var sender = new CustodianOrderEmailSender(properties, factory, orderEmailTransport, notifier);
    willThrow(new DataAccessResourceFailureException("database is down"))
        .given(notifier)
        .reportSendAttempt(eq(BATCH_ID), any());

    assertThatThrownBy(() -> sender.prepareSend(BATCH_ID, TUV100, TIMESTAMP, EXPORTS))
        .isInstanceOf(DataAccessResourceFailureException.class);
  }

  @Test
  void theSendStep_reportsAnUnexpectedTransportFailureAsUnknown_sinceTheEmailMayHaveGoneOut() {
    var sender = new CustodianOrderEmailSender(properties, factory, orderEmailTransport, notifier);
    given(orderEmailTransport.send(any())).willThrow(new IllegalStateException("a bug"));

    sender.prepareSend(BATCH_ID, TUV100, TIMESTAMP, EXPORTS).run();

    then(notifier)
        .should()
        .reportSendResult(
            eq(BATCH_ID),
            messageWithSubject(SUBJECT),
            argThat(result -> result.outcome() == UNKNOWN));
  }

  private static MandrillMessage messageWithSubject(String subject) {
    return argThat(message -> message.getSubject().equals(subject));
  }
}
