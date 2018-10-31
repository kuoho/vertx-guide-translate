package io.vertx.starter;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.impl.WebClientInternal;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author guohao
 * @date 2018-10-19 17:49
 */
@RunWith(VertxUnitRunner.class)
public class WebClientTest {

    private Vertx vertx;

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
    }

    @Test
    public void testClient(TestContext context) {
        Async async = context.async();

        vertx.createHttpServer().requestHandler(req ->
            req.response().putHeader("Content-Type", "text/plain").end("OK"))
            .listen(8080, context.asyncAssertSuccess(server -> {
                WebClient webClient = WebClient.create(vertx);

                webClient.get(8080, "localhost:8080", "/").send(ar -> {
                    if (ar.succeeded()) {
                        HttpResponse<Buffer> result = ar.result();
                        context.assertTrue(result.headers().contains("Content-Type"));
                        context.assertEquals("text/plain", result.getHeader("Content-Type"));
                        context.assertEquals("Ok", result.body().toString());
                        webClient.close();

                        async.complete();
                    } else {
                        async.resolve(Future.failedFuture(ar.cause()));
                    }
                });
            }));

    }
}
