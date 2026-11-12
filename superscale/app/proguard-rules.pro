# Keep the GLSurfaceView renderer entry points (called reflectively from native GL thread)
-keep class com.superscale.UpscaleView { *; }
-keep class com.superscale.UpscaleRenderer { *; }
