# Pending Tasks - Futmatch

## 1. User Settings / Profile Editing

### 1.1 Editable Fields
- [ ] **Edit Name** - Campo editable, analizar prevención de fraude (limitar cambios frecuentes)
- [ ] **Edit Email** - Con nuevo flujo de verificación por código
- [ ] **Edit Country** - Cambiar de texto libre a códigos (ej: "MX", "US")
- [ ] **Edit Gender** - Validación mediante códigos de Firebase/Remote Config
- [ ] **Edit Position** - Validación mediante códigos
- [ ] **Edit Language** - Validación mediante códigos

### 1.2 Email Verification Flow (Nuevo)
- [ ] Nuevo flujo para cambio de email:
    1. Usuario ingresa nuevo correo
    2. Envía código de verificación al nuevo correo
    3. Usuario ingresa código
    4. Valida código → actualiza email en BD

---

## 2. Code-based Fields (i18n Support)

Los campos deben usar códigos en lugar de valores hardcoded para soportar multilenguaje.

- [ ] **Gender**: Usar códigos (ej: "M", "F", "OTHER") → Firebase traduce a "Masculino"/"Femenino"
- [ ] **Country**: Cambiar de "Mexico" a código "MX" (como implementación existente en CountryCode)
- [ ] **Position**: Usar códigos (ej: "GK", "DEF", "MID", "FWD")
- [ ] **Language**: Usar códigos (ej: "es_MX", "en_US")

**Backend Changes:**
- [ ] Requests deben recibir códigos en lugar de valores
- [ ] Responses deben retornar códigos en lugar de valores

---

## 3. Profile Statistics

### 3.1 Match History
- [ ] Mostrar historial de partidos jugados por el usuario
- [ ] Indicar resultado: ganado/perdido
- [ ] Mostrar marcador del partido

### 3.2 Last Match Widget
- [ ] Mostrar último partido jugado en perfil propio
- [ ] Mostrar último partido jugado al visitar perfil de otro jugador
- [ ] Mostrar resultado (ganado/perdido) con el marcador

### 3.3 Statistics Design
- [ ] Definir qué estadísticas mostrar (partidos jugados, ganados, perdidos, goles, etc.)
- [ ] Crear endpoint o usar existentes para obtener estadísticas

---

## 4. Frontend Testing

- [ ] Probar nuevos inputs/forms de admin
- [ ] Aplicar nuevas features implementadas en backend (admin panel)
- [ ] Probar flujo completo de email verification

---

## 5. Reglas de Negocio (Pendiente Original)

### Ventana de 6 Horas y Reembolsos
- [ ] **Regla de No Reembolso:**
    - Si el usuario se sale del partido dentro de la ventana de **< 6 horas**:
        - **NO** se emite reembolso automático
        - El estado del pago se mantiene como `CAPTURED`
- [ ] **Alertas en UI:**
    - Mostrar Alert/Modal gigante antes de confirmar salida si está en ventana < 6h
    - Texto: *"Al faltar menos de 6 horas para el partido, tu pago ya ha sido procesado y NO habrá reembolso si te retiras ahora."*
