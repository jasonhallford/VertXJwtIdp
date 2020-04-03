package io.miscellanea.vertx.example;

/**
 * Thrown when an error occurs during IDP request processing.
 *
 * @author Jason Hallford
 */
public class IdpException extends RuntimeException {
  // Constructors
  public IdpException(String message) {
    super(message);
  }

  public IdpException(String message, Throwable cause) {
    super(message, cause);
  }
}
