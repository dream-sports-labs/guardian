package com.dreamsportslabs.guardian.dao.model;

import java.util.List;
import lombok.Data;

@Data
public class ScopeModel {
  private Integer id;
  private String tenantId;
  private String scope;
  private String displayName;
  private String description;
  private List<String> claims;
}
