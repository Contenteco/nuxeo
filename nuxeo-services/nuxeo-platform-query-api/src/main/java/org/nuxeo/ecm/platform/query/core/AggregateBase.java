/*
 * (C) Copyright 2014 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Benoit Delbosc
 */
package org.nuxeo.ecm.platform.query.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.AggregateDefinition;
import org.nuxeo.ecm.platform.query.api.AggregateRangeDateDefinition;
import org.nuxeo.ecm.platform.query.api.AggregateRangeDefinition;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.query.api.PredicateFieldDefinition;

/**
 * @since 5.9.6
 */
public class AggregateBase<B extends Bucket> implements Aggregate<B> {

    protected final AggregateDefinition definition;
    protected final DocumentModel searchDocument;
    protected List<String> selection;
    protected List<B> buckets;
    protected List<Bucket> extendedBuckets;
    protected Map<String,Bucket> bucketMap = null;

    public AggregateBase(AggregateDefinition definition,
                         DocumentModel searchDocument) {
        assert (definition != null);
        this.definition = definition;
        this.searchDocument = searchDocument;
    }

    @Override
    public String getId() {
        return definition.getId();
    }

    @Override
    public String getType() {
        return definition.getType();
    }

    @Override
    public String getField() {
        return definition.getDocumentField();
    }

    @Override
    public Map<String, String> getProperties() {
        return definition.getProperties();
    }

    @Override
    public List<AggregateRangeDefinition> getRanges() {
        return definition.getRanges();
    }

    @Override
    public List<AggregateRangeDateDefinition> getDateRanges() {
        return definition.getDateRanges();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getSelection() {
        if (selection == null) {
            PredicateFieldDefinition field = definition.getSearchField();
            if (searchDocument != null) {
                // property must be nxs:stringList
                List<String> value = null;
                Object resolvedProperties =  searchDocument.getProperty(
                        field.getSchema(), field.getName());
                if (resolvedProperties instanceof String[]) {
                    value = Arrays.asList((String[]) resolvedProperties);
                } else if (resolvedProperties instanceof List<?>) {
                    value = (List<String>) searchDocument.getProperty(
                            field.getSchema(), field.getName());
                }
                selection = value;
            }
            if (selection == null) {
                selection = Collections.<String> emptyList();
            }
        }
        return selection;
    }

    @Override
    public void setSelection(List<String> selection) {
        this.selection = selection;
    }

    @Override
    public List<B> getBuckets() {
        return buckets;
    }

    @Override
    public List<Bucket> getExtendedBuckets() {
        if (extendedBuckets == null) {
            extendedBuckets = new ArrayList<Bucket>();
            final List<String> currentSelection = getSelection();
            if (currentSelection != null) {
                for (String s : currentSelection) {
                    if (!hasBucket(s)) {
                        extendedBuckets.add(new MockBucket(s));
                    }
                }
            }
            extendedBuckets.addAll(buckets);
        }
        return extendedBuckets;
    }

    @Override
    public void setBuckets(List<B> buckets) {
        this.buckets = buckets;
        this.bucketMap = null;
        this.extendedBuckets = null;
    }

    public DocumentModel getSearchDocument() {
        return searchDocument;
    }

    @Override
    public String toString() {
        return String.format("Aggregate(%s, %s, %s, %s, %s)", getId(),
                getType(), getField(),
                (getSelection() != null) ? Arrays.toString(getSelection().toArray()): null,
                (buckets != null) ? Arrays.toString(buckets.toArray()): null);
    }

    @Override
    public boolean hasBucket(final String key) {
        return getBucketMap().containsKey(key);
    }

    @Override
    public Bucket getBucket(final String key) {
        return getBucketMap().get(key);
    }

    public Map<String, Bucket> getBucketMap() {
        if (bucketMap == null && getBuckets() != null) {
            bucketMap = new HashMap<String, Bucket>();
            for (Bucket b : getBuckets()) {
                bucketMap.put(b.getKey(), b);
            }
        }
        return bucketMap;
    }

    @Override
    public void resetSelection() {
        PredicateFieldDefinition field = definition.getSearchField();
        if (searchDocument != null) {
            searchDocument.setProperty(field.getSchema(), field.getName(), null);
            selection = Collections.<String> emptyList();
        }
    }

}
