package main

import (
	"context"
	"fmt"
	"net/http"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/status"
)

type metricKey struct {
	method string
	code   string
}

type metricValue struct {
	count        uint64
	durationNano uint64
}

type rpcMetrics struct {
	mu     sync.RWMutex
	values map[metricKey]metricValue
}

func newRPCMetrics() *rpcMetrics {
	return &rpcMetrics{values: make(map[metricKey]metricValue)}
}

func (m *rpcMetrics) intercept(
	ctx context.Context, request any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler,
) (any, error) {
	started := time.Now()
	response, err := handler(ctx, request)
	m.record(info.FullMethod, status.Code(err).String(), time.Since(started))
	return response, err
}

func (m *rpcMetrics) interceptStream(
	server any, stream grpc.ServerStream, info *grpc.StreamServerInfo, handler grpc.StreamHandler,
) error {
	started := time.Now()
	err := handler(server, stream)
	m.record(info.FullMethod, status.Code(err).String(), time.Since(started))
	return err
}

func (m *rpcMetrics) record(method, code string, duration time.Duration) {
	key := metricKey{method: method, code: code}
	m.mu.Lock()
	value := m.values[key]
	value.count++
	value.durationNano += uint64(duration)
	m.values[key] = value
	m.mu.Unlock()
}

func (m *rpcMetrics) serveHTTP(response http.ResponseWriter, _ *http.Request) {
	m.mu.RLock()
	keys := make([]metricKey, 0, len(m.values))
	values := make(map[metricKey]metricValue, len(m.values))
	for key, value := range m.values {
		keys = append(keys, key)
		values[key] = value
	}
	m.mu.RUnlock()
	sort.Slice(keys, func(i, j int) bool {
		if keys[i].method == keys[j].method {
			return keys[i].code < keys[j].code
		}
		return keys[i].method < keys[j].method
	})

	response.Header().Set("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
	_, _ = fmt.Fprintln(response, "# HELP grpc_echo_requests_total Completed unary gRPC requests.")
	_, _ = fmt.Fprintln(response, "# TYPE grpc_echo_requests_total counter")
	for _, key := range keys {
		value := values[key]
		_, _ = fmt.Fprintf(response, "grpc_echo_requests_total{method=\"%s\",code=\"%s\"} %d\n",
			prometheusLabel(key.method), prometheusLabel(key.code), value.count)
	}
	_, _ = fmt.Fprintln(response, "# HELP grpc_echo_request_duration_seconds_total Total unary gRPC request time.")
	_, _ = fmt.Fprintln(response, "# TYPE grpc_echo_request_duration_seconds_total counter")
	for _, key := range keys {
		value := values[key]
		_, _ = fmt.Fprintf(response, "grpc_echo_request_duration_seconds_total{method=\"%s\",code=\"%s\"} %.9f\n",
			prometheusLabel(key.method), prometheusLabel(key.code), float64(value.durationNano)/float64(time.Second))
	}
}

func prometheusLabel(value string) string {
	return strings.NewReplacer("\\", "\\\\", "\n", "\\n", "\"", "\\\"").Replace(value)
}

func newOperationalServer(address string, ready *atomic.Bool, metrics *rpcMetrics) *http.Server {
	mux := http.NewServeMux()
	mux.HandleFunc("/live", func(response http.ResponseWriter, _ *http.Request) {
		response.WriteHeader(http.StatusOK)
		_, _ = response.Write([]byte("ok\n"))
	})
	mux.HandleFunc("/ready", func(response http.ResponseWriter, _ *http.Request) {
		if !ready.Load() {
			http.Error(response, "draining", http.StatusServiceUnavailable)
			return
		}
		response.WriteHeader(http.StatusOK)
		_, _ = response.Write([]byte("ready\n"))
	})
	mux.HandleFunc("/metrics", metrics.serveHTTP)
	return &http.Server{
		Addr:              address,
		Handler:           mux,
		ReadHeaderTimeout: 2 * time.Second,
		IdleTimeout:       30 * time.Second,
	}
}
