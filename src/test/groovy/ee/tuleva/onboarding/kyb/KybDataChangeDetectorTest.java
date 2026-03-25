package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KybDataChangeDetectorTest {

  private final KybCheckHistory checkHistory = mock(KybCheckHistory.class);
  private final KybDataChangeDetector detector = new KybDataChangeDetector(checkHistory);

  private static final PersonalCode PERSONAL_CODE = new PersonalCode("38501010001");

  @Test
  void noPreviousChecksReturnsSuccess() {
    when(checkHistory.getLatestChecks(PERSONAL_CODE)).thenReturn(List.of());

    var currentChecks = List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")));
    var result = detector.detect(PERSONAL_CODE, currentChecks);

    assertThat(result.type()).isEqualTo(DATA_CHANGED);
    assertThat(result.success()).isTrue();
    assertThat(result.metadata()).containsKey("changes");
    assertThat((List<?>) result.metadata().get("changes")).isEmpty();
  }

  @Test
  void identicalResultsReturnsSuccess() {
    var checks = List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")));
    when(checkHistory.getLatestChecks(PERSONAL_CODE)).thenReturn(checks);

    var result = detector.detect(PERSONAL_CODE, checks);

    assertThat(result.success()).isTrue();
    assertThat((List<?>) result.metadata().get("changes")).isEmpty();
  }

  @Test
  void successFlipDetected() {
    var previousChecks = List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")));
    var currentChecks = List.of(new KybCheck(COMPANY_ACTIVE, false, Map.of("status", "L")));
    when(checkHistory.getLatestChecks(PERSONAL_CODE)).thenReturn(previousChecks);

    var result = detector.detect(PERSONAL_CODE, currentChecks);

    assertThat(result.success()).isFalse();
    var changes = (List<Map<String, Object>>) result.metadata().get("changes");
    assertThat(changes).hasSize(1);
    assertThat(changes.getFirst())
        .containsEntry("check", "COMPANY_ACTIVE")
        .containsEntry("previousSuccess", true)
        .containsEntry("currentSuccess", false);
  }

  @Test
  void metadataChangeDetected() {
    var previousChecks =
        List.of(
            new KybCheck(
                SOLE_MEMBER_OWNERSHIP,
                true,
                Map.of("personalCode", "38501010001", "ownershipPercent", 100)));
    var currentChecks =
        List.of(
            new KybCheck(
                SOLE_MEMBER_OWNERSHIP,
                true,
                Map.of("personalCode", "38501010001", "ownershipPercent", 50)));
    when(checkHistory.getLatestChecks(PERSONAL_CODE)).thenReturn(previousChecks);

    var result = detector.detect(PERSONAL_CODE, currentChecks);

    assertThat(result.success()).isFalse();
    var changes = (List<Map<String, Object>>) result.metadata().get("changes");
    assertThat(changes).hasSize(1);
    assertThat(changes.getFirst()).containsEntry("check", "SOLE_MEMBER_OWNERSHIP");
  }

  @Test
  void newCheckTypeDetected() {
    var previousChecks = List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")));
    var currentChecks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")),
            new KybCheck(SOLE_MEMBER_OWNERSHIP, true, Map.of()));
    when(checkHistory.getLatestChecks(PERSONAL_CODE)).thenReturn(previousChecks);

    var result = detector.detect(PERSONAL_CODE, currentChecks);

    assertThat(result.success()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void multipleChangesDetected() {
    var previousChecks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")),
            new KybCheck(SOLE_MEMBER_OWNERSHIP, true, Map.of()));
    var currentChecks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, false, Map.of("status", "L")),
            new KybCheck(SOLE_MEMBER_OWNERSHIP, false, Map.of()));
    when(checkHistory.getLatestChecks(PERSONAL_CODE)).thenReturn(previousChecks);

    var result = detector.detect(PERSONAL_CODE, currentChecks);

    assertThat(result.success()).isFalse();
    var changes = (List<Map<String, Object>>) result.metadata().get("changes");
    assertThat(changes).hasSize(2);
  }
}
