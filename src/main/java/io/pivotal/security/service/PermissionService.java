package io.pivotal.security.service;

import io.pivotal.security.auth.UserContext;
import io.pivotal.security.data.AccessControlDataService;
import io.pivotal.security.exceptions.PermissionException;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {
  private AccessControlDataService accessControlDataService;

  @Autowired
  public PermissionService(AccessControlDataService accessControlDataService) {
    this.accessControlDataService = accessControlDataService;
  }

  public void verifyAclReadPermission(UserContext user, String credentialName) {
    String actor = getActor(user);

    if (StringUtils.isEmpty(actor) || !accessControlDataService.hasAclReadPermission(actor, credentialName)) {
      throw new PermissionException("error.acl.lacks_acl_read");
    }
  }

  private String getActor(UserContext user) {
    return user.getAclUser();
  }
}
