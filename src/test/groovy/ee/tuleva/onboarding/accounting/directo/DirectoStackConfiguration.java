package ee.tuleva.onboarding.accounting.directo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import({DirectoConfiguration.class, DirectoPartMapper.class})
public class DirectoStackConfiguration {}
