package main

import (
	"context"
	"testing"

	"github.com/sapiece/gateway-grpc-echo/internal/schema"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/reflect/protoreflect"
	"google.golang.org/protobuf/types/dynamicpb"
)

func TestEchoValidationAndResponse(t *testing.T) {
	file, err := schema.File()
	if err != nil {
		t.Fatal(err)
	}
	service := &echoServer{instance: "test-1", reply: file.Messages().ByName("EchoReply")}
	request := dynamicpb.NewMessage(file.Messages().ByName("EchoRequest"))
	request.Set(request.Descriptor().Fields().ByName("message"), protoreflect.ValueOfString("ok"))
	request.Set(request.Descriptor().Fields().ByName("repeat"), protoreflect.ValueOfInt32(2))

	response, err := service.Echo(context.Background(), request)
	if err != nil {
		t.Fatal(err)
	}
	if got := response.Get(response.Descriptor().Fields().ByName("message")).String(); got != "okok" {
		t.Fatalf("message=%q, want okok", got)
	}
	if got := response.Get(response.Descriptor().Fields().ByName("instance")).String(); got != "test-1" {
		t.Fatalf("instance=%q, want test-1", got)
	}

	blank := dynamicpb.NewMessage(file.Messages().ByName("EchoRequest"))
	if _, err := service.Echo(context.Background(), blank); status.Code(err) != codes.InvalidArgument {
		t.Fatalf("blank status=%s, want InvalidArgument", status.Code(err))
	}
}
