package io.vertx.starter.wiki.database;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;

import java.util.HashMap;

/**
 * @author guohao
 * @date 2018-10-10 14:33
 */
public class WikiDatabaseServiceimpl implements WikiDatabaseService {
    
    private JDBCClient dbClient;
    private HashMap<SqlQuery, String> sqlQueries;
    private Handler<AsyncResult<WikiDatabaseService>> readyHandler;

    public WikiDatabaseServiceimpl(JDBCClient dbClient, HashMap<SqlQuery, String> sqlQueries,
                                   Handler<AsyncResult<WikiDatabaseService>> readyHandler) {
        this.dbClient = dbClient;
        this.sqlQueries = sqlQueries;
        this.readyHandler = readyHandler;
    }

    @Override
    public WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> handler) {
        return null;
    }

    @Override
    public WikiDatabaseService fetchPage(String page, Handler<AsyncResult<JsonObject>> resultHandler) {
        return null;
    }

    @Override
    public WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler) {
        return null;
    }

    @Override
    public WikiDatabaseService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler) {
        return null;
    }

    @Override
    public WikiDatabaseService deletePage(int id, Handler<AsyncResult<Void>> resultHandler) {
        return null;
    }
}
