package io.pivotal.security.entity;

import org.hibernate.annotations.GenericGenerator;

import static io.pivotal.security.constants.EncryptionConstants.ENCRYPTED_BYTES;
import static io.pivotal.security.constants.EncryptionConstants.NONCE;
import static io.pivotal.security.constants.UuidConstants.UUID_BYTES;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "EncryptionKeyCanary")
public class EncryptionKeyCanary implements EncryptedValueContainer {
  // Use VARBINARY to make all 3 DB types happy.
  // H2 doesn't distinguish between "binary" and "varbinary" - see
  // https://hibernate.atlassian.net/browse/HHH-9835 and
  // https://github.com/h2database/h2database/issues/345
  @Id
  @Column(length = UUID_BYTES, columnDefinition = "VARBINARY")
  @GeneratedValue(generator = "uuid2")
  @GenericGenerator(name = "uuid2", strategy = "uuid2")
  private UUID uuid;

  @Column(length = ENCRYPTED_BYTES + NONCE, name = "encrypted_value")
  private byte[] encryptedValue;

  @Column(length = NONCE)
  private byte[] nonce;
  private String name;

  public UUID getUuid() {
    return uuid;
  }

  public void setUuid(UUID uuid) {
    this.uuid = uuid;
  }

  @Override
  public byte[] getEncryptedValue() {
    return encryptedValue;
  }

  @Override
  public void setEncryptedValue(byte[] encryptedValue) {
    this.encryptedValue = encryptedValue;
  }

  @Override
  public byte[] getNonce() {
    return nonce;
  }

  @Override
  public void setNonce(byte[] nonce) {
    this.nonce = nonce;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}