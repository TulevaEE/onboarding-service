package ee.tuleva.onboarding.ariregister;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ariregister.generated.Seos;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CompanyRelationshipMapperTest {

  @Test
  void mapsPhysicalPersonWithAllFields() {
    var seos = new Seos();
    seos.setIsikuTyyp("F");
    seos.setIsikuRoll("JUHL");
    seos.setIsikuRollTekstina("Juhatuse liige");
    seos.setEesnimi("Jaan");
    seos.setNimiArinimi("Tamm");
    seos.setIsikukoodRegistrikood("39901010000");
    seos.setSynniaeg(LocalDate.of(1999, 1, 1));
    seos.setAlgusKpv(LocalDate.of(2017, 4, 3));
    seos.setLoppKpv(LocalDate.of(2025, 12, 31));
    seos.setOsaluseProtsent(new BigDecimal("50.00"));
    seos.setKontrolliTeostamiseViisTekstina("Osaluse kaudu");
    seos.setAadressRiik("EST");

    var result = CompanyRelationshipMapper.fromSeos(seos);

    assertThat(result)
        .isEqualTo(
            new CompanyRelationship(
                "F",
                "JUHL",
                "Juhatuse liige",
                "Jaan",
                "Tamm",
                "39901010000",
                LocalDate.of(1999, 1, 1),
                LocalDate.of(2017, 4, 3),
                LocalDate.of(2025, 12, 31),
                new BigDecimal("50.00"),
                "Osaluse kaudu",
                "EST"));
  }

  @Test
  void mapsJuridicalEntityWithMinimalFields() {
    var seos = new Seos();
    seos.setIsikuTyyp("J");
    seos.setIsikuRoll("S");
    seos.setIsikuRollTekstina("Osanik");
    seos.setNimiArinimi("Test Firma OÜ");
    seos.setIsikukoodRegistrikood("99000002");

    var result = CompanyRelationshipMapper.fromSeos(seos);

    assertThat(result)
        .isEqualTo(
            new CompanyRelationship(
                "J",
                "S",
                "Osanik",
                null,
                "Test Firma OÜ",
                "99000002",
                null,
                null,
                null,
                null,
                null,
                null));
  }
}
