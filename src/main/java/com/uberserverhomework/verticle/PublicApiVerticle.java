package com.uberserverhomework.verticle;

import io.reactivex.Completable;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PublicApiVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(PublicApiVerticle.class);

    private static final String TIME_ENDPOINT = "/v1/now";
    private static final String VIP_ENDPOINT = "/v1/VIP/:pointInTime";

    @Override
    public Completable rxStart() {

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

    private void getUnixTime(RoutingContext ctx) {
        initEventBus(ctx, "unix-time");
    }

    private void pointInTime(RoutingContext ctx) {
        initEventBus(ctx, "point-in-time");
    }

    private void initEventBus(RoutingContext ctx, String address) {
        getVertx().eventBus().<JsonObject>request(address, "", new DeliveryOptions().setSendTimeout(5000), reply -> {
            if (reply.succeeded()) {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(reply.result().body().encode());
            }
            else {
                if (reply.cause() instanceof ReplyException) {
                    ReplyException replyException = (ReplyException)reply.cause();
                    switch (replyException.failureType()) {
                        case TIMEOUT: {
                            //should never happen unless there is a bug in PointInTime
                            ctx.response().setStatusCode(504).end();
                            break;
                        }
                        case RECIPIENT_FAILURE: {
                            logger.error(replyException.getMessage());
                            ctx.response().setStatusCode(replyException.failureCode()).end();
                            break;
                        }
                        case NO_HANDLERS: {
                            ctx.response().setStatusCode(500).end();
                        }
                    }
                }
                else {
                    //it should never happen unless a bug of vertx
                    ctx.response().setStatusCode(500).end();
                }
            }
        });
    }
}
