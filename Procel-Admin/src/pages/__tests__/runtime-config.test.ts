import { beforeEach, describe, expect, it, vi } from "vitest";

const session = {
  accessToken: "token",
  tokenType: "Bearer",
  expiresAt: "2099-01-01T00:00:00Z",
  userId: "admin",
  email: "admin@example.com",
  roles: ["ADMIN" as const],
};

describe("runtime config", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockResolvedValue({
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      }),
    }));
    window.__PROCEL_CONFIG__ = {
      API_BASE_URL: "https://api.example.test",
      TELEMETRY_API_URL: "https://telemetry.example.test",
    };
  });

  it("usa API_BASE_URL e TELEMETRY_API_URL injetadas em runtime", async () => {
    const { apiBaseUrl } = await import("../../lib/api");
    const { telemetryApiBaseUrl, listTelemetryEvents } = await import("../../api/telemetry");

    expect(apiBaseUrl).toBe("https://api.example.test");
    expect(telemetryApiBaseUrl).toBe("https://telemetry.example.test");

    await listTelemetryEvents({ page: 0, size: 20 }, session);
    const [url] = vi.mocked(fetch).mock.calls[0];
    expect(String(url)).toContain("https://telemetry.example.test/api/telemetry/events?");
  });
});
