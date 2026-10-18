package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.application;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.ergoRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErgoFormTest {

  private final ErgoForm form = new ErgoForm();

  @Test
  void fillsTheBenefitPaymentFormToEndTheContractAndTransferEverythingToTheFund()
      throws IOException {
    var documents = form.documents(application(ergoRequest()));

    assertThat(documents).hasSize(1);
    var values = FilledForms.fieldValues(documents.getFirst());
    assertThat(values.get("opt")).startsWith("ennet");
    values.remove("opt");
    assertThat(values)
        .isEqualTo(
            Map.ofEntries(
                entry("Aeesnimi", "Jaan"),
                entry("Aperenimi", "Õunapuu"),
                entry("Aisikukood", "38001085718"),
                entry("Aaaddress", "Tamme tn 1-2, 10111 Tallinn, Eesti"),
                entry("Atel", "+37255555555"),
                entry("Aepost", "jaan@example.com"),
                entry("A birth", "Eesti"),
                entry("A citizen", "Eesti"),
                entry("A residency", "Eesti"),
                entry("APEP", "ei"),
                entry("no xx", "12"),
                entry("no xxxxxx", "345678"),
                entry("pol  pp kk aaaa", "01112026"),
                entry("market", "yes"),
                entry("%3", "100"),
                entry("teenusepakkuja", "Tuleva III Samba Pensionifond"),
                entry("ISIN", "EE3600001707"),
                entry("KV ees ja perenimi", "Jaan Õunapuu"),
                entry("KV  pp kk aaaa", "18102026")));
  }

  @Test
  void endsTheContractFromTodayWhenNoEndDateIsChosenAndMarksAPoliticallyExposedPerson()
      throws IOException {
    var request = ergoRequest();
    var asSoonAsPossible =
        new CreateInsuranceTransferFormCommand(
            request.insurer(),
            "12-345678",
            null,
            request.address(),
            List.of("EE", "FI"),
            "LV",
            "FI",
            true,
            null,
            null);

    var values = FilledForms.fieldValues(form.documents(application(asSoonAsPossible)).getFirst());

    assertThat(values)
        .contains(
            entry("pol  pp kk aaaa", "18102026"),
            entry("no xx", "12"),
            entry("no xxxxxx", "345678"),
            entry("A birth", "Läti"),
            entry("A citizen", "Eesti, Soome"),
            entry("A residency", "Soome"),
            entry("APEP", "jah"));
  }

  @Test
  void printsTheFilledInDetailsIntoAFormWithNoEditableFieldsLeft() throws IOException {
    var documents = form.documents(application(ergoRequest()));

    assertThat(FilledForms.text(documents))
        .contains("Jaan", "Õunapuu", "Tuleva III Samba Pensionifond");
    assertThat(FilledForms.hasEditableFields(documents)).isFalse();
  }
}
