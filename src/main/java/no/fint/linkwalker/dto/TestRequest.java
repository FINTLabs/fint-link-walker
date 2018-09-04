package no.fint.linkwalker.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.util.StringUtils;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class TestRequest {
    static final String DEFAULT_BASE_URL = "https://play-with-fint.felleskomponent.no";

    private String baseUrl;
    private String endpoint;
    private String orgId;
    private String client;

    @JsonIgnore
    public String getTarget() {
        if (StringUtils.isEmpty(baseUrl) || !UrlUtils.isAbsoluteUrl(baseUrl)) {
            return String.format("%s%s", DEFAULT_BASE_URL, endpoint);
        } else {
            return String.format("%s%s", baseUrl, endpoint);
        }
    }
}
