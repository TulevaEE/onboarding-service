package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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

  @Mock private NavLookup navLookup;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final PriceValidator priceValidator = new PriceValidator();
  private final InstrumentTypeClassifier classifier = new InstrumentTypeClassifier();

  private SebPriceVsNavCheckService service;

  @BeforeEach
  void setUp() {
    service = new SebPriceVsNavCheckService(navLookup, priceValidator, classifier, eventPublisher);
  }

  @Test
  void etfWithinTolerance_noEvent() {
    given(navLookup.findMarketPrice(ISIN, TRADE_DATE))
        .willReturn(Optional.of(new BigDecimal("4.7255")));

    service.check(execution(new BigDecimal("4.7255")), etfOrder());

    verifyNoInteractions(eventPublisher);
  }

  @Test
  void etfOutsideTolerance_emitsExecutionMismatchEvent() {
    given(navLookup.findMarketPrice(ISIN, TRADE_DATE))
        .willReturn(Optional.of(new BigDecimal("4.7800")));

    service.check(execution(new BigDecimal("4.7255")), etfOrder());

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
    service.check(execution(new BigDecimal("4.7255")), fundOrder());

    verifyNoInteractions(navLookup, eventPublisher);
  }

  @Test
  void etfWithNoNavRow_emitsNavMissingEvent() {
    given(navLookup.findMarketPrice(ISIN, TRADE_DATE)).willReturn(Optional.empty());

    service.check(execution(new BigDecimal("4.7255")), etfOrder());

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(NavMissingEvent.class);
    NavMissingEvent event = (NavMissingEvent) captor.getValue();
    assertThat(event.executionId()).isEqualTo(42L);
    assertThat(event.isin()).isEqualTo(ISIN);
    assertThat(event.tradeDate()).isEqualTo(TRADE_DATE);
  }

  @Test
  void executionWithNoTimestamp_isSkippedSafely() {
    TransactionExecution exec = execution(new BigDecimal("4.7255"));
    exec.setExecutionTimestamp(null);

    service.check(exec, etfOrder());

    verifyNoInteractions(navLookup, eventPublisher);
  }

  @Test
  void tradeDateIsDerivedFromExecutionTimestampInTallinnZone() {
    // 22:30Z on May 11 is already May 12 in Europe/Tallinn (summer time, UTC+3).
    // NAV table is Tallinn-keyed; cost-basis is Tallinn-keyed; this must match.
    Instant lateTrade = Instant.parse("2026-05-11T22:30:00Z");
    LocalDate expectedTallinnDate = LocalDate.of(2026, 5, 12);
    TransactionExecution exec = execution(new BigDecimal("4.7255"));
    exec.setExecutionTimestamp(lateTrade);

    given(navLookup.findMarketPrice(ISIN, expectedTallinnDate))
        .willReturn(Optional.of(new BigDecimal("4.7255")));

    service.check(exec, etfOrder());

    verify(navLookup).findMarketPrice(ISIN, expectedTallinnDate);
    verify(eventPublisher, never()).publishEvent(any());
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
