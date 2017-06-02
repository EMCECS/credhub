package io.pivotal.security.service;

import io.pivotal.security.data.CredentialVersionDataService;
import io.pivotal.security.domain.Credential;
import io.pivotal.security.exceptions.KeyNotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

@Component
public class EncryptionKeyRotator {

  private final CredentialVersionDataService credentialVersionDataService;
  private final Logger logger;

  EncryptionKeyRotator(CredentialVersionDataService credentialVersionDataService) {
    this.credentialVersionDataService = credentialVersionDataService;
    this.logger = LogManager.getLogger(this.getClass());
  }

  public void rotate() {
    final long start = System.currentTimeMillis();
    logger.info("Starting encryption key rotation.");
    int rotatedRecordCount = 0;

    final long startingNotRotatedRecordCount = credentialVersionDataService.countAllNotEncryptedByActiveKey();

    Slice<Credential> credentialsEncryptedByOldKey = credentialVersionDataService
        .findEncryptedWithAvailableInactiveKey();
    while (credentialsEncryptedByOldKey.hasContent()) {
      for (Credential credential : credentialsEncryptedByOldKey.getContent()) {
        try {
          credential.rotate();
          credentialVersionDataService.save(credential);
          rotatedRecordCount++;
        } catch (KeyNotFoundException e) {
          logger.error("key not found for value, unable to rotate");
        }
      }
      credentialsEncryptedByOldKey = credentialVersionDataService.findEncryptedWithAvailableInactiveKey();
    }

    final long finish = System.currentTimeMillis();
    final long duration = finish - start;
    final long endingNotRotatedRecordCount = startingNotRotatedRecordCount - rotatedRecordCount;

    if (rotatedRecordCount == 0 && endingNotRotatedRecordCount == 0) {
      logger.info("Found no records in need of encryption key rotation.");
    } else {
      logger.info("Finished encryption key rotation in " + duration + " milliseconds. Details:");
      logger.info("  Successfully rotated " + rotatedRecordCount + " item(s)");
      logger.info("  Skipped " + endingNotRotatedRecordCount
          + " item(s) due to missing master encryption key(s).");
    }
  }
}
