package io.miscellanea.vertx.example;

import io.miscellanea.vertx.example.IdpKeyLoader.KeyType;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
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
import java.util.UUID;

public class JwtIssuerVerticle extends AbstractVerticle {
  // Fields
  private static final Logger LOGGER = LoggerFactory.getLogger(JwtIssuerVerticle.class);
  public static final String DEFAULT_TIMEZONE = "Z";
  public static final String DEFAULT_ALGORITHM = "RS256";
  public static final String CLIENT_ID_FORM_FIELD = "client_id";
  public static final String CLIENT_SECRET_FORM_FIELD = "client_secret";
  public static final String CONTENT_TYPE_HEADER = "Content-Type";
  public static final String JWT_WRAPPER_ACCESS_TOKEN = "access_token";
  public static final String JWT_WRAPPER_TOKEN_TYPE = "token_type";
  public static final String JWT_WRAPPER_EXPIRES_IN = "expires_in";
  public static final String MIME_TYPE_JSON = "application/json";

  private int bindPort;
  private String keyStorePath;
  private String keyStorePassword;
  private JWTAuth signer;
  private String signerAlgorithm;
  private String issuerClaim;
  private String issuerTimeZone;
  private int epiresIn;

  // Constructors
  public JwtIssuerVerticle() {}

  // Vert.x life-cycle management
  @Override
  public void start(Promise<Void> startPromise) {
    LOGGER.debug("Starting JWT Issuer verticle.");

    this.initializeClaimsAndIssuerConfiguration();
    var router = this.registerRoutes();

    // Load the signer key. This might take time so this bit must
    // not run on the event loop.
    vertx.executeBlocking(
        this::createJwtSigner,
        asyncResult -> {
          if (asyncResult.succeeded()) {
            // Configure the endpoint for TLS. This requires that we provide HTTP
            // options that identify the keystore and its password.
            var httpOpts =
                new HttpServerOptions()
                    .setSsl(true)
                    .setKeyStoreOptions(
                        new JksOptions()
                            .setPath(this.keyStorePath)
                            .setPassword(this.keyStorePassword));
            vertx
                .createHttpServer(httpOpts)
                .requestHandler(router)
                .listen(
                    this.bindPort,
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

  private Router registerRoutes() {
    // Create and initialize the router. This object directs web
    // requests to specific handlers based on URL pattern matching.
    var router = Router.router(vertx);

    // Add a body handler to all routes. If we forget to do this,
    // we won't be able to access the content of any POST methods!
    router.route("/api/oauth2*").handler(BodyHandler.create());

    // Add handler to issue client credential flow tokens
    router.post("/api/oauth2/token").handler(this::issueJwt);

    return router;
  }

  private void initializeClaimsAndIssuerConfiguration() {
    // Initialize issuer configuration properties
    this.bindPort = config().getInteger(ConfigProp.IDP_BIND_PORT);
    this.issuerTimeZone =
        config().getString(ConfigProp.IDP_TIME_ZONE) != null
            ? config().getString(ConfigProp.IDP_TIME_ZONE)
            : DEFAULT_TIMEZONE; // default to UTC
    this.signerAlgorithm =
        config().getString(ConfigProp.IDP_ALGORITHM) != null
            ? config().getString(ConfigProp.IDP_ALGORITHM)
            : DEFAULT_ALGORITHM; // RSA with SHA-256 hash

    // Initialize TLS configuration
    this.keyStorePath = config().getString(ConfigProp.KEY_STORE);
    if (this.keyStorePath == null) {
      throw new IdpException(
          "Required configuration element '"
              + ConfigProp.KEY_STORE
              + "' is missing; verticle will not deploy.");
    }

    this.keyStorePassword = config().getString(ConfigProp.KEY_STORE_PASSWORD);
    if (this.keyStorePassword == null) {
      throw new IdpException(
          "Required configuration element '"
              + ConfigProp.KEY_STORE_PASSWORD
              + "' is missing; verticle will not deploy.");
    }

    // Initialize properties affecting claim issuance.
    var claimsConfig = config().getJsonObject(ConfigProp.CLAIMS_CONFIGURATION);
    this.issuerClaim = claimsConfig.getString(ConfigProp.ISSUER_CLAIM);
    this.epiresIn = claimsConfig.getInteger(ConfigProp.CLAIM_EXPIRES_IN);
  }

  // Path handlers
  private void issueJwt(RoutingContext routingContext) {
    LOGGER.debug("Handling request to issue JWT token.");

    // Read the multipart form data
    MultiMap attributes = routingContext.request().formAttributes();
    if (attributes != null) {
      var authnRequest =
          new JsonObject()
              .put(MessageField.CLIENT_ID, attributes.get(CLIENT_ID_FORM_FIELD))
              .put(MessageField.CLIENT_SECRET, attributes.get(CLIENT_SECRET_FORM_FIELD));

      getVertx()
          .eventBus()
          .request(
              EventBusAddress.CLIENT_AUTHENTICATE,
              authnRequest,
              response -> {
                JsonObject authnResult = (JsonObject) response.result().body();
                if (authnResult.getBoolean("authn")) {
                  try {
                    JsonObject jwt = this.generateJwt(authnResult);
                    routingContext
                        .response()
                        .putHeader(CONTENT_TYPE_HEADER, MIME_TYPE_JSON)
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

    var tokenConfig = config().getJsonObject(ConfigProp.CLAIMS_CONFIGURATION);
    LOGGER.debug("claims-config = {}", tokenConfig.toString());

    // Generate the token and its JSON wrapper
    jwt.put(
        JWT_WRAPPER_ACCESS_TOKEN,
        this.signer.generateToken(
            this.generateTokenClaims(authnResult),
            new JWTOptions().setAlgorithm(this.signerAlgorithm)));
    jwt.put(JWT_WRAPPER_TOKEN_TYPE, "bearer");
    jwt.put(JWT_WRAPPER_EXPIRES_IN, this.epiresIn);

    LOGGER.debug("Issued jtw: {}", jwt.toString());

    return jwt;
  }

  private JsonObject generateTokenClaims(JsonObject authnResult) {
    var claims = new JsonObject();

    claims.put("iss", this.issuerClaim);
    claims.put("sub", authnResult.getString("subject"));

    // Generate the time--in UTC--for all date/time based claims.
    var now = ZonedDateTime.now(ZoneId.of(this.issuerTimeZone));
    claims.put("iat", now.toEpochSecond() / 1000); // Adjust ms to seconds
    claims.put("nbf", now.toEpochSecond() / 1000); // Adjust ms to seconds
    claims.put("jti", UUID.randomUUID().toString());
    claims.put(
        "exp",
        now.plusSeconds(this.epiresIn).toInstant().toEpochMilli() / 1000); // Adjust ms to seconds

    return claims;
  }
}
