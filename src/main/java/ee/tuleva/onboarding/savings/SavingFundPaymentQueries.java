package ee.tuleva.onboarding.savings;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavingFundPaymentQueries {

  private final SavingFundPaymentRepository savingFundPaymentRepository;

  public int countIssuedPaymentMonthsSince(PartyId party, LocalDate from) {
    return savingFundPaymentRepository.countIssuedPaymentMonthsSince(party, from);
  }

  public List<SavingFundPayment> findRecentPayments(String description) {
    return savingFundPaymentRepository.findRecentPayments(description);
  }

  public UUID savePaymentData(SavingFundPayment payment) {
    return savingFundPaymentRepository.savePaymentData(payment);
  }

  public void attachParty(UUID paymentId, PartyId partyId) {
    savingFundPaymentRepository.attachParty(paymentId, partyId);
  }
}
