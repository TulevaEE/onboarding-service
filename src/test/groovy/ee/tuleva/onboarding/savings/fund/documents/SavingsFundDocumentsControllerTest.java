package ee.tuleva.onboarding.savings.fund.documents;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SavingsFundDocumentsController.class)
@AutoConfigureMockMvc
@WithMockUser
class SavingsFundDocumentsControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private SavingsFundDocumentsService savingsFundDocumentsService;

  @Test
  void getDocuments_returnsTheThreeDocumentUrls() throws Exception {
    given(savingsFundDocumentsService.getDocuments())
        .willReturn(
            new SavingsFundDocuments(
                "https://tuleva.ee/wp-content/uploads/2026/02/terms.pdf",
                "https://tuleva.ee/wp-content/uploads/2026/02/prospectus.pdf",
                "https://tuleva.ee/wp-content/uploads/2026/02/key-info.pdf"));

    mvc.perform(get("/v1/savings/documents"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {
                      "terms": "https://tuleva.ee/wp-content/uploads/2026/02/terms.pdf",
                      "prospectus": "https://tuleva.ee/wp-content/uploads/2026/02/prospectus.pdf",
                      "keyInformation": "https://tuleva.ee/wp-content/uploads/2026/02/key-info.pdf"
                    }
                    """));
  }
}
