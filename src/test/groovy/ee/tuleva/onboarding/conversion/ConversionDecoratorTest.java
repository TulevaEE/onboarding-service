package ee.tuleva.onboarding.conversion;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.GrantType.GRANT_TYPE;
import static ee.tuleva.onboarding.auth.principal.AuthenticatedPerson.SMART_ID_DOCUMENT_NUMBER;
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted;
import static ee.tuleva.onboarding.paymentrate.PaymentRatesFixture.samplePaymentRates;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ConversionDecoratorTest {

  @Test
  void recordsTheAuthAttributesWithoutTheSmartIdDocumentNumber() {
    var person =
        sampleAuthenticatedPersonAndMember()
            .attributes(
                Map.of(
                    GRANT_TYPE, "SMART_ID", SMART_ID_DOCUMENT_NUMBER, "PNOEE-38888888888-MOCK-Q"))
            .build();
    Map<String, @Nullable Object> metadata = new HashMap<>();

    new ConversionDecorator()
        .addConversionMetadata(
            metadata, fullyConverted(), true, true, person, samplePaymentRates());

    assertThat(metadata.get("authAttributes")).isEqualTo(Map.of(GRANT_TYPE, "SMART_ID"));
  }
}
