package ee.tuleva.onboarding.aml.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AmlHealthThresholdCacheTest {

  @Mock private AmlCheckHealthRepository mockAmlCheckHealthRepository;

  @InjectMocks private AmlHealthThresholdCache amlHealthThresholdCache;

  private final Instant NOW_INSTANT = Instant.parse("2025-05-16T12:00:00.00Z");
  private final Clock FIXED_CLOCK = Clock.fixed(NOW_INSTANT, ZoneId.of("UTC"));

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(FIXED_CLOCK);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  private AmlCheckTypeHealthThreshold mockThresholdDto(String type, Double seconds) {
    AmlCheckTypeHealthThreshold dto = mock(AmlCheckTypeHealthThreshold.class);
    lenient().when(dto.getType()).thenReturn(type);
    lenient().when(dto.getMaxIntervalSeconds()).thenReturn(seconds);
    return dto;
  }

  @Test
  void refreshThresholds_loadsDataFromRepository() {
    // given
    AmlCheckTypeHealthThreshold typeADto =
        mockThresholdDto(AmlCheckType.CONTACT_DETAILS.name(), 3600.0);
    AmlCheckTypeHealthThreshold typeBDto = mockThresholdDto(AmlCheckType.DOCUMENT.name(), 1800.0);
    List<AmlCheckTypeHealthThreshold> mockResults = Arrays.asList(typeADto, typeBDto);

    Instant expectedSinceTime = NOW_INSTANT.minus(30, ChronoUnit.DAYS);
    when(mockAmlCheckHealthRepository.findMaxIntervalSecondsPerTypeSince(expectedSinceTime))
        .thenReturn(mockResults);

    // when
    amlHealthThresholdCache.refreshThresholds();

    // then
    Optional<Duration> thresholdA =
        amlHealthThresholdCache.getThreshold(AmlCheckType.CONTACT_DETAILS.name());
    Optional<Duration> thresholdB =
        amlHealthThresholdCache.getThreshold(AmlCheckType.DOCUMENT.name());
    Optional<Duration> thresholdC =
        amlHealthThresholdCache.getThreshold(AmlCheckType.OCCUPATION.name());

    assertThat(thresholdA).isPresent().contains(Duration.ofSeconds(3600));
    assertThat(thresholdB).isPresent().contains(Duration.ofSeconds(1800));
    assertThat(thresholdC).isEmpty();
    verify(mockAmlCheckHealthRepository, times(1))
        .findMaxIntervalSecondsPerTypeSince(expectedSinceTime);
  }

  @Test
  void refreshThresholds_handlesNullTypeOrSecondsGracefully() {
    // given
    AmlCheckTypeHealthThreshold typeValidDto =
        mockThresholdDto(AmlCheckType.CONTACT_DETAILS.name(), 3600.0);
    AmlCheckTypeHealthThreshold typeNullTypeDto = mockThresholdDto(null, 1800.0);
    AmlCheckTypeHealthThreshold typeNullSecondsDto =
        mockThresholdDto(AmlCheckType.DOCUMENT.name(), null);
    List<AmlCheckTypeHealthThreshold> mockResults =
        Arrays.asList(typeValidDto, typeNullTypeDto, typeNullSecondsDto);
    Instant expectedSinceTime = NOW_INSTANT.minus(30, ChronoUnit.DAYS);
    when(mockAmlCheckHealthRepository.findMaxIntervalSecondsPerTypeSince(expectedSinceTime))
        .thenReturn(mockResults);

    // when
    amlHealthThresholdCache.refreshThresholds();

    // then
    assertThat(amlHealthThresholdCache.getThreshold(AmlCheckType.CONTACT_DETAILS.name()))
        .isPresent()
        .contains(Duration.ofSeconds(3600));
    assertThat(amlHealthThresholdCache.getThreshold(AmlCheckType.DOCUMENT.name())).isEmpty();
    assertThat(amlHealthThresholdCache.getAllThresholds().size()).isEqualTo(1);
  }

  @Test
  void refreshThresholds_handlesNegativeSecondsGracefully() {
    // given
    AmlCheckTypeHealthThreshold typeNegativeSecondsDto =
        mockThresholdDto(AmlCheckType.CONTACT_DETAILS.name(), -3600.0);
    List<AmlCheckTypeHealthThreshold> mockResults = List.of(typeNegativeSecondsDto);
    Instant expectedSinceTime = NOW_INSTANT.minus(30, ChronoUnit.DAYS);
    when(mockAmlCheckHealthRepository.findMaxIntervalSecondsPerTypeSince(expectedSinceTime))
        .thenReturn(mockResults);

    // when
    amlHealthThresholdCache.refreshThresholds();

    // then
    assertThat(amlHealthThresholdCache.getThreshold(AmlCheckType.CONTACT_DETAILS.name())).isEmpty();
    assertThat(amlHealthThresholdCache.getAllThresholds()).isEmpty();
  }

  @Test
  void refreshThresholds_handlesEmptyResultsFromRepository() {
    // given
    Instant expectedSinceTime = NOW_INSTANT.minus(30, ChronoUnit.DAYS);
    when(mockAmlCheckHealthRepository.findMaxIntervalSecondsPerTypeSince(expectedSinceTime))
        .thenReturn(Collections.emptyList());
    amlHealthThresholdCache.updateThresholdsForTest(Map.of("OLD_TYPE", Duration.ofHours(1)));

    // when
    amlHealthThresholdCache.refreshThresholds();

    // then
    assertThat(amlHealthThresholdCache.getAllThresholds()).isEmpty();
  }

  @Test
  void refreshThresholds_handlesRepositoryException() {
    // given
    Instant expectedSinceTime = NOW_INSTANT.minus(30, ChronoUnit.DAYS);
    when(mockAmlCheckHealthRepository.findMaxIntervalSecondsPerTypeSince(expectedSinceTime))
        .thenThrow(new RuntimeException("DB error"));
    amlHealthThresholdCache.updateThresholdsForTest(
        Map.of("EXISTING_TYPE", Duration.ofMinutes(10)));

    // when
    amlHealthThresholdCache.refreshThresholds();

    // then
    assertThat(amlHealthThresholdCache.getThreshold("EXISTING_TYPE"))
        .isPresent()
        .contains(Duration.ofMinutes(10));
  }

  @Test
  void getThreshold_returnsCorrectDurationForExistingType() {
    // given
    Map<String, Duration> testData = Map.of(AmlCheckType.RISK_LEVEL.name(), Duration.ofHours(5));
    amlHealthThresholdCache.updateThresholdsForTest(testData);

    // when
    Optional<Duration> threshold =
        amlHealthThresholdCache.getThreshold(AmlCheckType.RISK_LEVEL.name());

    // then
    assertThat(threshold).isPresent().contains(Duration.ofHours(5));
  }

  @Test
  void getThreshold_returnsEmptyForNonExistingType() {
    // given
    amlHealthThresholdCache.updateThresholdsForTest(Collections.emptyMap());

    // when
    Optional<Duration> threshold = amlHealthThresholdCache.getThreshold("NON_EXISTENT_TYPE_STRING");

    // then
    assertThat(threshold).isEmpty();
  }
}
