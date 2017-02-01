package ee.tuleva.comparisons;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

@Component
public class EstonianFeeFinderService {

    private static final String feeURLString = "https://www.pensionikeskus.ee/ii-sammas/fondid/fonditasude-vordlused/";

    @Autowired
    @Resource
    ComparisonDAO comparisonDAO;

    //TODO: schedule after every 3 months
    //@Scheduled(cron = "0 0 0 1 */3 ?")
    @Scheduled(fixedDelay = 3000)
    public void updateFeesFromPensionSystem() throws IOException, ParseException {

        Document doc = Jsoup.connect(feeURLString).get();

        Element table = doc.select("table").get(0);
        Elements allRows = table.select("tr");
        Elements rows = new Elements();

        for (Element e : allRows) {
            Attributes rowAttributes = e.attributes();
            for (Attribute attribute : rowAttributes) {
                if (attribute.getValue().equals("data")) {
                    rows.add(e);
                }
            }
        }

        for (Element e : rows) {

            String hrefToPensionFundDetails = e.select("a").first().attr("href");
            String[] partsOfPath = hrefToPensionFundDetails.split("/");
            int fundCodeInPensionSystem = Integer.parseInt(partsOfPath[partsOfPath.length - 1]);
            String isin = comparisonDAO.getIsin(fundCodeInPensionSystem);

            Elements cols = e.select("td");
            cols.remove(0);
            String tableColValues = cols.select("td").text();
            String[] allFeeValues = tableColValues.split("[^\\d]?%[^\\d]?");

            double sumFee = 0;
            for (String fee:allFeeValues){
                double f;
                if (fee.contains(",")){
                    NumberFormat format = NumberFormat.getInstance(Locale.FRANCE);
                    Number number = format.parse(fee);
                    f = number.doubleValue();
                }
                else {
                    f = Double.parseDouble(fee);
                }
                sumFee += f;
            }

            comparisonDAO.addFee(isin, sumFee);

        }

    }

}
