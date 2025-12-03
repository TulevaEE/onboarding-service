package ee.tuleva.onboarding.epis.contact;

import static ee.tuleva.onboarding.epis.contact.ContactDetails.LanguagePreferenceType.EST;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.notification.email.Emailable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactDetails implements Person, Emailable {

  private String firstName;
  private String lastName;
  private String personalCode;
  @Nullable private Instant lastUpdateDate;
  private String country;
  @Builder.Default private LanguagePreferenceType languagePreference = EST;
  @Builder.Default private String noticeNeeded = "Y"; // boolean { 'Y', 'N' }
  @Nullable private String email;
  private String phoneNumber;
  private String pensionAccountNumber;
  private List<Distribution> thirdPillarDistribution;
  private String activeSecondPillarFundIsin;
  private String activeSecondPillarFundPik;
  private boolean isSecondPillarActive;
  private boolean isThirdPillarActive;
  private Instant secondPillarOpenDate;
  private Instant thirdPillarInitDate;

  public Country getAddress() {
    return Country.builder().countryCode(country).build();
  }

  public ContactDetails setAddress(Country address) {
    country = address.getCountryCode();
    return this;
  }

  public enum LanguagePreferenceType {
    EST,
    RUS,
    ENG
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Distribution {
    private String activeThirdPillarFundIsin;
    private BigDecimal percentage;
  }
}
