package com.uberserverhomework;

import com.uberserverhomework.verticle.PublicApiVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class PublicApiVerticleTest {

    @Test
    public void whenRequestIsOk_Then200Code(Vertx vertx, VertxTestContext testContext) {

        WebClient webClient = WebClient.create(vertx);
        vertx.deployVerticle(com.uberserverhomework.verticle.PublicApiVerticle.class.getName(), testContext.succeeding(id -> {

            vertx.eventBus().consumer("unix-time", message -> {
                JsonObject unixTime = new JsonObject().put("now", "ok");
                message.reply(unixTime);
            });
            webClient
                .get(8080, "localhost", "/v1/now")
                .expect(ResponsePredicate.SC_OK)
                .as(BodyCodec.jsonObject())
                .putHeader("Content-Type", "application/json")
                .send(testContext.succeeding(resp -> {
                    testContext.verify(() -> {
                        assertThat(resp.statusCode()).isEqualTo(200);
                        //assertThat(resp.body()).contains(new Map.Entry("Yo!"));
                        testContext.completeNow();
                    });
                }));
        }));
    }

    @Test
    public void  whenRequestIsTimeout_ThenError(Vertx vertx, VertxTestContext testContext) {

        WebClient webClient = WebClient.create(vertx);
        vertx.deployVerticle(com.uberserverhomework.verticle.PublicApiVerticle.class.getName(), testContext.succeeding(id -> {

            vertx.eventBus().consumer("unix-time", message -> {
                JsonObject unixTime = new JsonObject().put("now", "ok");
                vertx.setTimer(6000, idx -> message.reply(unixTime));
            });

            webClient
                .get(8080, "localhost", "/v1/now")
                .as(BodyCodec.jsonObject())
                .putHeader("Content-Type", "application/json")
                .send(testContext.succeeding(resp -> testContext.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(504);
                    //assertThat(resp.body()).contains(new Map.Entry("Yo!"));
                    testContext.completeNow();
                })));
        }));
    }

    @Test
    public void  whenRequestNoHandler_ThenError(Vertx vertx, VertxTestContext testContext) {

        WebClient webClient = WebClient.create(vertx);
        vertx.deployVerticle(com.uberserverhomework.verticle.PublicApiVerticle.class.getName(), testContext.succeeding(id -> {
            webClient
                .get(8080, "localhost", "/v1/now")
                .as(BodyCodec.jsonObject())
                .putHeader("Content-Type", "application/json")
                .send(testContext.succeeding(resp -> testContext.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(500);
                    //assertThat(resp.body()).contains(new Map.Entry("Yo!"));
                    testContext.completeNow();
                })));
        }));
    }
}
