package io.vertx.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.FreeMarkerTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author guohao
 * @date 2018-10-08 20:44
 */
public class HttpServerVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

  public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
  public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

  private String wikiDbQueue = "wikidb.queue";

  @Override
  public void start(Future<Void> startFuture) {
    wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");

    HttpServer httpServer = vertx.createHttpServer();

    Router router = Router.router(vertx);
    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler);
    router.post().handler(BodyHandler.create());
    router.post("/save").handler(this::pageUpdateHandler);
    router.post("/create").handler(this::pageCreateHandler);
    router.post("/delete").handler(this::pageDeletionHandler);

    int portNum = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);

    httpServer.requestHandler(router::accept).listen(portNum, ar -> {
      if (ar.succeeded()) {
        LOGGER.info("HTTP server running on port " + portNum);
        startFuture.complete();
      } else {
        LOGGER.error("Could not start a HTTP server", ar.cause());
        startFuture.fail(ar.cause());
      }
    });
  }

  private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create();

  private void pageDeletionHandler(RoutingContext context) {
    DeliveryOptions options = new DeliveryOptions().addHeader("action", "all-pages");

    vertx.eventBus().send(wikiDbQueue, new JsonObject(), options, reply -> {
      if (reply.succeeded()) {
        JsonObject body = (JsonObject) reply.result().body();
        context.put("title", "Wiki home");
        context.put("pages", body.getJsonArray("pages").getList());
        templateEngine.render(context, "templates", "/index.ftl", ar -> {
          if (ar.succeeded()) {
            context.response().putHeader("Content-Type", "text/html");
            context.response().end(ar.result());
          } else {
            context.fail(ar.cause());
          }
        });
      } else {
        context.fail(reply.cause());
      }
    });
  }

  private void pageCreateHandler(RoutingContext context) {
  }

  private void pageUpdateHandler(RoutingContext context) {
    
  }

  private void pageRenderingHandler(RoutingContext context) {
  }

  private void indexHandler(RoutingContext context) {

  }
}
