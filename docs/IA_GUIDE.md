# AI Developer Guide for Futmatch Project

This document serves as the **ultimate context and guide** for any AI assistant or developer working on the `futmatch` project. It details the architecture, coding standards, and the exact workflow required to create new features or endpoints.

**Follow this guide strictly to ensure consistency and avoid common mistakes.**

---

## 1. Project Architecture (Clean Architecture)

The project follows a Clean Architecture approach with Ktor. The data flow is:

`Routing` -> `Controller` -> `Service` -> `Repository` -> `Database (Exposed)`

*   **Routing (`features/<feature>/<Feature>Routing.kt`):** Defines HTTP endpoints, handles authentication/authorization, and delegates to the Controller.
*   **Controller (`features/<feature>/<Feature>Controller.kt`):** Handles HTTP requests/responses, extracts parameters, calls the Service, and formats the response using `AppResult`.
*   **Service (`service/<feature>/<Feature>Service.kt`):** Contains business logic, validations, and returns `AppResult`.
*   **Repository (`data/repository/<feature>/<Feature>Repository.kt`):** Interface for data access.
*   **Repository Implementation (`data/repository/<feature>/<Feature>RepositoryImp.kt`):** Implementation using Exposed SQL.
*   **Database Table (`data/database/<feature>/<Feature>Table.kt`):** Exposed Table definition.
*   **Model (`model/<feature>/<Feature>.kt`):** Data classes (Serializable).

---

## 2. Workflow for Creating a New Feature

When asked to create a new feature (e.g., "Create CRUD for `Tournament`"), follow these steps in order:

### Phase 1: Database & Model
1.  **Create the Model:** Define the data class in `model/<feature>/`.
    *   *Tip:* Use `@Serializable`. For UUIDs, use `@Serializable(with = UUIDSerializer::class)`.
    *   *Tip:* Make `id` nullable (`val id: UUID? = null`) to handle creation (null ID) and retrieval (existing ID) with the same class, or separate into `Request`/`Response` models.
2.  **Create the Table:** Define the Exposed table in `data/database/<feature>/`.
    *   *Tip:* Ensure column types match model types.

### Phase 2: Repository
3.  **Create the Interface:** Define methods in `data/repository/<feature>/`.
4.  **Create the Implementation:** Implement methods in `data/repository/<feature>/`.
    *   *Tip:* Use `dbQuery { ... }` for all database operations.
    *   *Tip:* For string comparisons (search/validation), prefer **exact match** (`eq`) with `.trim()` over `lowerCase()` to avoid collation/encoding issues, unless fuzzy search is explicitly requested.

### Phase 3: Service (Business Logic)
5.  **Create the Service:** In `service/<feature>/`.
    *   **Return Type:** Always return `AppResult<T>`.
    *   **Locale:** Methods that return errors or messages **must** accept `locale: Locale` as a parameter.
    *   **Validations:** Perform business validations here (e.g., "Name already exists").
    *   **Strings:** Use `StringResourcesKey` for all messages. **NEVER hardcode strings.**

### Phase 4: Controller & Validation
6.  **Create Request Validation:** In `features/<feature>/validation/`.
    *   Use Ktor `RequestValidation` plugin.
    *   Register the validation in `config/RequestValidation.kt`.
7.  **Create the Controller:** In `features/<feature>/`.
    *   Extract `locale` using `call.retrieveLocale()`.
    *   Call Service methods passing `locale`.
    *   Respond using `call.respond(appResult)`.

### Phase 5: Routing & DI
8.  **Create Routing:** In `features/<feature>/`.
    *   Define endpoints (`POST`, `GET`, `PUT`, `DELETE`).
    *   **Authorization:** Use `call.requireRole(UserRole.ADMIN, ...)` for protected routes.
    *   Use `call.scope.get<Controller>()` (Koin).
9.  **Register in Koin:**
    *   `di/RepositoryModule.kt`: Bind Repository.
    *   `di/ServiceModule.kt`: Bind Service.
    *   `di/ControllerModule.kt`: Bind Controller (`scopedOf`).
10. **Register Routing:** Add the routing function call in `config/Routing.kt` (inside `authenticate` block if protected).

### Phase 6: Strings (CRITICAL STEP)
11. **Add String Keys:** Add new keys to `utils/StringResourcesKey.kt`.
12. **Add Translations:** Add values to:
    *   `resources/app_strings_en_US.properties`
    *   `resources/app_strings_es_MX.properties`
    *   *Warning:* Failing to do this will result in the UI showing raw keys (e.g., "location_created_success").

---

## 3. Common Utilities & Patterns

### Mappers (Data Conversion)
Create extension functions in `model/<feature>/mapper/<Feature>Mapper.kt` to convert between layers:
*   `Request.toModel()`
*   `Model.toResponse()`
*   `ResultRow.toModel()` (in Repository)

### Timestamps
*   Use `Long` (milliseconds) for all date/time fields.
*   Use `System.currentTimeMillis()` for current time.
*   **Do not** use `LocalDateTime` or `Instant` in models/tables unless strictly necessary.

### Image Uploads
If the feature requires image uploads:
1.  Controller: `call.receiveMultipart()`
2.  Service: Inject `ImageService`.
3.  Use `imageService.saveImages(multipart, path)` which returns metadata.
4.  Store the image key/metadata in the database.

### Money Handling (Prices)
The project uses a hybrid approach:
1.  **API Layer (Request/Response):** Prices are `Long` representing **cents** (e.g., 1050 = $10.50).
2.  **Internal Model & Database:** Prices are `BigDecimal` (e.g., 10.50).

**Conversion Logic (Mapper):**
*   **Request -> Model:** `priceInCents.toBigDecimal().movePointLeft(2)`
*   **Model -> Response:** `this.price.multiply(BigDecimal(100)).longValueExact()`

**Database Definition:**
```kotlin
val price = decimal("price", 10, 2)
```

### Database Syntax (Exposed DSL)
Follow the style in `FieldRepositoryImp.kt`:
*   Wrap everything in `dbQuery { ... }`.
*   Use DSL methods: `insert`, `update`, `deleteWhere`, `selectAll`.
*   Map results manually using private extension functions (e.g., `ResultRow.toModel()`).

### Transaction Management (Advanced)
For complex operations requiring atomicity (rollback on failure), use `DbExecutor`.

**Repository Pattern:**
*   **Simple Methods:** Use `suspend` and wrap logic in `dbQuery`.
*   **Transactional Methods:** Use suffix `Tx` (e.g., `createTx`), do **NOT** use `suspend`, and do **NOT** use `dbQuery`.

```kotlin
// Repository Interface
interface UserRepository {
    suspend fun create(user: User): User // For simple usage
    fun createTx(user: User): User       // For usage inside DbExecutor
}

// Repository Implementation
class UserRepositoryImpl : UserRepository {
    override suspend fun create(user: User): User = dbQuery {
        createTx(user) // Reuse logic
    }

    override fun createTx(user: User): User {
        // Direct Exposed call (NO dbQuery)
        val result = UserTable.insert { ... }
        return result.toUser()
    }
}
```

**Service Usage:**
```kotlin
class UserService(private val dbExecutor: DbExecutor, private val repo: UserRepository) {
    suspend fun complexOperation() {
        dbExecutor.tx {
            // Use Tx methods here!
            val user = repo.createTx(newUser)
            repo.updateTx(otherUser)
            // If this block throws, everything rolls back
        }
    }
}
```

---

## 4. Checklist for AI

Before marking a task as complete, verify:
- [ ] **Strings:** Did I add the keys to `StringResourcesKey.kt` AND both `.properties` files?
- [ ] **DI:** Did I register the new Repository, Service, and Controller in the `di/` modules?
- [ ] **Routing:** Did I call the new routing function in `config/Routing.kt`?
- [ ] **Validation:** Did I register the request validation in `config/RequestValidation.kt`?
- [ ] **Roles:** Did I protect sensitive endpoints with `call.requireRole(...)`?
- [ ] **Trim:** Did I `.trim()` string inputs before saving/validating to avoid whitespace duplicates?
- [ ] **Money:** Did I handle the Long (API) <-> BigDecimal (DB) conversion correctly?
- [ ] **Transactions:** Did I use `DbExecutor` and `Tx` methods for complex atomic operations?
- [ ] **Mappers:** Did I create the mapper file instead of putting logic in the controller?

---

## 5. Questions to Ask the User

If the user request is vague, ask these questions before generating code:

1.  **Model Fields:** What fields should the entity have? (e.g., Name, Address, Price).
2.  **Roles:** Who is allowed to Create/Update/Delete? (Admin, Organizer, Player?).
3.  **Uniqueness:** Should any field be unique? (e.g., Name, Email, Address).
4.  **Public/Private:** Are the GET endpoints public or protected?
