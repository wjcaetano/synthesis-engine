#########################
# Build Stage (Java)
#########################
FROM openjdk:25-jdk AS build
ARG VERSION
WORKDIR /home/app

# Copy wrapper & pom first to enable better layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests -Drevision="${VERSION}" dependency:go-offline

# App sources and extras
COPY src ./src
COPY .env .env
RUN mkdir -p proguard
COPY proguard/proguard.conf proguard/proguard.conf

# Package
RUN ./mvnw -B -DskipTests -Drevision="${VERSION}" clean package

#########################
# Python Stage
#  - Provides CPython 3.13.7 + pip-installed libs
#########################
FROM python:3.13.7-slim AS py
WORKDIR /tmp
# Copy only requirements first for better layer caching
COPY requirements.txt .
# Keep this as a single line (hadolint-friendly)
RUN python -m pip install --no-cache-dir pip==25.2 setuptools==80.9.0 && pip install --no-cache-dir -r requirements.txt

#########################
# Execution Stage (Java + Python)
#########################
FROM openjdk:25-slim
ARG VERSION

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/usr/local/bin:${PATH}"

RUN echo "Building version ${VERSION}"
RUN apt-get update && apt-get install -y --no-install-recommends graphviz=2.42.4-3 libfreetype6=2.13.3+dfsg-1 fontconfig=2.15.0-2.3 fonts-noto-cjk=1:20240730+repack1-1 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /home/app/target/synthesis-engine-${VERSION}.jar /app/brsp-synthesis-engine.jar
# Bring in the Python runtime + installed packages
COPY --from=py /usr/local /usr/local

VOLUME /app
VOLUME /data

CMD ["java", "-jar", "brsp-synthesis-engine.jar"]
