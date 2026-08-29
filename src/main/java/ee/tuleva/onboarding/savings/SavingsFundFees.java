package ee.tuleva.onboarding.savings;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import java.math.BigDecimal;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavingsFundFees {

  private final FundRepository fundRepository;
  private final SavingsFundConfiguration savingsFundConfiguration;

  public String ongoingChargesPercent(Locale locale) {
    String isin = savingsFundConfiguration.getIsin();
    Fund fund = fundRepository.findByIsin(isin);
    if (fund == null) {
      throw new IllegalStateException("Savings fund not found: isin=" + isin);
    }
    BigDecimal percent =
        fund.getOngoingChargesFigure().multiply(BigDecimal.valueOf(100)).stripTrailingZeros();
    String formatted = percent.toPlainString();
    return "et".equals(locale.getLanguage()) ? formatted.replace('.', ',') : formatted;
  }
}
