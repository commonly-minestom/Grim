package ac.grim.grimac.platform.minestom.initables;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.manager.init.start.StartableInitable;
import ac.grim.grimac.platform.api.scheduler.TaskHandle;
import ac.grim.grimac.platform.luckperms.AbstractLuckPermsHandler;
import ac.grim.grimac.utils.anticheat.LogUtil;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.LuckPermsEvent;
import net.luckperms.api.event.context.ContextUpdateEvent;
import net.minestom.server.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

public final class MinestomLuckPermsHandler extends AbstractLuckPermsHandler implements StartableInitable {
    private static final int MAX_PROVIDER_ATTEMPTS = 20;

    private TaskHandle retryTask;
    private boolean stopped;

    @Override
    public void start() {
        stopped = false;
        subscribeOrRetry(0);
    }

    @Override
    public void stop() {
        stopped = true;
        if (retryTask != null) {
            retryTask.cancel();
            retryTask = null;
        }
        super.stop();
    }

    @Override
    protected <T extends LuckPermsEvent> EventSubscription<T> subscribe(LuckPerms luckPerms, Class<T> eventClass, Consumer<? super T> handler) {
        return luckPerms.getEventBus().subscribe(eventClass, handler);
    }

    @Override
    protected UUID contextSubjectUuid(ContextUpdateEvent event) {
        return event.getSubject() instanceof Player player ? player.getUuid() : null;
    }

    private void subscribeOrRetry(int attempt) {
        if (stopped || isRegistered()) return;
        try {
            register(LuckPermsProvider.get());
        } catch (IllegalStateException exception) {
            if (attempt >= MAX_PROVIDER_ATTEMPTS) {
                LogUtil.warn("LuckPerms detected but its API was not available for the permission refresh hook");
                return;
            }
            retryTask = GrimAPI.INSTANCE.getScheduler().getGlobalRegionScheduler()
                    .runDelayed(GrimAPI.INSTANCE.getGrimPlugin(), () -> subscribeOrRetry(attempt + 1), 1L);
        }
    }
}
