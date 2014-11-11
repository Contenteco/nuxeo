/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.work;

import static org.nuxeo.ecm.core.work.api.Work.Progress.PROGRESS_INDETERMINATE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ConcurrentUpdateException;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentLocation;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.impl.DocumentLocationImpl;
import org.nuxeo.ecm.core.work.api.Work;
import org.nuxeo.ecm.core.work.api.WorkSchedulePath;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * A base implementation for a {@link Work} instance, dealing with most of the
 * details around state change.
 * <p>
 * Actual implementations must at a minimum implement the {@link #work()}
 * method. A method {@link #cleanUp} is available.
 * <p>
 * To deal with suspension, {@link #work()} should periodically check for
 * {@link #isSuspending()} and if true save its state and call
 * {@link #suspended()}.
 * <p>
 * Specific information about the work can be returned by {@link #getDocument()}
 * or {@link #getDocuments()}.
 *
 * @since 5.6
 */
public abstract class AbstractWork implements Work {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(AbstractWork.class);

    protected static final Random RANDOM = new Random();

    protected String id;

    /** Suspend requested by the work manager. */
    protected transient volatile boolean suspending;

    /** Suspend acknowledged by the work instance. */
    protected transient volatile boolean suspended;

    protected transient State state;

    protected transient Progress progress;

    /** Repository name for the Work instance, if relevant. */
    protected String repositoryName;

    /**
     * Doc id for the Work instance, if relevant. This describes for the
     * WorkManager a document on which this Work instance will act.
     * <p>
     * Either docId or docIds is set. Not both.
     */
    protected String docId;

    /**
     * Doc ids for the Work instance, if relevant. This describes for the
     * WorkManager the documents on which this Work instance will act.
     * <p>
     * Either docId or docIds is set. Not both.
     */
    protected List<String> docIds;

    /**
     * If {@code true}, the docId is only the root of a set of documents on
     * which this Work instance will act.
     */
    protected boolean isTree;

    protected String status;

    protected long schedulingTime;

    protected long startTime;

    protected long completionTime;

    protected transient CoreSession session;

    protected transient WorkSchedulePath schedulePath = WorkSchedulePath.EMPTY;

    /**
     * Constructs a {@link Work} instance with a unique id.
     */
    public AbstractWork() {
        // we user RANDOM to deal with these cases:
        // - several calls in the time granularity of nanoTime()
        // - several concurrent calls on different servers
        this(System.nanoTime() + "." + Math.abs(RANDOM.nextInt()));
    }

    public AbstractWork(String id) {
        this.id = id;
        progress = PROGRESS_INDETERMINATE;
        schedulingTime = System.currentTimeMillis();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public WorkSchedulePath getSchedulePath() {
        return schedulePath;
    }

    @Override
    public void setSchedulePath(WorkSchedulePath path) {
        schedulePath = path;
    }

    public void setDocument(String repositoryName, String docId, boolean isTree) {
        this.repositoryName = repositoryName;
        this.docId = docId;
        docIds = null;
        this.isTree = isTree;
    }

    public void setDocument(String repositoryName, String docId) {
        setDocument(repositoryName, docId, false);
    }

    public void setDocuments(String repositoryName, List<String> docIds) {
        this.repositoryName = repositoryName;
        if (docIds.size() == 1) {
            docId = docIds.get(0);
            this.docIds = null;
        } else {
            docId = null;
            this.docIds = new ArrayList<String>(docIds);
        }
    }

    @Override
    public void setWorkInstanceSuspending() {
        suspending = true;
    }

    @Override
    public boolean isSuspending() {
        return suspending;
    }

    @Override
    public void suspended() {
        suspended = true;
    }

    @Override
    public boolean isWorkInstanceSuspended() {
        return suspended;
    }

    @Override
    public void setWorkInstanceState(State state) {
        this.state = state;
        if (log.isTraceEnabled()) {
            log.trace(this + " state=" + state);
        }
    }

    @Override
    public State getWorkInstanceState() {
        return state;
    }

    @Override
    @Deprecated
    public State getState() {
        return state;
    }

    @Override
    public void setProgress(Progress progress) {
        this.progress = progress;
        if (log.isTraceEnabled()) {
            log.trace(String.valueOf(this));
        }
    }

    @Override
    public Progress getProgress() {
        return progress;
    }

    /**
     * Sets a human-readable status for this work instance.
     *
     * @param status the status
     */
    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String getStatus() {
        return status;
    }

    /**
     * May be called by implementing classes to open a session on the
     * repository.
     *
     * @return the session (also available in {@code session} field)
     */
    public CoreSession initSession() throws Exception {
        return initSession(repositoryName);
    }

    /**
     * May be called by implementing classes to open a session on the given
     * repository.
     *
     * @param repositoryName the repository name
     * @return the session (also available in {@code session} field)
     */
    public CoreSession initSession(String repositoryName) throws Exception {
        session = CoreInstance.openCoreSessionSystem(repositoryName);
        return session;
    }

    /**
     * Closes the session that was opened by {@link #initSession}.
     *
     * @since 5.8
     */
    public void closeSession() {
        if (session != null) {
            session.close();
            session = null;
        }
    }

    @Override
    public void work() throws Exception {
        Exception exception = null;
        int retryCount = getRetryCount(); // may be 0
        for (int i = 0; i <= retryCount; i++) {
            if (i > 0) {
                log.debug("Retrying work due to concurrent update (" + i
                        + "): " + this);
                log.trace("Concurrent update", exception);
                rollbackAndRetryTransaction();
            }
            try {
                retryableWork();
                return;
            } catch (ConcurrentUpdateException e) {
                exception = e;
            }
        }
        if (exception == null) {
            throw new RuntimeException("Invalid retry count: " + retryCount);
        }
        throw exception;
    }

    /**
     * Gets the number of times that this Work instance can be retried in case
     * of concurrent update exceptions.
     *
     * @return 0 for no retry, or more if some retries are possible
     * @see #retryableWork
     * @since 5.8
     */
    public int getRetryCount() {
        return 0;
    }

    /**
     * This method, along with {@link #getRetryCount}, should be implemented if
     * the work instance can be retried in case of concurrent update exceptions.
     *
     * @see #getRetryCount
     * @since 5.8
     */
    public void retryableWork() throws Exception {
        throw new UnsupportedOperationException("Missing implementation");
    }

    /**
     * This method is called after {@link #work} is done in a finally block,
     * whether work completed normally or was in error or was interrupted.
     *
     * @param ok {@code true} if the work completed normally
     * @param e the exception, if available
     */
    @Override
    public void cleanUp(boolean ok, Exception e) {
        setCompletionTime();
        if (!ok) {
            if (e instanceof InterruptedException) {
                log.debug("Suspended work: " + this);
            } else {
                log.error("Exception during work: " + this, e);
                if (WorkSchedulePath.captureStack) {
                    WorkSchedulePath.log.error("Work schedule path", schedulePath.getStack());
                }
            }
        }
        closeSession();
    }

    @Override
    public String getUserId() {
        // TODO
        return null;
    }

    @Override
    public long getSchedulingTime() {
        return schedulingTime;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public long getCompletionTime() {
        return completionTime;
    }

    @Override
    public void setStartTime() {
        startTime = System.currentTimeMillis();
    }

    protected void setCompletionTime() {
        completionTime = System.currentTimeMillis();
    }

    @Override
    public String getCategory() {
        return getClass().getSimpleName();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append('(');
        if (docId != null) {
            buf.append(docId);
            buf.append(", ");
        } else if (docIds != null && docIds.size() > 0) {
            buf.append(docIds.get(0));
            buf.append("..., ");
        }
        buf.append(getSchedulePath().getParentPath());
        buf.append(", ");
        buf.append(getProgress());
        buf.append(", ");
        buf.append(getStatus());
        buf.append(')');
        return buf.toString();
    }

    @Override
    public DocumentLocation getDocument() {
        if (docId != null) {
            return newDocumentLocation(docId);
        }
        return null;
    }

    @Override
    public List<DocumentLocation> getDocuments() {
        if (docIds != null) {
            List<DocumentLocation> res = new ArrayList<DocumentLocation>(
                    docIds.size());
            for (String docId : docIds) {
                res.add(newDocumentLocation(docId));
            }
            return res;
        }
        if (docId != null) {
            return Collections.singletonList(newDocumentLocation(docId));
        }
        return Collections.emptyList();
    }

    protected DocumentLocation newDocumentLocation(String docId) {
        return new DocumentLocationImpl(repositoryName, new IdRef(docId));
    }

    @Override
    public boolean isDocumentTree() {
        return isTree;
    }

    /**
     * Rolls back the transaction and starts another one. Closes the session as
     * well.
     *
     * @since 5.8
     */
    public void rollbackAndRetryTransaction() {
        closeSession();
        TransactionHelper.setTransactionRollbackOnly();
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
    }

    /**
     * Releases the transaction resources by committing the existing transaction
     * (if any). This is recommended before running a long process.
     */
    public void commitOrRollbackTransaction() {
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
        }
    }

    /**
     * Starts a new transaction.
     * <p>
     * Usually called after {@code commitOrRollbackTransaction()}, for instance
     * for saving back the results of a long process.
     *
     * @return true if a new transaction was started
     */
    public boolean startTransaction() {
        return TransactionHelper.startTransaction();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Work)) {
            return false;
        }
        return ((Work) other).getId().equals(getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

}
