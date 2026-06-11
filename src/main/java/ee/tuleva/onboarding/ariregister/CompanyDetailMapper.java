package ee.tuleva.onboarding.ariregister;

import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6EsindusoiguseEritingimus;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6EsindusoiguseEritingimused;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6Ettevotja;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6Isikuandmed;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6TeatatudTegevusala;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6Yldandmed;
import ee.tuleva.onboarding.country.CountryCodes;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

class CompanyDetailMapper {

  static CompanyDetail fromEttevotja(DetailandmedV6Ettevotja ettevotja) {
    var yldandmed = Optional.ofNullable(ettevotja.getYldandmed());
    var mainActivityRecord = yldandmed.flatMap(CompanyDetailMapper::extractMainActivityRecord);
    return new CompanyDetail(
        ettevotja.getNimi(),
        ettevotja.getAriregistriKood().toString(),
        yldandmed.map(DetailandmedV6Yldandmed::getStaatus).orElse(null),
        yldandmed.map(DetailandmedV6Yldandmed::getOiguslikVorm).orElse(null),
        yldandmed.flatMap(CompanyDetailMapper::extractFoundingDate).orElse(null),
        yldandmed.flatMap(CompanyDetailMapper::extractCurrentAddress).orElse(null),
        mainActivityRecord.map(DetailandmedV6TeatatudTegevusala::getEmtakTekstina).orElse(null),
        mainActivityRecord.map(DetailandmedV6TeatatudTegevusala::getNaceKood).orElse(null),
        extractRepresentationRights(ettevotja));
  }

  private static List<RepresentationRight> extractRepresentationRights(
      DetailandmedV6Ettevotja ettevotja) {
    return Optional.ofNullable(ettevotja.getIsikuandmed())
        .map(DetailandmedV6Isikuandmed::getEsindusoiguseEritingimused)
        .map(DetailandmedV6EsindusoiguseEritingimused::getItem)
        .orElseGet(List::of)
        .stream()
        .map(CompanyDetailMapper::toRepresentationRight)
        .toList();
  }

  private static RepresentationRight toRepresentationRight(
      DetailandmedV6EsindusoiguseEritingimus eritingimus) {
    return new RepresentationRight(
        eritingimus.getEsinduseTyyp(),
        eritingimus.getEsinduseTyypTekstina(),
        eritingimus.getEsinduseSisu(),
        eritingimus.getAlgusKpv(),
        eritingimus.getLoppKpv(),
        Optional.ofNullable(eritingimus.getKirjeId()).map(BigInteger::longValue).orElse(null));
  }

  private static Optional<LocalDate> extractFoundingDate(DetailandmedV6Yldandmed yldandmed) {
    return Optional.ofNullable(yldandmed.getEsmaregistreerimiseKpv());
  }

  private static Optional<CompanyAddress> extractCurrentAddress(DetailandmedV6Yldandmed yldandmed) {
    return Optional.ofNullable(yldandmed.getAadressid())
        .flatMap(
            aadressid ->
                aadressid.getItem().stream().filter(a -> a.getLoppKpv() == null).findFirst())
        .map(
            a ->
                new CompanyAddress(
                    a.getAadressAdsAdsNormaliseeritudTaisaadress(),
                    new AddressDetails(
                        a.getTanavMajaKorter(),
                        a.getEhakNimetus(),
                        a.getPostiindeks(),
                        CountryCodes.toAlpha2(a.getRiik()))));
  }

  private static Optional<DetailandmedV6TeatatudTegevusala> extractMainActivityRecord(
      DetailandmedV6Yldandmed yldandmed) {
    return Optional.ofNullable(yldandmed.getTeatatudTegevusalad())
        .flatMap(
            tegevusalad ->
                tegevusalad.getItem().stream()
                    .filter(t -> Boolean.TRUE.equals(t.isOnPohitegevusala()))
                    .findFirst());
  }
}
