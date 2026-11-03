package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.Insurer.ARTEA;
import static ee.tuleva.onboarding.insurance.Insurer.COMPENSA;
import static ee.tuleva.onboarding.insurance.Insurer.ERGO;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.insurance.pdf.FillablePdf;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class InsurerForms {

  private final Map<Insurer, InsurerForm> forms =
      Map.of(COMPENSA, new CompensaForm(), ERGO, new ErgoForm(), ARTEA, new ArteaForm());

  byte[] fill(InsuranceTransferApplication application) {
    InsurerForm form =
        requireNonNull(
            forms.get(application.insurer()),
            () -> "No form for insurer: insurer=" + application.insurer());
    return FillablePdf.fillAndFlatten(form.documents(application));
  }
}
