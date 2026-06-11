package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_AGE;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompanyAgeScreenerTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-06-11T10:00:00Z"), ZoneId.of("Europe/Tallinn"));

  private final CompanyAgeScreener screener = new CompanyAgeScreener(FIXED_CLOCK);

  @Test
  void companyOlderThanOneYearPasses() {
    var data = companyFoundedOn(LocalDate.of(2020, 1, 15));

    var result = screener.screen(data);

    assertThat(result)
        .containsExactly(new KybCheck(COMPANY_AGE, true, Map.of("foundingDate", "2020-01-15")));
  }

  @Test
  void companyYoungerThanOneYearFails() {
    var data = companyFoundedOn(LocalDate.of(2026, 3, 1));

    var result = screener.screen(data);

    assertThat(result)
        .containsExactly(new KybCheck(COMPANY_AGE, false, Map.of("foundingDate", "2026-03-01")));
  }

  @Test
  void companyFoundedExactlyOneYearAgoPasses() {
    var data = companyFoundedOn(LocalDate.of(2025, 6, 11));

    var result = screener.screen(data);

    assertThat(result)
        .containsExactly(new KybCheck(COMPANY_AGE, true, Map.of("foundingDate", "2025-06-11")));
  }

  @Test
  void emitsNoCheckWhenFoundingDateIsUnknown() {
    var data = companyFoundedOn(null);

    var result = screener.screen(data);

    assertThat(result).isEmpty();
  }

  private static KybCompanyData companyFoundedOn(LocalDate foundingDate) {
    return new KybCompanyData(
        new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
        new PersonalCode("38501010001"),
        CompanyStatus.R,
        List.of(),
        new SelfCertification(true, true, true),
        "EE",
        "Harju maakond, Tallinn, Pärnu mnt 1",
        foundingDate,
        false);
  }
}
