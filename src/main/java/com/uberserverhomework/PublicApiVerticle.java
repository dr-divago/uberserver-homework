package com.uberserverhomework;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.reactivex.Completable;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.client.predicate.ResponsePredicate;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class PublicApiVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(PublicApiVerticle.class);

  private WebClient webClient;

  private static final String TIME_ENDPOINT = "/v1/now";
  private static final String VIP_ENDPOINT = "/v1/VIP/:pointInTime";
  private static final String VIP_EXTERNAL_ENDPOINT = "/v1/coords/";

  private CircuitBreaker circuitBreaker;

  private Cache<String, JsonObject> pointInTimeCache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .build();

  @Override
  public Completable rxStart() {

    logger.info("Started");
    configureCircuitBreak();
    webClient = WebClient.create(vertx);

    Router router = Router.router(vertx);
    initRoute(router);

    return vertx.createHttpServer()
      .requestHandler(router)
      .rxListen(8080)
      .ignoreElement();
  }

  private void initRoute(Router router) {
    router.get(TIME_ENDPOINT).handler(this::getUnixTime);
    router.get(VIP_ENDPOINT).handler(this::pointInTime);
  }

  private void pointInTime(RoutingContext ctx) {
    String pointInTime = ctx.pathParam("pointInTime");
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
              cacheCoordinate(pointInTime, resp);
              returnResponseToClient(ctx, resp);
              promise.complete();
              },
            err -> {
              recoveryFromCache(ctx, pointInTime);
              promise.fail(err);
            }
            ),
      err -> {
        recoveryFromCache(ctx, pointInTime);
        return null;
    });
  }


  private void recoveryFromCache(RoutingContext ctx, String vipID) {
    logger.info("Error, trying to recover from the cache");
    JsonObject pointInTime = pointInTimeCache.getIfPresent(vipID);
    if (pointInTime == null) {
      logger.error("No cached data for pointInTime {}", vipID);
      ctx.fail(502);
    } else {
      logger.info("Found something in cache, return to client");
      transformAndReturn(ctx, pointInTime);
    }
  }


  private void cacheCoordinate(String vipID, HttpResponse<JsonObject> resp) {
    if (resp.statusCode() == 200) {
      logger.info("Caching coordinate");
      pointInTimeCache.put(vipID, resp.body());
    }
  }

  private void getUnixTime(RoutingContext ctx) {
    JsonObject resp = new JsonObject().put("now", Instant.now().toString());
    ctx.response()
      .putHeader("Content-Type", "application/json")
      .end(resp.encode());

  }

  private void returnResponseToClient(RoutingContext ctx, HttpResponse<JsonObject> resp) {
    if (resp.statusCode() != 200) {
      logger.info("Wrong status code return ", resp.statusCode());
      sendStatusCode(ctx, resp.statusCode());
    }
    else {
      transformAndReturn(ctx, resp.body());
    }
  }

  private void transformAndReturn(RoutingContext ctx, JsonObject pointInTime) {

    logger.info("Return the information to client");
    LatLong coordsObject = pointInTime.mapTo(LatLong.class);

    JsonObject respJson = new JsonObject()
      .put("source", "vip-db")
      .put("gpsCoords", coordsObject);

    ctx.response()
      .putHeader("Content-Type", "application/json")
      .end(respJson.encode());
  }

  private void sendStatusCode(RoutingContext ctx, int statusCode) {
    ctx.response().setStatusCode(statusCode).end();
  }

  private void configureCircuitBreak() {

    String tokenCircuitBreakerName = "token-circuit-breaker";
    circuitBreaker = CircuitBreaker.create(
      tokenCircuitBreakerName, vertx, new CircuitBreakerOptions()
        .setMaxFailures(5)
        .setMaxRetries(0)
        .setTimeout(5000)
        .setResetTimeout(10000))
      .openHandler(v -> logger.info("open", tokenCircuitBreakerName))
      .halfOpenHandler(v -> logger.info("half open",tokenCircuitBreakerName))
      .closeHandler(v -> logger.info("closed", tokenCircuitBreakerName));
  }
}
