package io.vertx.starter.wiki;

import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.starter.wiki.database.WikiDatabaseVerticle;

/**
 * @author guohao
 * @date 2018-10-09 15:50
 */
public class MainVerticle extends AbstractVerticle {
    @Override
    public void start(Future<Void> startFuture) {
        Single<String> dbVerticleDeploy = vertx.rxDeployVerticle(
            "io.vertx.starter.wiki.database.WikiDatabaseVerticle");
        dbVerticleDeploy.flatMap(id -> {
            Single<String> httpVerticleDeploy = vertx.rxDeployVerticle("io.vertx.starter.wiki.http.HttpServerVerticle",
                new DeploymentOptions().setInstances(2));
            return httpVerticleDeploy;
        }).subscribe(id -> startFuture.complete(), startFuture::fail);
    }

    //    @Override
//    public void start(Future<Void> startFuture) {
//        Future<String> dbVerticleDeployment = Future.future();
//        vertx.deployVerticle(new WikiDatabaseVerticle(), dbVerticleDeployment.completer());
//
//        dbVerticleDeployment.compose(id -> {
//            Future<String> httpVerticleDeployment = Future.future();
//            vertx.deployVerticle("io.vertx.starter.wiki.http.HttpServerVerticle",
//                new DeploymentOptions().setInstances(2),
//                httpVerticleDeployment.completer());
//
//            return httpVerticleDeployment;
//
//        }).setHandler(ar -> {
//            if (ar.succeeded()) {
//                startFuture.complete();
//            } else {
//                startFuture.fail(ar.cause());
//            }
//        });
//    }

}
