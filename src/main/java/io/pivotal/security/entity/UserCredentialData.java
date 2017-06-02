package io.pivotal.security.entity;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.SecondaryTable;

@Entity
@DiscriminatorValue("user")
@SecondaryTable(
    name = UserCredentialData.TABLE_NAME,
    pkJoinColumns = {@PrimaryKeyJoinColumn(name = "uuid", referencedColumnName = "uuid")}
)
public class UserCredentialData extends CredentialVersionData<UserCredentialData> {
  public static final String TABLE_NAME = "UserCredential";
  public static final String CREDENTIAL_TYPE = "user";

  @Column(table = UserCredentialData.TABLE_NAME, length = 7000)
  private String username;

  @Column(table = UserCredentialData.TABLE_NAME, length = 20)
  private String salt;

  public UserCredentialData() {
    this(null);
  }

  public UserCredentialData(String name) {
    super(name);
  }

  @Override
  public String getCredentialType() {
    return CREDENTIAL_TYPE;
  }

  public String getUsername() {
    return username;
  }

  public UserCredentialData setUsername(String username) {
    this.username = username;
    return this;
  }

  public UserCredentialData setSalt(String salt) {
    this.salt = salt;
    return this;
  }

  public String getSalt() {
    return salt;
  }
}
