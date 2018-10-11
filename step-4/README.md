## 测试 Vert.x 代码

**TIP**
>应的源代码位于指南存储库的`step-4`文件夹中。

到目前为止，我们一直在开发没有经过测试的 wiki 实现。这当然不是一个好习惯，所以让我们看看如何为 Vert.x 代码编写测试。

### 入门

`vertx-unit`模块提供了测试 Vert.x 中异步操作的实用工具。除此之外，您可以使用自己选择的测试框架，如JUnit。

使用JUnit，所需的Maven依赖项如下：

	<dependency>
	  <groupId>junit</groupId>
	  <artifactId>junit</artifactId>
	  <version>4.12</version>
	  <scope>test</scope>
	</dependency>
	<dependency>
	  <groupId>io.vertx</groupId>
	  <artifactId>vertx-unit</artifactId>
	  <scope>test</scope>
	</dependency>

JUnit 测试需要使用`VertxUnitRunner`运行器注解以使用`VertxUnitRunner`功能：

	@RunWith(VertxUnitRunner.class)
	public class SomeTest {
	  // (...)
	}

使用该执行器，JUnit 测试和生命周期方法接受`TestContext`参数。此对象提供对基本断言的访问，存储数据的上下文以及我们将在本节中看到的几个面向异步的辅助功能。

为了说明这一点，让我们考虑一个异步场景，我们要检查计时器任务是否已被调用一次，还有一个周期性任务已被调用3次。因为该代码是异步的，所以测试方法在测试完成之前已经退出，因此使测试通过或失败也需要以异步方式完成：

	@Test /*(timeout=5000)*/  (8)
	public void async_behavior(TestContext context) { (1)
	  Vertx vertx = Vertx.vertx();  (2)
	  context.assertEquals("foo", "foo");  (3)
	  Async a1 = context.async();   (4)
	  Async a2 = context.async(3);  (5)
	  vertx.setTimer(100, n -> a1.complete());  (6)
	  vertx.setPeriodic(100, n -> a2.countDown());  (7)
	}

1. `TestContext`是运行器提供的参数。
2. 由于我们处于单元测试中，因此我们需要创建 Vert.x 上下文。
3. 这是一个基本`TestContext`断言的示例。
4. 我们得到了第一个可以在稍后完成（或失败）的`Async`对象。
5. 此`Async`对象作为3次调用后成功完成的倒计时。
6. 计时器启动时完成。
7. 每个周期性任务都触发一个倒计时。所有`Async`对象完成后，测试通过。
8. 异步测试用例有一个默认（long）超时，但可以通过JUnit`@Test`注解覆盖它。

### 测试数据库操作

数据库服务非常适合编写测试。

我们首先需要部署数据库 verticle。我们将 JDBC 连接配置为具有内存存储的 HSQLDB，一旦成功，我们将为我们的测试用例获取服务代理。

由于这些操作涉及其他测试方法，我们使用 JUnit 的`before/after`生命周期方法：

	private Vertx vertx;
	private WikiDatabaseService service;
	
	@Before
	public void prepare(TestContext context) throws InterruptedException {
	  vertx = Vertx.vertx();
	
	  JsonObject conf = new JsonObject()  (1)
	    .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:testdb;shutdown=true")
	    .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);
	
	  vertx.deployVerticle(new WikiDatabaseVerticle(), new DeploymentOptions().setConfig(conf),
	    context.asyncAssertSuccess(id ->  (2)
	      service = WikiDatabaseService.createProxy(vertx, WikiDatabaseVerticle.CONFIG_WIKIDB_QUEUE)));
	}

1. 我们只覆盖了一些 verticle 的设置，其他设置将具有默认值。
2. `asyncAssertSuccess`用于提供 handler 检查异步操作是否成功。有一个没有参数的变体，以及像这样的变体，我们可以将结果链接到另一个 handler。

清理 Vert.x 上下文非常简单，我们再次使用`asyncAssertSuccess`来确保没有遇到错误：

	@After
	public void finish(TestContext context) {
	  vertx.close(context.asyncAssertSuccess());
	}

服务的操作本质上是 CRUD 操作，因此让 JUnit 测试用例结合所有这些操作是一种很好的测试方式：

	@Test
	public void crud_operations(TestContext context) {
	  Async async = context.async();
	
	  service.createPage("Test", "Some content", context.asyncAssertSuccess(v1 -> {
	
	    service.fetchPage("Test", context.asyncAssertSuccess(json1 -> {
	      context.assertTrue(json1.getBoolean("found"));
	      context.assertTrue(json1.containsKey("id"));
	      context.assertEquals("Some content", json1.getString("rawContent"));
	
	      service.savePage(json1.getInteger("id"), "Yo!", context.asyncAssertSuccess(v2 -> {
	
	        service.fetchAllPages(context.asyncAssertSuccess(array1 -> {
	          context.assertEquals(1, array1.size());
	
	          service.fetchPage("Test", context.asyncAssertSuccess(json2 -> {
	            context.assertEquals("Yo!", json2.getString("rawContent"));
	
	            service.deletePage(json1.getInteger("id"), v3 -> {
	
	              service.fetchAllPages(context.asyncAssertSuccess(array2 -> {
	                context.assertTrue(array2.isEmpty());
	                async.complete();  (1)
	              }));
	            });
	          }));
	        }));
	      }));
	    }));
	  }));
	  async.awaitSuccess(5000); (2)
	}

1. 这是唯一的`Async`最终完成的地方。
2. 这是退出测试用例方法的替代方法，依赖于JUnit超时。这里测试用例线程上的执行会一直等到`Async`完成或直到超时。