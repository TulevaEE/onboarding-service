package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.application;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.arteaRequest;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.compensaRequest;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.ergoRequest;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.longestApplication;
import static ee.tuleva.onboarding.insurance.Insurer.ARTEA;
import static ee.tuleva.onboarding.insurance.Insurer.COMPENSA;
import static ee.tuleva.onboarding.insurance.Insurer.ERGO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.insurance.pdf.FormDocument;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class InsurerFormLayoutTest {

  private static final Map<Insurer, InsurerForm> FORMS =
      Map.of(COMPENSA, new CompensaForm(), ERGO, new ErgoForm(), ARTEA, new ArteaForm());

  static Stream<InsuranceTransferApplication> applications() {
    return Stream.of(
        application(compensaRequest()),
        application(ergoRequest()),
        application(arteaRequest()),
        longestApplication(COMPENSA),
        longestApplication(ERGO),
        longestApplication(ARTEA));
  }

  @ParameterizedTest
  @MethodSource("applications")
  void printsEveryValueWhollyInsideItsBox(InsuranceTransferApplication application)
      throws IOException {
    for (FormDocument document : FORMS.get(application.insurer()).documents(application)) {
      assertThat(FilledForms.valuesDrawnOutsideTheirBox(document)).isEmpty();
    }
  }
}
