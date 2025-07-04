package com.dreamsportslabs.guardian.dto.request.scope;

import static com.dreamsportslabs.guardian.exception.ErrorEnum.INVALID_REQUEST;
import static com.dreamsportslabs.guardian.utils.ScopeUtil.validateClaims;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class CreateScopeRequestDto {
  private String name;
  private String displayName;
  private String description;
  private List<String> claims = new ArrayList<>();
  private String iconUrl;
  private Boolean isOidc = Boolean.FALSE;

  public void validate() {
    if (StringUtils.isBlank(name)) {
      throw INVALID_REQUEST.getCustomException("scope name is required");
    }

    validateClaims(name, claims);
  }
}
