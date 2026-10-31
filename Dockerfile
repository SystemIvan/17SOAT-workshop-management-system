# Multi-stage build para reduzir tamanho da imagem final
# Stage 1: Build com Maven
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copiar apenas pom.xml primeiro (aproveita cache Docker)
COPY pom.xml mvnw ./
COPY .mvn ./.mvn

# Download das dependências (cacheable layer)
RUN chmod +x mvnw && ./mvnw dependency:go-offline

# Copiar código-fonte
COPY src ./src

# Build da aplicação
RUN ./mvnw clean package -DskipTests

# ---

# Stage 2: Runtime com Java slim
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Informações da imagem
LABEL maintainer="Workshop Management System Team"
LABEL description="Workshop Management System - Service Order API"
LABEL version="1.0.0"

# Criar usuário non-root por segurança
RUN useradd -m -u 1000 appuser

# O JAR fica com dono root e somente leitura para o appuser: o processo não consegue alterar o próprio binário.
COPY --from=builder /app/target/*.jar app.jar

# Heap proporcional ao limite de memória do container (docker/K8s), em vez de um -Xmx fixo.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# UID numérico: o Kubernetes só consegue verificar runAsNonRoot com um UID, não com um nome de usuário.
USER 1000

# Porta padrão do Spring Boot
EXPOSE 8080

# Forma exec (sem shell): a JVM é o PID 1 e recebe o SIGTERM para o desligamento gracioso. A espera pelo banco fica
# com o orquestrador (depends_on no compose, readiness no Kubernetes).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
