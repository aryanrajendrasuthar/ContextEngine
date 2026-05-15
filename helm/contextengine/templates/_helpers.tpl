
{{/*
Common labels applied to all resources.
*/}}
{{- define "contextengine.labels" -}}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: contextengine
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end }}

{{/*
Selector labels for a specific service.
*/}}
{{- define "contextengine.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Image reference for a service. Falls back to the service name as the image name.
*/}}
{{- define "contextengine.image" -}}
{{ .Values.global.image.registry }}/{{ .serviceName }}:{{ .Values.global.image.tag }}
{{- end }}
