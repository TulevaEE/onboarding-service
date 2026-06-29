package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCheckType;
import ee.tuleva.onboarding.kyb.PersonalCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AmlKybCheckHistoryTest {

  private final AmlCheckRepository repository = mock(AmlCheckRepository.class);
  private final AmlKybCheckHistory history = new AmlKybCheckHistory(repository);

  private static final PersonalCode PERSONAL_CODE = new PersonalCode("38501010001");

  @Test
  void returnsEmptyWhenNoChecks() {
    when(repository.findAllByPersonalCodeAndCreatedTimeAfter(eq("38501010001"), any()))
        .thenReturn(List.of());

    var result = history.getLatestChecks(PERSONAL_CODE);

    assertThat(result).isEmpty();
  }

  @Test
  void returnsLatestKybChecks() {
    var amlCheck =
        AmlCheck.builder()
            .personalCode("38501010001")
            .type(KYB_COMPANY_ACTIVE)
            .success(true)
            .metadata(Map.of("status", "R"))
            .build();
    amlCheck.setCreatedTime(Instant.now());
    when(repository.findAllByPersonalCodeAndCreatedTimeAfter(eq("38501010001"), any()))
        .thenReturn(List.of(amlCheck));

    var result = history.getLatestChecks(PERSONAL_CODE);

    assertThat(result).containsExactly(new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")));
  }

  @Test
  void excludesNonKybCheckTypes() {
    var sanctionCheck =
        AmlCheck.builder()
            .personalCode("38501010001")
            .type(SANCTION)
            .success(true)
            .metadata(Map.of())
            .build();
    sanctionCheck.setCreatedTime(Instant.now());
    var kybCheck =
        AmlCheck.builder()
            .personalCode("38501010001")
            .type(KYB_COMPANY_ACTIVE)
            .success(true)
            .metadata(Map.of("status", "R"))
            .build();
    kybCheck.setCreatedTime(Instant.now());
    when(repository.findAllByPersonalCodeAndCreatedTimeAfter(eq("38501010001"), any()))
        .thenReturn(List.of(sanctionCheck, kybCheck));

    var result = history.getLatestChecks(PERSONAL_CODE);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo(COMPANY_ACTIVE);
  }

  @Test
  void everyScreenerCheckTypeRoundTripsThroughHistory() {
    // AML #78 guard: every screener check type (KybCheckType, except the DATA_CHANGED result type)
    // must reverse-map, otherwise it is dropped from history, stays forever "new", and a real
    // change to it is hidden once the detector baselines newly introduced types. COMPANY_AGE and
    // COMPANY_LEGAL_FORM were the original gap.
    for (KybCheckType kybType : KybCheckType.values()) {
      if (kybType == DATA_CHANGED) {
        continue;
      }
      var amlType = AmlCheckType.valueOf("KYB_" + kybType.name());
      var check =
          AmlCheck.builder()
              .personalCode("38501010001")
              .type(amlType)
              .success(true)
              .metadata(Map.of())
              .build();
      check.setCreatedTime(Instant.now());
      when(repository.findAllByPersonalCodeAndCreatedTimeAfter(eq("38501010001"), any()))
          .thenReturn(List.of(check));

      var result = history.getLatestChecks(PERSONAL_CODE);

      assertThat(result)
          .as("screener check %s must reverse-map through AmlKybCheckHistory", kybType)
          .hasSize(1);
      assertThat(result.getFirst().type()).isEqualTo(kybType);
    }
  }
}
