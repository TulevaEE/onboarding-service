package ee.tuleva.onboarding.audit.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditHealthServiceTest {

  @Mock private AuditHealthRepository mockAuditHealthRepository;
  @InjectMocks private AuditHealthService auditHealthService;

  private static final Instant NOW = Instant.parse("2025-05-27T12:00:00.00Z");
  private final Clock fixedClock = Clock.fixed(NOW, ZoneId.of("UTC"));

  private final long THRESHOLD_CALCULATION_PERIOD_DAYS = 10;

  private AuditLogInterval createInterval(final Double seconds) {
    return new AuditLogInterval(seconds);
  }

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(fixedClock);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  @DisplayName("initializeOrRefreshThreshold sets threshold correctly from repository")
  void initializeOrRefreshThreshold_setsThreshold() {
    // given
    Instant sinceTime = NOW.minus(THRESHOLD_CALCULATION_PERIOD_DAYS, ChronoUnit.DAYS);
    when(mockAuditHealthRepository.findLongestIntervalSecondsSince(sinceTime))
        .thenReturn(createInterval(3600.0));
    // when
    auditHealthService.initializeOrRefreshThreshold();
    // then
    when(mockAuditHealthRepository.findLastAuditEventTimestamp())
        .thenReturn(Optional.of(NOW.minus(Duration.ofMinutes(30))));
    assertThat(auditHealthService.isAuditLogDelayed()).isFalse();

    when(mockAuditHealthRepository.findLastAuditEventTimestamp())
        .thenReturn(Optional.of(NOW.minusSeconds(4321)));
    assertThat(auditHealthService.isAuditLogDelayed()).isTrue();
  }

  @Test
  @DisplayName(
      "initializeOrRefreshThreshold sets ZERO threshold if repository returns zero interval")
  void initializeOrRefreshThreshold_setsZeroOnZeroInterval() {
    // given
    Instant sinceTime = NOW.minus(THRESHOLD_CALCULATION_PERIOD_DAYS, ChronoUnit.DAYS);
    when(mockAuditHealthRepository.findLongestIntervalSecondsSince(sinceTime))
        .thenReturn(createInterval(0.0));
    // when
    auditHealthService.initializeOrRefreshThreshold();
    // then
    when(mockAuditHealthRepository.findLastAuditEventTimestamp())
        .thenReturn(Optional.of(NOW.minusSeconds(10)));
    assertThat(auditHealthService.isAuditLogDelayed()).isFalse();
  }

  @Test
  @DisplayName(
      "initializeOrRefreshThreshold sets ZERO threshold if repository returns negative interval")
  void initializeOrRefreshThreshold_setsZeroOnNegativeInterval() {
    // given
    Instant sinceTime = NOW.minus(THRESHOLD_CALCULATION_PERIOD_DAYS, ChronoUnit.DAYS);
    when(mockAuditHealthRepository.findLongestIntervalSecondsSince(sinceTime))
        .thenReturn(createInterval(-100.0));
    // when
    auditHealthService.initializeOrRefreshThreshold();
    // then
    when(mockAuditHealthRepository.findLastAuditEventTimestamp())
        .thenReturn(Optional.of(NOW.minusSeconds(10)));
    assertThat(auditHealthService.isAuditLogDelayed()).isFalse();
  }

  @Test
  @DisplayName(
      "initializeOrRefreshThreshold sets ZERO threshold if projection returns null interval")
  void initializeOrRefreshThreshold_setsZeroWhenProjectionReturnsNullInterval() {
    // given
    Instant sinceTime = NOW.minus(THRESHOLD_CALCULATION_PERIOD_DAYS, ChronoUnit.DAYS);
    when(mockAuditHealthRepository.findLongestIntervalSecondsSince(sinceTime))
        .thenReturn(createInterval(null));
    // when
    auditHealthService.initializeOrRefreshThreshold();
    // then
    when(mockAuditHealthRepository.findLastAuditEventTimestamp())
        .thenReturn(Optional.of(NOW.minusSeconds(10)));
    assertThat(auditHealthService.isAuditLogDelayed()).isFalse();
  }

  @Test
  @DisplayName("initializeOrRefreshThreshold handles repository exception")
  void initializeOrRefreshThreshold_handlesException() {
    // given
    Instant sinceTime = NOW.minus(THRESHOLD_CALCULATION_PERIOD_DAYS, ChronoUnit.DAYS);
    when(mockAuditHealthRepository.findLongestIntervalSecondsSince(sinceTime))
        .thenThrow(new RuntimeException("DB error"));
    // when
    auditHealthService.initializeOrRefreshThreshold();
    // then
    when(mockAuditHealthRepository.findLastAuditEventTimestamp())
        .thenReturn(Optional.of(NOW.minusSeconds(10)));
    assertThat(auditHealthService.isAuditLogDelayed()).isFalse();
  }

  @Test
  @DisplayName(
      "isAuditLogDelayed returns true if time since last event exceeds effective threshold")
  void isAuditLogDelayed_trueWhenDelayed() {
    // given
    auditHealthService.setMaxIntervalThresholdForTest(Duration.ofHours(1));
    Instant lastEventTime = NOW.minusSeconds(4321);
    when(mockAuditHealthRepository.findLastAuditEventTimestamp())
        .thenReturn(Optional.of(lastEventTime));
    // when
    boolean isDelayed = auditHealthService.isAuditLogDelayed();
    // then
    assertThat(isDelayed).isTrue();
  }

  @Test
  @DisplayName("isAuditLogDelayed returns false if time is within effective threshold")
  void isAuditLogDelayed_falseWhenNotDelayed() {
    // given
    auditHealthService.setMaxIntervalThresholdForTest(Duration.ofHours(1));
    Instant lastEventTime = NOW.minusSeconds(4319);
    when(mockAuditHealthRepository.findLastAuditEventTimestamp())
        .thenReturn(Optional.of(lastEventTime));
    // when
    boolean isDelayed = auditHealthService.isAuditLogDelayed();
    // then
    assertThat(isDelayed).isFalse();
  }

  @Test
  @DisplayName("isAuditLogDelayed returns false if time is exactly at effective threshold")
  void isAuditLogDelayed_falseAtEffectiveThreshold() {
    // given
    auditHealthService.setMaxIntervalThresholdForTest(Duration.ofHours(1));
    Instant lastEventTime = NOW.minusSeconds(4320);
    when(mockAuditHealthRepository.findLastAuditEventTimestamp())
        .thenReturn(Optional.of(lastEventTime));
    // when
    boolean isDelayed = auditHealthService.isAuditLogDelayed();
    // then
    assertThat(isDelayed).isFalse();
  }

  @Test
  @DisplayName(
      "isAuditLogDelayed returns true if no audit events ever and non-zero threshold exists")
  void isAuditLogDelayed_trueWhenNoEventsAndThresholdExists() {
    // given
    auditHealthService.setMaxIntervalThresholdForTest(Duration.ofHours(1));
    when(mockAuditHealthRepository.findLastAuditEventTimestamp()).thenReturn(Optional.empty());
    // when
    boolean isDelayed = auditHealthService.isAuditLogDelayed();
    // then
    assertThat(isDelayed).isTrue();
  }

  @Test
  @DisplayName("isAuditLogDelayed returns false if no audit events and ZERO threshold")
  void isAuditLogDelayed_falseWhenNoEventsAndZeroThreshold() {
    // given
    auditHealthService.setMaxIntervalThresholdForTest(Duration.ZERO);
    when(mockAuditHealthRepository.findLastAuditEventTimestamp()).thenReturn(Optional.empty());
    // when
    boolean isDelayed = auditHealthService.isAuditLogDelayed();
    // then
    assertThat(isDelayed).isFalse();
  }

  @Test
  @DisplayName("isAuditLogDelayed returns false if threshold is ZERO and events exist")
  void isAuditLogDelayed_falseWhenZeroThresholdAndEventsExist() {
    // given
    auditHealthService.setMaxIntervalThresholdForTest(Duration.ZERO);
    when(mockAuditHealthRepository.findLastAuditEventTimestamp())
        .thenReturn(Optional.of(NOW.minusSeconds(10)));
    // when
    boolean isDelayed = auditHealthService.isAuditLogDelayed();
    // then
    assertThat(isDelayed).isFalse();
  }
}
