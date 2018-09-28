package io.vertx.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;

public class MainVerticle extends AbstractVerticle {

  private static final
  private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages " +
    "(Id integer identity primary key, Name varchar(255) unique, Content clob)";
  private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?";
  private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
  private static final String SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?";
  private static final String SQL_ALL_PAGES = "select Name from Pages";
  private static final String SQL_DELETE_PAGE = "delete from Pages where Id = ?";

//  @Override
//  public void start() {
//    vertx.createHttpServer()
//        .requestHandler(req -> req.response().end("Hello Vert.x!"))
//        .listen(8080);
//  }


  @Override
  public void start(Future<Void> startFuture) {
    Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
    steps.setHandler(voidAR -> {
      if (voidAR.succeeded()) {
        startFuture.completer();
      } else {
        startFuture.fail(voidAR.cause());
      }
    });
  }

  private Future<Void> prepareDatabase() {
    Future<Void> future = Future.future();
    // ...
    return future;
  }

  private Future<Void> startHttpServer() {
    Future<Void> future = Future.future();
    // ...
    return future;

  }


}
