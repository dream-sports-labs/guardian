package com.dreamsportslabs.guardian.dto.request;

import static com.dreamsportslabs.guardian.exception.ErrorEnum.INVALID_REQUEST;

import com.dreamsportslabs.guardian.constant.Flow;
import com.dreamsportslabs.guardian.constant.ResponseType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class IdpConnectRequestDto {
  private String idProvider;
  private String identifier;
  private ResponseType responseType;
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

    if (responseType == null) {
      throw INVALID_REQUEST.getCustomException("Invalid response type");
    }

    if (flow == null) {
      throw INVALID_REQUEST.getCustomException("flow is required");
    }
  }
}
