package io.pivotal.security.service;

import com.greghaskins.spectrum.Spectrum;
import io.pivotal.security.data.CredentialVersionDataService;
import io.pivotal.security.domain.CertificateCredential;
import io.pivotal.security.domain.PasswordCredential;
import io.pivotal.security.domain.SshCredential;
import org.junit.runner.RunWith;
import org.springframework.data.domain.SliceImpl;

import java.util.ArrayList;

import static com.greghaskins.spectrum.Spectrum.beforeEach;
import static com.greghaskins.spectrum.Spectrum.it;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(Spectrum.class)
public class EncryptionKeyRotatorTest {

  private CredentialVersionDataService credentialVersionDataService;

  private CertificateCredential certificateCredential;
  private PasswordCredential passwordCredential;
  private SshCredential sshCredential;

  {
    beforeEach(() -> {
      credentialVersionDataService = mock(CredentialVersionDataService.class);

      certificateCredential = mock(CertificateCredential.class);
      passwordCredential = mock(PasswordCredential.class);
      sshCredential = mock(SshCredential.class);

      when(credentialVersionDataService.findEncryptedWithAvailableInactiveKey())
          .thenReturn(new SliceImpl<>(asList(certificateCredential, passwordCredential)))
          .thenReturn(new SliceImpl<>(asList(sshCredential)))
          .thenReturn(new SliceImpl<>(new ArrayList<>()));

      final EncryptionKeyRotator encryptionKeyRotator = new EncryptionKeyRotator(credentialVersionDataService);

      encryptionKeyRotator.rotate();
    });

    it("should rotate all the credentials and CAs that were encrypted with an available old key",
        () -> {
          verify(certificateCredential).rotate();
          verify(passwordCredential).rotate();
          verify(sshCredential).rotate();
        });

    it("should save all the credentials, CAs that were rotated", () -> {
      verify(credentialVersionDataService).save(certificateCredential);
      verify(credentialVersionDataService).save(passwordCredential);
      verify(credentialVersionDataService).save(sshCredential);
    });
  }
}
