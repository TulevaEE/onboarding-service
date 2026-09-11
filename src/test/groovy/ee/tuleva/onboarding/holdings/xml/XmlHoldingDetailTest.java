package ee.tuleva.onboarding.holdings.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.holdings.persistence.Region;
import ee.tuleva.onboarding.holdings.persistence.Sector;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.xml.transform.stream.StreamSource;
import org.eclipse.persistence.jaxb.BeanValidationException;
import org.junit.jupiter.api.Test;

class XmlHoldingDetailTest {

  @Test
  void unmarshalsAllFieldsFromAFullyPopulatedHolding() throws JAXBException {
    String xml =
        """
        <HoldingDetail _ExternalId="594918104" _Id="E0USA00A05">
          <Symbol>MSFT</Symbol>
          <Country _Id="USA">United States</Country>
          <CUSIP>594918104</CUSIP>
          <Currency _Id="USD">US Dollar</Currency>
          <SecurityName>Microsoft Corp</SecurityName>
          <LegalType>E</LegalType>
          <Weighting>2.76</Weighting>
          <NumberOfShare>7628806000</NumberOfShare>
          <ShareChange>0</ShareChange>
          <MarketValue>1367158323260</MarketValue>
          <Sector>11</Sector>
          <HoldingYTDReturn>11.02</HoldingYTDReturn>
          <Region>1</Region>
          <ISIN>US5949181045</ISIN>
          <StyleBox>3</StyleBox>
          <SEDOL>2588173</SEDOL>
          <FirstBoughtDate>2014-12-31</FirstBoughtDate>
        </HoldingDetail>
        """;

    XmlHoldingDetail result = unmarshal(xml);

    assertThat(result.getSymbol()).isEqualTo("MSFT");
    assertThat(result.getCountry()).isEqualTo("USA");
    assertThat(result.getCurrency()).isEqualTo("USD");
    assertThat(result.getSecurityName()).isEqualTo("Microsoft Corp");
    assertThat(result.getWeighting()).isEqualByComparingTo(new BigDecimal("2.76"));
    assertThat(result.getNumberOfShare()).isEqualTo(7628806000L);
    assertThat(result.getShareChange()).isEqualTo(0L);
    assertThat(result.getMarketValue()).isEqualTo(1367158323260L);
    assertThat(result.getSector()).isEqualTo(Sector.TECHNOLOGY);
    assertThat(result.getHoldingYtdReturn()).isEqualByComparingTo(new BigDecimal("11.02"));
    assertThat(result.getRegion()).isEqualTo(Region.UNITED_STATES);
    assertThat(result.getIsin()).isEqualTo("US5949181045");
    assertThat(result.getFirstBoughtDate()).isEqualTo(LocalDate.of(2014, 12, 31));
  }

  @Test
  void leavesEveryOptionalFieldNullWhenOnlySecurityNameIsPresent() throws JAXBException {
    String xml =
        """
        <HoldingDetail>
          <SecurityName>Only Name</SecurityName>
        </HoldingDetail>
        """;

    XmlHoldingDetail result = unmarshal(xml);

    assertThat(result.getSecurityName()).isEqualTo("Only Name");
    assertThat(result.getSymbol()).isNull();
    assertThat(result.getCountry()).isNull();
    assertThat(result.getCurrency()).isNull();
    assertThat(result.getWeighting()).isNull();
    assertThat(result.getNumberOfShare()).isNull();
    assertThat(result.getShareChange()).isNull();
    assertThat(result.getMarketValue()).isNull();
    assertThat(result.getSector()).isNull();
    assertThat(result.getHoldingYtdReturn()).isNull();
    assertThat(result.getRegion()).isNull();
    assertThat(result.getIsin()).isNull();
    assertThat(result.getFirstBoughtDate()).isNull();
  }

  @Test
  void rejectsAHoldingWithoutSecurityNameDuringUnmarshalling() {
    String xml = "<HoldingDetail></HoldingDetail>";

    assertThatThrownBy(() -> unmarshal(xml)).isInstanceOf(BeanValidationException.class);
  }

  @Test
  void mapsRegionCodeOneToUnitedStates() throws JAXBException {
    XmlHoldingDetail result = unmarshal(holdingWithRegion(1));

    assertThat(result.getRegion()).isEqualTo(Region.UNITED_STATES);
  }

  @Test
  void mapsRegionCodeSixteenToNotClassified() throws JAXBException {
    XmlHoldingDetail result = unmarshal(holdingWithRegion(16));

    assertThat(result.getRegion()).isEqualTo(Region.NOT_CLASSIFIED);
  }

  @Test
  void silentlyMapsAnUnknownRegionCodeToNullInsteadOfFailing() throws JAXBException {
    XmlHoldingDetail result = unmarshal(holdingWithRegion(999));

    assertThat(result.getRegion()).isNull();
  }

  @Test
  void mapsSectorCodeOneToBasicMaterials() throws JAXBException {
    XmlHoldingDetail result = unmarshal(holdingWithSector(1));

    assertThat(result.getSector()).isEqualTo(Sector.BASIC_MATERIALS);
  }

  @Test
  void mapsSectorCodeElevenToTechnology() throws JAXBException {
    XmlHoldingDetail result = unmarshal(holdingWithSector(11));

    assertThat(result.getSector()).isEqualTo(Sector.TECHNOLOGY);
  }

  @Test
  void silentlyMapsAnUnknownSectorCodeToNullInsteadOfFailing() throws JAXBException {
    XmlHoldingDetail result = unmarshal(holdingWithSector(999));

    assertThat(result.getSector()).isNull();
  }

  @Test
  void readsCountryFromTheIdAttributeNotTheElementText() throws JAXBException {
    String xml =
        """
        <HoldingDetail>
          <SecurityName>X</SecurityName>
          <Country _Id="EST">Estonia</Country>
        </HoldingDetail>
        """;

    XmlHoldingDetail result = unmarshal(xml);

    assertThat(result.getCountry()).isEqualTo("EST");
  }

  @Test
  void leavesCountryNullWhenTheIdAttributeIsMissing() throws JAXBException {
    String xml =
        """
        <HoldingDetail>
          <SecurityName>X</SecurityName>
          <Country>Estonia</Country>
        </HoldingDetail>
        """;

    XmlHoldingDetail result = unmarshal(xml);

    assertThat(result.getCountry()).isNull();
  }

  @Test
  void ignoresUnmappedElementsAndAttributesInsteadOfFailing() throws JAXBException {
    String xml =
        """
        <HoldingDetail _ExternalId="742718109" _Id="E0USA002UJ">
          <SecurityName>Procter Gamble</SecurityName>
          <CUSIP>742718109</CUSIP>
          <LegalType>E</LegalType>
          <StyleBox>3</StyleBox>
          <SEDOL>2591813</SEDOL>
        </HoldingDetail>
        """;

    XmlHoldingDetail result = unmarshal(xml);

    assertThat(result.getSecurityName()).isEqualTo("Procter Gamble");
  }

  private static String holdingWithRegion(int regionCode) {
    return "<HoldingDetail><SecurityName>X</SecurityName><Region>%d</Region></HoldingDetail>"
        .formatted(regionCode);
  }

  private static String holdingWithSector(int sectorCode) {
    return "<HoldingDetail><SecurityName>X</SecurityName><Sector>%d</Sector></HoldingDetail>"
        .formatted(sectorCode);
  }

  private static XmlHoldingDetail unmarshal(String xml) throws JAXBException {
    JAXBContext context = JAXBContext.newInstance(XmlHoldingDetail.class);
    Unmarshaller unmarshaller = context.createUnmarshaller();
    return unmarshaller
        .unmarshal(new StreamSource(new StringReader(xml)), XmlHoldingDetail.class)
        .getValue();
  }
}
