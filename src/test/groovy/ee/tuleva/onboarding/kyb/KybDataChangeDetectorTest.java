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
  private static final RegistryCode REGISTRY_CODE = new RegistryCode("12345678");

  @Test
  void noPreviousChecksReturnsSuccess() {
    when(checkHistory.getLatestChecks(PERSONAL_CODE, REGISTRY_CODE)).thenReturn(List.of());

    var currentChecks = List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")));
    var result = detector.detect(PERSONAL_CODE, REGISTRY_CODE, currentChecks);

    assertThat(result.type()).isEqualTo(DATA_CHANGED);
    assertThat(result.success()).isTrue();
    assertThat(result.metadata()).containsKey("changes");
    assertThat((List<?>) result.metadata().get("changes")).isEmpty();
  }

  @Test
  void identicalResultsReturnsSuccess() {
    var checks = List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")));
    when(checkHistory.getLatestChecks(PERSONAL_CODE, REGISTRY_CODE)).thenReturn(checks);

    var result = detector.detect(PERSONAL_CODE, REGISTRY_CODE, checks);

    assertThat(result.success()).isTrue();
    assertThat((List<?>) result.metadata().get("changes")).isEmpty();
  }

  @Test
  void successFlipDetected() {
    var previousChecks = List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")));
    var currentChecks = List.of(new KybCheck(COMPANY_ACTIVE, false, Map.of("status", "L")));
    when(checkHistory.getLatestChecks(PERSONAL_CODE, REGISTRY_CODE)).thenReturn(previousChecks);

    var result = detector.detect(PERSONAL_CODE, REGISTRY_CODE, currentChecks);

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
    when(checkHistory.getLatestChecks(PERSONAL_CODE, REGISTRY_CODE)).thenReturn(previousChecks);

    var result = detector.detect(PERSONAL_CODE, REGISTRY_CODE, currentChecks);

    assertThat(result.success()).isFalse();
    var changes = (List<Map<String, Object>>) result.metadata().get("changes");
    assertThat(changes).hasSize(1);
    assertThat(changes.getFirst()).containsEntry("check", "SOLE_MEMBER_OWNERSHIP");
  }

  @Test
  void newPassingCheckTypeDoesNotCountAsChange() {
    // Screener gained a check (e.g. COMPANY_AGE). For an existing company this is a screener
    // expansion, not a data change — it must not raise DATA_CHANGED (AML #78 false positive that
    // flagged ~96% of companies after COMPANY_AGE / COMPANY_LEGAL_FORM were added).
    var previousChecks = List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")));
    var currentChecks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")),
            new KybCheck(COMPANY_AGE, true, Map.of()));
    when(checkHistory.getLatestChecks(PERSONAL_CODE, REGISTRY_CODE)).thenReturn(previousChecks);

    var result = detector.detect(PERSONAL_CODE, REGISTRY_CODE, currentChecks);

    assertThat(result.success()).isTrue();
    assertThat((List<?>) result.metadata().get("changes")).isEmpty();
  }

  @Test
  void newFailingCheckTypeDoesNotCountAsChange() {
    // Even a newly introduced FAILING check is the new check's own risk (scored on its own
    // aml_check row), not a change in existing data — DATA_CHANGED stays clear.
    var previousChecks = List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")));
    var currentChecks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")),
            new KybCheck(COMPANY_AGE, false, Map.of()));
    when(checkHistory.getLatestChecks(PERSONAL_CODE, REGISTRY_CODE)).thenReturn(previousChecks);

    var result = detector.detect(PERSONAL_CODE, REGISTRY_CODE, currentChecks);

    assertThat(result.success()).isTrue();
    assertThat((List<?>) result.metadata().get("changes")).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void structuralCheckSwapStillCountsAsChange() {
    // A genuine structural change (sole- -> dual-member) swaps one conditional check for another.
    // The disappearing check still raises DATA_CHANGED via the removed-check path, so real changes
    // are NOT lost by baselining newly introduced check types.
    var previousChecks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")),
            new KybCheck(SOLE_MEMBER_OWNERSHIP, true, Map.of()));
    var currentChecks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")),
            new KybCheck(DUAL_MEMBER_OWNERSHIP, true, Map.of()));
    when(checkHistory.getLatestChecks(PERSONAL_CODE, REGISTRY_CODE)).thenReturn(previousChecks);

    var result = detector.detect(PERSONAL_CODE, REGISTRY_CODE, currentChecks);

    assertThat(result.success()).isFalse();
    var changes = (List<Map<String, Object>>) result.metadata().get("changes");
    assertThat(changes).hasSize(1);
    assertThat(changes.getFirst())
        .containsEntry("check", "SOLE_MEMBER_OWNERSHIP")
        .containsEntry("currentSuccess", "N/A");
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
    when(checkHistory.getLatestChecks(PERSONAL_CODE, REGISTRY_CODE)).thenReturn(previousChecks);

    var result = detector.detect(PERSONAL_CODE, REGISTRY_CODE, currentChecks);

    assertThat(result.success()).isFalse();
    var changes = (List<Map<String, Object>>) result.metadata().get("changes");
    assertThat(changes).hasSize(2);
  }

  @Test
  @SuppressWarnings("unchecked")
  void successFlipWithUnchangedMetadataIsMarkedInconclusive() {
    var metadata = Map.<String, Object>of("personalCode", "38501010001", "ownershipPercent", "100");
    var previousChecks = List.of(new KybCheck(SOLE_MEMBER_OWNERSHIP, true, metadata));
    var currentChecks = List.of(new KybCheck(SOLE_MEMBER_OWNERSHIP, false, metadata));
    when(checkHistory.getLatestChecks(PERSONAL_CODE, REGISTRY_CODE)).thenReturn(previousChecks);

    var result = detector.detect(PERSONAL_CODE, REGISTRY_CODE, currentChecks);

    var changes = (List<Map<String, Object>>) result.metadata().get("changes");
    assertThat(changes).hasSize(1);
    assertThat(changes.getFirst())
        .containsEntry("check", "SOLE_MEMBER_OWNERSHIP")
        .containsEntry("metadataChanged", false);
  }

  @Test
  @SuppressWarnings("unchecked")
  void metadataChangeIsMarkedAsEvidence() {
    var previousChecks =
        List.of(
            new KybCheck(
                SOLE_MEMBER_OWNERSHIP,
                true,
                Map.of("personalCode", "38501010001", "ownershipPercent", "100")));
    var currentChecks =
        List.of(
            new KybCheck(
                SOLE_MEMBER_OWNERSHIP,
                false,
                Map.of("personalCode", "39901010000", "ownershipPercent", "100")));
    when(checkHistory.getLatestChecks(PERSONAL_CODE, REGISTRY_CODE)).thenReturn(previousChecks);

    var result = detector.detect(PERSONAL_CODE, REGISTRY_CODE, currentChecks);

    var changes = (List<Map<String, Object>>) result.metadata().get("changes");
    assertThat(changes).hasSize(1);
    assertThat(changes.getFirst())
        .containsEntry("check", "SOLE_MEMBER_OWNERSHIP")
        .containsEntry("metadataChanged", true);
  }

  @Test
  @SuppressWarnings("unchecked")
  void removedCheckIsMarkedAsMetadataChanged() {
    var previousChecks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")),
            new KybCheck(SOLE_MEMBER_OWNERSHIP, true, Map.of()));
    var currentChecks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")),
            new KybCheck(DUAL_MEMBER_OWNERSHIP, true, Map.of()));
    when(checkHistory.getLatestChecks(PERSONAL_CODE, REGISTRY_CODE)).thenReturn(previousChecks);

    var result = detector.detect(PERSONAL_CODE, REGISTRY_CODE, currentChecks);

    var changes = (List<Map<String, Object>>) result.metadata().get("changes");
    assertThat(changes).hasSize(1);
    assertThat(changes.getFirst())
        .containsEntry("check", "SOLE_MEMBER_OWNERSHIP")
        .containsEntry("metadataChanged", true);
  }

  @Test
  void volatileSanctionMetadataAloneDoesNotCountAsChange() {
    var previousChecks =
        List.of(new KybCheck(COMPANY_SANCTION, true, Map.of("results", "[{id=Q1, score=0.31}]")));
    var currentChecks =
        List.of(new KybCheck(COMPANY_SANCTION, true, Map.of("results", "[{id=Q2, score=0.62}]")));
    when(checkHistory.getLatestChecks(PERSONAL_CODE, REGISTRY_CODE)).thenReturn(previousChecks);

    var result = detector.detect(PERSONAL_CODE, REGISTRY_CODE, currentChecks);

    assertThat(result.success()).isTrue();
    assertThat((List<?>) result.metadata().get("changes")).isEmpty();
  }

  @Test
  void sanctionSuccessFlipStillCountsAsChange() {
    var previousChecks = List.of(new KybCheck(COMPANY_SANCTION, true, Map.of("results", "[]")));
    var currentChecks =
        List.of(new KybCheck(COMPANY_SANCTION, false, Map.of("results", "[{match=true}]")));
    when(checkHistory.getLatestChecks(PERSONAL_CODE, REGISTRY_CODE)).thenReturn(previousChecks);

    var result = detector.detect(PERSONAL_CODE, REGISTRY_CODE, currentChecks);

    assertThat(result.success()).isFalse();
  }
}
