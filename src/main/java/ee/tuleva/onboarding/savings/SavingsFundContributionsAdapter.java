package ee.tuleva.onboarding.savings;

import ee.tuleva.onboarding.analytics.SaverId;
import ee.tuleva.onboarding.analytics.SavingsFundContributions;
import ee.tuleva.onboarding.party.PartyId;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SavingsFundContributionsAdapter implements SavingsFundContributions {

  private final SavingFundPaymentQueries savingsFundPayments;

  @Override
  public int countIssuedPaymentMonthsSince(SaverId saver, LocalDate from) {
    var partyId = new PartyId(PartyId.Type.valueOf(saver.type().name()), saver.code());
    return savingsFundPayments.countIssuedPaymentMonthsSince(partyId, from);
  }
}
