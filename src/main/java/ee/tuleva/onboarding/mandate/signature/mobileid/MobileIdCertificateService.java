package ee.tuleva.onboarding.mandate.signature.mobileid;

import ee.sk.mid.MidClient;
import ee.sk.mid.exception.MidInternalErrorException;
import ee.sk.mid.exception.MidMissingOrInvalidParameterException;
import ee.sk.mid.exception.MidNotMidClientException;
import ee.sk.mid.exception.MidUnauthorizedException;
import ee.sk.mid.rest.dao.request.MidCertificateRequest;
import ee.sk.mid.rest.dao.response.MidCertificateChoiceResponse;
import ee.tuleva.onboarding.auth.exception.MobileIdException;
import java.security.cert.X509Certificate;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class MobileIdCertificateService {
  private MidClient client;

  public X509Certificate getCertificate(String personalCode, String phoneNumber) {
    MidCertificateRequest request =
        MidCertificateRequest.newBuilder()
            .withPhoneNumber(phoneNumber)
            .withNationalIdentityNumber(personalCode)
            .build();

    try {
      MidCertificateChoiceResponse response = client.getMobileIdConnector().getCertificate(request);
      return client.createMobileIdCertificate(response);
    } catch (MidNotMidClientException e) {
      log.info("User is not a MID client or user's certificates are revoked");
      throw new MobileIdException(
          "You are not a Mobile-ID client or your Mobile-ID certificates are revoked. Please contact your mobile operator.");
    } catch (MidMissingOrInvalidParameterException | MidUnauthorizedException e) {
      log.error(
          "Integrator-side error with MID integration (including insufficient input validation) or configuration",
          e);
      throw new MobileIdException("Client side error with mobile-ID integration.", e);
    } catch (MidInternalErrorException e) {
      log.warn("MID service returned internal error that cannot be handled locally.");
      throw new MobileIdException("MID internal error", e);
    }
  }
}
