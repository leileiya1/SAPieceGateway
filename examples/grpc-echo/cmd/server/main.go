package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/sapiece/gateway-grpc-echo/internal/schema"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/health"
	healthpb "google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/keepalive"
	"google.golang.org/grpc/reflection"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/reflect/protoreflect"
	"google.golang.org/protobuf/reflect/protoregistry"
	"google.golang.org/protobuf/types/dynamicpb"
)

const defaultAddress = ":9090"

type echoServiceServer interface {
	Echo(context.Context, *dynamicpb.Message) (*dynamicpb.Message, error)
	StreamEcho(*dynamicpb.Message, grpc.ServerStream) error
	Fail(context.Context, *dynamicpb.Message) (*dynamicpb.Message, error)
}

type echoServer struct {
	instance string
	reply    protoreflect.MessageDescriptor
	sequence atomic.Int64
}

func (s *echoServer) Echo(ctx context.Context, request *dynamicpb.Message) (*dynamicpb.Message, error) {
	message := request.Get(request.Descriptor().Fields().ByName("message")).String()
	repeat := int(request.Get(request.Descriptor().Fields().ByName("repeat")).Int())
	delay := int(request.Get(request.Descriptor().Fields().ByName("delay_ms")).Int())
	if strings.TrimSpace(message) == "" {
		return nil, status.Error(codes.InvalidArgument, "message must not be blank")
	}
	if repeat == 0 {
		repeat = 1
	}
	if repeat < 1 || repeat > 8 {
		return nil, status.Error(codes.InvalidArgument, "repeat must be between 1 and 8")
	}
	if delay < 0 || delay > 5000 {
		return nil, status.Error(codes.InvalidArgument, "delay_ms must be between 0 and 5000")
	}
	if delay > 0 {
		timer := time.NewTimer(time.Duration(delay) * time.Millisecond)
		defer timer.Stop()
		select {
		case <-timer.C:
		case <-ctx.Done():
			return nil, status.FromContextError(ctx.Err()).Err()
		}
	}

	response := dynamicpb.NewMessage(s.reply)
	setString(response, "message", strings.Repeat(message, repeat))
	setString(response, "instance", s.instance)
	setInt64(response, "request_id", s.sequence.Add(1))
	return response, nil
}

func (s *echoServer) Fail(_ context.Context, request *dynamicpb.Message) (*dynamicpb.Message, error) {
	mode := request.Get(request.Descriptor().Fields().ByName("mode")).String()
	switch mode {
	case "invalid":
		return nil, status.Error(codes.InvalidArgument, "requested invalid argument")
	case "unavailable":
		return nil, status.Error(codes.Unavailable, "requested unavailable dependency")
	default:
		return nil, status.Error(codes.FailedPrecondition, "mode must be invalid or unavailable")
	}
}

func (s *echoServer) StreamEcho(request *dynamicpb.Message, stream grpc.ServerStream) error {
	message := request.Get(request.Descriptor().Fields().ByName("message")).String()
	repeat := int(request.Get(request.Descriptor().Fields().ByName("repeat")).Int())
	if strings.TrimSpace(message) == "" {
		return status.Error(codes.InvalidArgument, "message must not be blank")
	}
	if repeat == 0 {
		repeat = 1
	}
	if repeat < 1 || repeat > 8 {
		return status.Error(codes.InvalidArgument, "repeat must be between 1 and 8")
	}
	for index := 1; index <= repeat; index++ {
		if err := stream.Context().Err(); err != nil {
			return status.FromContextError(err).Err()
		}
		response := dynamicpb.NewMessage(s.reply)
		setString(response, "message", fmt.Sprintf("%s-%d", message, index))
		setString(response, "instance", s.instance)
		setInt64(response, "request_id", s.sequence.Add(1))
		if err := stream.SendMsg(response); err != nil {
			return err
		}
	}
	return nil
}

func main() {
	address := envOr("GRPC_ADDRESS", defaultAddress)
	operationsAddress := envOr("OPERATIONS_ADDRESS", ":8081")
	instance := envOr("INSTANCE_ID", "local")
	drainDelay := durationEnv("TERMINATION_DRAIN_DELAY", 4*time.Second)
	signatureMaxAge := durationEnv("GATEWAY_SIGNATURE_MAX_AGE", 30*time.Second)
	verifier, err := newSignatureVerifier(
		boolEnv("GATEWAY_HMAC_REQUIRED", false), os.Getenv("GATEWAY_DOWNSTREAM_SECRET"), signatureMaxAge)
	if err != nil {
		panic(err)
	}
	file, err := schema.File()
	if err != nil {
		panic(err)
	}
	if err := protoregistry.GlobalFiles.RegisterFile(file); err != nil && !errors.Is(err, protoregistry.NotFound) {
		slog.Warn("descriptor registration skipped", "error", err)
	}
	service := file.Services().ByName("EchoService")
	if service == nil {
		panic("EchoService descriptor is missing")
	}
	listener, err := net.Listen("tcp", address)
	if err != nil {
		panic(fmt.Errorf("listen %s: %w", address, err))
	}

	metrics := newRPCMetrics()
	serverOptions := []grpc.ServerOption{
		grpc.MaxRecvMsgSize(1 << 20),
		grpc.MaxSendMsgSize(1 << 20),
		grpc.MaxConcurrentStreams(256),
		grpc.KeepaliveEnforcementPolicy(keepalive.EnforcementPolicy{MinTime: 10 * time.Second, PermitWithoutStream: true}),
		grpc.KeepaliveParams(keepalive.ServerParameters{MaxConnectionAge: 5 * time.Minute, MaxConnectionAgeGrace: 30 * time.Second}),
		grpc.ChainUnaryInterceptor(metrics.intercept, verifier.intercept),
		grpc.ChainStreamInterceptor(metrics.interceptStream, verifier.interceptStream),
	}
	if boolEnv("GRPC_MTLS_ENABLED", false) {
		tlsOption, tlsErr := mtlsServerOption(
			envOr("GRPC_TLS_CERT_FILE", "/etc/grpc-tls/tls.crt"),
			envOr("GRPC_TLS_KEY_FILE", "/etc/grpc-tls/tls.key"),
			envOr("GRPC_TLS_CLIENT_CA_FILE", "/etc/grpc-tls/ca.crt"))
		if tlsErr != nil {
			panic(tlsErr)
		}
		serverOptions = append(serverOptions, tlsOption)
	}
	server := grpc.NewServer(serverOptions...)
	implementation := &echoServer{instance: instance, reply: file.Messages().ByName("EchoReply")}
	server.RegisterService(serviceDescription(service), implementation)
	healthServer := health.NewServer()
	healthpb.RegisterHealthServer(server, healthServer)
	healthServer.SetServingStatus("", healthpb.HealthCheckResponse_SERVING)
	healthServer.SetServingStatus(schema.ServiceName, healthpb.HealthCheckResponse_SERVING)
	reflection.Register(server)
	var ready atomic.Bool
	operations := newOperationalServer(operationsAddress, &ready, metrics)
	go func() {
		slog.Info("operations server listening", "address", operationsAddress)
		if err := operations.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("operations server stopped unexpectedly", "error", err)
		}
	}()

	stopped := make(chan struct{})
	go func() {
		defer close(stopped)
		slog.Info("gRPC echo server listening", "address", address, "instance", instance)
		if err := server.Serve(listener); err != nil && !errors.Is(err, grpc.ErrServerStopped) {
			slog.Error("gRPC server stopped unexpectedly", "error", err)
		}
	}()
	ready.Store(true)

	signals := make(chan os.Signal, 1)
	signal.Notify(signals, syscall.SIGINT, syscall.SIGTERM)
	<-signals
	slog.Info("draining gRPC server", "delay", drainDelay, "instance", instance)
	ready.Store(false)
	healthServer.Shutdown()
	time.Sleep(drainDelay)
	done := make(chan struct{})
	go func() {
		server.GracefulStop()
		close(done)
	}()
	select {
	case <-done:
	case <-time.After(8 * time.Second):
		server.Stop()
	}
	<-stopped
	shutdownContext, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	_ = operations.Shutdown(shutdownContext)
}

func serviceDescription(service protoreflect.ServiceDescriptor) *grpc.ServiceDesc {
	echo := service.Methods().ByName("Echo")
	streamEcho := service.Methods().ByName("StreamEcho")
	fail := service.Methods().ByName("Fail")
	return &grpc.ServiceDesc{
		ServiceName: schema.ServiceName,
		HandlerType: (*echoServiceServer)(nil),
		Methods: []grpc.MethodDesc{
			{MethodName: "Echo", Handler: unaryHandler(echo, func(ctx context.Context, server echoServiceServer, request *dynamicpb.Message) (*dynamicpb.Message, error) {
				return server.Echo(ctx, request)
			})},
			{MethodName: "Fail", Handler: unaryHandler(fail, func(ctx context.Context, server echoServiceServer, request *dynamicpb.Message) (*dynamicpb.Message, error) {
				return server.Fail(ctx, request)
			})},
		},
		Streams: []grpc.StreamDesc{
			{
				StreamName:    "StreamEcho",
				ServerStreams: true,
				Handler: func(server any, stream grpc.ServerStream) error {
					request := dynamicpb.NewMessage(streamEcho.Input())
					if err := stream.RecvMsg(request); err != nil {
						return err
					}
					return server.(echoServiceServer).StreamEcho(request, stream)
				},
			},
		},
		Metadata: "sapiece/demo/v1/echo.proto",
	}
}

type unaryInvoker func(context.Context, echoServiceServer, *dynamicpb.Message) (*dynamicpb.Message, error)

func unaryHandler(method protoreflect.MethodDescriptor, invoke unaryInvoker) grpc.MethodHandler {
	return func(server any, ctx context.Context, decode func(any) error, interceptor grpc.UnaryServerInterceptor) (any, error) {
		request := dynamicpb.NewMessage(method.Input())
		if err := decode(request); err != nil {
			return nil, err
		}
		if interceptor == nil {
			return invoke(ctx, server.(echoServiceServer), request)
		}
		info := &grpc.UnaryServerInfo{Server: server, FullMethod: "/" + schema.ServiceName + "/" + string(method.Name())}
		handler := func(callCtx context.Context, value any) (any, error) {
			return invoke(callCtx, server.(echoServiceServer), value.(*dynamicpb.Message))
		}
		return interceptor(ctx, request, info, handler)
	}
}

func setString(message *dynamicpb.Message, name protoreflect.Name, value string) {
	message.Set(message.Descriptor().Fields().ByName(name), protoreflect.ValueOfString(value))
}

func setInt64(message *dynamicpb.Message, name protoreflect.Name, value int64) {
	message.Set(message.Descriptor().Fields().ByName(name), protoreflect.ValueOfInt64(value))
}

func envOr(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}

func durationEnv(name string, fallback time.Duration) time.Duration {
	value := os.Getenv(name)
	if value == "" {
		return fallback
	}
	parsed, err := time.ParseDuration(value)
	if err != nil || parsed < 0 || parsed > 30*time.Second {
		return fallback
	}
	return parsed
}

func boolEnv(name string, fallback bool) bool {
	value := strings.TrimSpace(strings.ToLower(os.Getenv(name)))
	switch value {
	case "true", "1", "yes":
		return true
	case "false", "0", "no":
		return false
	default:
		return fallback
	}
}
