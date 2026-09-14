FROM golang:1.27-alpine@sha256:cf6fca6641884b8433441b2b0652976f975e1d0fdd26d177eaaf8596087f3125 AS builder
ARG GOPROXY=https://proxy.golang.org,direct
ENV GOPROXY=${GOPROXY}
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN mkdir -p /out \
    && CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags='-s -w' -o /out/grpc-echo ./cmd/server \
    && go run ./cmd/descriptor -out /out/echo.pb

FROM scratch AS server
COPY --from=builder /out/grpc-echo /grpc-echo
USER 65532:65532
EXPOSE 9090 8081
ENTRYPOINT ["/grpc-echo"]

FROM envoyproxy/envoy:v1.39.0@sha256:d59f7f5fa10cff6d5892b6c5e7df5c9297ddfb2c3683e33fbfb82da24de4fa66 AS transcoder
COPY --from=builder /out/echo.pb /etc/envoy/echo.pb
COPY envoy/envoy.yaml /etc/envoy/envoy.yaml
