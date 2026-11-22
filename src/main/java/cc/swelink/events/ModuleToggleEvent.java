package cc.swelink.events;

import cc.swelink.systems.modules.Module;

public record ModuleToggleEvent(Module module, boolean enabled) {
}
