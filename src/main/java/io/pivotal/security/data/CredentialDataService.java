package io.pivotal.security.data;

import io.pivotal.security.domain.Credential;
import io.pivotal.security.domain.CredentialViewFactory;
import io.pivotal.security.entity.CredentialData;
import io.pivotal.security.entity.CredentialName;
import io.pivotal.security.repository.CredentialNameRepository;
import io.pivotal.security.repository.CredentialRepository;
import io.pivotal.security.service.EncryptionKeyCanaryMapper;
import io.pivotal.security.view.FindCredentialResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static io.pivotal.security.repository.CredentialRepository.BATCH_SIZE;

@Service
public class CredentialDataService {

  protected final CredentialRepository credentialRepository;
  private final CredentialNameRepository credentialNameRepository;
  private final JdbcTemplate jdbcTemplate;
  private final EncryptionKeyCanaryMapper encryptionKeyCanaryMapper;
  private final CredentialViewFactory credentialViewFactory;
  private final String findMatchingNameQuery =
      " select name.name, credential.version_created_at from ("
          + "   select"
          + "     max(version_created_at) as version_created_at,"
          + "     credential_name_uuid"
          + "   from credential group by credential_name_uuid"
          + " ) as credential inner join ("
          + "   select * from credential_name"
          + "     where lower(name) like lower(?)"
          + " ) as name"
          + " on credential.credential_name_uuid = name.uuid"
          + " order by version_created_at desc";

  @Autowired
  protected CredentialDataService(
      CredentialRepository credentialRepository,
      CredentialNameRepository credentialNameRepository,
      JdbcTemplate jdbcTemplate,
      EncryptionKeyCanaryMapper encryptionKeyCanaryMapper,
      CredentialViewFactory credentialViewFactory
  ) {
    this.credentialRepository = credentialRepository;
    this.credentialNameRepository = credentialNameRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.encryptionKeyCanaryMapper = encryptionKeyCanaryMapper;
    this.credentialViewFactory = credentialViewFactory;
  }

  public <Z extends Credential> Z save(Z namedSecret) {
    return (Z) namedSecret.save(this);
  }

  public <Z extends Credential> Z save(CredentialData credentialData) {
    if (credentialData.getEncryptionKeyUuid() == null) {
      credentialData.setEncryptionKeyUuid(encryptionKeyCanaryMapper.getActiveUuid());
    }

    CredentialName credentialName = credentialData.getCredentialName();

    if (credentialName.getUuid() == null) {
      credentialData.setCredentialName(credentialNameRepository.saveAndFlush(credentialName));
    }

    return (Z) credentialViewFactory.makeCredentialFromEntity(credentialRepository.saveAndFlush(credentialData));
  }

  public List<String> findAllPaths() {
    return findAllPaths(true);
  }

  private List<String> findAllPaths(Boolean findPaths) {
    if (!findPaths) {
      return newArrayList();
    }

    return credentialRepository.findAll().stream()
        .map(credential -> credential.getCredentialName().getName())
        .flatMap(CredentialDataService::fullHierarchyForPath)
        .distinct()
        .sorted()
        .collect(Collectors.toList());
  }

  private static Stream<String> fullHierarchyForPath(String path) {
    String[] components = path.split("/");
    if (components.length > 1) {
      StringBuilder currentPath = new StringBuilder();
      List<String> pathSet = new ArrayList<>();
      for (int i = 0; i < components.length - 1; i++) {
        String element = components[i];
        currentPath.append(element).append('/');
        pathSet.add(currentPath.toString());
      }
      return pathSet.stream();
    } else {
      return Stream.of();
    }
  }

  public Credential findMostRecent(String name) {
    CredentialName credentialName = credentialNameRepository
        .findOneByNameIgnoreCase(StringUtils.prependIfMissing(name, "/"));

    if (credentialName == null) {
      return null;
    } else {
      return credentialViewFactory.makeCredentialFromEntity(credentialRepository
          .findFirstByCredentialNameUuidOrderByVersionCreatedAtDesc(credentialName.getUuid()));
    }
  }

  public Credential findByUuid(String uuid) {
    return credentialViewFactory.makeCredentialFromEntity(credentialRepository.findOneByUuid(UUID.fromString(uuid)));
  }

  public List<FindCredentialResult> findContainingName(String name) {
    return findMatchingName("%" + name + "%");
  }

  public List<FindCredentialResult> findStartingWithPath(String path) {
    path = StringUtils.prependIfMissing(path, "/");
    path = StringUtils.appendIfMissing(path, "/");

    return findMatchingName(path + "%");
  }

  public boolean delete(String name) {
    long numDeleted = credentialNameRepository.deleteByNameIgnoreCase(
        StringUtils.prependIfMissing(name, "/"));
    return numDeleted > 0;
  }

  public List<Credential> findAllByName(String name) {
    CredentialName credentialName = credentialNameRepository
        .findOneByNameIgnoreCase(StringUtils.prependIfMissing(name, "/"));

    return credentialName != null ? credentialViewFactory.makeCredentialsFromEntities(credentialRepository.findAllByCredentialNameUuid(credentialName.getUuid()))
        : newArrayList();
  }

  public Long count() {
    return credentialRepository.count();
  }

  public Long countAllNotEncryptedByActiveKey() {
    return credentialRepository.countByEncryptionKeyUuidNot(
        encryptionKeyCanaryMapper.getActiveUuid()
    );
  }

  public Long countEncryptedWithKeyUuidIn(List<UUID> uuids) {
    return credentialRepository.countByEncryptionKeyUuidIn(uuids);
  }

  public Slice<Credential> findEncryptedWithAvailableInactiveKey() {
    final Slice<CredentialData> credentialDataSlice = credentialRepository.findByEncryptionKeyUuidIn(
        encryptionKeyCanaryMapper.getCanaryUuidsWithKnownAndInactiveKeys(),
        new PageRequest(0, BATCH_SIZE)
    );
    return new SliceImpl(credentialViewFactory.makeCredentialsFromEntities(credentialDataSlice.getContent()));
  }

  private List<FindCredentialResult> findMatchingName(String nameLike) {
    final List<FindCredentialResult> query = jdbcTemplate.query(
        findMatchingNameQuery,
        new Object[]{nameLike},
        (rowSet, rowNum) -> {
          final Instant versionCreatedAt = Instant
              .ofEpochMilli(rowSet.getLong("version_created_at"));
          final String name = rowSet.getString("name");
          return new FindCredentialResult(versionCreatedAt, name);
        }
    );
    return query;
  }
}
