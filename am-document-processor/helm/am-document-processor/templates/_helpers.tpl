{{/*
Expand the name of the chart.
*/}}
{{- define "am-document-processor.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "am-document-processor.fullname" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "am-document-processor.labels" -}}
app.kubernetes.io/name: {{ include "am-document-processor.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "am-document-processor.selectorLabels" -}}
app.kubernetes.io/name: {{ include "am-document-processor.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Infrastructure service names
*/}}
{{- define "am-document-processor.postgresql.fullname" -}}
{{- .Values.postgresql.fullname }}
{{- end }}

{{- define "am-document-processor.kafka.fullname" -}}
{{- .Values.kafka.bootstrapServers }}
{{- end }}
