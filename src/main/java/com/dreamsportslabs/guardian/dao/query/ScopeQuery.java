package com.dreamsportslabs.guardian.dao.query;

public class ScopeQuery {

  public static final String GET_SCOPES_BY_TENANT_ID =
      "SELECT id, tenant_id, scope, display_name, description, claims "
          + "FROM scope "
          + "WHERE tenant_id = ? "
          + "ORDER BY scope";
}
