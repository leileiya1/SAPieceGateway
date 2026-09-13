#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "Run with sudo: sudo $0" >&2
  exit 1
fi

ready_nodes="$({
  k3s kubectl get nodes -o jsonpath='{range .items[*]}{.metadata.name}{"|"}{.spec.unschedulable}{"|"}{range .status.conditions[?(@.type=="Ready")]}{.status}{end}{"\n"}{end}'
} | awk -F '|' '$2 != "true" && $3 == "True" {count++} END {print count + 0}')"

if (( ready_nodes < 2 )); then
  echo "CoreDNS node-local redundancy requires at least two Ready schedulable nodes; found $ready_nodes" >&2
  exit 1
fi

k3s kubectl -n kube-system patch deployment coredns --type=merge -p '
{
  "spec": {
    "replicas": 2,
    "template": {
      "spec": {
        "topologySpreadConstraints": [
          {
            "maxSkew": 1,
            "topologyKey": "kubernetes.io/hostname",
            "whenUnsatisfiable": "DoNotSchedule",
            "labelSelector": {
              "matchLabels": {
                "k8s-app": "kube-dns"
              }
            }
          }
        ]
      }
    }
  }
}'

k3s kubectl -n kube-system patch service kube-dns --type=merge \
  -p '{"spec":{"internalTrafficPolicy":"Local"}}'
k3s kubectl -n kube-system rollout status deployment/coredns --timeout=180s
k3s kubectl -n kube-system get pods -l k8s-app=kube-dns -o wide
k3s kubectl -n kube-system get service kube-dns \
  -o custom-columns=NAME:.metadata.name,CLUSTER-IP:.spec.clusterIP,INTERNAL-TRAFFIC-POLICY:.spec.internalTrafficPolicy
