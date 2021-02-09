package com.uberserverhomework.verticle;

import com.uberserverhomework.model.Time;
import com.uberserverhomework.model.UnixTime;
import io.reactivex.Completable;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class UnixTimeVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(UnixTimeVerticle.class);

    private Time time;

    public UnixTimeVerticle(Time unixTime) {
        time = unixTime;
    }

    @Override
    public Completable rxStart() {
        logger.info("Started");
        getVertx().eventBus().consumer("unix-time", this::getUnixTime);

        return Completable.complete();
    }

    private void getUnixTime(Message<JsonObject> message) {
        JsonObject unixTime = new JsonObject().put("now", time.getNow());
        message.reply(unixTime);
    }
}
