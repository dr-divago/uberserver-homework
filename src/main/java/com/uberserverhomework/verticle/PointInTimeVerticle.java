package com.uberserverhomework.verticle;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uberserverhomework.model.LatLong;
import io.reactivex.Completable;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.client.predicate.ResponsePredicate;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PointInTimeVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(PointInTimeVerticle.class);

    private CircuitBreaker circuitBreaker;
    private WebClient webClient;

    private static final String VIP_EXTERNAL_ENDPOINT = "/v1/coords/";

    private Cache<Integer, JsonObject> pointInTimeCache;

    @Override
    public Completable rxStart() {

        logger.info("Started");
        configureCircuitBreak();
        webClient = WebClient.create(vertx);

        pointInTimeCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .build();

        getVertx().eventBus().consumer("point-in-time", this::pointInTime);

        return Completable.complete();
    }

    public Completable rxStop() {
        pointInTimeCache.cleanUp();
        return Completable.complete();
    }

    private void pointInTime(Message<JsonObject> message) {

        Integer pointInTime = message.body().getInteger("pointInTime");
        String requestVipPath = VIP_EXTERNAL_ENDPOINT + pointInTime;

        circuitBreaker.<Void>executeWithFallback(promise ->
                webClient
                    .get(8088, "localhost", requestVipPath)
                    .putHeader("Content-Type", "application/json")
                    .as(BodyCodec.jsonObject())
                    .expect(ResponsePredicate.SC_OK)
                    .timeout(5000)
                    .rxSend()
                    .subscribe(
                        resp -> {
                            cacheCoordinate(pointInTime, resp.body());
                            transformAndReturn(message, resp.body());
                            promise.complete();
                        },
                        err -> {
                            recoveryFromCache(message, pointInTime);
                            promise.fail(err);
                        }
                    ),
            err -> {
                recoveryFromCache(message, pointInTime);
                return null;
            });
    }

    private void cacheCoordinate(Integer pointInTime, JsonObject resp) {
        logger.info("Caching coordinate");
        pointInTimeCache.put(pointInTime, resp);
    }

    private void transformAndReturn(Message<JsonObject> message, JsonObject pointInTime) {

        logger.info("Building json response..");
        LatLong coordsObject = pointInTime.mapTo(LatLong.class);

        JsonObject respJson = new JsonObject()
            .put("source", "vip-db")
            .put("gpsCoords", coordsObject);

        message.reply(respJson);
    }

    private void recoveryFromCache(Message<JsonObject> message, Integer pointInTime) {
        logger.info("Error connecting to external service! Trying to recover gps coords from the cache...");

        JsonObject pointInTimeJson = pointInTimeCache.getIfPresent(pointInTime);
        if (pointInTimeJson == null) {
            logger.error("No cached data for pointInTime {}", pointInTime);
            message.fail(502, "No cached data for " + pointInTime);
        } else {
            logger.info("Found something in cache, return to client");
            transformAndReturn(message, pointInTimeJson);
        }
    }

    private void configureCircuitBreak() {
        String pointInTimeCircuitBreakerName = "point-in-time-circuit-breaker";
        circuitBreaker = CircuitBreaker.create(
            pointInTimeCircuitBreakerName, vertx, new CircuitBreakerOptions()
                .setMaxFailures(5)
                .setMaxRetries(0)
                .setTimeout(5000)
                .setResetTimeout(10000))
            .openHandler(v -> logger.info("open", pointInTimeCircuitBreakerName))
            .halfOpenHandler(v -> logger.info("half open",pointInTimeCircuitBreakerName))
            .closeHandler(v -> logger.info("closed", pointInTimeCircuitBreakerName));
    }
}
