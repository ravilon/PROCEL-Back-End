package com.procel.ingestion.service.rooms;

public class RoomsIngestionResult {

    private int fetched;
    private int inserted;
    private int updated;
    private int skipped;
    private long elapsedMs;

    public RoomsIngestionResult(int fetched, int inserted, int updated, int skipped, long elapsedMs) {
        this.fetched = fetched;
        this.inserted = inserted;
        this.updated = updated;
        this.skipped = skipped;
        this.elapsedMs = elapsedMs;
    }

    public int getFetched() { return fetched; }
    public int getInserted() { return inserted; }
    public int getUpdated() { return updated; }
    public int getSkipped() { return skipped; }
    public long getElapsedMs() { return elapsedMs; }
}
