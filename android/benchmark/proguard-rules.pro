# Benchmark test module: R8 only needs it to match the tested app's
# obfuscation state; no app code lives here. Suppress unresolved references
# coming from macrobenchmark/uiautomator instrumentation classes.
-dontwarn **
-dontobfuscate
