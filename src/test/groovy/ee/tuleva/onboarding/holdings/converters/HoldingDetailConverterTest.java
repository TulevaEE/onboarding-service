package ee.tuleva.onboarding.holdings.converters;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.holdings.persistence.HoldingDetail;
import ee.tuleva.onboarding.holdings.persistence.Region;
import ee.tuleva.onboarding.holdings.persistence.Sector;
import ee.tuleva.onboarding.holdings.xml.XmlHoldingDetail;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class HoldingDetailConverterTest {

  private final HoldingDetailConverter converter = new HoldingDetailConverter();

  @Test
  void convertsAFullyPopulatedXmlHoldingDetailToAMatchingHoldingDetail() {
    XmlHoldingDetail source =
        XmlHoldingDetail.builder()
            .symbol("AAPL")
            .country("USA")
            .currency("USD")
            .securityName("Apple Inc")
            .weighting(new BigDecimal("2.5"))
            .numberOfShare(1000L)
            .shareChange(50L)
            .marketValue(250000L)
            .sector(Sector.TECHNOLOGY)
            .holdingYtdReturn(new BigDecimal("12.34"))
            .region(Region.EUROZONE)
            .isin("US0378331005")
            .firstBoughtDate(LocalDate.of(2020, 1, 15))
            .build();

    HoldingDetail result = converter.convert(source);

    HoldingDetail expected =
        HoldingDetail.builder()
            .symbol("AAPL")
            .country("USA")
            .currency("USD")
            .securityName("Apple Inc")
            .weighting(new BigDecimal("2.5"))
            .numberOfShare(1000L)
            .shareChange(50L)
            .marketValue(250000L)
            .sector(Sector.TECHNOLOGY)
            .holdingYtdReturn(new BigDecimal("12.34"))
            .region(Region.EUROZONE)
            .isin("US0378331005")
            .firstBoughtDate(LocalDate.of(2020, 1, 15))
            .build();
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void convertsAnXmlHoldingDetailWithOnlySecurityNameToAHoldingDetailWithEverythingElseNull() {
    XmlHoldingDetail source = XmlHoldingDetail.builder().securityName("Only Name").build();

    HoldingDetail result = converter.convert(source);

    HoldingDetail expected = HoldingDetail.builder().securityName("Only Name").build();
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void neverPopulatesIdOrCreatedDateRegardlessOfInput() {
    XmlHoldingDetail source =
        XmlHoldingDetail.builder()
            .securityName("Apple Inc")
            .firstBoughtDate(LocalDate.of(2020, 1, 15))
            .build();

    HoldingDetail result = converter.convert(source);

    assertThat(result.getId()).isNull();
    assertThat(result.getCreatedDate()).isNull();
  }
}
