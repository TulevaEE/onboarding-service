package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnmatchedPendingTransactionFinderTest {

  @Mock private SebPendingTransactionExtractor extractor;
  @Mock private SebPendingTransactionMatcher matcher;
  @Mock private SebPendingTransactionComplexMatcher complexMatcher;
  @Mock private TransactionMatchingPolicy matchingPolicy;
  @Mock private TransactionOrderRepository orderRepository;
  @Mock private TransactionExecutionRepository executionRepository;
  @Mock private SebClientNameToFundResolver fundResolver;

  private final InvestmentReport report = InvestmentReport.builder().build();
  private final TransactionMatchingProperties properties =
      new TransactionMatchingProperties(null, null, null, null, null);

  private UnmatchedPendingTransactionFinder finder() {
    org.mockito.Mockito.lenient().when(matchingPolicy.current()).thenReturn(properties);
    return new UnmatchedPendingTransactionFinder(
        extractor,
        matcher,
        complexMatcher,
        matchingPolicy,
        orderRepository,
        executionRepository,
        fundResolver);
  }

  @Test
  void unmatchedRow_isReturned() {
    SebPendingTransactionRow row = row("R1");
    given(extractor.extract(report)).willReturn(List.of(row));
    given(executionRepository.findByBrokerTransactionId("R1")).willReturn(Optional.empty());
    given(matcher.match(row)).willReturn(Optional.empty());
    given(complexMatcher.match(row, properties)).willReturn(Optional.empty());
    given(complexMatcher.hasNearMissCandidate(row, properties)).willReturn(false);

    assertThat(finder().collectUnmatched(report)).containsExactly(row);
  }

  @Test
  void rowAlreadyLinkedToExecution_isSkipped() {
    SebPendingTransactionRow row = row("R1");
    given(extractor.extract(report)).willReturn(List.of(row));
    given(executionRepository.findByBrokerTransactionId("R1"))
        .willReturn(Optional.of(new TransactionExecution()));

    assertThat(finder().collectUnmatched(report)).isEmpty();
  }

  @Test
  void cleanlyMatchedRow_isSkipped() {
    SebPendingTransactionRow row = row("R1");
    given(extractor.extract(report)).willReturn(List.of(row));
    given(executionRepository.findByBrokerTransactionId("R1")).willReturn(Optional.empty());
    given(matcher.match(row)).willReturn(Optional.of(new TransactionOrder()));

    assertThat(finder().collectUnmatched(report)).isEmpty();
  }

  @Test
  void rowWithAnyNearMissCandidate_isNotUnmatched() {
    SebPendingTransactionRow row = row("R1");
    given(extractor.extract(report)).willReturn(List.of(row));
    given(executionRepository.findByBrokerTransactionId("R1")).willReturn(Optional.empty());
    given(matcher.match(row)).willReturn(Optional.empty());
    given(complexMatcher.match(row, properties)).willReturn(Optional.empty());
    given(complexMatcher.hasNearMissCandidate(row, properties)).willReturn(true);

    assertThat(finder().collectUnmatched(report)).isEmpty();
  }

  @Test
  void rowWithNullOurRef_doesNotQueryByNullBrokerId() {
    SebPendingTransactionRow row = row(null);
    given(extractor.extract(report)).willReturn(List.of(row));
    given(matcher.match(row)).willReturn(Optional.empty());
    given(complexMatcher.match(row, properties)).willReturn(Optional.empty());
    given(complexMatcher.hasNearMissCandidate(row, properties)).willReturn(false);

    assertThat(finder().collectUnmatched(report)).containsExactly(row);
    org.mockito.Mockito.verify(executionRepository, org.mockito.Mockito.never())
        .findByBrokerTransactionId(any());
  }

  @Test
  void rowLinkedViaClientRef_isSkipped() {
    UUID clientRef = UUID.randomUUID();
    SebPendingTransactionRow row = rowWithClientRef(clientRef);
    TransactionOrder order = TransactionOrder.builder().id(7L).build();
    given(extractor.extract(report)).willReturn(List.of(row));
    given(executionRepository.findByBrokerTransactionId("R1")).willReturn(Optional.empty());
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));
    given(executionRepository.findAllByOrderId(7L)).willReturn(List.of(new TransactionExecution()));

    assertThat(finder().collectUnmatched(report)).isEmpty();
  }

  @Test
  void rowWithClientRefButNoLinkedExecution_isReturned() {
    UUID clientRef = UUID.randomUUID();
    SebPendingTransactionRow row = rowWithClientRef(clientRef);
    given(extractor.extract(report)).willReturn(List.of(row));
    given(executionRepository.findByBrokerTransactionId("R1")).willReturn(Optional.empty());
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.empty());
    given(matcher.match(row)).willReturn(Optional.empty());
    given(complexMatcher.match(row, properties)).willReturn(Optional.empty());
    given(complexMatcher.hasNearMissCandidate(row, properties)).willReturn(false);

    assertThat(finder().collectUnmatched(report)).containsExactly(row);
  }

  @Test
  void collectInconsistent_clientRefMatchedRowWithMismatchedSide_isReturnedWithReason() {
    UUID clientRef = UUID.randomUUID();
    SebPendingTransactionRow row = rowWithClientRef(clientRef);
    TransactionOrder order = orderWithClientRef(clientRef, SELL);
    given(extractor.extract(report)).willReturn(List.of(row));
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));

    assertThat(finder().collectInconsistent(report))
        .containsExactly(
            new UnmatchedPendingTransactionFinder.InconsistentMatchedRow(
                row, order, "ISIN_SIDE_MISMATCH"));
  }

  @Test
  void
      collectInconsistent_clientRefMatchedRowWhoseClientNameResolvesToADifferentFund_isReturnedWithReason() {
    UUID clientRef = UUID.randomUUID();
    SebPendingTransactionRow row = rowWithClientRef(clientRef);
    TransactionOrder order = orderWithClientRef(clientRef, BUY);
    given(extractor.extract(report)).willReturn(List.of(row));
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));
    given(fundResolver.resolve(row.clientName())).willReturn(Optional.of(TUK75));

    assertThat(finder().collectInconsistent(report))
        .containsExactly(
            new UnmatchedPendingTransactionFinder.InconsistentMatchedRow(
                row, order, "FUND_MISMATCH"));
  }

  @Test
  void collectInconsistent_consistentClientRefMatchedRow_isNotReturned() {
    UUID clientRef = UUID.randomUUID();
    SebPendingTransactionRow row = rowWithClientRef(clientRef);
    TransactionOrder order = orderWithClientRef(clientRef, BUY);
    given(extractor.extract(report)).willReturn(List.of(row));
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));
    given(fundResolver.resolve(row.clientName())).willReturn(Optional.of(TKF100));

    assertThat(finder().collectInconsistent(report)).isEmpty();
  }

  @Test
  void collectInconsistent_rowWithoutClientRef_isSkipped() {
    SebPendingTransactionRow row = row("R1");
    given(extractor.extract(report)).willReturn(List.of(row));

    assertThat(finder().collectInconsistent(report)).isEmpty();
  }

  @Test
  void collectInconsistent_unknownClientRef_isSkipped() {
    UUID clientRef = UUID.randomUUID();
    SebPendingTransactionRow row = rowWithClientRef(clientRef);
    given(extractor.extract(report)).willReturn(List.of(row));
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.empty());

    assertThat(finder().collectInconsistent(report)).isEmpty();
  }

  private static TransactionOrder orderWithClientRef(
      UUID clientRef, ee.tuleva.onboarding.investment.transaction.TransactionType side) {
    return TransactionOrder.builder()
        .id(7L)
        .fund(TKF100)
        .instrumentIsin("IE000F60HVH9")
        .transactionType(side)
        .instrumentType(ETF)
        .orderVenue(OrderVenue.SEB)
        .orderUuid(clientRef)
        .orderStatus(SENT)
        .build();
  }

  private static SebPendingTransactionRow rowWithClientRef(UUID clientRef) {
    return new SebPendingTransactionRow(
        clientRef,
        "R1",
        "IE000F60HVH9",
        new BigDecimal("15007"),
        new BigDecimal("4.7255"),
        new BigDecimal("70915.58"),
        BigDecimal.ZERO,
        new BigDecimal("70915.58"),
        BUY,
        Instant.parse("2026-05-11T10:26:04Z"),
        LocalDate.of(2026, 5, 13),
        "Tuleva Täiendav Kogumisfond",
        "VP68168",
        "ICAV Amundi MSCI USA Screened UCITS ETF");
  }

  private static SebPendingTransactionRow row(String ourRef) {
    return new SebPendingTransactionRow(
        null,
        ourRef,
        "IE000F60HVH9",
        new BigDecimal("15007"),
        new BigDecimal("4.7255"),
        new BigDecimal("70915.58"),
        BigDecimal.ZERO,
        new BigDecimal("70915.58"),
        BUY,
        Instant.parse("2026-05-11T10:26:04Z"),
        LocalDate.of(2026, 5, 13),
        "Tuleva Täiendav Kogumisfond",
        "VP68168",
        "ICAV Amundi MSCI USA Screened UCITS ETF");
  }
}
