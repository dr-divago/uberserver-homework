package com.uberserverhomework;

import com.uberserverhomework.model.Time;
import com.uberserverhomework.verticle.UnixTimeVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class UnixTimeVerticleTest {
    private static final Logger logger = LoggerFactory.getLogger(UnixTimeVerticleTest.class);

    @Test
    public void whenRequestTime_ThenReturnNow(Vertx vertx, VertxTestContext testContext) {
        String now = Instant.now().toString();

        Time time = Mockito.mock(Time.class);
        Mockito.when(time.getNow()).thenReturn(now);

        vertx.deployVerticle(new UnixTimeVerticle(time), testContext.succeeding(id -> {

            logger.info("Sending message to UnixTimeVerticle");
            vertx.eventBus().<JsonObject>request("unix-time", new JsonObject(), new DeliveryOptions().setSendTimeout(5000), reply -> {
                if (reply.succeeded()) {
                    JsonObject resp = reply.result().body();

                    String nowTime = resp.getString("now");
                    assertThat(nowTime).isEqualTo(now);
                    testContext.completeNow();
                } else {
                    testContext.failNow(reply.cause());
                }
            });
        }));
    }
}
