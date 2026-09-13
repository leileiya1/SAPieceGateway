package schema

import (
	"fmt"

	"google.golang.org/protobuf/reflect/protodesc"
	"google.golang.org/protobuf/reflect/protoreflect"
	"google.golang.org/protobuf/types/descriptorpb"
)

const (
	PackageName = "sapiece.demo.v1"
	ServiceName = PackageName + ".EchoService"
)

// File builds the descriptor used by both the dynamic Go service and Envoy.
// Keeping one descriptor source prevents the demo server and transcoder from
// silently drifting apart without requiring protoc in the runtime image.
func File() (protoreflect.FileDescriptor, error) {
	optional := descriptorpb.FieldDescriptorProto_LABEL_OPTIONAL
	stringType := descriptorpb.FieldDescriptorProto_TYPE_STRING
	int32Type := descriptorpb.FieldDescriptorProto_TYPE_INT32
	int64Type := descriptorpb.FieldDescriptorProto_TYPE_INT64
	proto3 := "proto3"
	name := "sapiece/demo/v1/echo.proto"
	pkg := PackageName

	file := &descriptorpb.FileDescriptorProto{
		Name:    &name,
		Package: &pkg,
		Syntax:  &proto3,
		MessageType: []*descriptorpb.DescriptorProto{
			{
				Name: stringPtr("EchoRequest"),
				Field: []*descriptorpb.FieldDescriptorProto{
					field("message", 1, optional, stringType),
					field("repeat", 2, optional, int32Type),
					field("delay_ms", 3, optional, int32Type),
				},
			},
			{
				Name: stringPtr("FailureRequest"),
				Field: []*descriptorpb.FieldDescriptorProto{
					field("mode", 1, optional, stringType),
				},
			},
			{
				Name: stringPtr("EchoReply"),
				Field: []*descriptorpb.FieldDescriptorProto{
					field("message", 1, optional, stringType),
					field("instance", 2, optional, stringType),
					field("request_id", 3, optional, int64Type),
				},
			},
		},
		Service: []*descriptorpb.ServiceDescriptorProto{
			{
				Name: stringPtr("EchoService"),
				Method: []*descriptorpb.MethodDescriptorProto{
					method("Echo", "."+PackageName+".EchoRequest", "."+PackageName+".EchoReply"),
					streamMethod("StreamEcho", "."+PackageName+".EchoRequest", "."+PackageName+".EchoReply"),
					method("Fail", "."+PackageName+".FailureRequest", "."+PackageName+".EchoReply"),
				},
			},
		},
	}

	descriptor, err := protodesc.NewFile(file, nil)
	if err != nil {
		return nil, fmt.Errorf("build echo descriptor: %w", err)
	}
	return descriptor, nil
}

func field(name string, number int32, label descriptorpb.FieldDescriptorProto_Label,
	typeValue descriptorpb.FieldDescriptorProto_Type) *descriptorpb.FieldDescriptorProto {
	return &descriptorpb.FieldDescriptorProto{Name: &name, Number: &number, Label: &label, Type: &typeValue}
}

func method(name, input, output string) *descriptorpb.MethodDescriptorProto {
	return &descriptorpb.MethodDescriptorProto{Name: &name, InputType: &input, OutputType: &output}
}

func streamMethod(name, input, output string) *descriptorpb.MethodDescriptorProto {
	streaming := true
	return &descriptorpb.MethodDescriptorProto{
		Name: &name, InputType: &input, OutputType: &output, ServerStreaming: &streaming,
	}
}

func stringPtr(value string) *string { return &value }
