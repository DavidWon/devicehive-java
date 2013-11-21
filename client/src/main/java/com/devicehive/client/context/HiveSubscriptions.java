package com.devicehive.client.context;


import com.devicehive.client.config.Constants;
import com.devicehive.client.model.DeviceCommand;
import com.devicehive.client.model.DeviceNotification;
import com.devicehive.client.util.SubscriptionTask;
import com.devicehive.client.util.UpdatesSubscriptionTask;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Here is all logic that connected with subscribe/unsubscribe actions.
 * Represents commands updates storage, commands storage and notifications storage. Keeps implementation of
 * subscribe/unsubscribe actions.
 */
public class HiveSubscriptions {

    private static final int SUBSCRIPTIONS_THREAD_POOL_SIZE = 100;
    private static final Integer AWAIT_TERMINATION_TIMEOUT = 10;
    private static Logger logger = LoggerFactory.getLogger(HiveSubscriptions.class);
    private final HiveContext hiveContext;
    private ExecutorService subscriptionExecutor = Executors.newFixedThreadPool(SUBSCRIPTIONS_THREAD_POOL_SIZE);
    private Map<Pair<String, Set<String>>, Future<Void>> commandsSubscriptionsStorage = new HashMap<>();
    private Map<Pair<String, Set<String>>, Future<Void>> notificationsSubscriptionsStorage = new HashMap<>();
    private Map<Pair<String, Long>, Future<DeviceCommand>> commandsUpdateSubscriptionStorage = new HashMap<>();
    private ReadWriteLock rwCommandsLock = new ReentrantReadWriteLock();
    private ReadWriteLock rwCommandUpdateLock = new ReentrantReadWriteLock();
    private ReadWriteLock rwNotificationsLock = new ReentrantReadWriteLock();

    public HiveSubscriptions(HiveContext hiveContext) {
        this.hiveContext = hiveContext;
    }

    /**
     * Adds commands subscription to storage. Creates task that store commands in context's command queue. In case
     * when no device identifiers specified, subscription "for all available" will be added.
     *
     * @param headers   headers that defines the sample of commands
     * @param timestamp first command timestamp
     * @param names     names of commands that defines
     * @param deviceIds devices identifiers of devices that should be subscribed
     */
    public void addCommandsSubscription(Map<String, String> headers, Timestamp timestamp,
                                        Set<String> names, String... deviceIds) {
        if (deviceIds == null) {
            try {
                rwCommandsLock.writeLock().lock();
                if (!commandsSubscriptionsStorage.containsKey(ImmutablePair.of(Constants.FOR_ALL_SUBSTITUTE, names))) {
                    String path = "/device/command/poll";
                    SubscriptionTask task = new SubscriptionTask(hiveContext, timestamp, Constants.WAIT_TIMEOUT,
                            path, headers, names, Constants.FOR_ALL_SUBSTITUTE, DeviceCommand.class);
                    Future<Void> subscription = subscriptionExecutor.submit(task);
                    commandsSubscriptionsStorage.put(ImmutablePair.of(Constants.FOR_ALL_SUBSTITUTE, names),
                            subscription);
                    logger.debug("New subscription added for:" + Constants.FOR_ALL_SUBSTITUTE);
                }
            } finally {
                rwCommandsLock.writeLock().unlock();
            }
        } else {
            try {
                rwCommandsLock.writeLock().lock();
                for (String id : deviceIds) {
                    Future<Void> subscription = commandsSubscriptionsStorage.get(ImmutablePair.of(id, names));
                    if (subscription == null || subscription.isDone()) { //Returns true if this task completed.
                        // Completion may be due to normal termination, an exception, or cancellation --
                        // in all of these cases, this method will return true.
                        String path = "/device/" + id + "/command/poll";
                        SubscriptionTask task = new SubscriptionTask(hiveContext, timestamp, Constants.WAIT_TIMEOUT,
                                path, headers, names, id, DeviceCommand.class);
                        subscription = subscriptionExecutor.submit(task);
                        commandsSubscriptionsStorage.put(ImmutablePair.of(id, names), subscription);
                        logger.debug("New subscription added for device with id:" + id);
                    }
                }
            } finally {
                rwCommandsLock.writeLock().unlock();
            }
        }
    }

    /**
     * Put command updates into the queue as soon as update coming. Command update subscription adds when the command
     * insert executes.
     *
     * @param commandId command identifier
     * @param deviceId  device identifier
     */
    public void addCommandUpdateSubscription(long commandId, String deviceId) {
        try {
            rwCommandUpdateLock.writeLock().lock();
            Future<DeviceCommand> subscription =
                    commandsUpdateSubscriptionStorage.get(ImmutablePair.of(deviceId, commandId));
            if (subscription == null || subscription.isDone()) { //Returns true if this task completed.
                // Completion may be due to normal termination, an exception, or cancellation --
                // in all of these cases, this method will return true.
                String path = "/device/" + deviceId + "/command/" + commandId + "/poll";
                UpdatesSubscriptionTask task = new UpdatesSubscriptionTask(hiveContext, path, Constants.WAIT_TIMEOUT);
                subscription = subscriptionExecutor.submit(task);
                commandsUpdateSubscriptionStorage.put(ImmutablePair.of(deviceId, commandId), subscription);
                logger.debug("New subscription added for device with id:" + deviceId + " and command id: " +
                        commandId);
            }
        } finally {
            rwCommandUpdateLock.writeLock().unlock();
        }
    }

    /**
     * Remove command subscription for following command name and device identifier. In case when no device identifiers specified,
     * surrogate subscription "for all available" will be removed. This subscription does not
     * include subscriptions for specific device.
     *
     * @param names     set of command names
     * @param deviceIds device identifiers.
     */
    public void removeCommandSubscription(Set<String> names, String... deviceIds) {
        unsubscribe(rwCommandsLock, commandsSubscriptionsStorage, names, deviceIds);
    }

    /**
     * Adds subscription for notifications with following set of notification's names from device with defined device
     * identifiers. In case when no device identifiers specified, subscription for all available devices will be added.
     *
     * @param headers   headers that define the sample of commands
     * @param timestamp start timestamp
     * @param names     notifications names (statistics)
     * @param deviceIds device identifiers
     */
    public void addNotificationSubscription(Map<String, String> headers, Timestamp timestamp, Set<String> names,
                                            String... deviceIds) {
        if (deviceIds == null) {
            try {
                rwCommandsLock.writeLock().lock();
                if (!commandsSubscriptionsStorage.containsKey(ImmutablePair.of(Constants.FOR_ALL_SUBSTITUTE, names))) {
                    String path = "/device/notification/poll";
                    SubscriptionTask task = new SubscriptionTask(hiveContext, timestamp, Constants.WAIT_TIMEOUT,
                            path, headers, names, Constants.FOR_ALL_SUBSTITUTE, DeviceNotification.class);
                    Future<Void> subscription = subscriptionExecutor.submit(task);
                    commandsSubscriptionsStorage.put(ImmutablePair.of(Constants.FOR_ALL_SUBSTITUTE, names),
                            subscription);
                    logger.debug("New subscription added for:" + Constants.FOR_ALL_SUBSTITUTE);
                }
            } finally {
                rwCommandsLock.writeLock().unlock();
            }
        } else {
            try {
                rwCommandsLock.writeLock().lock();
                for (String id : deviceIds) {
                    Future<Void> subscription = commandsSubscriptionsStorage.get(ImmutablePair.of(id, names));
                    if (subscription == null || subscription.isDone()) { //Returns true if this task completed.
                        // Completion may be due to normal termination, an exception, or cancellation --
                        // in all of these cases, this method will return true.
                        String path = "/device/" + id + "/notification/poll";
                        SubscriptionTask task = new SubscriptionTask(hiveContext, timestamp, Constants.WAIT_TIMEOUT,
                                path, headers, names, id, DeviceNotification.class);
                        subscription = subscriptionExecutor.submit(task);
                        commandsSubscriptionsStorage.put(ImmutablePair.of(id, names), subscription);
                        logger.debug("New subscription added for device with id:" + id);
                    }
                }
            } finally {
                rwCommandsLock.writeLock().unlock();
            }
        }
    }

    /**
     * Remove notification subscription for following notification name and device identifier. In case when no device
     * identifiers specified, surrogate subscription "for all available" will be removed. This subscription does not
     * include subscriptions for specific device.
     *
     * @param names     set of notification names
     * @param deviceIds device identifiers.
     */
    public void removeNotificationSubscription(Set<String> names, String... deviceIds) {
        unsubscribe(rwNotificationsLock, notificationsSubscriptionsStorage, names, deviceIds);
    }

    private void unsubscribe(ReadWriteLock lock, Map<Pair<String, Set<String>>, Future<Void>> subscriptionStorage,
                             Set<String> names, String... deviceIds) {
        if (deviceIds == null) {
            try {
                lock.readLock().lock();
                Future<Void> task = subscriptionStorage.remove(ImmutablePair.of(Constants.FOR_ALL_SUBSTITUTE, names));
                if (task != null && !task.isDone()) {
                    boolean result = task.cancel(true);
                    logger.debug("Task is cancelled for device with id:" + Constants.FOR_ALL_SUBSTITUTE +
                            ". Cancellation result:" + result);
                }
            } finally {
                lock.readLock().unlock();
            }
        } else {
            try {
                lock.readLock().lock();
                for (String id : deviceIds) {
                    Future<Void> task = subscriptionStorage.remove(ImmutablePair.of(id, names));
                    if (task != null && !task.isDone()) {
                        boolean result = task.cancel(true);
                        logger.debug("Task is cancelled for device with id:" + id + ". Cancellation result:" + result);
                    }
                }
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    /**
     * Kills threads which monitoring commands, command updates and notifications.
     */
    public void shutdownThreads() {
        try {
            rwNotificationsLock.writeLock().lock();
            subscriptionExecutor.shutdown();
            try {
                if (!subscriptionExecutor.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TimeUnit.SECONDS)) {
                    subscriptionExecutor.shutdownNow();
                    if (!subscriptionExecutor.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TimeUnit.SECONDS))
                        logger.warn("Pool did not terminate");
                }
            } catch (InterruptedException ie) {
                logger.warn(ie.getMessage(), ie);
                subscriptionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        } finally {
            rwNotificationsLock.writeLock().unlock();
        }
    }
}
