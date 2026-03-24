package ee.tuleva.onboarding.ariregister;

import ee.tuleva.onboarding.ariregister.generated.Seos;

class CompanyRelationshipMapper {

  static CompanyRelationship fromSeos(Seos seos) {
    return new CompanyRelationship(
        seos.getIsikuTyyp(),
        seos.getIsikuRoll(),
        seos.getIsikuRollTekstina(),
        seos.getEesnimi(),
        seos.getNimiArinimi(),
        seos.getIsikukoodRegistrikood(),
        seos.getSynniaeg(),
        seos.getAlgusKpv(),
        seos.getLoppKpv(),
        seos.getOsaluseProtsent(),
        seos.getKontrolliTeostamiseViisTekstina(),
        seos.getAadressRiik());
  }
}
