package schema

import "testing"

func TestFileContainsPublicServiceContract(t *testing.T) {
	file, err := File()
	if err != nil {
		t.Fatal(err)
	}
	service := file.Services().ByName("EchoService")
	if service == nil || string(service.FullName()) != ServiceName {
		t.Fatalf("missing service %s", ServiceName)
	}
	if service.Methods().Len() != 3 {
		t.Fatalf("methods=%d, want 3", service.Methods().Len())
	}
	if !service.Methods().ByName("StreamEcho").IsStreamingServer() {
		t.Fatal("StreamEcho must remain server-streaming")
	}
}
