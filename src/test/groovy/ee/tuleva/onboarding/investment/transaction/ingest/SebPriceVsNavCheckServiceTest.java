package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SebPriceVsNavCheckServiceTest {

  private static final String ISIN = "IE000F60HVH9";
  private static final LocalDate TRADE_DATE = LocalDate.of(2026, 5, 11);
  private static final Instant TRADE_INSTANT = Instant.parse("2026-05-11T10:26:04Z");
  private static final Instant NOW = Instant.parse("2026-05-12T08:00:00Z");

  @Mock private PositionPriceResolver positionPriceResolver;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final PriceValidator priceValidator = new PriceValidator();
  private final InstrumentTypeClassifier classifier = new InstrumentTypeClassifier();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private SebPriceVsNavCheckService service() {
    return new SebPriceVsNavCheckService(
        positionPriceResolver, priceValidator, classifier, eventPublisher, clock);
  }

  @Test
  void etfWithinTolerance_noEvent() {
    given(positionPriceResolver.resolve(ISIN, TRADE_DATE, NOW))
        .willReturn(Optional.of(resolvedPrice(new BigDecimal("4.7255"), TRADE_DATE, OK)));

    service().check(execution(new BigDecimal("4.7255")), etfOrder());

    verifyNoInteractions(eventPublisher);
  }

  @Test
  void resolverCalledWithTradeDateAndNowCutoff() {
    given(positionPriceResolver.resolve(ISIN, TRADE_DATE, NOW))
        .willReturn(Optional.of(resolvedPrice(new BigDecimal("4.7255"), TRADE_DATE, OK)));

    service().check(execution(new BigDecimal("4.7255")), etfOrder());

    verify(positionPriceResolver).resolve(ISIN, TRADE_DATE, NOW);
  }

  @Test
  void etfOutsideTolerance_emitsExecutionMismatchEvent() {
    given(positionPriceResolver.resolve(ISIN, TRADE_DATE, NOW))
        .willReturn(Optional.of(resolvedPrice(new BigDecimal("4.7800"), TRADE_DATE, OK)));

    service().check(execution(new BigDecimal("4.7255")), etfOrder());

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(ExecutionMismatchEvent.class);
    ExecutionMismatchEvent event = (ExecutionMismatchEvent) captor.getValue();
    assertThat(event.executionId()).isEqualTo(42L);
    assertThat(event.isin()).isEqualTo(ISIN);
    assertThat(event.execPrice()).isEqualByComparingTo("4.7255");
    assertThat(event.navPrice()).isEqualByComparingTo("4.7800");
    assertThat(event.tradeDate()).isEqualTo(TRADE_DATE);
    assertThat(event.deltaPercent()).isBetween(new BigDecimal("1.13"), new BigDecimal("1.15"));
  }

  @Test
  void fundInstrument_skipsCheckEntirely() {
    service().check(execution(new BigDecimal("4.7255")), fundOrder());

    verifyNoInteractions(positionPriceResolver, eventPublisher);
  }

  @Test
  void etfWithNoResolvedPrice_emitsNavMissingEvent() {
    given(positionPriceResolver.resolve(ISIN, TRADE_DATE, NOW)).willReturn(Optional.empty());

    service().check(execution(new BigDecimal("4.7255")), etfOrder());

    assertNavMissingEvent();
  }

  @Test
  void etfWithNonOkValidationStatus_emitsNavMissingEvent() {
    given(positionPriceResolver.resolve(ISIN, TRADE_DATE, NOW))
        .willReturn(Optional.of(resolvedPrice(null, null, NO_PRICE_DATA)));

    service().check(execution(new BigDecimal("4.7255")), etfOrder());

    assertNavMissingEvent();
  }

  @Test
  void etfWithStalePriceForEarlierDate_emitsNavMissingEvent() {
    given(positionPriceResolver.resolve(ISIN, TRADE_DATE, NOW))
        .willReturn(
            Optional.of(resolvedPrice(new BigDecimal("4.7255"), TRADE_DATE.minusDays(3), OK)));

    service().check(execution(new BigDecimal("4.7255")), etfOrder());

    assertNavMissingEvent();
  }

  @Test
  void executionWithNoTimestamp_isSkippedSafely() {
    TransactionExecution exec = execution(new BigDecimal("4.7255"));
    exec.setExecutionTimestamp(null);

    service().check(exec, etfOrder());

    verifyNoInteractions(positionPriceResolver, eventPublisher);
  }

  @Test
  void tradeDateIsDerivedFromExecutionTimestampInTallinnZone() {
    // 22:30Z on May 11 is already May 12 in Europe/Tallinn (summer time, UTC+3).
    Instant lateTrade = Instant.parse("2026-05-11T22:30:00Z");
    LocalDate expectedTallinnDate = LocalDate.of(2026, 5, 12);
    TransactionExecution exec = execution(new BigDecimal("4.7255"));
    exec.setExecutionTimestamp(lateTrade);

    given(positionPriceResolver.resolve(ISIN, expectedTallinnDate, NOW))
        .willReturn(Optional.of(resolvedPrice(new BigDecimal("4.7255"), expectedTallinnDate, OK)));

    service().check(exec, etfOrder());

    verify(positionPriceResolver).resolve(ISIN, expectedTallinnDate, NOW);
    verify(eventPublisher, never()).publishEvent(any());
  }

  private void assertNavMissingEvent() {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(NavMissingEvent.class);
    NavMissingEvent event = (NavMissingEvent) captor.getValue();
    assertThat(event.executionId()).isEqualTo(42L);
    assertThat(event.isin()).isEqualTo(ISIN);
    assertThat(event.tradeDate()).isEqualTo(TRADE_DATE);
  }

  private static ResolvedPrice resolvedPrice(
      BigDecimal price, LocalDate priceDate, ValidationStatus status) {
    return ResolvedPrice.builder()
        .usedPrice(price)
        .validationStatus(status)
        .priceDate(priceDate)
        .build();
  }

  private static TransactionOrder etfOrder() {
    return TransactionOrder.builder().instrumentType(ETF).instrumentIsin(ISIN).build();
  }

  private static TransactionOrder fundOrder() {
    return TransactionOrder.builder().instrumentType(FUND).instrumentIsin(ISIN).build();
  }

  private static TransactionExecution execution(BigDecimal unitPrice) {
    return TransactionExecution.builder()
        .id(42L)
        .orderId(7L)
        .unitPrice(unitPrice)
        .executionTimestamp(TRADE_INSTANT)
        .source("SEB_OOTEL")
        .build();
  }
}
