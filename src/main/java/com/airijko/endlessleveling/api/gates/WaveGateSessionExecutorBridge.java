package com.airijko.endlessleveling.api.gates;

/**
 * Low-level execution bridge for wave gate session operations.
 *
 * Core owns the public wave session service and delegates execution to an
 * addon-provided executor bridge when one is available.
 */
public interface WaveGateSessionExecutorBridge extends WaveGateSessionBridge {
}
