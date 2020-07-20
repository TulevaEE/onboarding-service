package ee.tuleva.onboarding.conversion

class ConversionResponseFixture {

    static ConversionResponse notFullyConverted() {
        return ConversionResponse.builder()
            .secondPillar(ConversionResponse.Conversion.builder()
                .transfersComplete(true)
                .selectionComplete(false)
                .build()
            ).thirdPillar(ConversionResponse.Conversion.builder()
            .transfersComplete(true)
            .selectionComplete(false)
            .build()
        ).build()
    }

    static ConversionResponse fullyConverted() {
        return ConversionResponse.builder()
            .secondPillar(ConversionResponse.Conversion.builder()
                .transfersComplete(true)
                .selectionComplete(true)
                .build()
            ).thirdPillar(ConversionResponse.Conversion.builder()
            .transfersComplete(true)
            .selectionComplete(true)
            .build()
        ).build()
    }

}
