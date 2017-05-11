package io.pivotal.security.handler;

import io.pivotal.security.auth.UserContext;
import io.pivotal.security.data.AccessControlDataService;
import io.pivotal.security.data.CredentialNameDataService;
import io.pivotal.security.entity.AccessEntryData;
import io.pivotal.security.entity.CredentialName;
import io.pivotal.security.exceptions.EntryNotFoundException;
import io.pivotal.security.exceptions.PermissionException;
import io.pivotal.security.request.AccessControlEntry;
import io.pivotal.security.request.AccessControlOperation;
import io.pivotal.security.service.PermissionService;
import io.pivotal.security.view.AccessControlListResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static io.pivotal.security.request.AccessControlOperation.READ;
import static io.pivotal.security.request.AccessControlOperation.WRITE;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class AccessControlHandlerTest {
  private static final String CREDENTIAL_NAME = "/test-credential";

  private AccessControlHandler subject;

  private PermissionService permissionService;
  private AccessControlDataService accessControlDataService;
  private CredentialNameDataService credentialNameDataService;

  private final CredentialName credentialName = new CredentialName(CREDENTIAL_NAME);
  private final UserContext userContext = mock(UserContext.class);

  @Before
  public void beforeEach() {
    permissionService = mock(PermissionService.class);
    accessControlDataService = mock(AccessControlDataService.class);
    credentialNameDataService = mock(CredentialNameDataService.class);
    subject = new AccessControlHandler(
        permissionService,
        accessControlDataService,
        credentialNameDataService
    );

    when(credentialNameDataService.findOrThrow(any(String.class))).thenReturn(credentialName);
  }

  @Test
  public void getAccessControlListResponse_whenTheNameDoesntStartWithASlash_fixesTheName() {
    List<AccessEntryData> accessControlList = newArrayList();
    when(accessControlDataService.getAccessControlList(any(CredentialName.class)))
        .thenReturn(accessControlList);
    when(credentialNameDataService.findOrThrow(any(String.class)))
        .thenReturn(new CredentialName("/test-credential"));

    AccessControlListResponse response = subject.getAccessControlListResponse(
        null,
        "test-credential"
    );
    assertThat(response.getCredentialName(), equalTo("/test-credential"));
  }

  @Test
  public void getAccessControlListResponse_verifiesTheUserHasPermissionToReadTheAcl_andReturnsTheAclResponse() {
    ArrayList<AccessControlOperation> operations = newArrayList(
        READ,
        WRITE
    );
    AccessEntryData accessControlEntry = new AccessEntryData(
        credentialName,
        "test-actor",
        operations
    );
    List<AccessEntryData> accessControlList = newArrayList(accessControlEntry);
    when(accessControlDataService.getAccessControlList(credentialName))
        .thenReturn(accessControlList);

    AccessControlListResponse response = subject.getAccessControlListResponse(
        userContext,
        "/test-credential"
    );

    verify(permissionService, times(1))
        .verifyAclReadPermission(userContext, CREDENTIAL_NAME);

    List<AccessControlEntry> accessControlEntries = response.getAccessControlList();

    assertThat(response.getCredentialName(), equalTo("/test-credential"));
    assertThat(accessControlEntries, hasSize(1));

    AccessControlEntry entry = accessControlEntries.get(0);
    assertThat(entry.getActor(), equalTo("test-actor"));

    List<AccessControlOperation> allowedOperations = entry.getAllowedOperations();
    assertThat(allowedOperations, contains(
        equalTo(READ),
        equalTo(WRITE)
    ));
  }

  @Test
  public void setAccessControlEntries_setsAndReturnsTheAces() {
    when(permissionService.hasAclWritePermission(userContext, "/test-credential"))
        .thenReturn(true);

    List<AccessControlOperation> operations = newArrayList(
        READ,
        WRITE
    );
    List<AccessControlEntry> accessControlEntries = newArrayList(
        new AccessControlEntry("test-actor", operations)
    );

    AccessEntryData accessControlEntry = new AccessEntryData(credentialName,"test-actor", operations);

    AccessEntryData preexistingAccessControlEntry = new AccessEntryData(
        credentialName,
        "someone-else",
        newArrayList(READ)
    );
    List<AccessEntryData> expectedControlList = newArrayList(accessControlEntry, preexistingAccessControlEntry);

    when(accessControlDataService.saveAccessControlEntries(credentialName, accessControlEntries))
        .thenReturn(expectedControlList);

    AccessControlListResponse response = subject.setAccessControlEntries(userContext, "/test-credential", accessControlEntries);

    List<AccessControlEntry> result = response.getAccessControlList();

    assertThat(response.getCredentialName(), equalTo("/test-credential"));
    assertThat(result, hasSize(2));

    assertThat(result, containsInAnyOrder(
        samePropertyValuesAs(accessControlEntry)
    ));

    AccessControlEntry entry1 = result.get(0);
    assertThat(entry1, samePropertyValuesAs(accessControlEntry));
//    assertThat(entry1.getAllowedOperations(), contains(
//        equalTo(READ),
//        equalTo(WRITE)
//    ));

//    AccessControlEntry entry2 = result.get(1);
//    assertThat(entry2.getActor(), equalTo("someone-else"));
//    assertThat(entry2.getAllowedOperations(), contains(equalTo(READ)));
  }

  @Test
  public void setAccessControlEntries_whenUserDoesNotHavePermission_throwsException() {
    when(permissionService.hasAclWritePermission(userContext, "/test/credential"))
        .thenReturn(false);

    try {
      subject.setAccessControlEntries(userContext, "/test/credential", emptyList());
      fail("should throw");
    } catch (PermissionException e) {
      assertThat(e.getMessage(), equalTo("error.acl.lacks_credential_write"));
      verify(accessControlDataService, times(0)).saveAccessControlEntries(any(), any());
    }
  }

  @Test
  public void deleteAccessControlEntries_deletesTheAce() {
    when(credentialNameDataService.findOrThrow("/test-credential")).thenReturn(credentialName);
    when(permissionService.hasAclWritePermission(userContext, "/test-credential"))
        .thenReturn(true);

    AccessEntryData accessEntryData = new AccessEntryData(credentialName, "test-actor",
        newArrayList(READ));
    subject.deleteAccessControlEntries(
        userContext,
        accessEntryData);

    verify(accessControlDataService, times(1))
        .deleteAccessControlEntry(accessEntryData);
  }

  @Test
  public void deleteAccessControlEntries_verifiesTheUserHasPermission() {
    when(permissionService.hasAclWritePermission(userContext, "/test-credential"))
        .thenReturn(false);
    AccessEntryData entryData = new AccessEntryData(credentialName, "test-actor",
        newArrayList(READ, WRITE));

    try {
      subject.deleteAccessControlEntries(userContext, entryData);
    } catch (EntryNotFoundException e) {
      assertThat(e.getMessage(), equalTo("error.acl.lacks_credential_write"));
      verify(accessControlDataService, times(0)).deleteAccessControlEntry(any());
    }
  }
}
