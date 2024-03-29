package ee.tuleva.onboarding.fund;

import ee.tuleva.onboarding.fund.statistics.PensionFundStatistics;
import java.math.BigDecimal;
import java.util.Locale;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
class ExtendedApiFundResponse extends ApiFundResponse {

  private BigDecimal nav;
  private BigDecimal volume;
  private Integer peopleCount;
  private String shortName;

  ExtendedApiFundResponse(Fund fund, PensionFundStatistics pensionFundStatistics, Locale locale) {
    super(fund, locale);
    this.nav = pensionFundStatistics.getNav();
    this.volume = pensionFundStatistics.getVolume();
    this.peopleCount = pensionFundStatistics.getActiveCount();
    this.shortName = fund.getShortName();
  }
}
