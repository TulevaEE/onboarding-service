package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderType.MOC;
import static ee.tuleva.onboarding.investment.transaction.OrderType.NAV;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.FT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.FT_ETF;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.SEB_FUND;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.UUID_WORKBOOK;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.UNKNOWN;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderSender;
import ee.tuleva.onboarding.investment.transaction.OrderType;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRefusedException;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.notification.email.AtMostOnceEmailSender;
import ee.tuleva.onboarding.notification.email.EmailSendResult;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CustodianOrderEmailSenderTest {

  private static final String FT_KEY = "FT:12,13";
  private static final String SEB_FUND_KEY = "SEB_FUND:12,13";
  private static final String UUID_KEY = "UUID:12,13";
  private static final String FT_SUBJECT = "Tuleva ETF orders 26.11.2026 – MOC";
  private static final String SEB_FUND_SUBJECT = "Tuleva fondide tehingukorraldused 26.11.2026";
  private static final String UUID_SUBJECT = "Tehingukorralduste UUID 26.11.2026";

  @Mock private AtMostOnceEmailSender orderEmailTransport;
  @Mock private CustodianOrderEmailNotifier notifier;
  @Mock private CustodianOrderMessageFactory brokenFactory;

  private final CustodianOrderEmailProperties properties = configured(true);
  private final CustodianOrderMessageFactory factory =
      spy(new CustodianOrderMessageFactory(properties));

  @Test
  void prepareSend_recordsOneAttemptPerVenueWithOrders_andTheStepAfterTheCommitSendsEachOnce() {
    var sender = new CustodianOrderEmailSender(properties, factory, orderEmailTransport, notifier);
    var accepted = new EmailSendResult(SENT, List.of(), null);
    given(orderEmailTransport.send(any())).willReturn(accepted);

    var sendTheOrders = sender.prepareSend(ftAndFundDay(MOC));

    then(notifier).should().reportSendAttempt(eq(FT_KEY), messageWithSubject(FT_SUBJECT));
    then(notifier)
        .should()
        .reportSendAttempt(eq(SEB_FUND_KEY), messageWithSubject(SEB_FUND_SUBJECT));
    then(notifier).should().reportSendAttempt(eq(UUID_KEY), messageWithSubject(UUID_SUBJECT));
    then(orderEmailTransport).shouldHaveNoInteractions();

    sendTheOrders.run();

    then(orderEmailTransport).should().send(messageWithSubject(FT_SUBJECT));
    then(orderEmailTransport).should().send(messageWithSubject(SEB_FUND_SUBJECT));
    then(orderEmailTransport).should().send(messageWithSubject(UUID_SUBJECT));
    then(notifier)
        .should()
        .reportSendResult(eq(FT_KEY), messageWithSubject(FT_SUBJECT), eq(accepted));
  }

  @Test
  void prepareSend_aVenueWithoutOrdersGetsNoEmail() {
    var sender = new CustodianOrderEmailSender(properties, factory, orderEmailTransport, notifier);
    var fundsOnly =
        new OrderDay(
            List.of(12L, 13L),
            LocalDate.parse("2026-11-26"),
            Instant.parse("2026-11-26T08:50:00Z"),
            FUND_MANAGER,
            List.of(order(TUK00, BUY, FUND, SEB, MOC)),
            Map.of(SEB_FUND, new byte[] {1, 2}));

    sender.prepareSend(fundsOnly);

    then(notifier).should().reportSendAttempt(eq(SEB_FUND_KEY), any());
    then(notifier).shouldHaveNoMoreInteractions();
  }

  @Test
  void
      prepareSend_recordsNothingAndSendsNothing_whileTheOrderEmailsAreDisabledOrTheDayHasNoFiles() {
    var disabled = configured(false);
    var inert =
        new CustodianOrderEmailSender(
            disabled, new CustodianOrderMessageFactory(disabled), orderEmailTransport, notifier);
    var sender = new CustodianOrderEmailSender(properties, factory, orderEmailTransport, notifier);
    var noFiles =
        new OrderDay(
            List.of(12L),
            LocalDate.parse("2026-11-26"),
            Instant.parse("2026-11-26T08:50:00Z"),
            FUND_MANAGER,
            List.of(),
            Map.of());

    inert.prepareSend(ftAndFundDay(MOC)).run();
    sender.prepareSend(noFiles).run();

    then(notifier).shouldHaveNoInteractions();
    then(orderEmailTransport).shouldHaveNoInteractions();
  }

  @Test
  void prepareSend_whenEnabled_aVenueWithOrdersButNoRecipients_stopsTheWholeSendBeforeTheCommit() {
    var withoutFtRecipients =
        new CustodianOrderEmailProperties(
            true, List.of("funds@tuleva.ee"), null, null, properties.sebFund(), properties.uuid());
    var sender =
        new CustodianOrderEmailSender(
            withoutFtRecipients,
            new CustodianOrderMessageFactory(withoutFtRecipients),
            orderEmailTransport,
            notifier);

    assertThatThrownBy(() -> sender.prepareSend(ftAndFundDay(MOC)))
        .isInstanceOf(ResponseStatusException.class);
    then(notifier).shouldHaveNoInteractions();
  }

  @Test
  void prepareSend_ftOrdersOfMixedOrderTypes_areRefusedBeforeTheCommit() {
    var sender = new CustodianOrderEmailSender(properties, factory, orderEmailTransport, notifier);
    var mixed =
        new OrderDay(
            List.of(12L, 13L),
            LocalDate.parse("2026-11-26"),
            Instant.parse("2026-11-26T08:50:00Z"),
            FUND_MANAGER,
            List.of(order(TUK75, BUY, ETF, FT, MOC), order(TUV100, BUY, ETF, FT, NAV)),
            Map.of(FT_ETF, new byte[] {5, 6}, UUID_WORKBOOK, new byte[] {7, 8}));

    assertThatThrownBy(() -> sender.prepareSend(mixed))
        .isInstanceOf(TransactionBatchRefusedException.class);
    then(notifier).shouldHaveNoInteractions();
  }

  @Test
  void
      prepareSend_whenAMessageCannotBeBuilt_recordsItAsNotSentWithTheBatches_andPostsItFirstAfterTheCommit() {
    var sender =
        new CustodianOrderEmailSender(properties, brokenFactory, orderEmailTransport, notifier);
    var day = ftOnlyDay();
    given(brokenFactory.create(day, OrderEmail.FT)).willReturn(message(FT_SUBJECT));
    given(brokenFactory.create(day, OrderEmail.UUID))
        .willThrow(new IllegalStateException("the UUID file has no content"));
    given(brokenFactory.describe(day, OrderEmail.UUID)).willReturn("UUID workbook 26.11.2026");
    given(orderEmailTransport.send(any())).willReturn(new EmailSendResult(SENT, List.of(), null));

    var sendTheOrders = sender.prepareSend(day);

    then(notifier)
        .should()
        .recordNotBuilt(
            UUID_KEY,
            "UUID workbook 26.11.2026",
            "java.lang.IllegalStateException: the UUID file has no content");
    then(notifier).should(never()).reportSendAttempt(eq(UUID_KEY), any());

    sendTheOrders.run();

    var notBuiltPostedBeforeTheSlowSends = inOrder(notifier, orderEmailTransport);
    then(notifier)
        .should(notBuiltPostedBeforeTheSlowSends)
        .postNotBuilt(
            UUID_KEY,
            "UUID workbook 26.11.2026",
            "java.lang.IllegalStateException: the UUID file has no content");
    then(orderEmailTransport)
        .should(notBuiltPostedBeforeTheSlowSends)
        .send(messageWithSubject(FT_SUBJECT));
  }

  @Test
  void
      prepareSend_failingToRecordTheAttempt_failsTheBatchTransaction_sinceTheRowIsInTheSameDatabase() {
    var sender = new CustodianOrderEmailSender(properties, factory, orderEmailTransport, notifier);
    willThrow(new DataAccessResourceFailureException("database is down"))
        .given(notifier)
        .reportSendAttempt(eq(FT_KEY), any());

    assertThatThrownBy(() -> sender.prepareSend(ftOnlyDay()))
        .isInstanceOf(DataAccessResourceFailureException.class);
  }

  @Test
  void theSendStep_reportsAnUnexpectedTransportFailureAsUnknown_sinceTheEmailMayHaveGoneOut() {
    var sender = new CustodianOrderEmailSender(properties, factory, orderEmailTransport, notifier);
    given(orderEmailTransport.send(any())).willThrow(new IllegalStateException("a bug"));

    sender.prepareSend(ftOnlyDay()).run();

    then(notifier)
        .should()
        .reportSendResult(
            eq(FT_KEY),
            messageWithSubject(FT_SUBJECT),
            argThat(result -> result.outcome() == UNKNOWN));
  }

  @Test
  void theSendStep_oneEmailsReportFailing_stillSendsTheNextEmail() {
    var sender = new CustodianOrderEmailSender(properties, factory, orderEmailTransport, notifier);
    given(orderEmailTransport.send(any())).willReturn(new EmailSendResult(SENT, List.of(), null));
    willThrow(new IllegalStateException("a bug in the report"))
        .given(notifier)
        .reportSendResult(eq(FT_KEY), any(), any());

    sender.prepareSend(ftOnlyDay()).run();

    then(orderEmailTransport).should().send(messageWithSubject(UUID_SUBJECT));
  }

  private static final OrderSender FUND_MANAGER =
      new OrderSender("fundmanager@tuleva.ee", "Fund Manager");

  private static CustodianOrderEmailProperties configured(boolean enabled) {
    return new CustodianOrderEmailProperties(
        enabled,
        List.of("funds@tuleva.ee"),
        new CustodianOrderEmailProperties.Venue(List.of("desk@ft.example"), List.of(), null, null),
        null,
        new CustodianOrderEmailProperties.Venue(
            List.of("funds@seb.example"),
            List.of(),
            "Tuleva fondide tehingukorraldused {date}",
            "Manuses on tehingukorraldused."),
        new CustodianOrderEmailProperties.Venue(
            List.of("custody@seb.example"), List.of(), null, null));
  }

  private static OrderDay ftAndFundDay(OrderType ftOrderType) {
    return new OrderDay(
        List.of(12L, 13L),
        LocalDate.parse("2026-11-26"),
        Instant.parse("2026-11-26T08:50:00Z"),
        FUND_MANAGER,
        List.of(order(TUK75, BUY, ETF, FT, ftOrderType), order(TUK00, BUY, FUND, SEB, MOC)),
        Map.of(
            FT_ETF, new byte[] {5, 6},
            SEB_FUND, new byte[] {1, 2},
            UUID_WORKBOOK, new byte[] {7, 8}));
  }

  private static OrderDay ftOnlyDay() {
    return new OrderDay(
        List.of(12L, 13L),
        LocalDate.parse("2026-11-26"),
        Instant.parse("2026-11-26T08:50:00Z"),
        FUND_MANAGER,
        List.of(order(TUK75, BUY, ETF, FT, MOC)),
        Map.of(FT_ETF, new byte[] {5, 6}, UUID_WORKBOOK, new byte[] {7, 8}));
  }

  private static TransactionOrder order(
      TulevaFund fund,
      ee.tuleva.onboarding.investment.transaction.TransactionType side,
      InstrumentType instrumentType,
      OrderVenue venue,
      OrderType orderType) {
    return TransactionOrder.builder()
        .fund(fund)
        .instrumentIsin("IE00TEST0001")
        .transactionType(side)
        .instrumentType(instrumentType)
        .orderVenue(venue)
        .orderType(orderType)
        .orderAmount(new BigDecimal("1000"))
        .orderUuid(UUID.randomUUID())
        .expectedSettlementDate(LocalDate.parse("2026-11-28"))
        .build();
  }

  private static MandrillMessage message(String subject) {
    var message = new MandrillMessage();
    message.setSubject(subject);
    return message;
  }

  private static MandrillMessage messageWithSubject(String subject) {
    return argThat(message -> message.getSubject().equals(subject));
  }
}
