# syntax=docker/dockerfile:1.7
# Imagem da API do OrderFlow. Build a partir de backend/: docker build -t orderflow-api .

# --- build: compila e gera o jar (testes e lint rodam no CI, não aqui) ---
FROM eclipse-temurin:21.0.13.1_2-jdk-alpine AS build
WORKDIR /workspace

# Dependências primeiro, para aproveitar o cache de camadas
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/orderflow-*.jar extract --layers --launcher --destination target/extracted

# --- runtime: só o JRE e as camadas do jar, rodando como usuário sem privilégios ---
FROM eclipse-temurin:21.0.13.1_2-jre-alpine
WORKDIR /app

RUN addgroup -S -g 10001 orderflow && adduser -S -u 10001 -G orderflow -H orderflow

# Ordem das camadas: as que mudam menos primeiro
COPY --from=build --chown=orderflow:orderflow /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=orderflow:orderflow /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=orderflow:orderflow /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=orderflow:orderflow /workspace/target/extracted/application/ ./

USER 10001:10001
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
