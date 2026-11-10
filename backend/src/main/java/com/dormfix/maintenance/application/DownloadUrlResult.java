package com.dormfix.maintenance.application;

import java.time.Instant;

public record DownloadUrlResult(String url, Instant expiresAt) { }
