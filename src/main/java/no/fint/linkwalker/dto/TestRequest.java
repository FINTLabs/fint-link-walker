package no.fint.linkwalker.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TestRequest {
    private static final String DEFAULT_BASE_URL = "https://play-with-fint.felleskomponent.no";

    private String baseUrl;
    private String endpoint;

    public String getTarget() {
        if(StringUtils.isEmpty(baseUrl)) {
            return String.format("%s%s", DEFAULT_BASE_URL, endpoint);
        } else {
            return String.format("%s%s", baseUrl, endpoint);
        }
    }
}
