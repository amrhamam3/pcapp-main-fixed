plugins {
    kotlin("multiplatform") version "2.0.0"
    id("org.jetbrains.compose") version "1.6.11"
    kotlin("plugin.compose") version "2.0.0"
}

// 1. تحديد مستودعات تحميل مكتبات المطورين للمشروع
repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

// إصدار مكتبات JavaFX (للـ WebView) — نفس classifier الـ Windows لأن الـ build شغال على windows-latest
val javafxVersion = "21.0.2"
val javafxClassifier = "win"

kotlin {
    jvm("jvm") {
        withJava()
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                // مكتبات Compose Desktop كانت ناقصة هنا وده كان بيمنع البناء بالكامل
                implementation(compose.desktop.currentOs)
                implementation(compose.material)
                implementation(compose.foundation)
                implementation(compose.runtime)
                implementation(compose.ui)

                // مكتبات JavaFX بشكل مباشر (org.openjfx.javafxplugin ماكانش بيضيفها
                // لـ Kotlin Multiplatform source sets، فده كان بيسبب "Unresolved reference")
                implementation("org.openjfx:javafx-base:$javafxVersion:$javafxClassifier")
                implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxClassifier")
                implementation("org.openjfx:javafx-controls:$javafxVersion:$javafxClassifier")
                implementation("org.openjfx:javafx-swing:$javafxVersion:$javafxClassifier")
                implementation("org.openjfx:javafx-web:$javafxVersion:$javafxClassifier")
                // javafx-web بيحتاج javafx-media داخليًا (لدعم الفيديو/الصوت جوه الصفحة)
                // من غيرها بيحصل NoClassDefFoundError: com/sun/media/jfxmedia/... لحظة إنشاء WebEngine
                implementation("org.openjfx:javafx-media:$javafxVersion:$javafxClassifier")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi
            )
            packageVersion = "1.0.0"
            description = "Amr3D Nesting Pro Application"

            // بنحدد موديولات الـ JDK صراحةً بدل ما نسيب jlink يكتشفها تلقائي:
            // AWT/Swing/JavaFX الاكتشاف التلقائي بتاعهم بيفشل أحيانًا وبيطلع runtime
            // ناقص موديولات، فالتطبيق بيكراش لحظة الفتح من غير أي رسالة (لأنه GUI بلا console)
            modules(
                "java.desktop",
                "java.logging",
                "java.prefs",
                "java.xml",
                "java.naming",
                "java.scripting",
                "java.management",
                "java.instrument",
                "java.net.http",
                "jdk.unsupported",
                "jdk.unsupported.desktop",
                "jdk.crypto.ec"
            )

            windows {
                // بنفتح نافذة console مؤقتًا عشان لو التطبيق كراش نشوف رسالة الخطأ.
                // بعد ما نتأكد إن كل حاجة شغالة نقدر نرجعها false
                console = true
            }
        }
    }
}
