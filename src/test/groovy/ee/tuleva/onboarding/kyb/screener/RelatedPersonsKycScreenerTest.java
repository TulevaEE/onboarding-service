package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.RELATED_PERSONS_KYC;
import static ee.tuleva.onboarding.kyb.KybKycStatus.*;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.companyWith;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.kybPerson;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybKycStatus;
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
            .map(status -> boardMemberOwner("38501010001", 100.0).kycStatus(status).build())
            .toList();
    var data = companyWith(persons);

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
    var completed = boardMemberOwner("38501010001", 50.0).kycStatus(COMPLETED).build();
    var rejected = boardMemberOwner("38501010002", 50.0).kycStatus(REJECTED).build();
    var data = companyWith(completed, rejected);

    var result = screener.screen(data);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().success()).isFalse();

    var incompletePersons =
        (List<Map<String, String>>) result.getFirst().metadata().get("incompletePersons");
    assertThat(incompletePersons)
        .containsExactly(Map.of("personalCode", "38501010002", "kycStatus", "REJECTED"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void handlesNullPersonalCodeInMetadata() {
    var withCode = boardMemberOwner("38501010001", 100.0).build();
    var withoutCode = kybPerson().personalCode(null).build();
    var data = companyWith(withCode, withoutCode);

    var result = screener.screen(data);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().success()).isFalse();

    var incompletePersons =
        (List<Map<String, String>>) result.getFirst().metadata().get("incompletePersons");
    assertThat(incompletePersons)
        .containsExactlyInAnyOrder(
            Map.of("personalCode", "38501010001", "kycStatus", "UNKNOWN"),
            Map.of("kycStatus", "UNKNOWN"));
  }

  @Test
  void successMetadataHasNoIncompletePersons() {
    var person = boardMemberOwner("38501010001", 100.0).kycStatus(COMPLETED).build();
    var data = companyWith(person);

    var result = screener.screen(data);

    assertThat(result).contains(new KybCheck(RELATED_PERSONS_KYC, true, Map.of()));
  }
}
