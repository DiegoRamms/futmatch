# Guía Arquitectónica y de Desarrollo

Este documento es la guía de referencia para el desarrollo de nuevas funcionalidades. Su propósito es asegurar la consistencia, calidad y mantenibilidad del código, describiendo los patrones de diseño y la estructura del proyecto. Se utilizará el módulo de **autenticación (`auth`)** como el ejemplo principal a seguir.

## Tabla de Referencia Rápida (Cheatsheet)

| Tarea / Responsabilidad | Archivo Clave / Patrón a Seguir | Ejemplo |
|---|---|---|
| Definir un Endpoint | `src/main/kotlin/features/{feature-name}/{Feature}Routing.kt` | `route("/mi-ruta") { ... }` |
| Validar un Request | `src/main/kotlin/features/{feature-name}/validation/{Request}Validation.kt` | `fun MiRequest.validate()` |
| Orquestar la Petición | `src/main/kotlin/features/{feature-name}/{Feature}Controller.kt` | `val req = call.receive<T>()` |
| Implementar Lógica | `src/main/kotlin/service/{feature-name}/{Feature}Service.kt` | `override suspend fun miFuncion()` |
| Acceder a la BD | `src/main/kotlin/data/repository/{feature-name}Repository.kt` | `return dbQuery { ... }` |
| Manejar Errores | `throw CustomException()` desde el `Service` | `throw NotFoundException()` |
| Usar Textos Localizados | `src/main/kotlin/utils/StringResourcesKey.kt` | `StringResourcesKey.MI_MENSAJE` |
| Registrar Dependencia | `src/main/kotlin/di/{Layer}Module.kt` | `single<MyService> { ... }` |
| Proteger una Ruta | `src/main/kotlin/config/Routing.kt` | `authenticate { ... }` |


## 1. Filosofía y Estructura del Proyecto

El proyecto sigue una arquitectura multicapa (layered architecture) para separar responsabilidades, facilitar las pruebas y mejorar la escalabilidad.

-   **`src/main/kotlin/config/`**: Contiene la configuración inicial de la aplicación y de los plugins de Ktor (Base de datos, Seguridad, Serialización, etc.).
-   **`src/main/kotlin/di/`**: Módulos de Koin para la inyección de dependencias. Separa la creación de instancias de su uso.
-   **`src/main/kotlin/features/`**: Contiene la "capa de presentación". Cada funcionalidad (feature) tiene su propio paquete, que incluye:
    -   `...Routing.kt`: Define los endpoints de la API.
    -   `...Controller.kt`: Orquesta la interacción entre la petición HTTP y los servicios.
    -   `validation/`: Lógica de validación para los DTOs (Data Transfer Objects) de entrada.
-   **`src/main/kotlin/service/`**: La capa de lógica de negocio. Aquí reside el "cerebro" de la aplicación.
-   **`src/main/kotlin/data/`**: La capa de acceso a datos.
    -   `database/`: Define las tablas de la base de datos usando `Exposed`.
    -   `repository/`: Implementa el patrón Repositorio para abstraer el acceso a los datos.
-   **`src/main/kotlin/model/`**: Define los modelos de datos, incluyendo DTOs para requests/responses y modelos de dominio.
-   **`src/main/kotlin/utils/`**: Clases y funciones de utilidad reutilizables (ej. manejo de strings, excepciones personalizadas).

---

## 2. Creando un Nuevo Endpoint: Paso a Paso

Esta es la "receta" a seguir para crear una nueva funcionalidad.

### Paso 1: Definir la Ruta

Toda nueva ruta se define en un archivo `...Routing.kt` dentro del paquete de su `feature`.

-   **Ejemplo (`src/main/kotlin/features/auth/AuthRouting.kt`):**
    ```kotlin
    fun Routing.authRouting() {
        route("/auth") {
            // Inyecta el controlador
            val controller by inject<AuthController>()
    
            post("/signIn") {
                controller.signIn(this.call)
            }
        }
    }
    ```
-   **Para un nuevo endpoint:** Añade tu archivo de rutas y llama a su función principal desde `src/main/kotlin/config/Routing.kt`.

### Paso 2: Validar el Request (DTO)

La validación de los datos de entrada es automática gracias al plugin `RequestValidation`.

1.  **Crea el DTO:** Define una `data class` en `src/main/kotlin/model/{feature-name}/request/` que represente el cuerpo de la petición.
2.  **Crea la Lógica de Validación:** En el paquete `src/main/kotlin/features/{feature-name}/validation/`, crea un archivo para tu DTO y define una función de extensión `validate()`.
    -   **Ejemplo (`src/main/kotlin/features/auth/validation/RegisterUserRequestValidation.kt`):**
        ```kotlin
        fun RegisterUserRequest.validate(): ValidationResult {
            return when {
                !isValidEmail(email) ->
                    ValidationResult.Invalid(StringResourcesKey.REGISTER_EMAIL_INVALID_ERROR.value)
                // ... otras validaciones
                else -> ValidationResult.Valid
            }
        }
        ```
    > **Nota:** Las cadenas de error se obtienen del enum `StringResourcesKey` para soportar localización.

3.  **Registra la Validación:** En `src/main/kotlin/config/RequestValidation.kt`, añade tu DTO al bloque de `install(RequestValidation)`.
    ```kotlin
    install(RequestValidation) {
        validate<MiNuevoRequest> { request ->
            request.validate()
        }
    }
    ```

### Paso 3: Crear el Controlador

El controlador debe ser una capa delgada que conecta la ruta con el servicio.

-   **Responsabilidades:**
    1.  Recibir el `ApplicationCall`.
    2.  Extraer y deserializar el DTO de la petición (`call.receive()`).
    3.  Llamar al método correspondiente en el servicio.
    4.  Enviar la respuesta (`AppResponse`) al cliente.
-   **Ejemplo (`src/main/kotlin/features/auth/AuthController.kt`):**
    ```kotlin
    class AuthController(
        private val authService: AuthService
    ) {
        suspend fun signIn(call: ApplicationCall) {
            val request = call.receive<SignInRequest>()
            val response = authService.signIn(request)
            call.respond(response.appStatus, response)
        }
    }
    ```

### Paso 4: Implementar la Lógica de Negocio en el Servicio

El servicio contiene toda la lógica de negocio y no debe tener conocimiento de la capa HTTP.

-   **Responsabilidades:**
    -   Ejecutar la lógica de la funcionalidad.
    -   Llamar a los repositorios para interactuar con la base de datos.
    -   Lanzar excepciones personalizadas (ej. `ValueAlreadyExistsException`) si algo falla.
    -   Devolver un objeto `AppResponse` que representa el resultado.

### Paso 5: Acceder a la Base de Datos (Repositorios y Transacciones)

-   **Patrón Repositorio:** Toda la interacción con la base de datos debe ocurrir a través de un Repositorio. El repositorio abstrae las consultas a la base de datos (`Exposed`).
-   **Transacciones:** Para asegurar la integridad de los datos, todas las operaciones de base de datos deben ejecutarse dentro de una transacción. El proyecto provee una función `dbQuery` para esto.
    -   **Ejemplo de uso en un Servicio:**
        ```kotlin
        // En src/main/kotlin/service/auth/AuthService.kt
        override suspend fun signIn(request: SignInRequest): AppResponse<AuthResponse> {
            return dbQuery { // <-- Inicia una transacción
                // ... lógica que llama a userRepository.findByEmail(), etc.
            }
        }
        ```
    > La función `dbQuery` se encuentra en `src/main/kotlin/config/Database.kt` y utiliza `newSuspendedTransaction`.

### Paso 6: Inyección de Dependencias (Koin)

Para que tu nuevo `Controller`, `Service` o `Repository` pueda ser utilizado, debe ser registrado en los módulos de Koin (`src/main/kotlin/di/`).

-   **Ejemplo (`src/main/kotlin/di/ServiceModule.kt`):**
    ```kotlin
    val serviceModule = module {
        single<AuthService> { AuthServiceImpl(get(), get(), get()) }
        // single<MiNuevoService> { MiNuevoServiceImpl(get()) }
    }
    ```
    > `get()` le dice a Koin que resuelva e inyecte la dependencia requerida.

---

## 3. Patrones Transversales

### Manejo de Errores

El sistema utiliza un manejador de errores global (`StatusPages`) configurado en `src/main/kotlin/config/Routing.kt`.

-   **Cómo funciona:**
    1.  Desde tu **Servicio**, lanza una de las excepciones personalizadas definidas en `src/main/kotlin/utils/AppExceptions.kt` (ej. `ValueAlreadyExistsException("email")`, `NotFoundException()`).
    2.  El plugin `StatusPages` atrapará la excepción automáticamente.
    3.  Buscará el manejador (`exception<T>{...}`) correspondiente a esa excepción.
    4.  Construirá y enviará una respuesta de error estandarizada (`ErrorResponse`), utilizando el sistema de localización.
-   **Qué hacer:** Simplemente lanza la excepción apropiada desde tu lógica de negocio. No uses bloques `try-catch` en los controladores para esto.

### Localización (i18n)

El sistema soporta respuestas en múltiples idiomas (inglés y español) a través de archivos de propiedades.

-   **Archivos:** `src/main/resources/app_strings_en_US.properties` y `src/main/resources/app_strings_es_MX.properties`.
-   **Claves:** Todas las claves de texto están centralizadas en el enum `src/main/kotlin/utils/StringResourcesKey.kt`.
-   **Uso:** Al devolver un error o mensaje, utiliza una de las claves de `StringResourcesKey`. El sistema de manejo de errores se encargará de traducirla según el header `LAN` de la petición.
    -   **Ejemplo en una validación:** `ValidationResult.Invalid(StringResourcesKey.REGISTER_EMAIL_INVALID_ERROR.value)`

### Seguridad y Rutas Protegidas

-   **Para proteger una ruta:** Envuélvela en un bloque `authenticate` en el archivo de `...Routing.kt` correspondiente.
    ```kotlin
    authenticate {
        get("/mi-ruta-protegida") {
            // ...
        }
    }
    ```
-   **Para obtener el usuario actual:** Dentro de una ruta protegida, usa los helpers del `ApplicationCall`.
    ```kotlin
    val userId = call.getIdentifier() // Devuelve el UUID del usuario.
    ```