package ee.tuleva.onboarding.insurance;

import ee.tuleva.onboarding.insurance.pdf.FormDocument;
import ee.tuleva.onboarding.insurance.pdf.FormValues;
import java.util.List;

class CompensaForm implements InsurerForm {

  @Override
  public List<FormDocument> documents(InsuranceTransferApplication application) {
    final String TEMPLATE = "/insurance-transfer-forms/compensa-contract-termination.pdf";
    Applicant applicant = application.applicant();
    CreateInsuranceTransferFormCommand request = application.request();
    FormValues values =
        new FormValues()
            .text("Eesja perekonnanimi  Juriidilise isiku nimi", applicant.fullName())
            .text("Isikukood  Registrikood", applicant.personalCode())
            .text("Poliisi number", request.policyNumber())
            .text(
                "Postiaadress tänav majaja korterinumber talu küla ja vald postiindeks linn  maakond riik",
                FormText.address(request.address()))
            .text("Eposti aadress  soovin loobuda paberkirjadest", applicant.email())
            .text("Kontakttelefon", applicant.phoneNumber())
            .check("tm_vaba_ülekanne")
            .text("kehtivale_lepingule", applicant.pensionAccountNumber() + " (pensionikonto)")
            .text("kindlustusseltsi_nimi", FormText.fundWithIsin())
            .paragraph("Arvelduskonto omaniku nimi", TerminationStatement.of(application))
            .choose("kasusaaja", "YES")
            .text(
                "Kindlustusvõtja allkiri või esindaja nimi ja allkiri",
                applicant.fullName() + " (digiallkiri)")
            .text("Allakirjutamise aeg ja koht", FormText.date(application.today()));
    return List.of(new FormDocument(TEMPLATE, declareUsPerson(values, request.declaredUsPerson())));
  }

  private static FormValues declareUsPerson(FormValues values, boolean usPerson) {
    return usPerson
        ? values.choose("fatca", "YES").text("Jah riik", FormText.country("US"))
        : values.choose("fatca", "NO");
  }
}
