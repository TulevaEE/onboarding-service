package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.epis.mandate.ApplicationDTO
import spock.lang.Specification

import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL
import static java.math.BigDecimal.ONE

class ApplicationConverterSpec extends Specification {

  ApplicationConverter converter = new ApplicationConverter()

  def "converts ApplicationDTO to application - transfer"() {
    given:
    ApplicationDTO applicationDTO = sampleApplicationDto()

    when:
    Application application = converter.convert(applicationDTO)

    then:
    application.id == 123L
    application.type == TRANSFER
    application.status == PENDING
    application.details == TransferApplicationDetails.builder()
      .amount(ONE)
      .currency("EUR")
      .sourceFundIsin("source")
      .targetFundIsin("target")
      .build()
  }

  def "converts ApplicationDTO to application - withdrawal"() {
    given:
    ApplicationDTO applicationDTO = ApplicationDTO.builder()
      .status(PENDING)
      .type(WITHDRAWAL)
      .bankAccount("IBAN")
      .id(123L)
      .build()

    when:
    Application application = converter.convert(applicationDTO)

    then:
    application.id == 123L
    application.type == WITHDRAWAL
    application.status == PENDING
    application.details == WithdrawalApplicationDetails.builder()
      .depositAccountIBAN("IBAN")
      .build()
  }
}
