package io.miscellanea.vertx.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class CredentialManagerVerticle extends AbstractVerticle {
  // Private inner class to hold individual client configuration.
  private class ClientConfig {
    // Fields
    private String secret;
    private JsonArray roles;

    // Constructors
    public ClientConfig(String secret, JsonArray roles) {
      this.secret = secret;
      this.roles = roles;
    }

    // Accessors
    public String getSecret() {
      return secret;
    }

    public JsonArray getRoles() {
      return roles;
    }
  }

  // Fields
  private static final Logger LOGGER = LoggerFactory.getLogger(CredentialManagerVerticle.class);
  private static final String CLIENT_SECRET = "secret";
  private static final String CLIENT_ID = "id";
  private static final String CLIENT_ROLES = "roles";

  private Map<String, ClientConfig> idToClient = new HashMap<>();

  // Constructors
  public CredentialManagerVerticle() {}

  // Vert.x life-cycle management
  @Override
  public void start() {
    LOGGER.debug("Starting credential management verticle.");

    this.registerClients();

    LOGGER.debug("Registering for credential events.");
    var bus = getVertx().eventBus();
    bus.consumer("client.authenticate", this::authenticateClient);
    LOGGER.debug("Event registration complete.");

    LOGGER.info("Successfully started credential management verticle.");
  }

  public void stop() {
    LOGGER.debug("Credential management verticle stopped.");
  }

  // Handler methods
  private void authenticateClient(Message<JsonObject> message) {
    var payload = message.body();

    var clientId = payload.getString("client-id");
    var clientSecret = payload.getString("client-secret");

    LOGGER.debug("Authenticating client {}.", clientId);

    var authnResult = new JsonObject().put("subject", clientId).put("authn", false);

    var config = this.idToClient.get(clientId);
    if (config != null) {
      if (clientSecret.equals(config.getSecret())) {
        LOGGER.debug("Successfully authenticated client {}.", clientId);
        authnResult.put("roles", config.getRoles());
        authnResult.put("authn", true);
      } else {
        LOGGER.warn(
            "Client {} attempted to authenticate with invalid credentials; request denied.",
            clientId);
      }
    } else {
      LOGGER.info("Unable to authenticate unknown client {}.", clientId);
    }

    message.reply(authnResult);
  }

  // Utility methods
  private void registerClients() {
    LOGGER.debug("Registering clients...");
    var config = config();
    var clients = config().getJsonArray("client-config");
    if (clients != null) {
      for (int idx = 0; idx < clients.size(); idx++) {
        var client = clients.getJsonObject(idx);
        if (client.containsKey(CLIENT_ID) && client.containsKey(CLIENT_SECRET)) {
          var clientId = client.getString(CLIENT_ID);
          var clientSecret = client.getString(CLIENT_SECRET);
          var clientRoles = client.getJsonArray(CLIENT_ROLES);

          this.idToClient.put(clientId, new ClientConfig(clientSecret, clientRoles));
          LOGGER.debug("Successfully registered client {}.", client);
        } else {
          LOGGER.warn("Clients must have 'client-id' and 'client-secret' fields.");
        }
      }
    } else {
      LOGGER.warn("No clients are registered!");
    }
  }
}
