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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ScopeDao {

  private final MysqlClient mysqlClient;

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

  public Single<List<ScopeModel>> getOidcScopesByTenant(String tenantId) {
    return mysqlClient
        .getReaderPool()
        .preparedQuery(ScopeQuery.GET_OIDC_SCOPES_BY_TENANT_ID)
        .rxExecute(Tuple.of(tenantId))
        .map(this::mapToScopeList)
        .doOnSuccess(
            scopes -> log.debug("Retrieved {} OIDC scopes for tenant: {}", scopes.size(), tenantId))
        .doOnError(
            error -> log.error("Error retrieving OIDC scopes for tenant: {}", tenantId, error));
  }

  public Single<List<String>> getSupportedScopes(String tenantId) {
    return getOidcScopesByTenant(tenantId)
        .map(scopes -> scopes.stream().map(ScopeModel::getScope).collect(Collectors.toList()))
        .doOnSuccess(
            supportedScopes ->
                log.debug(
                    "Retrieved {} supported OIDC scopes for tenant: {}",
                    supportedScopes.size(),
                    tenantId));
  }

  public Single<List<String>> getSupportedClaims(String tenantId) {
    return getOidcScopesByTenant(tenantId)
        .map(
            scopes ->
                scopes.stream()
                    .flatMap(scope -> scope.getClaims().stream())
                    .distinct()
                    .collect(Collectors.toList()))
        .doOnSuccess(
            supportedClaims ->
                log.debug(
                    "Retrieved {} supported claims from OIDC scopes for tenant: {}",
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
    scope.setId(row.getInteger("id"));
    scope.setTenantId(row.getString("tenant_id"));
    scope.setScope(row.getString("scope"));
    scope.setDisplayName(row.getString("display_name"));
    scope.setDescription(row.getString("description"));
    scope.setIconUrl(row.getString("icon_url"));
    scope.setIsOidc(row.getBoolean("is_oidc"));

    // Parse claims JSON array
    JsonArray claimsArray = new JsonArray(row.getString("claims"));
    scope.setClaims(claimsArray.getList());

    return scope;
  }
}
