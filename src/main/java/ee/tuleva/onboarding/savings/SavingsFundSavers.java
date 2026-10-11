package ee.tuleva.onboarding.savings;

import ee.tuleva.onboarding.nudge.NudgeAccount;
import ee.tuleva.onboarding.nudge.SavingsFundSaverStatus;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavingsFundSavers implements SavingsFundSaverStatus {

  private final SavingFundPaymentRepository paymentRepository;

  @Override
  public boolean savesFor(NudgeAccount account) {
    return paymentRepository.existsIssuedPaymentFor(partyId(account));
  }

  private static PartyId partyId(NudgeAccount account) {
    return new PartyId(
        switch (account.type()) {
          case PERSON -> PartyId.Type.PERSON;
          case LEGAL_ENTITY -> PartyId.Type.LEGAL_ENTITY;
        },
        account.code());
  }
}
