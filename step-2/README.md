## 重构为独立的，可复用的 verticles

**TIP**
>相应的源码位于本指南仓库的`step-2`文件夹中

第一次迭代我们构建了一个可以工作的 wiki 应用。但是，它的实现还存在以下问题：
 
1. HTTP 请求的处理和数据库的访问代码在同一个方法中互相交织。
2. 许多配置数据（例如，端口号，JDBC 驱动程序等）在代码中硬编码。

### 架构和技术选择

第二次迭代将把代码重构为独立且可重用的 verticle：

![verticles-refactoring.png](images/verticles-refactoring.png)

我们将部署2个 verticle 来处理 HTTP 请求，1个 verticle 用于封装数据库持久化。生成的 verticles 不会直接互相引用，它们只会商定事件总线中的目标名称以及消息格式。这提供了简单而有效的解耦。

在事件总线上发送的消息将以 JSON 编码。虽然 Vert.x 在事件总线上支持灵活的序列化方案，来满足要求严格的或高度定制的上下文，但使用 JSON 数据通常是明智的选择。使用 JSON 的另一个好处是它是一种与编程语言无关的文本格式。由于 Vert.x 是多语言的，如果用不同语言编写的 verticle 需要通过消息传递进行通信，那么 JSON 是理想的选择。

### HTTP 服务器  verticle

	verticle 类开头样板代码和`start`方法如下所示：
	
	public class HttpServerVerticle extends AbstractVerticle {
	
	  private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);
	
	  public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";  (1)
	  public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";
	
	  private String wikiDbQueue = "wikidb.queue";
	
	  @Override
	  public void start(Future<Void> startFuture) throws Exception {
	
	    wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");  (2)
	
	    HttpServer server = vertx.createHttpServer();
	
	    Router router = Router.router(vertx);
	    router.get("/").handler(this::indexHandler);
	    router.get("/wiki/:page").handler(this::pageRenderingHandler);
	    router.post().handler(BodyHandler.create());
	    router.post("/save").handler(this::pageUpdateHandler);
	    router.post("/create").handler(this::pageCreateHandler);
	    router.post("/delete").handler(this::pageDeletionHandler);
	
	    int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);  (3)
	    server
	      .requestHandler(router::accept)
	      .listen(portNumber, ar -> {
	        if (ar.succeeded()) {
	          LOGGER.info("HTTP server running on port " + portNumber);
	          startFuture.complete();
	        } else {
	          LOGGER.error("Could not start a HTTP server", ar.cause());
	          startFuture.fail(ar.cause());
	        }
	      });
	  }
	
	  // (...)

1. 我们公开了 verticle 配置参数的 public 常量：HTTP 端口号和事件总线目的地的名称，以便将消息发布到数据库 verticle。
2. `AbstractVerticle＃config()`方法允许访问已提供的 verticle 配置。第二个参数是在没有找到指定配置的值时的默认值。
3. 配置的值不仅可以是`String`对象，还可以是整数，布尔值，复杂 JSON 数据等。

这个类的其余部分主要是把 HTTP 部分的代码提取出来，之前的数据库代码被事件总线消息替换。这是`indexHandler`方法代码：

	private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create();
	
	private void indexHandler(RoutingContext context) {
	
	  DeliveryOptions options = new DeliveryOptions().addHeader("action", "all-pages"); (2)
	
	  vertx.eventBus().send(wikiDbQueue, new JsonObject(), options, reply -> {  (1)
	    if (reply.succeeded()) {
	      JsonObject body = (JsonObject) reply.result().body();   (3)
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

1. vertx对象提供对事件总线的访问，我们向数据库 verticle 的队列发送消息。
2. Delivery options允许我们指定头部，负载编解码器和超时设置。
3. 成功后，回复包含有效负载。

正如我们所看到的，事件总线消息由一个正文，选项组成，还有一个可选的期望回复。如果预期不会返回响应，则使用没有处理程序参数的`send`方法变体。

我们将负载编码为 JSON 对象，并通过消息头(我们称之为`action`)指定数据库 Verticle 应执行的操作。

Verticle 剩余的代码在路由器 handler 中，它们同样使用事件总线来获取和存储数据：

	private static final String EMPTY_PAGE_MARKDOWN =
	"# A new page\n" +
	  "\n" +
	  "Feel-free to write in Markdown!\n";
	
	private void pageRenderingHandler(RoutingContext context) {
	
	  String requestedPage = context.request().getParam("page");
	  JsonObject request = new JsonObject().put("page", requestedPage);
	
	  DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-page");
	  vertx.eventBus().send(wikiDbQueue, request, options, reply -> {
	
	    if (reply.succeeded()) {
	      JsonObject body = (JsonObject) reply.result().body();
	
	      boolean found = body.getBoolean("found");
	      String rawContent = body.getString("rawContent", EMPTY_PAGE_MARKDOWN);
	      context.put("title", requestedPage);
	      context.put("id", body.getInteger("id", -1));
	      context.put("newPage", found ? "no" : "yes");
	      context.put("rawContent", rawContent);
	      context.put("content", Processor.process(rawContent));
	      context.put("timestamp", new Date().toString());
	
	      templateEngine.render(context, "templates","/page.ftl", ar -> {
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
	
	private void pageUpdateHandler(RoutingContext context) {
	
	  String title = context.request().getParam("title");
	  JsonObject request = new JsonObject()
	    .put("id", context.request().getParam("id"))
	    .put("title", title)
	    .put("markdown", context.request().getParam("markdown"));
	
	  DeliveryOptions options = new DeliveryOptions();
	  if ("yes".equals(context.request().getParam("newPage"))) {
	    options.addHeader("action", "create-page");
	  } else {
	    options.addHeader("action", "save-page");
	  }
	
	  vertx.eventBus().send(wikiDbQueue, request, options, reply -> {
	    if (reply.succeeded()) {
	      context.response().setStatusCode(303);
	      context.response().putHeader("Location", "/wiki/" + title);
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
	
	private void pageDeletionHandler(RoutingContext context) {
	  String id = context.request().getParam("id");
	  JsonObject request = new JsonObject().put("id", id);
	  DeliveryOptions options = new DeliveryOptions().addHeader("action", "delete-page");
	  vertx.eventBus().send(wikiDbQueue, request, options, reply -> {
	    if (reply.succeeded()) {
	      context.response().setStatusCode(303);
	      context.response().putHeader("Location", "/");
	      context.response().end();
	    } else {
	      context.fail(reply.cause());
	    }
	  });
	}

### 数据库 verticle

用 JDBC 连接当然需要驱动程序和一些配置，在第一次迭代中，我们将这些东西硬编码。

#### 可配置的 SQL 查询

verticle 将先前硬编码的值转换为配置参数，更进一步，我们还将通过从属性文件加载SQL查询。

查询语句将从作为配置参数传递的文件中加载，如果没有提供，则从默认资源加载。这种方法的优点是 verticle 可以适应不同的 JDBC 驱动程序和 SQL 方言。

verticle 类的先导代码主要由配置键的定义组成:

	public class WikiDatabaseVerticle extends AbstractVerticle {
	
	  public static final String CONFIG_WIKIDB_JDBC_URL = "wikidb.jdbc.url";
	  public static final String CONFIG_WIKIDB_JDBC_DRIVER_CLASS = "wikidb.jdbc.driver_class";
	  public static final String CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE = "wikidb.jdbc.max_pool_size";
	  public static final String CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE = "wikidb.sqlqueries.resource.file";
	
	  public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";
	
	  private static final Logger LOGGER = LoggerFactory.getLogger(WikiDatabaseVerticle.class);
	
	   // (...)

SQL 查询语句存储在一个属性文件，HSQLDB 的默认值位于`src/main/resources/db-queries.properties`:

	create-pages-table=create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)
	get-page=select Id, Content from Pages where Name = ?
	create-page=insert into Pages values (NULL, ?, ?)
	save-page=update Pages set Content = ? where Id = ?
	all-pages=select Name from Pages
	delete-page=delete from Pages where Id = ?

下面这段代码摘自`WikiDatabaseVerticle`类，首先从文件加载 SQL 语句，然后把他们放入一个map中:

	private enum SqlQuery {
	  CREATE_PAGES_TABLE,
	  ALL_PAGES,
	  GET_PAGE,
	  CREATE_PAGE,
	  SAVE_PAGE,
	  DELETE_PAGE
	}
	
	private final HashMap<SqlQuery, String> sqlQueries = new HashMap<>();
	
	private void loadSqlQueries() throws IOException {
	
	  String queriesFile = config().getString(CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE);
	  InputStream queriesInputStream;
	  if (queriesFile != null) {
	    queriesInputStream = new FileInputStream(queriesFile);
	  } else {
	    queriesInputStream = getClass().getResourceAsStream("/db-queries.properties");
	  }
	
	  Properties queriesProps = new Properties();
	  queriesProps.load(queriesInputStream);
	  queriesInputStream.close();
	
	  sqlQueries.put(SqlQuery.CREATE_PAGES_TABLE, queriesProps.getProperty("create-pages-table"));
	  sqlQueries.put(SqlQuery.ALL_PAGES, queriesProps.getProperty("all-pages"));
	  sqlQueries.put(SqlQuery.GET_PAGE, queriesProps.getProperty("get-page"));
	  sqlQueries.put(SqlQuery.CREATE_PAGE, queriesProps.getProperty("create-page"));
	  sqlQueries.put(SqlQuery.SAVE_PAGE, queriesProps.getProperty("save-page"));
	  sqlQueries.put(SqlQuery.DELETE_PAGE, queriesProps.getProperty("delete-page"));
	}

我们使用`SqlQuery`枚举类型来避免代码中的字符串常量。verticle`start方`法的代码如下：

	private JDBCClient dbClient;
	
	@Override
	public void start(Future<Void> startFuture) throws Exception {
	
	  /*
	   * Note: this uses blocking APIs, but data is small...
	   */
	  loadSqlQueries();  (1)
	
	  dbClient = JDBCClient.createShared(vertx, new JsonObject()
	    .put("url", config().getString(CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:file:db/wiki"))
	    .put("driver_class", config().getString(CONFIG_WIKIDB_JDBC_DRIVER_CLASS, "org.hsqldb.jdbcDriver"))
	    .put("max_pool_size", config().getInteger(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 30)));
	
	  dbClient.getConnection(ar -> {
	    if (ar.failed()) {
	      LOGGER.error("Could not open a database connection", ar.cause());
	      startFuture.fail(ar.cause());
	    } else {
	      SQLConnection connection = ar.result();
	      connection.execute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE), create -> {   (2)
	        connection.close();
	        if (create.failed()) {
	          LOGGER.error("Database preparation error", create.cause());
	          startFuture.fail(create.cause());
	        } else {
	          vertx.eventBus().consumer(config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue"), this::onMessage);  (3)
	          startFuture.complete();
	        }
	      });
	    }
	  });
	}

1. 有趣的是，我们打破了一个在Vert.x中的重要原则，即避免阻塞 API，但由于没有用于访问类路径上的资源的异步API，所以我们的选择有限。我们可以使用 vert.x `executeBlocking`方法将阻塞 I/O 操作从事件循环卸载到工作线程，但由于数据非常小，因此这样做没有明显的好处。
2. 这里是使用SQL查询的示例。
3. `consumer`方法注册了一个事件总线目的地的 handler。

#### 分发请求

事件总线消息的handler是`onMessage `方法：

	public enum ErrorCodes {
	  NO_ACTION_SPECIFIED,
	  BAD_ACTION,
	  DB_ERROR
	}
	
	public void onMessage(Message<JsonObject> message) {
	
	  if (!message.headers().contains("action")) {
	    LOGGER.error("No action header specified for message with headers {} and body {}",
	      message.headers(), message.body().encodePrettily());
	    message.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "No action header specified");
	    return;
	  }
	  String action = message.headers().get("action");
	
	  switch (action) {
	    case "all-pages":
	      fetchAllPages(message);
	      break;
	    case "get-page":
	      fetchPage(message);
	      break;
	    case "create-page":
	      createPage(message);
	      break;
	    case "save-page":
	      savePage(message);
	      break;
	    case "delete-page":
	      deletePage(message);
	      break;
	    default:
	      message.fail(ErrorCodes.BAD_ACTION.ordinal(), "Bad action: " + action);
	  }
	}

我们为错误定义了一个`ErrorCodes`枚举，我们用它来向消息发送者报告。为此，`Message`类的`fail`方法提供了一个方便的快捷方法来回复错误，原始的消息发送者将获得失败的`AsyncResult`。

#### 减少 JDBC 样板代码

到目前为止，我们已经看到了执行SQL查询的完整交互：

1. 检索连接
2. 执行请求
3. 释放连接

这导致需要写大量的代码来处理每个异步操作的错误，如：

	dbClient.getConnection(car -> {
	  if (car.succeeded()) {
	    SQLConnection connection = car.result();
	    connection.query(sqlQueries.get(SqlQuery.ALL_PAGES), res -> {
	      connection.close();
	      if (res.succeeded()) {
	        List<String> pages = res.result()
	          .getResults()
	          .stream()
	          .map(json -> json.getString(0))
	          .sorted()
	          .collect(Collectors.toList());
	        message.reply(new JsonObject().put("pages", new JsonArray(pages)));
	      } else {
	        reportQueryError(message, res.cause());
	      }
	    });
	  } else {
	    reportQueryError(message, car.cause());
	  }
	});

从 vert.x 3.5.0 开始，JDBC 客户端现在支持一次性(*one-shot*)操作，获取连接以执行SQL操作，然后在内部释放。与上面同样的代码现在简化为：

	dbClient.query(sqlQueries.get(SqlQuery.ALL_PAGES), res -> {
	  if (res.succeeded()) {
	    List<String> pages = res.result()
	      .getResults()
	      .stream()
	      .map(json -> json.getString(0))
	      .sorted()
	      .collect(Collectors.toList());
	    message.reply(new JsonObject().put("pages", new JsonArray(pages)));
	  } else {
	    reportQueryError(message, res.cause());
	  }
	});

对于获取连接进行单个操作的情况来说，这非常有用。重要的是需要注意，重用连接进行链式 SQL 操作在性能方面来说会更好。

该类的其余部分由私有方法组成，当`onMessage`分发传入消息时调用：

	private void fetchAllPages(Message<JsonObject> message) {
	  dbClient.query(sqlQueries.get(SqlQuery.ALL_PAGES), res -> {
	    if (res.succeeded()) {
	      List<String> pages = res.result()
	        .getResults()
	        .stream()
	        .map(json -> json.getString(0))
	        .sorted()
	        .collect(Collectors.toList());
	      message.reply(new JsonObject().put("pages", new JsonArray(pages)));
	    } else {
	      reportQueryError(message, res.cause());
	    }
	  });
	}
	
	private void fetchPage(Message<JsonObject> message) {
	  String requestedPage = message.body().getString("page");
	  JsonArray params = new JsonArray().add(requestedPage);
	
	  dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_PAGE), params, fetch -> {
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
	      message.reply(response);
	    } else {
	      reportQueryError(message, fetch.cause());
	    }
	  });
	}
	
	private void createPage(Message<JsonObject> message) {
	  JsonObject request = message.body();
	  JsonArray data = new JsonArray()
	    .add(request.getString("title"))
	    .add(request.getString("markdown"));
	
	  dbClient.updateWithParams(sqlQueries.get(SqlQuery.CREATE_PAGE), data, res -> {
	    if (res.succeeded()) {
	      message.reply("ok");
	    } else {
	      reportQueryError(message, res.cause());
	    }
	  });
	}
	
	private void savePage(Message<JsonObject> message) {
	  JsonObject request = message.body();
	  JsonArray data = new JsonArray()
	    .add(request.getString("markdown"))
	    .add(request.getString("id"));
	
	  dbClient.updateWithParams(sqlQueries.get(SqlQuery.SAVE_PAGE), data, res -> {
	    if (res.succeeded()) {
	      message.reply("ok");
	    } else {
	      reportQueryError(message, res.cause());
	    }
	  });
	}
	
	private void deletePage(Message<JsonObject> message) {
	  JsonArray data = new JsonArray().add(message.body().getString("id"));
	
	  dbClient.updateWithParams(sqlQueries.get(SqlQuery.DELETE_PAGE), data, res -> {
	    if (res.succeeded()) {
	      message.reply("ok");
	    } else {
	      reportQueryError(message, res.cause());
	    }
	  });
	}
	
	private void reportQueryError(Message<JsonObject> message, Throwable cause) {
	  LOGGER.error("Database query error", cause);
	  message.fail(ErrorCodes.DB_ERROR.ordinal(), cause.getMessage());
	}

### 在主 verticle 部署 verticles

我们仍然需要一个`MainVerticle`类，但它没有像初始迭代版本那样包含所有业务逻辑，它的唯一目的就是是引导应用程序并部署其他的 Verticle。

代码包括部署1个`WikiDatabaseVerticle`实例和2个`HttpServerVerticle`实例：

	public class MainVerticle extends AbstractVerticle {
	
	  @Override
	  public void start(Future<Void> startFuture) throws Exception {
	
	    Future<String> dbVerticleDeployment = Future.future();  (1)
	    vertx.deployVerticle(new WikiDatabaseVerticle(), dbVerticleDeployment.completer());  (2)
	
	    dbVerticleDeployment.compose(id -> {  (3)
	
	      Future<String> httpVerticleDeployment = Future.future();
	      vertx.deployVerticle(
	        "io.vertx.guides.wiki.HttpServerVerticle",  (4)
	        new DeploymentOptions().setInstances(2),    (5)
	        httpVerticleDeployment.completer());
	
	      return httpVerticleDeployment;  (6)
	
	    }).setHandler(ar -> {   (7)
	      if (ar.succeeded()) {
	        startFuture.complete();
	      } else {
	        startFuture.fail(ar.cause());
	      }
	    });
	  }
	}

1. 部署Verticle是一种异步操作，因此我们需要一个`Future`。参数类型`String`是因为 verticle 在部署成功后获得一个标识符。
2. 一种选择是使用`new`创建一个 verticle 实例，并将对象引用传递给`deploy`方法。`completer`返回值是一个简单地完成 future 的 handler。
3. 使用`compose`的顺序组合允许在一个异步操作完成之后执行另一个异步操作。当初始 future 成功完成时，将调用传入的组合函数(composition function)。
4. 类名的字符串也可以用来指定要部署的 verticle。对于其他 JVM 语言，基于字符串的约定允许指定模块/脚本。 
5. `DeploymentOption`类允许指定许多参数，尤其是要部署的实例数。
6. 组合函数(composition function)返回下一个 future。它的完成将触发组合操作的完成。
7. 我们定义一个handler来最终完成`MainVerticle`启动的 future。

精明的读者可能会猜想，我们如何在同一个 TCP 端口上部署 HTTP 服务器的代码两次，虽然TCP端口已经被占用，但任一实例都不会出现任何错误。对于许多 Web 框架，我们需要选择不同的 TCP 端口，并使用前置的 HTTP 代理来执行端口之间的负载平衡。

Vert.x 没有必要这样做，因为多个 verticle 可以共享相同的TCP端口。传入的连接简单地以接受线程循环的方式分发。