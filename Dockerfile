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

# Copiar JAR do stage anterior
COPY --from=builder /app/target/*.jar app.jar

# Ownership do arquivo
RUN chown appuser:appuser app.jar

# Trocar para usuário non-root
USER appuser

# Porta padrão do Spring Boot
EXPOSE 8080

# Comando de execução
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--server.port=8080"]
