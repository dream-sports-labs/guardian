package com.dreamsportslabs.guardian.dao.query;

public class OidcTokenQuery {
  public static final String SAVE_OIDC_REFRESH_TOKEN =
      """
      INSERT INTO oidc_refresh_token (
          tenant_id, client_id, user_id, refresh_token,
          refresh_token_exp, scope, device_name, ip
      ) VALUES (?, ?, ?, ?, ?, ?, ?, INET6_ATON(?))
      """;
}
