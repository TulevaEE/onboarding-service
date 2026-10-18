package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.ArteaQuestionnaire.MonthlyNetIncome.FROM_1201_TO_3000;
import static ee.tuleva.onboarding.insurance.ArteaQuestionnaire.MonthlyNetIncome.FROM_3001_TO_6000;
import static ee.tuleva.onboarding.insurance.ArteaQuestionnaire.MonthlyNetIncome.FROM_601_TO_1200;
import static ee.tuleva.onboarding.insurance.ArteaQuestionnaire.MonthlyNetIncome.OVER_6000;
import static ee.tuleva.onboarding.insurance.ArteaQuestionnaire.MonthlyNetIncome.UP_TO_600;
import static ee.tuleva.onboarding.insurance.IdentityDocument.Type.FOREIGN_PASSPORT;
import static ee.tuleva.onboarding.insurance.IdentityDocument.Type.ID_CARD;
import static ee.tuleva.onboarding.insurance.IdentityDocument.Type.PASSPORT;
import static ee.tuleva.onboarding.insurance.IdentityDocument.Type.RESIDENCE_PERMIT;
import static ee.tuleva.onboarding.insurance.IdentityDocument.Type.TEMPORARY_PASSPORT;
import static ee.tuleva.onboarding.insurance.IncomeSource.Type.DIVIDENDS;
import static ee.tuleva.onboarding.insurance.IncomeSource.Type.FAMILY_TRANSFERS;
import static ee.tuleva.onboarding.insurance.IncomeSource.Type.INHERITANCE;
import static ee.tuleva.onboarding.insurance.IncomeSource.Type.INVESTMENTS;
import static ee.tuleva.onboarding.insurance.IncomeSource.Type.LOAN;
import static ee.tuleva.onboarding.insurance.IncomeSource.Type.REAL_ESTATE;
import static ee.tuleva.onboarding.insurance.IncomeSource.Type.RENTAL;
import static ee.tuleva.onboarding.insurance.IncomeSource.Type.SALARY;
import static ee.tuleva.onboarding.insurance.IncomeSource.Type.SELF_EMPLOYMENT;
import static ee.tuleva.onboarding.insurance.Occupation.Status.COMPANY_OWNER;
import static ee.tuleva.onboarding.insurance.Occupation.Status.EMPLOYED;
import static ee.tuleva.onboarding.insurance.Occupation.Status.OTHER;
import static ee.tuleva.onboarding.insurance.Occupation.Status.RETIRED;
import static ee.tuleva.onboarding.insurance.Occupation.Status.SELF_EMPLOYED;
import static ee.tuleva.onboarding.insurance.Occupation.Status.STUDENT;
import static ee.tuleva.onboarding.insurance.Occupation.Status.UNEMPLOYED;
import static ee.tuleva.onboarding.personalcode.PersonalCode.getDateOfBirth;
import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.insurance.pdf.FormDocument;
import ee.tuleva.onboarding.insurance.pdf.FormValues;
import ee.tuleva.onboarding.mandate.details.BankAccountDetails.Bank;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

class ArteaForm implements InsurerForm {

  static final String PAYOUT_TEMPLATE = "/insurance-transfer-forms/artea-pension-payout.pdf";
  static final String QUESTIONNAIRE_TEMPLATE =
      "/insurance-transfer-forms/artea-know-your-customer.pdf";

  private static final Map<IdentityDocument.Type, String> DOCUMENT_OPTIONS =
      Map.of(
          ID_CARD, "0",
          PASSPORT, "1",
          RESIDENCE_PERMIT, "2",
          TEMPORARY_PASSPORT, "3",
          FOREIGN_PASSPORT, "4");

  private static final Map<Occupation.Status, List<String>> OCCUPATION_CHECK_BOXES =
      Map.of(
          EMPLOYED, List.of("Check Box3"),
          SELF_EMPLOYED, List.of("Check Box4", "Check Box12"),
          COMPANY_OWNER, List.of("Check Box16"),
          STUDENT, List.of("Check Box13"),
          RETIRED, List.of("Check Box14"),
          UNEMPLOYED, List.of("Check Box15"),
          OTHER, List.of("Check Box17"));

  private static final Map<Occupation.Status, String> OCCUPATION_DESCRIPTION_FIELDS =
      Map.of(
          SELF_EMPLOYED, "Text Field22",
          COMPANY_OWNER, "Text Field23",
          OTHER, "Text Field24");

  private static final Map<IncomeSource.Type, IncomeSourceFields> INCOME_SOURCE_FIELDS =
      Map.ofEntries(
          entry(SALARY, new IncomeSourceFields("Check Box18", null)),
          entry(SELF_EMPLOYMENT, new IncomeSourceFields("Check Box19", null)),
          entry(DIVIDENDS, new IncomeSourceFields("Check Box20", "Text Field25")),
          entry(INVESTMENTS, new IncomeSourceFields("Check Box21", "Text Field26")),
          entry(LOAN, new IncomeSourceFields("Check Box22", "Text Field27")),
          entry(INHERITANCE, new IncomeSourceFields("Check Box23", "Text Field28")),
          entry(RENTAL, new IncomeSourceFields("Check Box24", "Text Field29")),
          entry(REAL_ESTATE, new IncomeSourceFields("Check Box25", "Text Field30")),
          entry(FAMILY_TRANSFERS, new IncomeSourceFields("Check Box26", "Text Field31")),
          entry(IncomeSource.Type.OTHER, new IncomeSourceFields("Check Box27", "Text Field32")));

  private static final Map<ArteaQuestionnaire.MonthlyNetIncome, String> INCOME_OPTIONS =
      Map.of(
          UP_TO_600, "0",
          FROM_601_TO_1200, "1",
          FROM_1201_TO_3000, "2",
          FROM_3001_TO_6000, "3",
          OVER_6000, "4");

  @Override
  public List<FormDocument> documents(InsuranceTransferApplication application) {
    return List.of(
        new FormDocument(PAYOUT_TEMPLATE, payout(application)),
        new FormDocument(QUESTIONNAIRE_TEMPLATE, questionnaire(application)));
  }

  private static FormValues payout(InsuranceTransferApplication application) {
    Applicant applicant = application.applicant();
    CreateInsuranceTransferFormCommand request = application.request();
    return new FormValues()
        .text("Text Field0", FormText.date(application.today()))
        .text("Text Field1", applicant.fullName())
        .text("Text Field2", applicant.personalCode())
        .text("Text Field3", FormText.countries(request.citizenship()))
        .text("Text Field4", FormText.country(request.taxResidency()))
        .text("Text Field5", applicant.phoneNumber())
        .text("Text Field6", applicant.email())
        .text("Text Field7", FormText.address(request.address()))
        .text("Text Field8", request.policyNumber())
        .text("Text Field9", "ülesütlemisega")
        .text("Text Field11", FormText.FUND_NAME)
        .text("Text Field12", FormText.country(request.taxResidency()))
        .choose("Radio Button0", request.declaredPep() ? "1" : "0")
        .paragraph("Text Field14", TerminationStatement.of(application))
        .text("Text Field15", applicant.fullName());
  }

  private static FormValues questionnaire(InsuranceTransferApplication application) {
    final String ESTONIA = "EE";
    Applicant applicant = application.applicant();
    CreateInsuranceTransferFormCommand request = application.request();
    ArteaQuestionnaire answers = request.questionnaire();
    IdentityDocument document = answers.idDocument();
    FormValues values =
        new FormValues()
            .text("Text Field0", applicant.firstName())
            .text("Text Field1", applicant.personalCode())
            .text("Text Field2", applicant.lastName())
            .text("Text Field3", FormText.date(getDateOfBirth(applicant.personalCode())))
            .text("Text Field4", FormText.country(request.countryOfBirth()))
            .text("Text Field5", FormText.countries(request.citizenship()))
            .text("Text Field6", applicant.email())
            .text("Text Field7", applicant.phoneNumber())
            .text("Text Field8", FormText.address(request.address()))
            .choose("Radio Button0", fieldFor(DOCUMENT_OPTIONS, document.type()))
            .text("Text Field10", document.number())
            .text("Text Field11", FormText.date(document.issuedOn()))
            .text("Text Field12", FormText.date(document.validUntil()))
            .text("Text Field13", FormText.country(document.issuingCountry()))
            .check(ESTONIA.equalsIgnoreCase(request.taxResidency()) ? "Check Box0" : "Check Box1")
            .choose("Radio Button1", fieldFor(INCOME_OPTIONS, answers.monthlyNetIncome()))
            .text("Text Field33", applicant.fullName())
            .text("Text Field34", answers.premiumAccountIban())
            .text("Text Field35", Bank.displayNameFromIban(answers.premiumAccountIban()))
            .text("Text Field44", applicant.fullName())
            .text("Text Field45", FormText.date(application.today()));
    occupation(values, answers.occupation());
    answers.incomeSources().forEach(source -> incomeSource(values, source));
    politicalExposure(values, request.declaredPep());
    return values;
  }

  private static void occupation(FormValues values, Occupation occupation) {
    fieldFor(OCCUPATION_CHECK_BOXES, occupation.status()).forEach(values::check);
    if (occupation.status() == EMPLOYED) {
      values
          .text("Text Field19", occupation.position())
          .text("Text Field20", occupation.employer())
          .text("Text Field21", occupation.employerAddress());
    }
    String descriptionField = OCCUPATION_DESCRIPTION_FIELDS.get(occupation.status());
    if (descriptionField != null) {
      values.paragraph(descriptionField, occupation.description());
    }
  }

  private static void incomeSource(FormValues values, IncomeSource source) {
    IncomeSourceFields fields = fieldFor(INCOME_SOURCE_FIELDS, source.source());
    values.check(fields.checkBox());
    String detailsField = fields.details();
    if (detailsField != null) {
      values.paragraph(detailsField, source.details());
    }
  }

  private static <K, V> V fieldFor(Map<K, V> fields, K answer) {
    return requireNonNull(fields.get(answer), () -> "No form field for answer: answer=" + answer);
  }

  private static void politicalExposure(FormValues values, boolean pep) {
    values.choose("Radio Button2", pep ? "1" : "0");
    if (pep) {
      values.check("Check Box28");
    }
  }

  private record IncomeSourceFields(String checkBox, @Nullable String details) {}
}
