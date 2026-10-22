# 注解与签名信息（Room/Hilt 生成的实现类、kotlin 元数据排错都需要）
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# WorkManager：Worker 全限定类名会持久化到 WorkManager 自己的数据库，
# 混淆改名后已排队的任务找不到实现（AGENTS.md：BirthdayReminderWorker 类名不可改）。
-keep class * extends androidx.work.ListenableWorker { *; }

# Room @Entity/@Dao：生成的 *_Impl 直接引用，理论上不需保留；
# 保守保留实体包名防止未来迁移/调试脚本按名查找。
-keep class io.github.xiaohaoz1.jiarenxia.data.db.** { *; }

# kotlinx / kotlin 运行时的常见未解析告警（引用平台不存在的类，属正常）
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn org.jetbrains.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn com.google.errorprone.**
-dontwarn javax.annotation.**
