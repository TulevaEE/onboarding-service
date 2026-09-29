package ee.tuleva.onboarding.banking;

import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@RequiredArgsConstructor
public class ManagementCompanies {

  private static final String MANAGEMENT_FEE_DESCRIPTION = "valitsemistasu";

  private final SebAccountConfiguration sebAccountConfiguration;

  public boolean isManagementCompany(@Nullable String name) {
    return sebAccountConfiguration.isManagementCompany(name);
  }

  public boolean isManagementFee(StatementDebit debit) {
    var description = debit.description();
    return debit.amount().signum() < 0
        && isManagementCompany(debit.beneficiaryName())
        && description != null
        && description.toLowerCase(Locale.ROOT).contains(MANAGEMENT_FEE_DESCRIPTION);
  }
}
