package ee.tuleva.onboarding.savings;

import ee.tuleva.onboarding.party.PartyId;
import java.util.List;

public interface RedemptionQueries {

  List<PendingRedemption> getPendingRedemptions(PartyId partyId);
}
