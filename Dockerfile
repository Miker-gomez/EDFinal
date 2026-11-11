# ---------- Etapa 1: compilar con Gradle (JDK 21) ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
# gradlew puede llegar con CRLF desde Windows; sin esto falla con "bad interpreter".
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY src ./src
# La caché de Gradle persiste entre builds: solo la primera descarga dependencias.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon \
 && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

# ---------- Etapa 2: ejecutar en JRE 21 ----------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd --system --no-create-home spring \
 && mkdir /app/logs && chown spring /app/logs
COPY --from=build /workspace/app.jar /app/app.jar
USER spring

ENV TZ=America/Bogota
EXPOSE 8080
# exec: la JVM recibe SIGTERM de Docker; -Duser.timezone hace que Java use la misma zona que db.
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -Duser.timezone=${TZ} -jar /app/app.jar"]