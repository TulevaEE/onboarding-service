package ee.tuleva.onboarding.insurance;

import ee.tuleva.onboarding.insurance.pdf.FormDocument;
import ee.tuleva.onboarding.insurance.pdf.FormValues;
import java.util.List;

class ErgoForm implements InsurerForm {

  @Override
  public List<FormDocument> documents(InsuranceTransferApplication application) {
    final String TEMPLATE = "/insurance-transfer-forms/ergo-benefit-payment.pdf";
    Applicant applicant = application.applicant();
    CreateInsuranceTransferFormCommand request = application.request();
    ErgoPolicyNumber policy =
        ErgoPolicyNumber.parse(request.policyNumber())
            .orElseThrow(
                () -> new IllegalArgumentException("Policy number not in the form format"));
    FormValues values =
        new FormValues()
            .text("Aeesnimi", applicant.firstName())
            .text("Aperenimi", applicant.lastName())
            .text("Aisikukood", applicant.personalCode())
            .text("Aaaddress", FormText.address(request.address()))
            .text("Atel", applicant.phoneNumber())
            .text("Aepost", applicant.email())
            .text("A birth", FormText.country(request.countryOfBirth()))
            .text("A citizen", FormText.countries(request.citizenship()))
            .text("A residency", FormText.country(request.taxResidency()))
            .text("no xx", policy.series())
            .text("no xxxxxx", policy.number())
            .choose("opt", "ennet")
            .text("pol  pp kk aaaa", FormText.dateDigits(application.endDateOrToday()))
            .check("market")
            .text("%3", "100")
            .text(
                "teenusepakkuja",
                FormText.FUND_NAME + ", pensionikonto " + applicant.pensionAccountNumber())
            .text("ISIN", FormText.FUND_ISIN)
            .text("KV ees ja perenimi", applicant.fullName())
            .text("KV  pp kk aaaa", FormText.dateDigits(application.today()));
    if (!request.declaredPep()) {
      values.choose("APEP", "ei");
    }
    return List.of(new FormDocument(TEMPLATE, values));
  }
}
