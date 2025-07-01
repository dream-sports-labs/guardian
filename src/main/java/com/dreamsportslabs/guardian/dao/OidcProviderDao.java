package com.dreamsportslabs.guardian.dao;

import static com.dreamsportslabs.guardian.exception.ErrorEnum.INTERNAL_SERVER_ERROR;
import static com.dreamsportslabs.guardian.exception.ErrorEnum.PROVIDER_NOT_FOUND;

import com.dreamsportslabs.guardian.client.MysqlClient;
import com.dreamsportslabs.guardian.config.tenant.OidcProviderConfig;
import com.dreamsportslabs.guardian.dao.query.ConfigQuery;
import com.dreamsportslabs.guardian.utils.JsonUtils;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class OidcProviderDao {
  private final MysqlClient mysqlClient;

  public Single<OidcProviderConfig> getOidcProviderConfig(String tenantId, String providerName) {
    MySQLPool pool = mysqlClient.getReaderPool();

    return pool.preparedQuery(ConfigQuery.OIDC_PROVIDER_CONFIG)
        .rxExecute(Tuple.of(tenantId, providerName))
        .onErrorResumeNext(err -> Single.error(INTERNAL_SERVER_ERROR.getException(err)))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(Single.error(PROVIDER_NOT_FOUND.getException()))
        .map(rows -> JsonUtils.rowSetToList(rows, OidcProviderConfig.class).get(0));
  }
}
