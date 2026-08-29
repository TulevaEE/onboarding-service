package ee.tuleva.onboarding.fund;

import java.time.LocalDate;

public interface SavingsFundNav {

  boolean isSavingsFund(String isin);

  LocalDate safeMaxNavDate();
}
