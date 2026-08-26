package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.party.PartyId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavingsFundSavers {

  private final SavingFundPaymentRepository paymentRepository;

  public boolean isSaver(String personalCode) {
    return paymentRepository.existsIssuedPaymentFor(new PartyId(PartyId.Type.PERSON, personalCode));
  }
}
