package ee.tuleva.onboarding.banking;

import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;

@NullMarked
@RequiredArgsConstructor
public class ManagementCompanies {

  private final SebAccountConfiguration sebAccountConfiguration;

  public boolean isManagementCompany(String name) {
    return sebAccountConfiguration.isManagementCompany(name);
  }
}
