package ee.tuleva.onboarding.mandate;

import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.country.Country;
import org.springframework.boot.jackson.JacksonMixin;

@JacksonMixin(Country.class)
@JsonView(MandateView.Default.class)
public abstract class CountryViewMixin {}
