package ee.tuleva.onboarding.mandate.transfer

import ee.tuleva.onboarding.auth.PersonFixture
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.MandateFixture
import ee.tuleva.onboarding.mandate.processor.implementation.EpisService
import ee.tuleva.onboarding.mandate.processor.implementation.MandateApplication.MandateApplicationStatus
import ee.tuleva.onboarding.mandate.processor.implementation.MandateApplication.TransferExchangeDTO
import spock.lang.Specification

class TransferExchangeServiceSpec extends Specification {

    EpisService episService = Mock(EpisService)
    FundRepository fundRepository = Mock(FundRepository)
    TransferExchangeService service = new TransferExchangeService(episService, fundRepository)

    def "Get: return transfer exchanges"() {
        given:
        1 * episService.getTransferApplications(PersonFixture.samplePerson) >> sampleTransfersApplicationList
        2 * fundRepository.findByIsin(_ as String) >> MandateFixture.sampleFunds().first()

        when:
        List<TransferExchange> exchanges =
                service.get(PersonFixture.samplePerson)

        then:
        exchanges.size() == 3
    }

    List<TransferExchangeDTO> sampleTransfersApplicationList = [
            TransferExchangeDTO.builder()
                    .status(MandateApplicationStatus.FAILED)
                    .build(),
            TransferExchangeDTO.builder()
                    .status(MandateApplicationStatus.COMPLETE)
                    .build(),
            TransferExchangeDTO.builder()
                    .status(MandateApplicationStatus.PENDING)
                    .targetFundIsin("123")
                    .sourceFundIsin("223")
                    .build()
    ]
}
