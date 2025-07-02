package com.dreamsportslabs.guardian.dto.request;

import static com.dreamsportslabs.guardian.exception.ErrorEnum.INVALID_REQUEST;

import com.dreamsportslabs.guardian.constant.Flow;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class IdpConnectRequestDto {
  private String idProvider;
  private String identifier;
  private String responseType;
  private String nonce;
  private String codeVerifier;
  private Flow flow;
  private MetaInfo metaInfo;

  public void validate() {
    if (StringUtils.isBlank(idProvider)) {
      throw INVALID_REQUEST.getCustomException("idProvider is required");
    }

    if (StringUtils.isBlank(identifier)) {
      throw INVALID_REQUEST.getCustomException("identifier is required");
    }

    if (StringUtils.isBlank(responseType)) {
      throw INVALID_REQUEST.getCustomException("responseType is required");
    }

    if (!"token".equals(responseType) && !"code".equals(responseType)) {
      throw INVALID_REQUEST.getCustomException("responseType must be 'token' or 'code'");
    }

    if (flow == null) {
      throw INVALID_REQUEST.getCustomException("flow is required");
    }
  }
}
