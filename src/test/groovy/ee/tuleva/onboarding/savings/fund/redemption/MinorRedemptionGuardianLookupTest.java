package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.party.MinorGuardianLookupService;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.notification.RedemptionRequestedEvent;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MinorRedemptionGuardianLookupTest {

  private static final String PARENT = "38812121215";
  private static final String CHILD = "61506150006";
  private static final long PARENT_USER_ID = 7L;

  @Mock private MinorGuardianLookupService minorGuardianLookupService;
  @Mock private UserService userService;

  @InjectMocks private MinorRedemptionGuardianLookup lookup;

  @Test
  void looksUpTheGuardiansOfAMinorWhoseParentRequestsAPayoutAsTheParent() {
    given(userService.getById(PARENT_USER_ID)).willReturn(Optional.of(parent()));

    lookup.onRedemptionRequested(requested(new PartyId(PERSON, CHILD)));

    verify(minorGuardianLookupService).lookUpChildOnce(PARENT, CHILD);
  }

  @Test
  void looksUpNoOneForAnAdultsOwnPayout() {
    given(userService.getById(PARENT_USER_ID)).willReturn(Optional.of(parent()));

    lookup.onRedemptionRequested(requested(new PartyId(PERSON, PARENT)));

    verifyNoInteractions(minorGuardianLookupService);
  }

  @Test
  void looksUpNoOneForACompanysPayout() {
    lookup.onRedemptionRequested(requested(new PartyId(LEGAL_ENTITY, "14118923")));

    verifyNoInteractions(minorGuardianLookupService, userService);
  }

  @Test
  void neverFailsThePayoutWhenTheLookupFails() {
    given(userService.getById(PARENT_USER_ID)).willReturn(Optional.of(parent()));
    willThrow(new RuntimeException("register down"))
        .given(minorGuardianLookupService)
        .lookUpChildOnce(PARENT, CHILD);

    lookup.onRedemptionRequested(requested(new PartyId(PERSON, CHILD)));
  }

  private static RedemptionRequestedEvent requested(PartyId party) {
    return new RedemptionRequestedEvent(
        UUID.randomUUID(), PARENT_USER_ID, party, new BigDecimal("100.00"), BigDecimal.TEN);
  }

  private static User parent() {
    return User.builder().id(PARENT_USER_ID).personalCode(PARENT).build();
  }
}
