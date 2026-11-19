# Stage 1: Build the provider
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

# Build the project
RUN mvn clean package -DskipTests

# Stage 2: Create the Keycloak image.
# The provider pom compiles against this same Keycloak version.
# quay.io/keycloak/keycloak:26.8.2 was rebuilt on 2026-11-19 with patched
# Java 21.0.13.1 (RHSA-2026:100567). The digest is the multi-arch index.
FROM quay.io/keycloak/keycloak:26.8.2@sha256:7e66a96e4739239070aeaf72e4c5decf0f7f682cfc725b9f7ea2b336eaba38b3

# Refuse an older base image. 21.0.13.0 still has the HotSpot Base64 intrinsic bug.
RUN ver="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/.*java\.runtime\.version *= *//p' | sed 's/[[:space:]]//g')" \
    && [ -n "$ver" ] \
    && echo "java.runtime.version=${ver}" \
    && base="${ver%%+*}" \
    && base="${base%%-*}" \
    && IFS=. \
    && set -- $base \
    && maj="${1:-0}" min="${2:-0}" sec="${3:-0}" patch="${4:-0}" \
    && case "${maj}${min}${sec}${patch}" in *[!0-9]*) echo "Unparseable Java version ${ver}" >&2; exit 1 ;; esac \
    && ok=0 \
    && if [ "$maj" -gt 21 ] \
        || { [ "$maj" -eq 21 ] && [ "$min" -gt 0 ]; } \
        || { [ "$maj" -eq 21 ] && [ "$min" -eq 0 ] && [ "$sec" -gt 13 ]; } \
        || { [ "$maj" -eq 21 ] && [ "$min" -eq 0 ] && [ "$sec" -eq 13 ] && [ "$patch" -ge 1 ]; }; then \
         ok=1; \
       fi \
    && if [ "$ok" -ne 1 ]; then echo "Java ${ver} is older than 21.0.13.1" >&2; exit 1; fi \
    && echo "Java ${ver} meets the 21.0.13.1 minimum"

# Copy the provider JAR from the builder stage
COPY --from=builder /app/target/keycloak-2fa-email-authenticator-*.jar /opt/keycloak/providers/

# DobberSports login theme. kc.sh build only sees themes that are already in the image.
COPY themes/ds /opt/keycloak/themes/ds
# Git sha, so a theme deploy changes the CSS URL. Cloudflare otherwise tells
# browsers to keep /resources/<keycloak-version>/.../ds.css for four hours.
# The base image drops to uid 1000, which cannot rewrite the copied theme.
USER root
ARG DS_THEME_REV=dev
RUN sed -i "s|__DS_THEME_REV__|${DS_THEME_REV}|g" /opt/keycloak/themes/ds/login/theme.properties \
    && grep -q "dsVersion=${DS_THEME_REV}" /opt/keycloak/themes/ds/login/theme.properties \
    && chown -R 1000:0 /opt/keycloak/themes/ds \
    && /opt/keycloak/bin/kc.sh build
USER 1000

# Expose the port
EXPOSE 8080

# Entrypoint is inherited from the base image
