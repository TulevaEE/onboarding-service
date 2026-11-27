package ee.tuleva.onboarding.investment.transaction.export;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderType.MOC;
import static ee.tuleva.onboarding.investment.transaction.OrderType.NAV;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.FT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.FT_ETF;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.SEB_FUND;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.UUID_WORKBOOK;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderSender;
import ee.tuleva.onboarding.investment.transaction.OrderType;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRefusedException;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CustodianOrderMessageFactoryTest {

  private static final LocalDate TRADE_DATE = LocalDate.parse("2026-11-26");
  private static final Instant TEN_TO_ELEVEN_IN_TALLINN = Instant.parse("2026-11-26T08:50:00Z");
  private static final LocalDate SETTLEMENT_DATE = LocalDate.parse("2026-11-28");
  private static final OrderSender FUND_MANAGER =
      new OrderSender("fundmanager@tuleva.ee", "Fund Manager");

  private final CustodianOrderEmailProperties properties =
      new CustodianOrderEmailProperties(
          true,
          List.of("funds@tuleva.ee", "portfolio@tuleva.ee"),
          new CustodianOrderEmailProperties.Venue(
              List.of("desk@ft.example"), List.of("custody@seb.example"), null, null),
          new CustodianOrderEmailProperties.Venue(
              List.of("equities@seb.example"),
              List.of(),
              "Tuleva ETF orders {date}",
              "Please find attached Tuleva orders for today to be executed at MOC. Please confirm!"),
          new CustodianOrderEmailProperties.Venue(
              List.of("funds@seb.example", "custody@seb.example"),
              List.of(),
              "Tuleva fund orders {date}",
              "Please find attached Tuleva fund orders. Please confirm the receipt."),
          new CustodianOrderEmailProperties.Venue(
              List.of("custody@seb.example"), List.of(), null, null));

  private final CustodianOrderMessageFactory factory = new CustodianOrderMessageFactory(properties);

  @Test
  void theFtEmail_comesFromTheSenderWithTheTextOfTheDashboardDesign_leavingTheDatesToTheFile() {
    var day =
        day(
            order(TUK75, "IE00FT0001", SELL, ETF, FT, MOC, "1234567.891"),
            order(TUV100, "IE00FT0001", SELL, ETF, FT, MOC, "200000"),
            order(TUK00, "LU00SEB001", BUY, FUND, SEB, MOC, "75000"));

    var message = factory.create(day, OrderEmail.FT);

    assertThat(message.getFromEmail()).isEqualTo("fundmanager@tuleva.ee");
    assertThat(message.getFromName()).isEqualTo("Fund Manager");
    assertThat(message.getSubject()).isEqualTo("Tuleva ETF orders 26.11.2026 – MOC");
    assertThat(message.getText())
        .isEqualTo(
            """
            Hello,

            Please find attached our ETF orders for 26.11.2026: FT_ETF_orders_26112026.xlsx.

            Order type: Market on Close (MOC) for all orders.
            Approximate total: EUR 1,434,567.89.

            Please confirm receipt by replying to all.

            Kind regards,
            Fund Manager
            Tuleva""");
    assertThat(message.getHtml()).isNull();
  }

  @Test
  void theFtEmail_namesNavOrdersAsNavInItsSubjectAndText() {
    var day = day(order(TUK75, "IE00FT0001", BUY, ETF, FT, NAV, "1000"));

    var message = factory.create(day, OrderEmail.FT);

    assertThat(message.getSubject()).isEqualTo("Tuleva ETF orders 26.11.2026 – NAV");
    assertThat(message.getText()).contains("Order type: NAV for all orders.");
  }

  @Test
  void theFtEmail_goesToFt_withTheCommonCopiesTheVenuesCopiesAndTheSenderInCc() {
    var message =
        factory.create(day(order(TUK75, "IE00FT0001", BUY, ETF, FT, MOC, "1000")), OrderEmail.FT);

    assertThat(message.getTo())
        .extracting(Recipient::getEmail, Recipient::getType)
        .containsExactly(
            tuple("desk@ft.example", TO),
            tuple("funds@tuleva.ee", CC),
            tuple("portfolio@tuleva.ee", CC),
            tuple("custody@seb.example", CC),
            tuple("fundmanager@tuleva.ee", CC));
  }

  @Test
  void theFtEmail_carriesOnlyTheFtFile_underItsGasName() {
    var day = day(order(TUK75, "IE00FT0001", BUY, ETF, FT, MOC, "1000"));

    var message = factory.create(day, OrderEmail.FT);

    assertThat(message.getAttachments())
        .extracting(MessageContent::getName, MessageContent::getType, MessageContent::getContent)
        .containsExactly(
            tuple(
                "FT_ETF_orders_26112026.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Base64.getEncoder().encodeToString(new byte[] {5, 6})));
  }

  @Test
  void theSebFundEmail_takesItsSubjectAndTextFromConfiguration_inTheSheetGuidesEnglishFrame() {
    var day = day(order(TUK00, "LU00SEB001", BUY, FUND, SEB, MOC, "75000"));

    var message = factory.create(day, OrderEmail.SEB_FUND);

    assertThat(message.getSubject()).isEqualTo("Tuleva fund orders 26.11.2026");
    assertThat(message.getText())
        .isEqualTo(
            """
            Hi!

            Please find attached Tuleva fund orders. Please confirm the receipt.

            Best regards,
            Fund Manager
            Tuleva""");
    assertThat(message.getTo())
        .extracting(Recipient::getEmail, Recipient::getType)
        .containsExactly(
            tuple("funds@seb.example", TO),
            tuple("custody@seb.example", TO),
            tuple("funds@tuleva.ee", CC),
            tuple("portfolio@tuleva.ee", CC),
            tuple("fundmanager@tuleva.ee", CC));
    assertThat(message.getAttachments())
        .extracting(MessageContent::getName, MessageContent::getType)
        .containsExactly(tuple("SEB_FUND_ORDERS_26112026.csv", "text/csv"));
  }

  @Test
  void theUuidEmail_goesToSebCustodyWithTheGasDraftsText() {
    var day = day(order(TUK75, "IE00FT0001", BUY, ETF, FT, MOC, "1000"));

    var message = factory.create(day, OrderEmail.UUID);

    assertThat(message.getSubject()).isEqualTo("Tehingukorralduste UUID 26.11.2026");
    assertThat(message.getText())
        .isEqualTo(
            """
            Tere,

            Lisatud tänaste tehingukorralduste fail (26.11.2026).

            Fund Manager
            Tuleva""");
    assertThat(message.getTo())
        .extracting(Recipient::getEmail)
        .containsExactly(
            "custody@seb.example",
            "funds@tuleva.ee",
            "portfolio@tuleva.ee",
            "fundmanager@tuleva.ee");
    assertThat(message.getAttachments())
        .extracting(MessageContent::getName)
        .containsExactly("Tehingud_UUID_20261126_1050.xlsx");
  }

  @Test
  void everyOrderEmailIsATradeOrderTaggedWithItsKind_withoutTrackingAndWithItsCopiesVisible() {
    var day = day(order(TUK75, "IE00FT0001", BUY, ETF, FT, MOC, "1000"));

    var message = factory.create(day, OrderEmail.UUID);

    assertThat(message.getTags()).containsExactly("trade-order", "trade-order-uuid-workbook");
    assertThat(message.getTrackOpens()).isFalse();
    assertThat(message.getTrackClicks()).isFalse();
    assertThat(message.getPreserveRecipients()).isTrue();
  }

  @Test
  void requireOneFtOrderType_refusesFtOrdersOfMixedTypes_sinceTheEmailNamesOneTypeForAll() {
    var day =
        day(
            order(TUK75, "IE00FT0001", BUY, ETF, FT, MOC, "1000"),
            order(TUV100, "IE00FT0002", BUY, ETF, FT, NAV, "1000"));

    assertThatThrownBy(() -> CustodianOrderMessageFactory.requireOneFtOrderType(day))
        .isInstanceOf(TransactionBatchRefusedException.class);
  }

  @Test
  void requireOneFtOrderType_ignoresTheOrderTypesOfOtherVenues() {
    var day =
        day(
            order(TUK75, "IE00FT0001", BUY, ETF, FT, NAV, "1000"),
            order(TUK00, "LU00SEB001", BUY, FUND, SEB, MOC, "75000"));

    assertThatCode(() -> CustodianOrderMessageFactory.requireOneFtOrderType(day))
        .doesNotThrowAnyException();
  }

  @Test
  void aSebEmailWithoutItsConfiguredText_isNotBuilt() {
    var withoutSebTexts =
        new CustodianOrderMessageFactory(
            new CustodianOrderEmailProperties(true, List.of(), null, null, null, null));
    var day = day(order(TUK00, "LU00SEB001", BUY, FUND, SEB, MOC, "75000"));

    assertThatThrownBy(() -> withoutSebTexts.create(day, OrderEmail.SEB_FUND))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
  }

  private static OrderDay day(TransactionOrder... orders) {
    return new OrderDay(
        List.of(12L, 13L),
        TRADE_DATE,
        TEN_TO_ELEVEN_IN_TALLINN,
        FUND_MANAGER,
        List.of(orders),
        Map.of(
            SEB_FUND, new byte[] {1, 2},
            FT_ETF, new byte[] {5, 6},
            UUID_WORKBOOK, new byte[] {7, 8}));
  }

  private static TransactionOrder order(
      TulevaFund fund,
      String isin,
      TransactionType side,
      InstrumentType instrumentType,
      OrderVenue venue,
      OrderType orderType,
      String amount) {
    return TransactionOrder.builder()
        .fund(fund)
        .instrumentIsin(isin)
        .transactionType(side)
        .instrumentType(instrumentType)
        .orderVenue(venue)
        .orderType(orderType)
        .orderAmount(new BigDecimal(amount))
        .orderUuid(UUID.randomUUID())
        .expectedSettlementDate(SETTLEMENT_DATE)
        .build();
  }
}
