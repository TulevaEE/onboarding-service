package ee.tuleva.onboarding.mandate.transfer

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.mandate.processor.implementation.MandateApplication.MandateApplicationStatus
import org.hamcrest.Matchers
import org.springframework.test.web.servlet.MockMvc

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class TransferExchangeControllerSpec extends BaseControllerSpec {

    TransferExchangeService transferExchangeService = Mock(TransferExchangeService)

    TransferExchangeController controller = new TransferExchangeController(transferExchangeService)

    MockMvc mockMvc

    def setup() {
        mockMvc = mockMvc(controller)
    }

    def "/transfer-exchanges endpoint works"() {
        given:
        1 * transferExchangeService.get(_ as Person) >> sampleTransfersApplicationList

        expect:
        mockMvc.perform(get('/v1/transfer-exchanges')
                    .param('status', 'PENDING'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.*', Matchers.hasSize(1)))
    }

    List<TransferExchange> sampleTransfersApplicationList = [
            TransferExchange.builder()
                    .status(MandateApplicationStatus.FAILED)
                    .build(),
            TransferExchange.builder()
                    .status(MandateApplicationStatus.COMPLETE)
                    .build(),
            TransferExchange.builder()
                    .status(MandateApplicationStatus.PENDING)
                    .build()
    ]

}
