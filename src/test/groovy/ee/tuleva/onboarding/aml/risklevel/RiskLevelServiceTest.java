package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.AmlCheckType.RISK_LEVEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class RiskLevelServiceTest {

  private final AmlCheckRepository amlCheckRepository = mock(AmlCheckRepository.class);
  private final RiskLevelService riskLevelService =
      new RiskLevelService(amlCheckRepository, mock(ApplicationEventPublisher.class));

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(TestClockHolder.clock);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void addCheckIfMissing_returnsFalse_forSameMetadataInstance() {
    Map<String, Object> sharedMetadata = new HashMap<>();
    sharedMetadata.put("key", "value");
    AmlCheck newCheck = check(sharedMetadata);
    givenExistingChecks(check(sharedMetadata));

    boolean result = riskLevelService.addCheckIfMissing(newCheck);

    assertThat(result).isFalse();
    verify(amlCheckRepository, never()).save(any());
  }

  @Test
  void addCheckIfMissing_returnsTrue_whenNewMetadataIsNull() {
    AmlCheck newCheck = check(null);
    givenExistingChecks(check(Map.of("key", "value")));
    given(amlCheckRepository.save(any(AmlCheck.class))).willReturn(newCheck);

    boolean result = riskLevelService.addCheckIfMissing(newCheck);

    assertThat(result).isTrue();
    verify(amlCheckRepository).save(newCheck);
  }

  @Test
  void addCheckIfMissing_returnsTrue_whenExistingMetadataIsNull() {
    AmlCheck newCheck = check(Map.of("key", "value"));
    givenExistingChecks(check(null));
    given(amlCheckRepository.save(any(AmlCheck.class))).willReturn(newCheck);

    boolean result = riskLevelService.addCheckIfMissing(newCheck);

    assertThat(result).isTrue();
    verify(amlCheckRepository).save(newCheck);
  }

  private void givenExistingChecks(AmlCheck... existingChecks) {
    given(
            amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
                any(), any(), any()))
        .willReturn(List.of(existingChecks));
  }

  private static AmlCheck check(Map<String, Object> metadata) {
    return AmlCheck.builder().personalCode("12345").metadata(metadata).type(RISK_LEVEL).build();
  }
}
