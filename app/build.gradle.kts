import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import groovy.json.JsonOutput
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "top.aidanrao.buaa_classhopper"
    compileSdk = 34
    
    buildFeatures {
        buildConfig = true
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }

    val accessPolicyJson = System.getenv("ICLASS_ACCESS_POLICY_JSON")?.trim().orEmpty()
    if (accessPolicyJson.isNotEmpty()) {
        val valid = runCatching {
            JsonReader(StringReader(accessPolicyJson)).use { reader ->
                reader.isLenient = false
                fun consume() {
                    when (reader.peek()) {
                        JsonToken.BEGIN_OBJECT -> { reader.beginObject(); while (reader.hasNext()) { reader.nextName(); consume() }; reader.endObject() }
                        JsonToken.BEGIN_ARRAY -> { reader.beginArray(); while (reader.hasNext()) consume(); reader.endArray() }
                        JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                        JsonToken.BOOLEAN -> reader.nextBoolean()
                        JsonToken.NULL -> reader.nextNull()
                        else -> error("Invalid JSON")
                    }
                }
                consume()
                require(reader.peek() == JsonToken.END_DOCUMENT)
            }
            val policy = JsonParser.parseString(accessPolicyJson).asJsonObject
            val version = policy.get("schemaVersion")
            val revision = policy.get("revision")
            version != null && version.isJsonPrimitive && version.asJsonPrimitive.isNumber && version.toString() == "1" &&
                revision != null && revision.isJsonPrimitive && revision.asJsonPrimitive.isString && revision.asString.isNotBlank() &&
                listOf("studentIds", "names").all { key ->
                    val entries = policy.get(key)
                    entries != null && entries.isJsonArray && entries.asJsonArray.all {
                        it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank()
                    }
                }
        }.getOrDefault(false)
        require(valid) { "ICLASS_ACCESS_POLICY_JSON must contain schemaVersion=1, revision, studentIds and names as strict JSON" }
    }

    defaultConfig {
        buildConfigField("String", "ICLASS_ACCESS_POLICY_JSON", JsonOutput.toJson(accessPolicyJson))
        applicationId = "top.aidanrao.buaa_classhopper"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.1"

        buildConfigField("String", "APP_SECRET", "\"${System.getenv("APP_SECRET") ?: localProperties.getProperty("APP_SECRET", "")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = project.file("keystore.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation(libs.androidx.security.crypto)
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    
    testImplementation(libs.junit)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    testImplementation("org.mockito:mockito-core:5.12.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
