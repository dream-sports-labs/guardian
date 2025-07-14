package com.dreamsportslabs.guardian.dao;

import static com.dreamsportslabs.guardian.dao.query.ContactFlowBlockSql.GET_ACTIVE_FLOW_BLOCKS_BY_CONTACT;
import static com.dreamsportslabs.guardian.dao.query.ContactFlowBlockSql.GET_FLOW_BLOCK_REASON;
import static com.dreamsportslabs.guardian.dao.query.ContactFlowBlockSql.UNBLOCK_CONTACT_FLOW;
import static com.dreamsportslabs.guardian.dao.query.ContactFlowBlockSql.UPSERT_CONTACT_FLOW_BLOCK;
import static com.dreamsportslabs.guardian.exception.ErrorEnum.INTERNAL_SERVER_ERROR;

import com.dreamsportslabs.guardian.client.MysqlClient;
import com.dreamsportslabs.guardian.dao.model.ContactFlowBlockModel;
import com.dreamsportslabs.guardian.utils.JsonUtils;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @__({@Inject}))
public class ContactFlowBlockDao {

  private final MysqlClient mysqlClient;

  public Completable blockFlows(List<ContactFlowBlockModel> models) {
    List<Tuple> batchParams =
        models.stream()
            .map(
                model ->
                    Tuple.tuple()
                        .addValue(model.getTenantId())
                        .addValue(model.getContact())
                        .addValue(model.getFlowName())
                        .addValue(model.getReason())
                        .addValue(model.getUnblockedAt())
                        .addValue(model.isActive()))
            .collect(Collectors.toList());

    return mysqlClient
        .getWriterPool()
        .preparedQuery(UPSERT_CONTACT_FLOW_BLOCK)
        .rxExecuteBatch(batchParams)
        .ignoreElement()
        .onErrorResumeNext(
            err -> {
              log.error("Failed to block contact flows", err);
              return Completable.error(INTERNAL_SERVER_ERROR.getException(err));
            });
  }

  public Completable unblockFlows(String tenantId, String contact, List<String> flowNames) {
    List<Tuple> batchParams =
        flowNames.stream()
            .map(flowName -> Tuple.of(tenantId, contact, flowName))
            .collect(Collectors.toList());

    return mysqlClient
        .getWriterPool()
        .preparedQuery(UNBLOCK_CONTACT_FLOW)
        .rxExecuteBatch(batchParams)
        .ignoreElement();
  }

  public Single<List<ContactFlowBlockModel>> getActiveFlowBlocksByContact(
      String tenantId, String contact) {
    return mysqlClient
        .getReaderPool()
        .preparedQuery(GET_ACTIVE_FLOW_BLOCKS_BY_CONTACT)
        .rxExecute(Tuple.of(tenantId, contact))
        .map(rows -> JsonUtils.rowSetToList(rows, ContactFlowBlockModel.class));
  }

  public Single<BlockCheckResult> checkFlowBlockedWithReason(
      String tenantId, String contact, String flowName) {
    return mysqlClient
        .getReaderPool()
        .preparedQuery(GET_FLOW_BLOCK_REASON)
        .rxExecute(Tuple.of(tenantId, contact, flowName))
        .map(
            rows -> {
              if (rows.size() > 0) {
                String reason = rows.iterator().next().getString("reason");
                return new BlockCheckResult(true, reason);
              }
              return new BlockCheckResult(false, null);
            });
  }

  @Getter
  public static class BlockCheckResult {
    private final boolean blocked;
    private final String reason;

    public BlockCheckResult(boolean blocked, String reason) {
      this.blocked = blocked;
      this.reason = reason;
    }
  }
}
