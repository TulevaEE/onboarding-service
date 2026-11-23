package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.party.AutomaticGuardianLookups;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.notification.RedemptionRequestedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MinorRedemptionGuardianLookupTest {

  private static final String CHILD = "61506150006";

  @Mock private AutomaticGuardianLookups automaticGuardianLookups;

  @InjectMocks private MinorRedemptionGuardianLookup lookup;

  @Test
  void looksUpThePersonWhosePayoutIsRequestedOnceAsAnAutomaticLookup() {
    lookup.onRedemptionRequested(requested(new PartyId(PERSON, CHILD)));

    verify(automaticGuardianLookups).lookUpOnce(CHILD);
  }

  @Test
  void looksUpNoOneForACompanysPayout() {
    lookup.onRedemptionRequested(requested(new PartyId(LEGAL_ENTITY, "14118923")));

    verifyNoInteractions(automaticGuardianLookups);
  }

  @Test
  void neverFailsThePayoutWhenTheLookupFails() {
    willThrow(new RuntimeException("register down"))
        .given(automaticGuardianLookups)
        .lookUpOnce(CHILD);

    lookup.onRedemptionRequested(requested(new PartyId(PERSON, CHILD)));
  }

  private static RedemptionRequestedEvent requested(PartyId party) {
    return new RedemptionRequestedEvent(
        UUID.randomUUID(),
        7L,
        party,
        new BigDecimal("100.00"),
        BigDecimal.TEN,
        "EE471000001020145685",
        Instant.parse("2025-09-29T15:00:00Z"),
        Instant.parse("2025-09-30T13:00:00Z"),
        Instant.parse("2025-10-01T13:00:00Z"));
  }
}
