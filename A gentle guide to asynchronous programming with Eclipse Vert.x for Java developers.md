# Java开发者使用 Eclipse Vert.x 进行异步编程的简要指南

*致谢*
 
本文档收到了来自 Arnaud Esteve，Marc Paquette，Ashley Bye，Ger-Jan te Dorsthorst，Htet Aung Shine 等人的贡献。

## 引言

本指南简要介绍了 Vert.x 的异步编程，主要针对熟悉主流非异步Web开发框架和库（例如 Java EE，Spring）的开发者。

### 关于本指南

我们假设读者熟悉 Java 编程语言及其生态系统。

我们将从一个由关系数据库和服务器端页面渲染支持的 wiki Web 应用程序开始；然后我们将通过几个步骤改进应用程序，直到它成为“实时”web功能的现代单页面应用程序。在此过程中，您将学习：

1. 设计一个 Web 应用程序，通过模板在服务器端渲染页面，并使用关系数据库来持久化数据。
2. 将每个技术组件清晰地隔离为可重用的事件处理单元，称为 ***Verticle***。
3. 提取 Vert.x 服务，以便于设计在同一JVM进程内或集群中的分布式节点之间无缝地相互通信的 Verticle。
4. 使用异步操作测试代码。
5. 与暴露 Http/JSON web API的第三方服务进行集成。
6. 暴露 Http/JSON web API
7. 使用 HTTPS，Web 浏览器会话的用户身份验证和第三方客户端应用程序的JWT令牌保护和控制访问。
8. 重构一些代码以使用流行的 RxJava 库与 Vert.x 集成进行响应式编程
9. 使用 AngularJS 进行单页面应用程序的客户端编程。
10. 通过 SockJS 集成的统一的 Vert.x 事件总线进行实时 Web 编程。

NOTE
> 本文档和代码示例的源码可从[https://github.com/vert-x3/vertx-guide-for-java-devs](https://github.com/vert-x3/vertx-guide-for-java-devs)获得。我们欢迎问题报告，反馈和pull请求！

### 什么是 Vert.x?
> Eclipse Vert.x 是一个用于在 JVM 上构建响应式应用程序的工具包。
> 
> — Vert.x website

Eclipse Vert.x（我们在本文档的后面部分将简称为 Vert.x）是 Eclipse Foundation 的一个开源项目。Vert.x 由 Tim Fox 于2012年发起。

Vert.x 不是框架而是工具包：核心库定义了编写异步网络应用程序的基本API，然后您可以为应用程序选择有用的模块（例如，数据库连接，监视，身份验证，日志记录，服务发现，集群支持等）。 Vert.x 基于 Netty 项目，这是一个用于JVM的高性能异步网络库。如果需要，Vert.x将允许您访问 Netty 内部，但通常您将从 Vert.x 提供的更高级别的API中受益，同时与使用原生的 Netty 相比不会牺牲性能。

由于 Vert.x 是为异步通信而设计的，因此它可以处理更多的并发网络连接，而且只需要比 Java servlet 或 java.net 套接字类等同步API更少的线程。Vert.x 适用于大量的应用程序：高容量消息/事件处理，微服务，API网关，移动应用程序的HTTP API等。 Vert.x 及其生态系统提供各种技术工具，用于构建端对端的响应应用程序。

虽然听起来 Vert.x 仅对要求苛刻的应用程序有用，但本指南同样认为 Vert.x 适用于更传统的Web应用程序。正如我们将要看到的，代码仍然是相对容易理解的，但如果应用程序需要面对突然的流量高峰，代码已经准备好了用与扩展的基础部分，那就是 *异步处理事件*。

最后，值得一提的是 Vert.x 是多语言，它支持各种流行的JVM语言：Java，Groovy，Scala，Kotlin，JavaScript，Ruby和Ceylon。在 Vert.x 中支持一种语言的目标不仅仅是提供对API的访问，同时还要确保该语言的API在每种目标语言中都是通用的（例如，使用 Scala 的 futures 代替 Vert.x 的 futures）。使用不同的JVM语言开发 Vert.x 应用程序的不同技术部分是很可行的。
