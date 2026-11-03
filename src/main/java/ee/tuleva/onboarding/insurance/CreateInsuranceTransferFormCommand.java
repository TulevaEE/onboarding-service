package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.country.Iso2CountryCodeValidator.isIso2CountryCode;
import static ee.tuleva.onboarding.insurance.Insurer.ARTEA;
import static ee.tuleva.onboarding.insurance.Insurer.COMPENSA;
import static ee.tuleva.onboarding.insurance.Insurer.ERGO;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.country.ValidIso2CountryCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record CreateInsuranceTransferFormCommand(
    @NotNull Insurer insurer,
    @NotBlank @Size(max = 40) String policyNumber,
    @Nullable LocalDate endDate,
    @NotNull @Valid Address address,
    @Nullable @Size(max = 5) List<@ValidIso2CountryCode String> citizenship,
    @Nullable String countryOfBirth,
    @Nullable String taxResidency,
    @Nullable Boolean pep,
    @Nullable @Valid ArteaQuestionnaire artea) {

  public CreateInsuranceTransferFormCommand {
    if (policyNumber != null) {
      policyNumber =
          insurer == ARTEA ? ArteaContractNumber.printed(policyNumber) : policyNumber.strip();
    }
  }

  public boolean declaredPep() {
    return requireNonNull(pep, "Politically exposed person answer missing: insurer=" + insurer);
  }

  public ArteaQuestionnaire questionnaire() {
    return requireNonNull(artea, "Questionnaire missing: insurer=" + insurer);
  }

  @AssertTrue
  public boolean isIdentityAnsweredWhereAsked() {
    return insurer == COMPENSA
        || (citizenship != null
            && !citizenship.isEmpty()
            && isIso2CountryCode(countryOfBirth)
            && isIso2CountryCode(taxResidency)
            && pep != null);
  }

  @AssertTrue
  public boolean isQuestionnaireAnsweredWhereAsked() {
    return insurer != ARTEA || artea != null;
  }

  @AssertTrue
  public boolean isPolicyNumberInTheFormatTheFormHolds() {
    return insurer == null
        || policyNumber == null
        || switch (insurer) {
          case ERGO -> ErgoPolicyNumber.parse(policyNumber).isPresent();
          case ARTEA -> ArteaContractNumber.isInTheFormat(policyNumber);
          case COMPENSA -> true;
        };
  }
}
