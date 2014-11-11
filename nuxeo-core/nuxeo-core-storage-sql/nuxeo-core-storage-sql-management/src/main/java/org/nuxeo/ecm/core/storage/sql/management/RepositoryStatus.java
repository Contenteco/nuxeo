/*
 * Copyright (c) 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Florent Guillaume
 */

package org.nuxeo.ecm.core.storage.sql.management;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.naming.Binding;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.nuxeo.ecm.core.storage.sql.BinaryGarbageCollector;
import org.nuxeo.ecm.core.storage.sql.BinaryManagerStatus;
import org.nuxeo.ecm.core.storage.sql.Repository;
import org.nuxeo.ecm.core.storage.sql.RepositoryManagement;
import org.nuxeo.ecm.core.storage.sql.RepositoryResolver;

/**
 * An MBean to manage SQL storage repositories.
 *
 * @author Florent Guillaume
 */
public class RepositoryStatus implements RepositoryStatusMBean {

    protected List<RepositoryManagement> getRepositories() throws NamingException {
        List<RepositoryManagement> list = new LinkedList<RepositoryManagement>();
        InitialContext context;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(RepositoryStatus.class.getClassLoader());
        try {
            context = new InitialContext();
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
        // we search both JBoss-like and Glassfish-like prefixes
        // @see NXCore#getRepository
        for (String prefix : new String[] { "java:NXRepository", "NXRepository" }) {
            NamingEnumeration<Binding> bindings;
            try {
                bindings = context.listBindings(prefix);
            } catch (NamingException e) {
                continue;
            }
            NamingEnumeration<Binding> e = null;
            try {
                for (e = bindings; e.hasMore();) {
                    Binding binding = e.nextElement();
                    String name = binding.getName();
                    if (binding.isRelative()) {
                        name = prefix + '/' + name;
                    }
                    Object object = context.lookup(name);
                    if (!(object instanceof RepositoryManagement)) {
                        continue;
                    }
                    list.add((RepositoryManagement) object);
                }
            }
            finally {
                if (e != null) {
                    e.close();
                }
            }
        }
        if (list.size() == 0) {
            List<Repository> repos = RepositoryResolver.getRepositories();
            for (Repository repo : repos) {
                list.add(repo);
            }
        }
        return list;
    }

    @Override
    public String listActiveSessions() {
        List<RepositoryManagement> repositories = repositories();
        StringBuilder buf = new StringBuilder();
        buf.append("Actives sessions for SQL repositories:<br />");
        for (RepositoryManagement repository : repositories) {
            buf.append("<b>").append(repository.getName()).append("</b>: ");
            buf.append(repository.getActiveSessionsCount());
            buf.append("<br />");
        }
        return buf.toString();
    }

    @Override
    public int getActiveSessionsCount() {
        List<RepositoryManagement> repositories = repositories();
        int count = 0;
        for (RepositoryManagement repository : repositories) {
            count += repository.getActiveSessionsCount();
        }
        return count;
    }

    @Override
    public String clearCaches() {
        List<RepositoryManagement> repositories = repositories();
        StringBuilder buf = new StringBuilder();
        buf.append("Cleared cached objects for SQL repositories:<br />");
        for (RepositoryManagement repository : repositories) {
            buf.append("<b>").append(repository.getName()).append("</b>: ");
            buf.append(repository.clearCaches());
            buf.append("<br />");
        }
        return buf.toString();
    }

    protected List<RepositoryManagement> repositories() {
        try {
            return getRepositories();
        } catch (NamingException e) {
            throw new UnsupportedOperationException("Cannot fetch repositories", e);
        }
    }


    @Override
    public long getCachesSize() {
        List<RepositoryManagement> repositories = repositories();
        long size = 0;
        for (RepositoryManagement repository : repositories) {
            size += repository.getCacheSize();
        }
        return size;
    }

    @Override
    public String listRemoteSessions() {
        List<RepositoryManagement> repositories = repositories();
        StringBuilder buf = new StringBuilder();
        buf.append("Actives remote session for SQL repositories:<br />");
        for (RepositoryManagement repository : repositories) {
            buf.append("<b>").append(repository.getName()).append("</b>");
            buf.append("<br/>");
        }
        return buf.toString();
    }

    @Override
    public BinaryManagerStatus gcBinaries(boolean delete) {
        BinaryManagerStatus status = new BinaryManagerStatus();
        List<RepositoryManagement> repositories = repositories();
        long start = System.currentTimeMillis();
        Map<String, BinaryGarbageCollector> repogcs = new LinkedHashMap<String, BinaryGarbageCollector>();
        Map<String, BinaryGarbageCollector> gcs = new LinkedHashMap<String, BinaryGarbageCollector>();
        for (RepositoryManagement repository : repositories) {
            BinaryGarbageCollector gc = repository.getBinaryGarbageCollector();
            if (gc == null) {
                // no GC available for this repository (net backend)
            }
            String gcid = gc.getId();
            if (gcs.containsKey(gcid)) {
                // reuse existing GC with the same unique identifier
                gc = gcs.get(gcid);
            } else {
                gcs.put(gcid, gc);
                gc.start();
            }
            repogcs.put(repository.getName(), gc);
        }
        for (RepositoryManagement repository : repositories) {
            BinaryGarbageCollector gc = repogcs.get(repository.getName());
            repository.markReferencedBinaries(gc);
        }
        for (BinaryGarbageCollector gc : gcs.values()) {
            gc.stop(delete);
            BinaryManagerStatus s = gc.getStatus();
            status.numBinaries += s.numBinaries;
            status.sizeBinaries += s.sizeBinaries;
            status.numBinariesGC += s.numBinariesGC;
            status.sizeBinariesGC += s.sizeBinariesGC;
        }
        status.gcDuration = System.currentTimeMillis() - start;
        return status;
    }

    @Override
    public boolean isBinariesGCInProgress() {
        List<RepositoryManagement> repositories = repositories();
        for (RepositoryManagement repo : repositories) {
            BinaryGarbageCollector gc = repo.getBinaryGarbageCollector();
            if (gc != null & gc.isInProgress()) {
                return true;
            }
        }
        return false;
    }

}
