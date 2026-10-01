package ee.tuleva.onboarding.savings;

import ee.tuleva.onboarding.analytics.SaverId;
import ee.tuleva.onboarding.analytics.SavingsFundContributions;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.SavingFundStandingOrders;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SavingsFundContributionsAdapter implements SavingsFundContributions {

  private final SavingFundStandingOrders standingOrders;

  @Override
  public int countStandingOrderMonthsSince(SaverId saver, LocalDate from) {
    var partyType =
        switch (saver.type()) {
          case PERSON -> PartyId.Type.PERSON;
          case LEGAL_ENTITY -> PartyId.Type.LEGAL_ENTITY;
        };
    var partyId = new PartyId(partyType, saver.code());
    return standingOrders.countMonthsSince(partyId, from);
  }
}
