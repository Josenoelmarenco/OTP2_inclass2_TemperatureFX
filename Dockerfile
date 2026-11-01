# JavaFX desktop application.
#
# JavaFX publishes its Linux native libraries for x86_64 only, so the platform
# is pinned. On Apple Silicon Docker Desktop runs this image through emulation,
# exactly as in the SEP1 assignment.
FROM --platform=linux/amd64 maven:3.9-eclipse-temurin-21

# Native libraries JavaFX needs on Linux, a virtual framebuffer for headless
# runs, and the fonts required to display Japanese and Arabic correctly.
RUN apt-get update && apt-get install -y --no-install-recommends \
        libgl1 libglib2.0-0 libgtk-3-0 libxtst6 libxrender1 libxi6 libxxf86vm1 \
        xvfb \
        fonts-noto-core fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Build the application. -U re-resolves the JavaFX platform classifiers.
COPY . .
RUN mvn -q -B -U -DskipTests clean package

# Software rendering (there is no GPU in a container) and UTF-8 everywhere.
ENV _JAVA_OPTIONS="-Dprism.order=sw -Dfile.encoding=UTF-8"
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# The embedded H2 database file is created here.
VOLUME ["/app/data"]

RUN chmod +x /app/entrypoint.sh
ENTRYPOINT ["/app/entrypoint.sh"]
