package ee.tuleva.onboarding.ariregister;

import ee.tuleva.onboarding.ariregister.generated.Seos;
import java.time.LocalDate;
import javax.xml.datatype.XMLGregorianCalendar;

class CompanyRelationshipMapper {

  static CompanyRelationship fromSeos(Seos seos) {
    return new CompanyRelationship(
        seos.getIsikuTyyp(),
        seos.getIsikuRoll(),
        seos.getIsikuRollTekstina(),
        seos.getEesnimi(),
        seos.getNimiArinimi(),
        seos.getIsikukoodRegistrikood(),
        toLocalDate(seos.getSynniaeg()),
        toLocalDate(seos.getAlgusKpv()),
        toLocalDate(seos.getLoppKpv()),
        seos.getOsaluseProtsent(),
        seos.getKontrolliTeostamiseViisTekstina(),
        seos.getAadressRiik());
  }

  private static LocalDate toLocalDate(XMLGregorianCalendar calendar) {
    if (calendar == null) {
      return null;
    }
    return LocalDate.of(calendar.getYear(), calendar.getMonth(), calendar.getDay());
  }
}
