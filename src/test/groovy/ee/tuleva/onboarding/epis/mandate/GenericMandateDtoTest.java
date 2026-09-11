package ee.tuleva.onboarding.epis.mandate;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.details.SelectionMandateDetails;
import org.junit.jupiter.api.Test;

class GenericMandateDtoTest {

  @Test
  void getMandateType_delegatesToDetails() {
    GenericMandateDto<SelectionMandateDetails> dto =
        GenericMandateDto.<SelectionMandateDetails>builder()
            .details(new SelectionMandateDetails("EE3600109435"))
            .build();

    assertThat(dto.getMandateType()).isEqualTo(MandateType.SELECTION);
  }
}
