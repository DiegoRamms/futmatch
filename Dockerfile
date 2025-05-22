# Usa una imagen base ligera con JDK 17
FROM gradle:8.4.0-jdk17 AS build

# Copia tu proyecto al contenedor
COPY . /home/app

# Establece el directorio de trabajo
WORKDIR /home/app

# Compila la app
RUN gradle installDist

# -------------------
# Segunda etapa: contenedor liviano solo con el ejecutable
FROM openjdk:17-jdk-slim

# Define el directorio de trabajo
WORKDIR /app

# Copia desde el build stage el ejecutable
COPY --from=build /home/app/build/install/futmatch /app

# Expone el puerto para que Render lo use
EXPOSE 8080

# Define variable de entorno de puerto (por compatibilidad)
ENV PORT=8080

# Comando de arranque
CMD ["./bin/futmatch"]