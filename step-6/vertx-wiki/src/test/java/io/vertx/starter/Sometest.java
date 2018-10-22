package io.vertx.starter;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.starter.wiki.database.WikiDatabaseService;
import io.vertx.starter.wiki.database.WikiDatabaseVerticle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author guohao
 * @date 2018-10-12 20:19
 */
@RunWith(VertxUnitRunner.class)
public class Sometest {

    private Vertx vertx;
    private WikiDatabaseService service;

    @Before
    public void prepare(TestContext context) {
        vertx = Vertx.vertx();
        JsonObject conf = new JsonObject()
            .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:testdb;shutdown=true")
            .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);

        vertx.deployVerticle(WikiDatabaseVerticle::new, new DeploymentOptions().setConfig(conf),
            context.asyncAssertSuccess(s ->
                service = WikiDatabaseService.createProxy(vertx, WikiDatabaseVerticle.CONFIG_WIKIDB_QUEUE))
        );
    }

    @After
    public void finish(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void crud_operations(TestContext context) {
        Async async = context.async();
        service.createPage("Test", "Some content", context.asyncAssertSuccess(v1 -> {

            service.fetchPage("Test", context.asyncAssertSuccess(json1 -> {
                context.assertTrue(json1.getBoolean("found"));
                context.assertTrue(json1.containsKey("id"));
                context.assertEquals("Some content", json1.getString("rawContent"));

                service.savePage(json1.getInteger("id"), "Yo!", context.asyncAssertSuccess(v2 -> {

                    service.fetchAllPages(context.asyncAssertSuccess(array1 -> {
                        context.assertEquals(1, array1.size());

                        service.fetchPage("Test", context.asyncAssertSuccess(json2 -> {
                            context.assertEquals("Yo!", json2.getString("rawContent"));

                            service.deletePage(json1.getInteger("id"), v3 -> {

                                service.fetchAllPages(context.asyncAssertSuccess(array2 -> {
                                    context.assertTrue(array2.isEmpty());
                                    async.complete();
                                }));
                            });
                        }));
                    }));
                }));
            }));
        }));
        async.awaitSuccess(5000);
    }

    @Test
    public void asyncBehavior(TestContext context) {
        Vertx vertx = Vertx.vertx();
        context.assertEquals("foo", "foo");
        Async async1 = context.async();
        Async async2 = context.async(3);

        vertx.setTimer(1000, a -> {
            System.out.println("async1");
            ;
            async1.complete();
        });
        vertx.setPeriodic(2000, a -> {
            System.out.println("async2");
            ;
            async2.countDown();
        });
    }

}
