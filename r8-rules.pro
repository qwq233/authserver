-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,MethodParameters,SourceFile,LineNumberTable

# kotlin-reflect
-keep class kotlin.Metadata { *; }
-keepattributes InnerClasses,Signature,RuntimeVisible*Annotations,EnclosingMethod
-dontnote kotlin.internal.PlatformImplementationsKt
-dontwarn kotlin.reflect.jvm.internal.**
-dontwarn java.lang.ClassValue
-assumenosideeffects class kotlin.reflect.jvm.internal.CacheByClassKt {
    boolean useClassValue return false;
}

# kotlinx-coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn java.lang.instrument.Instrumentation
-dontwarn sun.misc.Signal
-dontwarn sun.misc.SignalHandler
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-keepclassmembers class kotlinx.coroutines.flow.ReadonlySharedFlow {
    kotlinx.coroutines.Job job;
}
-keepclassmembers class kotlinx.coroutines.flow.ReadonlyStateFlow {
    kotlinx.coroutines.Job job;
}

# kotlinx-serialization
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
}
-if @kotlinx.serialization.internal.NamedCompanion class *
-keepclassmembers class * {
    static <1> *;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.internal.ClassValueReferences
-keepclassmembers public class **$$serializer {
    private ** descriptor;
}
-if @kotlinx.serialization.Serializable class **
-keep,allowshrinking,allowoptimization,allowobfuscation,allowaccessmodification class <1>
-if @kotlinx.serialization.internal.NamedCompanion class *
-keep,allowshrinking,allowoptimization,allowobfuscation,allowaccessmodification class <1>
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor AtomicFU and ServiceLoader contracts
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}
-keepclassmembernames class io.ktor.** {
    volatile <fields>;
}
-keep class io.ktor.client.engine.** implements io.ktor.client.HttpClientEngineContainer

# Jansi native libraries access these JNI bridge classes and fields by name.
-keep class org.fusesource.jansi.internal.CLibrary { *; }
-keep class org.fusesource.jansi.internal.CLibrary$* { *; }
-keep class org.fusesource.jansi.internal.Kernel32 { *; }
-keep class org.fusesource.jansi.internal.Kernel32$* { *; }

# kotlinx-datetime optional serialization bridge
-dontwarn kotlinx.serialization.KSerializer
-dontwarn kotlinx.serialization.Serializable

# MariaDB Driver optional native, cloud, and authentication integrations
-dontwarn com.sun.jna.**
-dontwarn org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
-dontwarn org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
-dontwarn software.amazon.awssdk.**
-dontwarn waffle.windows.auth.**

# Public application entry points and reflection-based Logback configuration
-keep class top.qwq2333.authsrv.ApplicationKt {
    public static void installAuthServer(io.ktor.server.application.Application, javax.sql.DataSource, kotlin.time.Instant);
}
-keep class ch.qos.logback.core.ConsoleAppender { *; }
-keep class ch.qos.logback.classic.encoder.PatternLayoutEncoder { *; }
