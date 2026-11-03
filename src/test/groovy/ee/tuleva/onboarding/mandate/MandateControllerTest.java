package ee.tuleva.onboarding.mandate;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.mandate.generic.GenericMandateService;
import ee.tuleva.onboarding.signature.IdCardSignatureSession;
import ee.tuleva.onboarding.signature.SignableEntity;
import ee.tuleva.onboarding.signature.SigningServiceUnavailableException;
import ee.tuleva.onboarding.signature.SmartIdSignatureSession;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.digidoc4j.exceptions.OCSPRequestFailedException;
import org.digidoc4j.exceptions.TslDownloadException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(MandateController.class)
class MandateControllerTest {

  private static final long MANDATE_ID = 1L;
  private static final String SIGNING_UNAVAILABLE =
      """
      {"errors": [{"code": "signature.service.unavailable",
                   "message": "Signing is temporarily unavailable, please retry later.",
                   "arguments": []}]}
      """;

  @Autowired private MockMvc mvc;

  @MockitoBean private MandateRepository mandateRepository;
  @MockitoBean private MandateService mandateService;
  @MockitoBean private GenericMandateService genericMandateService;
  @MockitoBean private GenericSessionStore sessionStore;
  @MockitoBean private SignatureFileArchiver signatureFileArchiver;
  @MockitoBean private MandateFileService mandateFileService;
  @MockitoBean private LocaleService localeService;

  private final AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build();
  private final SignableEntity mandate = new SignableEntity("Mandate", MANDATE_ID);

  @Test
  void asksTheSmartIdSignerToRetryLaterWhenTheSignatureCannotBeValidatedAgainstTheTrustList()
      throws Exception {
    var session = new SmartIdSignatureSession(mandate, person.getPersonalCode(), List.of());
    given(sessionStore.get(SmartIdSignatureSession.class)).willReturn(Optional.of(session));
    given(localeService.getCurrentLocale()).willReturn(Locale.ENGLISH);
    given(
            mandateService.finalizeSmartIdSignature(
                person.getUserIdOrThrow(), MANDATE_ID, session, Locale.ENGLISH))
        .willThrow(new SigningServiceUnavailableException(new OCSPRequestFailedException("S-1")));

    mvc.perform(
            get("/v1/mandates/{id}/signature/smart-id/status", MANDATE_ID)
                .with(csrf())
                .with(signedInAs(person)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "60"))
        .andExpect(content().json(SIGNING_UNAVAILABLE, true));
  }

  @Test
  void asksTheIdCardSignerToRetryLaterWhenTheTrustListCannotBeDownloaded() throws Exception {
    var session =
        IdCardSignatureSession.builder()
            .signableEntity(mandate)
            .hashToSign("hash")
            .hashFunction("SHA-256")
            .build();
    given(sessionStore.get(IdCardSignatureSession.class)).willReturn(Optional.of(session));
    given(localeService.getCurrentLocale()).willReturn(Locale.ENGLISH);
    given(
            mandateService.persistIdCardSignature(
                eq(person.getUserIdOrThrow()),
                eq(MANDATE_ID),
                eq(session),
                anyString(),
                any(Locale.class)))
        .willThrow(
            new SigningServiceUnavailableException(
                new TslDownloadException("Failed to download LoTL")));

    mvc.perform(
            put("/v1/mandates/{id}/signature/id-card/signature", MANDATE_ID)
                .with(csrf())
                .with(signedInAs(person))
                .contentType(APPLICATION_JSON)
                .content("{\"signature\": \"c2lnbmF0dXJl\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "60"))
        .andExpect(content().json(SIGNING_UNAVAILABLE, true));
  }

  private static RequestPostProcessor signedInAs(AuthenticatedPerson person) {
    return authentication(
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER))));
  }
}
