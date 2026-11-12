# XXHashFactory.safeInstance() reflects both JavaSafe hash classes and streaming factories.
# Keep only their names and the INSTANCE fields accessed through Class.getField().
-keep,allowoptimization class net.jpountz.xxhash.XXHash32JavaSafe {
    public static final net.jpountz.xxhash.XXHash32 INSTANCE;
}
-keep,allowoptimization class net.jpountz.xxhash.XXHash64JavaSafe {
    public static final net.jpountz.xxhash.XXHash64 INSTANCE;
}
-keep,allowoptimization class net.jpountz.xxhash.StreamingXXHash32JavaSafe$Factory {
    public static final net.jpountz.xxhash.StreamingXXHash32$Factory INSTANCE;
}
-keep,allowoptimization class net.jpountz.xxhash.StreamingXXHash64JavaSafe$Factory {
    public static final net.jpountz.xxhash.StreamingXXHash64$Factory INSTANCE;
}
# Keep crash reports readable while retaining code/resource shrinking and optimization.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# Retrofit 2.9 keeps its service interfaces and Signature/annotation attributes, but
# full-mode R8 also needs these generic types retained for Ketch's suspend HEAD/GET API.
-keep,allowoptimization,allowshrinking interface kotlin.coroutines.Continuation
