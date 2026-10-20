package ee.tuleva.onboarding.ariregister;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.ariregister.generated.detailandmed.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompanyDetailMapperTest {

  @Test
  void mapsAllFields() {
    var ettevotja =
        ettevotjaWith(
            yldandmedWith(
                address("Pärnu mnt 1"),
                LocalDate.of(2024, 9, 1),
                activity("Fondide valitsemine", true)));

    var result = CompanyDetailMapper.fromEttevotja(ettevotja);

    assertThat(result.getName()).isEqualTo("Test OÜ");
    assertThat(result.getRegistryCode()).isEqualTo("12345678");
    assertThat(result.getStatus()).contains("R");
    assertThat(result.getLegalForm()).contains("OÜ");
    assertThat(result.getFoundingDate()).contains(LocalDate.of(2024, 9, 1));
    assertThat(result.getAddress())
        .contains(
            new CompanyAddress(
                "Pärnu mnt 1", new AddressDetails("Pärnu mnt 1", "Tallinn", "11313", "EE")));
    assertThat(result.getMainActivity()).contains("Fondide valitsemine");
    assertThat(result.getNaceCode()).contains("6630");
    assertThat(result.getActivityCodes()).containsExactly("66301");
    assertThat(result.getRepresentationRights()).isEmpty();
  }

  @Test
  void mapsTheCodesOfEveryCurrentActivityButNotTheEndedOnes() {
    var yldandmed = new DetailandmedV6Yldandmed();
    var tegevusalad = new DetailandmedV6TeatatudTegevusalad();
    tegevusalad.getItem().add(activityRecord("70201", true, null));
    tegevusalad.getItem().add(activityRecord("66301", false, null));
    tegevusalad.getItem().add(activityRecord("69202", true, LocalDate.of(2025, 4, 25)));
    yldandmed.setTeatatudTegevusalad(tegevusalad);

    var result = CompanyDetailMapper.fromEttevotja(ettevotjaWith(yldandmed));

    assertThat(result.getActivityCodes()).containsExactly("66301", "70201");
  }

  @Test
  void refusesACurrentActivityWithoutACodeRatherThanLeaveItUnchecked() {
    var yldandmed = new DetailandmedV6Yldandmed();
    var tegevusalad = new DetailandmedV6TeatatudTegevusalad();
    tegevusalad.getItem().add(activityRecord(null, true, null));
    yldandmed.setTeatatudTegevusalad(tegevusalad);
    var ettevotja = ettevotjaWith(yldandmed);

    assertThatThrownBy(() -> CompanyDetailMapper.fromEttevotja(ettevotja))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void collapsesAJointlyOwnedShareListedOncePerOwnerIntoOneShareholding() {
    var ettevotja =
        ettevotjaWithOffRegisterPersons(
            holding("38501010002", "100.000000", JOINT, 361709),
            holding("40404049996", "100.000000", JOINT, 361709));

    var result = CompanyDetailMapper.fromEttevotja(ettevotja);

    assertThat(result.getShareholdings())
        .containsExactly(
            new Shareholding(new BigDecimal("100.000000"), List.of("38501010002", "40404049996")));
  }

  @Test
  void keepsAJointShareApartFromAShareTheSameOwnerHoldsAlone() {
    var ettevotja =
        ettevotjaWithOffRegisterPersons(
            holding("38501010002", "75.000000", JOINT, 532088),
            holding("40404049996", "75.000000", JOINT, 532088),
            holding("38501010002", "25.000000", SOLE, 532089));

    var result = CompanyDetailMapper.fromEttevotja(ettevotja);

    assertThat(result.getShareholdings())
        .containsExactly(
            new Shareholding(new BigDecimal("75.000000"), List.of("38501010002", "40404049996")),
            new Shareholding(new BigDecimal("25.000000"), List.of("38501010002")));
  }

  @Test
  void countsJointRowsSeparatelyWhenTheyDisagreeOnTheShareOrLackAGroup() {
    var ettevotja =
        ettevotjaWithOffRegisterPersons(
            holding("38501010002", "100.000000", JOINT, 361709),
            holding("40404049996", "50.000000", JOINT, 361709),
            holding("39901019992", "50.000000", JOINT, null));

    var result = CompanyDetailMapper.fromEttevotja(ettevotja);

    assertThat(result.getShareholdings())
        .containsExactly(
            new Shareholding(new BigDecimal("100.000000"), List.of("38501010002")),
            new Shareholding(new BigDecimal("50.000000"), List.of("40404049996")),
            new Shareholding(new BigDecimal("50.000000"), List.of("39901019992")));
  }

  @Test
  void keepsAJointOwnerWithoutAnEstonianCodeOnTheShareAsUnidentified() {
    var ettevotja =
        ettevotjaWithOffRegisterPersons(
            holding("38501010002", "100.000000", JOINT, 361709),
            holding(null, "100.000000", JOINT, 361709));

    var result = CompanyDetailMapper.fromEttevotja(ettevotja);

    assertThat(result.getShareholdings())
        .containsExactly(
            new Shareholding(new BigDecimal("100.000000"), Arrays.asList("38501010002", null)));
  }

  @Test
  void neverMergesSolelyOwnedSharesOfTheSameSize() {
    var ettevotja =
        ettevotjaWithOffRegisterPersons(
            holding("38501010002", "50.000000", SOLE, 1),
            holding("40404049996", "50.000000", SOLE, 1));

    var result = CompanyDetailMapper.fromEttevotja(ettevotja);

    assertThat(result.getShareholdings())
        .containsExactly(
            new Shareholding(new BigDecimal("50.000000"), List.of("38501010002")),
            new Shareholding(new BigDecimal("50.000000"), List.of("40404049996")));
  }

  @Test
  void leavesOutEndedHoldingsAndPersonsWithoutAShare() {
    var ended = holding("38501010002", "100.000000", SOLE, 217920);
    ended.setLoppKpv(LocalDate.of(2023, 9, 2));
    var founder = holding("40404049996", null, SOLE, null);
    founder.setIsikuRoll("A");
    var ettevotja = ettevotjaWithOffRegisterPersons(ended, founder);

    var result = CompanyDetailMapper.fromEttevotja(ettevotja);

    assertThat(result.getShareholdings()).isEmpty();
  }

  @Test
  void handlesNullYldandmed() {
    var ettevotja = ettevotjaWith(null);

    var result = CompanyDetailMapper.fromEttevotja(ettevotja);

    assertThat(result.getName()).isEqualTo("Test OÜ");
    assertThat(result.getRegistryCode()).isEqualTo("12345678");
    assertThat(result.getStatus()).isEmpty();
    assertThat(result.getLegalForm()).isEmpty();
    assertThat(result.getFoundingDate()).isEmpty();
    assertThat(result.getAddress()).isEmpty();
    assertThat(result.getMainActivity()).isEmpty();
    assertThat(result.getNaceCode()).isEmpty();
    assertThat(result.getActivityCodes()).isEmpty();
    assertThat(result.getShareholdings()).isEmpty();
    assertThat(result.getRepresentationRights()).isEmpty();
  }

  @Test
  void mapsRepresentationRights() {
    var ettevotja = ettevotjaWith(null);
    ettevotja.setIsikuandmed(
        isikuandmedWith(
            eritingimus(
                "AINUESINDUS",
                "Juhatuse liige esindab äriühingut ainuisikuliselt",
                "Tehingute tegemiseks on nõutav nõukogu nõusolek",
                LocalDate.of(2023, 1, 15),
                null,
                12345),
            eritingimus("YHISESINDUS", "Ühine esindusõigus", null, null, null, 67890)));

    var result = CompanyDetailMapper.fromEttevotja(ettevotja);

    assertThat(result.getRepresentationRights())
        .containsExactly(
            new RepresentationRight(
                "AINUESINDUS",
                "Juhatuse liige esindab äriühingut ainuisikuliselt",
                "Tehingute tegemiseks on nõutav nõukogu nõusolek",
                LocalDate.of(2023, 1, 15),
                null,
                12345L),
            new RepresentationRight("YHISESINDUS", "Ühine esindusõigus", null, null, null, 67890L));
  }

  @Test
  void selectsCurrentAddress() {
    var yldandmed = new DetailandmedV6Yldandmed();
    var aadressid = new DetailandmedV6Aadressid();

    var old = new DetailandmedV6Aadress();
    old.setAadressAdsAdsNormaliseeritudTaisaadress("Old address");
    old.setAlgusKpv(LocalDate.of(2020, 1, 1));
    old.setLoppKpv(LocalDate.of(2024, 8, 31));

    var current = new DetailandmedV6Aadress();
    current.setAadressAdsAdsNormaliseeritudTaisaadress("Current address");
    current.setAlgusKpv(LocalDate.of(2024, 9, 1));

    aadressid.getItem().add(old);
    aadressid.getItem().add(current);
    yldandmed.setAadressid(aadressid);

    var result = CompanyDetailMapper.fromEttevotja(ettevotjaWith(yldandmed));

    assertThat(result.getAddress())
        .contains(
            new CompanyAddress("Current address", new AddressDetails(null, null, null, null)));
  }

  @Test
  void selectsMainActivity() {
    var yldandmed = new DetailandmedV6Yldandmed();
    var tegevusalad = new DetailandmedV6TeatatudTegevusalad();

    var secondary = new DetailandmedV6TeatatudTegevusala();
    secondary.setEmtakKood("70201");
    secondary.setEmtakTekstina("Secondary");
    secondary.setOnPohitegevusala(false);

    var main = new DetailandmedV6TeatatudTegevusala();
    main.setEmtakKood("62011");
    main.setEmtakTekstina("Main");
    main.setOnPohitegevusala(true);

    tegevusalad.getItem().add(secondary);
    tegevusalad.getItem().add(main);
    yldandmed.setTeatatudTegevusalad(tegevusalad);

    var result = CompanyDetailMapper.fromEttevotja(ettevotjaWith(yldandmed));

    assertThat(result.getMainActivity()).contains("Main");
  }

  private static DetailandmedV6Ettevotja ettevotjaWith(DetailandmedV6Yldandmed yldandmed) {
    var ettevotja = new DetailandmedV6Ettevotja();
    ettevotja.setNimi("Test OÜ");
    ettevotja.setAriregistriKood(BigInteger.valueOf(12345678));
    ettevotja.setYldandmed(yldandmed);
    return ettevotja;
  }

  private static DetailandmedV6Yldandmed yldandmedWith(
      DetailandmedV6Aadressid aadressid,
      LocalDate registrationDate,
      DetailandmedV6TeatatudTegevusalad tegevusalad) {
    var yldandmed = new DetailandmedV6Yldandmed();
    yldandmed.setStaatus("R");
    yldandmed.setOiguslikVorm("OÜ");
    yldandmed.setAadressid(aadressid);
    yldandmed.setEsmaregistreerimiseKpv(registrationDate);
    yldandmed.setTeatatudTegevusalad(tegevusalad);
    return yldandmed;
  }

  private static DetailandmedV6Aadressid address(String normalizedAddress) {
    var aadressid = new DetailandmedV6Aadressid();
    var aadress = new DetailandmedV6Aadress();
    aadress.setAadressAdsAdsNormaliseeritudTaisaadress(normalizedAddress);
    aadress.setTanavMajaKorter("Pärnu mnt 1");
    aadress.setEhakNimetus("Tallinn");
    aadress.setPostiindeks("11313");
    aadress.setRiik("EST");
    aadressid.getItem().add(aadress);
    return aadressid;
  }

  private static DetailandmedV6TeatatudTegevusalad activity(String name, boolean main) {
    var tegevusalad = new DetailandmedV6TeatatudTegevusalad();
    var tegevusala = new DetailandmedV6TeatatudTegevusala();
    tegevusala.setEmtakKood("66301");
    tegevusala.setEmtakTekstina(name);
    tegevusala.setNaceKood("6630");
    tegevusala.setOnPohitegevusala(main);
    tegevusalad.getItem().add(tegevusala);
    return tegevusalad;
  }

  private static DetailandmedV6TeatatudTegevusala activityRecord(
      String emtakCode, boolean main, LocalDate endDate) {
    var tegevusala = new DetailandmedV6TeatatudTegevusala();
    tegevusala.setEmtakKood(emtakCode);
    tegevusala.setOnPohitegevusala(main);
    tegevusala.setLoppKpv(endDate);
    return tegevusala;
  }

  private static final String SOLE = "L";
  private static final String JOINT = "Y";

  private static DetailandmedV6Ettevotja ettevotjaWithOffRegisterPersons(
      DetailandmedV6KaardivalineIsik... persons) {
    var kaardivalised = new DetailandmedV6KaardivalisedIsikud();
    kaardivalised.getItem().addAll(List.of(persons));
    var isikuandmed = new DetailandmedV6Isikuandmed();
    isikuandmed.setKaardivalisedIsikud(kaardivalised);
    var ettevotja = ettevotjaWith(null);
    ettevotja.setIsikuandmed(isikuandmed);
    return ettevotja;
  }

  private static DetailandmedV6KaardivalineIsik holding(
      String personalCode, String percent, String ownershipForm, Integer group) {
    var isik = new DetailandmedV6KaardivalineIsik();
    isik.setIsikuRoll("OSAN");
    isik.setIsikukoodRegistrikood(personalCode);
    isik.setOsaluseProtsent(percent == null ? null : new BigDecimal(percent));
    isik.setOsaluseOmandiliik(ownershipForm);
    isik.setGrupp(group == null ? null : BigInteger.valueOf(group));
    return isik;
  }

  private static DetailandmedV6Isikuandmed isikuandmedWith(
      DetailandmedV6EsindusoiguseEritingimus... eritingimused) {
    var container = new DetailandmedV6EsindusoiguseEritingimused();
    for (var eritingimus : eritingimused) {
      container.getItem().add(eritingimus);
    }
    var isikuandmed = new DetailandmedV6Isikuandmed();
    isikuandmed.setEsindusoiguseEritingimused(container);
    return isikuandmed;
  }

  private static DetailandmedV6EsindusoiguseEritingimus eritingimus(
      String type,
      String typeText,
      String content,
      LocalDate startDate,
      LocalDate endDate,
      int id) {
    var eritingimus = new DetailandmedV6EsindusoiguseEritingimus();
    eritingimus.setEsinduseTyyp(type);
    eritingimus.setEsinduseTyypTekstina(typeText);
    eritingimus.setEsinduseSisu(content);
    eritingimus.setAlgusKpv(startDate);
    eritingimus.setLoppKpv(endDate);
    eritingimus.setKirjeId(BigInteger.valueOf(id));
    return eritingimus;
  }
}
