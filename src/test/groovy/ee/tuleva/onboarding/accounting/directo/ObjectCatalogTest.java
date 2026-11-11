package ee.tuleva.onboarding.accounting.directo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectCatalogTest {

  private final ObjectCatalog catalog =
      new ObjectCatalog(
          List.of(
              new DirectoObject("TEAM1", "10"),
              new DirectoObject(" FUND1 ", " 20 "),
              new DirectoObject("E001", "50"),
              new DirectoObject("UNCLASSIFIED", null),
              new DirectoObject("BLANK", " ")));

  @Test
  void anObjectIsClassifiedWhenItHasALevel() {
    assertThat(List.of("TEAM1", "FUND1", "E001", "UNCLASSIFIED", "BLANK", "X404"))
        .filteredOn(catalog::isClassified)
        .containsExactly("TEAM1", "FUND1", "E001");
  }

  @Test
  void anEmployeeIsAnObjectAtLevelFifty() {
    assertThat(List.of("TEAM1", "FUND1", "E001", "UNCLASSIFIED", "X404"))
        .filteredOn(catalog::isEmployee)
        .containsExactly("E001");
  }

  @Test
  void aRowWithoutObjectsHasNoDimensions() {
    assertThat(catalog.dimensionsOf(rowWithObjects())).isEqualTo(Map.of());
  }

  @Test
  void codesAndLevelsOfTheCatalogAreTrimmed() {
    assertThat(catalog.dimensionsOf(rowWithObjects("TEAM1", "FUND1")))
        .isEqualTo(Map.of("team", "TEAM1", "fund", "FUND1"));
  }

  @Test
  void anObjectWithoutALevelIsNotADimension() {
    assertThatThrownBy(() -> catalog.dimensionsOf(rowWithObjects("UNCLASSIFIED")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> catalog.dimensionsOf(rowWithObjects("BLANK")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anObjectMissingFromTheCatalogIsNotADimension() {
    assertThatThrownBy(() -> catalog.dimensionsOf(rowWithObjects("X404")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aCodeListedTwiceAtOneLevelIsOneObject() {
    var listedTwice =
        new ObjectCatalog(
            List.of(new DirectoObject("TEAM1", "10"), new DirectoObject("TEAM1 ", "10")));

    assertThat(listedTwice.dimensionsOf(rowWithObjects("TEAM1")))
        .isEqualTo(Map.of("team", "TEAM1"));
  }

  @Test
  void aCodeListedAtTwoLevelsStopsTheEntity() {
    var objects = List.of(new DirectoObject("E001", "10"), new DirectoObject("E001", "50"));

    assertThatThrownBy(() -> new ObjectCatalog(objects))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static DocumentRow rowWithObjects(String... objectCodes) {
    return new DocumentRow(
        "500100",
        new BigDecimal("5.00"),
        LocalDate.parse("2026-03-12"),
        List.of(objectCodes),
        List.of(),
        List.of());
  }
}
