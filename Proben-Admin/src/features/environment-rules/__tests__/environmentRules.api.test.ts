import { beforeEach, describe, expect, it, vi } from "vitest";
import { environmentRulesApi } from "../../../api/environmentRules";

const session = {
  accessToken: "test-token",
  tokenType: "Bearer",
  expiresAt: "2099-01-01T00:00:00Z",
  userId: "admin",
  email: "admin@example.com",
  roles: ["ADMIN" as const],
};

describe("environmentRulesApi", () => {
  const fetchMock = vi.fn();
  beforeEach(() => {
    fetchMock.mockReset();
    fetchMock.mockImplementation(() =>
      Promise.resolve(
        new Response(JSON.stringify({ id: "rule-1" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );
    vi.stubGlobal("fetch", fetchMock);
  });

  it("uses dedicated endpoints for dry-run and manual execution", async () => {
    await environmentRulesApi.test("rule-1", "room 1", session);
    await environmentRulesApi.execute("rule-1", "room 1", session);
    expect(fetchMock.mock.calls[0][0]).toContain(
      "/api/environment-rules/rule-1/test",
    );
    expect(fetchMock.mock.calls[0][1].body).toContain(
      '"compartimentoId":"room 1"',
    );
    expect(fetchMock.mock.calls[1][0]).toContain(
      "/api/environment-rules/rule-1/execute?roomId=room%201",
    );
  });

  it("supports precedence preview, multi-room copy, history and safe deletion", async () => {
    await environmentRulesApi.resolve("room/1", session);
    await environmentRulesApi.copy("rule-1", ["room-1", "room-2"], session);
    await environmentRulesApi.executions("rule-1", session);
    await environmentRulesApi.deletePreview("rule-1", session);
    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual(
      expect.arrayContaining([
        expect.stringContaining("/resolution/room%2F1"),
        expect.stringContaining("/rule-1/copy"),
        expect.stringContaining("/executions?ruleId=rule-1"),
        expect.stringContaining("/rule-1/delete-preview"),
      ]),
    );
    expect(fetchMock.mock.calls[1][1].body).toBe(
      JSON.stringify({ compartimentoIds: ["room-1", "room-2"] }),
    );
  });
});
