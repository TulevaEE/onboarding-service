package ee.tuleva.onboarding.banking.seb;

import ee.tuleva.onboarding.banking.BankAccountType;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@RequiredArgsConstructor
@ConfigurationProperties("seb-gateway")
public class SebAccountConfiguration {

  @Getter private final Map<BankAccountType, String> accounts;
  @Getter private final String managementCompanyName;
  private final @Nullable List<String> registrarIbans;

  public boolean isManagementCompany(String name) {
    return managementCompanyName.equalsIgnoreCase(name);
  }

  public List<String> getRegistrarIbans() {
    return registrarIbans == null ? List.of() : registrarIbans;
  }
}
