package io.miscellanea.vertx.example;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The example's main class. It deploys two verticles:
 * <ol>
 *     <li>An issuer verticle, responsible for issuing JWTs</li>
 *     <li>A authenticator verticle, responsible for authenticating client
 *     token requests</li>
 * </ol>
 *
 * @author Jason Hallford
 */
public class JwtIdpDeployer {
  // Fields
  private static final Logger LOGGER = LoggerFactory.getLogger(JwtIdpDeployer.class);

  // Main method
  public static void main(String[] args) {
    LOGGER.debug("Bootstrapping the Vert.x runtime...");
    var vertx = Vertx.vertx();
    LOGGER.debug("Vert.x runtime initialized.");

    // Configure the runtime so that it reads configuration in the following order:
    // 1. System properties specified as "-D" options on the command line
    // 2. OS environment variables (useful for Docker)
    // 3. The default configuration file included in the JAR
    var defaultStore =
        new ConfigStoreOptions()
            .setType("file")
            .setFormat("json")
            .setConfig(new JsonObject().put("path", "conf/issuer-config.json"));
    var systemPropsStore = new ConfigStoreOptions().setType("sys");
    var envVarStore = new ConfigStoreOptions().setType("env");

    var configRetrieverOpts =
        new ConfigRetrieverOptions()
            .addStore(defaultStore)
            .addStore(envVarStore)
            .addStore(systemPropsStore);

    // Deploy the verticles.
    ConfigRetriever.create(vertx, configRetrieverOpts)
        .getConfig(
            config -> {
              var json = config.result();

              // Deploy the credential manager
              var credOpts = new DeploymentOptions().setConfig(json);
              vertx.deployVerticle(AuthenticatorVerticle.class.getName(), credOpts);

              // Deploy the REST API
              var apiOpts = new DeploymentOptions().setConfig(json);
              vertx.deployVerticle(JwtIssuerVerticle.class.getName(), apiOpts);
            });
  }
}
