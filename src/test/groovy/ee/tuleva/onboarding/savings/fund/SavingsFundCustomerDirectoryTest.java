package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.party.RepresentationType.GUARDIAN;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.aml.SavingsFundGuardian;
import ee.tuleva.onboarding.party.ParentChildLink;
import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.party.RepresentationType;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundCustomerDirectoryTest {

  private static final String CHILD_HOLDER = "68888888888";
  private static final String WARD_HOLDER = "48888888880";
  private static final String NOT_A_HOLDER = "58888888888";
  private static final String PARENT = "38888888888";
  private static final String CO_PARENT = "48888888888";
  private static final String GUARDIAN_OF_ADULT = "38888888881";

  @Mock private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Mock private ParentChildLinkService parentChildLinkService;
  @InjectMocks private SavingsFundCustomerDirectory customerDirectory;

  @Test
  void listsTheOnboardedCustomerPersonalCodes() {
    given(savingsFundOnboardingRepository.findPersonCodes())
        .willReturn(List.of("38888888888", "48888888880"));

    assertThat(customerDirectory.personalCodes()).containsExactly("38888888888", "48888888880");
  }

  @Test
  void listsTheGuardiansOfOnboardedCustomersOnceEachWithTheNameRecordedOnTheirLink() {
    given(savingsFundOnboardingRepository.findPersonCodes())
        .willReturn(List.of(CHILD_HOLDER, WARD_HOLDER));
    given(parentChildLinkService.findCurrentRepresentatives())
        .willReturn(
            List.of(
                link(PARENT, CHILD_HOLDER, LEGAL_REPRESENTATIVE, null, null),
                link(CO_PARENT, WARD_HOLDER, GUARDIAN, null, null),
                link(CO_PARENT, CHILD_HOLDER, LEGAL_REPRESENTATIVE, "Mari", "Maasikas"),
                link(GUARDIAN_OF_ADULT, WARD_HOLDER, GUARDIAN, null, null),
                link(PARENT, WARD_HOLDER, GUARDIAN, null, null),
                link(PARENT, NOT_A_HOLDER, LEGAL_REPRESENTATIVE, null, null),
                link(CO_PARENT, NOT_A_HOLDER, LEGAL_REPRESENTATIVE, null, null)));

    assertThat(customerDirectory.guardians())
        .containsExactlyInAnyOrder(
            new SavingsFundGuardian(PARENT, null, null),
            new SavingsFundGuardian(CO_PARENT, "Mari", "Maasikas"),
            new SavingsFundGuardian(GUARDIAN_OF_ADULT, null, null));
  }

  @Test
  void listsNoGuardiansOfPeopleWhoAreNotOnboarded() {
    given(savingsFundOnboardingRepository.findPersonCodes()).willReturn(List.of(CHILD_HOLDER));
    given(parentChildLinkService.findCurrentRepresentatives())
        .willReturn(List.of(link(PARENT, NOT_A_HOLDER, LEGAL_REPRESENTATIVE, null, null)));

    assertThat(customerDirectory.guardians()).isEmpty();
  }

  private static ParentChildLink link(
      String parentCode,
      String childCode,
      RepresentationType type,
      @Nullable String parentFirstName,
      @Nullable String parentLastName) {
    return ParentChildLink.builder()
        .parentPersonalCode(parentCode)
        .childPersonalCode(childCode)
        .relationshipType(type)
        .validUntil(LocalDate.of(2040, 1, 1))
        .parentFirstName(parentFirstName)
        .parentLastName(parentLastName)
        .build();
  }
}
