package ee.tuleva.onboarding.ariregister;

import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6Aadress;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6Ettevotja;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6TeatatudTegevusala;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6Yldandmed;
import java.time.LocalDate;
import java.util.Optional;

class CompanyDetailMapper {

  static CompanyDetail fromEttevotja(DetailandmedV6Ettevotja ettevotja) {
    var yldandmed = Optional.ofNullable(ettevotja.getYldandmed());
    return new CompanyDetail(
        ettevotja.getNimi(),
        ettevotja.getAriregistriKood().toString(),
        yldandmed.map(DetailandmedV6Yldandmed::getStaatus).orElse(null),
        yldandmed.flatMap(CompanyDetailMapper::extractFoundingDate).orElse(null),
        yldandmed.flatMap(CompanyDetailMapper::extractCurrentAddress).orElse(null),
        yldandmed.flatMap(CompanyDetailMapper::extractMainActivity).orElse(null));
  }

  private static Optional<LocalDate> extractFoundingDate(DetailandmedV6Yldandmed yldandmed) {
    return Optional.ofNullable(yldandmed.getEsmaregistreerimiseKpv());
  }

  private static Optional<String> extractCurrentAddress(DetailandmedV6Yldandmed yldandmed) {
    return Optional.ofNullable(yldandmed.getAadressid())
        .flatMap(
            aadressid ->
                aadressid.getItem().stream().filter(a -> a.getLoppKpv() == null).findFirst())
        .map(DetailandmedV6Aadress::getAadressAdsAdsNormaliseeritudTaisaadress);
  }

  private static Optional<String> extractMainActivity(DetailandmedV6Yldandmed yldandmed) {
    return Optional.ofNullable(yldandmed.getTeatatudTegevusalad())
        .flatMap(
            tegevusalad ->
                tegevusalad.getItem().stream()
                    .filter(t -> Boolean.TRUE.equals(t.isOnPohitegevusala()))
                    .findFirst())
        .map(DetailandmedV6TeatatudTegevusala::getEmtakTekstina);
  }
}
