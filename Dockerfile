# Imagen del backend para producción. El frontend se despliega aparte (Vercel) y no entra aquí.
#   docker build -t reservasunimag .
#   docker run --env-file .env.prod -p 8080:8080 reservasunimag
# Arranca con el perfil prod: las variables que pide están en src/main/resources/application-prod.properties.

# --- 1. Compilar el jar. Las pruebas no corren aquí: ya las corre el CI y necesitan Docker (Testcontainers)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Primero el wrapper y los archivos de Gradle, para que las dependencias queden en caché
# mientras no cambie build.gradle
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon -q > /dev/null

COPY src/main src/main
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -q

# El bootJar se parte en capas: las dependencias cambian poco y así no se vuelven a subir en cada versión.
# Se toma el jar que no es -plain, sin depender de la versión de build.gradle
RUN find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} application.jar \; \
    && java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

# --- 2. Imagen final: solo el JRE y la aplicación, con un usuario sin privilegios
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER spring
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=America/Bogota \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
