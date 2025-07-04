package com.dreamsportslabs.guardian.dto.request.scope;

import static com.dreamsportslabs.guardian.utils.ScopeUtil.validateClaims;

import java.util.List;
import lombok.Data;

@Data
public class UpdateScopeRequestDto {
  private String displayName;
  private String description;
  private List<String> claims;
  private String iconUrl;
  private Boolean isOidc;

  public void validate(String scopeName) {
    if (claims != null && !claims.isEmpty()) {
      validateClaims(scopeName, claims);
    }
  }
}
