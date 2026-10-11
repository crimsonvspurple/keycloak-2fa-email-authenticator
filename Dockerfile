# Stage 1: Build the provider
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

# Build the project
RUN mvn clean package -DskipTests

# Stage 2: Create the Keycloak image.
# The provider pom compiles against this same Keycloak version.
FROM quay.io/keycloak/keycloak:26.7.3

# Copy the provider JAR from the builder stage
COPY --from=builder /app/target/keycloak-2fa-email-authenticator-*.jar /opt/keycloak/providers/

# DobberSports login theme. kc.sh build only sees themes that are already in the image.
COPY themes/ds /opt/keycloak/themes/ds
# Git sha, so a theme deploy changes the CSS URL. Cloudflare otherwise tells
# browsers to keep /resources/<keycloak-version>/.../ds.css for four hours.
ARG DS_THEME_REV=dev
RUN sed -i "s|__DS_THEME_REV__|${DS_THEME_REV}|g" /opt/keycloak/themes/ds/login/theme.properties \
    && grep -q "dsVersion=${DS_THEME_REV}" /opt/keycloak/themes/ds/login/theme.properties \
    && /opt/keycloak/bin/kc.sh build

# Expose the port
EXPOSE 8080

# Entrypoint is inherited from the base image
