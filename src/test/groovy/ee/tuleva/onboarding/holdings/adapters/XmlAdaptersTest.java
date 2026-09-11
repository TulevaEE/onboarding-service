package ee.tuleva.onboarding.holdings.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.holdings.persistence.Region;
import ee.tuleva.onboarding.holdings.persistence.Sector;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class XmlAdaptersTest {

  @Test
  void sectorRoundTripsThroughItsNumericCode() {
    var adapter = new XmlSectorAdapter();
    for (Sector sector : Sector.values()) {
      assertThat(adapter.marshal(sector)).isEqualTo((long) sector.getValue());
      assertThat(adapter.unmarshal(adapter.marshal(sector))).isEqualTo(sector);
    }
  }

  @Test
  void regionRoundTripsThroughItsNumericCode() {
    var adapter = new XmlRegionAdapter();
    for (Region region : Region.values()) {
      assertThat(adapter.marshal(region)).isEqualTo((long) region.getValue());
      assertThat(adapter.unmarshal(adapter.marshal(region))).isEqualTo(region);
    }
  }

  @Test
  void dateRoundTripsThroughIsoFormat() {
    var adapter = new XmlDateAdapter();
    assertThat(adapter.marshal(LocalDate.of(2026, 8, 31))).isEqualTo("2026-08-31");
    assertThat(adapter.unmarshal("2026-08-31")).isEqualTo(LocalDate.of(2026, 8, 31));
  }
}
