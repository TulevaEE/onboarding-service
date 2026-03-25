package ee.tuleva.onboarding.ariregister;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ariregister.generated.detailandmed.*;
import java.math.BigInteger;
import java.time.LocalDate;
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
    assertThat(result.getAddress()).contains("Pärnu mnt 1");
    assertThat(result.getMainActivity()).contains("Fondide valitsemine");
    assertThat(result.getNaceCode()).contains("6630");
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

    assertThat(result.getAddress()).contains("Current address");
  }

  @Test
  void selectsMainActivity() {
    var yldandmed = new DetailandmedV6Yldandmed();
    var tegevusalad = new DetailandmedV6TeatatudTegevusalad();

    var secondary = new DetailandmedV6TeatatudTegevusala();
    secondary.setEmtakTekstina("Secondary");
    secondary.setOnPohitegevusala(false);

    var main = new DetailandmedV6TeatatudTegevusala();
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
    aadressid.getItem().add(aadress);
    return aadressid;
  }

  private static DetailandmedV6TeatatudTegevusalad activity(String name, boolean main) {
    var tegevusalad = new DetailandmedV6TeatatudTegevusalad();
    var tegevusala = new DetailandmedV6TeatatudTegevusala();
    tegevusala.setEmtakTekstina(name);
    tegevusala.setNaceKood("6630");
    tegevusala.setOnPohitegevusala(main);
    tegevusalad.getItem().add(tegevusala);
    return tegevusalad;
  }
}
