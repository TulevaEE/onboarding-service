package ee.tuleva.onboarding.contribution;

import static com.fasterxml.jackson.annotation.JsonSubTypes.*;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.*;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import java.time.Instant;

@JsonTypeInfo(use = Id.NAME, include = As.EXISTING_PROPERTY, property = "pillar", visible = true)
@JsonSubTypes({
  @Type(value = SecondPillarContribution.class, name = "2"),
  @Type(value = ThirdPillarContribution.class, name = "3")
})
public interface Contribution {
  Instant time();

  String sender();

  BigDecimal amount();

  Currency currency();

  Integer pillar();
}
