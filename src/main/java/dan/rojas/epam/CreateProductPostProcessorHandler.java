package dan.rojas.epam;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class CreateProductPostProcessorHandler implements RequestHandler<DynamodbEvent, Void> {

  private static final String BUKET_NAME = "danrojas.products.com";
  private static final String PRODUCTS_HTML_KEY = "products.html";
  private static final String MAIN_CLASS = "container-md";
  private static final String htmlTemplate = "<div class=\"col\"><div class=\"card\" style=\"width: 18rem;\">\n" +
      "    <img src=\"%s\" class=\"card-img-top\">\n" +
      "    <div class=\"card-body\">\n" +
      "        <h5 class=\"card-title\">%s</h5>\n" +
      "        <p class=\"card-text\">%s</p>\n" +
      "            <span class=\"badge bg-success\">$ %s</span>\n" +
      "    </div>\n" +
      "</div></div>";

  private final S3Client s3Client;

  public CreateProductPostProcessorHandler() {
    s3Client = DependencyFactory.s3Client();
  }

  @Override
  public Void handleRequest(DynamodbEvent dynamodbEvent, Context context) {
    System.out.println(dynamodbEvent);
    final String htmlToAppend = Optional.ofNullable(dynamodbEvent)
        .map(DynamodbEvent::getRecords)
        .orElse(new ArrayList<>())
        .stream()
        .map(this::processStreamRecord)
        .filter(Objects::nonNull)
        .map(this::createPage)
        .reduce("", String::concat);

    updateWebpage(htmlToAppend);
    return null;
  }

  private Map<String, String> processStreamRecord(final DynamodbEvent.DynamodbStreamRecord dynamodbStreamRecord) {
    System.out.println("DynamodbStreamRecord -> " + dynamodbStreamRecord);
    final Map<String, AttributeValue> newImage = dynamodbStreamRecord.getDynamodb().getNewImage();
    if (Objects.nonNull(newImage) && dynamodbStreamRecord.getEventName().equalsIgnoreCase("INSERT")) {
      final Map<String, String> itemMap = new HashMap<>();
      newImage.forEach((key, attribute) -> {
        itemMap.put(key, attribute.getS());
      });
      return itemMap;
    }
    return null;
  }

  private String createPage(final Map<String, String> stringStringMap) {
    System.out.println("Creating HTML Element for " + stringStringMap.get("id"));
    return String.format(htmlTemplate,
        stringStringMap.get("pictureUrl"),
        stringStringMap.get("id"),
        stringStringMap.get("name"),
        stringStringMap.get("price"));
  }

  private void updateWebpage(final String htmlToAppend) {
    if (Objects.nonNull(htmlToAppend) && !htmlToAppend.equals("")) {
      System.out.println("Uploading changes to S3");
      final ResponseBytes<GetObjectResponse> response = s3Client.getObject(
          GetObjectRequest.builder().bucket(BUKET_NAME).key(PRODUCTS_HTML_KEY).build(),
          ResponseTransformer.toBytes());

      final String productsHtml = response.asUtf8String();
      final Document productsDocument = Jsoup.parse(productsHtml);
      productsDocument.getElementsByClass(MAIN_CLASS)
          .get(0).child(0).append(htmlToAppend);

      s3Client.putObject(PutObjectRequest.builder().bucket(BUKET_NAME)
          .key(PRODUCTS_HTML_KEY).build(), RequestBody.fromString(productsDocument.toString(),
          StandardCharsets.UTF_8));
    }
  }
}
