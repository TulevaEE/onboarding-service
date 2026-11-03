package ee.tuleva.onboarding.oauth.server;

import ee.tuleva.onboarding.oauth.server.Grants.RevocationReason;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ConnectedApps {

  private final Grants grants;
  private final RegisteredClientRepository clients;

  List<ConnectedAppResponse> of(String personalCode) {
    return grants.liveOf(personalCode).stream()
        .flatMap(
            grant ->
                Optional.ofNullable(clients.findByClientId(grant.clientId()))
                    .map(client -> ConnectedAppResponse.of(grant, client))
                    .stream())
        .toList();
  }

  boolean revoke(String personalCode, UUID grantId) {
    var grant = grants.byId(grantId).filter(row -> row.personalCode().equals(personalCode));
    grant.ifPresent(row -> grants.revoke(row.id(), RevocationReason.PERSON));
    return grant.isPresent();
  }
}
