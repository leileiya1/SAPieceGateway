package main

import (
	"flag"
	"fmt"
	"os"

	"github.com/sapiece/gateway-grpc-echo/internal/schema"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/reflect/protodesc"
	"google.golang.org/protobuf/types/descriptorpb"
)

func main() {
	output := flag.String("out", "envoy/echo.pb", "descriptor output path")
	flag.Parse()

	file, err := schema.File()
	if err != nil {
		fatal(err)
	}
	encoded, err := proto.Marshal(&descriptorpb.FileDescriptorSet{
		File: []*descriptorpb.FileDescriptorProto{protodesc.ToFileDescriptorProto(file)},
	})
	if err != nil {
		fatal(fmt.Errorf("encode descriptor: %w", err))
	}
	if err := os.WriteFile(*output, encoded, 0o644); err != nil {
		fatal(fmt.Errorf("write descriptor: %w", err))
	}
}

func fatal(err error) {
	fmt.Fprintln(os.Stderr, err)
	os.Exit(1)
}
