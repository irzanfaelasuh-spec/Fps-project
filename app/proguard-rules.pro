# The Shizuku UserService is loaded by class name from the installed APK.
-keep class com.novaforge.fpsplus.monitor.FrameProbeService { public <init>(); *; }
-keep class com.novaforge.fpsplus.monitor.IFrameProbe { *; }
-keep class com.novaforge.fpsplus.monitor.IFrameProbe$Stub { *; }
