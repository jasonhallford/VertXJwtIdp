package io.miscellanea.vertx.example;

import io.miscellanea.vertx.example.IdpKeyLoader.KeyType;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtIssuerVerticle extends AbstractVerticle {
    // Fields
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtIssuerVerticle.class);

    private JWTAuth signer;

    // Constructors
    public JwtIssuerVerticle() {

    }

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

        // Load the signer key. This might take time so this bit must
        // not run on the event loop.
        vertx.executeBlocking(this::createJwtSigner, asyncResult -> {
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

            this.signer = JWTAuth.create(vertx, new JWTAuthOptions()
                    .addPubSecKey(new PubSecKeyOptions()
                            .setAlgorithm("RS256")
                            .setPublicKey(publicKey)
                            .setSecretKey(secretKey)
                    ));

            promise.complete();
        } catch (Exception e) {
            promise.fail(e);
        }
    }

    // Path handlers
    private void issueJwt(RoutingContext routingContext) {

    }
}
