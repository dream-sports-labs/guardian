package com.dreamsportslabs.guardian.dao;

import static com.dreamsportslabs.guardian.constant.Constants.MILLIS_TO_SECONDS;
import static com.dreamsportslabs.guardian.dao.query.ContactFlowBlockSql.GET_ACTIVE_FLOW_BLOCKS_BY_CONTACT;
import static com.dreamsportslabs.guardian.dao.query.ContactFlowBlockSql.UNBLOCK_CONTACT_FLOW;
import static com.dreamsportslabs.guardian.dao.query.ContactFlowBlockSql.UPSERT_CONTACT_FLOW_BLOCK;
import static com.dreamsportslabs.guardian.exception.ErrorEnum.INTERNAL_SERVER_ERROR;

import com.dreamsportslabs.guardian.client.MysqlClient;
import com.dreamsportslabs.guardian.dao.model.ContactFlowBlockModel;
import com.dreamsportslabs.guardian.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.redis.client.Command;
import io.vertx.rxjava3.redis.client.Redis;
import io.vertx.rxjava3.redis.client.Request;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @__({@Inject}))
public class ContactFlowBlockDao {

  private final MysqlClient mysqlClient;
  private final Redis redisClient;
  private final ObjectMapper objectMapper;

  private static final String REDIS_KEY_FORMAT = "contact_block:%s:%s:%s";

  private String buildRedisKey(String tenantId, String contact, String flowName) {
    return String.format(REDIS_KEY_FORMAT, tenantId, contact, flowName);
  }

  private int computeTTLSeconds(long unblockedAtEpochSeconds) {
    long nowSeconds = System.currentTimeMillis() / MILLIS_TO_SECONDS;
    long ttl = unblockedAtEpochSeconds - nowSeconds;
    return (int) Math.max(ttl, 1);
  }

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
                        .addValue(model.getOperator())
                        .addValue(model.getUnblockedAt())
                        .addValue(model.isActive()))
            .collect(Collectors.toList());

    return mysqlClient
        .getWriterPool()
        .preparedQuery(UPSERT_CONTACT_FLOW_BLOCK)
        .rxExecuteBatch(batchParams)
        .flatMapCompletable(rows -> cacheBlockedFlowsInRedis(models))
        .onErrorResumeNext(
            err -> {
              log.error("Failed to block contact flows", err);
              return Completable.error(INTERNAL_SERVER_ERROR.getException(err));
            });
  }

  private Completable cacheBlockedFlowsInRedis(List<ContactFlowBlockModel> models) {
    List<Request> requests = new ArrayList<>();

    for (ContactFlowBlockModel model : models) {
      try {
        String key = buildRedisKey(model.getTenantId(), model.getContact(), model.getFlowName());
        String json = objectMapper.writeValueAsString(model);
        int ttl = computeTTLSeconds(model.getUnblockedAt());

        Request request = Request.cmd(Command.SET).arg(key).arg(json).arg("EX").arg(ttl);
        requests.add(request);

      } catch (JsonProcessingException e) {
        log.warn("Skipping model due to serialization error: {}", model, e);
      }
    }

    if (requests.isEmpty()) {
      return Completable.complete();
    }

    return redisClient.batch(requests).ignoreElement();
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
        .flatMapCompletable(rows -> evictBlockedFlowsFromRedis(tenantId, contact, flowNames));
  }

  private Completable evictBlockedFlowsFromRedis(
      String tenantId, String contact, List<String> flowNames) {
    if (flowNames.isEmpty()) return Completable.complete();

    Request delRequest = Request.cmd(Command.DEL);
    flowNames.forEach(flowName -> delRequest.arg(buildRedisKey(tenantId, contact, flowName)));
    return redisClient.rxSend(delRequest).ignoreElement();
  }

  public Single<List<ContactFlowBlockModel>> getActiveFlowBlocksByContact(
      String tenantId, String contact) {
    return mysqlClient
        .getReaderPool()
        .preparedQuery(GET_ACTIVE_FLOW_BLOCKS_BY_CONTACT)
        .rxExecute(Tuple.of(tenantId, contact))
        .map(rows -> JsonUtils.rowSetToList(rows, ContactFlowBlockModel.class));
  }

  public Single<Boolean> isFlowBlocked(String tenantId, String contact, String flowName) {
    String redisKey = buildRedisKey(tenantId, contact, flowName);

    return redisClient
        .rxSend(Request.cmd(Command.EXISTS).arg(redisKey))
        .map(resp -> resp != null && resp.toInteger() == 1)
        .toSingle();
  }
}
