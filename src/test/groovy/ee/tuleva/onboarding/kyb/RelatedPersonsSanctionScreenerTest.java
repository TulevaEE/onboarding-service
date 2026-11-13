package ee.tuleva.onboarding.kyb;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import ee.tuleva.onboarding.aml.SanctionAndPepScreener;
import ee.tuleva.onboarding.ariregister.BeneficialOwner;
import ee.tuleva.onboarding.ariregister.BeneficialOwners;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RelatedPersonsSanctionScreenerTest {

  private static final String REGISTRY_CODE = "12345678";
  private static final String BOARD_MEMBER = "38001085718";
  private static final String BENEFICIAL_OWNER = "40404049996";
  private static final String BOARD_MEMBER_WITH_ACCOUNT = "39901019992";
  private static final BeneficialOwners NO_BENEFICIAL_OWNERS = new BeneficialOwners(List.of(), 0);

  @Mock private SanctionAndPepScreener sanctionAndPepScreener;
  @Mock private UserRepository userRepository;
  @InjectMocks private RelatedPersonsSanctionScreener screener;

  @Test
  void screensEachBoardMemberAndBeneficialOwnerWithoutAUserAccountByTheirRegistryName() {
    given(userRepository.findByPersonalCode(any())).willReturn(Optional.empty());
    var boardMember = new PersonImpl(BOARD_MEMBER, "Kati", "Karu");
    given(sanctionAndPepScreener.recordedCitizenships(boardMember)).willReturn(Countries.of("FI"));

    screener.screen(
        REGISTRY_CODE,
        List.of(relationship("JUHL", BOARD_MEMBER, "Kati", "Karu")),
        new BeneficialOwners(
            List.of(new BeneficialOwner("Tiit", "Tihane", BENEFICIAL_OWNER, "O")), 0));

    verify(sanctionAndPepScreener).addSanctionAndPepCheckIfMissing(boardMember, Countries.of("FI"));
    verify(sanctionAndPepScreener)
        .addSanctionAndPepCheckIfMissing(
            new PersonImpl(BENEFICIAL_OWNER, "Tiit", "Tihane"), Set.of());
  }

  @Test
  void screensABoardMemberWithAUserAccountOnThatAccount() {
    var user = User.builder().personalCode(BOARD_MEMBER_WITH_ACCOUNT).build();
    given(userRepository.findByPersonalCode(BOARD_MEMBER_WITH_ACCOUNT))
        .willReturn(Optional.of(user));

    screener.screen(
        REGISTRY_CODE,
        List.of(relationship("JUHJ", BOARD_MEMBER_WITH_ACCOUNT, "Kati", "Karu")),
        NO_BENEFICIAL_OWNERS);

    verify(sanctionAndPepScreener).addSanctionAndPepCheckIfMissing(user);
    verifyNoMoreInteractions(sanctionAndPepScreener);
  }

  @Test
  void screensABoardMemberWhoIsAlsoABeneficialOwnerOnce() {
    given(userRepository.findByPersonalCode(BOARD_MEMBER)).willReturn(Optional.empty());

    screener.screen(
        REGISTRY_CODE,
        List.of(
            relationship("JUHL", BOARD_MEMBER, "Kati", "Karu"),
            relationship("W", BOARD_MEMBER, "Kati", "Karu")),
        new BeneficialOwners(List.of(new BeneficialOwner("Kati", "Karu", BOARD_MEMBER, "O")), 0));

    verify(sanctionAndPepScreener)
        .addSanctionAndPepCheckIfMissing(new PersonImpl(BOARD_MEMBER, "Kati", "Karu"), Set.of());
  }

  @Test
  void leavesOutShareholdersLegalPersonsAndPeopleWithoutAnEstonianPersonalCodeOrAName() {
    screener.screen(
        REGISTRY_CODE,
        List.of(
            relationship("OSAN", BOARD_MEMBER, "Kati", "Karu"),
            new CompanyRelationship(
                "J",
                "JUHL",
                "JUHL",
                null,
                "Firma OÜ",
                "87654321",
                null,
                null,
                null,
                null,
                null,
                null),
            relationship("JUHL", "38888888888", "Mati", "Maru")),
        new BeneficialOwners(
            List.of(
                new BeneficialOwner(null, null, BENEFICIAL_OWNER, "O"),
                new BeneficialOwner("Tiit", "Tihane", null, "O")),
            1));

    verifyNoInteractions(sanctionAndPepScreener);
  }

  @Test
  void keepsScreeningTheOthersWhenOnePersonsScreeningFails() {
    given(userRepository.findByPersonalCode(any())).willReturn(Optional.empty());
    willThrow(new RuntimeException("screening unavailable"))
        .given(sanctionAndPepScreener)
        .addSanctionAndPepCheckIfMissing(new PersonImpl(BOARD_MEMBER, "Kati", "Karu"), Set.of());

    screener.screen(
        REGISTRY_CODE,
        List.of(relationship("JUHL", BOARD_MEMBER, "Kati", "Karu")),
        new BeneficialOwners(
            List.of(new BeneficialOwner("Tiit", "Tihane", BENEFICIAL_OWNER, "O")), 0));

    verify(sanctionAndPepScreener)
        .addSanctionAndPepCheckIfMissing(
            new PersonImpl(BENEFICIAL_OWNER, "Tiit", "Tihane"), Set.of());
  }

  private static CompanyRelationship relationship(
      String roleCode, String personalCode, String firstName, String lastName) {
    return new CompanyRelationship(
        "F",
        roleCode,
        roleCode,
        firstName,
        lastName,
        personalCode,
        null,
        null,
        null,
        BigDecimal.ZERO,
        null,
        "EST");
  }
}
