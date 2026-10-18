package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.ArteaQuestionnaire.MonthlyNetIncome.OVER_6000;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.application;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.arteaRequest;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.questionnaire;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArteaFormTest {

  private final ArteaForm form = new ArteaForm();

  @Test
  void fillsThePayoutFormToEndTheContractAndMoveItsValueToTheFund() throws IOException {
    var documents = form.documents(application(arteaRequest()));

    assertThat(documents)
        .extracting(document -> document.template())
        .containsExactly(ArteaForm.PAYOUT_TEMPLATE, ArteaForm.QUESTIONNAIRE_TEMPLATE);
    assertThat(FilledForms.fieldValues(documents.getFirst()))
        .isEqualTo(
            Map.ofEntries(
                entry("Text Field0", "18.10.2026"),
                entry("Text Field1", "Jaan Õunapuu"),
                entry("Text Field2", "38001085718"),
                entry("Text Field3", "Eesti, Soome"),
                entry("Text Field4", "Eesti"),
                entry("Text Field5", "+37255555555"),
                entry("Text Field6", "jaan@example.com"),
                entry("Text Field7", "Tamme tn 1-2, 10111 Tallinn, Eesti"),
                entry("Text Field8", "SP000123"),
                entry("Text Field9", "ülesütlemisega"),
                entry("Text Field11", "Tuleva III Samba Pensionifond"),
                entry("Text Field12", "Eesti"),
                entry("Radio Button0", "0"),
                entry(
                    "Text Field14",
                    "Ütlen üles täiendava kogumispensioni kindlustuslepingu nr SP000123 esimesel"
                        + " võimalusel ja soovin kasutada selle kogu tagastusväärtust pensionifondi"
                        + " Tuleva III Samba Pensionifond (ISIN EE3600001707) osakute omandamiseks,"
                        + " pensionikonto 993432432 (KoPS § 63 lg 5, lg 5¹ p 2, lg 5² ja 5³)."
                        + " Tulumaksu ei peeta kinni (TuMS § 21 lg 1¹ p 1 ja 2)."),
                entry("Text Field15", "Jaan Õunapuu")));
  }

  @Test
  void answersTheCustomerQuestionnaireForAnEmployedPerson() throws IOException {
    var documents = form.documents(application(arteaRequest()));

    assertThat(FilledForms.fieldValues(documents.get(1)))
        .isEqualTo(
            Map.ofEntries(
                entry("Text Field0", "Jaan"),
                entry("Text Field1", "38001085718"),
                entry("Text Field2", "Õunapuu"),
                entry("Text Field3", "08.01.1980"),
                entry("Text Field4", "Eesti"),
                entry("Text Field5", "Eesti, Soome"),
                entry("Text Field6", "jaan@example.com"),
                entry("Text Field7", "+37255555555"),
                entry("Text Field8", "Tamme tn 1-2, 10111 Tallinn, Eesti"),
                entry("Radio Button0", "0"),
                entry("Text Field10", "AB0000000"),
                entry("Text Field11", "01.01.2024"),
                entry("Text Field12", "01.01.2029"),
                entry("Text Field13", "Eesti"),
                entry("Check Box0", "Yes"),
                entry("Check Box3", "Yes"),
                entry("Text Field19", "Spetsialist"),
                entry("Text Field20", "Näidis OÜ"),
                entry("Text Field21", "Tallinn"),
                entry("Check Box18", "Yes"),
                entry("Check Box20", "Yes"),
                entry("Text Field25", "Näidis AS"),
                entry("Radio Button1", "2"),
                entry("Text Field33", "Jaan Õunapuu"),
                entry("Text Field34", "EE442200221092874625"),
                entry("Text Field35", "Swedbank AS"),
                entry("Radio Button2", "0"),
                entry("Text Field44", "Jaan Õunapuu"),
                entry("Text Field45", "18.10.2026")));
  }

  @Test
  void printsTheContractNumberAsTheInsurerWritesItHoweverItWasTyped() throws IOException {
    var values =
        FilledForms.fieldValues(
            form.documents(application(arteaRequest(" sp-000123 "))).getFirst());

    assertThat(values).contains(entry("Text Field8", "SP000123"));
    assertThat(values.get("Text Field14")).contains("kindlustuslepingu nr SP000123 ");
  }

  @Test
  void printsThePremiumAccountIbanInTheFormItsBankIsReadFrom() throws IOException {
    var answers = questionnaire();
    var typedIban =
        new ArteaQuestionnaire(
            answers.idDocument(),
            answers.occupation(),
            answers.incomeSources(),
            answers.monthlyNetIncome(),
            " ee44 2200 2210 9287 4625 ");

    var values =
        FilledForms.fieldValues(form.documents(application(arteaRequest(typedIban))).get(1));

    assertThat(values)
        .contains(
            entry("Text Field34", "EE442200221092874625"), entry("Text Field35", "Swedbank AS"));
  }

  @Test
  void describesAnOccupationOtherThanEmploymentAndMarksAPoliticallyExposedForeignResident()
      throws IOException {
    var answers = questionnaire();
    var companyOwner =
        new ArteaQuestionnaire(
            new IdentityDocument(
                IdentityDocument.Type.FOREIGN_PASSPORT,
                "FI1234567",
                LocalDate.of(2023, 5, 1),
                LocalDate.of(2033, 5, 1),
                "FI"),
            new Occupation(
                Occupation.Status.COMPANY_OWNER,
                "ignored",
                "ignored",
                "ignored",
                "Näidis OÜ, Tallinn, konsultatsioonid"),
            List.of(new IncomeSource(IncomeSource.Type.OTHER, "Säästud")),
            OVER_6000,
            answers.premiumAccountIban());
    var request = arteaRequest(companyOwner);
    var foreignResident =
        new CreateInsuranceTransferFormCommand(
            request.insurer(),
            request.policyNumber(),
            request.endDate(),
            request.address(),
            List.of("FI"),
            "FI",
            "FI",
            true,
            null,
            companyOwner);

    var values = FilledForms.fieldValues(form.documents(application(foreignResident)).get(1));

    assertThat(values)
        .contains(
            entry("Radio Button0", "4"),
            entry("Check Box1", "Yes"),
            entry("Check Box16", "Yes"),
            entry("Text Field23", "Näidis OÜ, Tallinn, konsultatsioonid"),
            entry("Check Box27", "Yes"),
            entry("Text Field32", "Säästud"),
            entry("Radio Button1", "4"),
            entry("Radio Button2", "1"),
            entry("Check Box28", "Yes"))
        .doesNotContainKeys("Check Box0", "Check Box3", "Text Field19", "Text Field20");
  }

  @Test
  void mergesThePayoutFormAndTheQuestionnaireIntoOneDocumentWithNoEditableFieldsLeft()
      throws IOException {
    var documents = form.documents(application(arteaRequest()));

    assertThat(FilledForms.pageCount(documents)).isEqualTo(4);
    assertThat(FilledForms.text(documents))
        .contains("Jaan Õunapuu", "SP000123", "Tuleva III Samba Pensionifond", "AB0000000");
    assertThat(FilledForms.hasEditableFields(documents)).isFalse();
  }
}
