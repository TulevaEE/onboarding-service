package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.user.User;
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
  @InjectMocks private KnownLookups lookups;

  private final User user = sampleUser().build();

  @Test
  void aFailedRepresentedPartiesLookupLeavesTheSavingsFundQuestionUnknown() {
    given(actingParties.representedBy(any())).willThrow(new RuntimeException("registry down"));

    assertThat(lookups.savesForAnyRepresentedParty(user, Known.NO)).isEqualTo(Known.UNKNOWN);
  }

  @Test
  void ownSavingsFundAccountAnswersWithoutLookingUpRepresentedParties() {
    assertThat(lookups.savesForAnyRepresentedParty(user, Known.YES)).isEqualTo(Known.YES);
  }
}
