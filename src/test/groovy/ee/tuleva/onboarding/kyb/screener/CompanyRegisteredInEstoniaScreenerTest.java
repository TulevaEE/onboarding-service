package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_REGISTERED_IN_ESTONIA;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompanyRegisteredInEstoniaScreenerTest {

  private final CompanyRegisteredInEstoniaScreener screener =
      new CompanyRegisteredInEstoniaScreener();

  @Test
  void passesWhenCountryCodeIsEstonia() {
    var data = companyWith("EE", "Harju maakond, Tallinn, Pärnu mnt 1");

    var result = screener.screen(data);

    assertThat(result)
        .containsExactly(
            new KybCheck(COMPANY_REGISTERED_IN_ESTONIA, true, Map.of("countryCode", "EE")));
  }

  @Test
  void passesWhenCountryUnknownButAddressMentionsEstonianCounty() {
    var data = companyWith(null, "Tartu maakond, Tartu linn, Paju 2");

    var result = screener.screen(data);

    assertThat(result)
        .containsExactly(
            new KybCheck(COMPANY_REGISTERED_IN_ESTONIA, true, Map.of("countryCode", "")));
  }

  @Test
  void failsWhenCountryUnknownAndNoEstonianCountyInAddress() {
    var data = companyWith(null, "Pärnu mnt 1, 11313 Tallinn");

    var result = screener.screen(data);

    assertThat(result)
        .containsExactly(
            new KybCheck(COMPANY_REGISTERED_IN_ESTONIA, false, Map.of("countryCode", "")));
  }

  @Test
  void failsWhenCountryIsForeignEvenIfAddressMentionsEstonianCounty() {
    var data = companyWith("DE", "Tartu maakond, Tartu linn, Paju 2");

    var result = screener.screen(data);

    assertThat(result)
        .containsExactly(
            new KybCheck(COMPANY_REGISTERED_IN_ESTONIA, false, Map.of("countryCode", "DE")));
  }

  private KybCompanyData companyWith(String countryCode, String fullAddress) {
    var person = boardMemberOwner("38501010002", 100.0).build();
    return new KybCompanyData(
        new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
        new PersonalCode("38501010002"),
        R,
        List.of(person),
        new SelfCertification(true, true, true),
        countryCode,
        fullAddress,
        null);
  }
}
