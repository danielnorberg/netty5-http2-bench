netty5-http2-bench
==================

```
mvn compile exec:java -Dexec.mainClass=Benchmark
[INFO] Scanning for projects...
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] Building netty5-http2-bench 1.0-SNAPSHOT
[INFO] ------------------------------------------------------------------------
[INFO]
[INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ netty5-http2-bench ---
[INFO] Using 'UTF-8' encoding to copy filtered resources.
[INFO] skip non existing resourceDirectory /Users/dano/projects/netty5-http2-bench/src/main/resources
[INFO]
[INFO] --- maven-compiler-plugin:3.1:compile (default-compile) @ netty5-http2-bench ---
[INFO] Changes detected - recompiling the module!
[INFO] Compiling 11 source files to /Users/dano/projects/netty5-http2-bench/target/classes
[INFO]
[INFO] >>> exec-maven-plugin:1.2.1:java (default-cli) @ netty5-http2-bench >>>
[INFO]
[INFO] <<< exec-maven-plugin:1.2.1:java (default-cli) @ netty5-http2-bench <<<
[INFO]
[INFO] --- exec-maven-plugin:1.2.1:java (default-cli) @ netty5-http2-bench ---
Aug 12, 2014 9:07:18 PM io.netty.handler.logging.LoggingHandler channelRegistered
INFO: [id: 0x39668b7e] REGISTERED
Aug 12, 2014 9:07:18 PM io.netty.handler.logging.LoggingHandler bind
INFO: [id: 0x39668b7e] BIND: 0.0.0.0/0.0.0.0:8080
Open your HTTP/2-enabled web browser and navigate to http://127.0.0.1:8080/
Aug 12, 2014 9:07:18 PM io.netty.handler.logging.LoggingHandler channelActive
INFO: [id: 0x39668b7e, /0:0:0:0:0:0:0:0:8080] ACTIVE
Aug 12, 2014 9:07:19 PM io.netty.handler.logging.LoggingHandler channelRead
INFO: [id: 0x39668b7e, /0:0:0:0:0:0:0:0:8080] RECEIVED: [id: 0xcb5e0e54, /127.0.0.1:63032 => /127.0.0.1:8080]
Connected to [127.0.0.1:8080]
User Event Triggered: UPGRADE_ISSUED
User Event Triggered: UPGRADE_SUCCESSFUL
User Event Triggered: UpgradeEvent [protocol=h2c-13, upgradeRequest=HttpObjectAggregator$AggregatedFullHttpRequest(CompositeByteBuf(ridx: 0, widx: 0, cap: 0, components=0))]
Received HTTP/2 response to the HTTP->HTTP/2 upgrade request
     2,434 (     2,434) ops/s. 3.407418719 ms average latency.      2,436 ops total.
     6,002 (     4,220) ops/s. 2.168898321 ms average latency.      8,458 ops total.
    14,371 (     7,603) ops/s. 1.241851766 ms average latency.     22,849 ops total.
    22,660 (    11,365) ops/s. 0.843030415 ms average latency.     45,537 ops total.
    23,021 (    13,695) ops/s. 0.705784008 ms average latency.     68,572 ops total.
    21,829 (    15,050) ops/s. 0.645936561 ms average latency.     90,434 ops total.
    23,773 (    16,296) ops/s. 0.599032392 ms average latency.    114,225 ops total.
    24,151 (    17,278) ops/s. 0.566719881 ms average latency.    138,409 ops total.
    24,231 (    18,050) ops/s. 0.543770044 ms average latency.    162,666 ops total.
    23,808 (    18,626) ops/s. 0.527965165 ms average latency.    186,509 ops total.
    23,991 (    20,780) ops/s. 0.481430544 ms average latency.    210,526 ops total.
...
```
