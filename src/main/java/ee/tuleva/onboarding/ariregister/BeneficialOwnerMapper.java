package ee.tuleva.onboarding.ariregister;

import ee.tuleva.onboarding.ariregister.generated.kasusaajad.TegelikudKasusaajadV2Kasusaaja;

class BeneficialOwnerMapper {

  static BeneficialOwner fromKasusaaja(TegelikudKasusaajadV2Kasusaaja kasusaaja) {
    return new BeneficialOwner(
        kasusaaja.getEesnimi(),
        kasusaaja.getNimi(),
        kasusaaja.getIsikukood(),
        kasusaaja.getKontrolliTeostamiseViis());
  }
}
