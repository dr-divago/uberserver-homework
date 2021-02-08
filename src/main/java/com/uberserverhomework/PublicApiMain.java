package com.uberserverhomework;

import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PublicApiMain {

  private static final Logger logger = LoggerFactory.getLogger(PublicApiMain.class);

  public static void main(String... args) {
    Vertx vertx = Vertx.vertx();

    vertx.rxDeployVerticle(new PublicApiVerticle())
    .subscribe(
      ok -> logger.info("Public Api Service running on port {}", 8080),
      error -> logger.error("Error starting PublicApi {}", error)
    );
  }
}
