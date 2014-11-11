/*
 * (C) Copyright 2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Nuxeo - initial API and implementation
 * $Id$
 */

package org.nuxeo.ecm.directory;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.metrics.MetricsService;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;

/**
 * Very simple cache system to cache directory entry lookups (not search
 * queries).
 * <p>
 * Beware that this cache is not transaction aware (which is not a problem for
 * LDAP directories anyway).
 * <p>
 * If we want to implement a smarter caching strategy we might prefer to base it
 * on jboss-cache instead of reinventing the wheel.
 *
 * @author Olivier Grisel <ogrisel@nuxeo.com>
 */
public class DirectoryCache {

    private final static Log log = LogFactory.getLog(DirectoryCache.class);

    protected final String name;

    protected final Map<String, CachedEntry> entryStore = new HashMap<String, CachedEntry>();

    protected final Map<String, CachedEntry> entryStoreWithoutReferences = new HashMap<String, CachedEntry>();

    protected final MetricRegistry metrics = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());

    protected final Counter hitsCounter;

    protected final Counter invalidationsCounter;

    protected final Counter maxCounter;

    protected final Counter sizeCounter;

    // time out in seconds an entry is kept in cache, entryCacheTimeout <= 0
    // means entries are kept in cache till manual invalidation
    protected int timeout = 0;

    protected DirectoryCache(String name) {
        this.name = name;
        hitsCounter = metrics.counter(MetricRegistry.name("nuxeo", "directories", name, "cache", "hits"));
        invalidationsCounter = metrics.counter(MetricRegistry.name("nuxeo", "directories", name, "cache", "invalidations"));
        sizeCounter = metrics.counter(MetricRegistry.name("nuxeo", "directories", name, "cache", "size"));
        maxCounter = metrics.counter(MetricRegistry.name("nuxeo", "directories", name, "cache", "max"));
    }

    // maximum number of entries kept in cache, entryCacheMaxSize <= 0 means
    // cache disabled; if the limit is reached, all entries get invalidated
    protected int maxSize = 0;

    protected boolean isCacheEnabled() {
        return maxSize > 0;
    }

    public DocumentModel getEntry(String entryId, EntrySource source)
            throws DirectoryException {
        return getEntry(entryId, source, true);
    }

    public DocumentModel getEntry(String entryId, EntrySource source,
            boolean fetchReferences) throws DirectoryException {
        if (!isCacheEnabled()) {
            return source.getEntryFromSource(entryId, fetchReferences);
        }

        DocumentModel dm;
        if (fetchReferences) {
            CachedEntry entry = entryStore.get(entryId);
            if (entry == null || entry.isExpired()) {
                // fetch the entry from the backend and cache it for later reuse
                dm = source.getEntryFromSource(entryId, fetchReferences);
                synchronized (this) {
                    // check that the cache limit has not yet been reached
                    if (maxSize > 0 && entryStore.size() >= maxSize) {
                        log.warn("Directory cacheMaxSize for " + name
                                + " is too small, flushing.");
                        entryStore.clear();
                    }
                    entryStore.put(entryId, new CachedEntry(dm, timeout));
                    sizeCounter.inc();
                    if (sizeCounter.getCount() > maxCounter.getCount()) {
                        maxCounter.inc();
                    }
                }
            } else {
                dm = entry.getDocumentModel();
                hitsCounter.inc();
            }
        } else {
            CachedEntry entry = entryStoreWithoutReferences.get(entryId);
            if (entry == null || entry.isExpired()) {
                // fetch the entry from the backend and cache it for later reuse
                dm = source.getEntryFromSource(entryId, fetchReferences);
                synchronized (this) {
                    // check that the cache limit has not yet been reached
                    if (maxSize > 0
                            && entryStoreWithoutReferences.size() >= maxSize) {
                        entryStoreWithoutReferences.clear();
                    }
                    entryStoreWithoutReferences.put(entryId, new CachedEntry(
                            dm, timeout));
                    sizeCounter.inc();
                    if (sizeCounter.getCount() > maxCounter.getCount()) {
                        maxCounter.inc();
                    }
                }
            } else {
                dm = entry.getDocumentModel();
                hitsCounter.inc();
            }
        }
        try {
            if (dm == null) {
                return null;
            }
            DocumentModel clone = dm.clone();
            // DocumentModelImpl#clone does not copy context data, hence
            // propagate the read-only flag manually
            if (BaseSession.isReadOnlyEntry(dm)) {
                BaseSession.setReadOnlyEntry(clone);
            }
            return clone;
        } catch (CloneNotSupportedException e) {
            // will never happen as long a DocumentModelImpl is used
            return dm;
        }
    }

    public void invalidate(List<String> entryIds) {
        if (isCacheEnabled()) {
            synchronized (this) {
                for (String entryId : entryIds) {
                    entryStore.remove(entryId);
                    entryStoreWithoutReferences.remove(entryId);
                    sizeCounter.dec();
                    invalidationsCounter.inc();
                }
            }
        }
    }

    public void invalidate(String... entryIds) {
        invalidate(Arrays.asList(entryIds));
    }

    public void invalidateAll() {
        if (isCacheEnabled()) {
            synchronized (this) {
                long count = sizeCounter.getCount();
                sizeCounter.dec(count);
                invalidationsCounter.inc(count);
                entryStore.clear();
                entryStoreWithoutReferences.clear();
            }
        }
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    protected static class CachedEntry {

        protected final DocumentModel entry;

        protected final Calendar expirationDate;

        CachedEntry(DocumentModel entry, int timeout) {
            this.entry = entry;
            expirationDate = Calendar.getInstance();
            expirationDate.add(Calendar.SECOND, timeout);
        }

        public DocumentModel getDocumentModel() {
            return entry;
        }

        public boolean isExpired() {
            return expirationDate.before(Calendar.getInstance());
        }
    }

}
