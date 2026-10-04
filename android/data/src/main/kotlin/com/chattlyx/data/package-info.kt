/**
 * ChattlyX data layer: repository implementations bridging network, database
 * and crypto (master spec Section 6.1). Room is the single source of truth;
 * network results are written to the database, never rendered directly.
 * Implementations arrive with their feature phases.
 */
package com.chattlyx.data
