package com.dreamsportslabs.guardian.dao.query;

public class ScopeQuery {

  public static final String GET_OIDC_SCOPES_BY_TENANT_ID =
      "SELECT id, tenant_id, scope, display_name, description, icon_url, claims, is_oidc "
          + "FROM scope "
          + "WHERE tenant_id = ? AND is_oidc = true "
          + "ORDER BY scope";
}
