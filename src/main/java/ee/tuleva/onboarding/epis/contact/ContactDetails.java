package ee.tuleva.onboarding.epis.contact;

import static ee.tuleva.onboarding.epis.contact.ContactDetails.LanguagePreferenceType.EST;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.user.address.Address;
import java.math.BigDecimal;
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
public class ContactDetails implements Person {

  private String firstName;
  private String lastName;
  private String personalCode;
  @Builder.Default private String country = "EE";
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

  public Address getAddress() {
    return Address.builder().countryCode(country).build();
  }

  public ContactDetails setAddress(Address address) {
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
