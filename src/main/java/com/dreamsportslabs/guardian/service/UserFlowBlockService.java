package com.dreamsportslabs.guardian.service;

import com.dreamsportslabs.guardian.constant.Contact;
import com.dreamsportslabs.guardian.dao.UserFlowBlockDao;
import com.dreamsportslabs.guardian.dao.model.PasswordlessModel;
import com.dreamsportslabs.guardian.dao.model.UserFlowBlockModel;
import com.dreamsportslabs.guardian.dto.request.*;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class UserFlowBlockService {
  private final UserFlowBlockDao userFlowBlockDao;

  public Completable blockUserFlows(V1BlockUserFlowRequestDto dto, String tenantId) {
    log.info(
        "Blocking flows for userIdentifier: {} in tenant: {}", dto.getUserIdentifier(), tenantId);

    List<UserFlowBlockModel> models =
        dto.getBlockFlows().stream()
            .map(
                flowName ->
                    UserFlowBlockModel.builder()
                        .tenantId(tenantId)
                        .userIdentifier(dto.getUserIdentifier())
                        .flowName(flowName)
                        .reason(dto.getReason())
                        .unblockedAt(dto.getUnblockedAt())
                        .isActive(true)
                        .build())
            .toList();

    return userFlowBlockDao
        .blockFlows(models)
        .doOnComplete(() -> log.info("Blocked flows saved successfully"))
        .doOnError(err -> log.error("Error while saving blocked flows: {}", err.getMessage(), err));
  }

  public Completable unblockUserFlows(V1UnblockUserFlowRequestDto dto, String tenantId) {
    log.info(
        "Unblocking flows for userIdentifier: {} of tenant: {}", dto.getUserIdentifier(), tenantId);

    return userFlowBlockDao
        .unblockFlows(tenantId, dto.getUserIdentifier(), dto.getUnblockFlows())
        .doOnComplete(() -> log.info("Flows unblocked successfully"))
        .doOnError(error -> log.error("Error during unblock: {}", error.getMessage(), error));
  }

  public Single<List<UserFlowBlockModel>> getActiveFlowsBlockedForUser(
      String tenantId, String userIdentifier) {
    return userFlowBlockDao.getActiveFlowBlocksByUser(tenantId, userIdentifier);
  }

  public Single<FlowBlockCheckResult> checkFlowBlockedWithReasonBatch(
      String tenantId, List<String> userIdentifiers, String flowName) {
    return userFlowBlockDao
        .checkFlowBlockedWithReasonBatch(tenantId, userIdentifiers, flowName)
        .map(result -> new FlowBlockCheckResult(result.isBlocked(), result.getReason()));
  }

  @Getter
  public static class FlowBlockCheckResult {
    private final boolean blocked;
    private final String reason;

    public FlowBlockCheckResult(boolean blocked, String reason) {
      this.blocked = blocked;
      this.reason = reason;
    }
  }

  public Single<FlowBlockCheckResult> isUserBlocked(
      V1SignInRequestDto requestDto, String tenantId) {

    if (StringUtils.isBlank(requestDto.getUsername())) {
      return Single.just(new FlowBlockCheckResult(false, null));
    }

    String username = requestDto.getUsername().trim();
    return isFlowBlocked(tenantId, List.of(username), "password");
  }

  public Single<FlowBlockCheckResult> isUserBlocked(
      V1SignUpRequestDto requestDto, String tenantId) {

    if (StringUtils.isBlank(requestDto.getUsername())) {
      return Single.just(new FlowBlockCheckResult(false, null));
    }

    String username = requestDto.getUsername().trim();

    return isFlowBlocked(tenantId, List.of(username), "password");
  }

  public Single<FlowBlockCheckResult> isUserBlocked(
      V1PasswordlessInitRequestDto requestDto, String tenantId) {
    if (requestDto.getContacts() == null || requestDto.getContacts().isEmpty()) {
      return Single.just(new FlowBlockCheckResult(false, null));
    }

    List<String> contacts =
        requestDto.getContacts().stream()
            .map(Contact::getIdentifier)
            .filter(StringUtils::isNotBlank)
            .toList();

    return isFlowBlocked(tenantId, contacts, "passwordless");
  }

  public Single<FlowBlockCheckResult> isUserBlocked(PasswordlessModel model, String tenantId) {
    if (model.getContacts() == null || model.getContacts().isEmpty()) {
      return Single.just(new FlowBlockCheckResult(false, null));
    }

    List<String> contacts =
        model.getContacts().stream()
            .map(Contact::getIdentifier)
            .filter(StringUtils::isNotBlank)
            .toList();

    return isFlowBlocked(tenantId, contacts, "passwordless");
  }

  public Single<FlowBlockCheckResult> isUserBlocked(
      String userIdentifier, String tenantId, String flow) {
    if (StringUtils.isBlank(userIdentifier)) {
      return Single.just(new FlowBlockCheckResult(false, null));
    }
    return isFlowBlocked(tenantId, List.of(userIdentifier), flow);
  }

  public Single<FlowBlockCheckResult> isFlowBlocked(
      String tenantId, List<String> userIdentifiers, String flow) {
    return checkFlowBlockedWithReasonBatch(tenantId, userIdentifiers, flow)
        .doOnSuccess(
            result -> {
              if (result.isBlocked()) {
                log.info(
                    "{} flow is blocked for userIdentifiers: {} in tenant: {} with reason: {}",
                    flow,
                    userIdentifiers,
                    tenantId,
                    result.getReason());
              }
            });
  }
}
