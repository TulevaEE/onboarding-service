package ee.tuleva.onboarding.aml.alert;

import static ee.tuleva.onboarding.aml.alert.AmlAlertType.TKF_VOLUME_15K_NEW_CLIENT;
import static ee.tuleva.onboarding.aml.alert.TkfFlowDirection.IN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TkfVolumeAlertServiceTest {

  @Mock private TkfVolumeReader reader;
  @Mock private TkfVolumeEvaluator evaluator;
  @Mock private AmlTkfVolumeAlertRepository alertRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final Clock clock = Clock.fixed(Instant.parse("2026-06-15T08:00:00Z"), ZoneOffset.UTC);

  private TkfVolumeAlertService service;

  @BeforeEach
  void setUp() {
    service = new TkfVolumeAlertService(reader, evaluator, alertRepository, eventPublisher, clock);
  }

  private TkfVolumeAggregate aggregate() {
    return new TkfVolumeAggregate(
        "38001010000",
        new BigDecimal("15000.00"),
        BigDecimal.ZERO,
        Instant.parse("2026-06-10T00:00:00Z"),
        null,
        "2026-06",
        new BigDecimal("15000.00"),
        Instant.parse("2026-06-10T00:00:00Z"),
        "2026",
        true,
        false,
        null);
  }

  private final TkfVolumeAlert alert =
      new TkfVolumeAlert(TKF_VOLUME_15K_NEW_CLIENT, IN, new BigDecimal("15000.00"), "2026-06");

  @Test
  @DisplayName("publishes an alert event and persists a dedup row for a qualifying aggregate")
  void checkAndAlert_qualifying_publishesEventAndTracks() {
    var aggregate = aggregate();
    when(reader.readVolumeAggregates()).thenReturn(List.of(aggregate));
    when(evaluator.evaluate(aggregate)).thenReturn(List.of(alert));
    when(alertRepository.existsByPersonalIdAndAlertTypeAndDirectionAndWindowKey(
            "38001010000", TKF_VOLUME_15K_NEW_CLIENT, IN, "2026-06"))
        .thenReturn(false);

    service.checkAndAlert();

    ArgumentCaptor<AmlThresholdAlertEvent> eventCaptor =
        ArgumentCaptor.forClass(AmlThresholdAlertEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    AmlThresholdAlertEvent event = eventCaptor.getValue();
    assertThat(event.getType()).isEqualTo(TKF_VOLUME_15K_NEW_CLIENT);
    assertThat(event.getPersonalId()).isEqualTo("38001010000");
    assertThat(event.getAmount()).isEqualByComparingTo("15000.00");
    assertThat(event.getReference()).isEqualTo("IN/2026-06");

    ArgumentCaptor<AmlTkfVolumeAlert> alertCaptor =
        ArgumentCaptor.forClass(AmlTkfVolumeAlert.class);
    verify(alertRepository).save(alertCaptor.capture());
    AmlTkfVolumeAlert saved = alertCaptor.getValue();
    assertThat(saved.getPersonalId()).isEqualTo("38001010000");
    assertThat(saved.getAlertType()).isEqualTo(TKF_VOLUME_15K_NEW_CLIENT);
    assertThat(saved.getDirection()).isEqualTo(IN);
    assertThat(saved.getWindowKey()).isEqualTo("2026-06");
    assertThat(saved.getAlertedAt()).isEqualTo(clock.instant());
  }

  @Test
  @DisplayName("does not re-alert an aggregate already alerted for the same window")
  void checkAndAlert_alreadyAlerted_skips() {
    var aggregate = aggregate();
    when(reader.readVolumeAggregates()).thenReturn(List.of(aggregate));
    when(evaluator.evaluate(aggregate)).thenReturn(List.of(alert));
    when(alertRepository.existsByPersonalIdAndAlertTypeAndDirectionAndWindowKey(
            "38001010000", TKF_VOLUME_15K_NEW_CLIENT, IN, "2026-06"))
        .thenReturn(true);

    service.checkAndAlert();

    verify(eventPublisher, never()).publishEvent(any());
    verify(alertRepository, never()).save(any());
  }

  @Test
  @DisplayName("does not persist a dedup row and does not throw when the alert send fails")
  void checkAndAlert_sendFails_doesNotTrackAndDoesNotThrow() {
    var aggregate = aggregate();
    when(reader.readVolumeAggregates()).thenReturn(List.of(aggregate));
    when(evaluator.evaluate(aggregate)).thenReturn(List.of(alert));
    when(alertRepository.existsByPersonalIdAndAlertTypeAndDirectionAndWindowKey(
            any(), any(), any(), any()))
        .thenReturn(false);
    doThrow(new IllegalStateException("Slack down")).when(eventPublisher).publishEvent(any());

    assertThatCode(() -> service.checkAndAlert()).doesNotThrowAnyException();

    verify(alertRepository, never()).save(any());
  }

  @Test
  @DisplayName("does nothing for an aggregate that does not qualify for any threshold")
  void checkAndAlert_nonQualifying_doesNothing() {
    var aggregate = aggregate();
    when(reader.readVolumeAggregates()).thenReturn(List.of(aggregate));
    when(evaluator.evaluate(aggregate)).thenReturn(List.of());

    service.checkAndAlert();

    verify(eventPublisher, never()).publishEvent(any());
    verify(alertRepository, never()).save(any());
  }
}
