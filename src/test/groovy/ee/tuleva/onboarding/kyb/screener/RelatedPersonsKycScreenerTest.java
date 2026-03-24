package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.RELATED_PERSONS_KYC;
import static ee.tuleva.onboarding.kyb.KybKycStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import ee.tuleva.onboarding.kyb.KybKycStatus;
import ee.tuleva.onboarding.kyb.KybRelatedPerson;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RelatedPersonsKycScreenerTest {

  private final RelatedPersonsKycScreener screener = new RelatedPersonsKycScreener();

  @ParameterizedTest
  @MethodSource("kycStatusScenarios")
  void checksAllRelatedPersonsKycStatus(List<KybKycStatus> statuses, boolean expectedSuccess) {
    var persons =
        statuses.stream()
            .map(
                status ->
                    new KybRelatedPerson(
                        "38501010001", true, true, true, BigDecimal.valueOf(100), status))
            .toList();
    var data = new KybCompanyData("12345678", "38501010001", R, persons);

    var result = screener.screen(data);

    assertThat(result).isPresent();
    assertThat(result.get().type()).isEqualTo(RELATED_PERSONS_KYC);
    assertThat(result.get().success()).isEqualTo(expectedSuccess);
  }

  static Stream<Arguments> kycStatusScenarios() {
    return Stream.of(
        Arguments.of(List.of(COMPLETED), true),
        Arguments.of(List.of(COMPLETED, COMPLETED), true),
        Arguments.of(List.of(PENDING), false),
        Arguments.of(List.of(COMPLETED, PENDING), false),
        Arguments.of(List.of(COMPLETED, REJECTED), false),
        Arguments.of(List.of(UNKNOWN), false));
  }

  @Test
  @SuppressWarnings("unchecked")
  void failureMetadataContainsIncompletePersons() {
    var completed =
        new KybRelatedPerson("38501010001", true, true, true, BigDecimal.valueOf(50), COMPLETED);
    var rejected =
        new KybRelatedPerson("38501010002", true, true, true, BigDecimal.valueOf(50), REJECTED);
    var data = new KybCompanyData("12345678", "38501010001", R, List.of(completed, rejected));

    var result = screener.screen(data);

    assertThat(result).isPresent();
    assertThat(result.get().success()).isFalse();

    var incompletePersons =
        (List<Map<String, String>>) result.get().metadata().get("incompletePersons");
    assertThat(incompletePersons)
        .containsExactly(Map.of("personalCode", "38501010002", "kycStatus", "REJECTED"));
  }

  @Test
  void successMetadataHasNoIncompletePersons() {
    var person =
        new KybRelatedPerson("38501010001", true, true, true, BigDecimal.valueOf(100), COMPLETED);
    var data = new KybCompanyData("12345678", "38501010001", R, List.of(person));

    var result = screener.screen(data);

    assertThat(result).contains(new KybCheck(RELATED_PERSONS_KYC, true, Map.of()));
  }
}
