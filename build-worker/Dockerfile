FROM ghcr.io/cirruslabs/flutter:3.27.0

# Create isolated non-root worker user
RUN groupadd -r sketchworker && useradd -r -g sketchworker -m -d /home/sketchworker sketchworker

# Install Node.js 20 runtime & unzip for isolated job execution
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl ca-certificates unzip gnupg \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/sketch-worker
COPY worker.mjs /opt/sketch-worker/worker.mjs
RUN mkdir -p /opt/sketch-worker/work && chown -R sketchworker:sketchworker /opt/sketch-worker

USER sketchworker
ENV PORT=8090
EXPOSE 8090

CMD ["node", "/opt/sketch-worker/worker.mjs"]
