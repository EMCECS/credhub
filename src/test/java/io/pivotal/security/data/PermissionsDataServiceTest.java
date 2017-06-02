package io.pivotal.security.data;

import io.pivotal.security.CredentialManagerApp;
import io.pivotal.security.entity.Credential;
import io.pivotal.security.entity.ValueCredentialData;
import io.pivotal.security.exceptions.EntryNotFoundException;
import io.pivotal.security.request.PermissionEntry;
import io.pivotal.security.request.PermissionOperation;
import io.pivotal.security.util.DatabaseProfileResolver;
import org.apache.commons.lang.StringUtils;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.hamcrest.core.IsEqual.equalTo;

@RunWith(SpringRunner.class)
@ActiveProfiles(value = "unit-test", resolver = DatabaseProfileResolver.class)
@SpringBootTest(classes = CredentialManagerApp.class)
@Transactional
public class PermissionsDataServiceTest {
  private static final String CREDENTIAL_NAME = "/lightsaber";
  private static final String CREDENTIAL_NAME_WITHOUT_LEADING_SLASH = StringUtils.removeStart(CREDENTIAL_NAME, "/");
  private static final String CREDENTIAL_NAME_DOES_NOT_EXIST = "/this/credential/does/not/exist";

  private static final String LUKE = "Luke";
  private static final String LEIA = "Leia";
  private static final String HAN_SOLO = "HansSolo";
  private static final String DARTH = "Darth";
  private static final String CHEWIE = "Chewie";

  @Autowired
  private PermissionsDataService subject;

  @Autowired
  private CredentialDataService credentialDataService;

  private List<PermissionEntry> aces;
  private Credential credential;

  @Before
  public void beforeEach() {
    seedDatabase();
  }

  @Test
  public void getAccessControlList_givenExistingCredentialName_returnsAcl() {
    final List<PermissionEntry> accessControlEntries = subject.getAccessControlList(credential);

    assertThat(accessControlEntries, hasSize(3));

    assertThat(accessControlEntries, containsInAnyOrder(
        allOf(hasProperty("actor", equalTo(LUKE)),
            hasProperty("allowedOperations", hasItems(PermissionOperation.WRITE))),
        allOf(hasProperty("actor", equalTo(LEIA)),
            hasProperty("allowedOperations", hasItems(PermissionOperation.READ))),
        allOf(hasProperty("actor", equalTo(HAN_SOLO)),
            hasProperty("allowedOperations",
                hasItems(PermissionOperation.READ_ACL))))
    );
  }

  @Test
  public void getAllowedOperations_whenTheCredentialExists_andTheActorHasPermissions_returnsListOfActivePermissions() {
    assertThat(subject.getAllowedOperations(CREDENTIAL_NAME, LUKE), containsInAnyOrder(
        PermissionOperation.WRITE,
        PermissionOperation.DELETE
    ));
  }

  @Test
  public void getAllowedOperations_whenTheNameIsMissingTheLeadingSlash_returnsListOfActivePermissions() {
    assertThat(subject.getAllowedOperations(CREDENTIAL_NAME_WITHOUT_LEADING_SLASH, LUKE), containsInAnyOrder(
        PermissionOperation.WRITE,
        PermissionOperation.DELETE
    ));
  }

  @Test
  public void getAllowedOperations_whenTheCredentialExists_andTheActorHasNoPermissions_returnsEmptyList() {
    assertThat(subject.getAllowedOperations(CREDENTIAL_NAME, DARTH).size(), equalTo(0));
  }

  @Test
  public void getAllowedOperations_whenTheCredentialDoesNotExist_returnsEmptyList() {
    assertThat(subject.getAllowedOperations("/unicorn", LEIA).size(), equalTo(0));
  }

  @Test
  public void getAccessControlList_whenGivenNonExistentCredentialName_throwsException() {
    try {
      subject.getAccessControlList(new Credential(CREDENTIAL_NAME_DOES_NOT_EXIST));
    } catch (EntryNotFoundException enfe) {
      assertThat(enfe.getMessage(), Matchers.equalTo("error.resource_not_found"));
    }
  }

  @Test
  public void setAccessControlEntries_whenGivenAnExistingAce_returnsTheAcl() {
    aces = singletonList(
        new PermissionEntry(LUKE, singletonList(PermissionOperation.READ))
    );

    subject.saveAccessControlEntries(credential, aces);

    List<PermissionEntry> response = subject.getAccessControlList(credential);

        assertThat(response, containsInAnyOrder(
        allOf(hasProperty("actor", equalTo(LUKE)),
            hasProperty("allowedOperations",
                hasItems(PermissionOperation.READ, PermissionOperation.WRITE))),
        allOf(hasProperty("actor", equalTo(LEIA)),
            hasProperty("allowedOperations", hasItems(PermissionOperation.READ))),
        allOf(hasProperty("actor", equalTo(HAN_SOLO)),
            hasProperty("allowedOperations",
                hasItems(PermissionOperation.READ_ACL)))));
  }

  @Test
  public void setAccessControlEntries_whenGivenANewAce_returnsTheAcl() {
    final ValueCredentialData valueCredentialData2 = new ValueCredentialData("lightsaber2");
    final Credential credential2 = valueCredentialData2.getCredential();

    credentialDataService.save(credential2);
    aces = singletonList(
        new PermissionEntry(LUKE, singletonList(PermissionOperation.READ)));

    subject.saveAccessControlEntries(credential2, aces);

    List<PermissionEntry> response = subject.getAccessControlList(credential2);


    final PermissionEntry permissionEntry = response.get(0);

    assertThat(response, hasSize(1));
    assertThat(permissionEntry.getActor(), equalTo(LUKE));
    assertThat(permissionEntry.getAllowedOperations(), hasSize(1));
    assertThat(permissionEntry.getAllowedOperations(), hasItem(PermissionOperation.READ));
  }

  @Test
  public void deleteAccessControlEntry_whenGivenExistingCredentialAndActor_deletesTheAce() {
    subject.deleteAccessControlEntry(CREDENTIAL_NAME, LUKE);

    final List<PermissionEntry> accessControlList = subject
        .getAccessControlList(credential);

    assertThat(accessControlList, hasSize(2));

    assertThat(accessControlList,
        not(contains(hasProperty("actor", equalTo(LUKE)))));
  }

  @Test
  public void deleteAccessControlEntry_whenNameIsMissingLeadingSlash_deletesTheAce() {
    boolean deleted = subject.deleteAccessControlEntry(CREDENTIAL_NAME_WITHOUT_LEADING_SLASH, LUKE);

    assertTrue(deleted);

    final List<PermissionEntry> accessControlList = subject
        .getAccessControlList(credential);

    assertThat(accessControlList, hasSize(2));

    assertThat(accessControlList,
        not(contains(hasProperty("actor", equalTo(LUKE)))));
  }

  @Test
  public void deleteAccessControlEntry_whenNonExistentResource_returnsFalse() {
    boolean deleted = subject.deleteAccessControlEntry("/some-thing-that-is-not-here", LUKE);
    assertFalse(deleted);
  }

  @Test
  public void deleteAccessControlEntry_whenNonExistentAce_returnsFalse() {
    boolean deleted = subject.deleteAccessControlEntry(CREDENTIAL_NAME, DARTH);
    assertFalse(deleted);
  }

  @Test
  public void hasAclReadPermission_whenActorHasAclRead_returnsTrue() {
    assertThat(subject.hasReadAclPermission(HAN_SOLO, CREDENTIAL_NAME),
        is(true));
  }

  @Test
  public void hasAclReadPermission_whenActorHasReadButNotReadAcl_returnsFalse() {
    assertThat(subject.hasReadAclPermission(LUKE, CREDENTIAL_NAME),
        is(false));
  }

  @Test
  public void hasAclReadPermission_whenActorHasNoPermissions_returnsFalse() {
    assertThat(subject.hasReadAclPermission(CHEWIE, CREDENTIAL_NAME),
        is(false));
  }

  @Test
  public void hasAclReadPermission_whenCredentialDoesNotExist_returnsFalse() {
    assertThat(subject.hasReadAclPermission(LUKE, CREDENTIAL_NAME_DOES_NOT_EXIST),
        is(false));
  }

  @Test
  public void hasAclWritePermission_whenActorHasAclWrite_returnsTrue() {
    assertThat(subject.hasAclWritePermission(HAN_SOLO, CREDENTIAL_NAME),
        is(true));
  }

  @Test
  public void hasAclWritePermission_whenActorHasWriteButNotWriteAcl_returnsFalse() {
    assertThat(subject.hasAclWritePermission(LUKE, CREDENTIAL_NAME),
        is(false));
  }

  @Test
  public void hasAclWritePermission_whenActorHasNoPermissions_returnsFalse() {
    assertThat(subject.hasAclWritePermission(CHEWIE, CREDENTIAL_NAME),
        is(false));
  }

  @Test
  public void hasAclWritePermission_whenCredentialDoesNotExist_returnsFalse() {
    assertThat(subject.hasAclWritePermission(LUKE, CREDENTIAL_NAME_DOES_NOT_EXIST),
        is(false));
  }

  @Test
  public void hasReadPermission_whenActorHasRead_returnsTrue() {
    assertThat(subject.hasReadPermission(LEIA, CREDENTIAL_NAME),
        is(true));
  }

  @Test
  public void hasReadPermission_givenNameWithoutLeadingSlashAndHasRead_returnsTrue() {
    assertThat(subject.hasReadPermission(LEIA, CREDENTIAL_NAME),
        is(true));
  }

  @Test
  public void hasReadPermission_whenActorHasWriteButNotRead_returnsFalse() {
    assertThat(subject.hasReadPermission(LUKE, CREDENTIAL_NAME),
        is(false));
  }

  @Test
  public void hasReadPermission_whenActorHasNoPermissions_returnsFalse() {
    assertThat(subject.hasReadPermission(CHEWIE, CREDENTIAL_NAME),
        is(false));
  }

  @Test
  public void hasCredentialWritePermission_whenActorHasWritePermission_returnsTrue() {
    assertThat(subject.hasCredentialWritePermission(LUKE, CREDENTIAL_NAME), is(true));
  }

  @Test
  public void hasCredentialWritePermission_whenActorOnlyHasOtherPermissions_returnsFalse() {
    assertThat(subject.hasCredentialWritePermission(LEIA, CREDENTIAL_NAME), is(false));
  }

  @Test
  public void hasCredentialWritePermission_whenActorHasNoPermissions_returnsFalse() {
    assertThat(subject.hasCredentialWritePermission(DARTH, CREDENTIAL_NAME), is(false));
  }

  @Test
  public void hasCredentialDeletePermission_whenActorHasDeletePermission_returnsTrue() {
    assertThat(subject.hasCredentialDeletePermission(LUKE, CREDENTIAL_NAME), is(true));
  }

  @Test
  public void hasCredentialDeletePermission_whenActorOnlyHasOtherPermissions_returnsFalse() {
    assertThat(subject.hasCredentialDeletePermission(LEIA, CREDENTIAL_NAME), is(false));
  }

  @Test
  public void hasCredentialDeletePermission_whenActorHasNoPermissions_returnsFalse() {
    assertThat(subject.hasCredentialDeletePermission(DARTH, CREDENTIAL_NAME), is(false));
  }

  @Test
  public void hasReadPermission_whenCredentialDoesNotExist_returnsFalse() {
    assertThat(subject.hasReadPermission(LUKE, CREDENTIAL_NAME_DOES_NOT_EXIST),
        is(false));
  }

  private void seedDatabase() {
    final ValueCredentialData valueCredentialData = new ValueCredentialData(CREDENTIAL_NAME);
    credential = valueCredentialData.getCredential();

    credential = credentialDataService.save(credential);

    subject.saveAccessControlEntries(
        credential,
        singletonList(new PermissionEntry(LUKE,
            newArrayList(PermissionOperation.WRITE, PermissionOperation.DELETE)))
    );

    subject.saveAccessControlEntries(
        credential,
        singletonList(new PermissionEntry(LEIA,
            singletonList(PermissionOperation.READ)))
    );

    subject.saveAccessControlEntries(
        credential,
        singletonList(new PermissionEntry(HAN_SOLO,
            newArrayList(PermissionOperation.READ_ACL, PermissionOperation.WRITE_ACL)))
    );
  }
}
