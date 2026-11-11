package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRADE_SETTLEMENT;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.BROKER_REFERENCE;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.INSTRUMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.ledger.SystemAccount.SECURITIES_CUSTODY;
import static ee.tuleva.onboarding.ledger.SystemAccount.TRADE_UNIT_SETTLEMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import ee.tuleva.onboarding.time.ClockConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  LedgerService.class,
  LedgerAccountService.class,
  LedgerPartyService.class,
  LedgerTransactionService.class,
  UserUnitBalanceGuard.class,
  FundBankLedger.class,
  TradeSettlementBookings.class,
  ClockConfig.class
})
class TradeSettlementBookingsTest {

  private static final String EJAP = "LU1291102447";

  @Autowired FundBankLedger fundBankLedger;
  @Autowired TradeSettlementBookings tradeSettlementBookings;
  @Autowired LedgerTransactionService ledgerTransactionService;
  @Autowired LedgerAccountService ledgerAccountService;

  @Test
  void bookedSince_givesEachTradeItsSebReferenceIsinBookingDateAndCash() {
    UUID externalReference = randomUUID();
    bookTrade(
        "DLA0553690", new BigDecimal("-209025.86"), LocalDate.of(2026, 5, 28), externalReference);

    assertThat(tradeSettlementBookings.bookedSince(LocalDate.of(2026, 5, 1)))
        .singleElement()
        .satisfies(
            booking -> {
              assertThat(booking.brokerReference()).isEqualTo("DLA0553690");
              assertThat(booking.isin()).isEqualTo(EJAP);
              assertThat(booking.bookingDate()).isEqualTo(LocalDate.of(2026, 5, 28));
              assertThat(booking.cashAmount()).isEqualByComparingTo("-209025.86");
              assertThat(booking.externalReference()).isEqualTo(externalReference);
              assertThat(booking.fund()).isEqualTo(TKF100);
            });
  }

  @Test
  void bookedSince_leavesOutTradesBookedBeforeTheDayAndBookingsOfOtherKinds() {
    bookTrade("DLA0553690", new BigDecimal("-209025.86"), LocalDate.of(2026, 4, 30), randomUUID());
    bookTrade("DLA0553685", new BigDecimal("-995377.50"), LocalDate.of(2026, 5, 1), randomUUID());
    fundBankLedger.recordBankFee(
        TKF100,
        new BigDecimal("-5.00"),
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        LocalDate.of(2026, 5, 2));

    assertThat(tradeSettlementBookings.bookedSince(LocalDate.of(2026, 5, 1)))
        .extracting(TradeSettlementBooking::brokerReference)
        .containsExactly("DLA0553685");
  }

  @Test
  void bookedSince_aTradeBookedWithoutACashLeg_hasNoCashAmountRatherThanZero() {
    LedgerAccount unitSettlement = instrumentAccount(TRADE_UNIT_SETTLEMENT);
    LedgerAccount custody = instrumentAccount(SECURITIES_CUSTODY);
    ledgerTransactionService.createTransaction(
        TRADE_SETTLEMENT,
        LocalDate.of(2026, 5, 28).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant(),
        randomUUID(),
        Map.of(BROKER_REFERENCE.getKey(), "DLA0553690", INSTRUMENT.getKey(), EJAP),
        new LedgerEntryDto(unitSettlement, new BigDecimal("-11704.00000")),
        new LedgerEntryDto(custody, new BigDecimal("11704.00000")));

    assertThat(tradeSettlementBookings.bookedSince(LocalDate.of(2026, 5, 1)))
        .singleElement()
        .satisfies(booking -> assertThat(booking.cashAmount()).isNull());
  }

  private LedgerAccount instrumentAccount(SystemAccount systemAccount) {
    return ledgerAccountService.createSystemAccount(
        systemAccount.getAccountName(TKF100, EJAP),
        systemAccount.getAccountType(),
        systemAccount.getAssetType());
  }

  private void bookTrade(
      String brokerReference, BigDecimal cash, LocalDate bookingDate, UUID externalReference) {
    fundBankLedger.recordTradeSettlement(
        TKF100,
        cash,
        new BigDecimal("11704.00000"),
        externalReference,
        FUND_INVESTMENT_CASH_CLEARING,
        new TradeSettlementDetails(
            brokerReference,
            EJAP,
            "EJAP",
            "BNP Paribas Easy MSCI Japan Min TE UCITS ETF",
            new BigDecimal("17.864")),
        bookingDate);
  }
}
