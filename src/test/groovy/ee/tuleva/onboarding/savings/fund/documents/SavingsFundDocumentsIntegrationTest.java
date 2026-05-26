package ee.tuleva.onboarding.savings.fund.documents;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SavingsFundDocumentsIntegrationTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private WordpressDocumentsClient wordpressDocumentsClient;

  @Test
  void servesBakedInFallbackToUnauthenticatedRequest_whenWordpressIsUnavailable() throws Exception {
    given(wordpressDocumentsClient.fetch()).willThrow(new RuntimeException("WordPress is down"));

    mvc.perform(get("/v1/savings/documents"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {
                      "terms": "https://tuleva.ee/wp-content/uploads/2026/02/Tuleva.eurofond.tingimused.02.02.2026.pdf",
                      "prospectus": "https://tuleva.ee/wp-content/uploads/2026/02/TKF100-Prospekt-kehtib-alates-27.02.2026.pdf",
                      "keyInformation": "https://tuleva.ee/wp-content/uploads/2026/02/Pohiteave-TKF100-kehtib-alates-27.02.2026.pdf"
                    }
                    """));

    verify(wordpressDocumentsClient, never()).fetch();
  }
}
