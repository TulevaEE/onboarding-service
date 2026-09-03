package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.aml.SanctionAndPepScreener;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.kyc.BeforeKycCheckedEvent;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuardianKycScreeningListenerTest {

  private static final String CHILD = "61506150006";
  private static final String ADULT = "38812121215";
  private static final String GUARDIAN = "38002020008";
  private static final String OTHER_GUARDIAN = "48002020009";

  @Mock private ParentChildLinkService parentChildLinkService;
  @Mock private UserService userService;
  @Mock private SanctionAndPepScreener sanctionAndPepScreener;

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC);

  private GuardianKycScreeningListener listener;

  @BeforeEach
  void setUp() {
    listener =
        new GuardianKycScreeningListener(
            parentChildLinkService, userService, sanctionAndPepScreener, clock);
  }

  @Test
  void screensEveryGuardianOfAMinorKycSubjectOnTheirOwnCountriesNotTheChildSubjectCountries() {
    User guardian = sampleUserNonMember().personalCode(GUARDIAN).build();
    User otherGuardian = sampleUserNonMember().personalCode(OTHER_GUARDIAN).build();
    given(parentChildLinkService.findGuardianCodes(CHILD))
        .willReturn(List.of(GUARDIAN, OTHER_GUARDIAN));
    given(userService.findByPersonalCode(GUARDIAN)).willReturn(Optional.of(guardian));
    given(userService.findByPersonalCode(OTHER_GUARDIAN)).willReturn(Optional.of(otherGuardian));

    listener.beforeKycChecked(
        new BeforeKycCheckedEvent(new PersonImpl(CHILD, "Mari", "Maasikas"), Countries.of("EE")));

    verify(sanctionAndPepScreener).addSanctionAndPepCheckIfMissing(guardian);
    verify(sanctionAndPepScreener).addSanctionAndPepCheckIfMissing(otherGuardian);
  }

  @Test
  void doesNothingForAnAdultKycSubject() {
    listener.beforeKycChecked(
        new BeforeKycCheckedEvent(new PersonImpl(ADULT, "Jordan", "Valdma"), Countries.of("EE")));

    verifyNoInteractions(parentChildLinkService, userService, sanctionAndPepScreener);
  }

  @Test
  void skipsGuardiansWithoutAUserAccount() {
    given(parentChildLinkService.findGuardianCodes(CHILD)).willReturn(List.of(GUARDIAN));
    given(userService.findByPersonalCode(GUARDIAN)).willReturn(Optional.empty());

    listener.beforeKycChecked(
        new BeforeKycCheckedEvent(new PersonImpl(CHILD, "Mari", "Maasikas"), Countries.of("EE")));

    verify(sanctionAndPepScreener, never()).addSanctionAndPepCheckIfMissing(any(User.class));
  }
}
