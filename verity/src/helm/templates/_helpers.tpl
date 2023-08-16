{{/*
Expand the name of the chart.
*/}}
{{- define "verity.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

# TODO legacy included .Values.service and .Values.env as part of fullname
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
Allow customization of the instance label value.
*/}}
{{- define "verity.instance-name" -}}
{{- default (printf "%s-%s" .Release.Name .Release.Namespace) .Values.instanceLabelOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}


{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "verity.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Service name of the instance
*/}}
{{- define "verity.service" -}}
{{- default (include "verity.instance-name" .) .Values.service | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Common labels
*/}}
{{- define "verity.labels" -}}
{{ include "verity.selectorLabels" . }}
{{ include "verity.akkaLabels" . }}
helm.sh/chart: {{ template "verity.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- if .Values.version }}
app.kubernetes.io/version: {{ .Values.version | quote }}
{{- end }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "verity.selectorLabels" -}}
app.kubernetes.io/name: {{ template "verity.name" . }}
app.kubernetes.io/instance: {{ template "verity.instance-name" . }}
{{- end }}

{{/*
Akka labels
*/}}
{{- define "verity.akkaLabels" -}}
app: {{ template "verity.fullname" . }}
{{- end }}

{{/*
DataDog labels
*/}}
{{- define "verity.datadogLabels" -}}
tags.datadoghq.com/service: {{ include "verity.service" . | quote }}
tags.datadoghq.com/env: {{ .Values.env | quote }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "verity.serviceAccountName" -}}
{{- include "verity.fullname" . }}
{{- end }}
