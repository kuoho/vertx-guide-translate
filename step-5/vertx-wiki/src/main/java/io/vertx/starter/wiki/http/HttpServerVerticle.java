package io.vertx.starter.wiki.http;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.FreeMarkerTemplateEngine;
import io.vertx.starter.wiki.database.WikiDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * @author guohao
 * @date 2018-10-08 20:44
 */
public class HttpServerVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

    public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
    public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

    private String wikiDbQueue = "wikidb.queue";

    private WikiDatabaseService dbService;

    private WebClient webClient;

    @Override
    public void start(Future<Void> startFuture) {
        wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");

        dbService = WikiDatabaseService.createProxy(vertx, wikiDbQueue);

        webClient = WebClient.create(vertx, new WebClientOptions()
            .setSsl(true)
            .setUserAgent("vert-x3"));

        HttpServer httpServer = vertx.createHttpServer();

        Router router = Router.router(vertx);
        router.get("/").handler(this::indexHandler);
        router.get("/wiki/:page").handler(this::pageRenderingHandler);
        router.post().handler(BodyHandler.create());
        router.post("/save").handler(this::pageUpdateHandler);
        router.post("/create").handler(this::pageCreateHandler);
        router.post("/delete").handler(this::pageDeletionHandler);
        router.get("/backup").handler(this::backupHandler);
        int portNum = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8082);

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

    private void backupHandler(RoutingContext context) {
        dbService.fetchAllPagesData(reply -> {
            if (reply.succeeded()) {


                JsonArray filesObj = new JsonArray();
                reply.result().forEach(page -> {
                    JsonObject fileObj = new JsonObject();
                    fileObj.put("name", page.getString("NAME"));
                    fileObj.put("content", page.getString("CONTENT"));
                    filesObj.add(fileObj);
                });

                JsonObject payLoad = new JsonObject()
                    .put("files", filesObj)
                    .put("language", "plaintext")
                    .put("title", "vertx-wiki-backup")
                    .put("public", true);

                webClient.post(443, "snippets.glot.io", "/snippets")
                    .putHeader("Content-Type", "application/json")
                    .as(BodyCodec.jsonObject()) //
                    .sendJsonObject(payLoad, ar -> {
                        if (ar.succeeded()) {
                            HttpResponse<JsonObject> response = ar.result();
                            if (response.statusCode() == 200) {
                                String url = "https://glot.io/snippets/" + response.body().getString("id");
                                context.put("backup_gist_url", url);  // <6>
                                indexHandler(context);
                            } else {
                                StringBuilder message = new StringBuilder()
                                    .append("Could not backup the wiki: ")
                                    .append(response.statusMessage());
                                JsonObject body = response.body();
                                if (body != null) {
                                    message.append(System.getProperty("line.separator"))
                                        .append(body.encodePrettily());
                                }
                                LOGGER.error(message.toString());
                                context.fail(502);
                            }
                        } else {
                            context.fail(ar.cause());
                        }
                    });

            } else {
                context.fail(reply.cause());
            }
        });
    }

    private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create();

    private void pageDeletionHandler(RoutingContext context) {
        dbService.deletePage(Integer.valueOf(context.request().getParam("id")), reply -> {
            if (reply.succeeded()) {
                context.response().setStatusCode(303);
                context.response().putHeader("Location", "/");
                context.response().end();
            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void pageCreateHandler(RoutingContext context) {
        String pageName = context.request().getParam("name");
        String location = "/wiki/" + pageName;
        if (pageName == null || pageName.isEmpty()) {
            location = "/";
        }
        context.response().setStatusCode(303);
        context.response().putHeader("Location", location);
        context.response().end();
    }

    private void pageUpdateHandler(RoutingContext context) {
        String title = context.request().getParam("title");
        String markdown = context.request().getParam("markdown");

        Handler<AsyncResult<Void>> handler = reply -> {
            if (reply.succeeded()) {
                context.response().setStatusCode(303);
                context.response().putHeader("Location", "/wiki/" + title);
                context.response().end();
            } else {
                context.fail(reply.cause());
            }
        };

        if ("yes".equals(context.request().getParam("newPage"))) {
            dbService.createPage(title, markdown, handler);
        } else {
            dbService.savePage(Integer.valueOf(context.request().getParam("id")), markdown, handler);
        }

    }

    private static final String EMPTY_PAGE_MARKDOWN =
        "# A new page\n" +
            "\n" +
            "Feel-free to write in Markdown!\n";

    private void pageRenderingHandler(RoutingContext context) {
        String requestedPage = context.request().getParam("page");
        dbService.fetchPage(requestedPage, reply -> {
            if (reply.succeeded()) {

                JsonObject payLoad = reply.result();
                boolean found = payLoad.getBoolean("found");
                String rawContent = payLoad.getString("rawContent", EMPTY_PAGE_MARKDOWN);
                context.put("title", requestedPage);
                context.put("id", payLoad.getInteger("id", -1));
                context.put("newPage", found ? "no" : "yes");
                context.put("rawContent", rawContent);
                context.put("content", Processor.process(rawContent));
                context.put("timestamp", new Date().toString());

                templateEngine.render(context, "templates", "/page.ftl", ar -> {
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

    private void indexHandler(RoutingContext context) {
        DeliveryOptions options = new DeliveryOptions().addHeader("action", "all-pages");

        dbService.fetchAllPages(reply -> {
            if (reply.succeeded()) {
                context.put("title", "Wiki home");
                context.put("pages", reply.result().getList());
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
}
