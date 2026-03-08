# Multi-stage build for minimal image size
# Stage 1: Build
FROM rust:1-bookworm AS builder

WORKDIR /app
COPY Cargo.toml Cargo.lock* ./
# Create dummy src to cache dependencies
RUN mkdir src && echo 'fn main() {}' > src/main.rs
RUN cargo build --release 2>/dev/null || true
# Now copy real source and rebuild
RUN rm -rf src target/release/desk-image target/release/deps/desk_image*
COPY src/ src/
RUN touch src/main.rs && cargo build --release

# Stage 2: Runtime
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/target/release/desk-image /usr/local/bin/desk-image
COPY config.toml /etc/desk-image/config.toml

# Default config overrides via env vars
ENV RUST_LOG=desk_image=info

EXPOSE 8090

ENTRYPOINT ["desk-image"]
CMD ["-c", "/etc/desk-image/config.toml"]
