package ee.tuleva.onboarding.savings;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavingFundPaymentQueries {

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final Clock clock;

  public List<SavingFundPayment> getPendingPayments(PartyId partyId) {
    final Duration BANK_CONFIRMATION_WINDOW = Duration.ofDays(7);
    var unconfirmedCutoff = clock.instant().minus(BANK_CONFIRMATION_WINDOW);
    return savingFundPaymentRepository
        .findPaymentsWithStatus(
            partyId,
            SavingFundPayment.Status.CREATED,
            SavingFundPayment.Status.RECEIVED,
            SavingFundPayment.Status.VERIFIED,
            SavingFundPayment.Status.RESERVED,
            SavingFundPayment.Status.FROZEN,
            SavingFundPayment.Status.TO_BE_RETURNED)
        .stream()
        .filter(payment -> !payment.isUnconfirmedSince(unconfirmedCutoff))
        .toList();
  }

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
