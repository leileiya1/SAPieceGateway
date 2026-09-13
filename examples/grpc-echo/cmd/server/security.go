package main

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/sapiece/gateway-grpc-echo/internal/schema"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

const (
	headerSignatureVersion = "x-gateway-signature-version"
	headerTimestamp        = "x-gateway-timestamp"
	headerUserID           = "x-user-id"
	headerRequestID        = "x-gateway-request-id"
	headerSignedMethod     = "x-gateway-signed-method"
	headerSignedPath       = "x-gateway-signed-path"
	headerSignature        = "x-gateway-signature"
)

type signatureVerifier struct {
	required bool
	secret   []byte
	maxAge   time.Duration
	now      func() time.Time

	mu   sync.Mutex
	seen map[string]time.Time
}

func newSignatureVerifier(required bool, secret string, maxAge time.Duration) (*signatureVerifier, error) {
	if required && len(secret) < 32 {
		return nil, status.Error(codes.FailedPrecondition, "GATEWAY_DOWNSTREAM_SECRET must contain at least 32 bytes")
	}
	return &signatureVerifier{
		required: required,
		secret:   []byte(secret),
		maxAge:   maxAge,
		now:      time.Now,
		seen:     make(map[string]time.Time),
	}, nil
}

func (v *signatureVerifier) intercept(
	ctx context.Context, request any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler,
) (any, error) {
	if err := v.verify(ctx, info.FullMethod); err != nil {
		return nil, err
	}
	return handler(ctx, request)
}

func (v *signatureVerifier) interceptStream(
	server any, stream grpc.ServerStream, info *grpc.StreamServerInfo, handler grpc.StreamHandler,
) error {
	if err := v.verify(stream.Context(), info.FullMethod); err != nil {
		return err
	}
	return handler(server, stream)
}

func (v *signatureVerifier) verify(ctx context.Context, fullMethod string) error {
	if !v.required || !strings.HasPrefix(fullMethod, "/"+schema.ServiceName+"/") {
		return nil
	}
	md, ok := metadata.FromIncomingContext(ctx)
	if !ok {
		return status.Error(codes.Unauthenticated, "gateway signature metadata is missing")
	}
	version, ok := one(md, headerSignatureVersion)
	if !ok || version != "v2" {
		return status.Error(codes.Unauthenticated, "unsupported gateway signature version")
	}
	timestampText, okTimestamp := one(md, headerTimestamp)
	userID, okUser := oneOrDefault(md, headerUserID, "0")
	method, okMethod := one(md, headerSignedMethod)
	path, okPath := one(md, headerSignedPath)
	requestID, okRequest := one(md, headerRequestID)
	signature, okSignature := one(md, headerSignature)
	if !(okTimestamp && okUser && okMethod && okPath && okRequest && okSignature) {
		return status.Error(codes.Unauthenticated, "gateway signature metadata is incomplete")
	}
	timestamp, err := strconv.ParseInt(timestampText, 10, 64)
	if err != nil {
		return status.Error(codes.Unauthenticated, "gateway timestamp is invalid")
	}
	now := v.now()
	signedAt := time.UnixMilli(timestamp)
	if signedAt.Before(now.Add(-v.maxAge)) || signedAt.After(now.Add(5*time.Second)) {
		return status.Error(codes.Unauthenticated, "gateway signature has expired")
	}
	expected := signGatewayRequest(v.secret, timestamp, userID, method, path, requestID)
	actual, err := hex.DecodeString(signature)
	if err != nil || !hmac.Equal(expected, actual) {
		return status.Error(codes.Unauthenticated, "gateway signature is invalid")
	}
	if !v.acceptOnce(requestID, now) {
		return status.Error(codes.Unauthenticated, "gateway request was replayed")
	}
	return nil
}

func (v *signatureVerifier) acceptOnce(requestID string, now time.Time) bool {
	v.mu.Lock()
	defer v.mu.Unlock()
	for id, expiresAt := range v.seen {
		if !expiresAt.After(now) {
			delete(v.seen, id)
		}
	}
	if _, exists := v.seen[requestID]; exists {
		return false
	}
	v.seen[requestID] = now.Add(v.maxAge)
	return true
}

func signGatewayRequest(secret []byte, timestamp int64, userID, method, path, requestID string) []byte {
	message := "timestamp=" + strconv.FormatInt(timestamp, 10) +
		"&userId=" + userID +
		"&method=" + strings.ToUpper(method) +
		"&path=" + path +
		"&requestId=" + requestID
	mac := hmac.New(sha256.New, secret)
	_, _ = mac.Write([]byte(message))
	return mac.Sum(nil)
}

func one(md metadata.MD, key string) (string, bool) {
	values := md.Get(key)
	return firstOnly(values)
}

func oneOrDefault(md metadata.MD, key, fallback string) (string, bool) {
	values := md.Get(key)
	if len(values) == 0 {
		return fallback, true
	}
	return firstOnly(values)
}

func firstOnly(values []string) (string, bool) {
	if len(values) != 1 || strings.TrimSpace(values[0]) == "" {
		return "", false
	}
	return values[0], true
}
