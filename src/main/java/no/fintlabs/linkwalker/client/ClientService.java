package no.fintlabs.linkwalker.client;

import lombok.RequiredArgsConstructor;
import no.fintlabs.FintCustomerObjectEvent;
import no.fintlabs.linkwalker.SecretService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final SecretService secretService;
    private final ClientEventRequestProducerService clientRequestService;

    public Optional<ClientEvent> get(String clientName, String organization) {
        return clientRequestService.get(
                ClientEvent.builder()
                        .operation(FintCustomerObjectEvent.Operation.READ)
                        .object(Client.builder()
                                .dn(createDn(clientName, organization))
                                .publicKey(secretService.getPublicKeyString())
                                .build())
                        .build()
        );
    }

    private String createDn(String clientName, String organization) {
        return String.format("cn=%s,ou=clients,ou=%s,ou=organisations,o=fint", clientName, organization.replace(".", "_"));
    }

}
