package ee.tuleva.onboarding.investment.report.publishing.github;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GitHubProperties.class)
class GitHubConfiguration {

  @Bean
  @ConditionalOnProperty(name = "investment-report-publishing.enabled", havingValue = "true")
  GitHubPrClient gitHubPrClient(GitHubProperties properties, Clock clock) {
    var restClient =
        RestClient.builder()
            .baseUrl("https://api.github.com/repos/" + properties.repo())
            .requestInterceptor(
                (request, body, execution) -> {
                  request.getHeaders().setBearerAuth(properties.token());
                  request.getHeaders().set("Accept", "application/vnd.github+json");
                  request.getHeaders().set("X-GitHub-Api-Version", "2022-11-28");
                  return execution.execute(request, body);
                })
            .build();
    return new GitHubPrClient(restClient, properties.defaultBranch(), clock);
  }
}
