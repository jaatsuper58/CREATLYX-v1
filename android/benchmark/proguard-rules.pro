# Benchmark test module: R8 only needs it to match the tested app's
# obfuscation state; no app code lives here. Missing classes below are
# optional runtime references inside instrumentation libraries (R8's own
# missing_rules.txt output suggested exactly these dontwarns).
-dontobfuscate
-dontwarn androidx.arch.core.**
-dontwarn androidx.profileinstaller.**
-dontwarn androidx.startup.**
-dontwarn com.google.errorprone.annotations.**
