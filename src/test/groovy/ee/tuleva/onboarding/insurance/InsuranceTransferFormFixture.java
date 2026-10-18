package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.ArteaQuestionnaire.MonthlyNetIncome.FROM_1201_TO_3000;
import static ee.tuleva.onboarding.insurance.Insurer.ARTEA;
import static ee.tuleva.onboarding.insurance.Insurer.COMPENSA;
import static ee.tuleva.onboarding.insurance.Insurer.ERGO;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

final class InsuranceTransferFormFixture {

  static final LocalDate TODAY = LocalDate.of(2026, 10, 18);
  static final String PENSION_ACCOUNT_NUMBER = "993432432";
  static final Instant CREATED_AT = Instant.parse("2026-10-18T08:00:00Z");

  private InsuranceTransferFormFixture() {}

  static Applicant applicant() {
    return new Applicant(
        "Jaan",
        "Õunapuu",
        "38001085718",
        "jaan@example.com",
        "+37255555555",
        PENSION_ACCOUNT_NUMBER);
  }

  static Address address() {
    return new Address("Tamme tn 1-2", "Tallinn", "10111", "EE");
  }

  static CreateInsuranceTransferFormCommand compensaRequest() {
    return new CreateInsuranceTransferFormCommand(
        COMPENSA, "UP000123", null, address(), null, null, null, null, false, null);
  }

  static CreateInsuranceTransferFormCommand ergoRequest() {
    return new CreateInsuranceTransferFormCommand(
        ERGO,
        "70-512-345678",
        LocalDate.of(2026, 11, 1),
        address(),
        List.of("EE"),
        "EE",
        "EE",
        false,
        null,
        null);
  }

  static CreateInsuranceTransferFormCommand arteaRequest() {
    return arteaRequest(questionnaire());
  }

  static CreateInsuranceTransferFormCommand arteaRequest(ArteaQuestionnaire questionnaire) {
    return new CreateInsuranceTransferFormCommand(
        ARTEA,
        "UP000123",
        null,
        address(),
        List.of("EE", "FI"),
        "EE",
        "EE",
        false,
        null,
        questionnaire);
  }

  static ArteaQuestionnaire questionnaire() {
    return new ArteaQuestionnaire(
        new IdentityDocument(
            IdentityDocument.Type.ID_CARD,
            "AB0000000",
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2029, 1, 1),
            "EE"),
        new Occupation(Occupation.Status.EMPLOYED, "Spetsialist", "Näidis OÜ", "Tallinn", null),
        List.of(
            new IncomeSource(IncomeSource.Type.SALARY, null),
            new IncomeSource(IncomeSource.Type.DIVIDENDS, "Näidis AS")),
        FROM_1201_TO_3000,
        "EE442200221092874625");
  }

  static InsuranceTransferApplication application(CreateInsuranceTransferFormCommand request) {
    return new InsuranceTransferApplication(applicant(), request, TODAY);
  }

  static InsuranceTransferForm savedForm(Long id, Long userId, Insurer insurer) {
    var form =
        new InsuranceTransferForm(
            userId, insurer, PENSION_ACCOUNT_NUMBER, new byte[] {1, 2, 3}, CREATED_AT);
    ReflectionTestUtils.setField(form, "id", id);
    return form;
  }

  static InsuranceTransferForm signedForm(Long id, Long userId, Insurer insurer) {
    var form = savedForm(id, userId, insurer);
    form.sign(new byte[] {4, 5, 6}, CREATED_AT.plusSeconds(60));
    return form;
  }
}
