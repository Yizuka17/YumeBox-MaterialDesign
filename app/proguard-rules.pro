-keep class com.github.yumelira.yumebox.App { *; }
-include ../core/consumer-rules.pro
-keep class androidx.core.app.CoreComponentFactory { *; }

# TextMate uses Joni. Its optional native backend is deliberately not packaged.
-dontwarn io.github.rosemoe.oniguruma.**

# YAML is decoded with SafeConstructor into maps; JavaBeans introspection is not used
# on Android. SnakeYAML's optional desktop bean support references these absent classes.
-dontwarn java.beans.**
# TypeDescription's logger reads TypeDescription.class.getPackage().getName(). R8
# repackaging into the unnamed package makes that null in packed release builds.
-keepnames class org.yaml.snakeyaml.**

# Shizuku / Sui: the API uses hidden-API reflection and the UserService is instantiated by name.
# The moe.shizuku.server AIDL interfaces must keep their names: the Binder descriptor is the
# interface name and the Shizuku server rejects a renamed one.
-keep class moe.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class rikka.sui.** { *; }
-keep class com.github.yumelira.yumebox.runtime.service.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn rikka.sui.**
