package banghak.home.halley.application.service;

import banghak.home.halley.domain.budget.ProductPreview;
import banghak.home.halley.domain.budget.ProductPreviewParser;
import banghak.home.halley.domain.budget.ProductUrlPolicy;
import org.springframework.stereotype.Service;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class ProductPreviewService {
    private static final int MAX_RESPONSE_BYTES = 1_000_000;
    private final ProductUrlPolicy urlPolicy = new ProductUrlPolicy();
    private final ProductPreviewParser parser = new ProductPreviewParser();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public ProductPreview preview(String rawUrl) {
        final URI uri = urlPolicy.requireAllowed(rawUrl);
        requirePublicAddress(uri.getHost());
        try {
            final HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5)).header("User-Agent", "Halley-ProductPreview/1.0")
                    .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body().length > MAX_RESPONSE_BYTES) {
                return ProductPreview.empty();
            }
            return parser.parse(new String(response.body(), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return ProductPreview.empty();
        }
    }

    private void requirePublicAddress(String host) {
        try {
            for (final InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("product url resolves to a private address");
                }
            }
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("product url host is unavailable");
        }
    }
}
