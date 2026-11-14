package fr.vlegall.seal.pki.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.pki.application.port.in.AuthorityQuery;
import fr.vlegall.seal.pki.domain.model.AuthorityKey;
import fr.vlegall.seal.pki.domain.port.out.AuthoritySigner;

@ApplicationService
public class AuthorityHandler implements AuthorityQuery {

    private final AuthoritySigner signer;

    public AuthorityHandler(AuthoritySigner signer) {
        this.signer = signer;
    }

    @Override
    public AuthorityKey authority() {
        return signer.key();
    }
}
