package ee.tuleva.onboarding.mandate.content;

import ee.tuleva.onboarding.mandate.MandateApplicationType;
import ee.tuleva.onboarding.mandate.MandateFileService;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/*
Temporary class to get Mandate XML message
*/


@RequiredArgsConstructor
@Slf4j
@Service
public class MandateXmlService {

    private final MandateFileService mandateFileService;

    public List<MandateXmlMessage> getRequestContents(User user, Long mandateId) {

        log.info("Generating XML for user id {} and mandate id {}", user.getId(), mandateId);

        return mandateFileService.getMandateFiles(mandateId, user).stream().map( signatureFile -> {

            String id = UUID.randomUUID().toString().replace("-", "");

            String xmlContent = Jsoup.parse(
                    new String(signatureFile.content, StandardCharsets.UTF_8)
            ).head().getElementById("avaldus").html();

            return MandateXmlMessage.builder().
                    message(
                            episEnvelopePrefix(id) +
                                    xmlContent +
                                    episEnvelopeSuffix
                    )
                    .processId(id)
                    .type(getType(xmlContent))
                    .build();

        }).collect(Collectors.toList());
    }

    private MandateApplicationType getType(String xmlContent) {
        MandateApplicationType type = null;

        if(xmlContent.contains("<OSAKUTE_VAHETAMISE_AVALDUS>")) {
            type = MandateApplicationType.TRANSFER;
        } else if(xmlContent.contains("VALIKUAVALDUS")) {
            type = MandateApplicationType.SELECTION;
        } else {
            throw new RuntimeException("Unknown mandate xml message type");
        }

        return type;
    }

    private String senderBic = "TULEVA20";
    private String recipientBic = "ECSDEE20";

    private String episEnvelopePrefix(String id)  {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Ex xmlns=\"urn:mhub:xsd:Envelope:01\" xmlns:ns2=\"urn:iso:std:iso:20022:tech:xsd:head.001.001.01\">\n" +
                "                <BizMsg>\n" +
                "                                <ns2:AppHdr>\n" +
                "                                                <ns2:Fr>\n" +
                "                                                                <ns2:FIId>\n" +
                "                                                                                <ns2:FinInstnId>\n" +
                "                                                                                                <ns2:BICFI>"+ senderBic +"</ns2:BICFI>\n" +
                "                                                                                </ns2:FinInstnId>\n" +
                "                                                                </ns2:FIId>\n" +
                "                                                </ns2:Fr>\n" +
                "                                                <ns2:To>\n" +
                "                                                                <ns2:FIId>\n" +
                "                                                                                <ns2:FinInstnId>\n" +
                "                                                                                                <ns2:BICFI>"+recipientBic+"</ns2:BICFI>\n" +
                "                                                                                </ns2:FinInstnId>\n" +
                "                                                                </ns2:FIId>\n" +
                "                                                </ns2:To>\n" +
                "                                                <ns2:BizMsgIdr>"+id+"</ns2:BizMsgIdr>\n" +
                "                                                <ns2:MsgDefIdr>epis</ns2:MsgDefIdr>\n" +
                "                                                <ns2:CreDt>2017-03-13T10:00:39.731Z</ns2:CreDt>\n" +
                "                                </ns2:AppHdr>";
    }


    private String episEnvelopeSuffix = "                </BizMsg>\n" +
            "</Ex>";

}
