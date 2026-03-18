package ee.tuleva.onboarding.ariregister;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ariregister.generated.Seos;
import java.time.LocalDate;
import javax.xml.datatype.DatatypeFactory;
import org.junit.jupiter.api.Test;

class CompanyPersonMapperTest {

  @Test
  void mapsSeosToCompanyPerson() throws Exception {
    var seos = new Seos();
    seos.setEesnimi("Tõnu");
    seos.setNimiArinimi("Pekk");
    seos.setIsikukoodRegistrikood("37201234567");
    seos.setIsikuRollTekstina("Juhatuse liige");
    seos.setAlgusKpv(DatatypeFactory.newInstance().newXMLGregorianCalendar("2017-04-03"));
    seos.setLoppKpv(DatatypeFactory.newInstance().newXMLGregorianCalendar("2025-12-31"));

    var result = CompanyPersonMapper.fromSeos(seos);

    assertThat(result)
        .isEqualTo(
            new CompanyPerson(
                "Tõnu",
                "Pekk",
                "37201234567",
                "Juhatuse liige",
                LocalDate.of(2017, 4, 3),
                LocalDate.of(2025, 12, 31)));
  }

  @Test
  void handlesNullDates() {
    var seos = new Seos();
    seos.setEesnimi("Mari");
    seos.setNimiArinimi("Maasikas");
    seos.setIsikukoodRegistrikood("48901234567");
    seos.setIsikuRollTekstina("Nõukogu liige");

    var result = CompanyPersonMapper.fromSeos(seos);

    assertThat(result)
        .isEqualTo(
            new CompanyPerson("Mari", "Maasikas", "48901234567", "Nõukogu liige", null, null));
  }
}
