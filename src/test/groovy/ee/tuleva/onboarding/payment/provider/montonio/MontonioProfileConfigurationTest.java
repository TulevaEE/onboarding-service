package ee.tuleva.onboarding.payment.provider.montonio;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.COOP;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LHV;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LUMINOR;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.SEB;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.SWEDBANK;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.TULUNDUSUHISTU;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME;

import ee.tuleva.onboarding.payment.PaymentData.PaymentChannel;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.DefaultResourceLoader;

class MontonioProfileConfigurationTest {

  private static final String SANDBOX_URL = "https://sandbox-stargate.montonio.com/api";

  private static final Map<PaymentChannel, Channel> SANDBOX_CHANNELS =
      Map.of(
          LUMINOR, new Channel("0be260f2-1966-4d18-bb9d-1f0909a663f7", "RIKOEE22"),
          SEB, new Channel("25f6018a-d93e-4f2a-b235-58ab79c47235", "EEUHEE2X"),
          SWEDBANK, new Channel("e343cf15-09ea-466d-91bd-b805bbed034a", "HABAEE2X"),
          LHV, new Channel("070e9cae-9f0c-423e-9345-70d624a05578", "LHVBEE22"),
          COOP, new Channel("5aa7cf67-4a11-472f-a42e-aa7a5a389100", "EKRDEE22"),
          TULUNDUSUHISTU, new Channel("912e0403-62ad-4b0c-a91c-129f64f7a3ea", null));

  private static final String SANDBOX_SAVINGS_ACCESS_KEY = "a7dca9bf-1b5a-4252-a4d9-39e9836f0f4e";

  @ParameterizedTest
  @MethodSource("localProfiles")
  void localRunsCreatePaymentsInTheSandboxAndSendNotificationsToAFakeUrl(List<String> profiles) {
    assertThat(montonioIn(environmentFor(profiles)))
        .isEqualTo(
            new MontonioSettings(
                SANDBOX_URL,
                true,
                SANDBOX_CHANNELS,
                SANDBOX_SAVINGS_ACCESS_KEY,
                "https://tuleva.ee/fake-notification-url"));
  }

  @Test
  void stagingCreatesPaymentsInTheSandboxAndReceivesNotificationsAtItsOwnApi() {
    assertThat(
            montonioIn(
                environmentFor(
                    List.of("staging"), Map.of("API_URL", "https://staging.tuleva.ee/v1"))))
        .isEqualTo(
            new MontonioSettings(
                SANDBOX_URL,
                false,
                SANDBOX_CHANNELS,
                SANDBOX_SAVINGS_ACCESS_KEY,
                "https://staging.tuleva.ee/v1/payments/savings/notifications"));
  }

  @Test
  void productionCreatesLivePaymentsWithTheProductionAccessKeysAndRealNotifications() {
    assertThat(montonioIn(environmentFor(List.of("production"))))
        .isEqualTo(
            new MontonioSettings(
                "https://stargate.montonio.com/api",
                false,
                Map.of(
                    LUMINOR, new Channel("fb673dc3-d898-4592-844f-83491aa430cc", "RIKOEE22"),
                    SEB, new Channel("bec7e2ff-df2b-4342-829e-d131706a7858", "EEUHEE2X"),
                    SWEDBANK, new Channel("0741a0c4-39c2-4d28-a692-0ab66d4c9c7c", "HABAEE2X"),
                    LHV, new Channel("955bc96c-aa71-4ff3-bea9-c8a429168c1d", "LHVBEE22"),
                    COOP, new Channel("f5cd942d-5b23-4467-80ce-95cd67186c6f", "EKRDEE22"),
                    TULUNDUSUHISTU, new Channel("02f596e6-d6b6-43a8-83ac-53d33cc1f13d", null)),
                "38ca14da-8b07-43af-9f43-e052b5d2a8c1",
                "https://onboarding-service.tuleva.ee/v1/payments/savings/notifications"));
  }

  private static Stream<List<String>> localProfiles() {
    return Stream.of(List.of(), List.of("dev"));
  }

  private record Channel(String accessKey, @Nullable String bic) {}

  private record MontonioSettings(
      String url,
      boolean fakeNotificationUrl,
      Map<PaymentChannel, Channel> channels,
      String savingsAccessKey,
      String savingsNotificationUrl) {}

  private static MontonioSettings montonioIn(ConfigurableEnvironment environment) {
    var binder = Binder.get(environment);
    return new MontonioSettings(
        binder.bind("payment-provider.url", String.class).get(),
        binder.bind("payment-provider.use-fake-notification-url", Boolean.class).get(),
        binder
            .bind(
                "payment-provider.payment-channels",
                Bindable.mapOf(PaymentChannel.class, MontonioPaymentChannel.class))
            .get()
            .entrySet()
            .stream()
            .collect(
                toMap(
                    Entry::getKey,
                    entry ->
                        new Channel(entry.getValue().getAccessKey(), entry.getValue().getBic()))),
        binder.bind("payment-provider.savings-channel.access-key", String.class).get(),
        binder.bind("payment-provider.savings-channel.notification-url", String.class).get());
  }

  private static ConfigurableEnvironment environmentFor(List<String> profiles) {
    return environmentFor(profiles, Map.of());
  }

  private static ConfigurableEnvironment environmentFor(
      List<String> profiles, Map<String, Object> environmentVariables) {
    var environment = new StandardEnvironment();
    var propertySources = environment.getPropertySources();
    propertySources.remove(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    propertySources.replace(
        SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
        new SystemEnvironmentPropertySource(
            SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environmentVariables));
    propertySources.addFirst(
        new MapPropertySource(
            "onlyTheDeployedConfiguration",
            Map.of("spring.config.location", "classpath:/application.yml")));
    ConfigDataEnvironmentPostProcessor.applyTo(
        environment, new DefaultResourceLoader(), null, profiles);
    return environment;
  }
}
