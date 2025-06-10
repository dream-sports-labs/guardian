package com.dreamsportslabs.guardian.dao;

import com.dreamsportslabs.guardian.client.MysqlClient;
import com.dreamsportslabs.guardian.dao.model.ScopeModel;
import com.dreamsportslabs.guardian.dao.query.ScopeQuery;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ScopeDao {

  @Inject private MysqlClient mysqlClient;

  public Single<List<ScopeModel>> getScopesByTenant(String tenantId) {
    return mysqlClient
        .getReaderPool()
        .preparedQuery(ScopeQuery.GET_SCOPES_BY_TENANT_ID)
        .rxExecute(Tuple.of(tenantId))
        .map(this::mapToScopeList)
        .doOnSuccess(
            scopes -> log.debug("Retrieved {} scopes for tenant: {}", scopes.size(), tenantId))
        .doOnError(error -> log.error("Error retrieving scopes for tenant: {}", tenantId, error));
  }

  public Single<List<String>> getSupportedScopes(String tenantId) {
    return getScopesByTenant(tenantId)
        .map(scopes -> scopes.stream().map(ScopeModel::getScope).collect(Collectors.toList()))
        .doOnSuccess(
            supportedScopes ->
                log.debug(
                    "Retrieved {} supported scopes for tenant: {}",
                    supportedScopes.size(),
                    tenantId));
  }

  public Single<List<String>> getSupportedClaims(String tenantId) {
    return getScopesByTenant(tenantId)
        .map(
            scopes ->
                scopes.stream()
                    .flatMap(scope -> scope.getClaims().stream())
                    .distinct()
                    .collect(Collectors.toList()))
        .doOnSuccess(
            supportedClaims ->
                log.debug(
                    "Retrieved {} supported claims for tenant: {}",
                    supportedClaims.size(),
                    tenantId));
  }

  private List<ScopeModel> mapToScopeList(RowSet<Row> rowSet) {
    return StreamSupport.stream(rowSet.spliterator(), false)
        .map(this::mapRowToScope)
        .collect(Collectors.toList());
  }

  private ScopeModel mapRowToScope(Row row) {
    ScopeModel scope = new ScopeModel();
    scope.setTenantId(row.getString("tenant_id"));
    scope.setScope(row.getString("scope"));
    scope.setDisplayName(row.getString("display_name"));
    scope.setDescription(row.getString("description"));

    // Parse claims JSON array
    JsonArray claimsArray = new JsonArray(row.getString("claims"));
    scope.setClaims(claimsArray.getList());

    return scope;
  }
}
