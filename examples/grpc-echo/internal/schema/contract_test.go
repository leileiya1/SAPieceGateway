package schema

import (
	"os"
	"regexp"
	"strconv"
	"testing"
)

func TestCheckedInProtoMatchesRuntimeDescriptor(t *testing.T) {
	source, err := os.ReadFile("../../proto/echo.proto")
	if err != nil {
		t.Fatal(err)
	}
	file, err := File()
	if err != nil {
		t.Fatal(err)
	}
	text := string(source)
	if !regexp.MustCompile(`(?m)^package\s+` + regexp.QuoteMeta(PackageName) + `\s*;`).MatchString(text) {
		t.Fatalf("proto package does not match %s", PackageName)
	}
	service := file.Services().ByName("EchoService")
	for index := 0; index < service.Methods().Len(); index++ {
		method := service.Methods().Get(index)
		streamToken := ""
		if method.IsStreamingServer() {
			streamToken = `stream\s+`
		}
		pattern := `(?m)rpc\s+` + regexp.QuoteMeta(string(method.Name())) + `\s*\(\s*` +
			regexp.QuoteMeta(string(method.Input().Name())) + `\s*\)\s*returns\s*\(\s*` +
			streamToken + regexp.QuoteMeta(string(method.Output().Name())) + `\s*\)`
		if !regexp.MustCompile(pattern).MatchString(text) {
			t.Fatalf("runtime method %s is missing or different in echo.proto", method.Name())
		}
	}
	for index := 0; index < file.Messages().Len(); index++ {
		message := file.Messages().Get(index)
		for fieldIndex := 0; fieldIndex < message.Fields().Len(); fieldIndex++ {
			field := message.Fields().Get(fieldIndex)
			pattern := `(?m)^\s*` + regexp.QuoteMeta(protoScalarName(field.Kind().String())) + `\s+` +
				regexp.QuoteMeta(string(field.Name())) + `\s*=\s*` + strconv.Itoa(int(field.Number())) + `\s*;`
			if !regexp.MustCompile(pattern).MatchString(text) {
				t.Fatalf("runtime field %s.%s is missing or different in echo.proto", message.Name(), field.Name())
			}
		}
	}
}

func protoScalarName(kind string) string {
	if kind == "int32" || kind == "int64" || kind == "string" {
		return kind
	}
	return regexp.QuoteMeta(kind)
}
