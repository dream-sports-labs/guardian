package com.dreamsportslabs.guardian.dao;

import static com.dreamsportslabs.guardian.constant.Constants.CACHE_KEY_STATE;
import static com.dreamsportslabs.guardian.constant.Constants.EXPIRY_OPTION_REDIS;
import static com.dreamsportslabs.guardian.constant.Constants.KEEP_TTL;
import static com.dreamsportslabs.guardian.constant.Constants.SET_IF_EXISTS;
import static com.dreamsportslabs.guardian.constant.Constants.SET_IF_NOT_EXISTS;
import static com.dreamsportslabs.guardian.exception.ErrorEnum.INTERNAL_SERVER_ERROR;

import com.dreamsportslabs.guardian.config.tenant.OtpConfig;
import com.dreamsportslabs.guardian.config.tenant.TenantConfig;
import com.dreamsportslabs.guardian.dao.model.PasswordlessModel;
import com.dreamsportslabs.guardian.registry.Registry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.redis.client.Command;
import io.vertx.rxjava3.redis.client.Redis;
import io.vertx.rxjava3.redis.client.Request;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class PasswordlessDao {
  private final Redis redisClient;
  private final ObjectMapper objectMapper;
  private final Registry registry;

  public Maybe<PasswordlessModel> getPasswordlessModel(String state, String tenantId) {
    return redisClient
        .rxSend(Request.cmd(Command.GET).arg(getCacheKey(tenantId, state)))
        .map(response -> objectMapper.readValue(response.toString(), PasswordlessModel.class));
  }

  @SneakyThrows
  public Single<PasswordlessModel> setPasswordlessModel(PasswordlessModel model, String tenantId) {
    OtpConfig otpConfig = registry.get(tenantId, TenantConfig.class).getOtpConfig();
    return redisClient
        .rxSend(
            Request.cmd(Command.SET)
                .arg(getCacheKey(tenantId, model.getState()))
                .arg(objectMapper.writeValueAsString(model))
                .arg(EXPIRY_OPTION_REDIS)
                .arg(otpConfig.getOtpValidity())
                .arg(SET_IF_NOT_EXISTS))
        .flatMap(
            response -> {
              if (response.toString().equals("OK")) {
                return Maybe.just(model);
              } else {
                return redisClient
                    .rxSend(
                        Request.cmd(Command.SET)
                            .arg(getCacheKey(tenantId, model.getState()))
                            .arg(objectMapper.writeValueAsString(model))
                            .arg(EXPIRY_OPTION_REDIS)
                            .arg(KEEP_TTL)
                            .arg(SET_IF_EXISTS))
                    .flatMap(response1 -> Maybe.just(model));
              }
            })
        .onErrorResumeNext(err -> Maybe.error(INTERNAL_SERVER_ERROR.getException(err)))
        .switchIfEmpty(Single.just(model));
  }

  public void deletePasswordlessModel(String state, String tenantId) {
    redisClient.rxSend(Request.cmd(Command.DEL).arg(getCacheKey(tenantId, state))).subscribe();
  }

  private String getCacheKey(String tenantId, String state) {
    return CACHE_KEY_STATE + "_" + tenantId + "_" + state;
  }
}
