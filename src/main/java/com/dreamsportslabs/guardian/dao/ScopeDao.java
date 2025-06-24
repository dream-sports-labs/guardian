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

@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ScopeDao {

  private final MysqlClient mysqlClient;

  public Single<List<String>> getSupportedScopes(String tenantId) {
    return getOidcScopesByTenant(tenantId)
        .map(scopes -> scopes.stream().map(ScopeModel::getScope).collect(Collectors.toList()));
  }

  public Single<List<String>> getSupportedClaims(String tenantId) {
    return getOidcScopesByTenant(tenantId)
        .map(
            scopes ->
                scopes.stream()
                    .flatMap(scope -> scope.getClaims().stream())
                    .distinct()
                    .collect(Collectors.toList()));
  }

  private Single<List<ScopeModel>> getOidcScopesByTenant(String tenantId) {
    return mysqlClient
        .getReaderPool()
        .preparedQuery(ScopeQuery.GET_OIDC_SCOPES_BY_TENANT_ID)
        .rxExecute(Tuple.of(tenantId))
        .map(this::mapToScopeList);
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
    scope.setScope(row.getString("name"));
    scope.setDisplayName(row.getString("display_name"));
    scope.setDescription(row.getString("description"));
    scope.setIconUrl(row.getString("icon_url"));
    scope.setIsOidc(row.getBoolean("is_oidc"));

    Object claimsRaw = row.getValue("claims");
    scope.setClaims(claimsRaw == null ? List.of() : new JsonArray(claimsRaw.toString()).getList());
    return scope;
  }
}
