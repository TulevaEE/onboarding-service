package ee.tuleva.onboarding.ariregister;

import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6EsindusoiguseEritingimus;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6EsindusoiguseEritingimused;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6Ettevotja;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6Isikuandmed;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6KaardivalineIsik;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6KaardivalisedIsikud;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6TeatatudTegevusala;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6TeatatudTegevusalad;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6Yldandmed;
import ee.tuleva.onboarding.country.CountryCodes;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

class CompanyDetailMapper {

  private static final String JOINT_OWNERSHIP = "Y";

  private record JointShare(BigInteger group, BigDecimal ownershipPercent) {}

  static CompanyDetail fromEttevotja(DetailandmedV6Ettevotja ettevotja) {
    var yldandmed = Optional.ofNullable(ettevotja.getYldandmed());
    var mainActivityRecord = yldandmed.flatMap(CompanyDetailMapper::extractMainActivityRecord);
    return CompanyDetail.builder()
        .name(ettevotja.getNimi())
        .registryCode(ettevotja.getAriregistriKood().toString())
        .status(yldandmed.map(DetailandmedV6Yldandmed::getStaatus).orElse(null))
        .legalForm(yldandmed.map(DetailandmedV6Yldandmed::getOiguslikVorm).orElse(null))
        .foundingDate(yldandmed.flatMap(CompanyDetailMapper::extractFoundingDate).orElse(null))
        .address(yldandmed.flatMap(CompanyDetailMapper::extractCurrentAddress).orElse(null))
        .mainActivity(
            mainActivityRecord.map(DetailandmedV6TeatatudTegevusala::getEmtakTekstina).orElse(null))
        .naceCode(
            mainActivityRecord.map(DetailandmedV6TeatatudTegevusala::getNaceKood).orElse(null))
        .activityCodes(
            yldandmed.map(CompanyDetailMapper::extractCurrentActivityCodes).orElseGet(List::of))
        .shareholdings(extractCurrentShareholdings(ettevotja))
        .representationRights(extractRepresentationRights(ettevotja))
        .build();
  }

  private static List<Shareholding> extractCurrentShareholdings(DetailandmedV6Ettevotja ettevotja) {
    var currentHoldings =
        Optional.ofNullable(ettevotja.getIsikuandmed())
            .map(DetailandmedV6Isikuandmed::getKaardivalisedIsikud)
            .map(DetailandmedV6KaardivalisedIsikud::getItem)
            .orElseGet(List::of)
            .stream()
            .filter(isik -> isik.getLoppKpv() == null && isik.getOsaluseProtsent() != null)
            .toList();
    var jointlyOwnedShares =
        currentHoldings.stream()
            .filter(CompanyDetailMapper::isJointlyOwned)
            .collect(
                groupingBy(
                    isik -> new JointShare(isik.getGrupp(), isik.getOsaluseProtsent()),
                    LinkedHashMap::new,
                    flatMapping(CompanyDetailMapper::ownerCode, toList())))
            .entrySet()
            .stream()
            .map(share -> new Shareholding(share.getKey().ownershipPercent(), share.getValue()));
    var otherShares =
        currentHoldings.stream()
            .filter(isik -> !isJointlyOwned(isik))
            .map(isik -> new Shareholding(isik.getOsaluseProtsent(), ownerCode(isik).toList()));
    return Stream.concat(jointlyOwnedShares, otherShares).toList();
  }

  private static boolean isJointlyOwned(DetailandmedV6KaardivalineIsik isik) {
    return JOINT_OWNERSHIP.equals(isik.getOsaluseOmandiliik()) && isik.getGrupp() != null;
  }

  private static Stream<String> ownerCode(DetailandmedV6KaardivalineIsik isik) {
    return Stream.ofNullable(isik.getIsikukoodRegistrikood());
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

  private static List<String> extractCurrentActivityCodes(DetailandmedV6Yldandmed yldandmed) {
    return Optional.ofNullable(yldandmed.getTeatatudTegevusalad())
        .map(DetailandmedV6TeatatudTegevusalad::getItem)
        .orElseGet(List::of)
        .stream()
        .filter(tegevusala -> tegevusala.getLoppKpv() == null)
        .map(CompanyDetailMapper::activityCode)
        .distinct()
        .sorted()
        .toList();
  }

  private static String activityCode(DetailandmedV6TeatatudTegevusala tegevusala) {
    var code = tegevusala.getEmtakKood();
    if (code == null) {
      throw new IllegalStateException(
          "Current business activity without an EMTAK code: kirjeId=" + tegevusala.getKirjeId());
    }
    return code;
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
