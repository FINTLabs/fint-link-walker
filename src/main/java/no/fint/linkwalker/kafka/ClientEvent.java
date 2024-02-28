package no.fint.linkwalker.kafka;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@AllArgsConstructor
@SuperBuilder
public class ClientEvent extends FintCustomerObjectEvent<Client> {


    public ClientEvent(Client object, String orgId, FintCustomerObjectEvent.Operation operation) {
        super(object, orgId, operation, null);
    }
}
