package com.dreamsportslabs.guardian.service;

import static com.dreamsportslabs.guardian.exception.ErrorEnum.FLOW_BLOCKED;

import com.dreamsportslabs.guardian.dto.UserDto;
import com.dreamsportslabs.guardian.dto.request.V1SignInRequestDto;
import com.dreamsportslabs.guardian.dto.request.V1SignUpRequestDto;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.MultivaluedMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class PasswordAuth {
  private final UserService userService;
  private final AuthorizationService authorizationService;
  private final ContactFlowBlockService contactFlowBlockService;
  private final ContactExtractionService contactExtractionService;

  public Single<Object> signIn(
      V1SignInRequestDto dto, MultivaluedMap<String, String> headers, String tenantId) {

    List<String> contacts = contactExtractionService.extractContactsFromSignIn(dto);

    if (!contacts.isEmpty()) {
      String contact = contacts.get(0);
      return contactFlowBlockService
          .checkApiBlockedWithReason(tenantId, contact, "/v1/signin")
          .flatMap(
              result -> {
                if (result.isBlocked()) {
                  log.warn(
                      "Password signin flow is blocked for contact: {} in tenant: {} with reason: {}",
                      contact,
                      tenantId,
                      result.getReason());
                  return Single.error(FLOW_BLOCKED.getCustomException(result.getReason()));
                }
                return performSignIn(dto, headers, tenantId);
              });
    }
    return performSignIn(dto, headers, tenantId);
  }

  public Single<Object> signUp(
      V1SignUpRequestDto dto, MultivaluedMap<String, String> headers, String tenantId) {

    List<String> contacts = contactExtractionService.extractContactsFromSignUp(dto);

    if (!contacts.isEmpty()) {
      String contact = contacts.get(0);
      return contactFlowBlockService
          .checkApiBlockedWithReason(tenantId, contact, "/v1/signup")
          .flatMap(
              result -> {
                if (result.isBlocked()) {
                  log.warn(
                      "Password signup flow is blocked for contact: {} in tenant: {} with reason: {}",
                      contact,
                      tenantId,
                      result.getReason());
                  return Single.error(FLOW_BLOCKED.getCustomException(result.getReason()));
                }
                return performSignUp(dto, headers, tenantId);
              });
    }

    return performSignUp(dto, headers, tenantId);
  }

  private Single<Object> performSignIn(
      V1SignInRequestDto dto, MultivaluedMap<String, String> headers, String tenantId) {
    return userService
        .authenticate(
            UserDto.builder()
                .username(dto.getUsername())
                .password(dto.getPassword())
                .additionalInfo(dto.getAdditionalInfo())
                .build(),
            headers,
            tenantId)
        .flatMap(
            user ->
                authorizationService.generate(
                    user, dto.getResponseType(), dto.getMetaInfo(), tenantId));
  }

  private Single<Object> performSignUp(
      V1SignUpRequestDto dto, MultivaluedMap<String, String> headers, String tenantId) {
    return userService
        .createUser(
            UserDto.builder()
                .username(dto.getUsername())
                .password(dto.getPassword())
                .additionalInfo(dto.getAdditionalInfo())
                .build(),
            headers,
            tenantId)
        .flatMap(
            user ->
                authorizationService.generate(
                    user, dto.getResponseType(), dto.getMetaInfo(), tenantId));
  }
}
