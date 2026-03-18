package ee.tuleva.onboarding.ariregister;

import ee.tuleva.onboarding.ariregister.generated.Seos;
import java.time.LocalDate;
import javax.xml.datatype.XMLGregorianCalendar;

class CompanyPersonMapper {

  static CompanyPerson fromSeos(Seos seos) {
    return new CompanyPerson(
        seos.getEesnimi(),
        seos.getNimiArinimi(),
        seos.getIsikukoodRegistrikood(),
        seos.getIsikuRollTekstina(),
        toLocalDate(seos.getAlgusKpv()),
        toLocalDate(seos.getLoppKpv()));
  }

  private static LocalDate toLocalDate(XMLGregorianCalendar calendar) {
    if (calendar == null) {
      return null;
    }
    return LocalDate.of(calendar.getYear(), calendar.getMonth(), calendar.getDay());
  }
}
