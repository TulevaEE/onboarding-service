package ee.tuleva.onboarding.aml.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AmlHealthCheckServiceTest {

  @Mock private AmlHealthThresholdCache mockAmlHealthThresholdCache;

  @Mock private AmlCheckHealthRepository mockAmlCheckHealthRepository;

  @InjectMocks private AmlHealthCheckService amlHealthCheckService;

  private final Instant NOW_INSTANT = Instant.parse("2025-05-16T12:00:00.00Z");

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(NOW_INSTANT, ZoneId.of("UTC"));
    ClockHolder.setClock(fixedClock);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void isCheckTypeDelayed_whenTimeSinceLastCheckExceedsThresholdPlusGrace_returnsTrue() {
    // given
    AmlCheckType checkType = AmlCheckType.CONTACT_DETAILS;
    Duration baseThreshold = Duration.ofHours(1); // 3600 seconds
    // 20% grace = 720 seconds. Effective threshold = 3600 + 720 = 4320 seconds
    // Last check was 1 hour and 13 minutes ago (3600 + 780 = 4380 seconds ago)
    Instant lastCheckTime =
        NOW_INSTANT.minus(baseThreshold).minus(Duration.ofMinutes(13)); // Exceeds grace

    when(mockAmlHealthThresholdCache.getThreshold(checkType.name()))
        .thenReturn(Optional.of(baseThreshold));
    when(mockAmlCheckHealthRepository.findLastCheckTimeByType(checkType.name()))
        .thenReturn(Optional.of(lastCheckTime));

    // when
    boolean isDelayed = amlHealthCheckService.isCheckTypeDelayed(checkType);

    // then
    assertThat(isDelayed).isTrue();
  }

  @Test
  void isCheckTypeDelayed_whenTimeSinceLastCheckIsWithinThresholdPlusGrace_returnsFalse() {
    // given
    AmlCheckType checkType = AmlCheckType.DOCUMENT;
    Duration baseThreshold = Duration.ofHours(1); // 3600 seconds
    // 20% grace = 720 seconds. Effective threshold = 4320 seconds
    // Last check was 1 hour and 10 minutes ago (3600 + 600 = 4200 seconds ago)
    Instant lastCheckTime =
        NOW_INSTANT.minus(baseThreshold).minus(Duration.ofMinutes(10)); // Within grace

    when(mockAmlHealthThresholdCache.getThreshold(checkType.name()))
        .thenReturn(Optional.of(baseThreshold));
    when(mockAmlCheckHealthRepository.findLastCheckTimeByType(checkType.name()))
        .thenReturn(Optional.of(lastCheckTime));

    // when
    boolean isDelayed = amlHealthCheckService.isCheckTypeDelayed(checkType);

    // then
    assertThat(isDelayed).isFalse();
  }

  @Test
  void isCheckTypeDelayed_whenTimeSinceLastCheckIsExactlyAtEffectiveThreshold_returnsFalse() {
    // given
    AmlCheckType checkType = AmlCheckType.PENSION_REGISTRY_NAME;
    Duration baseThreshold = Duration.ofMinutes(100); // 6000 seconds
    long graceNanos = (long) (baseThreshold.toNanos() * 0.2); // 20% grace = 1200 seconds
    Duration effectiveThreshold = baseThreshold.plusNanos(graceNanos); // 120 minutes

    Instant lastCheckTime = NOW_INSTANT.minus(effectiveThreshold); // Exactly at effective threshold

    when(mockAmlHealthThresholdCache.getThreshold(checkType.name()))
        .thenReturn(Optional.of(baseThreshold));
    when(mockAmlCheckHealthRepository.findLastCheckTimeByType(checkType.name()))
        .thenReturn(Optional.of(lastCheckTime));

    // when
    boolean isDelayed = amlHealthCheckService.isCheckTypeDelayed(checkType);

    // then
    assertThat(isDelayed).isFalse();
  }

  @Test
  void isCheckTypeDelayed_whenTimeSinceLastCheckIsJustOverEffectiveThreshold_returnsTrue() {
    // given
    AmlCheckType checkType = AmlCheckType.RISK_LEVEL;
    Duration baseThreshold = Duration.ofMinutes(100); // 6000 seconds
    long graceNanos = (long) (baseThreshold.toNanos() * 0.2); // 20% grace = 1200 seconds
    Duration effectiveThreshold = baseThreshold.plusNanos(graceNanos); // 120 minutes

    Instant lastCheckTime =
        NOW_INSTANT.minus(effectiveThreshold).minusSeconds(1); // 1 second over effective threshold

    when(mockAmlHealthThresholdCache.getThreshold(checkType.name()))
        .thenReturn(Optional.of(baseThreshold));
    when(mockAmlCheckHealthRepository.findLastCheckTimeByType(checkType.name()))
        .thenReturn(Optional.of(lastCheckTime));

    // when
    boolean isDelayed = amlHealthCheckService.isCheckTypeDelayed(checkType);

    // then
    assertThat(isDelayed).isTrue();
  }

  @Test
  void isCheckTypeDelayed_whenTimeSinceLastCheckIsWellWithinBaseThreshold_returnsFalse() {
    // given
    AmlCheckType checkType = AmlCheckType.DOCUMENT;
    Duration baseThreshold = Duration.ofHours(2);
    Instant lastCheckTime =
        NOW_INSTANT.minus(Duration.ofHours(1)); // 1 hour ago, well within 2h threshold

    when(mockAmlHealthThresholdCache.getThreshold(checkType.name()))
        .thenReturn(Optional.of(baseThreshold));
    when(mockAmlCheckHealthRepository.findLastCheckTimeByType(checkType.name()))
        .thenReturn(Optional.of(lastCheckTime));

    // when
    boolean isDelayed = amlHealthCheckService.isCheckTypeDelayed(checkType);

    // then
    assertThat(isDelayed).isFalse();
  }

  @Test
  void isCheckTypeDelayed_whenNoThresholdDefined_returnsFalse() {
    // given
    AmlCheckType checkType = AmlCheckType.OCCUPATION;
    when(mockAmlHealthThresholdCache.getThreshold(checkType.name())).thenReturn(Optional.empty());

    // when
    boolean isDelayed = amlHealthCheckService.isCheckTypeDelayed(checkType);

    // then
    assertThat(isDelayed).isFalse();
    verify(mockAmlCheckHealthRepository, never()).findLastCheckTimeByType(checkType.name());
  }

  @Test
  void isCheckTypeDelayed_whenThresholdExistsButCheckNeverRan_returnsTrue() {
    // given
    AmlCheckType checkType = AmlCheckType.PENSION_REGISTRY_NAME;
    Duration baseThreshold = Duration.ofMinutes(30);

    when(mockAmlHealthThresholdCache.getThreshold(checkType.name()))
        .thenReturn(Optional.of(baseThreshold));
    when(mockAmlCheckHealthRepository.findLastCheckTimeByType(checkType.name()))
        .thenReturn(Optional.empty());

    // when
    boolean isDelayed = amlHealthCheckService.isCheckTypeDelayed(checkType);

    // then
    assertThat(isDelayed).isTrue();
  }

  @Test
  void isCheckTypeDelayed_whenTimeSinceLastCheckEqualsBaseThreshold_returnsFalse() {
    // given
    AmlCheckType checkType = AmlCheckType.POLITICALLY_EXPOSED_PERSON;
    Duration baseThreshold = Duration.ofHours(1); // 3600 seconds
    // Effective threshold = 4320 seconds
    Instant lastCheckTime =
        NOW_INSTANT.minus(baseThreshold); // Exactly 1 hour ago (3600s), within grace

    when(mockAmlHealthThresholdCache.getThreshold(checkType.name()))
        .thenReturn(Optional.of(baseThreshold));
    when(mockAmlCheckHealthRepository.findLastCheckTimeByType(checkType.name()))
        .thenReturn(Optional.of(lastCheckTime));

    // when
    boolean isDelayed = amlHealthCheckService.isCheckTypeDelayed(checkType);

    // then
    assertThat(isDelayed).isFalse();
  }
}
