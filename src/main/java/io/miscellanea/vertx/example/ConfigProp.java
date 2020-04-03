package io.miscellanea.vertx.example;

/**
 * Constants naming configuration properties.
 *
 * @author Jason Hallford
 */
public final class ConfigProp {
  public static final String KEYS = "keys";
  public static final String PRIVATE_KEY = "private";
  public static final String PUBLIC_KEY = "public";
  public static final String IDP_CONFIG_FILE = "idp-config-file";
  public static final String IDP_BIND_PORT = "bind-port";
  public static final String IDP_TIME_ZONE = "idp-timezone";
  public static final String IDP_ALGORITHM = "idp-algorithm";
  public static final String CLAIMS_CONFIGURATION = "claims-config";
  public static final String ISSUER_CLAIM = "iss";
  public static final String CLAIM_EXPIRES_IN = "expires-in";
  public static final String KEY_STORE = "idp-keystore";
  public static final String KEY_STORE_PASSWORD = "idp-keystore-password";
  public static final String CLIENT_CONFIG = "client-config";

  private ConfigProp() {
  }
}
