package io.miscellanea.vertx.example;

/**
 * An enum to store configuration property names.
 *
 * @author Jason Hallford
 */
public enum ConfigProps {
  IdpBindPort("bind-port");

  private final String name;

  ConfigProps(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return this.name;
  }
}
