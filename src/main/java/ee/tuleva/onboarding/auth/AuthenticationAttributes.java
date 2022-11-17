package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.idcard.IdDocumentType;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;

public class AuthenticationAttributes {

  public static final String AUTHENTICATION_ATTRIBUTES_KEY =
      AuthenticationAttributes.class.getName();

  public AuthenticationAttributes() {
    this(new HashMap<>());
  }

  public AuthenticationAttributes(Map<String, Object> attributes) {
    this.attributes = new HashMap<>(attributes);
  }

  public static final String DOCUMENT_TYPE = AuthenticationAttributes.class.getName() + ".DOCUMENT";

  private final Map<String, Object> attributes;

  Map<String, Object> toMap() {
    return Collections.unmodifiableMap(attributes);
  }

  public void setDocumentType(IdDocumentType documentType) {
    attributes.put(DOCUMENT_TYPE, documentType);
  }

  public Optional<IdDocumentType> getDocumentType() {
    return Optional.ofNullable((IdDocumentType) attributes.get(DOCUMENT_TYPE));
  }

  public void setIssueTime(Instant issueTime) {
    attributes.put(OAuth2TokenIntrospectionClaimNames.IAT, issueTime);
  }

  public void setExpirationTime(Instant expirationTime) {
    attributes.put(OAuth2TokenIntrospectionClaimNames.EXP, expirationTime);
  }
}
