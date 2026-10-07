# ChattlyX app module — release shrinking rules.
# Library modules contribute their own consumer rules.

# kotlinx.serialization keeps @Serializable serializers reflectively.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

-keep,includedescriptorclasses class com.chattlyx.**$$serializer { *; }
-keepclassmembers class com.chattlyx.** { *** Companion; }
-keepclasseswithmembers class com.chattlyx.** { kotlinx.serialization.KSerializer serializer(...); }

# Protobuf lite (added when the WS codec lands in Phase 2).
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
