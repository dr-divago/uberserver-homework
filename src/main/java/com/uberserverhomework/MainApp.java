package com.uberserverhomework;

import com.uberserverhomework.model.UnixTime;
import com.uberserverhomework.verticle.PointInTimeVerticle;
import com.uberserverhomework.verticle.PublicApiVerticle;
import com.uberserverhomework.verticle.UnixTimeVerticle;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp {

    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);

    public static void main(String... args) {
        Vertx vertx = Vertx.vertx();

        vertx.rxDeployVerticle(new PublicApiVerticle())
            .subscribe(
                ok -> logger.info("Public Api Service running on port {}", 8080),
                error -> logger.error("Error starting PublicApi {}", error)
            );

        vertx.rxDeployVerticle(new PointInTimeVerticle())
            .subscribe(
                ok -> logger.info("PointInTimeVerticle running"),
                error -> logger.error("Error starting PointInTime {}", error)
            );

        vertx.rxDeployVerticle(new UnixTimeVerticle(new UnixTime()))
            .subscribe(
                ok -> logger.info("UnixTimeVerticle running"),
                error -> logger.error("Error starting UnixTimeVerticle {}", error)
            );
    }
}
