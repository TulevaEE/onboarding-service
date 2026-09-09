package ee.tuleva.onboarding.savings;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.nudge.SavingsFundFeeRate;
import java.math.BigDecimal;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavingsFundFees implements SavingsFundFeeRate {

  private final FundRepository fundRepository;
  private final SavingsFundConfiguration savingsFundConfiguration;

  @Override
  public BigDecimal ongoingChargesPercent() {
    String isin = savingsFundConfiguration.getIsin();
    Fund fund = fundRepository.findByIsin(isin);
    if (fund == null) {
      throw new IllegalStateException("Savings fund not found: isin=" + isin);
    }
    return fund.getOngoingChargesFigure().multiply(BigDecimal.valueOf(100)).stripTrailingZeros();
  }

  public String ongoingChargesPercent(Locale locale) {
    String formatted = ongoingChargesPercent().toPlainString();
    return "et".equals(locale.getLanguage()) ? formatted.replace('.', ',') : formatted;
  }
}
