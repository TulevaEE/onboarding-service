package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.PENSION_ACCOUNT_NUMBER;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.application;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.compensaRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompensaFormTest {

  private final CompensaForm form = new CompensaForm();

  @Test
  void fillsTheTerminationFormWithTheTaxFreeTransferToTheFund() throws IOException {
    var documents = form.documents(application(compensaRequest()));

    assertThat(documents).hasSize(1);
    assertThat(FilledForms.fieldValues(documents.getFirst()))
        .isEqualTo(
            Map.ofEntries(
                entry("Eesja perekonnanimi  Juriidilise isiku nimi", "Jaan Õunapuu"),
                entry("Isikukood  Registrikood", "38001085718"),
                entry("Poliisi number", "UP000123"),
                entry(
                    "Postiaadress tänav majaja korterinumber talu küla ja vald postiindeks linn  maakond riik",
                    "Tamme tn 1-2, 10111 Tallinn, Eesti"),
                entry("Eposti aadress  soovin loobuda paberkirjadest", "jaan@example.com"),
                entry("Kontakttelefon", "+37255555555"),
                entry("tm_vaba_ülekanne", "Yes"),
                entry("kehtivale_lepingule", PENSION_ACCOUNT_NUMBER + " (pensionikonto)"),
                entry("kindlustusseltsi_nimi", "Tuleva III Samba Pensionifond, ISIN EE3600001707"),
                entry(
                    "Arvelduskonto omaniku nimi",
                    "Ütlen üles täiendava kogumispensioni kindlustuslepingu nr UP000123 esimesel"
                        + " võimalusel ja soovin kasutada selle kogu tagastusväärtust pensionifondi"
                        + " Tuleva III Samba Pensionifond (ISIN EE3600001707) osakute omandamiseks,"
                        + " pensionikonto 993432432 (KoPS § 63 lg 5, lg 5¹ p 2, lg 5² ja 5³)."
                        + " Tulumaksu ei peeta kinni (TuMS § 21 lg 1¹ p 1 ja 2)."),
                entry("kasusaaja", "YES"),
                entry(
                    "Kindlustusvõtja allkiri või esindaja nimi ja allkiri",
                    "Jaan Õunapuu (digiallkiri)"),
                entry("Allakirjutamise aeg ja koht", "18.10.2026")));
  }

  @Test
  void leavesTheSectionTheInsurerFillsInUntouched() throws IOException {
    var values = FilledForms.fieldValues(form.documents(application(compensaRequest())).getFirst());

    assertThat(values)
        .doesNotContainKeys(
            "fatca",
            "Jah riik",
            "avalduse_vastuvõtja",
            "avalduse_kontrollija",
            "avalduse_vastuvõtmise_kuupäev");
  }

  @Test
  void blanksTheDottedWritingLinesUnderTheStatementSoNoneOfItLooksStruckThrough()
      throws IOException {
    var document = form.documents(application(compensaRequest())).getFirst();

    assertThat(FilledForms.paintsItsBoxWhiteBeforeTheText(document, "Arvelduskonto omaniku nimi"))
        .isTrue();
  }

  @Test
  void printsTheFilledInDetailsIntoAFormWithNoEditableFieldsLeft() throws IOException {
    var documents = form.documents(application(compensaRequest()));

    assertThat(FilledForms.text(documents))
        .contains(
            "Jaan Õunapuu",
            "38001085718",
            "UP000123",
            "Tuleva III Samba Pensionifond",
            "EE3600001707",
            PENSION_ACCOUNT_NUMBER);
    assertThat(FilledForms.hasEditableFields(documents)).isFalse();
  }
}
