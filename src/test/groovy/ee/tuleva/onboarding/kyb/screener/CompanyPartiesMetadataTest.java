package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybKycStatus.PENDING;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOnly;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.kybPerson;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.shareholderOwner;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompanyPartiesMetadataTest {

  @Test
  void listsEachPartyWithItsCodeNaturalPersonhoodAndRolesInAFixedOrder() {
    var owner = boardMemberOwner("38888888888", 100.0).build();

    var metadata = CompanyPartiesMetadata.of(List.of(owner));

    assertThat(metadata)
        .isEqualTo(
            Map.of(
                "parties",
                List.of(
                    Map.of(
                        "code",
                        "38888888888",
                        "naturalPerson",
                        true,
                        "roles",
                        List.of("BOARD_MEMBER", "SHAREHOLDER", "BENEFICIAL_OWNER")))));
  }

  @Test
  void sortsPartiesByCodeSoTheRegisterOrderDoesNotChangeTheMetadata() {
    var first = boardMemberOnly("38888888888").build();
    var second = shareholderOwner("48888888888", 100.0).build();
    var legalEntity =
        shareholderOwner("18888888", 0.0).naturalPerson(false).beneficialOwner(false).build();

    var metadata = CompanyPartiesMetadata.of(List.of(second, first, legalEntity));

    assertThat(metadata).isEqualTo(CompanyPartiesMetadata.of(List.of(legalEntity, first, second)));
    assertThat(metadata)
        .isEqualTo(
            Map.of(
                "parties",
                List.of(
                    Map.of(
                        "code",
                        "18888888",
                        "naturalPerson",
                        false,
                        "roles",
                        List.of("SHAREHOLDER")),
                    Map.of(
                        "code",
                        "38888888888",
                        "naturalPerson",
                        true,
                        "roles",
                        List.of("BOARD_MEMBER")),
                    Map.of(
                        "code",
                        "48888888888",
                        "naturalPerson",
                        true,
                        "roles",
                        List.of("SHAREHOLDER", "BENEFICIAL_OWNER")))));
  }

  @Test
  void leavesTheCodeOutForAPartyWithoutOneAndSortsItAfterThePartiesWithCodes() {
    var unidentified = kybPerson().naturalPerson(false).shareholder(true).build();
    var identified = boardMemberOnly("38888888888").build();

    var metadata = CompanyPartiesMetadata.of(List.of(unidentified, identified));

    assertThat(metadata)
        .isEqualTo(
            Map.of(
                "parties",
                List.of(
                    Map.of(
                        "code",
                        "38888888888",
                        "naturalPerson",
                        true,
                        "roles",
                        List.of("BOARD_MEMBER")),
                    Map.of("naturalPerson", false, "roles", List.of("SHAREHOLDER")))));
  }

  @Test
  void ordersPartiesWithoutCodesByTheirAttributesSoTheirRegisterOrderDoesNotMatter() {
    var naturalBoardMember = kybPerson().boardMember(true).build();
    var naturalShareholder = kybPerson().shareholder(true).build();
    var legalShareholder = kybPerson().naturalPerson(false).shareholder(true).build();

    assertThat(
            CompanyPartiesMetadata.of(
                List.of(naturalShareholder, legalShareholder, naturalBoardMember)))
        .isEqualTo(
            CompanyPartiesMetadata.of(
                List.of(naturalBoardMember, naturalShareholder, legalShareholder)));
  }

  @Test
  void ordersCodelessPartiesWithTheSameRolesByNaturalPersonhood() {
    var naturalShareholder = kybPerson().shareholder(true).build();
    var legalShareholder = kybPerson().naturalPerson(false).shareholder(true).build();

    assertThat(CompanyPartiesMetadata.of(List.of(naturalShareholder, legalShareholder)))
        .isEqualTo(CompanyPartiesMetadata.of(List.of(legalShareholder, naturalShareholder)));
  }

  @Test
  void ignoresTheOwnershipPercentBecauseOnlyWhoTheyAreAndTheirRolesMatter() {
    var before = shareholderOwner("38888888888", 40.0).build();
    var after = shareholderOwner("38888888888", 60.0).build();

    assertThat(CompanyPartiesMetadata.of(List.of(before)))
        .isEqualTo(CompanyPartiesMetadata.of(List.of(after)));
  }

  @Test
  void ignoresTheKycStatusBecauseRelatedPersonsKycTracksIt() {
    var pending = boardMemberOnly("38888888888").kycStatus(PENDING).build();
    var completed = boardMemberOnly("38888888888").build();

    assertThat(CompanyPartiesMetadata.of(List.of(pending)))
        .isEqualTo(CompanyPartiesMetadata.of(List.of(completed)));
  }

  @Test
  void recordsAnEmptyPartyListForACompanyWithoutRelatedPersons() {
    assertThat(CompanyPartiesMetadata.of(List.of())).isEqualTo(Map.of("parties", List.of()));
  }
}
