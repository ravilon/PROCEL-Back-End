import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  activateIntegrationBinding,
  activateIntegrationProfile,
  activateParserVersion,
  createIntegrationBinding,
  createIntegrationProfile,
  createParserVersion,
  deactivateIntegrationBinding,
  deactivateIntegrationProfile,
  getIntegrationProfile,
  getIntegrationSnapshot,
  listIntegrationBindings,
  listIntegrationProfiles,
  listParserVersions,
  searchCatalogSensors,
  updateIntegrationProfile,
  updateParserVersion,
} from "../../../api/sensorIntegrations";
import { adminSession } from "../../../test/fixtures/sensorIntegrations";

const okResponse = {
  ok: true,
  status: 200,
  json: vi.fn().mockResolvedValue({}),
} as unknown as Response;

describe("sensor integration api", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(okResponse));
  });

  it("does not call ingestion routes from admin services", async () => {
    await listIntegrationProfiles(true, adminSession);
    await getIntegrationProfile("profile-1", adminSession);
    await createIntegrationProfile({ nome: "P", descricao: null, source: "REST" }, adminSession);
    await updateIntegrationProfile("profile-1", { nome: "P", descricao: null, source: null }, adminSession);
    await activateIntegrationProfile("profile-1", adminSession);
    await deactivateIntegrationProfile("profile-1", adminSession);
    await createParserVersion("profile-1", {
      sensorResolutionMode: "ROUTE_SENSOR",
      messageIdPointer: "/messageId",
      sensorExternalIdPointer: null,
      timestampPointer: "/timestamp",
      sourceReceivedAtPointer: null,
      timestampFormat: "ISO_INSTANT",
      valueMappings: [{ parameterName: "temp", valuePointer: "/value", required: true }],
    }, adminSession);
    await listParserVersions("profile-1", adminSession);
    await updateParserVersion("profile-1", "version-1", {
      sensorResolutionMode: "ROUTE_SENSOR",
      messageIdPointer: "/messageId",
      sensorExternalIdPointer: null,
      timestampPointer: "/timestamp",
      sourceReceivedAtPointer: null,
      timestampFormat: "ISO_INSTANT",
      valueMappings: [{ parameterName: "temp", valuePointer: "/value", required: true }],
    }, adminSession);
    await activateParserVersion("profile-1", "version-1", { expectedActiveVersionId: null }, adminSession);
    await createIntegrationBinding("profile-1", { sensorExternalId: "sensor-1" }, adminSession);
    await listIntegrationBindings("profile-1", true, adminSession);
    await activateIntegrationBinding("binding-1", adminSession);
    await deactivateIntegrationBinding("binding-1", adminSession);
    await getIntegrationSnapshot(adminSession);
    await searchCatalogSensors("sensor", adminSession);

    const urls = vi.mocked(fetch).mock.calls.map(([url]) => String(url));
    expect(urls).not.toEqual(expect.arrayContaining([expect.stringContaining("/ingest")]));
    expect(urls).toEqual(expect.arrayContaining([
      expect.stringContaining("/api/sensor-integrations/profiles?includeInactive=true"),
      expect.stringContaining("/api/catalog/sensores?q=sensor&includeHidden=false"),
    ]));
  });
});
