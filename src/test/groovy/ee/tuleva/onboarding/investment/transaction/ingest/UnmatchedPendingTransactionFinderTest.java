package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
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
  @Mock private TransactionOrderRepository orderRepository;
  @Mock private TransactionExecutionRepository executionRepository;

  private final InvestmentReport report = InvestmentReport.builder().build();

  private UnmatchedPendingTransactionFinder finder() {
    return new UnmatchedPendingTransactionFinder(
        extractor, matcher, complexMatcher, orderRepository, executionRepository);
  }

  @Test
  void unmatchedRow_isReturned() {
    SebPendingTransactionRow row = row("R1");
    given(extractor.extract(report)).willReturn(List.of(row));
    given(executionRepository.findByBrokerTransactionId("R1")).willReturn(Optional.empty());
    given(matcher.match(row)).willReturn(Optional.empty());
    given(complexMatcher.match(row)).willReturn(Optional.empty());
    given(complexMatcher.hasNearMissCandidate(row)).willReturn(false);

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
    given(complexMatcher.match(row)).willReturn(Optional.empty());
    given(complexMatcher.hasNearMissCandidate(row)).willReturn(true);

    assertThat(finder().collectUnmatched(report)).isEmpty();
  }

  @Test
  void rowWithNullOurRef_doesNotQueryByNullBrokerId() {
    SebPendingTransactionRow row = row(null);
    given(extractor.extract(report)).willReturn(List.of(row));
    given(matcher.match(row)).willReturn(Optional.empty());
    given(complexMatcher.match(row)).willReturn(Optional.empty());
    given(complexMatcher.hasNearMissCandidate(row)).willReturn(false);

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
    given(executionRepository.findByOrderId(7L))
        .willReturn(Optional.of(new TransactionExecution()));

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
    given(complexMatcher.match(row)).willReturn(Optional.empty());
    given(complexMatcher.hasNearMissCandidate(row)).willReturn(false);

    assertThat(finder().collectUnmatched(report)).containsExactly(row);
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
