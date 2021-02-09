package com.uberserverhomework.verticle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uberserverhomework.model.LatLong;
import io.reactivex.Completable;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.client.predicate.ResponsePredicate;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public class PointInTimeVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(PointInTimeVerticle.class);

    private CircuitBreaker circuitBreaker;
    private WebClient webClient;

    private static final String VIP_EXTERNAL_ENDPOINT = "/v1/coords/";

    private Cache<Integer, LatLong> pointInTimeCache;
    private ObjectMapper mapper;

    @Override
    public Completable rxStart() {

        logger.info("Started");
        configureCircuitBreak();
        webClient = WebClient.create(vertx);

        pointInTimeCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .build();

        mapper = new ObjectMapper();
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
                        validResponse -> {
                            manageResponse(validResponse, message, pointInTime);
                            promise.complete();
                        },
                        errResponse -> {
                            Optional<LatLong> maybeLatLong = recoverFromCache(message, pointInTime);
                            maybeLatLong.ifPresent( latLong -> {
                                Optional<JsonObject> maybeJson = transformToJsonObject().apply(latLong);
                                maybeJson.ifPresent( jsonObject -> message.reply(jsonObject));
                            });
                            promise.fail(errResponse);
                        }
                    ),
            err -> {
                recoverFromCache(message, pointInTime);
                return null;
            });
    }

    private void manageResponse(HttpResponse<JsonObject> resp, Message<JsonObject> message, Integer pointInTime ) {
        Optional<LatLong> maybeLatLong = validateResponse(resp.body()).or( () -> recoverFromCache(message, pointInTime) );

        maybeLatLong.ifPresent( latLong -> {
            Optional<JsonObject> jsonObject =
                cacheCoordinate()
                    .andThen(transformToJsonObject())
                    .apply(pointInTime, latLong);

            message.reply(jsonObject.get());
        } );
    }

    private Optional<LatLong> validateResponse(JsonObject body) {
        try {
            LatLong latLong = body.mapTo(LatLong.class);
            ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<LatLong>> violations = validator.validate(latLong);
            if (!violations.isEmpty()) {
                logger.error("Error validation Json {} violations: {}", body, violations.toString());
                return Optional.empty();
            }

            return Optional.of(latLong);
        } catch (Exception e) {
            logger.error("Invalid Json {} exception {}", body, e);
            return Optional.empty();
        }
    }

    private BiFunction<Integer, LatLong, LatLong> cacheCoordinate() {
        return (pointInTime, latLong) -> {
            logger.info("Caching coordinate");
            pointInTimeCache.put(pointInTime, latLong);
            return latLong;
        };
    }

    private Function<LatLong, Optional<JsonObject>> transformToJsonObject()  {
        return latLong -> {
            logger.info("Building json response..");

            try {
                String jsonInString = mapper.writeValueAsString(latLong);
                JsonObject respJson = new JsonObject()
                    .put("source", "vip-db")
                    .put("gpsCoords", jsonInString);

                return Optional.of(respJson);
            } catch (JsonProcessingException e) {
                logger.error(e.getMessage());
            }
            return Optional.empty();
        };
    }

    private Optional<LatLong> recoverFromCache(Message<JsonObject> message, Integer pointInTime) {
        logger.info("Error connecting to external service! Trying to recover gps coords from the cache...");

        LatLong latLong = pointInTimeCache.getIfPresent(pointInTime);
        if (latLong == null) {
            logger.error("No cached data for pointInTime {}", pointInTime);
            message.fail(502, "No cached data for " + pointInTime);
            return Optional.empty();
        }

        logger.info("Found something in cache, return to client");
        return Optional.of(latLong);
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
            .halfOpenHandler(v -> logger.info("half open", pointInTimeCircuitBreakerName))
            .closeHandler(v -> logger.info("closed", pointInTimeCircuitBreakerName));
    }
}
