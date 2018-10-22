package io.vertx.starter.wiki.database;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;

import java.util.HashMap;
import java.util.List;

/**
 * @author guohao
 * @date 2018-10-10 14:04
 */
@ProxyGen
public interface WikiDatabaseService {

    @Fluent
    WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> handler);

    @Fluent
    WikiDatabaseService fetchPage(String page, Handler<AsyncResult<JsonObject>> resultHandler);

    @Fluent
    WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler);

    @Fluent
    WikiDatabaseService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler);

    @Fluent
    WikiDatabaseService deletePage(int id, Handler<AsyncResult<Void>> resultHandler);

    @Fluent
    WikiDatabaseService fetchAllPagesData(Handler<AsyncResult<List<JsonObject>>> resultHandler);

    static WikiDatabaseService create(JDBCClient dbClient, HashMap<SqlQuery, String> sqlQueries, Handler<AsyncResult<WikiDatabaseService>> readyHandler) {
        return new WikiDatabaseServiceimpl(dbClient, sqlQueries, readyHandler);
    }

    static WikiDatabaseService createProxy(Vertx vertx, String address) {
        return new WikiDatabaseServiceVertxEBProxy(vertx, address);
    }
}
