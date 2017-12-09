package no.fint;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.stream.Collectors;

/**
 * An indirection to the actual HttpClient, for testability and nothing else.
 */
@Component
public class HttpClient {

    public static final Response ABJECT_FAILURE = new Response(-1, null);

    public Response get(URL url) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(url.toString());
            Response response = new Response();
            client.execute(get, httpResponse -> {
                response.setResponseCode(httpResponse.getStatusLine().getStatusCode());

                String content = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent())).lines().collect(Collectors.joining());
                response.setEntity(content);
                return "";
            });
            return response;
        } catch (IOException e) {
            return ABJECT_FAILURE;
        }
    }

    public static class Response {
        private int responseCode;

        private String entity;

        public Response(int responseCode, String contentStream) {
            this.responseCode = responseCode;
            this.entity = contentStream;
        }

        public Response() {

        }

        public int getResponseCode() {
            return responseCode;
        }

        public void setResponseCode(int responseCode) {
            this.responseCode = responseCode;
        }

        public String getEntity() {
            return entity;
        }

        public void setEntity(String entity) {
            this.entity = entity;
        }
    }

}
