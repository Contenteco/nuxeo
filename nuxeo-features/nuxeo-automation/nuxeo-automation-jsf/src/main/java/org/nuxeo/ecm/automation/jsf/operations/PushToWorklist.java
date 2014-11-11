/*
 * (C) Copyright 2006-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 */

package org.nuxeo.ecm.automation.jsf.operations;

import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.jsf.OperationHelper;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.webapp.documentsLists.DocumentsListsManager;
import org.nuxeo.ecm.webapp.documentsLists.DocumentsListsPersistenceManager;

/**
 * @author <a href="mailto:td@nuxeo.com">Thierry Delprat</a>
 */
@Operation(id = PushToWorklist.ID, category = Constants.CAT_UI, requires = Constants.SEAM_CONTEXT, label = "Push to Worklist", description = "Add the input document(s) to worklist. Returns back the document(s)")
public class PushToWorklist {

    public static final String ID = "Seam.AddToWorklist";

    @Context
    protected OperationContext ctx;

    @OperationMethod
    public DocumentModel run(DocumentModel doc) throws Exception {
        if (OperationHelper.isSeamContextAvailable()) {
            OperationHelper.getDocumentListManager().addToWorkingList(
                    DocumentsListsManager.DEFAULT_WORKING_LIST, doc);
        }
        else {
            DocumentsListsPersistenceManager pm = new DocumentsListsPersistenceManager();
            pm.addDocumentToPersistentList(ctx.getPrincipal().getName(), DocumentsListsManager.DEFAULT_WORKING_LIST, doc);
        }
        return doc;
    }

    @OperationMethod
    public DocumentModelList run(DocumentModelList docs) throws Exception {
        if (OperationHelper.isSeamContextAvailable()) {
            OperationHelper.getDocumentListManager().addToWorkingList(
                    DocumentsListsManager.DEFAULT_WORKING_LIST, docs);
        }
        else {
            DocumentsListsPersistenceManager pm = new DocumentsListsPersistenceManager();
            for (DocumentModel doc :docs) {
                pm.addDocumentToPersistentList(ctx.getPrincipal().getName(), DocumentsListsManager.DEFAULT_WORKING_LIST, doc);
            }
        }
        return docs;
    }

}
