## Vert.x 编写的最小可运行的 wiki

我们将从第一次迭代开始，尽可能用 Vert.x 以最简单的代码编写一个 wiki。虽然下一次迭代将在代码库中引入更多优雅以及适当的测试，我们将看到 Vert.x 的快速原型设计既简单又实际。

在此阶段，wiki将使用服务器端渲染 HTML 页面和通过 JDBC 连接进行数据持久化。为此，我们将使用以下库。

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

