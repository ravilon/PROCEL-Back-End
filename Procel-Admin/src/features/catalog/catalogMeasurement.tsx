import { CheckCircleOutlined, ErrorOutline, HelpOutline } from "@mui/icons-material";
import type { Medicao } from "../../types";

export type MeasurementStatus = "ALL" | "APPROVED" | "REJECTED" | "UNCLASSIFIED";

export function toDateTimeLocal(date: Date) {
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

export function measurementStatus(measurement: Medicao): Exclude<MeasurementStatus, "ALL"> {
  const results = Object.values(measurement.qualificacoes).flat().map((item) => item.resultado);
  if (results.some((result) => ["ALERTA", "CRITICO", "INVALIDO"].includes(result))) {
    return "REJECTED";
  }
  if (results.some((result) => ["IDEAL", "NORMAL"].includes(result))) {
    return "APPROVED";
  }
  return "UNCLASSIFIED";
}

export const measurementStatusConfig = {
  APPROVED: { label: "Aprovada", color: "success" as const, icon: <CheckCircleOutlined /> },
  REJECTED: { label: "Reprovada", color: "error" as const, icon: <ErrorOutline /> },
  UNCLASSIFIED: { label: "Sem classificação", color: "default" as const, icon: <HelpOutline /> },
};