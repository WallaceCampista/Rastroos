# ─────────────────────────────────────────────────────────────
# Rastro$ — imagem de produção (multi-stage)
#   Stage 1: compila o jar e monta um JRE mínimo (jlink).
#   Stage 2: runtime distroless, non-root, sem shell nem gerenciador
#            de pacotes → superfície de ataque mínima.
#
# Build:  docker build -t rastroos:latest .
# ─────────────────────────────────────────────────────────────

# ── Stage 1: build ───────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# 1) Resolve dependências primeiro (camada cacheável enquanto o pom não muda)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp dependency:go-offline

# 2) Compila e empacota (os testes rodam no CI, não na imagem)
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -DskipTests clean package \
    && cp "$(ls target/*.jar | grep -v -- '-sources\|-javadoc' | head -n1)" app.jar

# 3) JRE mínimo com jlink. Usa o agregador java.se (garante toda a API SE)
#    + módulos jdk.* que libs do Spring/JDBC/observabilidade exigem.
RUN "$JAVA_HOME/bin/jlink" \
      --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.management,jdk.jfr,jdk.naming.dns \
      --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
      --output /javaruntime

# ── Stage 2: runtime (distroless, non-root) ──────────────────
# java-base-debian12 traz as libs nativas (glibc, libz, …) SEM JVM:
# a JVM vem do JRE do jlink acima. Tag :nonroot roda como uid 65532.
FROM gcr.io/distroless/java-base-debian12:nonroot AS runtime
WORKDIR /app

ENV JAVA_HOME=/opt/java \
    PATH="/opt/java/bin:${PATH}" \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/urandom"

COPY --from=build /javaruntime /opt/java
COPY --from=build /workspace/app.jar /app/app.jar

EXPOSE 8080

# Liveness/readiness são expostos em /actuator/health/{liveness,readiness}
# e devem ser checados pelo orquestrador (k8s) — distroless não tem curl.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
