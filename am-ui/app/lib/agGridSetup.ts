/**
 * Centralized AG Grid module registration.
 * Import this module once to register all required AG Grid modules.
 */
import { AllCommunityModule, ModuleRegistry } from 'ag-grid-community';

// Guard against multiple registrations
let isRegistered = false;

export function registerAgGridModules(): void {
  if (!isRegistered) {
    ModuleRegistry.registerModules([AllCommunityModule]);
    isRegistered = true;
  }
}

// Auto-register on import
registerAgGridModules();
