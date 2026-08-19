import { beforeEach, describe, expect, it, vi } from "vitest";
import { listTelemetryEvents, reprocessTelemetryEvent } from "../../api/telemetry";
import { adminSession } from "../../test/fixtures/sensorIntegrations";

const okResponse = {
  ok: true,
  status: 200,
  json: vi.fn().mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
} as unknown as Response;

describe("telemetry api", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(okResponse));
  });

  it("uses telemetry base url and sends the current jwt", async () => {
    await listTelemetryEvents({
      source: "MQTT",
      status: "CANONICAL_FAILED",
      sensorId: "sensor-1",
      producerId: "producer-1",
      messageId: "msg-1",
      page: 2,
      size: 20,
    }, adminSession);

    const [url, options] = vi.mocked(fetch).mock.calls[0];
    expect(String(url)).toContain("http://localhost:8081/api/telemetry/events?");
    expect(String(url)).toContain("source=MQTT");
    expect(String(url)).toContain("status=CANONICAL_FAILED");
    expect(String(url)).toContain("sensorId=sensor-1");
    expect(String(url)).toContain("producerId=producer-1");
    expect(String(url)).toContain("messageId=msg-1");
    expect(String(url)).toContain("page=2");
    expect((options?.headers as Headers).get("Authorization")).toBe("Bearer admin-token");
  });

  it("posts only the reprocess reason", async () => {
    await reprocessTelemetryEvent("raw-1", "retry", adminSession);

    const [url, options] = vi.mocked(fetch).mock.calls[0];
    expect(String(url)).toBe("http://localhost:8081/api/telemetry/events/raw-1/reprocess");
    expect(options?.method).toBe("POST");
    expect(options?.body).toBe(JSON.stringify({ reason: "retry" }));
  });
});
