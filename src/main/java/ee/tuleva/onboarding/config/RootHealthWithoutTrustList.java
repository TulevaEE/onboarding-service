package ee.tuleva.onboarding.config;

import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.web.AdditionalPathsMapper;
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace;
import org.springframework.boot.health.actuate.endpoint.AdditionalHealthEndpointPath;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroupsPostProcessor;
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.stereotype.Component;

@Component
class RootHealthWithoutTrustList implements HealthEndpointGroupsPostProcessor {

  @Override
  public HealthEndpointGroups postProcessHealthEndpointGroups(HealthEndpointGroups groups) {
    return new GroupsWithRootWithoutTrustList(new RootWithoutTrustList(groups));
  }

  private record GroupsWithRootWithoutTrustList(RootWithoutTrustList root)
      implements HealthEndpointGroups, AdditionalPathsMapper {

    @Override
    public HealthEndpointGroup getPrimary() {
      return root;
    }

    @Override
    public Set<String> getNames() {
      return root.groups().getNames();
    }

    @Override
    public @Nullable HealthEndpointGroup get(String name) {
      return root.groups().get(name);
    }

    @Override
    public @Nullable List<String> getAdditionalPaths(
        EndpointId endpointId, WebServerNamespace webServerNamespace) {
      return root.groups() instanceof AdditionalPathsMapper mapper
          ? mapper.getAdditionalPaths(endpointId, webServerNamespace)
          : null;
    }
  }

  private record RootWithoutTrustList(HealthEndpointGroups groups) implements HealthEndpointGroup {

    @Override
    public boolean isMember(String name) {
      return !TrustListHealthIndicator.NAME.equals(name) && root().isMember(name);
    }

    @Override
    public boolean showComponents(SecurityContext securityContext) {
      return root().showComponents(securityContext);
    }

    @Override
    public boolean showDetails(SecurityContext securityContext) {
      return root().showDetails(securityContext);
    }

    @Override
    public StatusAggregator getStatusAggregator() {
      return root().getStatusAggregator();
    }

    @Override
    public HttpCodeStatusMapper getHttpCodeStatusMapper() {
      return root().getHttpCodeStatusMapper();
    }

    @Override
    public @Nullable AdditionalHealthEndpointPath getAdditionalPath() {
      return root().getAdditionalPath();
    }

    private HealthEndpointGroup root() {
      return groups.getPrimary();
    }
  }
}
