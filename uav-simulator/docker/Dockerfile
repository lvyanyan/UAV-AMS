# ---- Stage 1: 编译 ----
FROM golang:1.22-alpine AS builder

RUN apk add --no-cache git gcc musl-dev

WORKDIR /build

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /build/uav-simulator ./cmd/simulator/

# ---- Stage 2: 运行 ----
FROM alpine:3.19

RUN apk add --no-cache ca-certificates tzdata curl
ENV TZ=Asia/Shanghai

WORKDIR /app

COPY --from=builder /build/uav-simulator .
COPY --from=builder /build/config/default.yaml ./config/default.yaml

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

EXPOSE 8080

ENTRYPOINT ["./uav-simulator"]
CMD ["-config", "config/default.yaml"]
