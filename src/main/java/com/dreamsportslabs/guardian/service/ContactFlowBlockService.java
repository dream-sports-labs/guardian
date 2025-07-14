package com.dreamsportslabs.guardian.service;

import static com.dreamsportslabs.guardian.constant.Constants.MILLIS_TO_SECONDS;

import com.dreamsportslabs.guardian.constant.BlockFlow;
import com.dreamsportslabs.guardian.dao.ContactFlowBlockDao;
import com.dreamsportslabs.guardian.dao.model.ContactFlowBlockModel;
import com.dreamsportslabs.guardian.dto.request.V1BlockContactFlowRequestDto;
import com.dreamsportslabs.guardian.dto.request.V1UnblockContactFlowRequestDto;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ContactFlowBlockService {
  private final ContactFlowBlockDao contactFlowBlockDao;

  public Completable blockContactFlows(V1BlockContactFlowRequestDto dto, String tenantId) {
    log.info("Blocking flows for contact: {} in tenant: {}", dto.getContact(), tenantId);

    List<ContactFlowBlockModel> models =
        dto.getBlockFlows().stream()
            .map(
                flowName ->
                    ContactFlowBlockModel.builder()
                        .tenantId(tenantId)
                        .contact(dto.getContact())
                        .flowName(flowName)
                        .reason(dto.getReason())
                        .unblockedAt(dto.getUnblockedAt())
                        .isActive(true)
                        .build())
            .toList();

    return contactFlowBlockDao
        .blockFlows(models)
        .doOnComplete(() -> log.info("Blocked flows saved successfully"))
        .doOnError(err -> log.error("Error while saving blocked flows: {}", err.getMessage(), err));
  }

  public Completable unblockContactFlows(V1UnblockContactFlowRequestDto dto, String tenantId) {
    log.info("Unblocking flows for contact: {} of tenant: {}", dto.getContact(), tenantId);

    return contactFlowBlockDao
        .unblockFlows(tenantId, dto.getContact(), dto.getUnblockFlows())
        .doOnComplete(() -> log.info("Flows unblocked successfully"))
        .doOnError(error -> log.error("Error during unblock: {}", error.getMessage(), error));
  }

  public Single<List<ContactFlowBlockModel>> getActiveFlowsBlockedForContact(
      String tenantId, String contact) {
    return contactFlowBlockDao
        .getActiveFlowBlocksByContact(tenantId, contact)
        .map(
            blocksList ->
                blocksList.stream().filter(this::isBlockActive).collect(Collectors.toList()));
  }

  public Single<ApiBlockCheckResult> checkApiBlockedWithReason(
      String tenantId, String contact, String apiPath) {
    String flowName = getFlowNameForApiPath(apiPath);
    if (flowName == null) {
      return Single.just(new ApiBlockCheckResult(false, null));
    }
    return contactFlowBlockDao
        .checkFlowBlockedWithReason(tenantId, contact, flowName)
        .map(result -> new ApiBlockCheckResult(result.isBlocked(), result.getReason()));
  }

  private String getFlowNameForApiPath(String apiPath) {
    return BlockFlow.getAllFlowNames().stream()
        .filter(flowName -> BlockFlow.fromString(flowName).getApiPaths().contains(apiPath))
        .findFirst()
        .orElse(null);
  }

  private boolean isBlockActive(ContactFlowBlockModel block) {
    long currentTimestamp = System.currentTimeMillis() / MILLIS_TO_SECONDS;
    return currentTimestamp < block.getUnblockedAt();
  }

  @Getter
  public static class ApiBlockCheckResult {
    private final boolean blocked;
    private final String reason;

    public ApiBlockCheckResult(boolean blocked, String reason) {
      this.blocked = blocked;
      this.reason = reason;
    }
  }
}
