package ee.tuleva.onboarding.banking;

import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@RequiredArgsConstructor
public class ManagementCompanies {

  private final SebAccountConfiguration sebAccountConfiguration;

  public boolean isManagementCompany(@Nullable String name) {
    return sebAccountConfiguration.isManagementCompany(name);
  }
}
