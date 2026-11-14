# picacompose 的裁剪规则。
#
# 这里只让 R8 干一件事：删掉没用到的代码和资源。改名和优化都关掉（见文件末尾），
# 所以类名原样保留 —— 崩溃栈不用 mapping 也能读，基线配置里的类名 1:1 对得上，
# 也就没有「按名字反射找类」被改坏的那类风险。
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

# 崩溃栈要能看出是哪个文件哪一行（不改名了，源文件名也照原样留）
-keepattributes SourceFile,LineNumberTable

# 只裁剪：不改名、不做优化（-dontoptimize 也免掉 R8 那些重排/内联）
-dontobfuscate
-dontoptimize
