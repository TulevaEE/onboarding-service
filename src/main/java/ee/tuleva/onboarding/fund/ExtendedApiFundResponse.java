package ee.tuleva.onboarding.fund;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import ee.tuleva.onboarding.fund.statistics.PensionFundStatistics;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NullUnmarked;

@Data
@NullUnmarked
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
class ExtendedApiFundResponse extends ApiFundResponse {

  private BigDecimal nav;
  private BigDecimal volume;
  private Integer peopleCount;
  private String shortName;

  @JsonInclude(NON_NULL)
  private BigDecimal fundManagerUnits;

  @JsonInclude(NON_NULL)
  private LocalDate fundManagerUnitsDate;

  ExtendedApiFundResponse(Fund fund, PensionFundStatistics pensionFundStatistics, Locale locale) {
    super(fund, locale);
    this.nav = pensionFundStatistics.getNav();
    this.volume = pensionFundStatistics.getVolume();
    this.peopleCount = pensionFundStatistics.getActiveCount();
    this.shortName = fund.getShortName();
  }
}
