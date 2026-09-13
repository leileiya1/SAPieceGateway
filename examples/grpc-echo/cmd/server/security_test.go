package main

import (
	"context"
	"encoding/hex"
	"strconv"
	"testing"
	"time"

	"github.com/sapiece/gateway-grpc-echo/internal/schema"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

func TestSignatureVerifierAcceptsOnceAndRejectsTampering(t *testing.T) {
	secret := "test-secret-with-at-least-32-bytes"
	now := time.UnixMilli(1_700_000_000_000)
	verifier, err := newSignatureVerifier(true, secret, 30*time.Second)
	if err != nil {
		t.Fatal(err)
	}
	verifier.now = func() time.Time { return now }
	info := &grpc.UnaryServerInfo{FullMethod: "/" + schema.ServiceName + "/Echo"}
	handler := func(context.Context, any) (any, error) { return "ok", nil }

	ctx := signedContext(secret, now.UnixMilli(), "42", "POST", "/api/echo", "request-1")
	if _, err := verifier.intercept(ctx, nil, info, handler); err != nil {
		t.Fatalf("valid signature rejected: %v", err)
	}
	if _, err := verifier.intercept(ctx, nil, info, handler); status.Code(err) != codes.Unauthenticated {
		t.Fatalf("replay status=%s, want Unauthenticated", status.Code(err))
	}

	tampered := signedContext(secret, now.UnixMilli(), "42", "POST", "/api/admin", "request-2")
	md, _ := metadata.FromIncomingContext(tampered)
	md = md.Copy()
	md.Set(headerSignedPath, "/api/echo")
	if _, err := verifier.intercept(metadata.NewIncomingContext(context.Background(), md), nil, info, handler); status.Code(err) != codes.Unauthenticated {
		t.Fatalf("tampered status=%s, want Unauthenticated", status.Code(err))
	}
}

func TestSignatureVerifierRejectsExpiredAndWeakConfiguration(t *testing.T) {
	if _, err := newSignatureVerifier(true, "short", 30*time.Second); err == nil {
		t.Fatal("weak secret was accepted")
	}
	secret := "test-secret-with-at-least-32-bytes"
	verifier, err := newSignatureVerifier(true, secret, 30*time.Second)
	if err != nil {
		t.Fatal(err)
	}
	now := time.UnixMilli(1_700_000_000_000)
	verifier.now = func() time.Time { return now }
	ctx := signedContext(secret, now.Add(-time.Minute).UnixMilli(), "0", "GET", "/api/echo", "old")
	info := &grpc.UnaryServerInfo{FullMethod: "/" + schema.ServiceName + "/Echo"}
	_, err = verifier.intercept(ctx, nil, info, func(context.Context, any) (any, error) { return nil, nil })
	if status.Code(err) != codes.Unauthenticated {
		t.Fatalf("expired status=%s, want Unauthenticated", status.Code(err))
	}
}

func signedContext(secret string, timestamp int64, userID, method, path, requestID string) context.Context {
	signature := hex.EncodeToString(signGatewayRequest([]byte(secret), timestamp, userID, method, path, requestID))
	return metadata.NewIncomingContext(context.Background(), metadata.Pairs(
		headerSignatureVersion, "v2",
		headerTimestamp, strconv.FormatInt(timestamp, 10),
		headerUserID, userID,
		headerSignedMethod, method,
		headerSignedPath, path,
		headerRequestID, requestID,
		headerSignature, signature,
	))
}
