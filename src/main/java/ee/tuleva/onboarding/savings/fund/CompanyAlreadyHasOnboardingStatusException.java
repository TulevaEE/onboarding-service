package ee.tuleva.onboarding.savings.fund;

import static org.springframework.http.HttpStatus.CONFLICT;

import lombok.Getter;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(CONFLICT)
public class CompanyAlreadyHasOnboardingStatusException extends RuntimeException {

  @Getter private final String registryCode;
  @Getter private final SavingsFundOnboardingStatus existingStatus;

  public CompanyAlreadyHasOnboardingStatusException(
      String registryCode, SavingsFundOnboardingStatus existingStatus) {
    super(
        "Company already has status: registryCode="
            + registryCode
            + ", status="
            + existingStatus
            + ". Pass override=true to force whitelisting.");
    this.registryCode = registryCode;
    this.existingStatus = existingStatus;
  }
}
