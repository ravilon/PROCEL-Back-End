import { beforeEach, describe, expect, it, vi } from "vitest";
import { getNumericBucketSummary, listNumericBuckets } from "../../api/analytics";

const session = {
  accessToken: "token",
  tokenType: "Bearer",
  expiresAt: "2099-01-01T00:00:00Z",
  userId: "admin",
  email: "admin@example.com",
  roles: ["ADMIN" as const],
};

const okResponse = {
  ok: true,
  status: 200,
  json: vi.fn().mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
} as unknown as Response;

describe("analytics api", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(okResponse));
  });

  it("serializa filtros de buckets e ignora parametros vazios", async () => {
    await listNumericBuckets({
      from: "2026-08-19T08:00",
      to: "2026-08-19T09:00",
      sensorExternalId: "",
      parametroDefId: "param-1",
      compartimentoId: "room-1",
      aggregationVersion: 2,
      page: 3,
      size: 50,
    }, session);

    const [url, options] = vi.mocked(fetch).mock.calls[0];
    expect(String(url)).toContain("/api/analytics/numeric-buckets?");
    expect(String(url)).toContain("from=");
    expect(String(url)).toContain("to=");
    expect(String(url)).not.toContain("sensorExternalId=");
    expect(String(url)).toContain("parametroDefId=param-1");
    expect(String(url)).toContain("compartimentoId=room-1");
    expect(String(url)).toContain("aggregationVersion=2");
    expect(String(url)).toContain("page=3");
    expect(String(url)).toContain("size=50");
    expect((options?.headers as Headers).get("Authorization")).toBe("Bearer token");
  });

  it("nao envia page e size ao endpoint summary", async () => {
    await getNumericBucketSummary({
      from: "2026-08-19T08:00",
      to: "2026-08-19T09:00",
      parametroDefId: "param-1",
    }, session);

    const [url] = vi.mocked(fetch).mock.calls[0];
    expect(String(url)).toContain("/api/analytics/numeric-buckets/summary?");
    expect(String(url)).toContain("parametroDefId=param-1");
    expect(String(url)).not.toContain("page=");
    expect(String(url)).not.toContain("size=");
  });
});
