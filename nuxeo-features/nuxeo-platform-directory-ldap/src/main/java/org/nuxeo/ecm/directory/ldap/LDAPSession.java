/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *
 * $Id$
 */

package org.nuxeo.ecm.directory.ldap;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SimpleTimeZone;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.SizeLimitExceededException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DataModel;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.impl.DataModelImpl;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.utils.SIDGenerator;
import org.nuxeo.ecm.directory.Directory;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.directory.DirectoryFieldMapper;
import org.nuxeo.ecm.directory.EntrySource;
import org.nuxeo.ecm.directory.Reference;
import org.nuxeo.ecm.directory.Session;

/**
 * This class represents a session against an LDAPDirectory.
 *
 * @author Olivier Grisel <ogrisel@nuxeo.com>
 *
 */
public class LDAPSession implements Session, EntrySource {

    // directory connection parameters
    private static final Log log = LogFactory.getLog(LDAPSession.class);

    protected final String schemaName;

    protected final DirContext dirContext;

    protected final String idAttribute;

    protected final LDAPDirectory directory;

    protected final String searchBaseDn;

    protected final Set<String> emptySet = Collections.emptySet();

    protected final String sid;

    protected final Map<String, Field> schemaFieldMap;

    protected final String substringMatchType;

    public LDAPSession(LDAPDirectory directory, DirContext dirContext) {
        this.directory = directory;
        this.dirContext = dirContext;
        DirectoryFieldMapper fieldMapper = directory.getFieldMapper();
        idAttribute = fieldMapper.getBackendField(directory.getConfig().getIdField());
        schemaName = directory.getSchema();
        schemaFieldMap = directory.getSchemaFieldMap();
        sid = String.valueOf(SIDGenerator.next());
        searchBaseDn = directory.getConfig().getSearchBaseDn();
        substringMatchType = directory.getConfig().getSubstringMatchType();
    }

    public Directory getDirectory() {
        return directory;
    }

    public DirContext getContext() {
        return dirContext;
    }

    public DocumentModel createEntry(Map<String, Object> fieldMap)
            throws DirectoryException {
        if (isReadOnly()) {
            return null;
        }
        List<String> referenceFieldList = new LinkedList<String>();
        try {
            String dn = String.format("%s=%s,%s", idAttribute,
                    fieldMap.get(getIdField()),
                    directory.getConfig().getCreationBaseDn());
            Attributes attrs = new BasicAttributes();
            Attribute attr;

            List<String> mandatoryAttributes = getMandatoryAttributes();
            for (String mandatoryAttribute : mandatoryAttributes) {
                attr = new BasicAttribute(mandatoryAttribute);
                attr.add(" ");
                attrs.put(attr);
            }

            String[] creationClasses = directory.getConfig().getCreationClasses();
            if (creationClasses.length != 0) {
                attr = new BasicAttribute("objectclass");
                for (String creationClasse : creationClasses) {
                    attr.add(creationClasse);
                }
                attrs.put(attr);
            }

            String backendFieldId;
            for (String fieldId : fieldMap.keySet()) {
                backendFieldId = directory.getFieldMapper().getBackendField(
                        fieldId);
                if (backendFieldId.equals(getPasswordField())) {
                    attr = new BasicAttribute(backendFieldId);
                    attr.add(fieldMap.get(fieldId)); // TODO: encode in ssha
                    // or md5
                    attrs.put(attr);
                } else if (directory.isReference(fieldId)) {
                    Reference reference = directory.getReference(fieldId);
                    if (reference instanceof LDAPReference) {
                        attr = new BasicAttribute(
                                ((LDAPReference) reference).getStaticAttributeId());
                        attr.add(directory.getConfig().getEmptyRefMarker());
                        attrs.put(attr);
                    }
                    referenceFieldList.add(fieldId);
                } else {
                    Object value = fieldMap.get(fieldId);
                    if ((value != null) && !value.equals("")) {
                        attrs.put(getAttributeValue(fieldId, value));
                    }
                }
            }
            dirContext.bind(dn, null, attrs);

            for (String referenceFieldName : referenceFieldList) {
                Reference reference = directory.getReference(referenceFieldName);
                List<String> targetIds = (List<String>) fieldMap.get(referenceFieldName);
                reference.addLinks((String) fieldMap.get(getIdField()),
                        targetIds);
            }

            directory.invalidateCaches();
            return fieldMapToDocumentModel(fieldMap);
        } catch (Exception e) {
            throw new DirectoryException("createEntry failed", e);
        }
    }

    public DocumentModel getEntry(String id) throws DirectoryException {
        return directory.getCache().getEntry(id, this);
    }

    public DocumentModel getEntryFromSource(String id)
            throws DirectoryException {
        try {
            SearchResult result = getLdapEntry(id);
            if (result == null) {
                return null;
            }
            // fetch result with references
            return ldapResultToDocumentModel(result, id, true);
        } catch (NamingException e) {
            throw new DirectoryException("getEntry failed: " + e.getMessage(),
                    e);
        }
    }

    public boolean hasEntry(String id) throws DirectoryException {
        try {
            // TODO: check directory cache first
            return getLdapEntry(id) != null;
        } catch (NamingException e) {
            throw new DirectoryException("hasEntry failed: " + e.getMessage(),
                    e);
        }
    }

    protected SearchResult getLdapEntry(String id) throws NamingException,
            DirectoryException {
        return getLdapEntry(id, false);
    }

    protected SearchResult getLdapEntry(String id, boolean fetchAllAttributes)
            throws NamingException, DirectoryException {
        String filterExpr;
        if (directory.getBaseFilter().startsWith("(")) {
            filterExpr = String.format("(&(%s={0})%s)", idAttribute,
                    directory.getBaseFilter());
        } else {
            filterExpr = String.format("(&(%s={0})(%s))", idAttribute,
                    directory.getBaseFilter());
        }
        String[] filterArgs = new String[] { id };
        SearchControls scts = directory.getSearchControls(fetchAllAttributes);

        if (log.isDebugEnabled()) {
            log.debug(String.format("LDAP search base='%s' filter='%s' "
                    + " args='%s' scope='%s'", searchBaseDn, filterExpr, id,
                    scts.getSearchScope()));
        }
        NamingEnumeration<SearchResult> results = dirContext.search(
                searchBaseDn, filterExpr, filterArgs, scts);

        if (!results.hasMore()) {
            log.debug("Entry not found: " + id);
            return null;
        }
        SearchResult result = results.next();
        if (results.hasMore()) {
            log.debug("More than one entry found");
            throw new DirectoryException("more than one entry found for " + id);
        }
        return result;
    }

    public DocumentModelList getEntries() throws DirectoryException {
        try {
            SearchControls scts = directory.getSearchControls();
            log.debug(String.format("LDAP search base='%s' filter='%s' "
                    + " args=* scope=%s", searchBaseDn,
                    directory.getBaseFilter(), scts.getSearchScope()));
            NamingEnumeration<SearchResult> results = dirContext.search(
                    searchBaseDn, directory.getBaseFilter(), scts);
            // skip reference fetching
            return ldapResultsToDocumentModels(results, false);
        } catch (SizeLimitExceededException e) {
            throw new org.nuxeo.ecm.directory.SizeLimitExceededException(e);
        } catch (NamingException e) {
            throw new DirectoryException("getEntries failed", e);
        }
    }

    public void updateEntry(DocumentModel docModel) throws DirectoryException {
        List<String> updateList = new ArrayList<String>();
        List<String> referenceFieldList = new LinkedList<String>();

        try {
            DataModel dataModel = docModel.getDataModel(schemaName);
            for (String fieldName : schemaFieldMap.keySet()) {
                if (!dataModel.isDirty(fieldName)) {
                    continue;
                }
                if (directory.isReference(fieldName)) {
                    referenceFieldList.add(fieldName);
                } else {
                    updateList.add(fieldName);
                }
            }

            if (!isReadOnly() && !updateList.isEmpty()) {
                Attributes attrs = new BasicAttributes();
                Attribute attr;
                SearchResult ldapEntry = getLdapEntry(docModel.getId());
                if (ldapEntry == null) {
                    throw new DirectoryException(docModel.getId()
                            + " not found");
                }
                Attributes oldattrs = ldapEntry.getAttributes();
                String dn = ldapEntry.getNameInNamespace();
                Attributes attrsToDel = new BasicAttributes();
                for (String f : updateList) {
                    // TODO: encode password
                    Object value = docModel.getProperty(schemaName, f);
                    String backendField = directory.getFieldMapper().getBackendField(
                            f);
                    if ((value == null) || (value.equals(""))) {
                        if (getMandatoryAttributes().contains(backendField)) {
                            attr = new BasicAttribute(backendField);
                            attr.add(" ");
                            attrs.put(attr);
                        } else if (oldattrs.get(backendField) != null) {
                            attr = new BasicAttribute(backendField);
                            attr.add(oldattrs.get(backendField).get());
                            attrsToDel.put(attr);
                        }
                    } else {
                        attrs.put(getAttributeValue(f, value));
                    }
                }
                dirContext.modifyAttributes(dn, DirContext.REMOVE_ATTRIBUTE,
                        attrsToDel);
                dirContext.modifyAttributes(dn, DirContext.REPLACE_ATTRIBUTE,
                        attrs);
            }

            // update reference fields
            for (String referenceFieldName : referenceFieldList) {
                Reference reference = directory.getReference(referenceFieldName);
                List<String> targetIds = (List<String>) docModel.getProperty(
                        schemaName, referenceFieldName);
                reference.setTargetIdsForSource(docModel.getId(), targetIds);
            }

        } catch (Exception e) {
            throw new DirectoryException("updateEntry failed: "
                    + e.getMessage(), e);
        }
        directory.invalidateCaches();
    }

    public void deleteEntry(DocumentModel dm) throws DirectoryException {
        deleteEntry(dm.getId());
    }

    public void deleteEntry(String id) throws DirectoryException {
        if (isReadOnly()) {
            return;
        }
        try {
            for (String fieldName : schemaFieldMap.keySet()) {
                if (directory.isReference(fieldName)) {
                    Reference reference = directory.getReference(fieldName);
                    reference.removeLinksForSource(id);
                }
            }
            SearchResult result = getLdapEntry(id);
            dirContext.destroySubcontext(result.getNameInNamespace());
        } catch (Exception e) {
            throw new DirectoryException("deleteEntry failed", e);
        }
        directory.invalidateCaches();
    }

    public void deleteEntry(String id, Map<String, String> map)
            throws DirectoryException {
        log.warn("Calling deleteEntry extended on LDAP directory");
        deleteEntry(id);
    }

    public DocumentModelList query(Map<String, Object> filter,
            Set<String> fulltext, boolean fetchReferences,
            Map<String, String> orderBy) throws DirectoryException {
        try {
            // building the query using filterExpr / filterArgs to
            // escape special characters and to fulltext search only on
            // the explicitly specified fields
            String[] filters = new String[filter.size()];
            String[] filterArgs = new String[filter.size()];

            if (fulltext == null) {
                fulltext = Collections.emptySet();
            }

            int index = 0;
            for (String fieldName : filter.keySet()) {
                if (directory.isReference(fieldName)) {
                    log.warn(fieldName
                            + " is a reference and will be ignored as a query criterion");
                    continue;
                }

                String backendFieldName = directory.getFieldMapper().getBackendField(
                        fieldName);
                Object fieldValue = filter.get(fieldName);

                StringBuilder currentFilter = new StringBuilder();
                currentFilter.append("(");
                if (fieldValue == null) {
                    currentFilter.append("!(" + backendFieldName + "=*)");
                } else if ("".equals(fieldValue)) {
                    if (fulltext.contains(fieldName)) {
                        currentFilter.append(backendFieldName + "=*");
                    } else {
                        currentFilter.append(backendFieldName + "=");
                    }
                } else {
                    currentFilter.append(backendFieldName + "=");
                    if (fulltext.contains(fieldName)) {
                        if (LDAPSubstringMatchType.SUBFINAL.equals(substringMatchType)) {
                            currentFilter.append("*{" + index + "}");
                        } else if (LDAPSubstringMatchType.SUBANY.equals(substringMatchType)) {
                            currentFilter.append("*{" + index + "}*");
                        } else {
                            // default behavior: subinitial
                            currentFilter.append("{" + index + "}*");
                        }
                    } else {
                        currentFilter.append("{" + index + "}");
                    }
                }
                currentFilter.append(")");
                filters[index] = currentFilter.toString();
                if (fieldValue != null && !"".equals(fieldValue)) {
                    // XXX: what kind of Objects can we get here? Is toString()
                    // enough?
                    filterArgs[index] = fieldValue.toString();
                }
                index++;
            }
            String filterExpr = "(&" + directory.getBaseFilter()
                    + StringUtils.join(filters) + ')';
            SearchControls scts = directory.getSearchControls();
            log.debug("LDAP search base=" + searchBaseDn + " filter="
                    + filterExpr + " args=" + StringUtils.join(filterArgs, ",")
                    + " scope=" + scts.getSearchScope());

            NamingEnumeration<SearchResult> results = dirContext.search(
                    searchBaseDn, filterExpr, filterArgs, scts);

            DocumentModelList entries = ldapResultsToDocumentModels(results,
                    fetchReferences);

            if (orderBy != null && !orderBy.isEmpty()) {
                directory.orderEntries(entries, orderBy);
            }

            return entries;
        } catch (SizeLimitExceededException e) {
            throw new org.nuxeo.ecm.directory.SizeLimitExceededException(e);
        } catch (NamingException e) {
            throw new DirectoryException("executeQuery failed", e);
        }
    }

    public DocumentModelList query(Map<String, Object> filter)
            throws DirectoryException {
        // by default, do not fetch references of result entries
        return query(filter, emptySet, new HashMap<String, String>());
    }

    public DocumentModelList query(Map<String, Object> filter,
            Set<String> fulltext, Map<String, String> orderBy)
            throws DirectoryException {

        return query(filter, fulltext, false, orderBy);

    }

    public DocumentModelList query(Map<String, Object> filter,
            Set<String> fulltext) throws DirectoryException {
        // by default, do not fetch references of result entries
        return query(filter, fulltext, new HashMap<String, String>());
    }

    public void commit() {
        // No LDAP support for transactions
    }

    public void rollback() {
        // No LDAP support for transactions
    }

    public void close() throws DirectoryException {
        try {
            dirContext.close();
            directory.removeSession(this);
        } catch (NamingException e) {
            throw new DirectoryException("close failed", e);
        }
    }

    public List<String> getProjection(Map<String, Object> filter,
            String columnName) throws DirectoryException {
        return getProjection(filter, emptySet, columnName);
    }

    public List<String> getProjection(Map<String, Object> filter,
            Set<String> fulltext, String columnName) throws DirectoryException {
        // XXX: this suboptimal code should be either optimized for LDAP or
        // moved to an abstract class
        List<String> result = new ArrayList<String>();
        DocumentModelList docList = query(filter, fulltext);
        String columnNameinDocModel = directory.getFieldMapper().getDirectoryField(
                columnName);
        for (DocumentModel docModel : docList) {
            Object obj = docModel.getProperty(schemaName, columnNameinDocModel);
            String propValue;
            if (obj instanceof String) {
                propValue = (String) obj;
            } else {
                propValue = String.valueOf(obj);
            }
            result.add(propValue);
        }
        return result;
    }

    // XXX: this should be moved to an abstract session class
    protected DocumentModel fieldMapToDocumentModel(Map<String, Object> fieldMap) {
        DataModel dataModel = new DataModelImpl(schemaName, fieldMap);

        String id = String.valueOf(fieldMap.get(getIdField()));
        DocumentModelImpl docModel = new DocumentModelImpl(sid, schemaName, id,
                null, null, null, new String[] { schemaName }, null);
        dataModel.setMap(fieldMap);
        docModel.addDataModel(dataModel);

        return docModel;
    }

    @SuppressWarnings("unchecked")
    protected Object getFieldValue(Attribute attribute, String fieldName,
            String entryId, boolean fetchReferences) throws DirectoryException {

        Field field = schemaFieldMap.get(fieldName);
        Type type = field.getType();
        Object defaultValue = field.getDefaultValue();
        String typeName = type.getName();
        if (attribute == null) {
            return defaultValue;
        }
        Object value;
        try {
            value = attribute.get();
        } catch (NamingException e) {
            throw new DirectoryException("Could not fetch value for "
                    + attribute, e);
        }
        if (value == null) {
            return defaultValue;
        }
        String trimmedValue = value.toString().trim();
        if ("string".equals(typeName)) {
            return trimmedValue;
        } else if ("integer".equals(typeName) || "long".equals(typeName)) {
            if ("".equals(trimmedValue)) {
                return defaultValue;
            }
            try {
                return Long.valueOf(trimmedValue);
            } catch (NumberFormatException e) {
                log.error(String.format(
                        "field %s of type %s has non-numeric value found on server: '%s' (ignoring and using default value instead)",
                        fieldName, typeName, trimmedValue));
                return defaultValue;
            }
        } else if (type.isListType()) {
            List<String> parsedItems = new LinkedList<String>();
            NamingEnumeration<Object> values = null;
            try {
                values = (NamingEnumeration<Object>) attribute.getAll();
                while (values.hasMore()) {
                    parsedItems.add(values.next().toString().trim());
                }
                return parsedItems;
            } catch (NamingException e) {
                log.error(String.format(
                        "field %s of type %s has non list value found on server: '%s' (ignoring and using default value instead)",
                        fieldName, typeName, values != null ? values.toString()
                                : trimmedValue));
                return defaultValue;
            }

        } else if ("date".equals(typeName)) {
            if ("".equals(trimmedValue)) {
                return defaultValue;
            }
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat(
                        "yyyyMMddHHmmss'Z'");
                dateFormat.setTimeZone(new SimpleTimeZone(0, "Z"));
                Date date = dateFormat.parse(trimmedValue);
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                return cal;
            } catch (ParseException e) {
                log.error(String.format(
                        "field %s of type %s has invalid value found on server: '%s' (ignoring and using default value instead)",
                        fieldName, typeName, trimmedValue));
                return defaultValue;
            }
        } else {
            throw new DirectoryException(
                    "Field type not supported in directories: " + typeName);
        }
    }

    @SuppressWarnings("unchecked")
    protected Attribute getAttributeValue(String fieldName, Object value)
            throws DirectoryException {
        Attribute attribute = new BasicAttribute(
                directory.getFieldMapper().getBackendField(fieldName));
        Type type = schemaFieldMap.get(fieldName).getType();
        String typeName = type.getName();

        if ("string".equals(typeName)) {
            attribute.add(value);
        } else if ("integer".equals(typeName) || "long".equals(typeName)) {
            attribute.add(value.toString());
        } else if (type.isListType()) {
            Collection<String> valueItems;
            if (value instanceof String[]) {
                valueItems = Arrays.asList((String[]) value);
            } else if (value instanceof Collection) {
                valueItems = (Collection<String>) value;
            } else {
                throw new DirectoryException(String.format(
                        "field %s with value %s does not match type %s",
                        fieldName, value.toString(), type.getName()));
            }
            for (String item : valueItems) {
                attribute.add(item);
            }
        } else if ("date".equals(typeName)) {
            Calendar cal = (Calendar) value;
            Date date = cal.getTime();
            SimpleDateFormat dateFormat = new SimpleDateFormat(
                    "yyyyMMddHHmmss'Z'");
            dateFormat.setTimeZone(new SimpleTimeZone(0, "Z"));
            attribute.add(dateFormat.format(date));
        } else {
            throw new DirectoryException(
                    "Field type not supported in directories: " + typeName);
        }

        return attribute;
    }

    protected DocumentModelList ldapResultsToDocumentModels(
            NamingEnumeration<SearchResult> results, boolean fetchReferences)
            throws DirectoryException {
        DocumentModelList list = new DocumentModelListImpl();
        try {
            DocumentModel entry;
            while (results.hasMore()) {
                SearchResult result = results.next();
                entry = ldapResultToDocumentModel(result, null, fetchReferences);
                if (entry != null) {
                    list.add(entry);
                }
            }
        } catch (NamingException e) {
            throw new DirectoryException("Could not create DocumentModelList",
                    e);
        }
        log.debug("LDAP search returned " + list.size() + " results");
        return list;
    }

    protected DocumentModel ldapResultToDocumentModel(SearchResult result,
            String entryId, boolean fetchReferences) throws DirectoryException {
        Attributes attributes = result.getAttributes();
        Attribute attribute;
        String attributeId;
        String passwordFieldId = getPasswordField();
        Map<String, Object> fieldMap = new HashMap<String, Object>();

        if (entryId == null) {
            try {
                // NXP-2461: check that id field is filled
                attribute = attributes.get(idAttribute);
                if (attribute != null) {
                    Object entry = attribute.get();
                    if (entry != null) {
                        entryId = entry.toString();
                    }
                }
            } catch (NamingException e) {
                throw new DirectoryException("could not fetch " + idAttribute,
                        e);
            }
        }

        if (entryId == null) {
            // don't bother
            return null;
        }
        for (String fieldName : schemaFieldMap.keySet()) {
            Reference reference = directory.getReference(fieldName);
            if (reference != null) {
                // reference resolution
                List<String> referencedIds;
                if (!fetchReferences) {
                    referencedIds = new ArrayList<String>();
                }
                if (reference instanceof LDAPReference) {
                    // optim: use the current LDAPSession directly to provide
                    // the LDAP reference with the needed backend entries
                    LDAPReference ldapReference = (LDAPReference) reference;
                    referencedIds = ldapReference.getLdapTargetIds(attributes);
                } else {
                    try {
                        referencedIds = reference.getTargetIdsForSource(entryId);
                    } catch (ClientException e) {
                        throw new DirectoryException(e);
                    }
                }
                fieldMap.put(fieldName, referencedIds);
            } else {
                // manage directly stored fields
                attributeId = directory.getFieldMapper().getBackendField(
                        fieldName);
                attribute = attributes.get(attributeId);
                if (fieldName.equals(passwordFieldId)) {
                    // do not try to fetch the password attribute
                    continue;
                } else {
                    fieldMap.put(fieldName, getFieldValue(attribute, fieldName,
                            entryId, fetchReferences));
                }
            }
        }
        // check if the idAttribute was returned from the search. If not
        // set it anyway.
        String fieldId = directory.getFieldMapper().getDirectoryField(idAttribute);
        Object obj = fieldMap.get(fieldId);
        if(obj == null) {
            fieldMap.put(fieldId, entryId);
        }
        return fieldMapToDocumentModel(fieldMap);
    }

    public boolean authenticate(String username, String password)
            throws DirectoryException {

        if (password == null || "".equals(password.trim())) {
            // never use anonymous bind as a way to authenticate a user in Nuxeo
            // EP
            return false;
        }

        // lookup the user: fetch its dn
        SearchResult entry;
        try {
            entry = getLdapEntry(username);
        } catch (NamingException e) {
            throw new DirectoryException("failed to fetch the ldap entry for "
                    + username, e);
        }
        if (entry == null) {
            // no such user => authentication failed
            return false;
        }
        String dn = entry.getNameInNamespace();
        Properties env = (Properties) directory.getContextProperties().clone();
        env.put(Context.SECURITY_PRINCIPAL, dn);
        env.put(Context.SECURITY_CREDENTIALS, password);
        try {
            // creating a context does a bind
            log.debug(String.format("LDAP bind dn='%s'", dn));
            // noinspection ResultOfObjectAllocationIgnored
            new InitialDirContext(env);
            log.debug("Bind succeeded, authentication ok");
            return true;
        } catch (NamingException e) {
            log.debug("Bind failed: " + e.getMessage());
            // authentication failed
            return false;
        }
    }

    public String getIdField() {
        return directory.getConfig().getIdField();
    }

    public String getPasswordField() {
        return directory.getConfig().getPasswordField();
    }

    public boolean isAuthenticating() throws DirectoryException {
        String password = getPasswordField();
        return schemaFieldMap.containsKey(password);
    }

    public boolean isReadOnly() {
        return directory.getConfig().getReadOnly();
    }

    public boolean rdnMatchesIdField() {
        return directory.getConfig().rdnAttribute.equals(idAttribute);
    }

    protected List<String> getMandatoryAttributes() throws DirectoryException {
        try {
            List<String> mandatoryAttributes = new ArrayList<String>();

            DirContext schema = dirContext.getSchema("");
            Attributes attributes;
            Attribute attribute;
            NamingEnumeration<String> values;
            String value;
            List<String> creationClasses = new ArrayList<String>(
                    Arrays.asList(directory.getConfig().getCreationClasses()));
            creationClasses.remove("top");
            for (String creationClass : creationClasses) {
                attributes = schema.getAttributes("ClassDefinition/"
                        + creationClass);
                attribute = attributes.get("MUST");
                if (attribute != null) {
                    values = (NamingEnumeration<String>) attribute.getAll();
                    while (values.hasMore()) {
                        value = values.next();
                        mandatoryAttributes.add(value);
                    }
                }
            }

            return mandatoryAttributes;
        } catch (NamingException e) {
            throw new DirectoryException("getMandatoryAttributes failed", e);
        }
    }

}
