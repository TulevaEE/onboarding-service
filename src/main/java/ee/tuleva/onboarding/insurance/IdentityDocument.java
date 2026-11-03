package ee.tuleva.onboarding.insurance;

import ee.tuleva.onboarding.country.ValidIso2CountryCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record IdentityDocument(
    @NotNull Type type,
    @NotBlank @Size(max = 40) String number,
    @NotNull LocalDate issuedOn,
    @NotNull LocalDate validUntil,
    @NotBlank @ValidIso2CountryCode String issuingCountry) {

  void requireValidOn(LocalDate today) {
    if (issuedOn.isAfter(today)) {
      throw new IdentityDocumentIssuedInTheFutureException(issuedOn, today);
    }
    if (validUntil.isBefore(today)) {
      throw new IdentityDocumentExpiredException(validUntil, today);
    }
  }

  public enum Type {
    ID_CARD,
    PASSPORT,
    RESIDENCE_PERMIT,
    TEMPORARY_PASSPORT,
    FOREIGN_PASSPORT
  }
}
