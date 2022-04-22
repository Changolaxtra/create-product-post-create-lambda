package dan.rojas.epam.html;

import dan.rojas.epam.S3Constants;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.Map;
import java.util.Optional;

public class ProductHtmlPage {

  private static final Logger logger = LoggerFactory.getLogger(ProductHtmlPage.class);

  private static final String MAIN_CLASS = "container-md";
  private static final String IMG = "img";
  private static final String SRC = "src";
  private static final String PICTURE_URL = "pictureUrl";
  private static final String DIV = "div";
  private static final String SPAN = "span";
  private static final String PRICE = "price";
  private static final String ID = "id";
  private static final String NAME = "name";

  private static final String htmlTemplate = "<div class=\"col\">" +
      "<div id=\"%s\" class=\"card\" style=\"width: 18rem;\">\n" +
      "    <img src=\"%s\" class=\"card-img-top\">\n" +
      "    <div class=\"card-body\">\n" +
      "        <h5 class=\"card-title\">%s</h5>\n" +
      "        <p class=\"card-text\">%s</p>\n" +
      "        <span class=\"badge bg-success\">$ %s</span>\n" +
      "    </div>\n" +
      "</div>" +
      "</div>";

  public static Document appendNewItem(final S3Client s3Client, final Map<String, String> item) {
    logger.info("Creating HTML Element for " + getId(item));
    final String htmlToAppend =
        String.format(htmlTemplate, getId(item), item.get(PICTURE_URL),
            item.get(ID), item.get(NAME), item.get(PRICE));

    final Document productsDocument = getPageFromS3(s3Client);
    productsDocument.getElementsByClass(MAIN_CLASS)
        .get(0).child(0).append(htmlToAppend);

    return productsDocument;
  }

  public static Document updateExistingItem(final S3Client s3Client, final Map<String, String> item) {
    logger.info("Updating HTML Element for " + getId(item));
    final Document productsDocument = getPageFromS3(s3Client);
    final Element productToUpdate = productsDocument.getElementById(getId(item));
    Optional.ofNullable(productToUpdate)
        .ifPresent(productElement -> {
          updateElementAttribute(productElement, IMG, SRC, item.get(PICTURE_URL));
          Optional.of(productElement.getElementsByTag(DIV))
              .map(Elements::first)
              .ifPresent(divCardElement -> {
                updateElementText(divCardElement, SPAN, "$ "+item.get(PRICE));
              });
        });
    return productsDocument;
  }

  private static Document getPageFromS3(final S3Client s3Client) {
    logger.info("Getting file from S3...");
    final ResponseBytes<GetObjectResponse> response = s3Client.getObject(
        GetObjectRequest.builder().bucket(S3Constants.BUKET_NAME).key(S3Constants.PRODUCTS_HTML_KEY).build(),
        ResponseTransformer.toBytes());

    final String productsHtml = response.asUtf8String();
    return Jsoup.parse(productsHtml);
  }


  private static String getId(final Map<String, String> item) {
    return item.get("id") + "-" + item.get("name");
  }

  private static void updateElementAttribute(final Element root,
                                             final String tag,
                                             final String attrKey,
                                             final String attrValue) {
    logger.info("Updating HTML Element " + tag + ":" + attrKey + " with " + attrValue);
    Optional.of(root.getElementsByTag(tag))
        .map(Elements::first)
        .ifPresent(imageElement -> imageElement.attr(attrKey, attrValue));
  }

  private static void updateElementText(final Element root, final String tag, final String text) {
    logger.info("Updating HTML Element " + tag + " with "+ text);
    Optional.of(root.getElementsByTag(tag))
        .map(Elements::first)
        .ifPresent(pElement -> pElement.text(text));
  }

}
