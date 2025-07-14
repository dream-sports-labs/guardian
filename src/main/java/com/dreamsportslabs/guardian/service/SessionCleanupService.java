package com.dreamsportslabs.guardian.service;

import static com.dreamsportslabs.guardian.constant.Constants.CACHE_KEY_CODE;
import static com.dreamsportslabs.guardian.constant.Constants.CACHE_KEY_STATE;
import static com.dreamsportslabs.guardian.constant.Constants.USERID;

import com.dreamsportslabs.guardian.dao.CodeDao;
import com.dreamsportslabs.guardian.dao.ContactVerifyDao;
import com.dreamsportslabs.guardian.dao.PasswordlessDao;
import com.dreamsportslabs.guardian.dao.model.CodeModel;
import com.dreamsportslabs.guardian.dao.model.OtpGenerateModel;
import com.dreamsportslabs.guardian.dao.model.PasswordlessModel;
import com.dreamsportslabs.guardian.registry.Registry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.redis.client.Command;
import io.vertx.rxjava3.redis.client.Redis;
import io.vertx.rxjava3.redis.client.Request;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SessionCleanupService {
  private final PasswordlessDao passwordlessDao;
  private final ContactVerifyDao contactVerifyDao;
  private final CodeDao codeDao;
  private final UserService userService;
  private final Registry registry;
  private final Redis redisClient;
  private final ObjectMapper objectMapper;

  /**
   * Comprehensive session cleanup for admin logout.
   * Clears all sessions related to the user including:
   * - Passwordless sessions
   * - Authorization codes
   * - Contact verification sessions (based on user's contacts)
   */
  public Completable cleanupAllUserSessions(String userId, String tenantId) {
    Map<String, String> userFilters = new HashMap<>();
    userFilters.put(USERID, userId);
    MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
    
    return userService
        .getUser(userFilters, headers, tenantId)
        .flatMapCompletable(
            user -> {
              List<Completable> cleanupTasks = new ArrayList<>();
              
              // Cleanup passwordless sessions
              cleanupTasks.add(cleanupPasswordlessSessions(userId, tenantId));
              
              // Cleanup authorization codes
              cleanupTasks.add(cleanupAuthorizationCodes(userId, tenantId));
              
              // Cleanup contact verification sessions based on user's contacts
              cleanupTasks.add(cleanupContactVerificationSessions(user, tenantId));
              
              return Completable.merge(cleanupTasks);
            })
        .onErrorComplete();
  }

  private Completable cleanupPasswordlessSessions(String userId, String tenantId) {
    return redisClient
        .rxSend(Request.cmd(Command.KEYS).arg(CACHE_KEY_STATE + "_" + tenantId + "_*"))
        .flatMapCompletable(
            response -> {
              List<Completable> deletions = new ArrayList<>();
              
              for (Object key : response) {
                String state = key.toString().substring((CACHE_KEY_STATE + "_" + tenantId + "_").length());
                
                deletions.add(
                    passwordlessDao
                        .getPasswordlessModel(state, tenantId)
                        .filter(model -> userId.equals(model.getUser().get(USERID)))
                        .flatMapCompletable(model -> {
                          passwordlessDao.deletePasswordlessModel(state, tenantId);
                          return Completable.complete();
                        })
                        .onErrorComplete());
              }
              
              return Completable.merge(deletions);
            })
        .onErrorComplete();
  }

  private Completable cleanupAuthorizationCodes(String userId, String tenantId) {
    return redisClient
        .rxSend(Request.cmd(Command.KEYS).arg(CACHE_KEY_CODE + "_" + tenantId + "_*"))
        .flatMapCompletable(
            response -> {
              List<Completable> deletions = new ArrayList<>();
              
              for (Object key : response) {
                String code = key.toString().substring((CACHE_KEY_CODE + "_" + tenantId + "_").length());
                
                deletions.add(
                    codeDao
                        .getCode(code, tenantId)
                        .filter(model -> userId.equals(model.getUser().get(USERID)))
                        .flatMapCompletable(model -> codeDao.deleteCode(code, tenantId))
                        .onErrorComplete());
              }
              
              return Completable.merge(deletions);
            })
        .onErrorComplete();
  }

  /**
   * Cleanup contact verification sessions that match user's contacts
   */
  private Completable cleanupContactVerificationSessions(JsonObject user, String tenantId) {
    List<Completable> deletions = new ArrayList<>();
    
    // Check for email-based sessions
    String email = user.getString("email");
    if (email != null) {
      deletions.add(cleanupContactSessionsByEmail(email, tenantId));
    }
    
    // Check for phone-based sessions
    String phone = user.getString("phoneNumber");
    if (phone != null) {
      deletions.add(cleanupContactSessionsByPhone(phone, tenantId));
    }
    
    return Completable.merge(deletions);
  }

  private Completable cleanupContactSessionsByEmail(String email, String tenantId) {
    return redisClient
        .rxSend(Request.cmd(Command.KEYS).arg(CACHE_KEY_STATE + "_otp_only_" + tenantId + "_*"))
        .flatMapCompletable(
            response -> {
              List<Completable> deletions = new ArrayList<>();
              
              for (Object key : response) {
                String state = key.toString().substring((CACHE_KEY_STATE + "_otp_only_" + tenantId + "_").length());
                
                deletions.add(
                    contactVerifyDao
                        .getOtpGenerateModel(tenantId, state)
                        .filter(model -> email.equals(model.getContact().getIdentifier()))
                        .flatMapCompletable(model -> {
                          contactVerifyDao.deleteOtpGenerateModel(tenantId, state);
                          return Completable.complete();
                        })
                        .onErrorComplete());
              }
              
              return Completable.merge(deletions);
            })
        .onErrorComplete();
  }

  private Completable cleanupContactSessionsByPhone(String phone, String tenantId) {
    return redisClient
        .rxSend(Request.cmd(Command.KEYS).arg(CACHE_KEY_STATE + "_otp_only_" + tenantId + "_*"))
        .flatMapCompletable(
            response -> {
              List<Completable> deletions = new ArrayList<>();
              
              for (Object key : response) {
                String state = key.toString().substring((CACHE_KEY_STATE + "_otp_only_" + tenantId + "_").length());
                
                deletions.add(
                    contactVerifyDao
                        .getOtpGenerateModel(tenantId, state)
                        .filter(model -> phone.equals(model.getContact().getIdentifier()))
                        .flatMapCompletable(model -> {
                          contactVerifyDao.deleteOtpGenerateModel(tenantId, state);
                          return Completable.complete();
                        })
                        .onErrorComplete());
              }
              
              return Completable.merge(deletions);
            })
        .onErrorComplete();
  }
} 