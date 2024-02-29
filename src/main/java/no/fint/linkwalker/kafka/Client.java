package no.fint.linkwalker.kafka;

import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@ToString(exclude = {"password", "clientSecret"})
@Builder
@Jacksonized
@EqualsAndHashCode
public final class Client implements Serializable {


    @Getter
    private String dn;

    @Getter
    private String name;

    @Setter
    @Getter
    private boolean isManaged;

    @Getter
    private String shortDescription;

    @Getter
    private String assetId;

    @Getter
    private String asset;

    @Getter
    @Setter
    private String note;

    @Getter
    private String password;

    @Getter
    private String clientSecret;

    @Getter
    @Setter
    private String publicKey;

    @Getter
    private String clientId;

    @Getter
    @Builder.Default
    private List<String> components = new ArrayList<>();

    @Getter
    @Builder.Default
    private List<String> accessPackages = new ArrayList<>();


    public void addComponent(String componentDn) {
        if (components.stream().noneMatch(componentDn::equalsIgnoreCase)) {
            components.add(componentDn);
        }
    }

    public void removeComponent(String componentDn) {
        components.removeIf(component -> component.equalsIgnoreCase(componentDn));
    }

    public void setAccessPackage(String accessPackageDn) {
        accessPackages.clear();
        accessPackages.add(accessPackageDn);
    }

//    public String getDn() {
//        if (dn != null) {
//            return dn.toString();
//        } else {
//            return null;
//        }
//    }
}
