package dan.rojas.epam.html;

import dan.rojas.epam.S3Constants;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.Map;
import java.util.Optional;

public class ProductHtmlPage {

  private static final String MAIN_CLASS = "container-md";
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
    System.out.println("Creating HTML Element for " + getId(item));
    final String htmlToAppend =
        String.format(htmlTemplate, getId(item), item.get("pictureUrl"),
            item.get("id"), item.get("name"), item.get("price"));

    final Document productsDocument = getPageFromS3(s3Client);
    productsDocument.getElementsByClass(MAIN_CLASS)
        .get(0).child(0).append(htmlToAppend);

    return productsDocument;
  }

  public static Document updateExistingItem(final S3Client s3Client, final Map<String, String> item) {
    System.out.println("Updating HTML Element for " + getId(item));
    final Document productsDocument = getPageFromS3(s3Client);
    final Element productToUpdate = productsDocument.getElementById(getId(item));
    Optional.ofNullable(productToUpdate)
        .ifPresent(productElement -> {
          updateElementAttribute(productElement, "img", "src", item.get("pictureUrl"));
          Optional.of(productElement.getElementsByTag("div"))
              .map(Elements::first)
              .ifPresent(divCardElement -> {
                updateElementText(divCardElement, "span", "$ "+item.get("price"));
              });
        });
    return productsDocument;
  }

  private static Document getPageFromS3(final S3Client s3Client) {
    System.out.println("Getting file from S3...");
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
    System.out.println("Updating HTML Element " + tag + ":" + attrKey + " with " + attrValue);
    Optional.of(root.getElementsByTag(tag))
        .map(Elements::first)
        .ifPresent(imageElement -> imageElement.attr(attrKey, attrValue));
  }

  private static void updateElementText(final Element root, final String tag, final String text) {
    System.out.println("Updating HTML Element " + tag + " with "+ text);
    Optional.of(root.getElementsByTag(tag))
        .map(Elements::first)
        .ifPresent(pElement -> pElement.text(text));
  }

}
