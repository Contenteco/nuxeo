/*
 * (C) Copyright 2012-2013 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Florent Guillaume
 *     Benoit Delbosc
 */
package org.nuxeo.ecm.core.work;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.naming.NamingException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.work.api.Work;
import org.nuxeo.ecm.core.work.api.Work.State;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.core.work.api.WorkQueueDescriptor;
import org.nuxeo.ecm.core.work.api.WorkQueuingImplDescriptor;
import org.nuxeo.ecm.core.work.api.WorkSchedulePath;
import org.nuxeo.runtime.metrics.MetricsService;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;

/**
 * The implementation of a {@link WorkManager}. This delegates the queuing
 * implementation to a {@link WorkQueuing} implementation.
 *
 * @since 5.6
 */
public class WorkManagerImpl extends DefaultComponent implements WorkManager {

    private static final Log log = LogFactory.getLog(WorkManagerImpl.class);

    public static final String DEFAULT_WORK_QUEUING = MemoryWorkQueuing.class.getName();

    // public static final String DEFAULT_WORK_QUEUING =
    // "org.nuxeo.ecm.core.work.redis.RedisWorkQueuing";

    protected static final String QUEUES_EP = "queues";

    protected static final String IMPL_EP = "implementation";

    public static final String DEFAULT_QUEUE_ID = "default";

    public static final String DEFAULT_CATEGORY = "default";

    protected static final int DEFAULT_MAX_POOL_SIZE = 4;

    protected static final String THREAD_PREFIX = "Nuxeo-Work-";

    // @GuardedBy("itself")
    protected WorkQueueDescriptorRegistry workQueueDescriptors;

    // used synchronized
    protected Map<String, WorkThreadPoolExecutor> executors;

    // @GuardedBy("itself")
    protected List<WorkAndScheduling> scheduledAfterCommit = new LinkedList<WorkAndScheduling>();

    protected WorkQueuing queuing;

    @Override
    public void activate(ComponentContext context) throws Exception {
        super.activate(context);
        workQueueDescriptors = new WorkQueueDescriptorRegistry();
        if (queuing == null) {
            queuing = newWorkQueuingDefault();
        }
        init();
    }

    @Override
    public void deactivate(ComponentContext context) throws Exception {
        closeQueuing();
        queuing = null;
        workQueueDescriptors = null;
        super.deactivate(context);
    }

    @Override
    public void registerContribution(Object contribution,
            String extensionPoint, ComponentInstance contributor)
            throws Exception {
        if (QUEUES_EP.equals(extensionPoint)) {
            WorkQueueDescriptor workQueueDescriptor = (WorkQueueDescriptor) contribution;
            log.info("Registered work queue " + workQueueDescriptor.id);
            synchronized (workQueueDescriptors) {
                workQueueDescriptors.addContribution(workQueueDescriptor);
            }
        } else if (IMPL_EP.equals(extensionPoint)) {
            WorkQueuingImplDescriptor descr = (WorkQueuingImplDescriptor) contribution;
            registerWorkQueuingDescriptor(descr);
        } else {
            throw new RuntimeException("Unknown extension point: "
                    + extensionPoint);
        }
    }

    @Override
    public void unregisterContribution(Object contribution,
            String extensionPoint, ComponentInstance contributor)
            throws Exception {
        if (QUEUES_EP.equals(extensionPoint)) {
            WorkQueueDescriptor workQueueDescriptor = (WorkQueueDescriptor) contribution;
            log.info("Unregistered work queue " + workQueueDescriptor.id);
            synchronized (workQueueDescriptors) {
                workQueueDescriptors.removeContribution(workQueueDescriptor);
            }
        } else if (IMPL_EP.equals(extensionPoint)) {
            WorkQueuingImplDescriptor descr = (WorkQueuingImplDescriptor) contribution;
            unregisterWorkQueuingDescriptor(descr);
        } else {
            throw new RuntimeException("Unknown extension point: "
                    + extensionPoint);
        }
    }

    protected void registerWorkQueuingDescriptor(WorkQueuingImplDescriptor descr) {
        WorkQueuing q = newWorkQueuing(descr.getWorkQueuingClass());
        closeQueuing();
        queuing = q;
    }

    protected void unregisterWorkQueuingDescriptor(
            WorkQueuingImplDescriptor descr) {
        closeQueuing();
        // fall back to memory-based
        queuing = newWorkQueuingDefault();
    }

    protected WorkQueuing newWorkQueuingDefault() {
        Class<?> klass;
        try {
            klass = Class.forName(DEFAULT_WORK_QUEUING);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        if (!(WorkQueuing.class.isAssignableFrom(klass))) {
            throw new RuntimeException("Class is not a "
                    + WorkQueuing.class.getSimpleName() + " implementation: "
                    + DEFAULT_WORK_QUEUING);
        }
        @SuppressWarnings("unchecked")
        Class<? extends WorkQueuing> workQueuingClass = (Class<? extends WorkQueuing>) klass;
        return newWorkQueuing(workQueuingClass);
    }

    protected WorkQueuing newWorkQueuing(Class<? extends WorkQueuing> klass) {
        WorkQueuing q;
        try {
            Constructor<? extends WorkQueuing> ctor = klass.getConstructor(WorkQueueDescriptorRegistry.class);
            q = ctor.newInstance(workQueueDescriptors);
        } catch (ReflectiveOperationException | SecurityException e) {
            throw new RuntimeException(e);
        }
        return q;
    }

    protected void closeQueuing() {
        try {
            shutdown(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restore interrupted status
            throw new RuntimeException(e);
        }
    }

    // ----- WorkManager -----

    @Override
    public List<String> getWorkQueueIds() {
        synchronized (workQueueDescriptors) {
            return workQueueDescriptors.getQueueIds();
        }
    }

    @Override
    public WorkQueueDescriptor getWorkQueueDescriptor(String queueId) {
        synchronized (workQueueDescriptors) {
            return workQueueDescriptors.get(queueId);
        }
    }

    @Override
    public String getCategoryQueueId(String category) {
        if (category == null) {
            category = DEFAULT_CATEGORY;
        }
        String queueId;
        synchronized (workQueueDescriptors) {
            queueId = workQueueDescriptors.getQueueId(category);
        }
        if (queueId == null) {
            queueId = DEFAULT_QUEUE_ID;
        }
        return queueId;
    }

    @Override
    public void init() {
        executors = new HashMap<String, WorkThreadPoolExecutor>();
        queuing.init();
    }

    protected synchronized WorkThreadPoolExecutor getExecutor(String queueId) {
        WorkQueueDescriptor workQueueDescriptor;
        synchronized (workQueueDescriptors) {
            workQueueDescriptor = workQueueDescriptors.get(queueId);
        }
        if (workQueueDescriptor == null) {
            throw new IllegalArgumentException("No such work queue: " + queueId);
        }

        WorkThreadPoolExecutor executor = executors.get(queueId);
        if (executor == null) {
            ThreadFactory threadFactory = new NamedThreadFactory(THREAD_PREFIX
                    + queueId + "-");
            int maxPoolSize = workQueueDescriptor.maxThreads;
            if (maxPoolSize <= 0) {
                maxPoolSize = DEFAULT_MAX_POOL_SIZE;
                workQueueDescriptor.maxThreads = maxPoolSize;
            }
            executor = new WorkThreadPoolExecutor(queueId, maxPoolSize,
                    maxPoolSize, 0, TimeUnit.SECONDS, queuing, threadFactory);
            executors.put(queueId, executor);
        }
        return executor;
    }

    @Override
    public boolean shutdownQueue(String queueId, long timeout, TimeUnit unit)
            throws InterruptedException {
        WorkThreadPoolExecutor executor = getExecutor(queueId);
        boolean terminated = shutdownExecutors(Collections.singleton(executor),
                timeout, unit);
        removeExecutor(queueId); // start afresh
        return terminated;
    }

    protected boolean shutdownExecutors(
            Collection<WorkThreadPoolExecutor> list, long timeout, TimeUnit unit)
            throws InterruptedException {
        // mark executors as shutting down
        for (WorkThreadPoolExecutor executor : list) {
            executor.shutdownAndSuspend();
        }

        long t0 = System.currentTimeMillis();
        long delay = unit.toMillis(timeout);

        // wait for termination or suspension
        boolean terminated = true;
        for (WorkThreadPoolExecutor executor : list) {
            long remaining = remainingMillis(t0, delay);
            if (!executor.awaitTerminationOrSave(remaining,
                    TimeUnit.MILLISECONDS)) {
                terminated = false;
                // hard shutdown for remaining tasks
                executor.shutdownNow();
            }
        }

        return terminated;
    }

    protected long remainingMillis(long t0, long delay) {
        long d = System.currentTimeMillis() - t0;
        if (d > delay) {
            return 0;
        }
        return delay - d;
    }

    protected synchronized void removeExecutor(String queueId) {
        executors.remove(queueId);
    }

    @Override
    public synchronized boolean shutdown(long timeout, TimeUnit unit)
            throws InterruptedException {
        if (executors == null) {
            return true;
        }
        List<WorkThreadPoolExecutor> executorList = new ArrayList<WorkThreadPoolExecutor>(
                executors.values());
        executors.clear();

        return shutdownExecutors(executorList, timeout, unit);
    }

    /**
     * A work instance and how to schedule it, for schedule-after-commit.
     *
     * @since 5.8
     */
    public static class WorkAndScheduling {
        public final Work work;

        public final Scheduling scheduling;

        public WorkAndScheduling(Work work, Scheduling scheduling) {
            this.work = work;
            this.scheduling = scheduling;
        }

        public static List<Work> getWorkList(List<WorkAndScheduling> lws) {
            List<Work> list = new LinkedList<Work>();
            for (WorkAndScheduling ws : lws) {
                list.add(ws.work);
            }
            return list;
        }
    }

    /**
     * Synchronization holding a list of {@link Work} instances with their
     * {@link Scheduling} until commit time, at which point each {@link Work}
     * instance will be scheduled.
     *
     * @since 5.7
     */
    public static class WorkSchedulingSynchronization implements
            Synchronization {

        protected final List<WorkAndScheduling> scheduledAfterCommit;

        protected final WorkManager workManager;

        public WorkSchedulingSynchronization(
                List<WorkAndScheduling> scheduledAfterCommit,
                WorkManager workManager) {
            this.scheduledAfterCommit = scheduledAfterCommit;
            this.workManager = workManager;
        }

        @Override
        public void beforeCompletion() {
        }

        @Override
        public void afterCompletion(int status) {
            WorkAndScheduling[] copy;
            synchronized (scheduledAfterCommit) {
                // use a copy and clear asap, as scheduling may check
                // already scheduled work to avoid duplicates
                copy = scheduledAfterCommit.toArray(new WorkAndScheduling[0]);
                scheduledAfterCommit.clear();
            }
            for (WorkAndScheduling ws : copy) {
                Work work = ws.work;
                if (status == Status.STATUS_COMMITTED) {
                    workManager.schedule(work, ws.scheduling, false);
                } else if (status == Status.STATUS_ROLLEDBACK) {
                    work.setWorkInstanceState(State.CANCELED);
                } else {
                    log.error("Unexpected status after completion: " + status);
                }
            }
        }
    }

    /**
     * Creates non-daemon threads at normal priority.
     */
    private static class NamedThreadFactory implements ThreadFactory {

        private final AtomicInteger threadNumber = new AtomicInteger();

        private final ThreadGroup group;

        private final String prefix;

        public NamedThreadFactory(String prefix) {
            SecurityManager sm = System.getSecurityManager();
            group = sm == null ? Thread.currentThread().getThreadGroup()
                    : sm.getThreadGroup();
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            String name = prefix + threadNumber.incrementAndGet();
            Thread thread = new Thread(group, r, name);
            // do not set daemon
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }

    /**
     * A handler for rejected tasks that discards them.
     */
    public static class CancelingPolicy implements RejectedExecutionHandler {

        public static final CancelingPolicy INSTANCE = new CancelingPolicy();

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            ((WorkThreadPoolExecutor) executor).removedFromQueue(r);
        }
    }

    /**
     * A {@link ThreadPoolExecutor} that keeps available the list of running
     * tasks.
     * <p>
     * Completed tasks are passed to another queue.
     * <p>
     * The scheduled queue and completed list are passed as arguments and can
     * have different implementations (in-memory, persisted, etc).
     *
     * @since 5.6
     */
    public static class WorkThreadPoolExecutor extends ThreadPoolExecutor {

        protected final String queueId;

        protected final WorkQueuing queuing;

        /**
         * Count of scheduled or running instances.
         */
        protected final AtomicInteger scheduledOrRunning = new AtomicInteger();

        /**
         * List of running Work instances, in order to be able to interrupt them
         * if requested.
         */
        // @GuardedBy("monitor")
        protected final List<Work> running;

        protected final Object monitor = new Object();

        // metrics

        protected final MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());

        protected final Counter scheduledCount;

        protected final Counter scheduledMax;

        protected final Counter runningCount;

        protected final Counter completedCount;

        protected final Timer workTimer;

        public WorkThreadPoolExecutor(String queueId, int corePoolSize,
                int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                WorkQueuing queuing, ThreadFactory threadFactory) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit,
                    queuing.getScheduledQueue(queueId), threadFactory);
            this.queueId = queueId;
            this.queuing = queuing;
            running = new LinkedList<Work>();
            // init metrics
            scheduledCount = registry.counter(MetricRegistry.name("nuxeo",
                    "works", queueId, "scheduled", "count"));
            scheduledMax = registry.counter(MetricRegistry.name("nuxeo",
                    "works", queueId, "scheduled", "max"));
            runningCount = registry.counter(MetricRegistry.name("nuxeo",
                    "works", queueId, "running"));
            completedCount = registry.counter(MetricRegistry.name("nuxeo",
                    "works", queueId, "completed"));
            workTimer = registry.timer(MetricRegistry.name("nuxeo", "works",
                    queueId, "total"));
        }

        public int getScheduledOrRunningSize() {
            return scheduledOrRunning.get();
        }

        @Override
        public void execute(Runnable r) {
            throw new UnsupportedOperationException("use other api");
        }

        /**
         * Executes the given task sometime in the future.
         *
         * @param work the work to execute
         * @see #execute(Runnable)
         */
        public void execute(Work work) {
            scheduledCount.inc();
            if (scheduledCount.getCount() > scheduledMax.getCount()) {
                scheduledMax.inc();
            }
            scheduledOrRunning.incrementAndGet();
            boolean ok = false;
            try {
                super.execute(new WorkHolder(work));
                ok = true;
            } finally {
                if (!ok) {
                    scheduledOrRunning.decrementAndGet();
                }
            }
        }

        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            Work work = WorkHolder.getWork(r);
            work.setWorkInstanceState(State.RUNNING);
            queuing.workRunning(queueId, work);
            synchronized (monitor) {
                running.add(work);
            }
            // metrics
            scheduledCount.dec();
            runningCount.inc();
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            scheduledOrRunning.decrementAndGet();
            Work work = WorkHolder.getWork(r);
            synchronized (monitor) {
                running.remove(work);
            }
            State state;
            if (t == null) {
                if (work.isWorkInstanceSuspended()) {
                    state = State.SCHEDULED;
                } else {
                    state = State.COMPLETED;
                }
            } else {
                state = State.FAILED;
            }
            work.setWorkInstanceState(state);
            queuing.workCompleted(queueId, work);
            // metrics
            runningCount.dec();
            completedCount.inc();
            workTimer.update(work.getCompletionTime() - work.getStartTime(),
                    TimeUnit.MILLISECONDS);
        }

        // called during shutdown
        // with tasks from the queue if new tasks are submitted
        // or with tasks drained from the queue
        protected void removedFromQueue(Runnable r) {
            Work work = WorkHolder.getWork(r);
            work.setWorkInstanceState(State.CANCELED);
        }

        /**
         * Initiates a shutdown of this executor and asks for work instances to
         * suspend themselves.
         *
         * The scheduled work instances are drained and suspended.
         */
        public void shutdownAndSuspend() {
            // rejected tasks will be discarded
            setRejectedExecutionHandler(CancelingPolicy.INSTANCE);
            // shutdown the executor
            // if a new task is scheduled it will be rejected -> discarded
            shutdown();
            // request all scheduled work instances to suspend (cancel)
            int n = queuing.setSuspending(queueId);
            scheduledOrRunning.addAndGet(-n);
            // request all running work instances to suspend (stop)
            synchronized (monitor) {
                for (Work work : running) {
                    work.setWorkInstanceSuspending();
                }
            }
        }

        /**
         * Blocks until all work instances have completed after a shutdown and
         * suspend request.
         *
         * @param timeout the time to wait
         * @param unit the timeout unit
         * @return true if all work stopped or was saved, false if some
         *         remaining after timeout
         */
        public boolean awaitTerminationOrSave(long timeout, TimeUnit unit)
                throws InterruptedException {
            boolean terminated = super.awaitTermination(timeout, unit);
            if (!terminated) {
                // drain queue from remaining scheduled work
                List<Runnable> drained = new ArrayList<Runnable>();
                getQueue().drainTo(drained);
                for (Runnable r : drained) {
                    removedFromQueue(r);
                }
            }
            // some work still remaining after timeout
            return terminated;
        }

        public Work removeScheduled(String workId) {
            Work w = queuing.removeScheduled(queueId, workId);
            if (w != null) {
                scheduledOrRunning.decrementAndGet();
            }
            return w;
        }

    }

    protected Work findScheduledAfterCommit(String workId) {
        synchronized (scheduledAfterCommit) {
            for (WorkAndScheduling ws : scheduledAfterCommit) {
                if (ws.work.getId().equals(workId)) {
                    return ws.work;
                }
            }
        }
        return null;
    }

    protected Work removeScheduledAfterCommit(String workId) {
        synchronized (scheduledAfterCommit) {
            for (Iterator<WorkAndScheduling> it = scheduledAfterCommit.iterator(); it.hasNext();) {
                WorkAndScheduling ws = it.next();
                if (ws.work.getId().equals(workId)) {
                    it.remove();
                    return ws.work;
                }
            }
        }
        return null;
    }

    /**
     * Gets the scheduled after commit tasks. Returns a copy.
     */
    protected List<Work> getScheduledAfterCommit() {
        synchronized (scheduledAfterCommit) {
            return WorkAndScheduling.getWorkList(scheduledAfterCommit);
        }
    }

    protected void executeAfterCommit(Work work, Scheduling scheduling) {
        synchronized (scheduledAfterCommit) {
            scheduledAfterCommit.add(new WorkAndScheduling(work, scheduling));
        }
    }

    @Override
    public void schedule(Work work) {
        schedule(work, Scheduling.ENQUEUE, false);
    }

    @Override
    public void schedule(Work work, boolean afterCommit) {
        schedule(work, Scheduling.ENQUEUE, afterCommit);
    }

    @Override
    public void schedule(Work work, Scheduling scheduling) {
        schedule(work, scheduling, false);
    }

    @Override
    public void schedule(Work work, Scheduling scheduling, boolean afterCommit) {
        work.setWorkInstanceState(State.SCHEDULED);
        if (afterCommit && scheduleAfterCommit(work, scheduling)) {
            return;
        }
        String workId = work.getId();
        String queueId = getCategoryQueueId(work.getCategory());
        if (log.isDebugEnabled()) {
            log.debug("Scheduling work: " + work + " using queue: " + queueId);
        }
        WorkSchedulePath.newInstance(work);
        if (log.isTraceEnabled()) {
            log.trace("Scheduling work: " + work + " using queue: " + queueId, work.getSchedulePath().getStack());
        } else if (log.isDebugEnabled()) {
            log.debug("Scheduling work: " + work + " using queue: " + queueId);
        }
        switch (scheduling) {
        case ENQUEUE:
            break;
        case CANCEL_SCHEDULED:
            Work w = removeScheduledAfterCommit(workId);
            if (w == null) {
                w = getExecutor(queueId).removeScheduled(workId);
            }
            if (w != null) {
                w.setWorkInstanceState(State.CANCELED);
                if (log.isDebugEnabled()) {
                    log.debug("Canceling existing scheduled work before scheduling");
                }
            }
            break;
        case IF_NOT_SCHEDULED:
        case IF_NOT_RUNNING:
        case IF_NOT_RUNNING_OR_SCHEDULED:
            if (hasWorkInState(workId, scheduling.state)) {
                // mark passed work as canceled
                work.setWorkInstanceState(State.CANCELED);
                if (log.isDebugEnabled()) {
                    log.debug("Canceling schedule because found: " + scheduling);
                }
                return;

            }
            break;

        }
        getExecutor(queueId).execute(work);
    }

    /**
     * Schedule after commit. Returns {@code false} if impossible (no
     * transaction or transaction manager).
     *
     * @since 5.8
     */
    protected boolean scheduleAfterCommit(Work work, Scheduling scheduling) {
        TransactionManager transactionManager;
        try {
            transactionManager = TransactionHelper.lookupTransactionManager();
        } catch (NamingException e) {
            transactionManager = null;
        }
        if (transactionManager == null) {
            if (log.isDebugEnabled()) {
                log.debug("Not scheduling work after commit because of missing transaction manager: "
                        + work);
            }
            return false;
        }
        try {
            Transaction transaction = transactionManager.getTransaction();
            if (transaction == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Not scheduling work after commit because of missing transaction: "
                            + work);
                }
                return false;
            }
            int status = transaction.getStatus();
            if (status == Status.STATUS_ACTIVE) {
                if (log.isDebugEnabled()) {
                    log.debug("Scheduling work after commit: " + work);
                }
                executeAfterCommit(work, scheduling);
                transaction.registerSynchronization(new WorkSchedulingSynchronization(
                        scheduledAfterCommit, this));
                return true;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Not scheduling work after commit because transaction is in status "
                            + status + ": " + work);
                }
                return false;
            }
        } catch (SystemException | RollbackException e) {
            log.error("Cannot schedule after commit", e);
            return false;
        }
    }

    @Override
    @Deprecated
    public Work find(Work work, State state, boolean useEquals, int[] pos) {
        if (pos != null) {
            pos[0] = 0; // compat
        }
        String workId = work.getId();
        // check scheduled after commit
        if (state == null || state == State.SCHEDULED) {
            Work w = findScheduledAfterCommit(workId);
            if (w != null) {
                return w;
            }
        }
        return queuing.find(workId, state);
    }

    /** @param state SCHEDULED, RUNNING or null for both */
    protected boolean hasWorkInState(String workId, State state) {
        // check scheduled after commit
        if (state == null || state == State.SCHEDULED) {
            if (findScheduledAfterCommit(workId) != null) {
                return true;
            }
        }
        State s = queuing.getWorkState(workId);
        if (s == null) {
            return false;
        }
        if (state == null) {
            return s == State.SCHEDULED || s == State.RUNNING;
        }
        if (state == State.COMPLETED) {
            return s == State.COMPLETED || s == State.FAILED
                    || s == State.CANCELED;
        }
        return state == s;
    }

    @Override
    public State getWorkState(String workId) {
        if (findScheduledAfterCommit(workId) != null) {
            return State.SCHEDULED;
        }
        return queuing.getWorkState(workId);
    }

    @Override
    public List<Work> listWork(String queueId, State state) {
        // don't return scheduled after commit
        return queuing.listWork(queueId, state);
    }

    @Override
    public int getQueueSize(String queueId, State state) {
        if (state == null) {
            return getScheduledAfterCommitSize()
                    + getScheduledOrRunningSize(queueId);
        } else if (state == State.SCHEDULED) {
            return getScheduledAfterCommitSize() + getScheduledSize(queueId);
        } else if (state == State.RUNNING) {
            return getRunningSize(queueId);
        } else if (state == State.COMPLETED) {
            return getCompletedSize(queueId);
        } else {
            throw new IllegalArgumentException(String.valueOf(state));
        }
    }

    @Override
    @Deprecated
    public int getNonCompletedWorkSize(String queueId) {
        return getScheduledOrRunningSize(queueId);
    }

    protected int getScheduledAfterCommitSize() {
        return scheduledAfterCommit.size();
    }

    protected int getScheduledSize(String queueId) {
        return queuing.getQueueSize(queueId, State.SCHEDULED);
    }

    protected int getRunningSize(String queueId) {
        return queuing.getQueueSize(queueId, State.RUNNING);
    }

    protected int getScheduledOrRunningSize(String queueId) {
        // check the thread pool directly, this avoids race conditions
        // because queueing.getRunningSize takes a bit of time to be
        // accurate after a work is scheduled
        return getExecutor(queueId).getScheduledOrRunningSize();
    }

    protected int getCompletedSize(String queueId) {
        return queuing.getQueueSize(queueId, State.COMPLETED);
    }

    @Override
    public boolean awaitCompletion(String queueId, long timeout, TimeUnit unit)
            throws InterruptedException {
        return awaitCompletion(Collections.singleton(queueId), timeout, unit);
    }

    @Override
    public boolean awaitCompletion(long timeout, TimeUnit unit)
            throws InterruptedException {
        return awaitCompletion(getWorkQueueIds(), timeout, unit);
    }

    private boolean awaitCompletion(Collection<String> queueIds, long timeout,
            TimeUnit unit) throws InterruptedException {
        long t0 = System.currentTimeMillis();
        long delay = unit.toMillis(timeout);
        for (;;) {
            boolean completed = true;
            for (String queueId : queueIds) {
                if (getScheduledAfterCommitSize() != 0
                        || getScheduledOrRunningSize(queueId) != 0) {
                    completed = false;
                    break;
                }
            }
            if (completed) {
                return true;
            }
            if (System.currentTimeMillis() - t0 > delay) {
                return false;
            }
            // TODO use wait/notify instead
            Thread.sleep(50);
        }
    }
    @Override
    public synchronized void clearCompletedWork(String queueId) {
        queuing.clearCompletedWork(queueId, 0);
    }

    @Override
    public synchronized void clearCompletedWork(long completionTime) {
        for (String queueId: queuing.getCompletedQueueIds()) {
            queuing.clearCompletedWork(queueId, completionTime);
        }
    }

    @Override
    public synchronized void cleanup() {
        log.debug("Clearing old completed work");
        for (String queueId : queuing.getCompletedQueueIds()) {
            WorkQueueDescriptor workQueueDescriptor = workQueueDescriptors.get(queueId);
            long delay = workQueueDescriptor.clearCompletedAfterSeconds * 1000L;
            if (delay > 0) {
                long completionTime = System.currentTimeMillis() - delay;
                queuing.clearCompletedWork(queueId, completionTime);
            }
        }
    }

}
