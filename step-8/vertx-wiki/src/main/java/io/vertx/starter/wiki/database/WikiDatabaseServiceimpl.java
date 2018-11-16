package io.vertx.starter.wiki.database;



import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.jdbc.JDBCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;

/**
 * @author guohao
 * @date 2018-10-10 14:33
 */
public class WikiDatabaseServiceimpl implements WikiDatabaseService {
    public static final Logger LOGGER = LoggerFactory.getLogger(WikiDatabaseServiceimpl.class);

    private JDBCClient dbClient;
    private HashMap<SqlQuery, String> sqlQueries;

    public WikiDatabaseServiceimpl(io.vertx.reactivex.ext.jdbc.JDBCClient dbClient, HashMap<SqlQuery, String> sqlQueries,
                                   Handler<AsyncResult<WikiDatabaseService>> readyHandler) {
        this.dbClient = new JDBC;
        this.sqlQueries = sqlQueries;
        dbClient.rxGetConnection(car -> {
            if (car.failed()) {
                LOGGER.error("Could not open a database connection", car.cause());
                readyHandler.handle(Future.failedFuture(car.cause()));
            } else {
                SQLConnection connection = car.result();
                connection.execute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE), res -> {
                    connection.close();

                    if (res.failed()) {
                        LOGGER.error("Database preparation error", car.cause());
                        readyHandler.handle(Future.failedFuture(res.cause()));
                    } else {
                        readyHandler.handle(Future.succeededFuture(this));
                    }
                } );

            }
        });
    }

    @Override
    public WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> handler) {
        dbClient.rxQuery(sqlQueries.get(SqlQuery.ALL_PAGES))
            .flatMapPublisher(res -> {
                List<JsonArray> results = res.getResults();
                return Flowable.fromIterable(results);
            }).map(json -> json.getString(0))
            .sorted()
            .collect(JsonArray::new, JsonArray::add)
            .subscribe(SingleHelper.toObserver(handler));
        return this;
    }

    @Override
    public WikiDatabaseService fetchPage(String page, Handler<AsyncResult<JsonObject>> resultHandler) {
        dbClient.rxQuerySingleWithParams(sqlQueries.get(SqlQuery.GET_PAGE), new JsonArray().add(page))
            .

            , fetch -> {
            if (fetch.succeeded()) {
                JsonObject response = new JsonObject();
                ResultSet resultSet = fetch.result();
                if (resultSet.getNumRows() == 0) {
                    response.put("found", false);
                } else {
                    response.put("found", true);
                    JsonArray row = resultSet.getResults().get(0);
                    response.put("id", row.getInteger(0));
                    response.put("rawContent", row.getString(1));
                }
                resultHandler.handle(Future.succeededFuture(response));
            } else {
                LOGGER.error("Database query error", fetch.cause());
                resultHandler.handle(Future.failedFuture(fetch.cause()));
            }
        }
        return this;
    }

    @Override
    public WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler) {
        JsonArray data = new JsonArray().add(title).add(markdown);
        dbClient.updateWithParams(sqlQueries.get(SqlQuery.CREATE_PAGE), data, res -> {
            if (res.succeeded()) {
                resultHandler.handle(Future.succeededFuture());
            } else {
                LOGGER.error("Database query error", res.cause());
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });
        return this;
    }

    @Override
    public WikiDatabaseService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler) {
        JsonArray data = new JsonArray().add(markdown).add(id);
        dbClient.updateWithParams(sqlQueries.get(SqlQuery.SAVE_PAGE), data, res -> {
            if (res.succeeded()) {
                resultHandler.handle(Future.succeededFuture());
            } else {
                LOGGER.error("Database query error", res.cause());
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });
        return this;
    }

    @Override
    public WikiDatabaseService deletePage(int id, Handler<AsyncResult<Void>> resultHandler) {
        JsonArray data = new JsonArray().add(id);
        dbClient.updateWithParams(sqlQueries.get(SqlQuery.DELETE_PAGE), data, res -> {
            if (res.succeeded()) {
                resultHandler.handle(Future.succeededFuture());
            } else {
                LOGGER.error("Database query error", res.cause());
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });
        return this;
    }

    @Override
    public WikiDatabaseService fetchAllPagesData(Handler<AsyncResult<List<JsonObject>>> resultHandler) {
        dbClient.query(sqlQueries.get(SqlQuery.ALL_PAGES_DATA), res -> {
            if (res.succeeded()) {
                resultHandler.handle(Future.succeededFuture(res.result().getRows()));
            } else {
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });
        return this;
    }
}
