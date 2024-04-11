package no.fintlabs.linkwalker.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import no.fintlabs.FintCustomerObjectEvent;

@Getter
@AllArgsConstructor
@SuperBuilder
public class ClientEvent extends FintCustomerObjectEvent<Client> {


    public ClientEvent(Client object, String orgId, FintCustomerObjectEvent.Operation operation) {
        super(object, orgId, operation, null);
    }
}