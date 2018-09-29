## Vert.x 编写的最小可用的 wiki

我们将从第一次迭代，并以尽可能简单的代码开始，用 Vert.x 编写一个 wiki应用。下一次迭代将会引入更多优雅的代码以及适当的测试，我们将看到用Vert.x 进行快速原型设计是简单且实际的目标。

在此阶段，wiki将使用服务器端渲染 HTML 页面并通过 JDBC 连接进行数据持久化。为此，我们将使用以下库。

1. [Vert.x web](http://vertx.io/docs/vertx-web/java/ "Vert.x web") 虽然 Vert.x 核心库确实支持创建 HTTP 服务器，但它没有提供优雅的 API 来处理路由，和请求有效负载等。
2. [Vert.x JDBC client](http://vertx.io/docs/vertx-jdbc-client/java/ "Vert.x JDBC client") 通过 JDBC 提供异步 API。
3. [Apache FreeMarker](http://freemarker.org/) 用于渲染服务器端页面，因为它是一个简单的模板引擎。
4. [Txtmark](https://github.com/rjeschke/txtmark) 将Markdown 文本渲染为 HTML，允许在 Markdown 中编辑 Wik i页面。

### 引导一个 Maven 应用

本指南选择使用 Apache Maven 作为构建工具，主要是因为它与主要的集成开发环境非常好地集成。您可以同样地使用其它构建工具，如 Gradle。

Vert.x 社区提供了可以克隆的项目模板结构 [https://github.com/vert-x3/vertx-maven-starter](https://github.com/vert-x3/vertx-maven-starter)。由于您可能会使用 git 作为版本控制，最快的方式是克隆项目，删除下面的 .git/ 文件夹，然后创建一个新的 Git 仓库：

    git clone https://github.com/vert-x3/vertx-maven-starter.git vertx-wiki
	cd vertx-wiki
	rm -rf .git
	git init


该项目提供 verticle 例子和单元测试。你可以安全地删除 `src/` 下面的所有 `.java` 文件来破解wiki，但在此之前你应该测试项目是否能够构建并成功地运行：

	mvn package exec:java

您可能会注意到 Maven 项目的 `pom.xml` 做了两件有趣的事情：
1. 它使用 Maven Shade 插件创建一个包含所有必需依赖项的 Jar 存档，后缀为 `-fat.jar`，也称为"fat jar"
2. 它使用 Exec Maven 插件来提供 `exec：java` 目标，该目标依次通过 Vert.x `io.vertx.core.Launcher` 类启动应用程序。这实际上相当于使用 Vert.x 发行版中附带的 vertx 命令行工具运行。

最后，您将注意到 `redeploy.sh` 和 `redeploy.bat` 脚本的存在，您可以使用这些脚本在代码更改时自动编译和重新部署。请注意，这样做需要确保这些脚本中的 `VERTICLE` 变量与使用的 main Verticle 匹配。

**NOTE**

此外，Fabric8 项目托管了一个 [Vert.x Maven 插件](href="https://vmp.fabric8.io/) 。它的目标是初始化，构建，打包和运行 Vert.x 项目。

通过克隆 Git starter 仓库来生成类似的项目：

	mkdir vertx-wiki
	cd vertx-wiki
	mvn io.fabric8:vertx-maven-plugin:1.0.13:setup -DvertxVersion=3.5.2
	git init

### 添加必要的依赖

第一批要添加到 Maven `pom.xml` 文件的依赖项是用于 Web 处理和渲染：

	<dependency>
	  <groupId>io.vertx</groupId>
      <artifactId>vertx-web</artifactId>
	</dependency>
	<dependency>
      <groupId>io.vertx</groupId>
      <artifactId>vertx-web-templ-freemarker</artifactId>
	</dependency>
	<dependency>
	  <groupId>com.github.rjeschke</groupId>
	  <artifactId>txtmark</artifactId>
	  <version>0.13</version>
	</dependency>

**TIP**
正如 `vertx-web-templ-freemarker` 名称所示，Vert.x web 为流行的模板引擎都提供了可插拔的支持：Handlebars，Jade，MVEL，Pebble，Thymeleaf，当然还有 Freemarker。

第二组依赖项是 JDBC 数据库访问所需的依赖项：

	<dependency>
      <groupId>io.vertx</groupId>
	  <artifactId>vertx-jdbc-client</artifactId>
	</dependency>
	<dependency>
	  <groupId>org.hsqldb</groupId>
	  <artifactId>hsqldb</artifactId>
	  <version>2.3.4</version>
	</dependency>

Vert.x JDBC 客户端库提供对任何 兼容 JDBC 的数据库的访问，但当然我们的项目需要在类路径上有一个 JDBC 驱动程序。

[HSQLDB]() 是著名的用 Java 编写的关系型数据库。非常流行的是，它被用作嵌入式数据，以避免单独运行第三方数据库服务器。它在单元和集成测试中也很受欢迎，因为它提供了（易失性）内存存储。

HSQLDB 作为嵌入式数据库非常适合我们入门。它将数据存储在本地文件中，并且由于 HSQLDB 库Jar包提供了 JDBC 驱动程序，因此 Vert.x  的 JDBC 配置将非常简单。

**NOTE**
> Vert.x 同样提供专用的 [MySQL 和 PostgreSQL]("http://vertx.io/docs/vertx-mysql-postgresql-client/java/") 客户端库。

> 当然，您可以使用通用的 Vert.x JDBC 客户端连接到 MySQL 或       PostgreSQL 数据库，但这些库通过使用这两个数据库的服务器网络协议而不是通过（阻塞）JDBC API 来提供更好的性能。

>Vert.x 还提供了处理流行的非关系型数据库 [MongoDB](href="http://vertx.io/docs/vertx-mongo-client/java/") 和 [Redis](href="http://vertx.io/docs/vertx-redis-client/java/") 的库。庞大的社区提供了与 Apache Cassandra，OrientDB 或 ElasticSearch 等其他存储系统的集成。

### verticle 剖析

我们的 wiki 应用的 verticle 由单个 `io.vertx.guides.wiki.MainVerticle` Java类组成。这个类继承自 `io.vertx.core.AbstractVerticle`，它是 verticles 的基类，主要提供：

1. 需要覆盖的生命周期 `stop` 和 `stop` 方法
2. 一个名为 `vertx` 的 `protect` 字段，它引用了正部署 verticle 的 Vert.x 环境
3. 一个配置对象的访问器，允许向 verticle 传递一些外部配置。

我们要开始一个 verticle， 只需覆盖 `start` 方法，如下所示：

	public class MainVerticle extends AbstractVerticle {

	  @Override
	  public void start(Future<Void> startFuture) throws Exception {
	    startFuture.complete();
	  }
	}

有两种形式的 `start`（和 `stop` ）方法：一个没有参数，另一个有 `future` 对象引用。无参数的变体意味着除非抛出异常，否则初始化或整理阶段总是成功的。具有 *future* 对象的方法提供了更细粒度的方式来表明最终操作成功与否。实际上，一些初始化或清理代码的操作可能需要异步操作，因此通过 *future* 对象进行报告顺理成章地适合异步的习惯用法。

### 关于 Vert.x future 对象和回调的几句话

Vert.x futures 不是 JDK 的 futures：它们可以互相组合并以非阻塞的方式查询。它们应该用于异步任务的简单协作，尤其是部署 verticle并检查它们是否已成功部署。

Vert.x 核心 API 基于异步事件通知的回调。有经验的开发人员自然会想到这为所谓的“回调地狱”打开了一扇门，其中的多层嵌套回调使代码难以理解，正如下面这个虚构的代码所示：

	foo.a(1, res1 -> {
	  if (res1.succeeded()) {
	    bar.b("abc", 1, res2 -> {
	      if (res.succeeded()) {
	         baz.c(res3 -> {
	           dosomething(res1, res2, res3, res4 -> {
	               // (...)
	           });
	         });
	      }
	    });
	  }
	});

虽然核心 API 本来可以设计成支持 promises 和 futures，但选择回调实际上很有意思，因为它允许使用不同的编程抽象。Vert.x 基本上是一个不带有偏向的项目，回调允许不同模型的实现来更好地应对异步编程：反应式扩展（通过 RxJava）， promises 和 futures，fibers （使用 bytecode instrumentation）等等。

虽然所有的 Vert.x API 在其他抽象（如RxJava）被利用之前都是面向回调的，但本指南在第一部分中只会使用回调，以确保读者熟悉 Vert.x 中的核心概念。从回调作为开始，来区分异步代码的各部分也可以说更容易。一旦在示例代码中，回调明显不会使得代码变得易于阅读的时，我们将引入 RxJava 的支持，用以展示如何通过以处理事件流的方式进行思考，来更好地表示相同的异步代码。

### Wiki verticle 初始化阶段

为了让我们的 wiki 应用运行，我们需要执行两个阶段的初始化：
1. 我们需要建立一个 JDBC 数据库连接，并确保数据库模式到位
2. 并且我们需要为 Web 应用启动一个 HTTP 服务器。

每个阶段都可能会失败（例如，HTTP 服务器 TCP 端口已经被占用），而且它们不应该并行运行，因为 Web 应用程序代码首先需要能正常访问数据库。

为了使我们的代码更清晰，我们将为每个阶段定义1个方法，并采用返回 *future / promise* 对象来通知每个阶段的完成，以及是否成功的模式：

	private Future<Void> prepareDatabase() {
	  Future<Void> future = Future.future();
	  // (...)
	  return future;
	}
	
	private Future<Void> startHttpServer() {
	  Future<Void> future = Future.future();
	  // (...)
	  return future;
	}

通过让每个方法返回一个*future*对象，start 方法的实现变成了方法组合：

	@Override
	public void start(Future<Void> startFuture) throws Exception {
	  Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
	  steps.setHandler(startFuture.completer());
	}

当 `prepareDatabase` 的 *future* 成功完成时，将调用 `startHttpServer`， future 对象 `steps` 依赖于`startHttpServer` 返回的 future 的结果。如果 `prepareDatabase` 遇到错误，则 `startHttpServer` 永远不会被调用，在这种情况下，`steps` 处于fail状态，会有一个描述错误的异常并完成。

最后 `steps` 完成：`setHandler`定义了一个在完成时被调用的处理程序。在我们的例子中，我们只想用 `steps` 完成 `startFuture` ,并使用 `completer` 方法来获取一个handler。这相当于：

	Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
	steps.setHandler(ar -> {  ①
	  if (ar.succeeded()) {
	    startFuture.complete();
	  } else {
	    startFuture.fail(ar.cause());
	  }
	});

①`ar`的类型是 `AsyncResult <Void>`。`AsyncResult <T>` 用于传递异步处理的结果，并且可以在成功时产生`T`类型的值，或者在处理失败时产生失败的异常。

#### 数据库初始化

wiki 应用数据库结构由包含以下列的一个单表 `Pages` 组成：

|Column|Type      |Description|
|------|----------|-----------|
|`Id`  |Integer   |Primary key|
|`Name`|Characters|Name of a wiki page, must be unique|
|`Content`|Text   |Markdown text of a wiki page|

数据库操作将是典型的创建，读取，更新，删除操作。为了让我们开始，我们将相应的SQL查询简单存储为MainVerticle类的静态字段。请注意，它们是用HSQLDB理解的SQL方言编写的，但其他关系数据库可能不一定支持：

    private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)";
	private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?"; (1)
	private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
	private static final String SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?";
	private static final String SQL_ALL_PAGES = "select Name from Pages";
	private static final String SQL_DELETE_PAGE = "delete from Pages where Id = ?";

>1.在查询语句中`?`是占位符，在执行查询时传递数据，Vert.x JDBC 客户端会阻止 SQL 注入。

应用程序的 verticle 需要持有作为数据库连接服务的`JDBCClient`对象（来自`io.vertx.ext.jdbc`包）的引用。我们使用`MainVerticle`中的一个字段来实现，我们还从 org.slf4j 包创建了一个通用记录器：

	private JDBCClient dbClient;

	private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

最后，这是`prepareDatabase`方法的完整实现。它尝试获取 JDBC 客户端连接，然后执行 SQL 查询语句以创建`Pages`表，除非它已经存在：

	private Future<Void> prepareDatabase() {
	  Future<Void> future = Future.future();
	
	  dbClient = JDBCClient.createShared(vertx, new JsonObject()  (1)
	    .put("url", "jdbc:hsqldb:file:db/wiki")   (2)
	    .put("driver_class", "org.hsqldb.jdbcDriver")   (3)
	    .put("max_pool_size", 30));   (4)
	
	  dbClient.getConnection(ar -> {    (5)
	    if (ar.failed()) {
	      LOGGER.error("Could not open a database connection", ar.cause());
	      future.fail(ar.cause());    (6)
	    } else {
	      SQLConnection connection = ar.result();   (7)
	      connection.execute(SQL_CREATE_PAGES_TABLE, create -> {
	        connection.close();   (8)
	        if (create.failed()) {
	          LOGGER.error("Database preparation error", create.cause());
	          future.fail(create.cause());
	        } else {
	          future.complete();  (9)
	        }
	      });
	    }
	  });
	
	  return future;
	}

1. `createShared`创建了一个共享连接，可以在 `vertx` 实例已知的verticle 之间共享，这通常是一件好事。
2. JDBC 客户端连接是通过传递一个 Vert.x JSON 对象完成的。这里`url`是JDBC URL。
3. 就像`url`一样，`driver_class` 指定正在使用的 JDBC 驱动程序并指向驱动程序类。
4. `max_pool_size`是并发连接数。我们在这里选择了30，它只是一个随意数字。
5. 获取连接是一个异步操作，它给我们提供了`AsyncResult <SQLConnection>`。必须对其进行测试以确定是否可以建立连接（`AsyncResult`实际上是`Future`的 super-interface）。 
6. 如果无法获得SQL连接，*future* 通过`fail`方法完成，`AsyncResult` 通过`cause`方法提供了失败的异常原因。
7. `SQLConnection`是成功的`AsyncResult`返回的结果。我们可以用它来执行 SQL 查询。
8. 在检查 SQL 查询是否成功之前，我们必须通过调用`close`来释放它，否则JDBC客户端连接池最终会耗尽。
9. 我们通过调用*future*对象的 success 方法完成了 `prepareDatabase`。

**TIP**
>Vert.x 项目支持的SQL数据库模块目前不提供通过 SQL 查询之外的任何内容（例如，对象关系映射器），因为它们专注于提供对数据库的异步访问。但是，没有任何禁止使用[来自社区的更高级模块](https://github.com/vert-x3/vertx-awesome)，我们特别推荐尝试下像[this jOOq generator for Vert.x](https://github.com/jklingsporn/vertx-jooq)或者[this POJO mapper](https://github.com/BraintagsGmbH/vertx-pojo-mapper)等项目。

#### 关于日志
前一小节引入了一个 logger，我们选择了[SLF4J](https://www.slf4j.org/)库。Vert.x在日志记录方面也是自由的，不会倾向于某一特定的库：您可以选择任何流行的 Java 日志库。我们推荐 SLF4J，因为它是 Java 生态系统中流行的日志记录抽象和统一库。

我们同时也推荐使用[Logback](https://logback.qos.ch/)作为 logger 实现。可以通过添加两个依赖项来完成 SLF4J 和 Logback 的集成，或者只是添加 logback-classic，它同时添加这两个库的依赖（顺便说一下，它们来自同一个作者）：

	<dependency>
	  <groupId>ch.qos.logback</groupId>
	  <artifactId>logback-classic</artifactId>
	  <version>1.2.3</version>
	</dependency>

默认情况下，SLF4J 会向控制台输出来自 Vert.x, Netty, C3PO 和 wiki 应用的许多日志事件。我们可以通过添加`src/main/resources/logback.xml`配置文件来减少不必要的冗余信息（有关详细信息，请参阅[https://logback.qos.ch/](https://logback.qos.ch/)）：

	<configuration>

	  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
	    <encoder>
	      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
	    </encoder>
	  </appender>
	
	  <logger name="com.mchange.v2" level="warn"/>
	  <logger name="io.netty" level="warn"/>
	  <logger name="io.vertx" level="info"/>
	  <logger name="io.vertx.guides.wiki" level="debug"/>
	
	  <root level="debug">
	    <appender-ref ref="STDOUT"/>
	  </root>
	
	</configuration>

最后，HSQLDB 作为嵌入数据库时与 logger 不能集成地不是很好。默认情况下，它会尝试重新配置日志记录系统，因此我们需要禁用它，通过在启动应用时将`-Dhsqldb.reconfig_logging = false`属性传给 Java 虚拟机。

#### HTTP 服务器初始化

HTTP 服务器利用`vertx-web`项目轻松的为 HTTP 请求定义*分发路由(dispatching routes)*。实际上，Vert.x core API 允许启动 HTTP 服务器并监听传入的连接，但它不提供任何方便的工具，例如，根据不同的请求 URl或者处理不同的请求 body 使用不同的 handles。这是路由器的作用，因为它根据 URL, HTTP 方法等将请求分发给不同的processing handlers。

初始化包括设置*请求路由器(request router)*，然后启动HTTP服务器：

	private Future<Void> startHttpServer() {
	  Future<Void> future = Future.future();
	  HttpServer server = vertx.createHttpServer();   (1)
	
	  Router router = Router.router(vertx);   (2)
	  router.get("/").handler(this::indexHandler);
	  router.get("/wiki/:page").handler(this::pageRenderingHandler); (3)
	  router.post().handler(BodyHandler.create());  (4)
	  router.post("/save").handler(this::pageUpdateHandler);
	  router.post("/create").handler(this::pageCreateHandler);
	  router.post("/delete").handler(this::pageDeletionHandler);
	
	  server
	    .requestHandler(router::accept)   (5)
	    .listen(8080, ar -> {   (6)
	      if (ar.succeeded()) {
	        LOGGER.info("HTTP server running on port 8080");
	        future.complete();
	      } else {
	        LOGGER.error("Could not start a HTTP server", ar.cause());
	        future.fail(ar.cause());
	      }
	    });
	
	  return future;
	}

1. `vertx`上下文对象提供了方法创建 HTTP 服务器，客户端，TCP/UDP 服务器和客户端等。 
2. `Router`类来自`vertx-web`:`io.vertx.ext.web.Router`。
3. 路由器有自己的handlers，可以通过 URL 和 HTTP方法 组合或者单独定义。对于简短的handlers，Java lambda 表达式是可以的选项，但对于更加复杂的 handlers，最好定义一个私有方法。请注意，URL 可以是参数化的：`/wiki/:page`将匹配`/wiki/Hello`之类的请求，在这种情况下，`page`参数将是可用的，它的值为`Hello`。
4. 这使得所有 HTTP POST 请求都首先通过一个 handler，在这里是`io.vertx.ext.web.handler.BodyHandler`。该 handler 根据 HTTP 请求（例如，表单提交）自动解码 body，然后可以将其作为 Vert.x 缓冲区对象进行操作。
5. 路由器 router 对象可以用作HTTP 服务器 handler，然后将其分发给上面定义的其他handlers。
6. 启动 HTTP 服务器是异步操作，因此需要检查`AsyncResult <HttpServer>`是否成功。顺便说一句，`8080`参数指定了服务器使用的 TCP 端口。