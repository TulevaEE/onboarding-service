package ee.tuleva.onboarding.savings.fund;

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
    BigDecimal percent =
        fundRepository
            .findByIsin(savingsFundConfiguration.getIsin())
            .getOngoingChargesFigure()
            .multiply(BigDecimal.valueOf(100))
            .stripTrailingZeros();
    String formatted = percent.toPlainString();
    return "et".equals(locale.getLanguage()) ? formatted.replace('.', ',') : formatted;
  }
}
