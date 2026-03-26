package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.RELATED_PERSONS_KYC;
import static ee.tuleva.onboarding.kyb.KybKycStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.CompanyDto;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import ee.tuleva.onboarding.kyb.KybKycStatus;
import ee.tuleva.onboarding.kyb.KybRelatedPerson;
import ee.tuleva.onboarding.kyb.LegalForm;
import ee.tuleva.onboarding.kyb.PersonalCode;
import ee.tuleva.onboarding.kyb.RegistryCode;
import ee.tuleva.onboarding.kyb.SelfCertification;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
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
                        new PersonalCode("38501010001"),
                        true,
                        true,
                        true,
                        BigDecimal.valueOf(100),
                        status))
            .toList();
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            new PersonalCode("38501010001"),
            R,
            persons,
            new SelfCertification(true, true, true));

    var result = screener.screen(data);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo(RELATED_PERSONS_KYC);
    assertThat(result.getFirst().success()).isEqualTo(expectedSuccess);
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
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(50), COMPLETED);
    var rejected =
        new KybRelatedPerson(
            new PersonalCode("38501010002"), true, true, true, BigDecimal.valueOf(50), REJECTED);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            new PersonalCode("38501010001"),
            R,
            List.of(completed, rejected),
            new SelfCertification(true, true, true));

    var result = screener.screen(data);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().success()).isFalse();

    var incompletePersons =
        (List<Map<String, String>>) result.getFirst().metadata().get("incompletePersons");
    assertThat(incompletePersons)
        .containsExactly(Map.of("personalCode", "38501010002", "kycStatus", "REJECTED"));
  }

  // TODO: entities without personal code are not supported at the moment
  @Disabled
  @Test
  @SuppressWarnings("unchecked")
  void handlesNullPersonalCodeInMetadata() {
    var withCode =
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(100), UNKNOWN);
    var withoutCode = new KybRelatedPerson(null, false, false, false, BigDecimal.ZERO, UNKNOWN);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            new PersonalCode("38501010001"),
            R,
            List.of(withCode, withoutCode),
            new SelfCertification(true, true, true));

    var result = screener.screen(data);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().success()).isFalse();

    var incompletePersons =
        (List<Map<String, String>>) result.getFirst().metadata().get("incompletePersons");
    assertThat(incompletePersons).hasSize(2);
  }

  @Test
  void successMetadataHasNoIncompletePersons() {
    var person =
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(100), COMPLETED);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            new PersonalCode("38501010001"),
            R,
            List.of(person),
            new SelfCertification(true, true, true));

    var result = screener.screen(data);

    assertThat(result).contains(new KybCheck(RELATED_PERSONS_KYC, true, Map.of()));
  }
}
