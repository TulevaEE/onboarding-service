package ee.tuleva.onboarding.savings;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavingFundPaymentQueries {

  private final SavingFundPaymentRepository savingFundPaymentRepository;

  public int countIssuedPaymentMonthsSince(PartyId party, LocalDate from) {
    return savingFundPaymentRepository.countIssuedPaymentMonthsSince(party, from);
  }
}
