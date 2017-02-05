package ee.tuleva.onboarding.comparisons;

import ee.tuleva.onboarding.comparisons.exceptions.ComparisonException;
import ee.tuleva.onboarding.comparisons.exceptions.IsinNotFoundException;
import ee.tuleva.onboarding.comparisons.exceptions.SourceHTMLChangedException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class PensionikeskusCodeToIsin {

    private static final Map<Integer, String> map;

    private static final String secondPillarFundsURLString = "http://www.pensionikeskus.ee/ii-sammas/fondid/kohustuslikud-pensionifondid/";

    static {
        map = new HashMap<>();
        map.put(44, "EE3600019790"); //Pension Fund LHV 25
        map.put(35, "EE3600019808"); //Pension Fund LHV 50
        map.put(73, "EE3600109401"); //Pension Fund LHV Index
        map.put(45, "EE3600019816"); //Pension Fund LHV Interest
        map.put(47, "EE3600019832"); //Pension Fund LHV L
        map.put(39, "EE3600019774"); //Pension Fund LHV M
        map.put(46, "EE3600019824"); //Pension Fund LHV S
        map.put(38, "EE3600019766"); //Pension Fund LHV XL
        map.put(59, "EE3600019782"); //Pension Fund LHV XS
        map.put(48, "EE3600098430"); //Nordea Pension Fund A
        map.put(57, "EE3600103503"); //Nordea Pension Fund A Plus
        map.put(49, "EE3600098448"); //Nordea Pension Fund B
        map.put(50, "EE3600098455"); //Nordea Pension Fund C
        map.put(56, "EE3600103297"); //SEB Energetic Pension Fund
        map.put(75, "EE3600109427"); //SEB Energetic Pension Fund Index
        map.put(60, "EE3600019717"); //SEB Conservative Pension Fund
        map.put(51, "EE3600098612"); //SEB Optimal Pension Fund
        map.put(61, "EE3600019725"); //SEB Progressive Pension Fund
        map.put(58, "EE3600019733"); //Swedbank Pension Fund K1 (Conservative Strategy)
        map.put(36, "EE3600019741"); //Swedbank Pension Fund K2 (Balanced Strategy)
        map.put(37, "EE3600019758"); //Swedbank Pension Fund K3 (Growth Strategy)
        map.put(52, "EE3600103248"); //Swedbank Pension Fund K4 (Equity Strategy)
        map.put(74, "EE3600109393"); //Swedbank Pension Fund K90-99 (Life-cycle Strategy)

    }

    static String getIsin(int code) throws ComparisonException, IOException{

        if (!map.containsKey(code)) {

            Document doc = Jsoup.connect(secondPillarFundsURLString+code).get();

            String foundisin = findNewIsinFromHTML(doc);

            map.put(code,foundisin);

        }
        return map.get(code);
    }

    private static String findNewIsinFromHTML(Document doc) throws IOException, ComparisonException {

        Elements fundData = doc.getElementsByClass("fund-block");
        if (fundData == null || fundData.isEmpty()) throw new SourceHTMLChangedException();

        Element isinData = fundData.first().select("p").first();
        if (isinData == null) throw new SourceHTMLChangedException();

        String[] isinDataAttributes = isinData.text().split(" ");
        if (isinDataAttributes.length != 2) throw new SourceHTMLChangedException();

        String isin = isinDataAttributes[1];

        if (!isinCodeValid(isin)) throw new IsinNotFoundException("Invalid ISIN format scraped from source page");

        return isin;

    }

    private static boolean isinCodeValid(String foundisin) {
        return foundisin.length() == 12 && foundisin.startsWith("EE");
    }

}
