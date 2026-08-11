package ee.tuleva.onboarding.mandate.command;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.country.Country;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CreateMandateCommandTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  void isInvalidWhenNeitherFutureContributionIsinNorFundTransferExchangesPresent() {
    CreateMandateCommand command = commandWith(null, List.of());

    Set<ConstraintViolation<CreateMandateCommand>> violations = validator.validate(command);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString())
        .isEqualTo("sourceIsinPresent");
  }

  @Test
  void isValidWhenFutureContributionIsinPresent() {
    CreateMandateCommand command = commandWith("EE3600109435", List.of());

    assertThat(validator.validate(command)).isEmpty();
  }

  @Test
  void isValidWhenFundTransferExchangesPresent() {
    CreateMandateCommand command = commandWith(null, List.of(sampleExchange()));

    assertThat(validator.validate(command)).isEmpty();
  }

  private CreateMandateCommand commandWith(
      String futureContributionFundIsin, List<MandateFundTransferExchangeCommand> exchanges) {
    CreateMandateCommand command = new CreateMandateCommand();
    command.setFutureContributionFundIsin(futureContributionFundIsin);
    command.setFundTransferExchanges(exchanges);
    command.setAddress(Country.builder().countryCode("EE").build());
    return command;
  }

  private MandateFundTransferExchangeCommand sampleExchange() {
    MandateFundTransferExchangeCommand exchange = new MandateFundTransferExchangeCommand();
    exchange.setSourceFundIsin("EE3600019758");
    exchange.setTargetFundIsin("EE3600109435");
    exchange.setAmount(BigDecimal.ONE);
    return exchange;
  }
}
