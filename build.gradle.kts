// Vertical Slice Architecture (modular monolith).
// Cada :feature:* es un slice vertical autocontenido (UI + lógica + datos).
// Los :core:* solo albergan infraestructura compartida sin lógica de negocio.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    // En el classpath; se aplican en :app sólo si existe google-services.json.
    alias(libs.plugins.gms.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
