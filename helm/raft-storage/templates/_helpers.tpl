{{/*
Expand the name of the chart.
*/}}
{{- define "raft-storage.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "raft-storage.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "raft-storage.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "raft-storage.labels" -}}
helm.sh/chart: {{ include "raft-storage.chart" . }}
{{ include "raft-storage.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "raft-storage.selectorLabels" -}}
app.kubernetes.io/name: {{ include "raft-storage.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "raft-storage.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "raft-storage.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Create the name of the namespace to use
*/}}
{{- define "raft-storage.namespaceName" -}}
{{- if .Values.namespace.create }}
{{- .Values.namespace.name }}
{{- else }}
{{- "default" }}
{{- end }}
{{- end }}

{{/*
Create Redis connection string
*/}}
{{- define "raft-storage.redisHost" -}}
{{- if .Values.redis.enabled }}
{{- printf "%s-redis-master" (include "raft-storage.fullname" .) }}
{{- else }}
{{- .Values.redis.external.host }}
{{- end }}
{{- end }}

{{/*
Create Prometheus endpoint
*/}}
{{- define "raft-storage.prometheusEndpoint" -}}
{{- if .Values.monitoring.prometheus.enabled }}
{{- printf "%s-prometheus-server" (include "raft-storage.fullname" .) }}
{{- else }}
{{- .Values.monitoring.prometheus.external.endpoint }}
{{- end }}
{{- end }}

{{/*
Create Grafana endpoint
*/}}
{{- define "raft-storage.grafanaEndpoint" -}}
{{- if .Values.monitoring.grafana.enabled }}
{{- printf "%s-grafana" (include "raft-storage.fullname" .) }}
{{- else }}
{{- .Values.monitoring.grafana.external.endpoint }}
{{- end }}
{{- end }}
