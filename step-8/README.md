## 使用RxJava进行反应式编程

**TIP**
> 相应的源代码位于指南仓库的`step-8`文件夹中。

到目前为止，我们已经使用基于回调的 API 探索了 Vert.x 栈的各个方面。它仅仅是可以工作，这种编程模型是许多语言的开发者都熟悉的。但是，它可能会变得有点单调乏味，特别是当你组合多个事件源或处理复杂的数据流时。

这正是 RxJava 的亮点，并且 Vert.x 与它无缝集成。

**NOTE**
>在本指南中，使用了 RxJava 2.x，但 Vert.x 也适用于 RxJava 1.x。RxJava 2.x 已经在Reactive-Streams 规范的基础上从头到尾完全重写。在[What’s different in 2.0](https://github.com/ReactiveX/RxJava/wiki/What%27s-different-in-2.0)维基页面了解更多信息。

### 启用 RxJava API

除基于回调的 API 外，Vert.x 模块提供了 "Rxified" API。要启用它，首先将`vertx-rx-java2`模块添加到 Maven POM 文件：

	<dependency>
	  <groupId>io.vertx</groupId>
	  <artifactId>vertx-rx-java2</artifactId>
	</dependency>

然后必须修改 verticle，让它们继承`io.vertx.reactivex.core.AbstractVerticle`而不是`io.vertx.core.AbstractVerticle`。这有什么不同呢？前一个类继承后者并公开一个`io.vertx.reactivex.core.Vertx`成员。

`io.vertx.reactivex.core.Vertx`定义了额外的`rxSomething(...)`方法，这些方法等同于基于回调的对应方法。

让我们来看看`MainVerticle`，以便在实践中更好地了解它的工作原理：
	Single<String> dbVerticleDeployment = vertx.rxDeployVerticle("io.vertx.guides.wiki.database.WikiDatabaseVerticle");

`rxDeploy`方法不将`Handler <AsyncResult <String>>`作为final 参数。相反，它返回`Single <String>`。

此外，调用此方法时操作不会启动。它在您订阅`Single`时开始。操作完成后，它会发射部署`id`或通过`Throwable`发出问题原因。

### 按顺序部署 verticle

要完成`MainVerticle`重构，我们必须确保部署操作按顺序被触发执行：

	dbVerticleDeployment
	  .flatMap(id -> { (1)
	
	    Single<String> httpVerticleDeployment = vertx.rxDeployVerticle(
	      "io.vertx.guides.wiki.http.HttpServerVerticle",
	      new DeploymentOptions().setInstances(2));
	
	    return httpVerticleDeployment;
	  })
	  .flatMap(id -> vertx.rxDeployVerticle("io.vertx.guides.wiki.http.AuthInitializerVerticle")) (2)
	  .subscribe(id -> startFuture.complete(), startFuture::fail); (3)

1. `flatMap`操作符将函数应用于`dbVerticleDeployment`的结果。这里它安排了`HttpServerVerticle`的部署。
2. 我们在这里使用了更短的 lambda 形式。
3. 执行订阅时操作开始。根据操作成功或出错，`MainVerticle`启动 future 完成或者失败。

### 部分 "Rxifying" HttpServerVerticle

如果您按顺序跟着指南操作，编写代码，那么你的`HttpServerVerticle`类仍然使用基于回调的 API。在你可以使用 RxJava API 自然地（即同时）执行异步操作之前，您需要重构`HttpServerVerticle`。

#### 导入 Vert.x 类的 RxJava 版本

	import io.vertx.guides.wiki.database.reactivex.WikiDatabaseService;
	import io.vertx.reactivex.core.AbstractVerticle;
	import io.vertx.reactivex.core.http.HttpServer;
	import io.vertx.reactivex.ext.auth.User;
	import io.vertx.reactivex.ext.auth.jdbc.JDBCAuth;
	import io.vertx.reactivex.ext.auth.jwt.JWTAuth;
	import io.vertx.reactivex.ext.jdbc.JDBCClient;
	import io.vertx.reactivex.ext.web.Router;
	import io.vertx.reactivex.ext.web.RoutingContext;
	import io.vertx.reactivex.ext.web.client.WebClient;
	import io.vertx.reactivex.ext.web.codec.BodyCodec;
	import io.vertx.reactivex.ext.web.handler.*;
	import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
	import io.vertx.reactivex.ext.web.templ.FreeMarkerTemplateEngine;


#### 在 "Rxified" vertx 实例上使用委托

当你拥有`io.vertx.reactivex.core.Vertx`实例,想调用方法获得`io.vertx.core.Vertx`实例时，请调用`getDelegate()`方法。在创建`WikiDatabaseService`实例时，需要调整 verticle 的`start()`方法：

	@Override
	public void start(Future<Void> startFuture) throws Exception {
	
	  String wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");
	  dbService = io.vertx.guides.wiki.database.WikiDatabaseService.createProxy(vertx.getDelegate(), wikiDbQueue);

### 并发执行授权查询

在前面的示例中，我们了解了如何使用 RxJava 操作符和Rxified Vert.x API 按顺序执行异步操作。但有时这种保证不是必需的，或者您只是出于性能原因希望它们并发运行。

`HttpServerVerticle`中的 JWT 令牌生成过程就是这种情况的一个很好的例子。要创建令牌，我们需要完成所有授权查询，但查询彼此独立：

	auth.rxAuthenticate(creds).flatMap(user -> {
	  Single<Boolean> create = user.rxIsAuthorized("create"); (1)
	  Single<Boolean> delete = user.rxIsAuthorized("delete");
	  Single<Boolean> update = user.rxIsAuthorized("update");
	
	  return Single.zip(create, delete, update, (canCreate, canDelete, canUpdate) -> { (2)
	    return jwtAuth.generateToken(
	      new JsonObject()
	        .put("username", context.request().getHeader("login"))
	        .put("canCreate", canCreate)
	        .put("canDelete", canDelete)
	        .put("canUpdate", canUpdate),
	      new JWTOptions()
	        .setSubject("Wiki API")
	        .setIssuer("Vert.x"));
	  });
	}).subscribe(token -> {
	  context.response().putHeader("Content-Type", "text/plain").end(token);
	}, t -> context.fail(401));

1. 创建三个Single对象，表示不同的授权查询。
2. 当三个操作成功完成时，`zip`操作符的回调将使用返回的结果调用。

### 查询数据库

#### 直接查询

经常需要单个数据库查询来响应用户的请求。对于这种简单的情况，`JDBCClient`提供了`rxQueryXXX`和`rxUpdateXXX`方法：

	String query = sqlQueries.get(SqlQuery.GET_PAGE_BY_ID);
	JsonArray params = new JsonArray().add(id);
	Single<ResultSet> resultSet = dbClient.rxQueryWithParams(query, params);

#### 使用数据库连接

当直接查询不适合时（例如，当多个查询必须参与同一事务时），您可以从池中获取数据库连接。您所要做的就是在`JDBCClient`上调用`rxGetConnection`：

	Single<SQLConnection> connection = dbClient.rxGetConnection();

该方法返回一个`Single <Connection>`，您可以使用`flatMapXXX`轻松转换它以执行SQL查询：

	connection.flatMapCompletable(conn -> conn.rxExecute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE)))

但是，如果不再可以访问`SQLConnection`引用，我们如何释放连接呢？一个简单方便的方法是在`doFinally`回调中调用`close`方法：

	private Single<SQLConnection> getConnection() {
	  return dbClient.rxGetConnection().flatMap(conn -> {
	    Single<SQLConnection> connectionSingle = Single.just(conn); (1)
	    return connectionSingle.doFinally(conn::close); (2)
	  });
	}
1. 获取连接后，我们将其包装成`Single`
2. 修改`Single`以在`doFinally`回调中调用`close`

现在，只要我们需要使用数据库连接，我们就调用`getConnection`。

### 缩小回调和RxJava之间的差距

有时，您可能需要将 RxJava 代码与基于回调的 API 混合使用。例如，服务代理接口只能使用回调定义，但实现使用Vert.x Rxified API。

在这种情况下，`io.vertx.reactivex.SingleHelper.toObserver`类可以将`Handler <AsyncResult <T>>`桥接为`RxJava SingleObserver <T>`：

	@Override
	public WikiDatabaseService fetchAllPagesData(Handler<AsyncResult<List<JsonObject>>> resultHandler) { (1)
	  dbClient.rxQuery(sqlQueries.get(SqlQuery.ALL_PAGES_DATA))
	    .map(ResultSet::getRows)
	    .subscribe(SingleHelper.toObserver(resultHandler));  (2)
	  return this;
	}

