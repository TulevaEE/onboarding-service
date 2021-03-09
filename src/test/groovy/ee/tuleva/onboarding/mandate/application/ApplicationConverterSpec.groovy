package ee.tuleva.onboarding.mandate.application


import ee.tuleva.onboarding.epis.mandate.ApplicationDTO
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.fund.response.FundDto
import spock.lang.Specification

class ApplicationConverterSpec extends Specification {
    FundRepository fundRepository = Mock()
    ApplicationConverter converter = new ApplicationConverter(fundRepository)

    def "converts ApplicationDTO to application"() {
        given:
        ApplicationDTO applicationDTO = ApplicationDTO.builder()
            .type(ApplicationType.TRANSFER)
            .status(ApplicationStatus.PENDING)
            .id(123L)
            .currency("EUR")
            .amount(BigDecimal.ONE)
            .sourceFundIsin("source")
            .targetFundIsin("target")
            .build()
        fundRepository.findByIsin("source") >> sourceFund
        fundRepository.findByIsin("target") >> targetFund

        when:
        Application application = converter.convert(applicationDTO, 'et')

        then:
        application.id == 123L
        application.type == ApplicationType.TRANSFER
        application.status == ApplicationStatus.PENDING
        application.details == TransferApplicationDetails.builder()
            .amount(BigDecimal.ONE)
            .currency("EUR")
            .sourceFund(new FundDto(sourceFund, 'et'))
            .targetFund(new FundDto(targetFund, 'et'))
            .build()
    }

    Fund sourceFund = Fund.builder()
        .nameEstonian("source fund name est")
        .nameEnglish("source fund name eng")
        .build()

    Fund targetFund = Fund.builder()
        .nameEstonian("target fund name est")
        .nameEnglish("target fund name eng")
        .build()
}
