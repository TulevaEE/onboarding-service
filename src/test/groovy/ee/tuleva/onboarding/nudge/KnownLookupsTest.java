package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.user.UserContactsFixture.contactSummaryWithContributionPeriods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.user.SecondPillarContributions;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserContacts;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnownLookupsTest {

  @Mock private SecondPillarLeaverStatus leaverStatus;
  @Mock private RecurringContributionStatus recurringStatus;
  @Mock private SavingsFundSaverStatus saverStatus;
  @Mock private TaxHeadroom taxHeadroom;
  @Mock private ActingParties actingParties;
  @Mock private UserContacts userContacts;
  @Mock private SecondPillarContributions secondPillarContributions;
  @InjectMocks private KnownLookups lookups;

  private final User user = sampleUser().build();

  @Test
  void aFailedRepresentedPartiesLookupLeavesTheSavingsFundQuestionUnknown() {
    given(actingParties.representedBy(any())).willThrow(new RuntimeException("registry down"));

    assertThat(lookups.savesForAnyRepresentedParty(user.getPersonalCode(), Known.NO))
        .isEqualTo(Known.UNKNOWN);
  }

  @Test
  void aLeaverWhoseDateHasComeMayRejoinWhenAnApplicationEndedTheirContributions() {
    given(userContacts.forPerson(user))
        .willReturn(contactSummaryWithContributionPeriods(List.of()));
    given(secondPillarContributions.mayRejoinToday(List.of())).willReturn(true);
    given(leaverStatus.contributionsCannotRestart(user.getPersonalCode())).willReturn(false);

    assertThat(lookups.mayRejoinSecondPillar(user)).isEqualTo(Known.YES);
  }

  @Test
  void aLeaverWhoseDateHasComeMayNotRejoinWhenTheirContributionsEndedWithoutAnApplication() {
    given(userContacts.forPerson(user))
        .willReturn(contactSummaryWithContributionPeriods(List.of()));
    given(secondPillarContributions.mayRejoinToday(List.of())).willReturn(true);
    given(leaverStatus.contributionsCannotRestart(user.getPersonalCode())).willReturn(true);

    assertThat(lookups.mayRejoinSecondPillar(user)).isEqualTo(Known.NO);
  }

  @Test
  void aLeaverWhoseDateHasNotComeMayNotRejoinAndTheRegistryIsNotRead() {
    given(userContacts.forPerson(user))
        .willReturn(contactSummaryWithContributionPeriods(List.of()));
    given(secondPillarContributions.mayRejoinToday(List.of())).willReturn(false);

    assertThat(lookups.mayRejoinSecondPillar(user)).isEqualTo(Known.NO);
    verifyNoInteractions(leaverStatus);
  }

  @Test
  void ownSavingsFundAccountAnswersWithoutLookingUpRepresentedParties() {
    assertThat(lookups.savesForAnyRepresentedParty(user.getPersonalCode(), Known.YES))
        .isEqualTo(Known.YES);
  }
}
