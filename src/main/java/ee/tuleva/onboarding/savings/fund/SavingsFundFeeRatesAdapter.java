package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.notification.email.firstpayment.SavingsFundFeeRates;
import ee.tuleva.onboarding.savings.SavingsFundFees;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SavingsFundFeeRatesAdapter implements SavingsFundFeeRates {

  private final SavingsFundFees savingsFundFees;

  @Override
  public String ongoingChargesPercent(Locale locale) {
    return savingsFundFees.ongoingChargesPercent(locale);
  }
}
