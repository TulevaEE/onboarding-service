package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.SettlementSource.BANK_STATEMENT;
import static ee.tuleva.onboarding.investment.transaction.SettlementSource.HISTORICAL_IMPORT;
import static ee.tuleva.onboarding.investment.transaction.SettlementSource.PENDING_REPORT_ABSENCE;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionSettlementServiceTest {

  private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");
  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 5, 13);

  @Mock private TransactionSettlementRepository settlementRepository;
  @Mock private TransactionOrderRepository orderRepository;

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private TransactionSettlementService service() {
    return new TransactionSettlementService(settlementRepository, orderRepository, clock);
  }

  @Test
  void recordSettlement_createsSettlementAndTransitionsOrderToSettled() {
    TransactionOrder order = order(42L);
    given(settlementRepository.findByOrderId(42L)).willReturn(Optional.empty());
    given(settlementRepository.save(any(TransactionSettlement.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    TransactionSettlement settlement =
        service().recordSettlement(order, REPORT_DATE, PENDING_REPORT_ABSENCE);

    assertThat(settlement.getOrderId()).isEqualTo(42L);
    assertThat(settlement.getSettledAt()).isEqualTo(NOW);
    assertThat(settlement.getReportDate()).isEqualTo(REPORT_DATE);
    assertThat(settlement.getSource()).isEqualTo(PENDING_REPORT_ABSENCE);
    assertThat(order.getOrderStatus()).isEqualTo(SETTLED);
    verify(orderRepository).save(order);
  }

  @Test
  void recordSettlement_keepsWhatDatedTheSettlement() {
    TransactionOrder order = order(42L);
    given(settlementRepository.findByOrderId(42L)).willReturn(Optional.empty());
    given(settlementRepository.save(any(TransactionSettlement.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    TransactionSettlement settlement =
        service().recordSettlement(order, REPORT_DATE, HISTORICAL_IMPORT);

    assertThat(settlement.getSource()).isEqualTo(HISTORICAL_IMPORT);
  }

  @Test
  void recordSettlement_isIdempotentWhenSettlementAlreadyExists() {
    TransactionOrder order = order(42L);
    TransactionSettlement existing =
        TransactionSettlement.builder()
            .id(7L)
            .orderId(42L)
            .settledAt(Instant.parse("2026-05-12T09:00:00Z"))
            .reportDate(LocalDate.of(2026, 5, 12))
            .build();
    given(settlementRepository.findByOrderId(42L)).willReturn(Optional.of(existing));

    TransactionSettlement settlement =
        service().recordSettlement(order, REPORT_DATE, PENDING_REPORT_ABSENCE);

    assertThat(settlement).isEqualTo(existing);
    assertThat(order.getOrderStatus()).isEqualTo(EXECUTED);
    verify(settlementRepository, never()).save(any());
    verify(orderRepository, never()).save(any());
  }

  @Test
  void settleFromTheBank_settlesAnOrderWithNoSettlementOnTheBookingDate() {
    TransactionOrder order = order(42L);
    given(settlementRepository.findByOrderId(42L)).willReturn(Optional.empty());
    given(settlementRepository.save(any(TransactionSettlement.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    assertThat(service().settleFromTheBank(order, REPORT_DATE)).isEqualTo(new BankDating.Settled());
    verify(settlementRepository)
        .save(
            argThat(
                saved ->
                    saved.getReportDate().equals(REPORT_DATE)
                        && saved.getSource() == BANK_STATEMENT));
    assertThat(order.getOrderStatus()).isEqualTo(SETTLED);
  }

  @Test
  void settleFromTheBank_redatesAnAbsenceSettlementAndReturnsWhatItWas() {
    TransactionOrder order = order(42L);
    TransactionSettlement existing = settlement(LocalDate.of(2026, 5, 15), PENDING_REPORT_ABSENCE);
    given(settlementRepository.findByOrderId(42L)).willReturn(Optional.of(existing));

    assertThat(service().settleFromTheBank(order, REPORT_DATE))
        .isEqualTo(
            new BankDating.Redated(
                new PreviousSettlement(LocalDate.of(2026, 5, 15), PENDING_REPORT_ABSENCE)));
    assertThat(existing.getReportDate()).isEqualTo(REPORT_DATE);
    assertThat(existing.getSource()).isEqualTo(BANK_STATEMENT);
    verify(settlementRepository).save(existing);
  }

  @Test
  void settleFromTheBank_redatesAnAbsenceSettlementThatWasWrittenJustBeforeIt() {
    TransactionOrder order = order(42L);
    TransactionSettlement writtenMeanwhile =
        settlement(LocalDate.of(2026, 5, 15), PENDING_REPORT_ABSENCE);
    given(settlementRepository.findByOrderId(42L))
        .willReturn(Optional.empty(), Optional.of(writtenMeanwhile));

    assertThat(service().settleFromTheBank(order, REPORT_DATE))
        .isEqualTo(
            new BankDating.Redated(
                new PreviousSettlement(LocalDate.of(2026, 5, 15), PENDING_REPORT_ABSENCE)));
    assertThat(writtenMeanwhile.getReportDate()).isEqualTo(REPORT_DATE);
    assertThat(writtenMeanwhile.getSource()).isEqualTo(BANK_STATEMENT);
  }

  @Test
  void settleFromTheBank_leavesASettlementTheBankAlreadyDated() {
    TransactionOrder order = order(42L);
    TransactionSettlement byTheBank = settlement(LocalDate.of(2026, 5, 11), BANK_STATEMENT);
    given(settlementRepository.findByOrderId(42L)).willReturn(Optional.of(byTheBank));

    assertThat(service().settleFromTheBank(order, REPORT_DATE))
        .isEqualTo(new BankDating.AlreadyByTheBank());
    assertThat(byTheBank.getReportDate()).isEqualTo(LocalDate.of(2026, 5, 11));
    verify(settlementRepository, never()).save(any());
  }

  @Test
  void isSettledByTheBank_onlyForASettlementTheBankDated() {
    given(settlementRepository.findByOrderId(1L))
        .willReturn(Optional.of(settlement(REPORT_DATE, BANK_STATEMENT)));
    given(settlementRepository.findByOrderId(2L))
        .willReturn(Optional.of(settlement(REPORT_DATE, PENDING_REPORT_ABSENCE)));
    given(settlementRepository.findByOrderId(3L)).willReturn(Optional.empty());

    assertThat(service().isSettledByTheBank(order(1L))).isTrue();
    assertThat(service().isSettledByTheBank(order(2L))).isFalse();
    assertThat(service().isSettledByTheBank(order(3L))).isFalse();
  }

  private static TransactionSettlement settlement(LocalDate reportDate, SettlementSource source) {
    return TransactionSettlement.builder()
        .id(7L)
        .orderId(42L)
        .settledAt(Instant.parse("2026-05-15T09:00:00Z"))
        .reportDate(reportDate)
        .source(source)
        .build();
  }

  private TransactionOrder order(Long id) {
    return TransactionOrder.builder()
        .id(id)
        .fund(TUK75)
        .instrumentIsin("IE000F60HVH9")
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderVenue(SEB)
        .orderStatus(EXECUTED)
        .build();
  }
}
