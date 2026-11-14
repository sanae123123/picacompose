# picacompose 的 R8 规则。
#
# 代码里一行反射都没有（shared / picaapi 全搜过 javaClass、Class.forName、
# kotlin.reflect、getDeclaredMethod），所以真正会被裁坏的地方只有一类：
# 靠 ServiceLoader 从 META-INF/services 里找实现的那些。服务文件是静态资源，
# R8 不会删，但实现类的构造函数一旦被裁掉，运行时就是 ServiceConfigurationError。
# 下面这四条是按 release 包里实际存在的服务文件逐条钉的，不多留。

# coil 的 fetcher 发现
-keep class coil3.network.ktor3.internal.KtorNetworkFetcherServiceLoaderTarget { <init>(); }

# ktor 的引擎发现：HttpClient() 不带引擎参数时靠这个容器找到 OkHttp
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { <init>(); }
-keep class io.ktor.client.engine.okhttp.OkHttpEngine { *; }

# coroutines-android：主线程 dispatcher 和未捕获异常处理器
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { <init>(); }
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { <init>(); }

# picaapi 的模型里有一批 enum，序列化按名字对；名字被改成 a/b/c 就对不上了
-keepclassmembers enum * { *; }

# 崩溃栈要能看出是哪个文件哪一行
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
