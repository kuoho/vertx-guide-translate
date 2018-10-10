## 重构为 Vert.x 服务

**TIP**
>相应的源代码位于指南库的step-3文件夹中。

与我们的初始实现相比，之前的重构向前迈出了一大步，因为我们提取了独立且可配置的Verticle，并且通过在事件总线上使用异步消息进行连接。我们还看到，我们可以部署给定的 verticle 的多个实例，以更好地应对负载并更好地利用 CPU 核心。

在本节中，我们将了解如何设计和使用 Vert.x 服务。服务的主要优点是它定义了一个接口，用于执行 verticle 公开的确定的操作。我们还利用代码生成来处理所有的事件总线消息垂直传递，而不是像我们在上一节中那样自己手动编码。

我们还要将代码重构为不同的Java包：

	step-3/src/main/java/
	└── io
	    └── vertx
	        └── guides
	            └── wiki
	                ├── MainVerticle.java
	                ├── database
	                │   ├── ErrorCodes.java
	                │   ├── SqlQuery.java
	                │   ├── WikiDatabaseService.java
	                │   ├── WikiDatabaseServiceImpl.java
	                │   ├── WikiDatabaseVerticle.java
	                │   └── package-info.java
	                └── http
	                    └── HttpServerVerticle.java

`io.vertx.guides.wiki`现在将包含 main verticle，`io.vertx.guides.wiki.database`包含数据库 verticle 和 service，以及`io.vertx.guides.wiki.http HTTP` http server 服务。

### Maven 配置更改

首先，我们需要将以下2个依赖项添加到项目中。显然我们需要`vertx-service-proxy` APIs：

	<dependency>
	  <groupId>io.vertx</groupId>
	  <artifactId>vertx-service-proxy</artifactId>
	</dependency>

我们需要 Vert.x 代码生成模块作为仅编译时依赖项（`provided ` scope）：

	<dependency>
	  <groupId>io.vertx</groupId>
	  <artifactId>vertx-codegen</artifactId>
	  <scope>provided</scope>
	</dependency>

接下来我们必须调整`maven-compiler-plugin`配置以使用代码生成，这是通过`javac`注解处理器完成的：

	<plugin>
	  <artifactId>maven-compiler-plugin</artifactId>
	  <version>3.5.1</version>
	  <configuration>
	    <source>1.8</source>
	    <target>1.8</target>
	    <useIncrementalCompilation>false</useIncrementalCompilation>
	
	    <annotationProcessors>
	      <annotationProcessor>io.vertx.codegen.CodeGenProcessor</annotationProcessor>
	    </annotationProcessors>
	    <generatedSourcesDirectory>${project.basedir}/src/main/generated</generatedSourcesDirectory>
	    <compilerArgs>
	      <arg>-AoutputDirectory=${project.basedir}/src/main</arg>
	    </compilerArgs>
	
	  </configuration>
	</plugin>

请注意，生成的代码放在`src/main/generated`中，某些集成开发环境（如IntelliJ IDEA）将自动获取类路径。

更新`maven-clean-plugin`去删除这些生成的文件也是一个好主意：

	<plugin>
	  <artifactId>maven-clean-plugin</artifactId>
	  <version>3.0.0</version>
	  <configuration>
	    <filesets>
	      <fileset>
	        <directory>${project.basedir}/src/main/generated</directory>
	      </fileset>
	    </filesets>
	  </configuration>
	</plugin>

**TIP**
>有关 Vert.x services 的完整文档，请访问[http://vertx.io/docs/vertx-service-proxy/java/](http://vertx.io/docs/vertx-service-proxy/java/)

### 数据库服务接口

定义服务接口就像定义 Java 接口一样简单，还需要遵守一些规则以让代码生成可以正常工作，并确保与 Vert.x 中其他代码的互通性。

接口定义的开头是：

	@ProxyGen
	public interface WikiDatabaseService {
	
	  @Fluent
	  WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler);
	
	  @Fluent
	  WikiDatabaseService fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler);
	
	  @Fluent
	  WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler);
	
	  @Fluent
	  WikiDatabaseService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler);
	
	  @Fluent
	  WikiDatabaseService deletePage(int id, Handler<AsyncResult<Void>> resultHandler);
	
	  // (...)

1. `ProxyGen`注解用于触发生成该服务客户端的代理的代码。
2. `Fluent`注解是可选的，但通过返回服务实例来进行链式操作时允许 *fluent* 接口。当服务被其他JVM语言消费时，这对代码生成器非常有用。
3. 参数类型需要是字符串，Java 原始类型，JSON 对象或数组，任何枚举类型或者这些类型的`java.util`集合（`List`/`Set`/`Map`）。支持任意 Java 类的唯一方法是将它们作为 Vert.x 数据对象，用`@DataObject`注解。传递其他类型的最后机会是服务引用类型。
4. 因为服务提供异步结果，所以服务的方法的最后一个参数需要是`Handler <AsyncResult <T>>`，其中`T`是复合如上所述的代码生成规则的任何类型。

比较好的实践是，服务接口提供静态方法，通过事件总线来创建真正的服务实现实例和客户端代码代理的实例。

我们将create定义为简单地委托给实现类及其构造函数：
我们定义了一个`create`方法来简单的委托给它的实现类和它的构造函数：

	static WikiDatabaseService create(JDBCClient dbClient, HashMap<SqlQuery, String> sqlQueries, Handler<AsyncResult<WikiDatabaseService>> readyHandler) {
	  return new WikiDatabaseServiceImpl(dbClient, sqlQueries, readyHandler);
	}

Vert.x 代码生成器创建代理类，并通过`VertxEBProxy`后缀来命名它。这些代理类的构造函数需要 Vert.x 上下文的引用以及事件总线上的目的地地址：

	static WikiDatabaseService createProxy(Vertx vertx, String address) {
	  return new WikiDatabaseServiceVertxEBProxy(vertx, address);
	}

**NOTE**
>`SqlQuery`和`ErrorCodes`枚举类型在之前的迭代版本中是内部类，现在被提取出来作为 package-protected 类型。请参阅`SqlQuery.java`和`ErrorCodes.java`。

### 数据库服务实现

服务实现是以前的`WikiDatabaseVerticle`类代码的简单端口。本质区别在于构造函数和服务方法中的 asynchronous result handler 的支持(在构造函数中用于报告初始化结果，在方法中用于报告操作成功)。

该类的代码如下:

	class WikiDatabaseServiceImpl implements WikiDatabaseService {
	
	  private static final Logger LOGGER = LoggerFactory.getLogger(WikiDatabaseServiceImpl.class);
	
	  private final HashMap<SqlQuery, String> sqlQueries;
	  private final JDBCClient dbClient;
	
	  WikiDatabaseServiceImpl(JDBCClient dbClient, HashMap<SqlQuery, String> sqlQueries, Handler<AsyncResult<WikiDatabaseService>> readyHandler) {
	    this.dbClient = dbClient;
	    this.sqlQueries = sqlQueries;
	
	    dbClient.getConnection(ar -> {
	      if (ar.failed()) {
	        LOGGER.error("Could not open a database connection", ar.cause());
	        readyHandler.handle(Future.failedFuture(ar.cause()));
	      } else {
	        SQLConnection connection = ar.result();
	        connection.execute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE), create -> {
	          connection.close();
	          if (create.failed()) {
	            LOGGER.error("Database preparation error", create.cause());
	            readyHandler.handle(Future.failedFuture(create.cause()));
	          } else {
	            readyHandler.handle(Future.succeededFuture(this));
	          }
	        });
	      }
	    });
	  }
	
	  @Override
	  public WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler) {
	    dbClient.query(sqlQueries.get(SqlQuery.ALL_PAGES), res -> {
	      if (res.succeeded()) {
	        JsonArray pages = new JsonArray(res.result()
	          .getResults()
	          .stream()
	          .map(json -> json.getString(0))
	          .sorted()
	          .collect(Collectors.toList()));
	        resultHandler.handle(Future.succeededFuture(pages));
	      } else {
	        LOGGER.error("Database query error", res.cause());
	        resultHandler.handle(Future.failedFuture(res.cause()));
	      }
	    });
	    return this;
	  }
	
	  @Override
	  public WikiDatabaseService fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler) {
	    dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_PAGE), new JsonArray().add(name), fetch -> {
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
	    });
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
	}

在代理类的代码生成工作之前，还需要最后一步：服务包需要一个`package-info.java`注解来定义 Vert.x 模块：

	@ModuleGen(groupPackage = "io.vertx.guides.wiki.database", name = "wiki-database")
	package io.vertx.guides.wiki.database;
	
	import io.vertx.codegen.annotations.ModuleGen;

### 从数据库 verticle 公开数据库服务

由于大多数数据库处理代码已移至`WikiDatabaseServiceImpl`，因此`WikiDatabaseVerticle`类现在包含两个方法：`start`方法注册服务，一个实用方法来加载SQL查询：

	public class WikiDatabaseVerticle extends AbstractVerticle {
	
	  public static final String CONFIG_WIKIDB_JDBC_URL = "wikidb.jdbc.url";
	  public static final String CONFIG_WIKIDB_JDBC_DRIVER_CLASS = "wikidb.jdbc.driver_class";
	  public static final String CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE = "wikidb.jdbc.max_pool_size";
	  public static final String CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE = "wikidb.sqlqueries.resource.file";
	  public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";
	
	  @Override
	  public void start(Future<Void> startFuture) throws Exception {
	
	    HashMap<SqlQuery, String> sqlQueries = loadSqlQueries();
	
	    JDBCClient dbClient = JDBCClient.createShared(vertx, new JsonObject()
	      .put("url", config().getString(CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:file:db/wiki"))
	      .put("driver_class", config().getString(CONFIG_WIKIDB_JDBC_DRIVER_CLASS, "org.hsqldb.jdbcDriver"))
	      .put("max_pool_size", config().getInteger(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 30)));
	
	    WikiDatabaseService.create(dbClient, sqlQueries, ready -> {
	      if (ready.succeeded()) {
	        ServiceBinder binder = new ServiceBinder(vertx);
	        binder
	          .setAddress(CONFIG_WIKIDB_QUEUE)
	          .register(WikiDatabaseService.class, ready.result()); (1)
	        startFuture.complete();
	      } else {
	        startFuture.fail(ready.cause());
	      }
	    });
	  }
	
	  /*
	   * Note: this uses blocking APIs, but data is small...
	   */
	  private HashMap<SqlQuery, String> loadSqlQueries() throws IOException {
	
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
	
	    HashMap<SqlQuery, String> sqlQueries = new HashMap<>();
	    sqlQueries.put(SqlQuery.CREATE_PAGES_TABLE, queriesProps.getProperty("create-pages-table"));
	    sqlQueries.put(SqlQuery.ALL_PAGES, queriesProps.getProperty("all-pages"));
	    sqlQueries.put(SqlQuery.GET_PAGE, queriesProps.getProperty("get-page"));
	    sqlQueries.put(SqlQuery.CREATE_PAGE, queriesProps.getProperty("create-page"));
	    sqlQueries.put(SqlQuery.SAVE_PAGE, queriesProps.getProperty("save-page"));
	    sqlQueries.put(SqlQuery.DELETE_PAGE, queriesProps.getProperty("delete-page"));
	    return sqlQueries;
	  }
	}

1. 我们在这里注册了服务

注册服务需要接口类，Vert.x 上下文，服务实现和事件总线目标。

`WikiDatabaseServiceVertxEBProxy`生成类处理在事件总线上接收的消息，然后将它们分发到`WikiDatabaseServiceImpl`。它的作用实际上非常接近我们在上一节中所做的：使用`action`头发送消息以指定要调用的方法，和 JSON 编码的参数。

### 获取数据库服务代理

重构为 Vert.x 服务的最后步骤是调整 HTTP 服务器 Verticle 以获取数据库服务的代理，并在handler中使用它而不是事件总线。

首先，我们需要在 verticle 启动时创建代理：

	private WikiDatabaseService dbService;
	
	@Override
	public void start(Future<Void> startFuture) throws Exception {
	
	  String wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue"); (1)
	  dbService = WikiDatabaseService.createProxy(vertx, wikiDbQueue);
	
	  HttpServer server = vertx.createHttpServer();
	  // (...)

1. 我们只需要确保使用和`WikiDatabaseVerticle`发布的服务相同的事件总线目的地。

然后，我们需要通过调用数据库服务来替换对事件总线的调用：

	private void indexHandler(RoutingContext context) {
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
	
	private void pageUpdateHandler(RoutingContext context) {
	  String title = context.request().getParam("title");
	
	  Handler<AsyncResult<Void>> handler = reply -> {
	    if (reply.succeeded()) {
	      context.response().setStatusCode(303);
	      context.response().putHeader("Location", "/wiki/" + title);
	      context.response().end();
	    } else {
	      context.fail(reply.cause());
	    }
	  };
	
	  String markdown = context.request().getParam("markdown");
	  if ("yes".equals(context.request().getParam("newPage"))) {
	    dbService.createPage(title, markdown, handler);
	  } else {
	    dbService.savePage(Integer.valueOf(context.request().getParam("id")), markdown, handler);
	  }
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

`WikiDatabaseServiceVertxProxyHandler`生成的类将作为事件总线消息转发调用。

**TIP**
>完全可以通过事件总线消息直接使用 Vert.x 服务，因为这是生成的代理所做的事情。