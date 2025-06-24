package com.dreamsportslabs.guardian.dao;

import static com.dreamsportslabs.guardian.exception.ErrorEnum.INTERNAL_SERVER_ERROR;

import com.dreamsportslabs.guardian.client.MysqlClient;
import com.dreamsportslabs.guardian.config.tenant.OidcProviderConfig;
import com.dreamsportslabs.guardian.dao.query.ConfigQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class OidcProviderDao {
  private final MysqlClient mysqlClient;
  private final ObjectMapper objectMapper;

  public Maybe<OidcProviderConfig> getOidcProviderConfig(String tenantId, String providerName) {
    MySQLPool pool = mysqlClient.getReaderPool();

    return pool.preparedQuery(ConfigQuery.OIDC_PROVIDER_CONFIG)
        .rxExecute(Tuple.of(tenantId, providerName))
        .onErrorResumeNext(err -> Single.error(INTERNAL_SERVER_ERROR.getException(err)))
        .flatMapMaybe(this::mapToOidcProviderConfig);
  }

  private Maybe<OidcProviderConfig> mapToOidcProviderConfig(RowSet<Row> rowSet) {
    if (rowSet.size() == 0) {
      return Maybe.empty();
    }

    Row row = rowSet.iterator().next();
    try {
      OidcProviderConfig config =
          objectMapper.readValue(row.toJson().toString(), OidcProviderConfig.class);
      return Maybe.just(config);
    } catch (Exception e) {
      log.error("Error mapping OIDC provider config", e);
      return Maybe.error(INTERNAL_SERVER_ERROR.getException(e));
    }
  }
}
