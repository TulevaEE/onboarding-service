package ee.tuleva.onboarding.insurance;

import ee.tuleva.onboarding.insurance.pdf.FormDocument;
import java.util.List;

interface InsurerForm {

  List<FormDocument> documents(InsuranceTransferApplication application);
}
