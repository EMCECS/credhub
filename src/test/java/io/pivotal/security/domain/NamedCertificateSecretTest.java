package io.pivotal.security.domain;

import com.greghaskins.spectrum.Spectrum;
import io.pivotal.security.CredentialManagerApp;
import io.pivotal.security.entity.NamedCertificateSecretData;
import io.pivotal.security.request.CertificateSetRequestFields;
import io.pivotal.security.service.Encryption;
import io.pivotal.security.util.DatabaseProfileResolver;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static com.greghaskins.spectrum.Spectrum.beforeEach;
import static com.greghaskins.spectrum.Spectrum.describe;
import static com.greghaskins.spectrum.Spectrum.it;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(Spectrum.class)
@ActiveProfiles(value = {"unit-test"}, resolver = DatabaseProfileResolver.class)
@SpringBootTest(classes = CredentialManagerApp.class)
public class NamedCertificateSecretTest {

  private NamedCertificateSecret subject;

  private Encryptor encryptor;

  private byte[] encryptedValue;

  private byte[] nonce;
  private UUID canaryUuid;
  private NamedCertificateSecretData namedCertificateSecretData;

  private byte[] encryptedPrivateKey;

  private byte[] privateKeyNonce;

  {
    beforeEach(() -> {
      encryptor = mock(Encryptor.class);
      encryptedValue = "fake-encrypted-value".getBytes();
      nonce = "fake-nonce".getBytes();
      canaryUuid = UUID.randomUUID();
      when(encryptor.encrypt("my-priv"))
          .thenReturn(new Encryption(canaryUuid, encryptedValue, nonce));
      when(encryptor.decrypt(any(UUID.class), eq(encryptedValue), eq(nonce))).thenReturn("my-priv");

      namedCertificateSecretData = new NamedCertificateSecretData("/Foo");
      subject = new NamedCertificateSecret(namedCertificateSecretData)
          .setEncryptor(encryptor)
          .setCa("my-ca")
          .setCertificate("my-cert")
          .setPrivateKey("my-priv");
    });

    it("returns type certificate", () -> {
      assertThat(subject.getSecretType(), equalTo("certificate"));
    });

    it("sets the nonce and the encrypted private key", () -> {
      subject.setPrivateKey("my-priv");
      assertThat(namedCertificateSecretData.getEncryptedValue(), notNullValue());
      assertThat(namedCertificateSecretData.getNonce(), notNullValue());
    });

    it("can decrypt the private key", () -> {
      subject.setPrivateKey("my-priv");
      assertThat(subject.getPrivateKey(), equalTo("my-priv"));
    });

    it("adds a slash to caName", () -> {
      subject.setCaName("something");
      assertThat(subject.getCaName(), equalTo("/something"));

      subject.setCaName("/something");
      assertThat(subject.getCaName(), equalTo("/something"));

      subject.setCaName("");
      assertThat(subject.getCaName(), equalTo(""));

      subject.setCaName(null);
      assertThat(subject.getCaName(), equalTo(null));
    });

    describe("#copyInto", () -> {
      beforeEach(() -> {
        canaryUuid = UUID.randomUUID();
        encryptedPrivateKey = "encrypted-fake-private-key".getBytes();
        privateKeyNonce = "some nonce".getBytes();
        when(encryptor.encrypt(eq("private_key"))).thenReturn(new Encryption(
            canaryUuid,
            encryptedPrivateKey,
            privateKeyNonce
        ));
        when(encryptor.decrypt(canaryUuid, encryptedPrivateKey, privateKeyNonce)).thenReturn(
            "private_key"
        );

      });
      it("should copy the correct values", () -> {
        Instant frozenTime = Instant.ofEpochSecond(1400000000L);
        UUID uuid = UUID.randomUUID();
        UUID encryptionKeyUuid = UUID.randomUUID();

        namedCertificateSecretData = new NamedCertificateSecretData("/name");
        subject = new NamedCertificateSecret(namedCertificateSecretData);
        subject.setEncryptor(encryptor);
        subject.setPrivateKey("private_key");
        subject.setCa("fake-ca");
        subject.setCertificate("fake-certificate");
        subject.setCaName("ca-name");
        subject.setUuid(uuid);
        subject.setVersionCreatedAt(frozenTime);

        NamedCertificateSecret copy = new NamedCertificateSecret();
        subject.copyInto(copy);

        assertThat(copy.getName(), equalTo("/name"));
        assertThat(copy.getCaName(), equalTo("/ca-name"));
        assertThat(copy.getCa(), equalTo("fake-ca"));
        assertThat(copy.getPrivateKey(), equalTo("private_key"));
        assertThat(copy.getUuid(), not(equalTo(uuid)));

        assertThat(copy.getVersionCreatedAt(), not(equalTo(frozenTime)));

        verify(encryptor).encrypt("private_key");
        verify(encryptor).decrypt(canaryUuid, encryptedPrivateKey, privateKeyNonce);
      });
    });

    describe(".createNewVersion", () -> {
      beforeEach(() -> {
        byte[] encryptedValue = "new-fake-encrypted".getBytes();
        byte[] nonce = "new-fake-nonce".getBytes();
        when(encryptor.encrypt("new private key"))
            .thenReturn(new Encryption(canaryUuid, encryptedValue, nonce));
        when(encryptor.decrypt(any(UUID.class), eq(encryptedValue), eq(nonce)))
            .thenReturn("new private key");

        subject = new NamedCertificateSecret("/existingName");
        subject.setEncryptor(encryptor);
//        subject.setEncryptedValue("old encrypted private key".getBytes());
      });

      it("copies name from existing", () -> {
        CertificateSetRequestFields fields = new CertificateSetRequestFields("new private key",
            "certificate", "ca");
        NamedCertificateSecret newSecret = (NamedCertificateSecret) NamedCertificateSecret
            .createNewVersion(subject, "anything I AM IGNORED", fields, encryptor,
                new ArrayList<>());

        assertThat(newSecret.getName(), equalTo("/existingName"));
        assertThat(newSecret.getPrivateKey(), equalTo("new private key"));
        assertThat(newSecret.getCa(), equalTo("ca"));
        assertThat(newSecret.getCertificate(), equalTo("certificate"));
        assertThat(newSecret.getCaName(), equalTo(null));
      });

      it("creates new if no existing", () -> {
        CertificateSetRequestFields fields = new CertificateSetRequestFields("new private key",
            "certificate", "ca");
        NamedCertificateSecret newSecret = (NamedCertificateSecret) NamedCertificateSecret
            .createNewVersion(null, "/newName", fields, encryptor, new ArrayList<>());

        assertThat(newSecret.getName(), equalTo("/newName"));
        assertThat(newSecret.getPrivateKey(), equalTo("new private key"));
        assertThat(newSecret.getCa(), equalTo("ca"));
        assertThat(newSecret.getCertificate(), equalTo("certificate"));
        assertThat(newSecret.getCaName(), equalTo(null));
      });
    });
  }
}
