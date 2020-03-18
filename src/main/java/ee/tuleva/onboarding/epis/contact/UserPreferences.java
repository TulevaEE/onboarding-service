package ee.tuleva.onboarding.epis.contact;

import static ee.tuleva.onboarding.epis.contact.UserPreferences.ContactPreferenceType.E;
import static ee.tuleva.onboarding.epis.contact.UserPreferences.LanguagePreferenceType.EST;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.user.address.Address;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
// TODO: rename to ContactDetails
public class UserPreferences implements Person {

  public enum ContactPreferenceType {
    E,
    P
  } // E - email, P - postal

  private String firstName;

  private String lastName;

  private String personalCode;

  @Builder.Default private ContactPreferenceType contactPreference = E;

  private String districtCode;

  private String addressRow1;

  private String addressRow2;

  private String addressRow3;

  private String postalIndex;

  @Builder.Default private String country = "EE";

  public enum LanguagePreferenceType {
    EST,
    RUS,
    ENG
  }

  @Builder.Default private LanguagePreferenceType languagePreference = EST;

  @Builder.Default private String noticeNeeded = "Y"; // boolean { 'Y', 'N' }

  private String email;

  private String phoneNumber;

  private String pensionAccountNumber;

  private List<Distribution> thirdPillarDistribution;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Distribution {
    private String activeThirdPillarFundIsin;
    private BigDecimal percentage;
  }

  private String activeSecondPillarFundIsin;

  private boolean isSecondPillarActive;

  private boolean isThirdPillarActive;

  public Address getAddress() {
    return Address.builder()
        .street(addressRow1)
        .countryCode(country)
        .postalCode(postalIndex)
        .districtCode(districtCode)
        .build();
  }

  public UserPreferences setAddress(Address address) {
    addressRow1 = address.getStreet();
    addressRow2 = null;
    addressRow3 = null;
    country = address.getCountryCode();
    districtCode = address.getDistrictCode();
    postalIndex = address.getPostalCode();
    return this;
  }

  public String getDistrictName() {
    return districtCodeToName.get(districtCode);
  }

  private static Map<String, String> districtCodeToName =
      new HashMap<String, String>() {
        {
          put("1060", "Abja-Paluoja linn");
          put("1301", "Antsla linn");
          put("0170", "Elva linn");
          put("0183", "Haapsalu linn");
          put("0037", "Harju maakond");
          put("0039", "Hiiu maakond");
          put("0044", "Ida-Viru maakond");
          put("0051", "Järva maakond");
          put("0249", "Jõgeva linn");
          put("0049", "Jõgeva maakond");
          put("2270", "Jõhvi linn");
          put("0279", "Kallaste linn");
          put("3895", "Kärdla linn");
          put("2761", "Karksi-Nuia linn");
          put("2928", "Kehra linn");
          put("0296", "Keila linn");
          put("3083", "Kilingi-Nõmme linn");
          put("0309", "Kiviõli linn");
          put("0322", "Kohtla-Järve linn");
          put("0345", "Kunda linn");
          put("0349", "Kuressaare linn");
          put("0057", "Lääne maakond");
          put("0059", "Lääne-Viru maakond");
          put("4330", "Lihula linn");
          put("0424", "Loksa linn");
          put("0446", "Maardu linn");
          put("0490", "Mõisaküla linn");
          put("0485", "Mustvee linn");
          put("0513", "Narva-Jõesuu linn");
          put("0511", "Narva linn");
          put("5754", "Otepää linn");
          put("0566", "Paide linn");
          put("0580", "Paldiski linn");
          put("0625", "Pärnu linn");
          put("0067", "Pärnu maakond");
          put("0617", "Põltsamaa linn");
          put("6536", "Põlva linn");
          put("0065", "Põlva maakond");
          put("6671", "Püssi linn");
          put("0663", "Rakvere linn");
          put("7216", "Räpina linn");
          put("6826", "Rapla linn");
          put("0070", "Rapla maakond");
          put("0074", "Saare maakond");
          put("0728", "Saue linn");
          put("0735", "Sillamäe linn");
          put("0741", "Sindi linn");
          put("7836", "Suure-Jaani linn");
          put("0784", "Tallinn");
          put("8130", "Tamsalu linn");
          put("8140", "Tapa linn");
          put("0795", "Tartu linn");
          put("0078", "Tartu maakond");
          put("0823", "Tõrva linn");
          put("8595", "Türi linn");
          put("0854", "Valga linn");
          put("0082", "Valga maakond");
          put("0897", "Viljandi linn");
          put("0084", "Viljandi maakond");
          put("0912", "Võhma linn");
          put("0919", "Võru linn");
          put("0086", "Võru maakond");
        }
      };
}
