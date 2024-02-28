package no.fint.linkwalker.kafka;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.util.StringUtils;

import java.lang.reflect.ParameterizedType;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class FintCustomerObjectEvent<T> {
    private T object;
    private String orgId;
    private Operation operation;
    private String errorMessage;


    public boolean hasError() {
        return StringUtils.hasText(errorMessage);
    }

    @JsonIgnore
    public String getOrganisationObjectName() {
        return orgId.replaceAll("\\.", "_");
    }

    @JsonIgnore
    public String getOperationWithType() {
        return String.format("%s-%s", operation.name(), getParameterClass().getSimpleName().toUpperCase());
    }

    @SuppressWarnings("unchecked")
    private Class<T> getParameterClass() {
        return (Class<T>) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0];
    }

    public enum Operation {
        CREATE,
        READ,
        UPDATE,
        DELETE
    }
}
