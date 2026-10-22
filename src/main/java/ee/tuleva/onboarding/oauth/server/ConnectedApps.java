package ee.tuleva.onboarding.oauth.server;

import ee.tuleva.onboarding.oauth.server.Grants.RevocationReason;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ConnectedApps {

  private final Grants grants;
  private final ConfiguredClients clients;

  List<ConnectedAppResponse> of(String personalCode) {
    return grants.liveOf(personalCode).stream()
        .flatMap(
            grant ->
                clients
                    .find(grant.clientId())
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
