package ee.tuleva.onboarding.mandate.command.application.transfer

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.mandate.processor.implementation.EpisService
import ee.tuleva.onboarding.mandate.processor.implementation.MandateApplication.MandateApplicationStatus
import ee.tuleva.onboarding.mandate.processor.implementation.MandateApplication.TransferExchangeDTO
import org.hamcrest.Matchers
import org.springframework.test.web.servlet.MockMvc

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class TransferExchangeControllerSpec extends BaseControllerSpec {

    EpisService episService = Mock(EpisService)

    TransferExchangeController controller = new TransferExchangeController(episService)

    MockMvc mockMvc

    def setup() {
        mockMvc = mockMvc(controller)
    }

    def "/transfer-exchanges endpoint works"() {
        given:
        1 * episService.getTransferApplications(_ as Person) >> sampleTransfersApplicationList

        expect:
        mockMvc.perform(get('/v1/transfer-exchanges')
                    .param('status', 'PENDING'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.*', Matchers.hasSize(1)))
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
                    .targetFundIsin("target fund isin")
                    .sourceFundIsin("source fund isin")
                    .build()
    ]

}
