# Resumen del dashboard administrativo de Desktop

## `GET /admin/desktop/dashboard/summary`

Endpoint exclusivo para Futmatch Desktop. Requiere un JWT válido con rol `ADMIN`.

El dashboard móvil tendrá su propio endpoint y contrato, de acuerdo con sus métricas y necesidades de información.

Devuelve exclusivamente los conteos que muestra el dashboard; no carga listas, imágenes ni detalles de sedes, canchas o partidos.

```json
{
  "data": {
    "locationsCount": 3,
    "fieldsCount": 12,
    "matchesCount": 25
  }
}
```

`matchesCount` representa el total histórico de partidos. La definición de "partidos activos/próximos" aún requiere confirmación de negocio antes de sustituir esta métrica.
