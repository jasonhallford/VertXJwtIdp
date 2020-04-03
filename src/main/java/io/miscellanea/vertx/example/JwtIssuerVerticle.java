package io.miscellanea.vertx.example;

import io.miscellanea.vertx.example.IdpKeyLoader.KeyType;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.jwt.JWTOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class JwtIssuerVerticle extends AbstractVerticle {
  // Fields
  private static final Logger LOGGER = LoggerFactory.getLogger(JwtIssuerVerticle.class);

  private JWTAuth signer;
  private String issuerClaim;
  private String issuerTimeZone;
  private int epiresIn;

  // Constructors
  public JwtIssuerVerticle() {}

  // Vert.x life-cycle management
  @Override
  public void start(Promise<Void> startPromise) {
    LOGGER.debug("Starting JWT Issuer verticle.");

    // Create and initialize the router. This object directs web
    // requests to specific handlers based on URL pattern matching.
    var router = Router.router(vertx);

    // Add a body handler to all routes. If we forget to do this,
    // we won't be able to access the content of any POST methods!
    router.route("/api/oauth2*").handler(BodyHandler.create());

    // Add handler to issue client credential flow tokens
    router.post("/api/oauth2/token").handler(this::issueJwt);

    // Initalize claims
    var claimsConfig = config().getJsonObject("claims-config");
    this.issuerClaim =claimsConfig.getString("iss");
    this.epiresIn = claimsConfig.getInteger("expires-in");
    this.issuerTimeZone = config().getString("issuer-tz");

    // Load the signer key. This might take time so this bit must
    // not run on the event loop.
    vertx.executeBlocking(
        this::createJwtSigner,
        asyncResult -> {
          if (asyncResult.succeeded()) {
            // Initialize the web server and bring the verticle online.
            vertx
                .createHttpServer()
                .requestHandler(router)
                .listen(
                    config().getInteger(ConfigProps.IdpBindPort.toString()),
                    result -> {
                      if (result.succeeded()) {
                        LOGGER.debug("HTTP server started successfully.");
                        startPromise.complete();
                      } else {
                        LOGGER.error(
                            "Unable to start HTTP server. Reason: {}", result.cause().getMessage());
                        startPromise.fail(result.cause());
                      }
                    });
          } else {
            // Abort verticle initialization.
            startPromise.fail(asyncResult.cause());
          }
        });
  }

  // Verticle initialization
  private void createJwtSigner(Promise<Object> promise) {
    try {
      String secretKey = new IdpKeyLoader(KeyType.IdpPrivate, config()).loadKey();
      String publicKey = new IdpKeyLoader(KeyType.IdpPublic, config()).loadKey();

      this.signer =
          JWTAuth.create(
              vertx,
              new JWTAuthOptions()
                  .addPubSecKey(
                      new PubSecKeyOptions()
                          .setAlgorithm("RS256")
                          .setPublicKey(publicKey)
                          .setSecretKey(secretKey)));

      promise.complete();
    } catch (Exception e) {
      promise.fail(e);
    }
  }

  // Path handlers
  private void issueJwt(RoutingContext routingContext) {
    LOGGER.debug("Handling request to issue JWT token.");

    // Read the multipart form data
    MultiMap attributes = routingContext.request().formAttributes();
    if (attributes != null) {
      var authnRequest =
          new JsonObject()
              .put("client-id", attributes.get("client_id"))
              .put("client-secret", attributes.get("client_secret"));

      getVertx()
          .eventBus()
          .request(
              "client.authenticate",
              authnRequest,
              response -> {
                JsonObject authnResult = (JsonObject) response.result().body();
                if (authnResult.getBoolean("authn")) {
                  try {
                    JsonObject jwt = this.generateJwt(authnResult);
                    routingContext
                        .response()
                        .putHeader("Content-Type", "application/json")
                        .setStatusCode(200)
                        .end(jwt.toString());
                  } catch (Exception e) {
                    LOGGER.error("Unable to generate JWT.", e);
                    routingContext.response().setStatusCode(500).end();
                  }
                } else {
                  routingContext.response().setStatusCode(401).end();
                }
              });
    } else {
      routingContext.response().setStatusCode(400).end();
    }
  }

  private JsonObject generateJwt(JsonObject authnResult) {
    var jwt = new JsonObject();

    var tokenConfig = config().getJsonObject("claims-config");
    LOGGER.debug("claims-config = {}", tokenConfig.toString());

    // Generate the token and its JSON wrapper
    jwt.put(
        "access_token",
        this.signer.generateToken(
            this.generateTokenClaims(authnResult),
            new JWTOptions().setAlgorithm(tokenConfig.getString("algorithm"))));
    jwt.put("token_type", "bearer");
    jwt.put("expires_in", this.epiresIn);

    LOGGER.debug("Issued jtw: {}", jwt.toString());

    return jwt;
  }

  private JsonObject generateTokenClaims(JsonObject authnResult) {
    var claims = new JsonObject();

    claims.put("iss", this.issuerClaim);
    claims.put("sub",authnResult.getString("subject"));

    // Generate the time--in UTC--for all date/time based claims.
    var now = ZonedDateTime.now(ZoneId.of(this.issuerTimeZone));
    claims.put("iat", DateTimeFormatter.ISO_INSTANT.format(now));
    claims.put("nbf", DateTimeFormatter.ISO_INSTANT.format(now));
    claims.put("jti", UUID.randomUUID().toString());
    claims.put("exp", DateTimeFormatter.ISO_INSTANT.format(now.plusSeconds(this.epiresIn)));

    return claims;
  }
}
