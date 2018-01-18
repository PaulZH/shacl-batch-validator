package zone.cogni.shacl_validator;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

public class UrlHeadersResource extends UrlResource {

  private HttpHeaders headers = new HttpHeaders();

  public UrlHeadersResource(URI uri) throws MalformedURLException {
    super(uri);
  }

  public UrlHeadersResource(URL url) {
    super(url);
  }

  public UrlHeadersResource(String path) throws MalformedURLException {
    super(path);
  }

  public UrlHeadersResource(String protocol, String location) throws MalformedURLException {
    super(protocol, location);
  }

  public UrlHeadersResource(String protocol, String location, String fragment) throws MalformedURLException {
    super(protocol, location, fragment);
  }

  public UrlHeadersResource setHeader(String headerName, String headerValue) {
    headers.set(headerName, headerValue);
    return this;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    try {
      RestTemplate restTemplate = new RestTemplate();
      HttpEntity<?> httpEntity = new HttpEntity<>(headers);

      ResponseEntity<String> exchange = restTemplate.exchange(getURI(), HttpMethod.GET, httpEntity, String.class);
      String body = exchange.getBody();

      return new ByteArrayResource(body.getBytes("UTF-8")).getInputStream();
    }
    catch (RestClientException e) {
      throw new IOException(e);
    }
  }

}
