{{/*
Expand the name of the chart.
*/}}
{{- define "verity.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

# FIXME legacy included .Values.service and .Values.env as part of fullname
{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "verity.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}


{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "verity.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Common labels
*/}}
{{- define "verity.labels" -}}
helm.sh/chart: {{ include "verity.chart" . }}
{{ include "verity.selectorLabels" . }}
{{- if .Values.version }}
app.kubernetes.io/version: {{ .Values.version | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "verity.selectorLabels" -}}
app.kubernetes.io/name: {{ include "verity.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "verity.serviceAccountName" -}}
{{- include "verity.fullname" . }}
{{- end }}

{{/*
Vault injection template
*/}}
{{- define "verity.vaultInjectTemplate" -}}
{{`{{ with secret "`}}{{ .Secret }}{{`" -}}`}}
{{`{{ range $k, $v := .Data.data -}}
  export {{ $k }}='{{ $v }}'
{{ end }}
{{- end }}`}}
{{- end }}

{{/*
Vault annotations template
*/}}
{{- define "verity.vaultAnnotationsTemplate" -}}
{{- $credentials := printf "kubernetes_secrets/eks-%s/%s-%s" .Values.gitlab_env .Values.name .Values.service }}
{{- $credentials_tf := printf "kubernetes_secrets/eks-%s/%s-%s-tf" .Values.gitlab_env .Values.name .Values.service }}
{{- $app_confluent := printf "kubernetes_secrets/eks-%s/%s/app-confluent" .Values.gitlab_env .Values.name }}
vault.hashicorp.com/agent-inject: "true"
vault.hashicorp.com/role: "k8s-eks-{{ coalesce .Values.vault_auth_role .Values.env }}"
vault.hashicorp.com/auth-path: "auth/k8s/eks-{{ coalesce .Values.vault_auth_path .Values.gitlab_env }}"
vault.hashicorp.com/agent-inject-secret-credentials: "{{ $credentials }}"
vault.hashicorp.com/agent-inject-secret-credentials-tf: "{{ $credentials_tf }}"
vault.hashicorp.com/agent-inject-secret-app-confluent: "{{ $app_confluent }}"
vault.hashicorp.com/tls-secret: vault-tls-secret
vault.hashicorp.com/ca-cert: /vault/tls/evernym-root-ca.crt
vault.hashicorp.com/agent-pre-populate-only : "true"
vault.hashicorp.com/agent-inject-template-credentials: |
{{ include "verity.vaultInjectTemplate" (dict "Secret" $credentials) | indent 2 }}
vault.hashicorp.com/agent-inject-template-credentials-tf: |
{{ include "verity.vaultInjectTemplate" (dict "Secret" $credentials_tf) | indent 2 }}
vault.hashicorp.com/agent-inject-template-app-confluent: |
{{ include "verity.vaultInjectTemplate" (dict "Secret" $app_confluent) | indent 2 }}
{{- end }}
