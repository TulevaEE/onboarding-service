package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.fund.SavingsFundNav;
import ee.tuleva.onboarding.savings.FundNavProvider;
import ee.tuleva.onboarding.savings.SavingsFundConfiguration;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SavingsFundNavAdapter implements SavingsFundNav {

  private final SavingsFundConfiguration savingsFundConfiguration;
  private final FundNavProvider fundNavProvider;

  @Override
  public boolean isSavingsFund(String isin) {
    return savingsFundConfiguration.getIsin().equals(isin);
  }

  @Override
  public LocalDate safeMaxNavDate() {
    return fundNavProvider.safeMaxNavDate();
  }
}
